package com.jojokorok.nourishrx.backup;

import java.util.ArrayList;
import java.util.List;

/**
 * Versioned, storage-independent representation of a NourishRx backup.
 *
 * <p>Database row identifiers are retained so relationships can be restored without ambiguity.
 * Premium purchase state is intentionally not part of the portable backup.</p>
 */
public final class NourishRxBackup {
    public static final String FORMAT_ID = "nourishrx-backup";
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int SOURCE_DATABASE_VERSION = 10;

    public String format = FORMAT_ID;
    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public Metadata metadata = new Metadata();
    public Settings settings = new Settings();
    public List<ProfileRecord> profiles = new ArrayList<>();
    public List<MedicationRecord> medications = new ArrayList<>();
    public List<DoseLogRecord> doseLogs = new ArrayList<>();
    public List<NutritionMealRecord> nutritionMeals = new ArrayList<>();
    public List<FoodRecord> foods = new ArrayList<>();
    public List<MealFoodLogRecord> mealFoodLogs = new ArrayList<>();
    public List<WaterEntryRecord> waterEntries = new ArrayList<>();
    public List<WeightEntryRecord> weightEntries = new ArrayList<>();
    public List<MealDefaultRecord> mealDefaults = new ArrayList<>();
    public List<SavedMealRecord> savedMeals = new ArrayList<>();
    public List<SavedMealItemRecord> savedMealItems = new ArrayList<>();

    public static final class Metadata {
        public long exportedAtEpochMillis;
        public String appVersion = "";
        public int sourceDatabaseVersion = SOURCE_DATABASE_VERSION;
    }

    public static final class Settings {
        public long selectedProfileId;
        public String appMode = "medication";
    }

    public static final class ProfileRecord {
        public long id;
        public String name = "";
        public long createdAtEpochMillis;
        public AvatarRecord avatar;
    }

    /** Image bytes make avatars portable across devices; content URIs do not. */
    public static final class AvatarRecord {
        public String mimeType = "";
        public String base64Data = "";
        public float zoom = 1.0f;
        public float offsetX;
        public float offsetY;
        public float aspectRatio = 1.0f;
    }

    public static final class MedicationRecord {
        public long id;
        public long profileId;
        public String name = "";
        public String dosage = "";
        public String instructions = "";
        public List<Integer> doseMinutes = new ArrayList<>();
        public int quantity;
        public int refillThreshold;
        public int repeatReminderMinutes;
        public boolean active;
        public long createdAtEpochMillis;
    }

    public static final class DoseLogRecord {
        public long id;
        public long medicationId;
        public long scheduledAtEpochMillis;
        public String status = "";
        public long loggedAtEpochMillis;
    }

    public static final class NutritionMealRecord {
        public long id;
        public long profileId;
        public String name = "";
        public int calories;
        public float proteinGrams;
        public float carbsGrams;
        public float fatGrams;
        public long loggedAtEpochMillis;
    }

    public static final class FoodRecord {
        public long id;
        public long profileId;
        public String brand = "";
        public String name = "";
        public String servingSize = "";
        public float servingsPerContainer;
        public int calories;
        public float totalFatGrams;
        public float saturatedFatGrams;
        public float transFatGrams;
        public float cholesterolMg;
        public float sodiumMg;
        public float totalCarbsGrams;
        public float fiberGrams;
        public float totalSugarsGrams;
        public float addedSugarsGrams;
        public float proteinGrams;
        public float vitaminDMcg;
        public float calciumMg;
        public float ironMg;
        public float potassiumMg;
        public long createdAtEpochMillis;
    }

    public static final class MealFoodLogRecord {
        public long id;
        public long profileId;
        public long foodId;
        public String mealName = "";
        public float servings;
        public long eatenAtEpochMillis;
    }

    public static final class WaterEntryRecord {
        public long id;
        public long profileId;
        public int ounces;
        public long loggedAtEpochMillis;
    }

    public static final class WeightEntryRecord {
        public long id;
        public long profileId;
        public float pounds;
        public long loggedAtEpochMillis;
    }

    public static final class MealDefaultRecord {
        public long id;
        public long profileId;
        public String name = "";
        public int sortOrder;
    }

    public static final class SavedMealRecord {
        public long id;
        public long profileId;
        public String name = "";
        public String notes = "";
        public long createdAtEpochMillis;
    }

    public static final class SavedMealItemRecord {
        public long id;
        public long savedMealId;
        public long foodId;
        public float servings;
        public int sortOrder;
    }
}
