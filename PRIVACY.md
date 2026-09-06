# Privacy

NourishRx is designed as a local-first personal health organizer.

## Local Data

The app stores profile, medication, nutrition, meal log, water, and weight data in a local SQLite database on the Android device. Android cloud backup is disabled in the app manifest.

## OpenFoodFacts Lookup

NourishRx can search OpenFoodFacts when the user chooses the online food lookup option. This sends the search text and selected product barcode to OpenFoodFacts public API endpoints.

The app does not include a private OpenFoodFacts API key. OpenFoodFacts public read endpoints are rate-limited, so the app uses explicit button-triggered searches and paged results instead of search-as-you-type.

## No Account System

The app does not currently include user accounts, remote sync, analytics, or advertising integrations.

## User-Created Backups

NourishRx creates a JSON backup only when the user selects **Export backup** and chooses a destination through Android's file picker. The backup may contain profile photos, medication records, nutrition data, meal history, water entries, weight entries, and app settings.

Backup files are not encrypted by NourishRx. Anyone with access to an exported file may be able to read its contents, so backups should be stored only in a private, trusted location. Importing in Replace mode removes the app's current local records before restoring the selected backup; users should create a current backup first.

## Sensitive Health Information

Medication and nutrition data can be sensitive. Users should avoid sharing app databases, screenshots, or exported builds that contain personal information.

## Medical Disclaimer

NourishRx is not medical software and does not provide medical advice. Users should verify medication and nutrition information with trusted professional or official sources.
