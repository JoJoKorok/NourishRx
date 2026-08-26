package com.jojokorok.nourishrx.backup;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.MedicationStore;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupExportFlow {
    public interface Callbacks {
        long currentProfileId();

        String currentAppMode();
    }

    private final Activity activity;
    private final int requestCode;
    private final Callbacks callbacks;
    private final BackupFileExporter exporter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean exportInProgress = new AtomicBoolean(false);

    public BackupExportFlow(
            Activity activity,
            MedicationStore store,
            int requestCode,
            Callbacks callbacks
    ) {
        this.activity = activity;
        this.requestCode = requestCode;
        this.callbacks = callbacks;
        this.exporter = new BackupFileExporter(
                activity.getContentResolver(),
                new BackupSnapshotReader(activity, store),
                new BackupJsonCodec()
        );
    }

    public void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(
                        Intent.EXTRA_TITLE,
                        "NourishRx-backup-" + LocalDate.now() + ".json"
                );
        activity.startActivityForResult(intent, requestCode);
    }

    public boolean handleActivityResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        if (!exportInProgress.compareAndSet(false, true)) {
            Toast.makeText(activity, "A backup is already being saved.", Toast.LENGTH_SHORT).show();
            return true;
        }

        Uri destination = data.getData();
        long selectedProfileId = callbacks.currentProfileId();
        String appMode = callbacks.currentAppMode();
        executor.execute(() -> export(destination, selectedProfileId, appMode));
        return true;
    }

    public boolean ownsRequestCode(int candidateRequestCode) {
        return candidateRequestCode == requestCode;
    }

    public void close() {
        executor.shutdownNow();
    }

    private void export(Uri destination, long selectedProfileId, String appMode) {
        String message;
        int duration;
        try {
            exporter.export(destination, selectedProfileId, appMode);
            message = "NourishRx backup saved.";
            duration = Toast.LENGTH_SHORT;
        } catch (IOException | BackupFormatException exception) {
            message = "Backup could not be saved: " + safeMessage(exception);
            duration = Toast.LENGTH_LONG;
        } finally {
            exportInProgress.set(false);
        }

        String resultMessage = message;
        int resultDuration = duration;
        activity.runOnUiThread(() -> Toast.makeText(activity, resultMessage, resultDuration).show());
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "unknown error" : message;
    }
}
