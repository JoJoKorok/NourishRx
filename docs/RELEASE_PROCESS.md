# Release Process

This repository should stay source-only. Do not commit generated APK files, signing keys, local build folders, or local release folders.

## Before a Public Release

1. Increase `versionCode` in `app/build.gradle` for Android upgrade ordering.
2. Set `versionName` to the public release version.
3. Confirm the app name, icon, package identity, and branch name are presentation-ready.
4. Build and test the APK on a physical Android phone.
5. Test medication reminders, profile switching, food creation, OpenFoodFacts search, meal logging, water logging, and weight logging.
6. Add current screenshots to the README or a `docs/screenshots` folder.
7. Confirm no local paths, personal test APKs, signing keys, or private notes are tracked by Git.

## Build a Debug APK

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated under:

```text
app/build/outputs/apk/debug/
```

## GitHub Release

1. Create a tag matching the app version, such as `v1.2.0`.
2. Create a GitHub Release from that tag.
3. Upload the APK as a release asset.
4. Include a short changelog and install notes.

Generated APK files should live in GitHub Releases, not in Git history.

## Current Package Identity

The public Android application ID is:

```text
com.jojokorok.nourishrx
```

Changing this ID later creates a separate Android app install rather than an in-place update.
