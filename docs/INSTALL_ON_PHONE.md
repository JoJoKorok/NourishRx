# Install on an Android Phone

Android apps can be installed from an APK file. Updates work when the new APK uses the same application ID and signing key as the app already installed on the phone.

## First-Time Phone Setup

1. Open Settings on the Android phone.
2. Go to About phone.
3. Tap Build number seven times to enable developer options.
4. Go back to Settings.
5. Open System > Developer options.
6. Turn on USB debugging.
7. Plug the phone into the computer.
8. Approve the USB debugging prompt on the phone.

## Build the APK

In Android Studio:

1. Open the repository.
2. Let Gradle sync.
3. Select the `app` configuration.
4. Run the app on the connected phone, or build a debug APK.

From the command line:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Install or Update

Install the debug APK with `adb`:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The `-r` flag updates the existing app while keeping local app data when the package name and signing key match.

## Troubleshooting

If the phone rejects the update:

- Make sure the new APK uses the same application ID as the installed app.
- Make sure the new APK is signed with the same signing key as the installed app.
- If this is a local test build and data can be removed, uninstall the old app and install again.

If `adb` does not detect the phone:

- Confirm USB debugging is enabled.
- Try a different USB cable or USB port.
- Run `adb devices` and approve any phone prompt.
