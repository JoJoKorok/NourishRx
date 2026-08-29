package com.jojokorok.nourishrx.backup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Button;
import android.widget.Toast;

import com.jojokorok.nourishrx.ui.NourishColors;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupImportFlow {
    private final Activity activity;
    private final int requestCode;
    private final BackupFileImporter importer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean importInProgress = new AtomicBoolean(false);

    public BackupImportFlow(Activity activity, int requestCode) {
        this.activity = activity;
        this.requestCode = requestCode;
        this.importer = new BackupFileImporter(
                activity.getContentResolver(),
                new BackupJsonCodec()
        );
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
            showOnUiThread(() -> showPreview(preview));
        } catch (BackupFormatException exception) {
            showOnUiThread(() -> showInvalidBackup(exception.validationErrors()));
        } catch (IOException exception) {
            showOnUiThread(() -> showReadError(exception));
        } finally {
            importInProgress.set(false);
        }
    }

    private void showPreview(BackupImportPreview preview) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Backup ready to import")
                .setMessage(preview.displayMessage(ZoneId.systemDefault()))
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> stylePositiveButton(dialog));
        dialog.show();
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
}
