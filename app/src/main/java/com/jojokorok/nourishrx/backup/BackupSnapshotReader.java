package com.jojokorok.nourishrx.backup;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class BackupSnapshotReader {
    private static final int MAX_AVATAR_BYTES = 10 * 1024 * 1024;

    private final Context context;
    private final MedicationStore store;

    public BackupSnapshotReader(Context context, MedicationStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    public NourishRxBackup read(long selectedProfileId, String appMode) throws IOException {
        NourishRxBackup backup = new NourishRxBackup();
        backup.metadata.exportedAtEpochMillis = System.currentTimeMillis();
        backup.metadata.appVersion = appVersion();
        backup.settings.selectedProfileId = selectedProfileId;
        backup.settings.appMode = "nutrition".equals(appMode) ? "nutrition" : "medication";

        SQLiteDatabase database = store.getReadableDatabase();
        database.beginTransaction();
        try {
            readProfiles(database, backup);
            readMedications(database, backup);
            readDoseLogs(database, backup);
            readNutritionMeals(database, backup);
            readFoods(database, backup);
            readMealFoodLogs(database, backup);
            readWaterEntries(database, backup);
            readWeightEntries(database, backup);
            readMealDefaults(database, backup);
            readSavedMeals(database, backup);
            readSavedMealItems(database, backup);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

        if (backup.settings.selectedProfileId <= 0 && !backup.profiles.isEmpty()) {
            backup.settings.selectedProfileId = backup.profiles.get(0).id;
        }
        return backup;
    }

    private void readProfiles(SQLiteDatabase database, NourishRxBackup backup) throws IOException {
        try (Cursor cursor = queryAll(database, "profiles")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.ProfileRecord record = new NourishRxBackup.ProfileRecord();
                record.id = longValue(cursor, "id");
                record.name = stringValue(cursor, "name");
                record.createdAtEpochMillis = longValue(cursor, "created_at");

                String avatarUri = stringValue(cursor, "avatar_uri");
                if (!avatarUri.isEmpty()) {
                    record.avatar = readAvatar(
                            avatarUri,
                            record.name,
                            floatValue(cursor, "avatar_zoom"),
                            floatValue(cursor, "avatar_offset_x"),
                            floatValue(cursor, "avatar_offset_y"),
                            floatValue(cursor, "avatar_aspect_ratio")
                    );
                }
                backup.profiles.add(record);
            }
        }
    }

    private void readMedications(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "medications")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.MedicationRecord record = new NourishRxBackup.MedicationRecord();
                record.id = longValue(cursor, "id");
                record.profileId = longValue(cursor, "profile_id");
                record.name = stringValue(cursor, "name");
                record.dosage = stringValue(cursor, "dosage");
                record.instructions = stringValue(cursor, "instructions");
                record.quantity = intValue(cursor, "quantity");
                record.refillThreshold = intValue(cursor, "refill_threshold");
                record.repeatReminderMinutes = intValue(cursor, "repeat_reminder_minutes");
                record.active = intValue(cursor, "active") == 1;
                record.createdAtEpochMillis = longValue(cursor, "created_at");
                Medication normalizedMedication = new Medication(
                        record.id,
                        record.profileId,
                        record.name,
                        record.dosage,
                        record.instructions,
                        intValue(cursor, "first_dose_minutes"),
                        intValue(cursor, "doses_per_day"),
                        Medication.parseDoseMinutes(stringValue(cursor, "dose_minutes")),
                        record.quantity,
                        record.refillThreshold,
                        record.repeatReminderMinutes,
                        record.active,
                        record.createdAtEpochMillis
                );
                record.doseMinutes.addAll(normalizedMedication.doseMinutes());
                backup.medications.add(record);
            }
        }
    }

    private void readDoseLogs(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "dose_logs")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.DoseLogRecord record = new NourishRxBackup.DoseLogRecord();
                record.id = longValue(cursor, "id");
                record.medicationId = longValue(cursor, "medication_id");
                record.scheduledAtEpochMillis = longValue(cursor, "scheduled_at");
                record.status = stringValue(cursor, "status");
                record.loggedAtEpochMillis = longValue(cursor, "logged_at");
                backup.doseLogs.add(record);
            }
        }
    }

    private void readNutritionMeals(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "nutrition_meals")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.NutritionMealRecord record = new NourishRxBackup.NutritionMealRecord();
                record.id = longValue(cursor, "id");
                record.profileId = longValue(cursor, "profile_id");
                record.name = stringValue(cursor, "name");
                record.calories = intValue(cursor, "calories");
                record.proteinGrams = floatValue(cursor, "protein_grams");
                record.carbsGrams = floatValue(cursor, "carbs_grams");
                record.fatGrams = floatValue(cursor, "fat_grams");
                record.loggedAtEpochMillis = longValue(cursor, "logged_at");
                backup.nutritionMeals.add(record);
            }
        }
    }

    private void readFoods(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "nutrition_foods")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.FoodRecord record = new NourishRxBackup.FoodRecord();
                record.id = longValue(cursor, "id");
                record.profileId = longValue(cursor, "profile_id");
                record.brand = stringValue(cursor, "brand");
                record.name = stringValue(cursor, "name");
                record.servingSize = stringValue(cursor, "serving_size");
                record.servingsPerContainer = floatValue(cursor, "servings_per_container");
                record.calories = intValue(cursor, "calories");
                record.totalFatGrams = floatValue(cursor, "total_fat_grams");
                record.saturatedFatGrams = floatValue(cursor, "saturated_fat_grams");
                record.transFatGrams = floatValue(cursor, "trans_fat_grams");
                record.cholesterolMg = floatValue(cursor, "cholesterol_mg");
                record.sodiumMg = floatValue(cursor, "sodium_mg");
                record.totalCarbsGrams = floatValue(cursor, "total_carbs_grams");
                record.fiberGrams = floatValue(cursor, "fiber_grams");
                record.totalSugarsGrams = floatValue(cursor, "total_sugars_grams");
                record.addedSugarsGrams = floatValue(cursor, "added_sugars_grams");
                record.proteinGrams = floatValue(cursor, "protein_grams");
                record.vitaminDMcg = floatValue(cursor, "vitamin_d_mcg");
                record.calciumMg = floatValue(cursor, "calcium_mg");
                record.ironMg = floatValue(cursor, "iron_mg");
                record.potassiumMg = floatValue(cursor, "potassium_mg");
                record.createdAtEpochMillis = longValue(cursor, "created_at");
                backup.foods.add(record);
            }
        }
    }

    private void readMealFoodLogs(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "meal_food_logs")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.MealFoodLogRecord record = new NourishRxBackup.MealFoodLogRecord();
                record.id = longValue(cursor, "id");
                record.profileId = longValue(cursor, "profile_id");
                record.foodId = longValue(cursor, "food_id");
                record.mealName = stringValue(cursor, "meal_name");
                record.servings = floatValue(cursor, "servings");
                record.eatenAtEpochMillis = longValue(cursor, "eaten_at");
                backup.mealFoodLogs.add(record);
            }
        }
    }

    private void readWaterEntries(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "water_entries")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.WaterEntryRecord record = new NourishRxBackup.WaterEntryRecord();
                record.id = longValue(cursor, "id");
                record.profileId = longValue(cursor, "profile_id");
                record.ounces = intValue(cursor, "ounces");
                record.loggedAtEpochMillis = longValue(cursor, "logged_at");
                backup.waterEntries.add(record);
            }
        }
    }

    private void readWeightEntries(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "weight_entries")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.WeightEntryRecord record = new NourishRxBackup.WeightEntryRecord();
                record.id = longValue(cursor, "id");
                record.profileId = longValue(cursor, "profile_id");
                record.pounds = floatValue(cursor, "pounds");
                record.loggedAtEpochMillis = longValue(cursor, "logged_at");
                backup.weightEntries.add(record);
            }
        }
    }

    private void readMealDefaults(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "meal_defaults")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.MealDefaultRecord record = new NourishRxBackup.MealDefaultRecord();
                record.id = longValue(cursor, "id");
                record.profileId = longValue(cursor, "profile_id");
                record.name = stringValue(cursor, "name");
                record.sortOrder = intValue(cursor, "sort_order");
                backup.mealDefaults.add(record);
            }
        }
    }

    private void readSavedMeals(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "saved_meals")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.SavedMealRecord record = new NourishRxBackup.SavedMealRecord();
                record.id = longValue(cursor, "id");
                record.profileId = longValue(cursor, "profile_id");
                record.name = stringValue(cursor, "name");
                record.notes = stringValue(cursor, "notes");
                record.createdAtEpochMillis = longValue(cursor, "created_at");
                backup.savedMeals.add(record);
            }
        }
    }

    private void readSavedMealItems(SQLiteDatabase database, NourishRxBackup backup) {
        try (Cursor cursor = queryAll(database, "saved_meal_items")) {
            while (cursor.moveToNext()) {
                NourishRxBackup.SavedMealItemRecord record = new NourishRxBackup.SavedMealItemRecord();
                record.id = longValue(cursor, "id");
                record.savedMealId = longValue(cursor, "saved_meal_id");
                record.foodId = longValue(cursor, "food_id");
                record.servings = floatValue(cursor, "servings");
                record.sortOrder = intValue(cursor, "sort_order");
                backup.savedMealItems.add(record);
            }
        }
    }

    private NourishRxBackup.AvatarRecord readAvatar(
            String avatarUri,
            String profileName,
            float zoom,
            float offsetX,
            float offsetY,
            float aspectRatio
    ) throws IOException {
        Uri uri = Uri.parse(avatarUri);
        NourishRxBackup.AvatarRecord avatar = new NourishRxBackup.AvatarRecord();
        String mimeType = context.getContentResolver().getType(uri);
        avatar.mimeType = mimeType == null || mimeType.trim().isEmpty() ? "image/*" : mimeType;
        avatar.base64Data = Base64.encodeToString(readAvatarBytes(uri, profileName), Base64.NO_WRAP);
        avatar.zoom = zoom;
        avatar.offsetX = offsetX;
        avatar.offsetY = offsetY;
        avatar.aspectRatio = aspectRatio;
        return avatar;
    }

    private byte[] readAvatarBytes(Uri uri, String profileName) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Profile photo could not be opened for " + profileName);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_AVATAR_BYTES) {
                    throw new IOException("Profile photo is too large for " + profileName);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (SecurityException exception) {
            throw new IOException("Profile photo access was lost for " + profileName, exception);
        }
    }

    private String appVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName == null ? "unknown" : packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "unknown";
        }
    }

    private static Cursor queryAll(SQLiteDatabase database, String table) {
        return database.query(table, null, null, null, null, null, "id ASC");
    }

    private static String stringValue(Cursor cursor, String column) {
        String value = cursor.getString(cursor.getColumnIndexOrThrow(column));
        return value == null ? "" : value;
    }

    private static long longValue(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column));
    }

    private static int intValue(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column));
    }

    private static float floatValue(Cursor cursor, String column) {
        return cursor.getFloat(cursor.getColumnIndexOrThrow(column));
    }
}
