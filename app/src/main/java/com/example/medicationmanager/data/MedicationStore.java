package com.example.medicationmanager.data;

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
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_PROFILES = "profiles";
    private static final String TABLE_MEDICATIONS = "medications";
    private static final String TABLE_DOSE_LOGS = "dose_logs";

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
        values.put("quantity", medication.quantity);
        values.put("refill_threshold", medication.refillThreshold);
        values.put("active", medication.active ? 1 : 0);
        values.put("created_at", medication.createdAt);
        return values;
    }

    private void createProfilesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PROFILES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL" +
                ")");
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
        values.put("created_at", System.currentTimeMillis());
        return db.insert(TABLE_PROFILES, null, values);
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
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        );
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
                cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                cursor.getInt(cursor.getColumnIndexOrThrow("refill_threshold")),
                cursor.getInt(cursor.getColumnIndexOrThrow("active")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        );
    }
}
