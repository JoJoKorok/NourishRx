package com.jojokorok.nourishrx.reminders;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.Profile;

import java.time.ZoneId;
import java.util.List;

public final class ReminderScheduler {
    public static final String ACTION_REMINDER = "com.jojokorok.nourishrx.ACTION_REMINDER";
    public static final String ACTION_REPEAT_REMINDER = "com.jojokorok.nourishrx.ACTION_REPEAT_REMINDER";
    public static final String ACTION_TAKEN = "com.jojokorok.nourishrx.ACTION_TAKEN";
    public static final String ACTION_SKIPPED = "com.jojokorok.nourishrx.ACTION_SKIPPED";
    public static final String EXTRA_MEDICATION_ID = "extra_medication_id";
    public static final String EXTRA_PROFILE_ID = "extra_profile_id";
    public static final String EXTRA_SCHEDULED_AT = "extra_scheduled_at";
    public static final String CHANNEL_ID = "medication_reminders";
    private static final String CHANNEL_ID_PREFIX = "medication_reminders_profile_";

    private ReminderScheduler() {
    }

    public static void scheduleAll(Context context) {
        MedicationStore store = new MedicationStore(context);
        ensureNotificationChannel(context, store);
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
        scheduleAt(context, reminderIntent(context, medication.id, medication.profileId, scheduledAt), scheduledAt);
    }

    public static void scheduleRepeat(Context context, Medication medication, long scheduledAt) {
        if (medication == null || !medication.active || medication.repeatReminderMinutes <= 0 || scheduledAt <= 0) {
            return;
        }

        long delayMillis = medication.repeatReminderMinutes * 60_000L;
        long triggerAt = Math.max(System.currentTimeMillis() + delayMillis, scheduledAt + delayMillis);
        scheduleAt(
                context,
                repeatReminderIntent(context, medication.id, medication.profileId, scheduledAt),
                triggerAt
        );
    }

    public static void cancel(Context context, long medicationId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(reminderIntent(context, medicationId, 0, 0));
    }

    public static void cancelRepeat(Context context, long medicationId, long scheduledAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || scheduledAt <= 0) {
            return;
        }
        alarmManager.cancel(repeatReminderIntent(context, medicationId, 0, scheduledAt));
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
        ensureNotificationChannel(context, new MedicationStore(context));
    }

    public static void ensureNotificationChannel(Context context, Profile profile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        long profileId = profile == null ? 0 : profile.id;
        String profileName = profile == null ? "Medication" : profile.name;
        NotificationChannel channel = new NotificationChannel(
                profileId > 0 ? channelId(profileId) : CHANNEL_ID,
                profileName + " reminders",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Dose reminders for " + profileName);
        notificationManager.createNotificationChannel(channel);
    }

    public static String channelId(long profileId) {
        return profileId > 0 ? CHANNEL_ID_PREFIX + profileId : CHANNEL_ID;
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

    private static void scheduleAt(Context context, PendingIntent pendingIntent, long scheduledAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledAt, pendingIntent);
            return;
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledAt, pendingIntent);
    }

    private static PendingIntent reminderIntent(Context context, long medicationId, long profileId, long scheduledAt) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.setData(Uri.parse("medication-manager://reminder/" + medicationId));
        intent.putExtra(EXTRA_MEDICATION_ID, medicationId);
        intent.putExtra(EXTRA_PROFILE_ID, profileId);
        intent.putExtra(EXTRA_SCHEDULED_AT, scheduledAt);
        return PendingIntent.getBroadcast(
                context,
                requestCode(medicationId, 0, 0),
                intent,
                pendingIntentFlags()
        );
    }

    private static PendingIntent repeatReminderIntent(Context context, long medicationId, long profileId, long scheduledAt) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REPEAT_REMINDER);
        intent.setData(Uri.parse("medication-manager://repeat/" + medicationId + "/" + scheduledAt));
        intent.putExtra(EXTRA_MEDICATION_ID, medicationId);
        intent.putExtra(EXTRA_PROFILE_ID, profileId);
        intent.putExtra(EXTRA_SCHEDULED_AT, scheduledAt);
        return PendingIntent.getBroadcast(
                context,
                requestCode(medicationId, scheduledAt, ACTION_REPEAT_REMINDER.hashCode()),
                intent,
                pendingIntentFlags()
        );
    }

    private static void ensureNotificationChannel(Context context, MedicationStore store) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        ensureNotificationChannel(context, (Profile) null);
        for (Profile profile : store.getProfiles()) {
            ensureNotificationChannel(context, profile);
        }
    }

    private static int requestCode(long medicationId, long scheduledAt, int salt) {
        long value = (medicationId * 31L) + ((scheduledAt / 60_000L) * 17L) + salt;
        return (int) (value & 0x7fffffff);
    }

    private static int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
