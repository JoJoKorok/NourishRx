package com.jojokorok.nourishrx.medications;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.reminders.ReminderScheduler;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.util.ArrayList;
import java.util.List;

public class MedicationEditorFlow {
    public interface Callbacks {
        long currentProfileId();

        void onMedicationSaved();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final Callbacks callbacks;

    public MedicationEditorFlow(
            Activity activity,
            MedicationStore store,
            NourishUi ui,
            Callbacks callbacks
    ) {
        this.activity = activity;
        this.store = store;
        this.ui = ui;
        this.callbacks = callbacks;
    }

    public void show(Medication existing) {
        Medication medication = existing == null ? Medication.empty() : new Medication(
                existing.id,
                existing.profileId,
                existing.name,
                existing.dosage,
                existing.instructions,
                existing.firstDoseMinutes,
                existing.dosesPerDay,
                existing.doseMinutes(),
                existing.quantity,
                existing.refillThreshold,
                existing.repeatReminderMinutes,
                existing.active,
                existing.createdAt
        );
        if (existing == null) {
            medication.profileId = callbacks.currentProfileId();
        }

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(10), ui.dp(18), 0);

        EditText nameField = ui.field(
                "Medication name",
                medication.name,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        EditText dosageField = ui.field("Dosage", medication.dosage, InputType.TYPE_CLASS_TEXT);
        EditText instructionsField = ui.field(
                "Instructions",
                medication.instructions,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );

        ArrayList<Integer> selectedDoseMinutes = new ArrayList<>(medication.doseMinutes());
        TextView frequencySummary = ui.text("", 13, NourishColors.MUTED, Typeface.BOLD);
        LinearLayout doseTimesList = new LinearLayout(activity);
        doseTimesList.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] renderDoseTimes = new Runnable[1];
        renderDoseTimes[0] = () -> renderDoseTimeRows(
                doseTimesList,
                frequencySummary,
                selectedDoseMinutes,
                renderDoseTimes[0]
        );
        renderDoseTimes[0].run();

        int[] selectedRepeatReminderMinutes = new int[]{medication.repeatReminderMinutes};
        TextView repeatSummary = ui.text("", 13, NourishColors.MUTED, Typeface.BOLD);
        LinearLayout repeatOptions = new LinearLayout(activity);
        repeatOptions.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] renderRepeatOptions = new Runnable[1];
        renderRepeatOptions[0] = () -> renderRepeatReminderOptions(
                repeatOptions,
                repeatSummary,
                selectedRepeatReminderMinutes,
                renderRepeatOptions[0]
        );
        renderRepeatOptions[0].run();

        Button addDoseTime = ui.button("+ Dose time", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        addDoseTime.setOnClickListener(view -> {
            if (selectedDoseMinutes.size() >= Medication.MAX_DOSES_PER_DAY) {
                Toast.makeText(
                        activity,
                        "Maximum is " + Medication.MAX_DOSES_PER_DAY + " doses per day.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            selectedDoseMinutes.add(nextSuggestedDoseTime(selectedDoseMinutes));
            renderDoseTimes[0].run();
        });

        EditText quantityField = ui.field(
                "Current quantity",
                existing == null ? "" : String.valueOf(medication.quantity),
                InputType.TYPE_CLASS_NUMBER
        );
        EditText thresholdField = ui.field(
                "Refill threshold",
                existing == null ? "" : String.valueOf(medication.refillThreshold),
                InputType.TYPE_CLASS_NUMBER
        );
        CheckBox activeBox = new CheckBox(activity);
        activeBox.setText("Active reminders");
        activeBox.setTextColor(NourishColors.INK);
        activeBox.setTextSize(15);
        activeBox.setChecked(medication.active);

        form.addView(nameField);
        form.addView(dosageField);
        form.addView(instructionsField);
        form.addView(ui.fieldLabel("Frequency"));
        form.addView(frequencySummary);
        form.addView(doseTimesList);
        LinearLayout.LayoutParams addDoseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        addDoseParams.topMargin = ui.dp(8);
        form.addView(addDoseTime, addDoseParams);
        form.addView(ui.fieldLabel("Repeat alerts"));
        form.addView(repeatSummary);
        form.addView(repeatOptions);
        form.addView(quantityField);
        form.addView(thresholdField);
        form.addView(activeBox);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? "Add medication" : "Edit medication")
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> saveMedication(
                    dialog,
                    medication,
                    nameField,
                    dosageField,
                    instructionsField,
                    selectedDoseMinutes,
                    quantityField,
                    thresholdField,
                    selectedRepeatReminderMinutes[0],
                    activeBox.isChecked()
            ));
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void saveMedication(
            AlertDialog dialog,
            Medication medication,
            EditText nameField,
            EditText dosageField,
            EditText instructionsField,
            ArrayList<Integer> selectedDoseMinutes,
            EditText quantityField,
            EditText thresholdField,
            int repeatReminderMinutes,
            boolean active
    ) {
        String name = nameField.getText().toString().trim();
        String dosage = dosageField.getText().toString().trim();
        if (name.isEmpty()) {
            nameField.setError("Required");
            return;
        }
        if (dosage.isEmpty()) {
            dosageField.setError("Required");
            return;
        }

        Medication toSave = new Medication(
                medication.id,
                medication.profileId,
                name,
                dosage,
                instructionsField.getText().toString(),
                selectedDoseMinutes.get(0),
                selectedDoseMinutes.size(),
                selectedDoseMinutes,
                parseInt(quantityField, 0),
                parseInt(thresholdField, 0),
                repeatReminderMinutes,
                active,
                medication.createdAt
        );

        store.saveMedication(toSave);
        if (toSave.active) {
            ReminderScheduler.scheduleNext(activity, toSave);
        } else {
            ReminderScheduler.cancel(activity, toSave.id);
        }
        dialog.dismiss();
        callbacks.onMedicationSaved();
    }

    private void renderDoseTimeRows(
            LinearLayout container,
            TextView frequencySummary,
            ArrayList<Integer> doseMinutes,
            Runnable refresh
    ) {
        normalizeDoseTimes(doseMinutes);
        frequencySummary.setText(
                "Frequency: " + plural(doseMinutes.size(), "dose/day", "doses/day")
        );
        container.removeAllViews();

        for (int i = 0; i < doseMinutes.size(); i++) {
            int index = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, ui.dp(8), 0, 0);

            TextView label = ui.text("Dose " + (index + 1), 14, NourishColors.INK, Typeface.BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ui.dp(72), ui.dp(44));
            row.addView(label, labelParams);

            Button time = ui.button(
                    Medication.formatMinutes(doseMinutes.get(index)),
                    NourishColors.BLUE,
                    NourishColors.BLUE_SOFT
            );
            time.setOnClickListener(view -> showDoseTimePicker(doseMinutes, index, refresh));
            row.addView(time, new LinearLayout.LayoutParams(0, ui.dp(44), 1));

            Button remove = ui.button("Remove", NourishColors.CORAL, NourishColors.CORAL_SOFT);
            remove.setEnabled(doseMinutes.size() > 1);
            remove.setAlpha(doseMinutes.size() > 1 ? 1.0f : 0.45f);
            remove.setOnClickListener(view -> {
                if (doseMinutes.size() <= 1) {
                    Toast.makeText(activity, "Keep at least one dose time.", Toast.LENGTH_SHORT).show();
                    return;
                }
                doseMinutes.remove(index);
                refresh.run();
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(ui.dp(96), ui.dp(44));
            removeParams.leftMargin = ui.dp(8);
            row.addView(remove, removeParams);

            container.addView(row);
        }
    }

    private void showDoseTimePicker(ArrayList<Integer> doseMinutes, int index, Runnable refresh) {
        int existingMinutes = doseMinutes.get(index);
        int hour = existingMinutes / 60;
        int minute = existingMinutes % 60;
        TimePickerDialog dialog = new TimePickerDialog(
                activity,
                (view, selectedHour, selectedMinute) -> {
                    int newMinutes = Medication.normalizeMinutes((selectedHour * 60) + selectedMinute);
                    for (int i = 0; i < doseMinutes.size(); i++) {
                        if (i != index && doseMinutes.get(i) == newMinutes) {
                            Toast.makeText(activity, "That dose time is already scheduled.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    doseMinutes.set(index, newMinutes);
                    refresh.run();
                },
                hour,
                minute,
                false
        );
        dialog.show();
    }

    private void renderRepeatReminderOptions(
            LinearLayout container,
            TextView repeatSummary,
            int[] repeatReminderMinutes,
            Runnable refresh
    ) {
        repeatReminderMinutes[0] = Math.max(
                0,
                Math.min(Medication.MAX_REPEAT_REMINDER_MINUTES, repeatReminderMinutes[0])
        );
        repeatSummary.setText(Medication.repeatReminderLabel(repeatReminderMinutes[0]));
        container.removeAllViews();

        addRepeatReminderRow(container, repeatReminderMinutes, refresh, new int[]{0, 5, 10});
        addRepeatReminderRow(container, repeatReminderMinutes, refresh, new int[]{30, 60, -1});
    }

    private void addRepeatReminderRow(
            LinearLayout container,
            int[] repeatReminderMinutes,
            Runnable refresh,
            int[] options
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(8), 0, 0);

        for (int option : options) {
            boolean selected = option < 0
                    ? isCustomRepeatReminder(repeatReminderMinutes[0])
                    : repeatReminderMinutes[0] == option;
            Button optionButton = ui.button(
                    repeatReminderOptionLabel(option),
                    selected ? Color.WHITE : NourishColors.BLUE,
                    selected ? NourishColors.BLUE : NourishColors.BLUE_SOFT
            );
            optionButton.setOnClickListener(view -> {
                if (option < 0) {
                    showCustomRepeatReminderDialog(repeatReminderMinutes, refresh);
                    return;
                }
                repeatReminderMinutes[0] = option;
                refresh.run();
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(42), 1);
            if (row.getChildCount() > 0) {
                params.leftMargin = ui.dp(8);
            }
            row.addView(optionButton, params);
        }

        container.addView(row);
    }

    private void showCustomRepeatReminderDialog(int[] repeatReminderMinutes, Runnable refresh) {
        String currentValue = isCustomRepeatReminder(repeatReminderMinutes[0])
                ? String.valueOf(repeatReminderMinutes[0])
                : "";
        EditText minutesField = ui.field(
                "Minutes between alerts",
                currentValue,
                InputType.TYPE_CLASS_NUMBER
        );
        minutesField.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Custom repeat")
                .setView(minutesField)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                int minutes = parseInt(minutesField, 0);
                if (minutes <= 0) {
                    minutesField.setError("Enter minutes");
                    return;
                }
                if (minutes > Medication.MAX_REPEAT_REMINDER_MINUTES) {
                    minutesField.setError("Use 1440 minutes or less");
                    return;
                }

                repeatReminderMinutes[0] = minutes;
                refresh.run();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private int nextSuggestedDoseTime(List<Integer> doseMinutes) {
        if (doseMinutes.isEmpty()) {
            return 8 * 60;
        }

        ArrayList<Integer> sorted = new ArrayList<>(doseMinutes);
        normalizeDoseTimes(sorted);
        int candidate = Medication.normalizeMinutes(sorted.get(sorted.size() - 1) + (4 * 60));
        for (int attempt = 0; attempt < Medication.MAX_DOSES_PER_DAY; attempt++) {
            if (!sorted.contains(candidate)) {
                return candidate;
            }
            candidate = Medication.normalizeMinutes(candidate + 60);
        }
        return 8 * 60;
    }

    private void normalizeDoseTimes(ArrayList<Integer> doseMinutes) {
        if (doseMinutes.isEmpty()) {
            doseMinutes.add(8 * 60);
        }
        for (int i = 0; i < doseMinutes.size(); i++) {
            doseMinutes.set(i, Medication.normalizeMinutes(doseMinutes.get(i)));
        }
        doseMinutes.sort(Integer::compareTo);
        while (doseMinutes.size() > Medication.MAX_DOSES_PER_DAY) {
            doseMinutes.remove(doseMinutes.size() - 1);
        }
    }

    private boolean isCustomRepeatReminder(int minutes) {
        return minutes > 0 && minutes != 5 && minutes != 10 && minutes != 30 && minutes != 60;
    }

    private String repeatReminderOptionLabel(int minutes) {
        if (minutes < 0) {
            return "Custom";
        }
        if (minutes == 0) {
            return "Off";
        }
        if (minutes == 60) {
            return "1 hr";
        }
        return minutes + " min";
    }

    private int parseInt(EditText field, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(field.getText().toString().trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
