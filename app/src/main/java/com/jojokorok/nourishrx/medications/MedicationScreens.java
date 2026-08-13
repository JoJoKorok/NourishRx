package com.jojokorok.nourishrx.medications;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.util.List;

public class MedicationScreens {
    public interface Callbacks {
        long currentProfileId();

        String selectedProfileName();

        View sectionTitle(String title, String subtitle);

        void emptyState(String message, String action, View.OnClickListener listener);

        void showMedicationDialog(Medication medication);

        void toggleMedication(Medication medication);

        void confirmDelete(Medication medication);

        void adjustInventory(Medication medication, int delta);

        void showInventoryDialog(Medication medication);
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final Callbacks callbacks;

    public MedicationScreens(
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

    public void renderMedications(LinearLayout content) {
        List<Medication> medications = store.getAllMedications(callbacks.currentProfileId());
        String profileName = callbacks.selectedProfileName();
        content.addView(callbacks.sectionTitle("Medications", profileName + " has " + medications.size() + " saved"));

        if (medications.isEmpty()) {
            callbacks.emptyState("Add names, doses, instructions, and reminders for " + profileName + ".", "Add medication", view -> callbacks.showMedicationDialog(null));
            return;
        }

        for (Medication medication : medications) {
            content.addView(medicationCard(medication));
        }
    }

    public void renderInventory(LinearLayout content) {
        List<Medication> medications = store.getAllMedications(callbacks.currentProfileId());
        long lowCount = medications.stream().filter(Medication::isLowStock).count();
        String profileName = callbacks.selectedProfileName();
        content.addView(callbacks.sectionTitle("Stock", profileName + " has " + lowCount + " low stock"));

        if (medications.isEmpty()) {
            callbacks.emptyState("Inventory for " + profileName + " appears here after adding meds.", "Add medication", view -> callbacks.showMedicationDialog(null));
            return;
        }

        for (Medication medication : medications) {
            content.addView(inventoryCard(medication));
        }
    }

    private View medicationCard(Medication medication) {
        LinearLayout card = ui.card();
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.text(medication.name, 19, NourishColors.INK, Typeface.BOLD));
        details.addView(ui.text(medication.dosage, 14, NourishColors.MUTED, Typeface.NORMAL));
        details.addView(ui.text(medication.doseCountLabel() + " at " + medication.scheduleSummary(), 14, NourishColors.MUTED, Typeface.NORMAL));
        details.addView(ui.text(medication.repeatReminderLabel(), 13, NourishColors.MUTED, Typeface.NORMAL));
        if (!medication.instructions.isEmpty()) {
            details.addView(ui.text(medication.instructions, 14, NourishColors.MUTED, Typeface.NORMAL));
        }
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(ui.statusBadge(medication.active ? "Active" : "Paused"));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button edit = button("Edit", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        edit.setOnClickListener(view -> callbacks.showMedicationDialog(medication));
        actions.addView(edit, weightedActionParams());

        Button toggle = button(medication.active ? "Pause" : "Resume", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        toggle.setOnClickListener(view -> callbacks.toggleMedication(medication));
        actions.addView(toggle, weightedActionParams());

        Button delete = button("Delete", NourishColors.CORAL, NourishColors.CORAL_SOFT);
        delete.setOnClickListener(view -> callbacks.confirmDelete(medication));
        actions.addView(delete, weightedActionParams());
        card.addView(actions);
        return card;
    }

    private View inventoryCard(Medication medication) {
        LinearLayout card = ui.card();
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.text(medication.name, 19, NourishColors.INK, Typeface.BOLD));
        details.addView(ui.text(
                medication.quantity + " remaining",
                14,
                medication.isLowStock() ? NourishColors.CORAL : NourishColors.MUTED,
                Typeface.BOLD
        ));
        details.addView(ui.text("Refill threshold: " + medication.refillThreshold, 13, NourishColors.MUTED, Typeface.NORMAL));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(ui.statusBadge(medication.isLowStock() ? "Refill" : "OK"));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button minus = button("-1", NourishColors.CORAL, NourishColors.CORAL_SOFT);
        minus.setOnClickListener(view -> callbacks.adjustInventory(medication, -1));
        actions.addView(minus, weightedActionParams());

        Button plus = button("+10", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        plus.setOnClickListener(view -> callbacks.adjustInventory(medication, 10));
        actions.addView(plus, weightedActionParams());

        Button set = button("Set", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        set.setOnClickListener(view -> callbacks.showInventoryDialog(medication));
        actions.addView(set, weightedActionParams());
        card.addView(actions);
        return card;
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

    private Button button(String label, int textColor, int backgroundColor) {
        return ui.button(label, textColor, backgroundColor);
    }
}
