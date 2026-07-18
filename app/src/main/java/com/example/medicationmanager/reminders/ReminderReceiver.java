package com.example.medicationmanager.reminders;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.example.medicationmanager.MainActivity;
import com.example.medicationmanager.R;
import com.example.medicationmanager.data.Medication;
import com.example.medicationmanager.data.MedicationStore;
import com.example.medicationmanager.data.Profile;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        long medicationId = intent.getLongExtra(ReminderScheduler.EXTRA_MEDICATION_ID, -1);
        long scheduledAt = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, 0);

        if (ReminderScheduler.ACTION_TAKEN.equals(action)) {
            logDose(context, medicationId, scheduledAt, MedicationStore.STATUS_TAKEN);
            return;
        }

        if (ReminderScheduler.ACTION_SKIPPED.equals(action)) {
            logDose(context, medicationId, scheduledAt, MedicationStore.STATUS_SKIPPED);
            return;
        }

        if (ReminderScheduler.ACTION_REMINDER.equals(action)) {
            showReminder(context, medicationId, scheduledAt);
        }
    }

    private void logDose(Context context, long medicationId, long scheduledAt, String status) {
        if (medicationId <= 0 || scheduledAt <= 0) {
            return;
        }
        MedicationStore store = new MedicationStore(context);
        store.logDose(medicationId, scheduledAt, status);
        if (MedicationStore.STATUS_TAKEN.equals(status)) {
            store.adjustInventory(medicationId, -1);
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(notificationId(medicationId));
        }

        Medication medication = store.getMedication(medicationId);
        ReminderScheduler.scheduleNext(context, medication);
    }

    private void showReminder(Context context, long medicationId, long scheduledAt) {
        MedicationStore store = new MedicationStore(context);
        Medication medication = store.getMedication(medicationId);
        if (medication == null || !medication.active || scheduledAt <= 0) {
            return;
        }

        if (store.getDoseStatus(medicationId, scheduledAt) != null) {
            ReminderScheduler.scheduleNext(context, medication);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ReminderScheduler.scheduleNext(context, medication);
            return;
        }

        ReminderScheduler.ensureNotificationChannel(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String details = medication.dosage;
        if (!medication.instructions.isEmpty()) {
            details += " - " + medication.instructions;
        }
        Profile profile = store.getProfile(medication.profileId);
        String profileName = profile == null ? "Profile" : profile.name;

        Notification notification = new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pill)
                .setContentTitle(profileName + ": time for " + medication.name)
                .setContentText(details)
                .setStyle(new Notification.BigTextStyle().bigText(details))
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setWhen(scheduledAt)
                .setShowWhen(true)
                .setAutoCancel(true)
                .addAction(
                        R.drawable.ic_stat_pill,
                        "Taken",
                        ReminderScheduler.doseActionIntent(
                                context,
                                ReminderScheduler.ACTION_TAKEN,
                                medicationId,
                                scheduledAt
                        )
                )
                .addAction(
                        R.drawable.ic_stat_pill,
                        "Skip",
                        ReminderScheduler.doseActionIntent(
                                context,
                                ReminderScheduler.ACTION_SKIPPED,
                                medicationId,
                                scheduledAt
                        )
                )
                .build();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(notificationId(medicationId), notification);
        }

        ReminderScheduler.scheduleNext(context, medication);
    }

    private static int notificationId(long medicationId) {
        return (int) (medicationId & 0x7fffffff);
    }
}
