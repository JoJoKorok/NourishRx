package com.jojokorok.nourishrx.backup;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BackupDatabaseImporter {
    private static final String TABLE_PROFILES = "profiles";
    private static final String TABLE_MEDICATIONS = "medications";
    private static final String TABLE_DOSE_LOGS = "dose_logs";
    private static final String TABLE_NUTRITION_MEALS = "nutrition_meals";
    private static final String TABLE_NUTRITION_FOODS = "nutrition_foods";
    private static final String TABLE_MEAL_FOOD_LOGS = "meal_food_logs";
    private static final String TABLE_WATER_ENTRIES = "water_entries";
    private static final String TABLE_WEIGHT_ENTRIES = "weight_entries";
    private static final String TABLE_MEAL_DEFAULTS = "meal_defaults";
    private static final String TABLE_SAVED_MEALS = "saved_meals";
    private static final String TABLE_SAVED_MEAL_ITEMS = "saved_meal_items";

    private final MedicationStore store;
    private final BackupAvatarStore avatarStore;

    public BackupDatabaseImporter(Context context, MedicationStore store) {
        this.store = store;
        this.avatarStore = new BackupAvatarStore(
                new File(context.getFilesDir(), "imported-profile-avatars")
        );
    }

    public BackupImportResult apply(NourishRxBackup backup, BackupImportMode mode)
            throws BackupImportException {
        if (mode == null) {
            throw new BackupImportException("Choose whether to merge or replace existing data");
        }
        BackupValidationResult validation = BackupValidator.validate(backup);
        if (!validation.isValid()) {
            throw new BackupImportException(String.join("; ", validation.errors()));
        }

        BackupAvatarStore.PreparedAvatars preparedAvatars = avatarStore.prepare(backup.profiles);
        SQLiteDatabase database = store.getWritableDatabase();
        List<String> replacedAvatarUris = mode == BackupImportMode.REPLACE
                ? currentAvatarUris(database)
                : new ArrayList<>();

        Map<Long, Long> profileIds = new HashMap<>();
        Map<Long, Long> medicationIds = new HashMap<>();
        Map<Long, Long> foodIds = new HashMap<>();
        Map<Long, Long> savedMealIds = new HashMap<>();
        long restoredProfileId = 0;
        try {
            database.beginTransaction();
            try {
                if (mode == BackupImportMode.REPLACE) {
                    clearCurrentData(database);
                }
                insertProfiles(database, backup, mode, preparedAvatars, profileIds);
                insertMedications(database, backup, mode, profileIds, medicationIds);
                insertDoseLogs(database, backup, mode, medicationIds);
                insertNutritionMeals(database, backup, mode, profileIds);
                insertFoods(database, backup, mode, profileIds, foodIds);
                insertMealFoodLogs(database, backup, mode, profileIds, foodIds);
                insertWaterEntries(database, backup, mode, profileIds);
                insertWeightEntries(database, backup, mode, profileIds);
                insertMealDefaults(database, backup, mode, profileIds);
                insertSavedMeals(database, backup, mode, profileIds, savedMealIds);
                insertSavedMealItems(database, backup, mode, savedMealIds, foodIds);
                if (mode == BackupImportMode.REPLACE) {
                    restoredProfileId = mappedId(
                            profileIds,
                            backup.settings.selectedProfileId,
                            "selected profile"
                    );
                }
                verifyForeignKeys(database);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } catch (RuntimeException | BackupImportException exception) {
            preparedAvatars.discard();
            if (exception instanceof BackupImportException) {
                throw (BackupImportException) exception;
            }
            throw new BackupImportException(
                    "The import failed, so no database changes were saved",
                    exception
            );
        }

        if (mode == BackupImportMode.REPLACE) {
            avatarStore.deleteManagedUris(replacedAvatarUris);
        }
        return new BackupImportResult(
                mode,
                backup.profiles.size(),
                recordCount(backup),
                restoredProfileId,
                mode == BackupImportMode.REPLACE ? backup.settings.appMode : ""
        );
    }

    private static void insertProfiles(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            BackupAvatarStore.PreparedAvatars avatars,
            Map<Long, Long> profileIds
    ) {
        for (NourishRxBackup.ProfileRecord record : backup.profiles) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("name", record.name);
            values.put("avatar_uri", avatars.uriFor(record.id));
            values.put("avatar_zoom", record.avatar == null ? 1.0f : record.avatar.zoom);
            values.put("avatar_offset_x", record.avatar == null ? 0.0f : record.avatar.offsetX);
            values.put("avatar_offset_y", record.avatar == null ? 0.0f : record.avatar.offsetY);
            values.put("avatar_aspect_ratio", record.avatar == null ? 1.0f : record.avatar.aspectRatio);
            values.put("created_at", record.createdAtEpochMillis);
            profileIds.put(record.id, insert(database, TABLE_PROFILES, values));
        }
    }

    private static void insertMedications(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> profileIds,
            Map<Long, Long> medicationIds
    ) throws BackupImportException {
        for (NourishRxBackup.MedicationRecord record : backup.medications) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("profile_id", mappedId(profileIds, record.profileId, "medication profile"));
            values.put("name", record.name);
            values.put("dosage", record.dosage);
            values.put("instructions", record.instructions);
            values.put("first_dose_minutes", record.doseMinutes.get(0));
            values.put("doses_per_day", record.doseMinutes.size());
            values.put("dose_minutes", Medication.serializeDoseMinutes(record.doseMinutes));
            values.put("quantity", record.quantity);
            values.put("refill_threshold", record.refillThreshold);
            values.put("repeat_reminder_minutes", record.repeatReminderMinutes);
            values.put("active", record.active ? 1 : 0);
            values.put("created_at", record.createdAtEpochMillis);
            medicationIds.put(record.id, insert(database, TABLE_MEDICATIONS, values));
        }
    }

    private static void insertDoseLogs(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> medicationIds
    ) throws BackupImportException {
        for (NourishRxBackup.DoseLogRecord record : backup.doseLogs) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("medication_id", mappedId(medicationIds, record.medicationId, "dose medication"));
            values.put("scheduled_at", record.scheduledAtEpochMillis);
            values.put("status", record.status);
            values.put("logged_at", record.loggedAtEpochMillis);
            insert(database, TABLE_DOSE_LOGS, values);
        }
    }

    private static void insertNutritionMeals(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> profileIds
    ) throws BackupImportException {
        for (NourishRxBackup.NutritionMealRecord record : backup.nutritionMeals) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("profile_id", mappedId(profileIds, record.profileId, "nutrition meal profile"));
            values.put("name", record.name);
            values.put("calories", record.calories);
            values.put("protein_grams", record.proteinGrams);
            values.put("carbs_grams", record.carbsGrams);
            values.put("fat_grams", record.fatGrams);
            values.put("logged_at", record.loggedAtEpochMillis);
            insert(database, TABLE_NUTRITION_MEALS, values);
        }
    }

    private static void insertFoods(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> profileIds,
            Map<Long, Long> foodIds
    ) throws BackupImportException {
        for (NourishRxBackup.FoodRecord record : backup.foods) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("profile_id", mappedId(profileIds, record.profileId, "food profile"));
            values.put("brand", record.brand);
            values.put("name", record.name);
            values.put("serving_size", record.servingSize);
            values.put("servings_per_container", record.servingsPerContainer);
            values.put("calories", record.calories);
            values.put("total_fat_grams", record.totalFatGrams);
            values.put("saturated_fat_grams", record.saturatedFatGrams);
            values.put("trans_fat_grams", record.transFatGrams);
            values.put("cholesterol_mg", record.cholesterolMg);
            values.put("sodium_mg", record.sodiumMg);
            values.put("total_carbs_grams", record.totalCarbsGrams);
            values.put("fiber_grams", record.fiberGrams);
            values.put("total_sugars_grams", record.totalSugarsGrams);
            values.put("added_sugars_grams", record.addedSugarsGrams);
            values.put("protein_grams", record.proteinGrams);
            values.put("vitamin_d_mcg", record.vitaminDMcg);
            values.put("calcium_mg", record.calciumMg);
            values.put("iron_mg", record.ironMg);
            values.put("potassium_mg", record.potassiumMg);
            values.put("created_at", record.createdAtEpochMillis);
            foodIds.put(record.id, insert(database, TABLE_NUTRITION_FOODS, values));
        }
    }

    private static void insertMealFoodLogs(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> profileIds,
            Map<Long, Long> foodIds
    ) throws BackupImportException {
        for (NourishRxBackup.MealFoodLogRecord record : backup.mealFoodLogs) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("profile_id", mappedId(profileIds, record.profileId, "food log profile"));
            values.put("food_id", mappedId(foodIds, record.foodId, "food log food"));
            values.put("meal_name", record.mealName);
            values.put("servings", record.servings);
            values.put("eaten_at", record.eatenAtEpochMillis);
            insert(database, TABLE_MEAL_FOOD_LOGS, values);
        }
    }

    private static void insertWaterEntries(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> profileIds
    ) throws BackupImportException {
        for (NourishRxBackup.WaterEntryRecord record : backup.waterEntries) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("profile_id", mappedId(profileIds, record.profileId, "water profile"));
            values.put("ounces", record.ounces);
            values.put("logged_at", record.loggedAtEpochMillis);
            insert(database, TABLE_WATER_ENTRIES, values);
        }
    }

    private static void insertWeightEntries(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> profileIds
    ) throws BackupImportException {
        for (NourishRxBackup.WeightEntryRecord record : backup.weightEntries) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("profile_id", mappedId(profileIds, record.profileId, "weight profile"));
            values.put("pounds", record.pounds);
            values.put("logged_at", record.loggedAtEpochMillis);
            insert(database, TABLE_WEIGHT_ENTRIES, values);
        }
    }

    private static void insertMealDefaults(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> profileIds
    ) throws BackupImportException {
        for (NourishRxBackup.MealDefaultRecord record : backup.mealDefaults) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("profile_id", mappedId(profileIds, record.profileId, "meal default profile"));
            values.put("name", record.name);
            values.put("sort_order", record.sortOrder);
            insert(database, TABLE_MEAL_DEFAULTS, values);
        }
    }

    private static void insertSavedMeals(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> profileIds,
            Map<Long, Long> savedMealIds
    ) throws BackupImportException {
        for (NourishRxBackup.SavedMealRecord record : backup.savedMeals) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("profile_id", mappedId(profileIds, record.profileId, "saved meal profile"));
            values.put("name", record.name);
            values.put("notes", record.notes);
            values.put("created_at", record.createdAtEpochMillis);
            savedMealIds.put(record.id, insert(database, TABLE_SAVED_MEALS, values));
        }
    }

    private static void insertSavedMealItems(
            SQLiteDatabase database,
            NourishRxBackup backup,
            BackupImportMode mode,
            Map<Long, Long> savedMealIds,
            Map<Long, Long> foodIds
    ) throws BackupImportException {
        for (NourishRxBackup.SavedMealItemRecord record : backup.savedMealItems) {
            ContentValues values = new ContentValues();
            putOriginalId(values, record.id, mode);
            values.put("saved_meal_id", mappedId(savedMealIds, record.savedMealId, "saved meal item"));
            values.put("food_id", mappedId(foodIds, record.foodId, "saved meal food"));
            values.put("servings", record.servings);
            values.put("sort_order", record.sortOrder);
            insert(database, TABLE_SAVED_MEAL_ITEMS, values);
        }
    }

    private static void clearCurrentData(SQLiteDatabase database) {
        database.delete(TABLE_DOSE_LOGS, null, null);
        database.delete(TABLE_SAVED_MEAL_ITEMS, null, null);
        database.delete(TABLE_MEAL_FOOD_LOGS, null, null);
        database.delete(TABLE_NUTRITION_MEALS, null, null);
        database.delete(TABLE_WATER_ENTRIES, null, null);
        database.delete(TABLE_WEIGHT_ENTRIES, null, null);
        database.delete(TABLE_MEAL_DEFAULTS, null, null);
        database.delete(TABLE_SAVED_MEALS, null, null);
        database.delete(TABLE_MEDICATIONS, null, null);
        database.delete(TABLE_NUTRITION_FOODS, null, null);
        database.delete(TABLE_PROFILES, null, null);
    }

    private static List<String> currentAvatarUris(SQLiteDatabase database) {
        List<String> uris = new ArrayList<>();
        try (Cursor cursor = database.query(
                TABLE_PROFILES,
                new String[]{"avatar_uri"},
                "avatar_uri != ''",
                null,
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                uris.add(cursor.getString(cursor.getColumnIndexOrThrow("avatar_uri")));
            }
        }
        return uris;
    }

    private static void verifyForeignKeys(SQLiteDatabase database) throws BackupImportException {
        try (Cursor cursor = database.rawQuery("PRAGMA foreign_key_check", null)) {
            if (cursor.moveToFirst()) {
                String table = cursor.getString(0);
                long rowId = cursor.getLong(1);
                throw new BackupImportException(
                        "Imported relationships are invalid in " + table + " row " + rowId
                );
            }
        }
    }

    private static long insert(SQLiteDatabase database, String table, ContentValues values) {
        return database.insertOrThrow(table, null, values);
    }

    private static void putOriginalId(
            ContentValues values,
            long originalId,
            BackupImportMode mode
    ) {
        if (mode == BackupImportMode.REPLACE) {
            values.put("id", originalId);
        }
    }

    private static long mappedId(Map<Long, Long> ids, long originalId, String relationship)
            throws BackupImportException {
        Long mappedId = ids.get(originalId);
        if (mappedId == null || mappedId <= 0) {
            throw new BackupImportException("Could not restore " + relationship + " relationship");
        }
        return mappedId;
    }

    private static int recordCount(NourishRxBackup backup) {
        return backup.profiles.size()
                + backup.medications.size()
                + backup.doseLogs.size()
                + backup.nutritionMeals.size()
                + backup.foods.size()
                + backup.mealFoodLogs.size()
                + backup.waterEntries.size()
                + backup.weightEntries.size()
                + backup.mealDefaults.size()
                + backup.savedMeals.size()
                + backup.savedMealItems.size();
    }
}
