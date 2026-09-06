package com.jojokorok.nourishrx.backup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BackupValidationHardeningTest {
    private final BackupJsonCodec codec = new BackupJsonCodec();

    @Test
    public void rejectsOlderAndNewerSchemaVersions() {
        JsonObject older = encodedMinimalBackup();
        older.addProperty("schemaVersion", NourishRxBackup.CURRENT_SCHEMA_VERSION - 1);
        BackupFormatException olderError = assertThrows(
                BackupFormatException.class,
                () -> codec.fromJson(older.toString())
        );

        JsonObject newer = encodedMinimalBackup();
        newer.addProperty("schemaVersion", NourishRxBackup.CURRENT_SCHEMA_VERSION + 1);
        BackupFormatException newerError = assertThrows(
                BackupFormatException.class,
                () -> codec.fromJson(newer.toString())
        );

        assertTrue(olderError.getMessage().contains("Unsupported schema version"));
        assertTrue(newerError.getMessage().contains("Unsupported schema version"));
    }

    @Test
    public void rejectsMissingOrNullTopLevelCollections() {
        String[] collectionNames = {
                "profiles",
                "medications",
                "doseLogs",
                "nutritionMeals",
                "foods",
                "mealFoodLogs",
                "waterEntries",
                "weightEntries",
                "mealDefaults",
                "savedMeals",
                "savedMealItems"
        };

        for (String collectionName : collectionNames) {
            JsonObject missing = encodedMinimalBackup();
            missing.remove(collectionName);
            BackupFormatException missingError = assertThrows(
                    BackupFormatException.class,
                    () -> codec.fromJson(missing.toString())
            );
            assertTrue(missingError.getMessage().contains(collectionName));

            JsonObject nullCollection = encodedMinimalBackup();
            nullCollection.add(collectionName, null);
            BackupFormatException nullError = assertThrows(
                    BackupFormatException.class,
                    () -> codec.fromJson(nullCollection.toString())
            );
            assertTrue(nullError.getMessage().contains(collectionName));
        }
    }

    @Test
    public void rejectsDuplicateRecordIds() {
        NourishRxBackup backup = minimalBackup();
        NourishRxBackup.ProfileRecord duplicate = profile(1, "Duplicate");
        backup.profiles.add(duplicate);

        BackupValidationResult result = BackupValidator.validate(backup);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("duplicate id 1")));
    }

    @Test
    public void rejectsDuplicateDoseSchedules() {
        NourishRxBackup backup = minimalBackup();
        backup.medications.add(medication(10, 1));
        backup.doseLogs.add(doseLog(20, 10, 1_752_001_200_000L));
        backup.doseLogs.add(doseLog(21, 10, 1_752_001_200_000L));

        BackupValidationResult result = BackupValidator.validate(backup);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("duplicates a medication schedule")));
    }

    @Test
    public void rejectsBrokenFoodRelationships() {
        NourishRxBackup backup = minimalBackup();
        NourishRxBackup.MealFoodLogRecord log = new NourishRxBackup.MealFoodLogRecord();
        log.id = 30;
        log.profileId = 1;
        log.foodId = 999;
        log.mealName = "Lunch";
        log.servings = 1.0f;
        log.eatenAtEpochMillis = 1_752_010_100_000L;
        backup.mealFoodLogs.add(log);

        BackupValidationResult result = BackupValidator.validate(backup);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("foodId")));
    }

    @Test
    public void rejectsTruncatedJson() {
        String json = codec.toJson(minimalBackup());

        assertThrows(
                BackupFormatException.class,
                () -> codec.fromJson(json.substring(0, json.length() / 2))
        );
    }

    private JsonObject encodedMinimalBackup() {
        return JsonParser.parseString(codec.toJson(minimalBackup())).getAsJsonObject();
    }

    static NourishRxBackup minimalBackup() {
        NourishRxBackup backup = new NourishRxBackup();
        backup.metadata.exportedAtEpochMillis = 1_752_000_000_000L;
        backup.metadata.appVersion = "1.2.1";
        backup.settings.selectedProfileId = 1;
        backup.settings.appMode = "medication";
        backup.profiles.add(profile(1, "Jordan"));
        return backup;
    }

    static NourishRxBackup.ProfileRecord profile(long id, String name) {
        NourishRxBackup.ProfileRecord profile = new NourishRxBackup.ProfileRecord();
        profile.id = id;
        profile.name = name;
        profile.createdAtEpochMillis = 1_700_000_000_000L + id;
        return profile;
    }

    static NourishRxBackup.MedicationRecord medication(long id, long profileId) {
        NourishRxBackup.MedicationRecord medication = new NourishRxBackup.MedicationRecord();
        medication.id = id;
        medication.profileId = profileId;
        medication.name = "Example medication";
        medication.dosage = "10 mg";
        medication.doseMinutes.add(480);
        medication.quantity = 30;
        medication.refillThreshold = 7;
        medication.active = true;
        medication.createdAtEpochMillis = 1_700_000_000_100L + id;
        return medication;
    }

    static NourishRxBackup.DoseLogRecord doseLog(long id, long medicationId, long scheduledAt) {
        NourishRxBackup.DoseLogRecord doseLog = new NourishRxBackup.DoseLogRecord();
        doseLog.id = id;
        doseLog.medicationId = medicationId;
        doseLog.scheduledAtEpochMillis = scheduledAt;
        doseLog.status = "taken";
        doseLog.loggedAtEpochMillis = scheduledAt + 60_000L;
        return doseLog;
    }
}
