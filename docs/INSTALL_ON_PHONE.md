# Install or Update on Your Pixel

Android apps install from an APK file. Updates work when the new APK uses the same `applicationId` and signing key as the app already on the phone.

For your Pixel 7a, the easiest local updater is:

```powershell
cd "C:\Users\jbeke\Documents\Programming Projects\Personal\Medication_Manager"
powershell -ExecutionPolicy Bypass -File .\tools\install-to-phone.ps1
```

For a packaged release APK, Command Prompt users can run:

```cmd
cd "C:\Users\jbeke\Documents\Programming Projects\Personal\Medication_Manager"
.\releases\v1.0-debug\install-on-pixel-7a.cmd
```

## First-Time Phone Setup

1. On the Pixel, open Settings.
2. Go to About phone.
3. Tap Build number 7 times.
4. Go back to Settings.
5. Open System > Developer options.
6. Turn on USB debugging.
7. Plug the phone into the computer.
8. Tap Allow USB debugging on the phone.

## Build Then Install

1. In Android Studio, open this project.
2. Press Run once, or build the APK from Android Studio.
3. Run the PowerShell install command above.

The script finds the newest APK under `app\build\outputs\apk` and runs:

```powershell
adb install -r <apk>
```

That `-r` means replace/update the existing app while keeping app data.

## Future Updates

1. Change the app code.
2. Run or build it in Android Studio.
3. Run `tools\install-to-phone.ps1` again.

If Android says the update is not allowed, uninstall the old app or make sure the APK is signed with the same key as the version already installed.
