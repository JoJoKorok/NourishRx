package com.jojokorok.nourishrx.backup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class BackupValidator {
    private BackupValidator() {
    }

    public static BackupValidationResult validate(NourishRxBackup backup) {
        List<String> errors = new ArrayList<>();
        if (backup == null) {
            errors.add("Backup is missing");
            return new BackupValidationResult(errors);
        }

        if (!NourishRxBackup.FORMAT_ID.equals(backup.format)) {
            errors.add("Unsupported backup format");
        }
        if (backup.schemaVersion != NourishRxBackup.CURRENT_SCHEMA_VERSION) {
            errors.add("Unsupported schema version: " + backup.schemaVersion);
        }
        if (backup.metadata == null) {
            errors.add("Backup metadata is missing");
        } else {
            positive(backup.metadata.exportedAtEpochMillis, "metadata.exportedAtEpochMillis", errors);
            requiredText(backup.metadata.appVersion, "metadata.appVersion", errors);
            positive(backup.metadata.sourceDatabaseVersion, "metadata.sourceDatabaseVersion", errors);
        }
        if (backup.settings == null) {
            errors.add("Backup settings are missing");
        } else if (!"medication".equals(backup.settings.appMode)
                && !"nutrition".equals(backup.settings.appMode)) {
            errors.add("settings.appMode must be medication or nutrition");
        }

        Set<Long> profileIds = ids(backup.profiles, item -> item.id, "profiles", errors);
        Set<Long> medicationIds = ids(backup.medications, item -> item.id, "medications", errors);
        Set<Long> foodIds = ids(backup.foods, item -> item.id, "foods", errors);
        Set<Long> savedMealIds = ids(backup.savedMeals, item -> item.id, "savedMeals", errors);
        ids(backup.doseLogs, item -> item.id, "doseLogs", errors);
        ids(backup.nutritionMeals, item -> item.id, "nutritionMeals", errors);
        ids(backup.mealFoodLogs, item -> item.id, "mealFoodLogs", errors);
        ids(backup.waterEntries, item -> item.id, "waterEntries", errors);
        ids(backup.weightEntries, item -> item.id, "weightEntries", errors);
        ids(backup.mealDefaults, item -> item.id, "mealDefaults", errors);
        ids(backup.savedMealItems, item -> item.id, "savedMealItems", errors);

        if (backup.settings != null && backup.settings.selectedProfileId > 0
                && !profileIds.contains(backup.settings.selectedProfileId)) {
            errors.add("settings.selectedProfileId does not reference a profile");
        }

        validateProfiles(backup.profiles, errors);
        validateMedications(backup.medications, profileIds, errors);
        validateDoseLogs(backup.doseLogs, medicationIds, errors);
        validateNutritionMeals(backup.nutritionMeals, profileIds, errors);
        validateFoods(backup.foods, profileIds, errors);
        validateMealFoodLogs(backup.mealFoodLogs, profileIds, foodIds, errors);
        validateWaterEntries(backup.waterEntries, profileIds, errors);
        validateWeightEntries(backup.weightEntries, profileIds, errors);
        validateMealDefaults(backup.mealDefaults, profileIds, errors);
        validateSavedMeals(backup.savedMeals, profileIds, errors);
        validateSavedMealItems(backup.savedMealItems, savedMealIds, foodIds, errors);
        return new BackupValidationResult(errors);
    }

    private static void validateProfiles(List<NourishRxBackup.ProfileRecord> records, List<String> errors) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.ProfileRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "profiles[" + i + "]";
            requiredText(record.name, path + ".name", errors);
            positive(record.createdAtEpochMillis, path + ".createdAtEpochMillis", errors);
            if (record.avatar != null) {
                requiredText(record.avatar.mimeType, path + ".avatar.mimeType", errors);
                requiredText(record.avatar.base64Data, path + ".avatar.base64Data", errors);
                finiteInRange(record.avatar.zoom, 1.0f, 3.0f, path + ".avatar.zoom", errors);
                finiteInRange(record.avatar.offsetX, -1.0f, 1.0f, path + ".avatar.offsetX", errors);
                finiteInRange(record.avatar.offsetY, -1.0f, 1.0f, path + ".avatar.offsetY", errors);
                finiteInRange(record.avatar.aspectRatio, 0.75f, 1.65f, path + ".avatar.aspectRatio", errors);
            }
        }
    }

    private static void validateMedications(
            List<NourishRxBackup.MedicationRecord> records,
            Set<Long> profileIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.MedicationRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "medications[" + i + "]";
            reference(record.profileId, profileIds, path + ".profileId", errors);
            requiredText(record.name, path + ".name", errors);
            requiredText(record.dosage, path + ".dosage", errors);
            if (record.doseMinutes == null || record.doseMinutes.isEmpty()) {
                errors.add(path + ".doseMinutes must contain at least one time");
            } else if (record.doseMinutes.size() > 24) {
                errors.add(path + ".doseMinutes cannot contain more than 24 times");
            } else {
                Set<Integer> uniqueMinutes = new HashSet<>();
                for (Integer minute : record.doseMinutes) {
                    if (minute == null || minute < 0 || minute >= 24 * 60 || !uniqueMinutes.add(minute)) {
                        errors.add(path + ".doseMinutes contains an invalid or duplicate time");
                        break;
                    }
                }
            }
            nonNegative(record.quantity, path + ".quantity", errors);
            nonNegative(record.refillThreshold, path + ".refillThreshold", errors);
            if (record.repeatReminderMinutes < 0 || record.repeatReminderMinutes > 24 * 60) {
                errors.add(path + ".repeatReminderMinutes is outside the supported range");
            }
            positive(record.createdAtEpochMillis, path + ".createdAtEpochMillis", errors);
        }
    }

    private static void validateDoseLogs(
            List<NourishRxBackup.DoseLogRecord> records,
            Set<Long> medicationIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        Set<String> schedules = new HashSet<>();
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.DoseLogRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "doseLogs[" + i + "]";
            reference(record.medicationId, medicationIds, path + ".medicationId", errors);
            positive(record.scheduledAtEpochMillis, path + ".scheduledAtEpochMillis", errors);
            if (!"taken".equals(record.status) && !"skipped".equals(record.status)) {
                errors.add(path + ".status must be taken or skipped");
            }
            positive(record.loggedAtEpochMillis, path + ".loggedAtEpochMillis", errors);
            if (!schedules.add(record.medicationId + ":" + record.scheduledAtEpochMillis)) {
                errors.add(path + " duplicates a medication schedule");
            }
        }
    }

    private static void validateNutritionMeals(
            List<NourishRxBackup.NutritionMealRecord> records,
            Set<Long> profileIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.NutritionMealRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "nutritionMeals[" + i + "]";
            reference(record.profileId, profileIds, path + ".profileId", errors);
            requiredText(record.name, path + ".name", errors);
            nonNegative(record.calories, path + ".calories", errors);
            finiteNonNegative(record.proteinGrams, path + ".proteinGrams", errors);
            finiteNonNegative(record.carbsGrams, path + ".carbsGrams", errors);
            finiteNonNegative(record.fatGrams, path + ".fatGrams", errors);
            positive(record.loggedAtEpochMillis, path + ".loggedAtEpochMillis", errors);
        }
    }

    private static void validateFoods(
            List<NourishRxBackup.FoodRecord> records,
            Set<Long> profileIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.FoodRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "foods[" + i + "]";
            reference(record.profileId, profileIds, path + ".profileId", errors);
            requiredText(record.name, path + ".name", errors);
            finiteNonNegative(record.servingsPerContainer, path + ".servingsPerContainer", errors);
            nonNegative(record.calories, path + ".calories", errors);
            finiteNonNegative(record.totalFatGrams, path + ".totalFatGrams", errors);
            finiteNonNegative(record.saturatedFatGrams, path + ".saturatedFatGrams", errors);
            finiteNonNegative(record.transFatGrams, path + ".transFatGrams", errors);
            finiteNonNegative(record.cholesterolMg, path + ".cholesterolMg", errors);
            finiteNonNegative(record.sodiumMg, path + ".sodiumMg", errors);
            finiteNonNegative(record.totalCarbsGrams, path + ".totalCarbsGrams", errors);
            finiteNonNegative(record.fiberGrams, path + ".fiberGrams", errors);
            finiteNonNegative(record.totalSugarsGrams, path + ".totalSugarsGrams", errors);
            finiteNonNegative(record.addedSugarsGrams, path + ".addedSugarsGrams", errors);
            finiteNonNegative(record.proteinGrams, path + ".proteinGrams", errors);
            finiteNonNegative(record.vitaminDMcg, path + ".vitaminDMcg", errors);
            finiteNonNegative(record.calciumMg, path + ".calciumMg", errors);
            finiteNonNegative(record.ironMg, path + ".ironMg", errors);
            finiteNonNegative(record.potassiumMg, path + ".potassiumMg", errors);
            positive(record.createdAtEpochMillis, path + ".createdAtEpochMillis", errors);
        }
    }

    private static void validateMealFoodLogs(
            List<NourishRxBackup.MealFoodLogRecord> records,
            Set<Long> profileIds,
            Set<Long> foodIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.MealFoodLogRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "mealFoodLogs[" + i + "]";
            reference(record.profileId, profileIds, path + ".profileId", errors);
            reference(record.foodId, foodIds, path + ".foodId", errors);
            requiredText(record.mealName, path + ".mealName", errors);
            finitePositive(record.servings, path + ".servings", errors);
            positive(record.eatenAtEpochMillis, path + ".eatenAtEpochMillis", errors);
        }
    }

    private static void validateWaterEntries(
            List<NourishRxBackup.WaterEntryRecord> records,
            Set<Long> profileIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.WaterEntryRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "waterEntries[" + i + "]";
            reference(record.profileId, profileIds, path + ".profileId", errors);
            nonNegative(record.ounces, path + ".ounces", errors);
            positive(record.loggedAtEpochMillis, path + ".loggedAtEpochMillis", errors);
        }
    }

    private static void validateWeightEntries(
            List<NourishRxBackup.WeightEntryRecord> records,
            Set<Long> profileIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.WeightEntryRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "weightEntries[" + i + "]";
            reference(record.profileId, profileIds, path + ".profileId", errors);
            finitePositive(record.pounds, path + ".pounds", errors);
            positive(record.loggedAtEpochMillis, path + ".loggedAtEpochMillis", errors);
        }
    }

    private static void validateMealDefaults(
            List<NourishRxBackup.MealDefaultRecord> records,
            Set<Long> profileIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.MealDefaultRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "mealDefaults[" + i + "]";
            reference(record.profileId, profileIds, path + ".profileId", errors);
            requiredText(record.name, path + ".name", errors);
            nonNegative(record.sortOrder, path + ".sortOrder", errors);
        }
    }

    private static void validateSavedMeals(
            List<NourishRxBackup.SavedMealRecord> records,
            Set<Long> profileIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.SavedMealRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "savedMeals[" + i + "]";
            reference(record.profileId, profileIds, path + ".profileId", errors);
            requiredText(record.name, path + ".name", errors);
            positive(record.createdAtEpochMillis, path + ".createdAtEpochMillis", errors);
        }
    }

    private static void validateSavedMealItems(
            List<NourishRxBackup.SavedMealItemRecord> records,
            Set<Long> savedMealIds,
            Set<Long> foodIds,
            List<String> errors
    ) {
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            NourishRxBackup.SavedMealItemRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            String path = "savedMealItems[" + i + "]";
            reference(record.savedMealId, savedMealIds, path + ".savedMealId", errors);
            reference(record.foodId, foodIds, path + ".foodId", errors);
            finitePositive(record.servings, path + ".servings", errors);
            nonNegative(record.sortOrder, path + ".sortOrder", errors);
        }
    }

    private static <T> Set<Long> ids(
            List<T> records,
            Function<T, Long> idReader,
            String path,
            List<String> errors
    ) {
        Set<Long> ids = new HashSet<>();
        if (records == null) {
            errors.add(path + " is missing");
            return ids;
        }
        for (int i = 0; i < records.size(); i++) {
            T record = records.get(i);
            if (record == null) {
                errors.add(path + "[" + i + "] is missing");
                continue;
            }
            long id = idReader.apply(record);
            if (id <= 0) {
                errors.add(path + "[" + i + "].id must be positive");
            } else if (!ids.add(id)) {
                errors.add(path + " contains duplicate id " + id);
            }
        }
        return ids;
    }

    private static void reference(long id, Set<Long> ids, String path, List<String> errors) {
        if (!ids.contains(id)) {
            errors.add(path + " does not reference an existing record");
        }
    }

    private static void requiredText(String value, String path, List<String> errors) {
        if (value == null || value.trim().isEmpty()) {
            errors.add(path + " is required");
        }
    }

    private static void positive(long value, String path, List<String> errors) {
        if (value <= 0) {
            errors.add(path + " must be positive");
        }
    }

    private static void nonNegative(long value, String path, List<String> errors) {
        if (value < 0) {
            errors.add(path + " cannot be negative");
        }
    }

    private static void finitePositive(float value, String path, List<String> errors) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            errors.add(path + " must be a positive finite number");
        }
    }

    private static void finiteNonNegative(float value, String path, List<String> errors) {
        if (!Float.isFinite(value) || value < 0.0f) {
            errors.add(path + " must be a non-negative finite number");
        }
    }

    private static void finiteInRange(
            float value,
            float minimum,
            float maximum,
            String path,
            List<String> errors
    ) {
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            errors.add(path + " is outside the supported range");
        }
    }
}
