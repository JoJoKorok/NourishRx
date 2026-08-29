package com.jojokorok.nourishrx.backup;

import org.junit.Test;

import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BackupImportPreviewTest {
    @Test
    public void summarizesAllBackupRecordGroups() {
        NourishRxBackup backup = validBackup();
        addRelatedRecords(backup);
        BackupImportPreview preview = BackupImportPreview.from(backup);

        assertEquals(1, preview.profileCount);
        assertEquals(1, preview.medicationCount);
        assertEquals(1, preview.doseLogCount);
        assertEquals(1, preview.foodCount);
        assertEquals(1, preview.nutritionMealCount);
        assertEquals(1, preview.mealFoodLogCount);
        assertEquals(1, preview.savedMealCount);
        assertEquals(1, preview.savedMealItemCount);
        assertEquals(1, preview.waterEntryCount);
        assertEquals(1, preview.weightEntryCount);
        assertEquals(1, preview.mealDefaultCount);
        assertTrue(preview.displayMessage(ZoneId.of("UTC")).contains("Nothing on this device has been changed"));
    }

    @Test
    public void rejectsInvalidBackupBeforePreview() {
        NourishRxBackup backup = validBackup();
        backup.schemaVersion = 99;

        assertThrows(BackupFormatException.class, () -> BackupImportPreview.from(backup));
    }

    private static NourishRxBackup validBackup() {
        NourishRxBackup backup = new NourishRxBackup();
        backup.metadata.exportedAtEpochMillis = 1_752_000_000_000L;
        backup.metadata.appVersion = "1.2.1";
        backup.settings.selectedProfileId = 1;

        NourishRxBackup.ProfileRecord profile = new NourishRxBackup.ProfileRecord();
        profile.id = 1;
        profile.name = "Jordan";
        profile.createdAtEpochMillis = 1_700_000_000_000L;
        backup.profiles.add(profile);

        NourishRxBackup.MedicationRecord medication = new NourishRxBackup.MedicationRecord();
        medication.id = 10;
        medication.profileId = 1;
        medication.name = "Example medication";
        medication.dosage = "10 mg";
        medication.doseMinutes.add(480);
        medication.quantity = 30;
        medication.refillThreshold = 7;
        medication.active = true;
        medication.createdAtEpochMillis = 1_700_000_000_100L;
        backup.medications.add(medication);
        return backup;
    }

    private static void addRelatedRecords(NourishRxBackup backup) {
        NourishRxBackup.DoseLogRecord doseLog = new NourishRxBackup.DoseLogRecord();
        doseLog.id = 20;
        doseLog.medicationId = 10;
        doseLog.scheduledAtEpochMillis = 1_752_000_100_000L;
        doseLog.status = "taken";
        doseLog.loggedAtEpochMillis = 1_752_000_160_000L;
        backup.doseLogs.add(doseLog);

        NourishRxBackup.FoodRecord food = new NourishRxBackup.FoodRecord();
        food.id = 30;
        food.profileId = 1;
        food.name = "Yogurt";
        food.createdAtEpochMillis = 1_700_000_000_200L;
        backup.foods.add(food);

        NourishRxBackup.NutritionMealRecord meal = new NourishRxBackup.NutritionMealRecord();
        meal.id = 31;
        meal.profileId = 1;
        meal.name = "Breakfast";
        meal.loggedAtEpochMillis = 1_752_000_200_000L;
        backup.nutritionMeals.add(meal);

        NourishRxBackup.MealFoodLogRecord foodLog = new NourishRxBackup.MealFoodLogRecord();
        foodLog.id = 32;
        foodLog.profileId = 1;
        foodLog.foodId = 30;
        foodLog.mealName = "Breakfast";
        foodLog.servings = 1.0f;
        foodLog.eatenAtEpochMillis = 1_752_000_200_000L;
        backup.mealFoodLogs.add(foodLog);

        NourishRxBackup.SavedMealRecord savedMeal = new NourishRxBackup.SavedMealRecord();
        savedMeal.id = 40;
        savedMeal.profileId = 1;
        savedMeal.name = "Quick breakfast";
        savedMeal.createdAtEpochMillis = 1_700_000_000_300L;
        backup.savedMeals.add(savedMeal);

        NourishRxBackup.SavedMealItemRecord savedItem = new NourishRxBackup.SavedMealItemRecord();
        savedItem.id = 41;
        savedItem.savedMealId = 40;
        savedItem.foodId = 30;
        savedItem.servings = 1.0f;
        backup.savedMealItems.add(savedItem);

        NourishRxBackup.WaterEntryRecord water = new NourishRxBackup.WaterEntryRecord();
        water.id = 50;
        water.profileId = 1;
        water.ounces = 16;
        water.loggedAtEpochMillis = 1_752_000_300_000L;
        backup.waterEntries.add(water);

        NourishRxBackup.WeightEntryRecord weight = new NourishRxBackup.WeightEntryRecord();
        weight.id = 51;
        weight.profileId = 1;
        weight.pounds = 165.0f;
        weight.loggedAtEpochMillis = 1_752_000_400_000L;
        backup.weightEntries.add(weight);

        NourishRxBackup.MealDefaultRecord mealDefault = new NourishRxBackup.MealDefaultRecord();
        mealDefault.id = 52;
        mealDefault.profileId = 1;
        mealDefault.name = "Breakfast";
        backup.mealDefaults.add(mealDefault);
    }
}
