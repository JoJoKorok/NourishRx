package com.jojokorok.nourishrx.backup;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public final class BackupImportPreview {
    public final int schemaVersion;
    public final String appVersion;
    public final long exportedAtEpochMillis;
    public final int profileCount;
    public final int medicationCount;
    public final int doseLogCount;
    public final int foodCount;
    public final int nutritionMealCount;
    public final int mealFoodLogCount;
    public final int savedMealCount;
    public final int savedMealItemCount;
    public final int waterEntryCount;
    public final int weightEntryCount;
    public final int mealDefaultCount;

    private BackupImportPreview(NourishRxBackup backup) {
        schemaVersion = backup.schemaVersion;
        appVersion = backup.metadata.appVersion;
        exportedAtEpochMillis = backup.metadata.exportedAtEpochMillis;
        profileCount = backup.profiles.size();
        medicationCount = backup.medications.size();
        doseLogCount = backup.doseLogs.size();
        foodCount = backup.foods.size();
        nutritionMealCount = backup.nutritionMeals.size();
        mealFoodLogCount = backup.mealFoodLogs.size();
        savedMealCount = backup.savedMeals.size();
        savedMealItemCount = backup.savedMealItems.size();
        waterEntryCount = backup.waterEntries.size();
        weightEntryCount = backup.weightEntries.size();
        mealDefaultCount = backup.mealDefaults.size();
    }

    public static BackupImportPreview from(NourishRxBackup backup) {
        BackupValidationResult validation = BackupValidator.validate(backup);
        if (!validation.isValid()) {
            throw new BackupFormatException(validation.errors());
        }
        return new BackupImportPreview(backup);
    }

    public String displayMessage(ZoneId zoneId) {
        DateTimeFormatter dateFormatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(zoneId);
        return "Created " + dateFormatter.format(Instant.ofEpochMilli(exportedAtEpochMillis))
                + "\nNourishRx " + appVersion + " - backup format " + schemaVersion
                + "\n\nProfiles: " + profileCount
                + "\nMedications: " + medicationCount
                + "\nDose history entries: " + doseLogCount
                + "\nFoods: " + foodCount
                + "\nManual meal summaries: " + nutritionMealCount
                + "\nLogged food entries: " + mealFoodLogCount
                + "\nSaved meals: " + savedMealCount
                + "\nSaved meal items: " + savedMealItemCount
                + "\nWater entries: " + waterEntryCount
                + "\nWeight entries: " + weightEntryCount
                + "\nDefault meal names: " + mealDefaultCount
                + "\n\nThis backup is valid. Nothing on this device has been changed.";
    }
}
