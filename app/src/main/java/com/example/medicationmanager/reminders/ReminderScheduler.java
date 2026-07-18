package com.example.medicationmanager.reminders;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.example.medicationmanager.data.Medication;
import com.example.medicationmanager.data.MedicationStore;

import java.time.ZoneId;
import java.util.List;

public final class ReminderScheduler {
    public static final String ACTION_REMINDER = "com.example.medicationmanager.ACTION_REMINDER";
    public static final String ACTION_TAKEN = "com.example.medicationmanager.ACTION_TAKEN";
    public static final String ACTION_SKIPPED = "com.example.medicationmanager.ACTION_SKIPPED";
    public static final String EXTRA_MEDICATION_ID = "extra_medication_id";
    public static final String EXTRA_SCHEDULED_AT = "extra_scheduled_at";
    public static final String CHANNEL_ID = "medication_reminders";

    private ReminderScheduler() {
    }

    public static void scheduleAll(Context context) {
        MedicationStore store = new MedicationStore(context);
        List<Medication> activeMedications = store.getActiveMedications();
        for (Medication medication : activeMedications) {
            scheduleNext(context, medication);
        }
    }

    public static void scheduleNext(Context context, Medication medication) {
        if (medication == null || !medication.active) {
            return;
        }
        long scheduledAt = medication.nextDoseAfter(System.currentTimeMillis(), ZoneId.systemDefault());
        scheduleAt(context, medication.id, scheduledAt);
    }

    public static void cancel(Context context, long medicationId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(reminderIntent(context, medicationId, 0));
    }

    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    public static Intent exactAlarmSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Medication reminders",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Dose reminders for scheduled medications");
        notificationManager.createNotificationChannel(channel);
    }

    public static PendingIntent doseActionIntent(
            Context context,
            String action,
            long medicationId,
            long scheduledAt
    ) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(action);
        intent.setData(Uri.parse("medication-manager://dose/" + action + "/" + medicationId + "/" + scheduledAt));
        intent.putExtra(EXTRA_MEDICATION_ID, medicationId);
        intent.putExtra(EXTRA_SCHEDULED_AT, scheduledAt);
        return PendingIntent.getBroadcast(
                context,
                requestCode(medicationId, scheduledAt, action.hashCode()),
                intent,
                pendingIntentFlags()
        );
    }

    private static void scheduleAt(Context context, long medicationId, long scheduledAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = reminderIntent(context, medicationId, scheduledAt);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledAt, pendingIntent);
            return;
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledAt, pendingIntent);
    }

    private static PendingIntent reminderIntent(Context context, long medicationId, long scheduledAt) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.setData(Uri.parse("medication-manager://reminder/" + medicationId));
        intent.putExtra(EXTRA_MEDICATION_ID, medicationId);
        intent.putExtra(EXTRA_SCHEDULED_AT, scheduledAt);
        return PendingIntent.getBroadcast(
                context,
                requestCode(medicationId, 0, 0),
                intent,
                pendingIntentFlags()
        );
    }

    private static int requestCode(long medicationId, long scheduledAt, int salt) {
        long value = (medicationId * 31L) + ((scheduledAt / 60_000L) * 17L) + salt;
        return (int) (value & 0x7fffffff);
    }

    private static int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
