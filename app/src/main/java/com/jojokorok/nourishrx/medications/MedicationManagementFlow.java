package com.jojokorok.nourishrx.medications;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.reminders.ReminderScheduler;
import com.jojokorok.nourishrx.ui.NourishUi;

public class MedicationManagementFlow {
    public interface Callbacks {
        void onMedicationChanged();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final Callbacks callbacks;

    public MedicationManagementFlow(
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

    public void showInventoryDialog(Medication medication) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText quantityField = ui.field(
                "Current quantity",
                String.valueOf(medication.quantity),
                InputType.TYPE_CLASS_NUMBER
        );
        EditText thresholdField = ui.field(
                "Refill threshold",
                String.valueOf(medication.refillThreshold),
                InputType.TYPE_CLASS_NUMBER
        );
        form.addView(quantityField);
        form.addView(thresholdField);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Update stock")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                medication.quantity = parseInt(quantityField, 0);
                medication.refillThreshold = parseInt(thresholdField, 0);
                store.saveMedication(medication);
                dialog.dismiss();
                callbacks.onMedicationChanged();
            });
        });

        dialog.show();
    }

    public void confirmDelete(Medication medication) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete " + medication.name + "?")
                .setMessage("This removes the medication and its dose history from this phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    ReminderScheduler.cancel(activity, medication.id);
                    store.deleteMedication(medication.id);
                    callbacks.onMedicationChanged();
                })
                .show();
    }

    public void toggleMedication(Medication medication) {
        medication.active = !medication.active;
        store.saveMedication(medication);
        if (medication.active) {
            ReminderScheduler.scheduleNext(activity, medication);
        } else {
            ReminderScheduler.cancel(activity, medication.id);
        }
        callbacks.onMedicationChanged();
    }

    public void adjustInventory(Medication medication, int delta) {
        store.adjustInventory(medication.id, delta);
        callbacks.onMedicationChanged();
    }

    private int parseInt(EditText field, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(field.getText().toString().trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
