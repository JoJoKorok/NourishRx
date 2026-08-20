package com.jojokorok.nourishrx.nutrition;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.WeightEntry;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishShapes;
import com.jojokorok.nourishrx.ui.NourishSpacing;
import com.jojokorok.nourishrx.ui.NourishTypography;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NutritionTrackingFlow {
    public interface Callbacks {
        long currentProfileId();

        void showLogFoodDialog(String mealName);

        void onTrackingChanged();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final ZoneId zoneId;
    private final Callbacks callbacks;
    private final DateTimeFormatter shortDateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault());

    public NutritionTrackingFlow(
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

    public View waterCard(int waterOunces, long startMillis, long endMillis) {
        LinearLayout card = ui.card();
        card.setElevation(ui.dp(NourishShapes.ELEVATION_FLAT));
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.displayText(
                "Water",
                NourishTypography.BODY_LARGE,
                NourishColors.INK
        ));
        details.addView(
                ui.text(
                        waterOunces + " oz logged today",
                        NourishTypography.LABEL,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                ),
                wrapParams(NourishSpacing.XXS)
        );
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(waterStatusBadge(waterOunces >= 64));
        card.addView(top);

        LinearLayout progressLabels = new LinearLayout(activity);
        progressLabels.setOrientation(LinearLayout.HORIZONTAL);
        progressLabels.setGravity(Gravity.CENTER_VERTICAL);
        progressLabels.addView(
                ui.text(
                        "Daily progress",
                        NourishTypography.CAPTION,
                        NourishColors.INK_SECONDARY,
                        Typeface.BOLD
                ),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)
        );
        TextView reference = ui.text(
                Math.min(waterOunces, 64) + " of 64 oz",
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.NORMAL
        );
        reference.setGravity(Gravity.END);
        progressLabels.addView(reference);
        card.addView(progressLabels, wrapParams(NourishSpacing.MD));

        ProgressBar progress = new ProgressBar(
                activity,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        progress.setMax(64);
        progress.setProgress(Math.min(waterOunces, 64));
        progress.setProgressTintList(ColorStateList.valueOf(NourishColors.GREEN));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(NourishColors.SURFACE_SUBTLE));
        card.addView(progress, matchParams(8, NourishSpacing.XS));

        card.addView(sectionLabel("Quick add"));
        LinearLayout actions = actionRow();
        Button addEight = ui.button("+8 oz", NourishColors.BLUE, Color.TRANSPARENT);
        addEight.setSingleLine(true);
        addEight.setOnClickListener(view -> addWaterAndRefresh(8));
        actions.addView(addEight, weightedActionParams(false));

        Button addSixteen = ui.button("+16 oz", NourishColors.GREEN_DARK, Color.TRANSPARENT);
        addSixteen.setSingleLine(true);
        addSixteen.setOnClickListener(view -> addWaterAndRefresh(16));
        actions.addView(addSixteen, weightedActionParams(false));

        Button custom = ui.button("Custom", NourishColors.GOLD, Color.TRANSPARENT);
        custom.setSingleLine(true);
        custom.setOnClickListener(view -> showWaterDialog());
        actions.addView(custom, weightedActionParams(true));
        card.addView(actions);

        if (waterOunces > 0) {
            Button clear = ui.button("Clear today's water", NourishColors.CORAL, Color.TRANSPARENT);
            clear.setSingleLine(true);
            clear.setOnClickListener(view -> confirmClearWater(startMillis, endMillis));
            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(44)
            );
            clearParams.topMargin = ui.dp(NourishSpacing.XS);
            card.addView(clear, clearParams);
        }
        return card;
    }

    public View weightCard(List<WeightEntry> weights) {
        LinearLayout card = ui.card();
        card.setElevation(ui.dp(NourishShapes.ELEVATION_FLAT));
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.displayText(
                "Weight",
                NourishTypography.BODY_LARGE,
                NourishColors.INK
        ));
        if (weights.isEmpty()) {
            details.addView(
                    ui.text(
                            "No weight logged yet",
                            NourishTypography.LABEL,
                            NourishColors.MUTED,
                            Typeface.NORMAL
                    ),
                    wrapParams(NourishSpacing.XXS)
            );
        } else {
            WeightEntry latest = weights.get(0);
            details.addView(
                    ui.displayText(
                            formatPounds(latest.pounds) + " lb",
                            NourishTypography.TITLE,
                            NourishColors.INK
                    ),
                    wrapParams(NourishSpacing.XS)
            );
            details.addView(ui.text(
                    "Latest entry - " + formatShortDateTime(latest.loggedAt),
                    NourishTypography.CAPTION,
                    NourishColors.MUTED,
                    Typeface.NORMAL
            ));
        }
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button add = ui.button("Log weight", NourishColors.ON_ACCENT, NourishColors.GREEN);
        add.setSingleLine(true);
        add.setOnClickListener(view -> showWeightDialog());
        LinearLayout.LayoutParams addParams = compactButtonParams();
        addParams.leftMargin = ui.dp(NourishSpacing.SM);
        top.addView(add, addParams);
        card.addView(top);

        if (!weights.isEmpty()) {
            card.addView(sectionLabel("Recent entries"));
        }
        for (WeightEntry entry : weights) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, ui.dp(NourishSpacing.XS), 0, ui.dp(NourishSpacing.XS));

            LinearLayout label = new LinearLayout(activity);
            label.setOrientation(LinearLayout.VERTICAL);
            label.addView(ui.text(
                    formatPounds(entry.pounds) + " lb",
                    NourishTypography.BODY,
                    NourishColors.INK,
                    Typeface.BOLD
            ));
            label.addView(ui.text(
                    formatShortDateTime(entry.loggedAt),
                    NourishTypography.CAPTION,
                    NourishColors.MUTED,
                    Typeface.NORMAL
            ));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button delete = ui.button("Remove", NourishColors.CORAL, Color.TRANSPARENT);
            delete.setSingleLine(true);
            delete.setOnClickListener(view -> {
                store.deleteWeightEntry(entry.id);
                callbacks.onTrackingChanged();
            });
            row.addView(delete, new LinearLayout.LayoutParams(ui.dp(92), ui.dp(40)));
            card.addView(divider());
            card.addView(row);
        }
        return card;
    }

    public void showMealDefaultsDialog() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        LinearLayout defaultsList = new LinearLayout(activity);
        defaultsList.setOrientation(LinearLayout.VERTICAL);
        renderMealDefaultRows(defaultsList, new ArrayList<>(store.getMealDefaults(callbacks.currentProfileId())));
        form.addView(defaultsList);

        Button addDefault = ui.button("+ Default meal", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        addDefault.setOnClickListener(view -> {
            ArrayList<String> names = mealDefaultNamesFrom(defaultsList);
            names.add("Meal " + (names.size() + 1));
            renderMealDefaultRows(defaultsList, names);
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        addParams.topMargin = ui.dp(10);
        form.addView(addDefault, addParams);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Default meals")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                ArrayList<String> names = mealDefaultNamesFrom(defaultsList);
                if (names.isEmpty()) {
                    Toast.makeText(activity, "Keep at least one default meal.", Toast.LENGTH_SHORT).show();
                    return;
                }
                store.saveMealDefaults(callbacks.currentProfileId(), names);
                dialog.dismiss();
                callbacks.onTrackingChanged();
            });
        });

        dialog.show();
    }

    private void renderMealDefaultRows(LinearLayout container, ArrayList<String> mealNames) {
        container.removeAllViews();
        if (mealNames.isEmpty()) {
            mealNames.add("Meal 1");
        }

        for (int i = 0; i < mealNames.size(); i++) {
            int index = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, ui.dp(8), 0, 0);

            EditText nameField = ui.field(
                    "Meal name",
                    mealNames.get(i),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
            );
            row.addView(nameField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button remove = ui.button("Remove", NourishColors.CORAL, NourishColors.CORAL_SOFT);
            remove.setEnabled(mealNames.size() > 1);
            remove.setAlpha(mealNames.size() > 1 ? 1.0f : 0.45f);
            remove.setOnClickListener(view -> {
                ArrayList<String> names = mealDefaultNamesFrom(container);
                if (names.size() <= 1) {
                    Toast.makeText(activity, "Keep at least one default meal.", Toast.LENGTH_SHORT).show();
                    return;
                }
                names.remove(index);
                renderMealDefaultRows(container, names);
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(ui.dp(96), ui.dp(48));
            removeParams.leftMargin = ui.dp(8);
            row.addView(remove, removeParams);

            container.addView(row);
        }
    }

    private ArrayList<String> mealDefaultNamesFrom(LinearLayout container) {
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                if (row.getChildCount() > 0 && row.getChildAt(0) instanceof EditText) {
                    String name = ((EditText) row.getChildAt(0)).getText().toString().trim();
                    if (!name.isEmpty() && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }

    private void addWaterAndRefresh(int ounces) {
        store.addWater(callbacks.currentProfileId(), ounces);
        callbacks.onTrackingChanged();
    }

    public void showWaterDialog() {
        LinearLayout form = dialogBody();
        form.addView(dialogHeader(
                "Add water",
                "Log a custom amount for the current profile."
        ));
        form.addView(ui.fieldLabel("Amount in ounces"));
        EditText ouncesField = ui.field("Example: 12", "", InputType.TYPE_CLASS_NUMBER);
        form.addView(ouncesField);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            styleDialogActions(dialog, false);
            Button add = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            add.setOnClickListener(view -> {
                int ounces = parseInt(ouncesField, 0);
                if (ounces <= 0) {
                    ouncesField.setError("Enter ounces");
                    return;
                }
                store.addWater(callbacks.currentProfileId(), ounces);
                dialog.dismiss();
                callbacks.onTrackingChanged();
            });
            ouncesField.requestFocus();
        });

        dialog.show();
    }

    private void confirmClearWater(long startMillis, long endMillis) {
        LinearLayout content = dialogBody();
        content.addView(dialogHeader(
                "Clear today's water?",
                "This removes every water entry logged today for the current profile."
        ));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (ignored, which) -> {
                    store.clearWater(callbacks.currentProfileId(), startMillis, endMillis);
                    callbacks.onTrackingChanged();
                })
                .create();
        dialog.setOnShowListener(ignored -> styleDialogActions(dialog, true));
        dialog.show();
    }

    public void showWeightDialog() {
        LinearLayout form = dialogBody();
        form.addView(dialogHeader(
                "Log weight",
                "Add a dated weight entry for the current profile."
        ));
        form.addView(ui.fieldLabel("Weight in pounds"));
        EditText weightField = ui.field(
                "Example: 165.5",
                "",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        form.addView(weightField);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            styleDialogActions(dialog, false);
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                float pounds = parseFloat(weightField, 0.0f);
                if (pounds <= 0.0f) {
                    weightField.setError("Enter weight");
                    return;
                }
                store.saveWeightEntry(new WeightEntry(
                        0,
                        callbacks.currentProfileId(),
                        pounds,
                        System.currentTimeMillis()
                ));
                dialog.dismiss();
                callbacks.onTrackingChanged();
            });
            weightField.requestFocus();
        });

        dialog.show();
    }

    private LinearLayout actionRow() {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, ui.dp(12), 0, 0);
        return actions;
    }

    private LinearLayout.LayoutParams compactButtonParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ui.dp(44)
        );
    }

    private LinearLayout.LayoutParams weightedActionParams(boolean last) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(44), 1);
        if (!last) {
            params.rightMargin = ui.dp(NourishSpacing.XS);
        }
        return params;
    }

    private View sectionLabel(String title) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, ui.dp(NourishSpacing.LG), 0, 0);
        section.addView(divider());
        section.addView(
                ui.text(
                        title,
                        NourishTypography.CAPTION,
                        NourishColors.INK_SECONDARY,
                        Typeface.BOLD
                ),
                wrapParams(NourishSpacing.SM)
        );
        return section;
    }

    private View waterStatusBadge(boolean complete) {
        TextView badge = ui.text(
                complete ? "Hydrated" : "In progress",
                NourishTypography.CAPTION,
                complete ? NourishColors.GREEN_DARK : NourishColors.BLUE,
                Typeface.BOLD
        );
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setPadding(
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.XXS),
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.XXS)
        );
        badge.setBackground(ui.rounded(
                complete ? NourishColors.GREEN_SOFT : NourishColors.BLUE_SOFT,
                Color.TRANSPARENT,
                ui.dp(NourishShapes.RADIUS_CONTROL)
        ));
        return badge;
    }

    private View divider() {
        View divider = new View(activity);
        divider.setBackgroundColor(NourishColors.DIVIDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(1)
        ));
        return divider;
    }

    private LinearLayout dialogBody() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.SM)
        );
        return body;
    }

    private View dialogHeader(String title, String subtitle) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, 0, ui.dp(NourishSpacing.XS));
        header.addView(ui.displayText(title, NourishTypography.TITLE, NourishColors.INK));
        header.addView(
                ui.text(
                        subtitle,
                        NourishTypography.LABEL,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                ),
                wrapParams(NourishSpacing.XXS)
        );
        return header;
    }

    private void styleDialogActions(AlertDialog dialog, boolean destructive) {
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positive.setTextColor(destructive ? NourishColors.CORAL : NourishColors.GREEN);
        positive.setTypeface(Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.BOLD));

        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        negative.setTextColor(NourishColors.INK_SECONDARY);
        negative.setTypeface(Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL));
    }

    private LinearLayout.LayoutParams matchParams(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(height)
        );
        params.topMargin = ui.dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams wrapParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = ui.dp(topMargin);
        return params;
    }

    private String formatShortDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(shortDateTimeFormatter);
    }

    private String formatPounds(float pounds) {
        if (Math.abs(pounds - Math.round(pounds)) < 0.05f) {
            return String.valueOf(Math.round(pounds));
        }
        return String.format(Locale.getDefault(), "%.1f", pounds);
    }

    private int parseInt(EditText field, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(field.getText().toString().trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private float parseFloat(EditText field, float fallback) {
        try {
            return Math.max(0.0f, Float.parseFloat(field.getText().toString().trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
