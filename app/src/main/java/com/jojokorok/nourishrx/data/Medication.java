package com.jojokorok.nourishrx.data;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Medication {
    public static final int MINUTES_PER_DAY = 24 * 60;
    public static final int MAX_DOSES_PER_DAY = 24;
    public static final int MAX_REPEAT_REMINDER_MINUTES = 24 * 60;

    public long id;
    public long profileId;
    public String name;
    public String dosage;
    public String instructions;
    public int firstDoseMinutes;
    public int dosesPerDay;
    public List<Integer> customDoseMinutes;
    public int quantity;
    public int refillThreshold;
    public int repeatReminderMinutes;
    public boolean active;
    public long createdAt;

    public Medication(
            long id,
            long profileId,
            String name,
            String dosage,
            String instructions,
            int firstDoseMinutes,
            int dosesPerDay,
            int quantity,
            int refillThreshold,
            boolean active,
            long createdAt
    ) {
        this(
                id,
                profileId,
                name,
                dosage,
                instructions,
                firstDoseMinutes,
                dosesPerDay,
                null,
                quantity,
                refillThreshold,
                0,
                active,
                createdAt
        );
    }

    public Medication(
            long id,
            long profileId,
            String name,
            String dosage,
            String instructions,
            int firstDoseMinutes,
            int dosesPerDay,
            List<Integer> doseMinutes,
            int quantity,
            int refillThreshold,
            boolean active,
            long createdAt
    ) {
        this(
                id,
                profileId,
                name,
                dosage,
                instructions,
                firstDoseMinutes,
                dosesPerDay,
                doseMinutes,
                quantity,
                refillThreshold,
                0,
                active,
                createdAt
        );
    }

    public Medication(
            long id,
            long profileId,
            String name,
            String dosage,
            String instructions,
            int firstDoseMinutes,
            int dosesPerDay,
            List<Integer> doseMinutes,
            int quantity,
            int refillThreshold,
            int repeatReminderMinutes,
            boolean active,
            long createdAt
    ) {
        this.id = id;
        this.profileId = profileId > 0 ? profileId : 1;
        this.name = clean(name);
        this.dosage = clean(dosage);
        this.instructions = clean(instructions);
        this.customDoseMinutes = sanitizeDoseMinutes(doseMinutes, firstDoseMinutes, dosesPerDay);
        this.firstDoseMinutes = this.customDoseMinutes.get(0);
        this.dosesPerDay = this.customDoseMinutes.size();
        this.quantity = Math.max(0, quantity);
        this.refillThreshold = Math.max(0, refillThreshold);
        this.repeatReminderMinutes = clamp(repeatReminderMinutes, 0, MAX_REPEAT_REMINDER_MINUTES);
        this.active = active;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public static Medication empty() {
        return new Medication(
                0,
                1,
                "",
                "",
                "",
                8 * 60,
                1,
                30,
                7,
                true,
                System.currentTimeMillis()
        );
    }

    public List<Integer> doseMinutes() {
        return new ArrayList<>(customDoseMinutes);
    }

    public List<Long> scheduledDoseTimes(LocalDate date, ZoneId zoneId) {
        List<Long> times = new ArrayList<>();
        for (int minute : doseMinutes()) {
            ZonedDateTime scheduled = date.atStartOfDay(zoneId).plusMinutes(minute);
            times.add(scheduled.toInstant().toEpochMilli());
        }
        return times;
    }

    public long nextDoseAfter(long afterMillis, ZoneId zoneId) {
        ZonedDateTime after = Instant.ofEpochMilli(afterMillis).atZone(zoneId);
        LocalDate startDate = after.toLocalDate();
        for (int dayOffset = 0; dayOffset < 8; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);
            for (long scheduledAt : scheduledDoseTimes(date, zoneId)) {
                if (scheduledAt > afterMillis + 1_000) {
                    return scheduledAt;
                }
            }
        }
        return scheduledDoseTimes(startDate.plusDays(1), zoneId).get(0);
    }

    public boolean isLowStock() {
        return active && quantity <= refillThreshold;
    }

    public String scheduleSummary() {
        StringBuilder builder = new StringBuilder();
        List<Integer> minutes = doseMinutes();
        for (int i = 0; i < minutes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(formatMinutes(minutes.get(i)));
        }
        return builder.toString();
    }

    public String doseCountLabel() {
        return dosesPerDay == 1 ? "1 dose/day" : dosesPerDay + " doses/day";
    }

    public String repeatReminderLabel() {
        if (repeatReminderMinutes <= 0) {
            return "No repeat alerts";
        }
        if (repeatReminderMinutes == 1) {
            return "Repeats every minute";
        }
        if (repeatReminderMinutes % 60 == 0) {
            int hours = repeatReminderMinutes / 60;
            return hours == 1 ? "Repeats every hour" : "Repeats every " + hours + " hours";
        }
        return "Repeats every " + repeatReminderMinutes + " minutes";
    }

    public static String serializeDoseMinutes(List<Integer> doseMinutes) {
        JSONArray array = new JSONArray();
        for (int minute : sanitizeDoseMinutes(doseMinutes, 8 * 60, 1)) {
            array.put(minute);
        }
        return array.toString();
    }

    public static List<Integer> parseDoseMinutes(String value) {
        List<Integer> minutes = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return minutes;
        }

        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < array.length(); i++) {
                minutes.add(array.getInt(i));
            }
        } catch (JSONException exception) {
            return new ArrayList<>();
        }
        return sanitizeDoseMinutes(minutes, 8 * 60, 1);
    }

    public static String formatMinutes(int minutes) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());
        return LocalDate.now()
                .atStartOfDay()
                .plusMinutes(normalizeMinutes(minutes))
                .format(formatter);
    }

    public static int normalizeMinutes(int minutes) {
        int result = minutes % MINUTES_PER_DAY;
        return result < 0 ? result + MINUTES_PER_DAY : result;
    }

    private static List<Integer> defaultDoseMinutes(int firstDoseMinutes, int dosesPerDay) {
        int safeDosesPerDay = clamp(dosesPerDay, 1, MAX_DOSES_PER_DAY);
        int interval = MINUTES_PER_DAY / safeDosesPerDay;
        List<Integer> minutes = new ArrayList<>();
        for (int i = 0; i < safeDosesPerDay; i++) {
            minutes.add(normalizeMinutes(firstDoseMinutes + (i * interval)));
        }
        Collections.sort(minutes);
        return minutes;
    }

    private static List<Integer> sanitizeDoseMinutes(List<Integer> doseMinutes, int fallbackFirstDoseMinutes, int fallbackDosesPerDay) {
        List<Integer> minutes = new ArrayList<>();
        if (doseMinutes != null) {
            for (Integer minute : doseMinutes) {
                if (minute != null) {
                    int normalized = normalizeMinutes(minute);
                    if (!minutes.contains(normalized)) {
                        minutes.add(normalized);
                    }
                }
            }
        }

        if (minutes.isEmpty()) {
            minutes.addAll(defaultDoseMinutes(fallbackFirstDoseMinutes, fallbackDosesPerDay));
        }
        Collections.sort(minutes);
        if (minutes.size() > MAX_DOSES_PER_DAY) {
            return new ArrayList<>(minutes.subList(0, MAX_DOSES_PER_DAY));
        }
        return minutes;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
