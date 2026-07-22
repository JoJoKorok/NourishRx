package com.jojokorok.nourishrx.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MedicationStore extends SQLiteOpenHelper {
    public static final String STATUS_TAKEN = "taken";
    public static final String STATUS_SKIPPED = "skipped";

    private static final String DATABASE_NAME = "medication_manager.db";
    private static final int DATABASE_VERSION = 8;

    private static final String TABLE_PROFILES = "profiles";
    private static final String TABLE_MEDICATIONS = "medications";
    private static final String TABLE_DOSE_LOGS = "dose_logs";
    private static final String TABLE_NUTRITION_MEALS = "nutrition_meals";
    private static final String TABLE_NUTRITION_FOODS = "nutrition_foods";
    private static final String TABLE_MEAL_FOOD_LOGS = "meal_food_logs";
    private static final String TABLE_WATER_ENTRIES = "water_entries";
    private static final String TABLE_WEIGHT_ENTRIES = "weight_entries";
    private static final String TABLE_MEAL_DEFAULTS = "meal_defaults";

    public MedicationStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createProfilesTable(db);
        long defaultProfileId = ensureDefaultProfile(db);

        db.execSQL("CREATE TABLE " + TABLE_MEDICATIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL DEFAULT " + defaultProfileId + ", " +
                "name TEXT NOT NULL, " +
                "dosage TEXT NOT NULL, " +
                "instructions TEXT NOT NULL DEFAULT '', " +
                "first_dose_minutes INTEGER NOT NULL, " +
                "doses_per_day INTEGER NOT NULL, " +
                "dose_minutes TEXT NOT NULL DEFAULT '', " +
                "quantity INTEGER NOT NULL, " +
                "refill_threshold INTEGER NOT NULL, " +
                "active INTEGER NOT NULL DEFAULT 1, " +
                "created_at INTEGER NOT NULL, " +
                "FOREIGN KEY(profile_id) REFERENCES " + TABLE_PROFILES + "(id) ON DELETE CASCADE" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_DOSE_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "medication_id INTEGER NOT NULL, " +
                "scheduled_at INTEGER NOT NULL, " +
                "status TEXT NOT NULL, " +
                "logged_at INTEGER NOT NULL, " +
                "FOREIGN KEY(medication_id) REFERENCES " + TABLE_MEDICATIONS + "(id) ON DELETE CASCADE" +
                ")");

        db.execSQL("CREATE UNIQUE INDEX dose_logs_unique_schedule ON " +
                TABLE_DOSE_LOGS + "(medication_id, scheduled_at)");
        db.execSQL("CREATE INDEX dose_logs_scheduled_at ON " +
                TABLE_DOSE_LOGS + "(scheduled_at)");

        createNutritionTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createProfilesTable(db);
            long defaultProfileId = ensureDefaultProfile(db);
            if (!columnExists(db, TABLE_MEDICATIONS, "profile_id")) {
                db.execSQL("ALTER TABLE " + TABLE_MEDICATIONS +
                        " ADD COLUMN profile_id INTEGER NOT NULL DEFAULT " + defaultProfileId);
            }
        }
        if (oldVersion < 3 && !columnExists(db, TABLE_PROFILES, "avatar_uri")) {
            db.execSQL("ALTER TABLE " + TABLE_PROFILES +
                    " ADD COLUMN avatar_uri TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 4) {
            addProfileColumnIfMissing(db, "avatar_zoom", "REAL NOT NULL DEFAULT 1.0");
            addProfileColumnIfMissing(db, "avatar_offset_x", "REAL NOT NULL DEFAULT 0.0");
            addProfileColumnIfMissing(db, "avatar_offset_y", "REAL NOT NULL DEFAULT 0.0");
            addProfileColumnIfMissing(db, "avatar_aspect_ratio", "REAL NOT NULL DEFAULT 1.0");
        }
        if (oldVersion < 5) {
            addMedicationColumnIfMissing(db, "dose_minutes", "TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 6) {
            createNutritionTables(db);
        }
        if (oldVersion < 7) {
            createNutritionTables(db);
        }
        if (oldVersion < 8) {
            createNutritionTables(db);
        }
    }

    public long ensureDefaultProfile() {
        return ensureDefaultProfile(getWritableDatabase());
    }

    public Profile getProfile(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_PROFILES,
                null,
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return profileFrom(cursor);
            }
        }
        return null;
    }

    public List<Profile> getProfiles() {
        SQLiteDatabase db = getReadableDatabase();
        List<Profile> profiles = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_PROFILES,
                null,
                null,
                null,
                null,
                null,
                "created_at ASC, name COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                profiles.add(profileFrom(cursor));
            }
        }
        if (profiles.isEmpty()) {
            long id = ensureDefaultProfile();
            Profile profile = getProfile(id);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    public long saveProfile(String name) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            cleanName = "Me";
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", cleanName);
        values.put("avatar_uri", "");
        values.put("avatar_zoom", 1.0f);
        values.put("avatar_offset_x", 0.0f);
        values.put("avatar_offset_y", 0.0f);
        values.put("avatar_aspect_ratio", 1.0f);
        values.put("created_at", System.currentTimeMillis());
        return db.insert(TABLE_PROFILES, null, values);
    }

    public void renameProfile(long profileId, String name) {
        String cleanName = name == null ? "" : name.trim();
        if (profileId <= 0 || cleanName.isEmpty()) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", cleanName);
        db.update(TABLE_PROFILES, values, "id = ?", new String[]{String.valueOf(profileId)});
    }

    public void updateProfileAvatar(
            long profileId,
            String avatarUri,
            float zoom,
            float offsetX,
            float offsetY,
            float aspectRatio
    ) {
        if (profileId <= 0) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("avatar_uri", avatarUri == null ? "" : avatarUri.trim());
        values.put("avatar_zoom", clamp(zoom, 1.0f, 3.0f, 1.0f));
        values.put("avatar_offset_x", clamp(offsetX, -1.0f, 1.0f, 0.0f));
        values.put("avatar_offset_y", clamp(offsetY, -1.0f, 1.0f, 0.0f));
        values.put("avatar_aspect_ratio", clamp(aspectRatio, 0.75f, 1.65f, 1.0f));
        db.update(TABLE_PROFILES, values, "id = ?", new String[]{String.valueOf(profileId)});
    }

    public void clearProfileAvatar(long profileId) {
        updateProfileAvatar(profileId, "", 1.0f, 0.0f, 0.0f, 1.0f);
    }

    public boolean deleteProfile(long profileId) {
        if (profileId <= 0 || getProfiles().size() <= 1) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = new String[]{String.valueOf(profileId)};
            db.delete(
                    TABLE_DOSE_LOGS,
                    "medication_id IN (SELECT id FROM " + TABLE_MEDICATIONS + " WHERE profile_id = ?)",
                    args
            );
            db.delete(TABLE_MEDICATIONS, "profile_id = ?", args);
            db.delete(TABLE_NUTRITION_MEALS, "profile_id = ?", args);
            db.delete(TABLE_MEAL_FOOD_LOGS, "profile_id = ?", args);
            db.delete(TABLE_NUTRITION_FOODS, "profile_id = ?", args);
            db.delete(TABLE_WATER_ENTRIES, "profile_id = ?", args);
            db.delete(TABLE_WEIGHT_ENTRIES, "profile_id = ?", args);
            db.delete(TABLE_MEAL_DEFAULTS, "profile_id = ?", args);
            int deleted = db.delete(TABLE_PROFILES, "id = ?", args);
            db.setTransactionSuccessful();
            return deleted > 0;
        } finally {
            db.endTransaction();
        }
    }

    public int getMedicationCountForProfile(long profileId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) AS count FROM " + TABLE_MEDICATIONS + " WHERE profile_id = ?",
                new String[]{String.valueOf(profileId)}
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow("count"));
            }
        }
        return 0;
    }

    public long saveMedication(Medication medication) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = medicationValues(medication);
        if (medication.id > 0) {
            db.update(TABLE_MEDICATIONS, values, "id = ?", new String[]{String.valueOf(medication.id)});
            return medication.id;
        }
        long id = db.insert(TABLE_MEDICATIONS, null, values);
        medication.id = id;
        return id;
    }

    public Medication getMedication(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_MEDICATIONS,
                null,
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return medicationFrom(cursor);
            }
        }
        return null;
    }

    public List<Medication> getAllMedications() {
        return getMedications(null, null, "active DESC, name COLLATE NOCASE ASC");
    }

    public List<Medication> getAllMedications(long profileId) {
        return getMedications(
                "profile_id = ?",
                new String[]{String.valueOf(profileId)},
                "active DESC, name COLLATE NOCASE ASC"
        );
    }

    public List<Medication> getActiveMedications() {
        return getMedications("active = 1", null, "name COLLATE NOCASE ASC");
    }

    public List<Medication> getActiveMedications(long profileId) {
        return getMedications(
                "active = 1 AND profile_id = ?",
                new String[]{String.valueOf(profileId)},
                "name COLLATE NOCASE ASC"
        );
    }

    public void deleteMedication(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_DOSE_LOGS, "medication_id = ?", new String[]{String.valueOf(id)});
        db.delete(TABLE_MEDICATIONS, "id = ?", new String[]{String.valueOf(id)});
    }

    public void adjustInventory(long medicationId, int delta) {
        Medication medication = getMedication(medicationId);
        if (medication == null) {
            return;
        }
        medication.quantity = Math.max(0, medication.quantity + delta);
        saveMedication(medication);
    }

    public List<NutritionMeal> getNutritionMeals(long profileId, long startMillis, long endMillis) {
        SQLiteDatabase db = getReadableDatabase();
        List<NutritionMeal> meals = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_NUTRITION_MEALS,
                null,
                "profile_id = ? AND logged_at >= ? AND logged_at < ?",
                new String[]{
                        String.valueOf(profileId),
                        String.valueOf(startMillis),
                        String.valueOf(endMillis)
                },
                null,
                null,
                "logged_at DESC, id DESC"
        )) {
            while (cursor.moveToNext()) {
                meals.add(nutritionMealFrom(cursor));
            }
        }
        return meals;
    }

    public long saveNutritionMeal(NutritionMeal meal) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("profile_id", meal.profileId > 0 ? meal.profileId : ensureDefaultProfile());
        values.put("name", meal.name);
        values.put("calories", meal.calories);
        values.put("protein_grams", meal.proteinGrams);
        values.put("carbs_grams", meal.carbsGrams);
        values.put("fat_grams", meal.fatGrams);
        values.put("logged_at", meal.loggedAt);
        if (meal.id > 0) {
            db.update(TABLE_NUTRITION_MEALS, values, "id = ?", new String[]{String.valueOf(meal.id)});
            return meal.id;
        }
        long id = db.insert(TABLE_NUTRITION_MEALS, null, values);
        meal.id = id;
        return id;
    }

    public void deleteNutritionMeal(long mealId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NUTRITION_MEALS, "id = ?", new String[]{String.valueOf(mealId)});
    }

    public List<NutritionFood> getNutritionFoods(long profileId) {
        SQLiteDatabase db = getReadableDatabase();
        List<NutritionFood> foods = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_NUTRITION_FOODS,
                null,
                "profile_id = ?",
                new String[]{String.valueOf(profileId)},
                null,
                null,
                "brand COLLATE NOCASE ASC, name COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                foods.add(nutritionFoodFrom(cursor));
            }
        }
        return foods;
    }

    public NutritionFood getNutritionFood(long foodId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_NUTRITION_FOODS,
                null,
                "id = ?",
                new String[]{String.valueOf(foodId)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return nutritionFoodFrom(cursor);
            }
        }
        return null;
    }

    public long saveNutritionFood(NutritionFood food) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = nutritionFoodValues(food);
        if (food.id > 0) {
            db.update(TABLE_NUTRITION_FOODS, values, "id = ?", new String[]{String.valueOf(food.id)});
            return food.id;
        }
        long id = db.insert(TABLE_NUTRITION_FOODS, null, values);
        food.id = id;
        return id;
    }

    public void deleteNutritionFood(long foodId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = new String[]{String.valueOf(foodId)};
            db.delete(TABLE_MEAL_FOOD_LOGS, "food_id = ?", args);
            db.delete(TABLE_NUTRITION_FOODS, "id = ?", args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<MealFoodLog> getMealFoodLogs(long profileId, long startMillis, long endMillis) {
        SQLiteDatabase db = getReadableDatabase();
        List<MealFoodLog> logs = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_MEAL_FOOD_LOGS,
                null,
                "profile_id = ? AND eaten_at >= ? AND eaten_at < ?",
                new String[]{
                        String.valueOf(profileId),
                        String.valueOf(startMillis),
                        String.valueOf(endMillis)
                },
                null,
                null,
                "eaten_at DESC, id DESC"
        )) {
            while (cursor.moveToNext()) {
                long foodId = cursor.getLong(cursor.getColumnIndexOrThrow("food_id"));
                NutritionFood food = getNutritionFood(foodId);
                if (food != null) {
                    logs.add(mealFoodLogFrom(cursor, food));
                }
            }
        }
        return logs;
    }

    public long saveMealFoodLog(MealFoodLog log) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("profile_id", log.profileId > 0 ? log.profileId : ensureDefaultProfile());
        values.put("food_id", log.foodId);
        values.put("meal_name", log.mealName);
        values.put("servings", log.servings);
        values.put("eaten_at", log.eatenAt);
        if (log.id > 0) {
            db.update(TABLE_MEAL_FOOD_LOGS, values, "id = ?", new String[]{String.valueOf(log.id)});
            return log.id;
        }
        long id = db.insert(TABLE_MEAL_FOOD_LOGS, null, values);
        log.id = id;
        return id;
    }

    public void deleteMealFoodLog(long logId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MEAL_FOOD_LOGS, "id = ?", new String[]{String.valueOf(logId)});
    }

    public List<String> getMealDefaults(long profileId) {
        SQLiteDatabase db = getReadableDatabase();
        List<String> names = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_MEAL_DEFAULTS,
                new String[]{"name"},
                "profile_id = ?",
                new String[]{String.valueOf(profileId)},
                null,
                null,
                "sort_order ASC, id ASC"
        )) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name.trim());
                }
            }
        }

        if (names.isEmpty()) {
            names.add("Breakfast");
            names.add("Lunch");
            names.add("Dinner");
            saveMealDefaults(profileId, names);
        }
        return names;
    }

    public void saveMealDefaults(long profileId, List<String> names) {
        SQLiteDatabase db = getWritableDatabase();
        long safeProfileId = profileId > 0 ? profileId : ensureDefaultProfile();
        List<String> cleanNames = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                String clean = name == null ? "" : name.trim();
                if (!clean.isEmpty() && !cleanNames.contains(clean)) {
                    cleanNames.add(clean);
                }
            }
        }
        if (cleanNames.isEmpty()) {
            cleanNames.add("Meal 1");
        }

        db.beginTransaction();
        try {
            db.delete(TABLE_MEAL_DEFAULTS, "profile_id = ?", new String[]{String.valueOf(safeProfileId)});
            for (int i = 0; i < cleanNames.size(); i++) {
                ContentValues values = new ContentValues();
                values.put("profile_id", safeProfileId);
                values.put("name", cleanNames.get(i));
                values.put("sort_order", i);
                db.insert(TABLE_MEAL_DEFAULTS, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void addWater(long profileId, int ounces) {
        if (ounces <= 0) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("profile_id", profileId > 0 ? profileId : ensureDefaultProfile());
        values.put("ounces", ounces);
        values.put("logged_at", System.currentTimeMillis());
        db.insert(TABLE_WATER_ENTRIES, null, values);
    }

    public int getWaterOunces(long profileId, long startMillis, long endMillis) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(ounces), 0) AS total FROM " + TABLE_WATER_ENTRIES +
                        " WHERE profile_id = ? AND logged_at >= ? AND logged_at < ?",
                new String[]{
                        String.valueOf(profileId),
                        String.valueOf(startMillis),
                        String.valueOf(endMillis)
                }
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow("total"));
            }
        }
        return 0;
    }

    public void clearWater(long profileId, long startMillis, long endMillis) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(
                TABLE_WATER_ENTRIES,
                "profile_id = ? AND logged_at >= ? AND logged_at < ?",
                new String[]{
                        String.valueOf(profileId),
                        String.valueOf(startMillis),
                        String.valueOf(endMillis)
                }
        );
    }

    public long saveWeightEntry(WeightEntry entry) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("profile_id", entry.profileId > 0 ? entry.profileId : ensureDefaultProfile());
        values.put("pounds", entry.pounds);
        values.put("logged_at", entry.loggedAt);
        if (entry.id > 0) {
            db.update(TABLE_WEIGHT_ENTRIES, values, "id = ?", new String[]{String.valueOf(entry.id)});
            return entry.id;
        }
        long id = db.insert(TABLE_WEIGHT_ENTRIES, null, values);
        entry.id = id;
        return id;
    }

    public List<WeightEntry> getWeightEntries(long profileId, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<WeightEntry> entries = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_WEIGHT_ENTRIES,
                null,
                "profile_id = ?",
                new String[]{String.valueOf(profileId)},
                null,
                null,
                "logged_at DESC, id DESC",
                String.valueOf(Math.max(1, limit))
        )) {
            while (cursor.moveToNext()) {
                entries.add(weightEntryFrom(cursor));
            }
        }
        return entries;
    }

    public void deleteWeightEntry(long entryId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_WEIGHT_ENTRIES, "id = ?", new String[]{String.valueOf(entryId)});
    }

    public void logDose(long medicationId, long scheduledAt, String status) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("medication_id", medicationId);
        values.put("scheduled_at", scheduledAt);
        values.put("status", status);
        values.put("logged_at", System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_DOSE_LOGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getDoseStatus(long medicationId, long scheduledAt) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_DOSE_LOGS,
                new String[]{"status"},
                "medication_id = ? AND scheduled_at = ?",
                new String[]{String.valueOf(medicationId), String.valueOf(scheduledAt)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("status"));
            }
        }
        return null;
    }

    public Map<String, String> getDoseLogsBetween(long startInclusive, long endExclusive) {
        SQLiteDatabase db = getReadableDatabase();
        Map<String, String> logs = new LinkedHashMap<>();
        try (Cursor cursor = db.query(
                TABLE_DOSE_LOGS,
                new String[]{"medication_id", "scheduled_at", "status"},
                "scheduled_at >= ? AND scheduled_at < ?",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)},
                null,
                null,
                "scheduled_at ASC"
        )) {
            while (cursor.moveToNext()) {
                long medicationId = cursor.getLong(cursor.getColumnIndexOrThrow("medication_id"));
                long scheduledAt = cursor.getLong(cursor.getColumnIndexOrThrow("scheduled_at"));
                String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                logs.put(doseKey(medicationId, scheduledAt), status);
            }
        }
        return logs;
    }

    public static String doseKey(long medicationId, long scheduledAt) {
        return medicationId + ":" + scheduledAt;
    }

    private List<Medication> getMedications(String selection, String[] selectionArgs, String orderBy) {
        SQLiteDatabase db = getReadableDatabase();
        List<Medication> medications = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_MEDICATIONS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                orderBy
        )) {
            while (cursor.moveToNext()) {
                medications.add(medicationFrom(cursor));
            }
        }
        return medications;
    }

    private ContentValues medicationValues(Medication medication) {
        ContentValues values = new ContentValues();
        values.put("profile_id", medication.profileId > 0 ? medication.profileId : ensureDefaultProfile());
        values.put("name", medication.name);
        values.put("dosage", medication.dosage);
        values.put("instructions", medication.instructions);
        values.put("first_dose_minutes", medication.firstDoseMinutes);
        values.put("doses_per_day", medication.dosesPerDay);
        values.put("dose_minutes", Medication.serializeDoseMinutes(medication.doseMinutes()));
        values.put("quantity", medication.quantity);
        values.put("refill_threshold", medication.refillThreshold);
        values.put("active", medication.active ? 1 : 0);
        values.put("created_at", medication.createdAt);
        return values;
    }

    private ContentValues nutritionFoodValues(NutritionFood food) {
        ContentValues values = new ContentValues();
        values.put("profile_id", food.profileId > 0 ? food.profileId : ensureDefaultProfile());
        values.put("brand", food.brand);
        values.put("name", food.name);
        values.put("serving_size", food.servingSize);
        values.put("servings_per_container", food.servingsPerContainer);
        values.put("calories", food.calories);
        values.put("total_fat_grams", food.totalFatGrams);
        values.put("saturated_fat_grams", food.saturatedFatGrams);
        values.put("trans_fat_grams", food.transFatGrams);
        values.put("cholesterol_mg", food.cholesterolMg);
        values.put("sodium_mg", food.sodiumMg);
        values.put("total_carbs_grams", food.totalCarbsGrams);
        values.put("fiber_grams", food.fiberGrams);
        values.put("total_sugars_grams", food.totalSugarsGrams);
        values.put("added_sugars_grams", food.addedSugarsGrams);
        values.put("protein_grams", food.proteinGrams);
        values.put("vitamin_d_mcg", food.vitaminDMcg);
        values.put("calcium_mg", food.calciumMg);
        values.put("iron_mg", food.ironMg);
        values.put("potassium_mg", food.potassiumMg);
        values.put("created_at", food.createdAt);
        return values;
    }

    private void createProfilesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PROFILES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "avatar_uri TEXT NOT NULL DEFAULT '', " +
                "avatar_zoom REAL NOT NULL DEFAULT 1.0, " +
                "avatar_offset_x REAL NOT NULL DEFAULT 0.0, " +
                "avatar_offset_y REAL NOT NULL DEFAULT 0.0, " +
                "avatar_aspect_ratio REAL NOT NULL DEFAULT 1.0, " +
                "created_at INTEGER NOT NULL" +
                ")");
    }

    private void createNutritionTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NUTRITION_MEALS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "calories INTEGER NOT NULL DEFAULT 0, " +
                "protein_grams REAL NOT NULL DEFAULT 0.0, " +
                "carbs_grams REAL NOT NULL DEFAULT 0.0, " +
                "fat_grams REAL NOT NULL DEFAULT 0.0, " +
                "logged_at INTEGER NOT NULL, " +
                "FOREIGN KEY(profile_id) REFERENCES " + TABLE_PROFILES + "(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_meals_profile_day ON " +
                TABLE_NUTRITION_MEALS + "(profile_id, logged_at)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NUTRITION_FOODS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL, " +
                "brand TEXT NOT NULL DEFAULT '', " +
                "name TEXT NOT NULL, " +
                "serving_size TEXT NOT NULL DEFAULT '', " +
                "servings_per_container REAL NOT NULL DEFAULT 0.0, " +
                "calories INTEGER NOT NULL DEFAULT 0, " +
                "total_fat_grams REAL NOT NULL DEFAULT 0.0, " +
                "saturated_fat_grams REAL NOT NULL DEFAULT 0.0, " +
                "trans_fat_grams REAL NOT NULL DEFAULT 0.0, " +
                "cholesterol_mg REAL NOT NULL DEFAULT 0.0, " +
                "sodium_mg REAL NOT NULL DEFAULT 0.0, " +
                "total_carbs_grams REAL NOT NULL DEFAULT 0.0, " +
                "fiber_grams REAL NOT NULL DEFAULT 0.0, " +
                "total_sugars_grams REAL NOT NULL DEFAULT 0.0, " +
                "added_sugars_grams REAL NOT NULL DEFAULT 0.0, " +
                "protein_grams REAL NOT NULL DEFAULT 0.0, " +
                "vitamin_d_mcg REAL NOT NULL DEFAULT 0.0, " +
                "calcium_mg REAL NOT NULL DEFAULT 0.0, " +
                "iron_mg REAL NOT NULL DEFAULT 0.0, " +
                "potassium_mg REAL NOT NULL DEFAULT 0.0, " +
                "created_at INTEGER NOT NULL, " +
                "FOREIGN KEY(profile_id) REFERENCES " + TABLE_PROFILES + "(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_profile_name ON " +
                TABLE_NUTRITION_FOODS + "(profile_id, brand, name)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MEAL_FOOD_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL, " +
                "food_id INTEGER NOT NULL, " +
                "meal_name TEXT NOT NULL, " +
                "servings REAL NOT NULL DEFAULT 1.0, " +
                "eaten_at INTEGER NOT NULL, " +
                "FOREIGN KEY(profile_id) REFERENCES " + TABLE_PROFILES + "(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(food_id) REFERENCES " + TABLE_NUTRITION_FOODS + "(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_food_logs_profile_day ON " +
                TABLE_MEAL_FOOD_LOGS + "(profile_id, eaten_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_food_logs_food ON " +
                TABLE_MEAL_FOOD_LOGS + "(food_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_WATER_ENTRIES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL, " +
                "ounces INTEGER NOT NULL DEFAULT 0, " +
                "logged_at INTEGER NOT NULL, " +
                "FOREIGN KEY(profile_id) REFERENCES " + TABLE_PROFILES + "(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS water_entries_profile_day ON " +
                TABLE_WATER_ENTRIES + "(profile_id, logged_at)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_WEIGHT_ENTRIES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL, " +
                "pounds REAL NOT NULL DEFAULT 0.0, " +
                "logged_at INTEGER NOT NULL, " +
                "FOREIGN KEY(profile_id) REFERENCES " + TABLE_PROFILES + "(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS weight_entries_profile_day ON " +
                TABLE_WEIGHT_ENTRIES + "(profile_id, logged_at)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MEAL_DEFAULTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(profile_id) REFERENCES " + TABLE_PROFILES + "(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_defaults_profile_order ON " +
                TABLE_MEAL_DEFAULTS + "(profile_id, sort_order)");
    }

    private long ensureDefaultProfile(SQLiteDatabase db) {
        try (Cursor cursor = db.query(
                TABLE_PROFILES,
                new String[]{"id"},
                null,
                null,
                null,
                null,
                "created_at ASC, id ASC",
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            }
        }

        ContentValues values = new ContentValues();
        values.put("name", "Me");
        values.put("avatar_uri", "");
        values.put("avatar_zoom", 1.0f);
        values.put("avatar_offset_x", 0.0f);
        values.put("avatar_offset_y", 0.0f);
        values.put("avatar_aspect_ratio", 1.0f);
        values.put("created_at", System.currentTimeMillis());
        return db.insert(TABLE_PROFILES, null, values);
    }

    private void addProfileColumnIfMissing(SQLiteDatabase db, String columnName, String definition) {
        if (!columnExists(db, TABLE_PROFILES, columnName)) {
            db.execSQL("ALTER TABLE " + TABLE_PROFILES + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private void addMedicationColumnIfMissing(SQLiteDatabase db, String columnName, String definition) {
        if (!columnExists(db, TABLE_MEDICATIONS, columnName)) {
            db.execSQL("ALTER TABLE " + TABLE_MEDICATIONS + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean columnExists(SQLiteDatabase db, String tableName, String columnName) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            while (cursor.moveToNext()) {
                String existing = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if (columnName.equals(existing)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Profile profileFrom(Cursor cursor) {
        return new Profile(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                stringValue(cursor, "avatar_uri", ""),
                floatValue(cursor, "avatar_zoom", 1.0f),
                floatValue(cursor, "avatar_offset_x", 0.0f),
                floatValue(cursor, "avatar_offset_y", 0.0f),
                floatValue(cursor, "avatar_aspect_ratio", 1.0f),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        );
    }

    private String stringValue(Cursor cursor, String columnName, String fallback) {
        int index = cursor.getColumnIndex(columnName);
        if (index < 0) {
            return fallback;
        }
        String value = cursor.getString(index);
        return value == null ? fallback : value;
    }

    private float floatValue(Cursor cursor, String columnName, float fallback) {
        int index = cursor.getColumnIndex(columnName);
        if (index < 0) {
            return fallback;
        }
        return cursor.getFloat(index);
    }

    private float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0 && min > 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private Medication medicationFrom(Cursor cursor) {
        return new Medication(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("profile_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("dosage")),
                cursor.getString(cursor.getColumnIndexOrThrow("instructions")),
                cursor.getInt(cursor.getColumnIndexOrThrow("first_dose_minutes")),
                cursor.getInt(cursor.getColumnIndexOrThrow("doses_per_day")),
                Medication.parseDoseMinutes(stringValue(cursor, "dose_minutes", "")),
                cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                cursor.getInt(cursor.getColumnIndexOrThrow("refill_threshold")),
                cursor.getInt(cursor.getColumnIndexOrThrow("active")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        );
    }

    private NutritionMeal nutritionMealFrom(Cursor cursor) {
        return new NutritionMeal(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("profile_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getInt(cursor.getColumnIndexOrThrow("calories")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("protein_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("carbs_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("fat_grams")),
                cursor.getLong(cursor.getColumnIndexOrThrow("logged_at"))
        );
    }

    private NutritionFood nutritionFoodFrom(Cursor cursor) {
        return new NutritionFood(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("profile_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("brand")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("serving_size")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("servings_per_container")),
                cursor.getInt(cursor.getColumnIndexOrThrow("calories")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("total_fat_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("saturated_fat_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("trans_fat_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("cholesterol_mg")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("sodium_mg")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("total_carbs_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("fiber_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("total_sugars_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("added_sugars_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("protein_grams")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("vitamin_d_mcg")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("calcium_mg")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("iron_mg")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("potassium_mg")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        );
    }

    private MealFoodLog mealFoodLogFrom(Cursor cursor, NutritionFood food) {
        return new MealFoodLog(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("profile_id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("food_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("meal_name")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("servings")),
                cursor.getLong(cursor.getColumnIndexOrThrow("eaten_at")),
                food
        );
    }

    private WeightEntry weightEntryFrom(Cursor cursor) {
        return new WeightEntry(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("profile_id")),
                cursor.getFloat(cursor.getColumnIndexOrThrow("pounds")),
                cursor.getLong(cursor.getColumnIndexOrThrow("logged_at"))
        );
    }
}
