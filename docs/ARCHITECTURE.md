# NourishRx Architecture

NourishRx is a native Android application written in Java with framework views and local SQLite storage. The code is organized by feature so that screen construction, user actions, and platform behavior do not accumulate in the main activity.

## Entry Points

- `MainActivity` owns the selected profile, current app mode, and current tab. It creates feature flows, connects their callbacks, and routes the selected screen into the app shell.
- `BarcodeScannerActivity` owns the camera preview and barcode scan result.
- `BootReceiver` restores scheduled medication reminders after the device restarts.
- `ReminderReceiver` displays medication notifications and handles notification actions.

## Packages

- `about` renders About, credits, and planned premium information.
- `api` contains the OpenFoodFacts HTTP client and response parsing.
- `barcode` coordinates barcode lookup, inspection, and usage limits.
- `data` contains application models and `MedicationStore`, the SQLite persistence boundary.
- `medications` contains medication screens, editing, management, and daily dose workflows.
- `nutrition` contains food editing, meal logging, saved meals, online food search, water, weight, and nutrition screens.
- `premium` contains premium tiers, feature definitions, and local entitlement checks.
- `profiles` contains profile management and profile photo cropping.
- `reminders` contains notification permission handling, alarm scheduling, receivers, and notification channels.
- `ui` contains the app shell, shared colors, and reusable view helpers.

## Flow Pattern

Feature flow classes receive the Android activity, the dependencies they use, and a small callback interface. The callback interface lets a flow request navigation or a screen refresh without directly owning global app state.

For example, a nutrition flow reads and writes through `MedicationStore`, then calls `onNutritionChanged`. `MainActivity` responds by rendering the shell and the currently selected screen again. This keeps feature behavior testable in isolation from navigation decisions and prevents feature classes from depending on one another unnecessarily.

## Data and Privacy

`MedicationStore` is the single local persistence interface for profiles, medications, dose logs, foods, meals, water, and weight. Records that belong to a person carry a profile identifier so medication and nutrition data remain separated by profile.

The app stores health data on the device. Network access is used only for user-initiated OpenFoodFacts searches and barcode lookups.

## Build Verification

Use the following checks after structural changes:

```powershell
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
