package com.jojokorok.nourishrx.premium;

public enum PremiumFeature {
    BARCODE_SCANNING(
            "barcode_scanning",
            "Barcode scanning",
            "Scan food barcodes and import nutrition facts faster."
    ),
    ADVANCED_NUTRITION_DASHBOARD(
            "advanced_nutrition_dashboard",
            "Advanced nutrition dashboard",
            "Review longer-term calories, macros, water, and weight trends."
    ),
    MEDICATION_ADHERENCE_REPORTS(
            "medication_adherence_reports",
            "Medication adherence reports",
            "Review taken, skipped, and missed medication patterns over time."
    ),
    DATA_IMPORT_EXPORT(
            "data_import_export",
            "Data import/export",
            "Move local medication and nutrition records in or out of NourishRx."
    ),
    THEMES(
            "themes",
            "Themes",
            "Customize the visual style of the app."
    ),
    CLOUD_BACKUP_SYNC(
            "cloud_backup_sync",
            "Cloud backup and sync",
            "Keep app data backed up and available across devices."
    );

    public final String id;
    public final String title;
    public final String description;

    PremiumFeature(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }
}
