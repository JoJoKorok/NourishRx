package com.jojokorok.nourishrx.profiles;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.Profile;
import com.jojokorok.nourishrx.reminders.ReminderScheduler;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.util.List;

public class ProfileManagementFlow {
    public interface Callbacks {
        long currentProfileId();

        void setSelectedProfileId(long profileId);

        void renderShell();

        String plural(long count, String singular, String plural);

        View profileAvatar(Profile profile, int sizeDp, int fallbackColor, int textSp);

        int avatarWidthDp(Profile profile, int heightDp);

        void chooseProfilePhoto(Profile profile);

        void showProfilePhotoEditor(
                Profile profile,
                String avatarUri,
                float zoom,
                float offsetX,
                float offsetY,
                float aspectRatio
        );
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final Callbacks callbacks;

    public ProfileManagementFlow(
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

    public void showProfilesDialog() {
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (Profile profile : store.getProfiles()) {
            list.addView(profileManagementRow(profile, dialogRef));
        }

        Button addProfile = button("+ New profile", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        addProfile.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showAddProfileDialog();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        addParams.topMargin = ui.dp(12);
        list.addView(addProfile, addParams);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(list);

        dialogRef[0] = new AlertDialog.Builder(activity)
                .setTitle("Profiles")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();
        dialogRef[0].show();
    }

    private View profileManagementRow(Profile profile, AlertDialog[] dialogRef) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(12));
        row.setBackground(ui.rounded(NourishColors.CARD, NourishColors.BORDER, ui.dp(20)));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        boolean selected = profile.id == callbacks.currentProfileId();
        View avatar = callbacks.profileAvatar(profile, 38, selected ? NourishColors.GREEN : NourishColors.BLUE, 14);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                ui.dp(callbacks.avatarWidthDp(profile, 38)),
                ui.dp(38)
        );
        avatarParams.rightMargin = ui.dp(10);
        top.addView(avatar, avatarParams);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(profile.name, 17, NourishColors.INK, Typeface.BOLD));
        String subtitle = selected ? "Current profile" : callbacks.plural(store.getMedicationCountForProfile(profile.id), "med", "meds");
        labels.addView(text(subtitle, 12, NourishColors.MUTED, Typeface.BOLD));
        top.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(top);

        LinearLayout actions = actionRow();
        Button switchButton = button(
                selected ? "Current" : "Switch",
                selected ? NourishColors.GREEN : NourishColors.BLUE,
                selected ? NourishColors.GREEN_SOFT : NourishColors.BLUE_SOFT
        );
        switchButton.setOnClickListener(view -> {
            callbacks.setSelectedProfileId(profile.id);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            callbacks.renderShell();
        });
        actions.addView(switchButton, weightedActionParams());

        Button photo = button("Photo", NourishColors.GOLD, NourishColors.GOLD_SOFT);
        photo.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showProfilePhotoOptions(profile);
        });
        actions.addView(photo, weightedActionParams());
        row.addView(actions);

        LinearLayout editActions = actionRow();
        Button rename = button("Rename", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        rename.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showRenameProfileDialog(profile);
        });
        editActions.addView(rename, weightedActionParams());

        Button delete = button("Delete", NourishColors.CORAL, NourishColors.CORAL_SOFT);
        delete.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            confirmDeleteProfile(profile);
        });
        editActions.addView(delete, weightedActionParams());
        row.addView(editActions);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = ui.dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private void showProfilePhotoOptions(Profile profile) {
        if (!profile.hasAvatar()) {
            callbacks.chooseProfilePhoto(profile);
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(profile.name + " photo")
                .setItems(new CharSequence[]{"Edit framing", "Change photo", "Remove photo"}, (dialog, which) -> {
                    if (which == 0) {
                        callbacks.showProfilePhotoEditor(
                                profile,
                                profile.avatarUri,
                                profile.avatarZoom,
                                profile.avatarOffsetX,
                                profile.avatarOffsetY,
                                profile.avatarAspectRatio
                        );
                    } else if (which == 1) {
                        callbacks.chooseProfilePhoto(profile);
                    } else {
                        store.clearProfileAvatar(profile.id);
                        Toast.makeText(activity, "Profile photo removed.", Toast.LENGTH_SHORT).show();
                        callbacks.renderShell();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameProfileDialog(Profile profile) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText nameField = ui.field(
                "Profile name",
                profile.name,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        form.addView(nameField);

        AlertDialog dialog = new AlertDialog.Builder(activity)
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
                callbacks.renderShell();
            });
        });

        dialog.show();
    }

    private void confirmDeleteProfile(Profile profile) {
        if (store.getProfiles().size() <= 1) {
            Toast.makeText(activity, "Keep at least one profile.", Toast.LENGTH_SHORT).show();
            return;
        }

        int medicationCount = store.getMedicationCountForProfile(profile.id);
        String message = "This removes " + profile.name + " and " +
                callbacks.plural(medicationCount, "medication", "medications") +
                " with dose history from this phone.";
        new AlertDialog.Builder(activity)
                .setTitle("Delete " + profile.name + "?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (Medication medication : store.getAllMedications(profile.id)) {
                        ReminderScheduler.cancel(activity, medication.id);
                    }
                    boolean deleted = store.deleteProfile(profile.id);
                    if (deleted && callbacks.currentProfileId() == profile.id) {
                        List<Profile> profiles = store.getProfiles();
                        if (!profiles.isEmpty()) {
                            callbacks.setSelectedProfileId(profiles.get(0).id);
                        }
                    }
                    ReminderScheduler.scheduleAll(activity);
                    callbacks.renderShell();
                })
                .show();
    }

    private void showAddProfileDialog() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText nameField = ui.field(
                "Person's name",
                "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        form.addView(nameField);

        AlertDialog dialog = new AlertDialog.Builder(activity)
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
                callbacks.setSelectedProfileId(profileId);
                dialog.dismiss();
                callbacks.renderShell();
            });
        });

        dialog.show();
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

    private TextView text(String value, int sp, int color, int style) {
        return ui.text(value, sp, color, style);
    }

    private Button button(String label, int textColor, int backgroundColor) {
        return ui.button(label, textColor, backgroundColor);
    }
}
