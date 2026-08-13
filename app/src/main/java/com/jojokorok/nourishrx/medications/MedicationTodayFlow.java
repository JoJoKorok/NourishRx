package com.jojokorok.nourishrx.medications;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.reminders.ReminderScheduler;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MedicationTodayFlow {
    public interface Callbacks {
        long currentProfileId();

        String selectedProfileName();

        View sectionTitle(String title, String subtitle);

        void emptyState(String message, String action, View.OnClickListener listener);

        void showMedicationEditor();

        void onDoseChanged();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final ZoneId zoneId;
    private final Callbacks callbacks;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());

    public MedicationTodayFlow(
            Activity activity,
            MedicationStore store,
            NourishUi ui,
            ZoneId zoneId,
            Callbacks callbacks
    ) {
        this.activity = activity;
        this.store = store;
        this.ui = ui;
        this.zoneId = zoneId;
        this.callbacks = callbacks;
    }

    public void renderToday(LinearLayout content) {
        List<DoseRow> rows = doseRowsFor(LocalDate.now(zoneId));
        String profileName = callbacks.selectedProfileName();

        content.addView(callbacks.sectionTitle(
                "Today",
                profileName + " has " + rows.size() + " scheduled doses"
        ));

        if (store.getActiveMedications(callbacks.currentProfileId()).isEmpty()) {
            callbacks.emptyState(
                    "Add the first medication for " + profileName + ".",
                    "Add medication",
                    view -> callbacks.showMedicationEditor()
            );
            return;
        }

        if (rows.isEmpty()) {
            callbacks.emptyState(
                    "No active doses are scheduled for " + profileName + " today.",
                    "Add medication",
                    view -> callbacks.showMedicationEditor()
            );
            return;
        }

        for (DoseRow row : rows) {
            content.addView(doseCard(row));
        }

        TextView footer = ui.text(
                "Always follow your prescriber's directions.",
                12,
                NourishColors.MUTED,
                Typeface.NORMAL
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, ui.dp(12), 0, 0);
        content.addView(footer);
    }

    public int doseCountFor(LocalDate date) {
        return doseRowsFor(date).size();
    }

    private View doseCard(DoseRow row) {
        LinearLayout card = ui.card();
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView time = ui.timePill(formatTime(row.scheduledAt));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(ui.dp(82), ui.dp(50));
        timeParams.rightMargin = ui.dp(12);
        top.addView(time, timeParams);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.text(row.medication.name, 19, NourishColors.INK, Typeface.BOLD));
        details.addView(ui.text(row.medication.dosage, 14, NourishColors.MUTED, Typeface.NORMAL));
        if (!row.medication.instructions.isEmpty()) {
            details.addView(ui.text(
                    row.medication.instructions,
                    14,
                    NourishColors.MUTED,
                    Typeface.NORMAL
            ));
        }
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(ui.statusBadge(rowStatus(row)));
        card.addView(top);

        if (row.status == null) {
            LinearLayout actions = actionRow();
            Button taken = ui.button("Taken", NourishColors.GREEN, NourishColors.GREEN_SOFT);
            taken.setOnClickListener(view -> markDose(row, MedicationStore.STATUS_TAKEN));
            actions.addView(taken, weightedActionParams());

            Button skip = ui.button("Skip", NourishColors.CORAL, NourishColors.CORAL_SOFT);
            skip.setOnClickListener(view -> markDose(row, MedicationStore.STATUS_SKIPPED));
            actions.addView(skip, weightedActionParams());
            card.addView(actions);
        }

        if (row.medication.isLowStock()) {
            TextView lowStock = ui.text(
                    "Low stock: " + row.medication.quantity + " left",
                    13,
                    NourishColors.CORAL,
                    Typeface.BOLD
            );
            lowStock.setPadding(0, ui.dp(8), 0, 0);
            card.addView(lowStock);
        }

        return card;
    }

    private void markDose(DoseRow row, String status) {
        store.logDose(row.medication.id, row.scheduledAt, status);
        if (MedicationStore.STATUS_TAKEN.equals(status)) {
            store.adjustInventory(row.medication.id, -1);
        }
        ReminderScheduler.scheduleNext(activity, store.getMedication(row.medication.id));
        callbacks.onDoseChanged();
    }

    private List<DoseRow> doseRowsFor(LocalDate date) {
        long start = date.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        Map<String, String> logs = store.getDoseLogsBetween(start, end);

        List<DoseRow> rows = new ArrayList<>();
        for (Medication medication : store.getActiveMedications(callbacks.currentProfileId())) {
            for (long scheduledAt : medication.scheduledDoseTimes(date, zoneId)) {
                String status = logs.get(MedicationStore.doseKey(medication.id, scheduledAt));
                rows.add(new DoseRow(medication, scheduledAt, status));
            }
        }
        rows.sort(Comparator.comparingLong(row -> row.scheduledAt));
        return rows;
    }

    private String rowStatus(DoseRow row) {
        if (MedicationStore.STATUS_TAKEN.equals(row.status)) {
            return "Taken";
        }
        if (MedicationStore.STATUS_SKIPPED.equals(row.status)) {
            return "Skipped";
        }
        long now = System.currentTimeMillis();
        if (row.scheduledAt < now - (15 * 60_000L)) {
            return "Due";
        }
        if (row.scheduledAt <= now + (30 * 60_000L)) {
            return "Next";
        }
        return "Upcoming";
    }

    private String formatTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormatter);
    }

    private LinearLayout actionRow() {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, ui.dp(12), 0, 0);
        return actions;
    }

    private LinearLayout.LayoutParams weightedActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(44), 1);
        params.rightMargin = ui.dp(8);
        return params;
    }

    private static final class DoseRow {
        final Medication medication;
        final long scheduledAt;
        final String status;

        DoseRow(Medication medication, long scheduledAt, String status) {
            this.medication = medication;
            this.scheduledAt = scheduledAt;
            this.status = status;
        }
    }
}
