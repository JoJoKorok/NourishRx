package com.jojokorok.nourishrx.backup;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BackupJsonCodecTest {
    private final BackupJsonCodec codec = new BackupJsonCodec();

    @Test
    public void completeBackupRoundTripsWithoutLosingRelationships() {
        NourishRxBackup backup = completeBackup();

        String json = codec.toJson(backup);
        NourishRxBackup restored = codec.fromJson(json);

        assertEquals(NourishRxBackup.FORMAT_ID, restored.format);
        assertEquals(NourishRxBackup.CURRENT_SCHEMA_VERSION, restored.schemaVersion);
        assertEquals("1.2.1", restored.metadata.appVersion);
        assertEquals(1L, restored.settings.selectedProfileId);
        assertEquals("nutrition", restored.settings.appMode);
        assertEquals("Jordan", restored.profiles.get(0).name);
        assertEquals("image/png", restored.profiles.get(0).avatar.mimeType);
        assertEquals(Arrays.asList(480, 1200), restored.medications.get(0).doseMinutes);
        assertEquals(10L, restored.doseLogs.get(0).medicationId);
        assertEquals(30L, restored.mealFoodLogs.get(0).foodId);
        assertEquals(50L, restored.savedMealItems.get(0).savedMealId);
        assertEquals(30L, restored.savedMealItems.get(0).foodId);
        assertFalse(json.contains("premium"));
        assertFalse(json.contains("barcodeLookups"));
    }

    @Test
    public void rejectsUnsupportedSchemaVersion() {
        NourishRxBackup backup = completeBackup();
        backup.schemaVersion = 99;

        BackupFormatException exception = assertThrows(
                BackupFormatException.class,
                () -> codec.toJson(backup)
        );

        assertTrue(exception.getMessage().contains("Unsupported schema version"));
    }

    @Test
    public void rejectsBrokenRecordRelationships() {
        NourishRxBackup backup = completeBackup();
        backup.savedMealItems.get(0).foodId = 999;

        BackupValidationResult result = BackupValidator.validate(backup);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("foodId")));
    }

    @Test
    public void rejectsMalformedJson() {
        assertThrows(BackupFormatException.class, () -> codec.fromJson("{broken"));
    }

    private static NourishRxBackup completeBackup() {
        NourishRxBackup backup = new NourishRxBackup();
        backup.metadata.exportedAtEpochMillis = 1_752_000_000_000L;
        backup.metadata.appVersion = "1.2.1";
        backup.settings.selectedProfileId = 1;
        backup.settings.appMode = "nutrition";

        NourishRxBackup.ProfileRecord profile = new NourishRxBackup.ProfileRecord();
        profile.id = 1;
        profile.name = "Jordan";
        profile.createdAtEpochMillis = 1_700_000_000_000L;
        profile.avatar = new NourishRxBackup.AvatarRecord();
        profile.avatar.mimeType = "image/png";
        profile.avatar.base64Data = "aW1hZ2U=";
        profile.avatar.zoom = 1.2f;
        backup.profiles.add(profile);

        NourishRxBackup.MedicationRecord medication = new NourishRxBackup.MedicationRecord();
        medication.id = 10;
        medication.profileId = 1;
        medication.name = "Example medication";
        medication.dosage = "10 mg";
        medication.doseMinutes.addAll(Arrays.asList(480, 1200));
        medication.quantity = 28;
        medication.refillThreshold = 7;
        medication.repeatReminderMinutes = 15;
        medication.active = true;
        medication.createdAtEpochMillis = 1_700_000_000_100L;
        backup.medications.add(medication);

        NourishRxBackup.DoseLogRecord doseLog = new NourishRxBackup.DoseLogRecord();
        doseLog.id = 20;
        doseLog.medicationId = 10;
        doseLog.scheduledAtEpochMillis = 1_752_001_200_000L;
        doseLog.status = "taken";
        doseLog.loggedAtEpochMillis = 1_752_001_260_000L;
        backup.doseLogs.add(doseLog);

        NourishRxBackup.NutritionMealRecord nutritionMeal = new NourishRxBackup.NutritionMealRecord();
        nutritionMeal.id = 21;
        nutritionMeal.profileId = 1;
        nutritionMeal.name = "Lunch";
        nutritionMeal.calories = 520;
        nutritionMeal.proteinGrams = 30.0f;
        nutritionMeal.carbsGrams = 58.0f;
        nutritionMeal.fatGrams = 18.0f;
        nutritionMeal.loggedAtEpochMillis = 1_752_010_000_000L;
        backup.nutritionMeals.add(nutritionMeal);

        NourishRxBackup.FoodRecord food = new NourishRxBackup.FoodRecord();
        food.id = 30;
        food.profileId = 1;
        food.brand = "Example brand";
        food.name = "Greek yogurt";
        food.servingSize = "1 cup (227 g)";
        food.servingsPerContainer = 4.0f;
        food.calories = 130;
        food.totalFatGrams = 0.0f;
        food.sodiumMg = 85.0f;
        food.totalCarbsGrams = 9.0f;
        food.totalSugarsGrams = 7.0f;
        food.proteinGrams = 22.0f;
        food.calciumMg = 250.0f;
        food.createdAtEpochMillis = 1_700_000_000_200L;
        backup.foods.add(food);

        NourishRxBackup.MealFoodLogRecord foodLog = new NourishRxBackup.MealFoodLogRecord();
        foodLog.id = 31;
        foodLog.profileId = 1;
        foodLog.foodId = 30;
        foodLog.mealName = "Breakfast";
        foodLog.servings = 1.5f;
        foodLog.eatenAtEpochMillis = 1_752_010_100_000L;
        backup.mealFoodLogs.add(foodLog);

        NourishRxBackup.WaterEntryRecord water = new NourishRxBackup.WaterEntryRecord();
        water.id = 40;
        water.profileId = 1;
        water.ounces = 16;
        water.loggedAtEpochMillis = 1_752_010_200_000L;
        backup.waterEntries.add(water);

        NourishRxBackup.WeightEntryRecord weight = new NourishRxBackup.WeightEntryRecord();
        weight.id = 41;
        weight.profileId = 1;
        weight.pounds = 165.5f;
        weight.loggedAtEpochMillis = 1_752_010_300_000L;
        backup.weightEntries.add(weight);

        NourishRxBackup.MealDefaultRecord mealDefault = new NourishRxBackup.MealDefaultRecord();
        mealDefault.id = 42;
        mealDefault.profileId = 1;
        mealDefault.name = "Breakfast";
        mealDefault.sortOrder = 0;
        backup.mealDefaults.add(mealDefault);

        NourishRxBackup.SavedMealRecord savedMeal = new NourishRxBackup.SavedMealRecord();
        savedMeal.id = 50;
        savedMeal.profileId = 1;
        savedMeal.name = "Quick breakfast";
        savedMeal.notes = "Weekday option";
        savedMeal.createdAtEpochMillis = 1_700_000_000_300L;
        backup.savedMeals.add(savedMeal);

        NourishRxBackup.SavedMealItemRecord item = new NourishRxBackup.SavedMealItemRecord();
        item.id = 51;
        item.savedMealId = 50;
        item.foodId = 30;
        item.servings = 1.0f;
        item.sortOrder = 0;
        backup.savedMealItems.add(item);
        return backup;
    }
}
