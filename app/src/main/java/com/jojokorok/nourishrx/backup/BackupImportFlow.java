package com.jojokorok.nourishrx.backup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Button;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.ui.NourishColors;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupImportFlow {
    public interface Callbacks {
        void prepareForImport();

        void onImportCompleted(BackupImportResult result);

        void onImportFailed();
    }

    private final Activity activity;
    private final int requestCode;
    private final Callbacks callbacks;
    private final BackupFileImporter importer;
    private final BackupDatabaseImporter databaseImporter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean importInProgress = new AtomicBoolean(false);

    public BackupImportFlow(
            Activity activity,
            MedicationStore store,
            int requestCode,
            Callbacks callbacks
    ) {
        this.activity = activity;
        this.requestCode = requestCode;
        this.callbacks = callbacks;
        this.importer = new BackupFileImporter(
                activity.getContentResolver(),
                new BackupJsonCodec()
        );
        this.databaseImporter = new BackupDatabaseImporter(activity, store);
    }

    public void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        activity.startActivityForResult(intent, requestCode);
    }

    public boolean handleActivityResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        if (!importInProgress.compareAndSet(false, true)) {
            Toast.makeText(activity, "A backup is already being checked.", Toast.LENGTH_SHORT).show();
            return true;
        }

        executor.execute(() -> readAndPreview(data));
        return true;
    }

    public boolean ownsRequestCode(int candidateRequestCode) {
        return candidateRequestCode == requestCode;
    }

    public void close() {
        executor.shutdownNow();
    }

    private void readAndPreview(Intent data) {
        try {
            NourishRxBackup backup = importer.read(data.getData());
            BackupImportPreview preview = BackupImportPreview.from(backup);
            showOnUiThread(() -> showPreview(backup, preview));
        } catch (BackupFormatException exception) {
            showOnUiThread(() -> showInvalidBackup(exception.validationErrors()));
        } catch (IOException exception) {
            showOnUiThread(() -> showReadError(exception));
        } finally {
            importInProgress.set(false);
        }
    }

    private void showPreview(NourishRxBackup backup, BackupImportPreview preview) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Backup ready to import")
                .setMessage(preview.displayMessage(ZoneId.systemDefault()))
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Merge", (ignored, which) -> confirmMerge(backup))
                .setPositiveButton("Replace", (ignored, which) -> confirmReplace(backup))
                .create();
        dialog.setOnShowListener(ignored -> styleImportChoiceButtons(dialog));
        dialog.show();
    }

    private void confirmMerge(NourishRxBackup backup) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Merge backup data?")
                .setMessage(
                        "Current records will stay on this device. Backup profiles and all of their records "
                                + "will be added as new data."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Merge data", (ignored, which) -> applyBackup(backup, BackupImportMode.MERGE))
                .create();
        dialog.setOnShowListener(ignored -> stylePositiveButton(dialog));
        dialog.show();
    }

    private void confirmReplace(NourishRxBackup backup) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Replace all local data?")
                .setMessage(
                        "This removes the medication, nutrition, profile, water, and weight records currently "
                                + "on this device, then restores the selected backup. This cannot be undone."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Replace data", (ignored, which) -> applyBackup(backup, BackupImportMode.REPLACE))
                .create();
        dialog.setOnShowListener(ignored -> {
            stylePositiveButton(dialog);
            Button replace = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (replace != null) {
                replace.setTextColor(NourishColors.CORAL);
            }
        });
        dialog.show();
    }

    private void applyBackup(NourishRxBackup backup, BackupImportMode mode) {
        if (!importInProgress.compareAndSet(false, true)) {
            Toast.makeText(activity, "A backup operation is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            try {
                callbacks.prepareForImport();
                BackupImportResult result = databaseImporter.apply(backup, mode);
                showOnUiThread(() -> {
                    callbacks.onImportCompleted(result);
                    Toast.makeText(
                            activity,
                            mode == BackupImportMode.MERGE
                                    ? "Backup merged successfully."
                                    : "Backup restored successfully.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (BackupImportException | RuntimeException exception) {
                showOnUiThread(() -> {
                    callbacks.onImportFailed();
                    showImportError(exception);
                });
            } finally {
                importInProgress.set(false);
            }
        });
    }

    private void showInvalidBackup(List<String> errors) {
        StringBuilder message = new StringBuilder(
                "This file cannot be imported. Nothing on this device has been changed."
        );
        int shownErrors = Math.min(errors.size(), 3);
        for (int i = 0; i < shownErrors; i++) {
            message.append("\n\n").append(errors.get(i));
        }
        if (errors.size() > shownErrors) {
            message.append("\n\nAnd ").append(errors.size() - shownErrors).append(" more issue(s).");
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Backup cannot be used")
                .setMessage(message.toString())
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> stylePositiveButton(dialog));
        dialog.show();
    }

    private void showReadError(IOException exception) {
        String reason = exception.getMessage();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "The selected file could not be read.";
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Backup could not be opened")
                .setMessage(reason + "\n\nNothing on this device has been changed.")
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> stylePositiveButton(dialog));
        dialog.show();
    }

    private void showImportError(Exception exception) {
        String reason = exception.getMessage();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "The backup could not be imported.";
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Import did not finish")
                .setMessage(reason + "\n\nYour previous data remains available.")
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> stylePositiveButton(dialog));
        dialog.show();
    }

    private void showOnUiThread(Runnable action) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                action.run();
            }
        });
    }

    private static void stylePositiveButton(AlertDialog dialog) {
        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(NourishColors.BLUE);
        }
    }

    private static void styleImportChoiceButtons(AlertDialog dialog) {
        stylePositiveButton(dialog);
        Button replace = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (replace != null) {
            replace.setTextColor(NourishColors.CORAL);
        }
        Button merge = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (merge != null) {
            merge.setTextColor(NourishColors.BLUE);
        }
        Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (cancel != null) {
            cancel.setTextColor(NourishColors.INK_SECONDARY);
        }
    }
}
