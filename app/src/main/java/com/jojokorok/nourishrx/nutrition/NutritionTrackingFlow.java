package com.jojokorok.nourishrx.nutrition;

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
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.WeightEntry;
import com.jojokorok.nourishrx.ui.NourishColors;
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

    public View defaultMealsCard(List<String> mealDefaults) {
        LinearLayout card = ui.card();
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.text("Default meals", 19, NourishColors.INK, Typeface.BOLD));
        details.addView(ui.text(
                plural(mealDefaults.size(), "saved meal name", "saved meal names"),
                13,
                NourishColors.MUTED,
                Typeface.BOLD
        ));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button edit = ui.button("Edit", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        edit.setOnClickListener(view -> showMealDefaultsDialog());
        top.addView(edit, compactButtonParams());
        card.addView(top);

        for (String mealName : mealDefaults) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, ui.dp(10), 0, 0);

            TextView name = ui.text(mealName, 15, NourishColors.INK, Typeface.BOLD);
            row.addView(name, new LinearLayout.LayoutParams(0, ui.dp(42), 1));

            Button log = ui.button("Log", NourishColors.GREEN, NourishColors.GREEN_SOFT);
            log.setOnClickListener(view -> callbacks.showLogFoodDialog(mealName));
            row.addView(log, new LinearLayout.LayoutParams(ui.dp(88), ui.dp(42)));
            card.addView(row);
        }
        return card;
    }

    public View waterCard(int waterOunces, long startMillis, long endMillis) {
        LinearLayout card = ui.card();
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.text("Water", 19, NourishColors.INK, Typeface.BOLD));
        details.addView(ui.text(waterOunces + " oz today", 14, NourishColors.MUTED, Typeface.BOLD));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(ui.statusBadge(waterOunces >= 64 ? "Hydrated" : "Track"));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button addEight = ui.button("+8 oz", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        addEight.setOnClickListener(view -> addWaterAndRefresh(8));
        actions.addView(addEight, weightedActionParams());

        Button addSixteen = ui.button("+16 oz", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        addSixteen.setOnClickListener(view -> addWaterAndRefresh(16));
        actions.addView(addSixteen, weightedActionParams());

        Button custom = ui.button("Custom", NourishColors.GOLD, NourishColors.GOLD_SOFT);
        custom.setOnClickListener(view -> showWaterDialog());
        actions.addView(custom, weightedActionParams());
        card.addView(actions);

        if (waterOunces > 0) {
            Button clear = ui.button("Clear today", NourishColors.CORAL, NourishColors.CORAL_SOFT);
            clear.setOnClickListener(view -> confirmClearWater(startMillis, endMillis));
            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(44)
            );
            clearParams.topMargin = ui.dp(10);
            card.addView(clear, clearParams);
        }
        return card;
    }

    public View weightCard(List<WeightEntry> weights) {
        LinearLayout card = ui.card();
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.text("Weight", 19, NourishColors.INK, Typeface.BOLD));
        if (weights.isEmpty()) {
            details.addView(ui.text("No weight logged yet", 14, NourishColors.MUTED, Typeface.BOLD));
        } else {
            WeightEntry latest = weights.get(0);
            details.addView(ui.text(formatPounds(latest.pounds) + " lb latest", 14, NourishColors.MUTED, Typeface.BOLD));
            details.addView(ui.text(formatShortDateTime(latest.loggedAt), 13, NourishColors.MUTED, Typeface.NORMAL));
        }
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button add = ui.button("+ Weight", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        add.setOnClickListener(view -> showWeightDialog());
        top.addView(add, compactButtonParams());
        card.addView(top);

        for (WeightEntry entry : weights) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, ui.dp(10), 0, 0);

            LinearLayout label = new LinearLayout(activity);
            label.setOrientation(LinearLayout.VERTICAL);
            label.addView(ui.text(formatPounds(entry.pounds) + " lb", 15, NourishColors.INK, Typeface.BOLD));
            label.addView(ui.text(formatShortDateTime(entry.loggedAt), 12, NourishColors.MUTED, Typeface.NORMAL));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button delete = ui.button("Delete", NourishColors.CORAL, NourishColors.CORAL_SOFT);
            delete.setOnClickListener(view -> {
                store.deleteWeightEntry(entry.id);
                callbacks.onTrackingChanged();
            });
            row.addView(delete, new LinearLayout.LayoutParams(ui.dp(94), ui.dp(40)));
            card.addView(row);
        }
        return card;
    }

    private void showMealDefaultsDialog() {
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

    private void showWaterDialog() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText ouncesField = ui.field("Ounces", "", InputType.TYPE_CLASS_NUMBER);
        form.addView(ouncesField);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Add water")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
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
        });

        dialog.show();
    }

    private void confirmClearWater(long startMillis, long endMillis) {
        new AlertDialog.Builder(activity)
                .setTitle("Clear today's water?")
                .setMessage("This removes all water entries for today.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    store.clearWater(callbacks.currentProfileId(), startMillis, endMillis);
                    callbacks.onTrackingChanged();
                })
                .show();
    }

    private void showWeightDialog() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText weightField = ui.field(
                "Weight in pounds",
                "",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        form.addView(weightField);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Log weight")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
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

    private LinearLayout.LayoutParams weightedActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(44), 1);
        params.rightMargin = ui.dp(8);
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

    private String plural(long count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
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
