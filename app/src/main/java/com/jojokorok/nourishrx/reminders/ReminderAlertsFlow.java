package com.jojokorok.nourishrx.reminders;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import com.jojokorok.nourishrx.ui.NourishColors;

public class ReminderAlertsFlow {
    public interface Callbacks {
        void onAlertStateChanged();
    }

    private final Activity activity;
    private final int notificationRequestCode;
    private final Callbacks callbacks;

    public ReminderAlertsFlow(
            Activity activity,
            int notificationRequestCode,
            Callbacks callbacks
    ) {
        this.activity = activity;
        this.notificationRequestCode = notificationRequestCode;
        this.callbacks = callbacks;
    }

    public void initialize() {
        ReminderScheduler.ensureNotificationChannel(activity);
        ReminderScheduler.scheduleAll(activity);
    }

    public void refreshSchedules() {
        ReminderScheduler.scheduleAll(activity);
    }

    public void handleNotificationPermissionResult(boolean granted) {
        if (granted) {
            Toast.makeText(activity, "Notification reminders are enabled.", Toast.LENGTH_SHORT).show();
            handleAlertsTap();
        } else {
            Toast.makeText(
                    activity,
                    "Notifications are off. Schedules still stay saved.",
                    Toast.LENGTH_LONG
            ).show();
        }
        callbacks.onAlertStateChanged();
    }

    public void handleAlertsTap() {
        if (needsNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        notificationRequestCode
                );
            }
            return;
        }

        if (needsExactAlarmAccess()) {
            activity.startActivity(ReminderScheduler.exactAlarmSettingsIntent(activity));
            return;
        }

        ReminderScheduler.scheduleAll(activity);
        Toast.makeText(activity, "Reminder alerts are ready.", Toast.LENGTH_SHORT).show();
    }

    public String alertsLabel() {
        if (needsNotificationPermission()) {
            return "Enable alerts";
        }
        if (needsExactAlarmAccess()) {
            return "Alarm access";
        }
        return "Alerts on";
    }

    public int alertColor() {
        if (needsNotificationPermission()) {
            return NourishColors.CORAL;
        }
        if (needsExactAlarmAccess()) {
            return NourishColors.BLUE;
        }
        return NourishColors.GREEN;
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED;
    }

    private boolean needsExactAlarmAccess() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !ReminderScheduler.canScheduleExactAlarms(activity);
    }
}
