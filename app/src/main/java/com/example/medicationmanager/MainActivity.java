package com.example.medicationmanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.example.medicationmanager.data.Medication;
import com.example.medicationmanager.data.MedicationStore;
import com.example.medicationmanager.data.Profile;
import com.example.medicationmanager.reminders.ReminderScheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 42;
    private static final String PREF_SELECTED_PROFILE_ID = "selected_profile_id";

    private static final int COLOR_SURFACE = Color.rgb(246, 242, 232);
    private static final int COLOR_CARD = Color.rgb(255, 252, 246);
    private static final int COLOR_INK = Color.rgb(32, 37, 50);
    private static final int COLOR_MUTED = Color.rgb(102, 99, 112);
    private static final int COLOR_GREEN = Color.rgb(32, 120, 100);
    private static final int COLOR_GREEN_SOFT = Color.rgb(220, 242, 233);
    private static final int COLOR_CORAL = Color.rgb(214, 95, 73);
    private static final int COLOR_CORAL_SOFT = Color.rgb(252, 228, 219);
    private static final int COLOR_BLUE = Color.rgb(70, 111, 168);
    private static final int COLOR_BLUE_SOFT = Color.rgb(226, 235, 249);
    private static final int COLOR_GOLD = Color.rgb(179, 127, 47);
    private static final int COLOR_GOLD_SOFT = Color.rgb(255, 240, 201);
    private static final int COLOR_BORDER = Color.rgb(224, 217, 203);
    private static final int COLOR_TAB_TRACK = Color.rgb(234, 228, 214);

    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault());
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());

    private MedicationStore store;
    private LinearLayout root;
    private LinearLayout content;
    private String currentTab = "today";
    private long currentProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new MedicationStore(this);
        currentProfileId = loadSelectedProfileId();
        ReminderScheduler.ensureNotificationChannel(this);
        ReminderScheduler.scheduleAll(this);
        renderShell();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            currentProfileId = resolveProfileId(currentProfileId);
            ReminderScheduler.scheduleAll(this);
            renderShell();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification reminders are enabled.", Toast.LENGTH_SHORT).show();
                handleAlertsTap();
            } else {
                Toast.makeText(this, "Notifications are off. Schedules still stay saved.", Toast.LENGTH_LONG).show();
            }
            renderShell();
        }
    }

    private void renderShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_SURFACE);
        root.setPadding(dp(16), dp(12), dp(16), 0);

        root.addView(headerPanel());
        root.addView(tabRow());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
        renderCurrentTab();
    }

    private View headerPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(14));
        panel.setBackground(roundedGradient(
                new int[]{
                        Color.rgb(222, 244, 231),
                        Color.rgb(255, 237, 215),
                        Color.rgb(236, 242, 255)
                },
                dp(26)
        ));
        panel.setElevation(dp(2));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        String profileName = selectedProfileName();
        TextView mark = text(profileInitials(profileName), 18, Color.WHITE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(COLOR_GREEN, Color.TRANSPARENT, dp(24)));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        markParams.rightMargin = dp(12);
        top.addView(mark, markParams);

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        TextView headline = displayText(profileName, 28, COLOR_INK);
        headline.setSingleLine(true);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        titleGroup.addView(headline);
        TextView date = text("Today - " + LocalDate.now().format(dateFormatter), 13, COLOR_MUTED, Typeface.BOLD);
        titleGroup.addView(date);
        top.addView(titleGroup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button add = button("+ Med", Color.WHITE, COLOR_GREEN);
        add.setOnClickListener(view -> showMedicationDialog(null));
        top.addView(add, compactButtonParams());
        panel.addView(top);

        Button profileButton = button("Manage profiles", COLOR_BLUE, Color.WHITE);
        profileButton.setOnClickListener(view -> showProfilesDialog());
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        profileParams.topMargin = dp(12);
        panel.addView(profileButton, profileParams);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, 0);
        List<Medication> medications = store.getAllMedications(currentProfileId);
        int todayCount = doseRowsFor(LocalDate.now(zoneId)).size();
        long lowCount = medications.stream().filter(Medication::isLowStock).count();
        stats.addView(summaryPill(plural(todayCount, "dose", "doses"), COLOR_GREEN, COLOR_GREEN_SOFT));
        stats.addView(summaryPill(plural(medications.size(), "med", "meds"), COLOR_BLUE, COLOR_BLUE_SOFT));
        stats.addView(summaryPill(plural(lowCount, "refill", "refills"), COLOR_GOLD, COLOR_GOLD_SOFT));
        panel.addView(stats);

        Button alerts = button(alertsLabel(), alertColor(), Color.WHITE);
        alerts.setOnClickListener(view -> handleAlertsTap());
        LinearLayout.LayoutParams alertParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        alertParams.topMargin = dp(12);
        panel.addView(alerts, alertParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(12);
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout tabRow() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(rounded(COLOR_TAB_TRACK, Color.TRANSPARENT, dp(20)));
        tabs.addView(tabButton("Today", "today"));
        tabs.addView(tabButton("Meds", "meds"));
        tabs.addView(tabButton("Stock", "stock"));
        return tabs;
    }

    private Button tabButton(String label, String tab) {
        boolean selected = currentTab.equals(tab);
        Button button = button(label, selected ? Color.WHITE : COLOR_MUTED, selected ? COLOR_GREEN : Color.TRANSPARENT);
        button.setOnClickListener(view -> {
            currentTab = tab;
            renderShell();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        button.setLayoutParams(params);
        return button;
    }

    private void renderCurrentTab() {
        content.removeAllViews();
        if ("meds".equals(currentTab)) {
            renderMedications();
        } else if ("stock".equals(currentTab)) {
            renderInventory();
        } else {
            renderToday();
        }
    }

    private void renderToday() {
        LocalDate today = LocalDate.now(zoneId);
        List<DoseRow> rows = doseRowsFor(today);
        String profileName = selectedProfileName();

        content.addView(sectionTitle("Today", profileName + " has " + rows.size() + " scheduled doses"));

        if (store.getActiveMedications(currentProfileId).isEmpty()) {
            emptyState("Add the first medication for " + profileName + ".", "Add medication", view -> showMedicationDialog(null));
            return;
        }

        if (rows.isEmpty()) {
            emptyState("No active doses are scheduled for " + profileName + " today.", "Add medication", view -> showMedicationDialog(null));
            return;
        }

        for (DoseRow row : rows) {
            content.addView(doseCard(row));
        }

        TextView footer = text("Always follow your prescriber's directions.", 12, COLOR_MUTED, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(12), 0, 0);
        content.addView(footer);
    }

    private void renderMedications() {
        List<Medication> medications = store.getAllMedications(currentProfileId);
        content.addView(sectionTitle("Medications", selectedProfileName() + " has " + medications.size() + " saved"));

        if (medications.isEmpty()) {
            emptyState("Add names, doses, instructions, and reminders for " + selectedProfileName() + ".", "Add medication", view -> showMedicationDialog(null));
            return;
        }

        for (Medication medication : medications) {
            content.addView(medicationCard(medication));
        }
    }

    private void renderInventory() {
        List<Medication> medications = store.getAllMedications(currentProfileId);
        long lowCount = medications.stream().filter(Medication::isLowStock).count();
        content.addView(sectionTitle("Stock", selectedProfileName() + " has " + lowCount + " low stock"));

        if (medications.isEmpty()) {
            emptyState("Inventory for " + selectedProfileName() + " appears here after adding meds.", "Add medication", view -> showMedicationDialog(null));
            return;
        }

        for (Medication medication : medications) {
            content.addView(inventoryCard(medication));
        }
    }

    private View doseCard(DoseRow row) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView time = timePill(formatTime(row.scheduledAt));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(dp(82), dp(50));
        timeParams.rightMargin = dp(12);
        top.addView(time, timeParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(row.medication.name, 19, COLOR_INK, Typeface.BOLD));
        details.addView(text(row.medication.dosage, 14, COLOR_MUTED, Typeface.NORMAL));
        if (!row.medication.instructions.isEmpty()) {
            details.addView(text(row.medication.instructions, 14, COLOR_MUTED, Typeface.NORMAL));
        }
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(rowStatus(row)));
        card.addView(top);

        if (row.status == null) {
            LinearLayout actions = actionRow();
            Button taken = button("Taken", COLOR_GREEN, COLOR_GREEN_SOFT);
            taken.setOnClickListener(view -> markDose(row, MedicationStore.STATUS_TAKEN));
            actions.addView(taken, weightedActionParams());

            Button skip = button("Skip", COLOR_CORAL, COLOR_CORAL_SOFT);
            skip.setOnClickListener(view -> markDose(row, MedicationStore.STATUS_SKIPPED));
            actions.addView(skip, weightedActionParams());
            card.addView(actions);
        }

        if (row.medication.isLowStock()) {
            TextView lowStock = text("Low stock: " + row.medication.quantity + " left", 13, COLOR_CORAL, Typeface.BOLD);
            lowStock.setPadding(0, dp(8), 0, 0);
            card.addView(lowStock);
        }

        return card;
    }

    private View medicationCard(Medication medication) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(medication.name, 19, COLOR_INK, Typeface.BOLD));
        details.addView(text(medication.dosage, 14, COLOR_MUTED, Typeface.NORMAL));
        details.addView(text(medication.doseCountLabel() + " at " + medication.scheduleSummary(), 14, COLOR_MUTED, Typeface.NORMAL));
        if (!medication.instructions.isEmpty()) {
            details.addView(text(medication.instructions, 14, COLOR_MUTED, Typeface.NORMAL));
        }
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(medication.active ? "Active" : "Paused"));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button edit = button("Edit", COLOR_BLUE, COLOR_BLUE_SOFT);
        edit.setOnClickListener(view -> showMedicationDialog(medication));
        actions.addView(edit, weightedActionParams());

        Button toggle = button(medication.active ? "Pause" : "Resume", COLOR_GREEN, COLOR_GREEN_SOFT);
        toggle.setOnClickListener(view -> toggleMedication(medication));
        actions.addView(toggle, weightedActionParams());

        Button delete = button("Delete", COLOR_CORAL, COLOR_CORAL_SOFT);
        delete.setOnClickListener(view -> confirmDelete(medication));
        actions.addView(delete, weightedActionParams());
        card.addView(actions);
        return card;
    }

    private View inventoryCard(Medication medication) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(medication.name, 19, COLOR_INK, Typeface.BOLD));
        details.addView(text(medication.quantity + " remaining", 14, medication.isLowStock() ? COLOR_CORAL : COLOR_MUTED, Typeface.BOLD));
        details.addView(text("Refill threshold: " + medication.refillThreshold, 13, COLOR_MUTED, Typeface.NORMAL));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(medication.isLowStock() ? "Refill" : "OK"));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button minus = button("-1", COLOR_CORAL, COLOR_CORAL_SOFT);
        minus.setOnClickListener(view -> adjustInventory(medication, -1));
        actions.addView(minus, weightedActionParams());

        Button plus = button("+10", COLOR_GREEN, COLOR_GREEN_SOFT);
        plus.setOnClickListener(view -> adjustInventory(medication, 10));
        actions.addView(plus, weightedActionParams());

        Button set = button("Set", COLOR_BLUE, COLOR_BLUE_SOFT);
        set.setOnClickListener(view -> showInventoryDialog(medication));
        actions.addView(set, weightedActionParams());
        card.addView(actions);
        return card;
    }

    private void showProfilesDialog() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(8), dp(18), 0);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (Profile profile : store.getProfiles()) {
            list.addView(profileManagementRow(profile, dialogRef));
        }

        Button addProfile = button("+ New profile", COLOR_GREEN, COLOR_GREEN_SOFT);
        addProfile.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showAddProfileDialog();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        addParams.topMargin = dp(12);
        list.addView(addProfile, addParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(list);

        dialogRef[0] = new AlertDialog.Builder(this)
                .setTitle("Profiles")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();
        dialogRef[0].show();
    }

    private View profileManagementRow(Profile profile, AlertDialog[] dialogRef) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(12));
        row.setBackground(rounded(COLOR_CARD, COLOR_BORDER, dp(20)));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        boolean selected = profile.id == currentProfileId;
        TextView avatar = text(profileInitials(profile.name), 14, Color.WHITE, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(selected ? COLOR_GREEN : COLOR_BLUE, Color.TRANSPARENT, dp(18)));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        avatarParams.rightMargin = dp(10);
        top.addView(avatar, avatarParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(profile.name, 17, COLOR_INK, Typeface.BOLD));
        String subtitle = selected ? "Current profile" : plural(store.getMedicationCountForProfile(profile.id), "med", "meds");
        labels.addView(text(subtitle, 12, COLOR_MUTED, Typeface.BOLD));
        top.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(top);

        LinearLayout actions = actionRow();
        Button switchButton = button(selected ? "Current" : "Switch", selected ? COLOR_GREEN : COLOR_BLUE, selected ? COLOR_GREEN_SOFT : COLOR_BLUE_SOFT);
        switchButton.setOnClickListener(view -> {
            setSelectedProfileId(profile.id);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            renderShell();
        });
        actions.addView(switchButton, weightedActionParams());

        Button rename = button("Rename", COLOR_BLUE, COLOR_BLUE_SOFT);
        rename.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showRenameProfileDialog(profile);
        });
        actions.addView(rename, weightedActionParams());

        Button delete = button("Delete", COLOR_CORAL, COLOR_CORAL_SOFT);
        delete.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            confirmDeleteProfile(profile);
        });
        actions.addView(delete, weightedActionParams());
        row.addView(actions);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private void showRenameProfileDialog(Profile profile) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText nameField = field("Profile name", profile.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(nameField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rename profile")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                String name = nameField.getText().toString().trim();
                if (name.isEmpty()) {
                    nameField.setError("Required");
                    return;
                }
                store.renameProfile(profile.id, name);
                dialog.dismiss();
                renderShell();
            });
        });

        dialog.show();
    }

    private void confirmDeleteProfile(Profile profile) {
        if (store.getProfiles().size() <= 1) {
            Toast.makeText(this, "Keep at least one profile.", Toast.LENGTH_SHORT).show();
            return;
        }

        int medicationCount = store.getMedicationCountForProfile(profile.id);
        String message = "This removes " + profile.name + " and " +
                plural(medicationCount, "medication", "medications") +
                " with dose history from this phone.";
        new AlertDialog.Builder(this)
                .setTitle("Delete " + profile.name + "?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (Medication medication : store.getAllMedications(profile.id)) {
                        ReminderScheduler.cancel(this, medication.id);
                    }
                    boolean deleted = store.deleteProfile(profile.id);
                    if (deleted && currentProfileId == profile.id) {
                        List<Profile> profiles = store.getProfiles();
                        if (!profiles.isEmpty()) {
                            setSelectedProfileId(profiles.get(0).id);
                        }
                    }
                    ReminderScheduler.scheduleAll(this);
                    renderShell();
                })
                .show();
    }

    private void showAddProfileDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText nameField = field("Person's name", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(nameField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New profile")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button create = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            create.setOnClickListener(view -> {
                String name = nameField.getText().toString().trim();
                if (name.isEmpty()) {
                    nameField.setError("Required");
                    return;
                }
                long profileId = store.saveProfile(name);
                setSelectedProfileId(profileId);
                dialog.dismiss();
                renderShell();
            });
        });

        dialog.show();
    }

    private void showMedicationDialog(Medication existing) {
        Medication medication = existing == null ? Medication.empty() : new Medication(
                existing.id,
                existing.profileId,
                existing.name,
                existing.dosage,
                existing.instructions,
                existing.firstDoseMinutes,
                existing.dosesPerDay,
                existing.quantity,
                existing.refillThreshold,
                existing.active,
                existing.createdAt
        );
        if (existing == null) {
            medication.profileId = currentProfileId;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(10), dp(18), 0);

        EditText nameField = field("Medication name", medication.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText dosageField = field("Dosage", medication.dosage, InputType.TYPE_CLASS_TEXT);
        EditText instructionsField = field("Instructions", medication.instructions, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        final int[] selectedMinutes = {medication.firstDoseMinutes};
        EditText timeField = field("First dose time", Medication.formatMinutes(selectedMinutes[0]), InputType.TYPE_NULL);
        timeField.setFocusable(false);
        timeField.setOnClickListener(view -> showTimePicker(timeField, selectedMinutes));

        Spinner dosesSpinner = new Spinner(this);
        String[] doseOptions = {
                "1 dose/day",
                "2 doses/day",
                "3 doses/day",
                "4 doses/day",
                "5 doses/day",
                "6 doses/day",
                "7 doses/day",
                "8 doses/day"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, doseOptions);
        dosesSpinner.setAdapter(adapter);
        dosesSpinner.setSelection(Math.max(0, medication.dosesPerDay - 1));
        dosesSpinner.setPadding(dp(10), 0, dp(10), 0);
        dosesSpinner.setBackground(rounded(COLOR_CARD, COLOR_BORDER, dp(18)));

        EditText quantityField = field("Current quantity", String.valueOf(medication.quantity), InputType.TYPE_CLASS_NUMBER);
        EditText thresholdField = field("Refill threshold", String.valueOf(medication.refillThreshold), InputType.TYPE_CLASS_NUMBER);
        CheckBox activeBox = new CheckBox(this);
        activeBox.setText("Active reminders");
        activeBox.setTextColor(COLOR_INK);
        activeBox.setTextSize(15);
        activeBox.setChecked(medication.active);

        form.addView(nameField);
        form.addView(dosageField);
        form.addView(instructionsField);
        form.addView(timeField);
        form.addView(fieldLabel("Frequency"));
        form.addView(dosesSpinner);
        form.addView(quantityField);
        form.addView(thresholdField);
        form.addView(activeBox);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add medication" : "Edit medication")
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
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
                        selectedMinutes[0],
                        dosesSpinner.getSelectedItemPosition() + 1,
                        parseInt(quantityField, 0),
                        parseInt(thresholdField, 0),
                        activeBox.isChecked(),
                        medication.createdAt
                );

                store.saveMedication(toSave);
                if (toSave.active) {
                    ReminderScheduler.scheduleNext(this, toSave);
                } else {
                    ReminderScheduler.cancel(this, toSave.id);
                }
                dialog.dismiss();
                renderShell();
            });
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showInventoryDialog(Medication medication) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText quantityField = field("Current quantity", String.valueOf(medication.quantity), InputType.TYPE_CLASS_NUMBER);
        EditText thresholdField = field("Refill threshold", String.valueOf(medication.refillThreshold), InputType.TYPE_CLASS_NUMBER);
        form.addView(quantityField);
        form.addView(thresholdField);

        AlertDialog dialog = new AlertDialog.Builder(this)
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
                renderShell();
            });
        });

        dialog.show();
    }

    private void showTimePicker(EditText timeField, int[] selectedMinutes) {
        int hour = selectedMinutes[0] / 60;
        int minute = selectedMinutes[0] % 60;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    selectedMinutes[0] = (selectedHour * 60) + selectedMinute;
                    timeField.setText(Medication.formatMinutes(selectedMinutes[0]));
                },
                hour,
                minute,
                false
        );
        dialog.show();
    }

    private void confirmDelete(Medication medication) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + medication.name + "?")
                .setMessage("This removes the medication and its dose history from this phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    ReminderScheduler.cancel(this, medication.id);
                    store.deleteMedication(medication.id);
                    renderShell();
                })
                .show();
    }

    private void toggleMedication(Medication medication) {
        medication.active = !medication.active;
        store.saveMedication(medication);
        if (medication.active) {
            ReminderScheduler.scheduleNext(this, medication);
        } else {
            ReminderScheduler.cancel(this, medication.id);
        }
        renderShell();
    }

    private void adjustInventory(Medication medication, int delta) {
        store.adjustInventory(medication.id, delta);
        renderShell();
    }

    private void markDose(DoseRow row, String status) {
        store.logDose(row.medication.id, row.scheduledAt, status);
        if (MedicationStore.STATUS_TAKEN.equals(status)) {
            store.adjustInventory(row.medication.id, -1);
        }
        ReminderScheduler.scheduleNext(this, store.getMedication(row.medication.id));
        renderShell();
    }

    private List<DoseRow> doseRowsFor(LocalDate date) {
        long start = date.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        Map<String, String> logs = store.getDoseLogsBetween(start, end);

        List<DoseRow> rows = new ArrayList<>();
        for (Medication medication : store.getActiveMedications(currentProfileId)) {
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

    private long loadSelectedProfileId() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        long savedProfileId = preferences.getLong(PREF_SELECTED_PROFILE_ID, 0);
        long profileId = resolveProfileId(savedProfileId);
        preferences.edit().putLong(PREF_SELECTED_PROFILE_ID, profileId).apply();
        return profileId;
    }

    private long resolveProfileId(long profileId) {
        if (profileId > 0 && store.getProfile(profileId) != null) {
            return profileId;
        }
        return store.ensureDefaultProfile();
    }

    private void setSelectedProfileId(long profileId) {
        currentProfileId = resolveProfileId(profileId);
        getPreferences(MODE_PRIVATE)
                .edit()
                .putLong(PREF_SELECTED_PROFILE_ID, currentProfileId)
                .apply();
    }

    private String selectedProfileName() {
        Profile profile = store.getProfile(currentProfileId);
        if (profile == null) {
            currentProfileId = resolveProfileId(currentProfileId);
            profile = store.getProfile(currentProfileId);
        }
        return profile == null ? "Me" : profile.name;
    }

    private String formatTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormatter);
    }

    private void handleAlertsTap() {
        if (needsNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            }
            return;
        }

        if (!ReminderScheduler.canScheduleExactAlarms(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(ReminderScheduler.exactAlarmSettingsIntent(this));
            return;
        }

        ReminderScheduler.scheduleAll(this);
        Toast.makeText(this, "Reminder alerts are ready.", Toast.LENGTH_SHORT).show();
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private String alertsLabel() {
        if (needsNotificationPermission()) {
            return "Enable alerts";
        }
        if (!ReminderScheduler.canScheduleExactAlarms(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return "Alarm access";
        }
        return "Alerts on";
    }

    private int alertColor() {
        if (needsNotificationPermission()) {
            return COLOR_CORAL;
        }
        if (!ReminderScheduler.canScheduleExactAlarms(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return COLOR_BLUE;
        }
        return COLOR_GREEN;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setPadding(0, dp(8), 0, dp(8));

        View marker = new View(this);
        marker.setBackground(rounded(COLOR_GREEN, Color.TRANSPARENT, dp(3)));
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(5), dp(42));
        markerParams.rightMargin = dp(10);
        group.addView(marker, markerParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 21, COLOR_INK, Typeface.BOLD));
        labels.addView(text(subtitle, 13, COLOR_MUTED, Typeface.BOLD));
        group.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return group;
    }

    private void emptyState(String message, String action, View.OnClickListener listener) {
        LinearLayout state = new LinearLayout(this);
        state.setOrientation(LinearLayout.VERTICAL);
        state.setGravity(Gravity.CENTER_HORIZONTAL);
        state.setPadding(dp(18), dp(28), dp(18), dp(28));
        state.setBackground(roundedGradient(
                new int[]{
                        Color.rgb(255, 251, 239),
                        Color.rgb(229, 244, 238)
                },
                dp(24)
        ));
        state.setElevation(dp(1));

        TextView mark = text("Rx", 24, Color.WHITE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(COLOR_GREEN, Color.TRANSPARENT, dp(28)));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        markParams.bottomMargin = dp(14);
        state.addView(mark, markParams);

        TextView messageView = text(message, 16, COLOR_MUTED, Typeface.BOLD);
        messageView.setGravity(Gravity.CENTER);
        state.addView(messageView);

        Button button = button(action, COLOR_GREEN, COLOR_GREEN_SOFT);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        params.topMargin = dp(16);
        state.addView(button, params);

        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stateParams.topMargin = dp(8);
        content.addView(state, stateParams);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(COLOR_CARD, COLOR_BORDER, dp(22)));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout actionRow() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        return actions;
    }

    private TextView statusBadge(String label) {
        int textColor = COLOR_BLUE;
        int background = COLOR_BLUE_SOFT;
        if ("Taken".equals(label) || "Active".equals(label) || "OK".equals(label)) {
            textColor = COLOR_GREEN;
            background = COLOR_GREEN_SOFT;
        } else if ("Due".equals(label) || "Skipped".equals(label) || "Paused".equals(label) || "Refill".equals(label)) {
            textColor = COLOR_CORAL;
            background = COLOR_CORAL_SOFT;
        }
        TextView badge = text(label, 12, textColor, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        badge.setBackground(rounded(background, Color.TRANSPARENT, dp(16)));
        return badge;
    }

    private TextView timePill(String value) {
        TextView pill = text(value, 13, COLOR_BLUE, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(rounded(COLOR_BLUE_SOFT, Color.TRANSPARENT, dp(18)));
        return pill;
    }

    private TextView summaryPill(String label, int textColor, int background) {
        TextView pill = text(label, 12, textColor, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(7), dp(10), dp(7));
        pill.setBackground(rounded(background, Color.TRANSPARENT, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.rightMargin = dp(8);
        pill.setLayoutParams(params);
        return pill;
    }

    private TextView displayText(String value, int sp, int color) {
        TextView textView = text(value, sp, color, Typeface.BOLD);
        textView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        textView.setIncludeFontPadding(false);
        return textView;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextColor(color);
        textView.setTextSize(sp);
        textView.setTypeface(Typeface.create("sans-serif", style));
        textView.setLineSpacing(dp(2), 1.0f);
        return textView;
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 13, COLOR_MUTED, Typeface.BOLD);
        label.setPadding(0, dp(10), 0, dp(3));
        return label;
    }

    private EditText field(String hint, String value, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(inputType);
        field.setSingleLine(!hint.equals("Instructions"));
        field.setTextColor(COLOR_INK);
        field.setHintTextColor(COLOR_MUTED);
        field.setTextSize(15);
        field.setPadding(dp(14), dp(10), dp(14), dp(10));
        field.setMinHeight(dp(48));
        field.setBackground(rounded(COLOR_CARD, COLOR_BORDER, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        field.setLayoutParams(params);
        return field;
    }

    private Button button(String label, int textColor, int backgroundColor) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setMinHeight(dp(42));
        button.setMinWidth(dp(68));
        button.setPadding(dp(12), 0, dp(12), 0);
        int stroke = backgroundColor == Color.TRANSPARENT || backgroundColor == Color.WHITE
                ? COLOR_BORDER
                : Color.TRANSPARENT;
        button.setBackground(rounded(backgroundColor, stroke, dp(18)));
        return button;
    }

    private GradientDrawable rounded(int color, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable roundedGradient(int[] colors, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams compactButtonParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
    }

    private LinearLayout.LayoutParams weightedActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.rightMargin = dp(8);
        return params;
    }

    private String plural(long count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private String profileInitials(String name) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            return "ME";
        }

        String[] parts = cleanName.split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }
        return initials.length() == 0 ? "ME" : initials.toString();
    }

    private int parseInt(EditText field, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(field.getText().toString().trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
