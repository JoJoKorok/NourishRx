package com.jojokorok.nourishrx.reminders;

import android.content.Context;
import android.content.SharedPreferences;

final class ReminderScheduleGeneration {
    private static final String PREFERENCES_NAME = "reminder_schedule_state";
    private static final String KEY_GENERATION = "schedule_generation";

    private ReminderScheduleGeneration() {
    }

    static long current(Context context) {
        return preferences(context).getLong(KEY_GENERATION, 0);
    }

    static void advance(Context context) {
        long next = Math.max(current(context) + 1, System.currentTimeMillis());
        preferences(context).edit().putLong(KEY_GENERATION, next).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }
}
