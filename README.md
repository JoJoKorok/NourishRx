# Medication Manager

A native Android MVP for medication scheduling and organization.

## Current Features

- Today view with each scheduled dose for active medications.
- Named medication profiles for different people.
- Manual medication entry with dosage, instructions, first dose time, daily frequency, stock count, and refill threshold.
- Mark doses as taken or skipped.
- Inventory view with low-stock warnings and quick stock adjustments.
- Local SQLite storage on the device.
- Android cloud backup disabled by default for medication privacy.
- Android reminder notifications with Taken and Skip actions.
- Reminder rescheduling after reboot, app update, and alarm-permission changes.

## Project Shape

- Language: Java
- UI: Android framework views, no AndroidX runtime dependency
- Storage: `SQLiteOpenHelper`
- Reminders: `AlarmManager`, `BroadcastReceiver`, `NotificationChannel`
- Minimum SDK: 26
- Target/compile SDK: 37

## Run It

This workspace did not have Gradle or an Android SDK available, so the app source is scaffolded for Android Studio.

1. Open this folder in Android Studio.
2. Install SDK 37 if Android Studio prompts for it.
3. Sync the Gradle project.
4. Run the `app` configuration on an emulator or Android phone.

For command-line builds on a machine with Gradle installed:

```powershell
gradle wrapper --gradle-version 9.4.1
.\gradlew.bat assembleDebug
```

Android 13 and newer require notification permission before reminders can appear. Android 12 and newer may also ask for exact alarm access; the app falls back to inexact alarms when exact alarm access is not granted.

## Safety and Privacy Notes

This is a personal/portfolio medication organizer, not medical advice or certified medical software. Always confirm medication names, dosages, and schedules with the prescription label or a clinician.

## Install on a Phone

Use the local install/update script after Android Studio builds an APK:

```powershell
.\tools\install-to-phone.ps1
```

See `docs/INSTALL_ON_PHONE.md` for Pixel setup and update steps.

## Main Files

- `app/src/main/java/com/example/medicationmanager/MainActivity.java`
- `app/src/main/java/com/example/medicationmanager/data/MedicationStore.java`
- `app/src/main/java/com/example/medicationmanager/reminders/ReminderScheduler.java`
- `app/src/main/AndroidManifest.xml`
