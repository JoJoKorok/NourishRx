package com.jojokorok.nourishrx.backup;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.Profile;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class BackupDatabaseImporterTest {
    private static final String DATABASE_NAME = "medication_manager.db";

    private IsolatedContext isolatedContext;
    private MedicationStore store;

    @Before
    public void setUp() {
        isolatedContext = new IsolatedContext(
                ApplicationProvider.getApplicationContext(),
                "backup_import_test_" + System.nanoTime() + "_"
        );
        store = new MedicationStore(isolatedContext);
        store.getWritableDatabase();
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
        if (isolatedContext != null) {
            isolatedContext.deleteDatabase(DATABASE_NAME);
        }
    }

    @Test
    public void testMergeRemapsImportedRelationships() throws Exception {
        NourishRxBackup backup = relatedBackup();

        BackupImportResult result = importer().apply(backup, BackupImportMode.MERGE);

        SQLiteDatabase database = store.getReadableDatabase();
        long importedProfileId = singleId(database, "profiles", "name = ?", "Imported");
        long medicationId = singleId(
                database,
                "medications",
                "profile_id = ?",
                String.valueOf(importedProfileId)
        );
        long foodId = singleId(
                database,
                "nutrition_foods",
                "profile_id = ?",
                String.valueOf(importedProfileId)
        );
        long savedMealId = singleId(
                database,
                "saved_meals",
                "profile_id = ?",
                String.valueOf(importedProfileId)
        );

        assertEquals(BackupImportMode.MERGE, result.mode);
        assertEquals(2, store.getProfiles().size());
        assertFalse(importedProfileId == backup.profiles.get(0).id);
        assertEquals(1, rowCount(database, "dose_logs", "medication_id = ?", medicationId));
        assertEquals(1, rowCount(database, "meal_food_logs", "profile_id = ? AND food_id = ?", importedProfileId, foodId));
        assertEquals(1, rowCount(database, "saved_meal_items", "saved_meal_id = ? AND food_id = ?", savedMealId, foodId));
    }

    @Test
    public void testReplaceRollsBackWhenAnInsertFails() throws Exception {
        long originalProfileId = store.ensureDefaultProfile();
        store.renameProfile(originalProfileId, "Existing");
        Medication originalMedication = new Medication(
                0,
                originalProfileId,
                "Existing medication",
                "5 mg",
                "",
                480,
                1,
                Collections.singletonList(480),
                30,
                7,
                true,
                1_700_000_000_000L
        );
        store.saveMedication(originalMedication);
        SQLiteDatabase database = store.getWritableDatabase();
        database.execSQL(
                "CREATE TRIGGER backup_test_fail BEFORE INSERT ON profiles " +
                        "WHEN NEW.name = 'Imported' BEGIN " +
                        "SELECT RAISE(ABORT, 'forced import failure'); END"
        );

        try {
            importer().apply(relatedBackup(), BackupImportMode.REPLACE);
            fail("Expected the forced profile insert to fail");
        } catch (BackupImportException expected) {
            assertTrue(expected.getMessage().contains("no database changes were saved"));
        } finally {
            database.execSQL("DROP TRIGGER IF EXISTS backup_test_fail");
        }

        List<Profile> profiles = store.getProfiles();
        List<Medication> medications = store.getAllMedications(originalProfileId);
        assertEquals(1, profiles.size());
        assertEquals("Existing", profiles.get(0).name);
        assertEquals(1, medications.size());
        assertEquals("Existing medication", medications.get(0).name);
    }

    private BackupDatabaseImporter importer() {
        return new BackupDatabaseImporter(isolatedContext, store);
    }

    private static NourishRxBackup relatedBackup() {
        NourishRxBackup backup = minimalBackup();
        backup.profiles.get(0).name = "Imported";
        backup.medications.add(medication(10, 1));
        backup.doseLogs.add(doseLog(20, 10, 1_752_001_200_000L));

        NourishRxBackup.FoodRecord food = new NourishRxBackup.FoodRecord();
        food.id = 30;
        food.profileId = 1;
        food.name = "Greek yogurt";
        food.servingSize = "1 cup";
        food.servingsPerContainer = 4.0f;
        food.calories = 130;
        food.proteinGrams = 22.0f;
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

        NourishRxBackup.SavedMealRecord savedMeal = new NourishRxBackup.SavedMealRecord();
        savedMeal.id = 40;
        savedMeal.profileId = 1;
        savedMeal.name = "Quick breakfast";
        savedMeal.createdAtEpochMillis = 1_700_000_000_300L;
        backup.savedMeals.add(savedMeal);

        NourishRxBackup.SavedMealItemRecord item = new NourishRxBackup.SavedMealItemRecord();
        item.id = 41;
        item.savedMealId = 40;
        item.foodId = 30;
        item.servings = 1.0f;
        backup.savedMealItems.add(item);
        return backup;
    }

    private static NourishRxBackup minimalBackup() {
        NourishRxBackup backup = new NourishRxBackup();
        backup.metadata.exportedAtEpochMillis = 1_752_000_000_000L;
        backup.metadata.appVersion = "1.2.1";
        backup.settings.selectedProfileId = 1;
        backup.settings.appMode = "medication";

        NourishRxBackup.ProfileRecord profile = new NourishRxBackup.ProfileRecord();
        profile.id = 1;
        profile.name = "Jordan";
        profile.createdAtEpochMillis = 1_700_000_000_001L;
        backup.profiles.add(profile);
        return backup;
    }

    private static NourishRxBackup.MedicationRecord medication(long id, long profileId) {
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

    private static NourishRxBackup.DoseLogRecord doseLog(
            long id,
            long medicationId,
            long scheduledAt
    ) {
        NourishRxBackup.DoseLogRecord doseLog = new NourishRxBackup.DoseLogRecord();
        doseLog.id = id;
        doseLog.medicationId = medicationId;
        doseLog.scheduledAtEpochMillis = scheduledAt;
        doseLog.status = "taken";
        doseLog.loggedAtEpochMillis = scheduledAt + 60_000L;
        return doseLog;
    }

    private static long singleId(
            SQLiteDatabase database,
            String table,
            String selection,
            String... selectionArgs
    ) {
        try (Cursor cursor = database.query(
                table,
                new String[]{"id"},
                selection,
                selectionArgs,
                null,
                null,
                null
        )) {
            assertTrue("Expected one row in " + table, cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private static int rowCount(
            SQLiteDatabase database,
            String table,
            String selection,
            long... selectionArgs
    ) {
        String[] args = new String[selectionArgs.length];
        for (int index = 0; index < selectionArgs.length; index++) {
            args[index] = String.valueOf(selectionArgs[index]);
        }
        try (Cursor cursor = database.query(
                table,
                new String[]{"COUNT(*)"},
                selection,
                args,
                null,
                null,
                null
        )) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static final class IsolatedContext extends ContextWrapper {
        private final String prefix;

        IsolatedContext(Context targetContext, String prefix) {
            super(targetContext);
            this.prefix = prefix;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(prefix + name);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name,
                int mode,
                SQLiteDatabase.CursorFactory factory
        ) {
            return super.openOrCreateDatabase(prefix + name, mode, factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name,
                int mode,
                SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler
        ) {
            return super.openOrCreateDatabase(prefix + name, mode, factory, errorHandler);
        }

        @Override
        public boolean deleteDatabase(String name) {
            return super.deleteDatabase(prefix + name);
        }
    }
}
