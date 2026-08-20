# NourishRx

NourishRx is a native Android health organizer that combines medication scheduling with nutrition tracking. It supports shared profiles, medication reminders, local food logging, reusable nutrition facts, water intake, weight tracking, and optional food lookup through the public OpenFoodFacts API.

This project is built as a personal portfolio app and is not medical software.

Created by [Joseph Bekele](https://github.com/JoJoKorok).

## Features

- Shared profiles for medication and nutrition records.
- Medication scheduling with custom dose times.
- Dose logging with taken and skipped states.
- Inventory tracking with refill warnings.
- Local reminder notifications using Android alarms.
- Reusable food database with brand, food name, serving size, servings per container, and nutrition facts.
- Meal logging by saved food and serving amount.
- Configurable default meal names.
- Water intake and weight tracking.
- Optional OpenFoodFacts search with inspect-before-save food imports.
- Local SQLite storage on the device.
- Android cloud backup disabled by default for privacy.

## Screenshots

Screenshots should be added before a public portfolio release. Recommended views:

- Home screen with profile and mode switch.
- Medication schedule view.
- Food database view.
- OpenFoodFacts search and inspect flow.
- Meal log and daily nutrition summary.

## Tech Stack

- Java
- Android framework views
- SQLiteOpenHelper
- AlarmManager
- BroadcastReceiver
- NotificationChannel
- OpenFoodFacts public API
- Gradle Android plugin

## Project Shape

```text
app/src/main/java/com/jojokorok/nourishrx/
  MainActivity.java
  BarcodeScannerActivity.java
  about/
  api/OpenFoodFactsClient.java
  barcode/
  data/
  medications/
  nutrition/
  premium/
  profiles/
  reminders/
  ui/

app/src/main/res/
  drawable/
  mipmap-anydpi-v26/
  values/

docs/
  ARCHITECTURE.md
  INSTALL_ON_PHONE.md
  RELEASE_PROCESS.md
```

`MainActivity` coordinates app state and routes events between focused feature flows. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for package responsibilities and the callback pattern used by the application.

## Build

Open the repository in Android Studio, let Gradle sync, then run the `app` configuration on an emulator or Android device.

Command-line debug build:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux, use `./gradlew :app:assembleDebug`.

When Android Studio may be open, use the isolated Windows build command so command-line verification does not share Android Studio's generated files:

```powershell
.\tools\build-android.bat :app:assembleDebug
```

Isolated build outputs are written under `%LOCALAPPDATA%\NourishRx\cli-build` and use a non-persistent Gradle process.

## Install on Android

Build a debug APK, then install it with Android Studio or `adb`:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The `-r` flag updates an existing install while keeping local app data when the package name and signing key match.

See [docs/INSTALL_ON_PHONE.md](docs/INSTALL_ON_PHONE.md) for setup details.

## Privacy

Medication, profile, nutrition, water, and weight data are stored locally on the device. OpenFoodFacts is contacted only when a user explicitly searches for foods online. The app does not include a private OpenFoodFacts API key.

See [PRIVACY.md](PRIVACY.md) for more detail.

## Medical Disclaimer

NourishRx is a personal organizer and portfolio project. It is not a substitute for professional medical advice, diagnosis, treatment, medication counseling, or nutrition counseling. Medication names, schedules, and nutrition information should be verified against trusted sources such as prescription labels, clinicians, pharmacists, and official nutrition labels.

## Release Notes

Generated APK files should not be committed to the repository. Publish installable APKs through GitHub Releases instead.

See [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md) for a suggested release workflow.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
