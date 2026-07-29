package com.jojokorok.nourishrx.reminders;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.jojokorok.nourishrx.MainActivity;
import com.jojokorok.nourishrx.R;
import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.Profile;

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
            return;
        }

        if (ReminderScheduler.ACTION_REPEAT_REMINDER.equals(action)) {
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
        ReminderScheduler.cancelRepeat(context, medicationId, scheduledAt);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Medication medication = store.getMedication(medicationId);
        if (notificationManager != null) {
            notificationManager.cancel(notificationId(medication));
        }

        ReminderScheduler.scheduleNext(context, medication);
    }

    private void showReminder(Context context, long medicationId, long scheduledAt) {
        MedicationStore store = new MedicationStore(context);
        Medication medication = store.getMedication(medicationId);
        if (medication == null || !medication.active || scheduledAt <= 0) {
            ReminderScheduler.cancelRepeat(context, medicationId, scheduledAt);
            return;
        }

        if (store.getDoseStatus(medicationId, scheduledAt) != null) {
            ReminderScheduler.cancelRepeat(context, medicationId, scheduledAt);
            ReminderScheduler.scheduleNext(context, medication);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ReminderScheduler.cancelRepeat(context, medicationId, scheduledAt);
            ReminderScheduler.scheduleNext(context, medication);
            return;
        }

        String details = medication.dosage;
        if (!medication.instructions.isEmpty()) {
            details += " - " + medication.instructions;
        }
        Profile profile = store.getProfile(medication.profileId);
        String profileName = profile == null ? "Profile" : profile.name;
        ReminderScheduler.ensureNotificationChannel(context, profile);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openIntent.putExtra(ReminderScheduler.EXTRA_PROFILE_ID, medication.profileId);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                notificationId(medication),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(context, ReminderScheduler.channelId(medication.profileId))
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
            notificationManager.notify(notificationId(medication), notification);
        }

        ReminderScheduler.scheduleRepeat(context, medication, scheduledAt);
        ReminderScheduler.scheduleNext(context, medication);
    }

    private static int notificationId(Medication medication) {
        if (medication == null) {
            return 0;
        }
        long value = (medication.profileId * 1_000_003L) + medication.id;
        return (int) (value & 0x7fffffff);
    }
}
