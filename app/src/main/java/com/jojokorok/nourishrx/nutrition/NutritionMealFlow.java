package com.jojokorok.nourishrx.nutrition;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.MealFoodLog;
import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.NutritionFood;
import com.jojokorok.nourishrx.data.NutritionTotals;
import com.jojokorok.nourishrx.data.SavedMeal;
import com.jojokorok.nourishrx.data.SavedMealItem;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NutritionMealFlow {
    public interface Callbacks {
        long currentProfileId();

        void showFoodEditor();

        void onNutritionChanged();

        void onSavedMealLogged();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final ZoneId zoneId;
    private final Callbacks callbacks;

    public NutritionMealFlow(
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

    public void showLogFoodDialog(String presetName) {
        showLogFoodDialog(null, presetName, 0);
    }

    public void showLogFoodDialog(String presetName, long selectedFoodId) {
        showLogFoodDialog(null, presetName, selectedFoodId);
    }

    public void showSavedMealDialog(SavedMeal existing) {
        List<NutritionFood> foods = store.getNutritionFoods(callbacks.currentProfileId());
        if (foods.isEmpty()) {
            Toast.makeText(activity, "Create a food before saving a meal.", Toast.LENGTH_SHORT).show();
            callbacks.showFoodEditor();
            return;
        }

        SavedMeal savedMeal = existing == null
                ? new SavedMeal(0, callbacks.currentProfileId(), "", "", System.currentTimeMillis())
                : existing;

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        EditText nameField = ui.field(
                "Saved meal name",
                savedMeal.name.equals("Saved meal") && existing == null ? "" : savedMeal.name,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        EditText notesField = ui.field(
                "Notes",
                savedMeal.notes,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        form.addView(ui.fieldLabel("Meal"));
        form.addView(nameField);
        form.addView(notesField);

        LinearLayout itemsContainer = new LinearLayout(activity);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        ArrayList<SavedMealItemDraft> drafts = savedMealDraftsFromItems(existing == null
                ? new ArrayList<>()
                : store.getSavedMealItems(existing.id));
        if (drafts.isEmpty()) {
            drafts.add(new SavedMealItemDraft(foods.get(0).id, 0.0f));
        }
        renderSavedMealItemRows(itemsContainer, foods, drafts);

        form.addView(ui.fieldLabel("Foods"));
        form.addView(itemsContainer);

        Button addFood = ui.button("+ Food item", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        addFood.setOnClickListener(view -> {
            ArrayList<SavedMealItemDraft> updated = savedMealDraftsFromRows(itemsContainer, foods);
            updated.add(new SavedMealItemDraft(foods.get(0).id, 0.0f));
            renderSavedMealItemRows(itemsContainer, foods, updated);
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        addParams.topMargin = ui.dp(10);
        form.addView(addFood, addParams);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? "Create saved meal" : "Edit saved meal")
                .setView(scrollView)
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

                ArrayList<SavedMealItem> items = savedMealItemsFromRows(itemsContainer, foods);
                if (items == null) {
                    return;
                }
                if (items.isEmpty()) {
                    Toast.makeText(activity, "Add at least one food.", Toast.LENGTH_SHORT).show();
                    return;
                }

                store.saveSavedMeal(new SavedMeal(
                        savedMeal.id,
                        callbacks.currentProfileId(),
                        name,
                        notesField.getText().toString(),
                        savedMeal.createdAt
                ), items);
                dialog.dismiss();
                callbacks.onNutritionChanged();
            });
        });

        dialog.show();
    }

    public void showLogSavedMealDialog(SavedMeal savedMeal) {
        List<SavedMealItem> items = store.getSavedMealItems(savedMeal.id);
        if (items.isEmpty()) {
            Toast.makeText(activity, "Add foods before logging this saved meal.", Toast.LENGTH_SHORT).show();
            showSavedMealDialog(savedMeal);
            return;
        }

        NutritionTotals totals = totalsFor(items);
        long baseTime = System.currentTimeMillis();

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        form.addView(ui.fieldLabel("Saved meal"));
        form.addView(ui.text(savedMeal.name, 18, NourishColors.INK, Typeface.BOLD));
        form.addView(ui.text(savedMealItemsSummary(items), 13, NourishColors.MUTED, Typeface.NORMAL));
        form.addView(ui.text(nutritionTotalsLine(totals), 13, NourishColors.MUTED, Typeface.BOLD));

        EditText mealNameField = ui.field(
                "Meal name",
                defaultMealName(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        final boolean[] customMealTime = {false};
        final int[] mealMinutes = {minuteOfDay(baseTime)};
        LinearLayout timeActions = actionRow();
        Button timeButton = ui.button("Time: now", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        timeButton.setOnClickListener(view -> showMealTimePicker(timeButton, mealMinutes, customMealTime));
        timeActions.addView(timeButton, weightedActionParams());

        Button nowButton = ui.button("Use now", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        nowButton.setOnClickListener(view -> {
            customMealTime[0] = false;
            mealMinutes[0] = minuteOfDay(System.currentTimeMillis());
            timeButton.setText("Time: now");
        });
        timeActions.addView(nowButton, weightedActionParams());

        form.addView(ui.fieldLabel("Meal log"));
        form.addView(mealNameField);
        form.addView(ui.fieldLabel("Time eaten"));
        form.addView(timeActions);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Log saved meal")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                String mealName = mealNameField.getText().toString().trim();
                if (mealName.isEmpty()) {
                    mealNameField.setError("Required");
                    return;
                }

                long eatenAt = customMealTime[0]
                        ? millisForMealTime(baseTime, mealMinutes[0])
                        : System.currentTimeMillis();
                for (SavedMealItem item : items) {
                    if (item.food == null) {
                        continue;
                    }
                    store.saveMealFoodLog(new MealFoodLog(
                            0,
                            callbacks.currentProfileId(),
                            item.food.id,
                            mealName,
                            item.servings,
                            eatenAt,
                            item.food
                    ));
                }

                Toast.makeText(activity, "Logged " + savedMeal.name, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                callbacks.onSavedMealLogged();
            });
        });

        dialog.show();
    }

    private void renderSavedMealItemRows(
            LinearLayout container,
            List<NutritionFood> foods,
            ArrayList<SavedMealItemDraft> drafts
    ) {
        container.removeAllViews();
        if (drafts.isEmpty()) {
            drafts.add(new SavedMealItemDraft(foods.get(0).id, 0.0f));
        }

        ArrayList<String> foodNames = new ArrayList<>();
        for (NutritionFood food : foods) {
            foodNames.add(food.displayName());
        }

        for (int i = 0; i < drafts.size(); i++) {
            int index = i;
            SavedMealItemDraft draft = drafts.get(i);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(12));
            row.setBackground(ui.rounded(NourishColors.CARD, NourishColors.BORDER, ui.dp(18)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.topMargin = ui.dp(8);
            row.setLayoutParams(rowParams);

            Spinner foodSpinner = new Spinner(activity);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    activity,
                    android.R.layout.simple_spinner_item,
                    foodNames
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            foodSpinner.setAdapter(adapter);
            foodSpinner.setSelection(savedMealFoodIndex(foods, draft.foodId));
            foodSpinner.setPadding(ui.dp(10), 0, ui.dp(10), 0);
            foodSpinner.setBackground(ui.rounded(NourishColors.CARD, NourishColors.BORDER, ui.dp(18)));
            row.addView(foodSpinner, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(48)
            ));

            EditText servingsField = ui.field(
                    "Servings used",
                    draft.servings > 0.0f ? formatValue(draft.servings) : "",
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
            );
            row.addView(servingsField);

            Button remove = ui.button("Remove", NourishColors.CORAL, NourishColors.CORAL_SOFT);
            remove.setEnabled(drafts.size() > 1);
            remove.setAlpha(drafts.size() > 1 ? 1.0f : 0.45f);
            remove.setOnClickListener(view -> {
                ArrayList<SavedMealItemDraft> updated = savedMealDraftsFromRows(container, foods);
                if (updated.size() <= 1) {
                    Toast.makeText(activity, "Keep at least one food.", Toast.LENGTH_SHORT).show();
                    return;
                }
                updated.remove(index);
                renderSavedMealItemRows(container, foods, updated);
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(42)
            );
            removeParams.topMargin = ui.dp(8);
            row.addView(remove, removeParams);

            row.setTag(new SavedMealItemRowControls(foodSpinner, servingsField));
            container.addView(row);
        }
    }

    private ArrayList<SavedMealItemDraft> savedMealDraftsFromItems(List<SavedMealItem> items) {
        ArrayList<SavedMealItemDraft> drafts = new ArrayList<>();
        for (SavedMealItem item : items) {
            long foodId = item.foodId > 0 ? item.foodId : item.food == null ? 0 : item.food.id;
            if (foodId > 0) {
                drafts.add(new SavedMealItemDraft(foodId, item.servings));
            }
        }
        return drafts;
    }

    private ArrayList<SavedMealItemDraft> savedMealDraftsFromRows(
            LinearLayout container,
            List<NutritionFood> foods
    ) {
        ArrayList<SavedMealItemDraft> drafts = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (!(child.getTag() instanceof SavedMealItemRowControls)) {
                continue;
            }
            SavedMealItemRowControls controls = (SavedMealItemRowControls) child.getTag();
            int selectedIndex = controls.foodSpinner.getSelectedItemPosition();
            if (selectedIndex < 0 || selectedIndex >= foods.size()) {
                continue;
            }
            drafts.add(new SavedMealItemDraft(
                    foods.get(selectedIndex).id,
                    parseFloat(controls.servingsField, 0.0f)
            ));
        }
        return drafts;
    }

    private ArrayList<SavedMealItem> savedMealItemsFromRows(
            LinearLayout container,
            List<NutritionFood> foods
    ) {
        ArrayList<SavedMealItem> items = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (!(child.getTag() instanceof SavedMealItemRowControls)) {
                continue;
            }
            SavedMealItemRowControls controls = (SavedMealItemRowControls) child.getTag();
            int selectedIndex = controls.foodSpinner.getSelectedItemPosition();
            if (selectedIndex < 0 || selectedIndex >= foods.size()) {
                continue;
            }
            float servings = parseFloat(controls.servingsField, 0.0f);
            if (servings <= 0.0f) {
                controls.servingsField.setError("Enter servings");
                return null;
            }

            NutritionFood food = foods.get(selectedIndex);
            items.add(new SavedMealItem(0, 0, food.id, servings, items.size(), food));
        }
        return items;
    }

    private int savedMealFoodIndex(List<NutritionFood> foods, long foodId) {
        for (int i = 0; i < foods.size(); i++) {
            if (foods.get(i).id == foodId) {
                return i;
            }
        }
        return 0;
    }

    public void showLogFoodDialog(MealFoodLog existing) {
        showLogFoodDialog(
                existing,
                existing == null ? "" : existing.mealName,
                existing == null ? 0 : existing.foodId
        );
    }

    private void showLogFoodDialog(MealFoodLog existing, String presetName, long selectedFoodId) {
        List<NutritionFood> foods = store.getNutritionFoods(callbacks.currentProfileId());
        if (foods.isEmpty()) {
            Toast.makeText(activity, "Create a food before logging it.", Toast.LENGTH_SHORT).show();
            callbacks.showFoodEditor();
            return;
        }

        long targetFoodId = existing == null ? selectedFoodId : existing.foodId;
        int selectedIndex = 0;
        ArrayList<String> foodNames = new ArrayList<>();
        for (int i = 0; i < foods.size(); i++) {
            NutritionFood food = foods.get(i);
            foodNames.add(food.displayName());
            if (food.id == targetFoodId) {
                selectedIndex = i;
            }
        }

        long baseTime = existing == null ? System.currentTimeMillis() : existing.eatenAt;
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        form.addView(ui.fieldLabel("Food"));
        Spinner foodSpinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                foodNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        foodSpinner.setAdapter(adapter);
        foodSpinner.setSelection(selectedIndex);
        foodSpinner.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        foodSpinner.setBackground(ui.rounded(NourishColors.CARD, NourishColors.BORDER, ui.dp(18)));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(48)
        );
        spinnerParams.topMargin = ui.dp(8);
        form.addView(foodSpinner, spinnerParams);

        EditText mealNameField = ui.field(
                "Meal name",
                presetName,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        EditText servingsField = ui.field(
                "Servings eaten",
                existing == null ? "" : formatValue(existing.servings),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        final boolean[] customMealTime = {existing != null};
        final int[] mealMinutes = {minuteOfDay(baseTime)};
        LinearLayout timeActions = actionRow();
        Button timeButton = ui.button(
                customMealTime[0] ? "Ate at " + Medication.formatMinutes(mealMinutes[0]) : "Time: now",
                NourishColors.BLUE,
                NourishColors.BLUE_SOFT
        );
        timeButton.setOnClickListener(view -> showMealTimePicker(timeButton, mealMinutes, customMealTime));
        timeActions.addView(timeButton, weightedActionParams());

        Button nowButton = ui.button("Use now", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        nowButton.setOnClickListener(view -> {
            customMealTime[0] = false;
            mealMinutes[0] = minuteOfDay(System.currentTimeMillis());
            timeButton.setText("Time: now");
        });
        timeActions.addView(nowButton, weightedActionParams());

        form.addView(mealNameField);
        form.addView(servingsField);
        form.addView(ui.fieldLabel("Time eaten"));
        form.addView(timeActions);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? "Log food" : "Edit food log")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                float servings = parseFloat(servingsField, 0.0f);
                if (servings <= 0.0f) {
                    servingsField.setError("Enter servings");
                    return;
                }

                NutritionFood food = foods.get(foodSpinner.getSelectedItemPosition());
                String mealName = mealNameField.getText().toString().trim();
                if (mealName.isEmpty()) {
                    mealName = "Meal";
                }

                store.saveMealFoodLog(new MealFoodLog(
                        existing == null ? 0 : existing.id,
                        callbacks.currentProfileId(),
                        food.id,
                        mealName,
                        servings,
                        customMealTime[0]
                                ? millisForMealTime(baseTime, mealMinutes[0])
                                : System.currentTimeMillis(),
                        food
                ));
                dialog.dismiss();
                callbacks.onNutritionChanged();
            });
        });

        dialog.show();
    }

    public void confirmDeleteMealLog(MealFoodLog log) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete this food log?")
                .setMessage("This removes the entry from the meal log.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deleteMealFoodLog(log.id);
                    callbacks.onNutritionChanged();
                })
                .show();
    }

    public void confirmDeleteSavedMeal(SavedMeal savedMeal) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete " + savedMeal.name + "?")
                .setMessage("This removes the saved meal combination.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deleteSavedMeal(savedMeal.id);
                    callbacks.onNutritionChanged();
                })
                .show();
    }

    private void showMealTimePicker(Button timeButton, int[] mealMinutes, boolean[] customMealTime) {
        int hour = mealMinutes[0] / 60;
        int minute = mealMinutes[0] % 60;
        TimePickerDialog dialog = new TimePickerDialog(
                activity,
                (view, selectedHour, selectedMinute) -> {
                    mealMinutes[0] = Medication.normalizeMinutes((selectedHour * 60) + selectedMinute);
                    customMealTime[0] = true;
                    timeButton.setText("Ate at " + Medication.formatMinutes(mealMinutes[0]));
                },
                hour,
                minute,
                false
        );
        dialog.show();
    }

    private int minuteOfDay(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).getHour() * 60 +
                Instant.ofEpochMilli(epochMillis).atZone(zoneId).getMinute();
    }

    private long millisForMealTime(long baseMillis, int minutes) {
        LocalDate date = Instant.ofEpochMilli(baseMillis).atZone(zoneId).toLocalDate();
        return date.atStartOfDay(zoneId)
                .plusMinutes(Medication.normalizeMinutes(minutes))
                .toInstant()
                .toEpochMilli();
    }

    private String defaultMealName() {
        for (String name : store.getMealDefaults(callbacks.currentProfileId())) {
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        return "Meal";
    }

    private NutritionTotals totalsFor(List<SavedMealItem> items) {
        NutritionTotals totals = new NutritionTotals();
        for (SavedMealItem item : items) {
            totals.addFood(item.food, item.servings);
        }
        return totals;
    }

    private String nutritionTotalsLine(NutritionTotals totals) {
        return totals.calories + " cal - " +
                formatGrams(totals.proteinGrams) + " protein - " +
                formatGrams(totals.totalCarbsGrams) + " carbs - " +
                formatGrams(totals.totalFatGrams) + " fat";
    }

    private String savedMealItemsSummary(List<SavedMealItem> items) {
        if (items.isEmpty()) {
            return "No foods added";
        }

        StringBuilder summary = new StringBuilder();
        int visibleCount = Math.min(2, items.size());
        for (int i = 0; i < visibleCount; i++) {
            SavedMealItem item = items.get(i);
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(item.food == null ? "Food" : item.food.displayName());
            summary.append(" x");
            summary.append(formatValue(item.servings));
        }
        if (items.size() > visibleCount) {
            summary.append(" + ");
            summary.append(items.size() - visibleCount);
            summary.append(" more");
        }
        return summary.toString();
    }

    private String formatGrams(float value) {
        return formatValue(value) + "g";
    }

    private String formatValue(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private float parseFloat(EditText field, float fallback) {
        try {
            return Math.max(0.0f, Float.parseFloat(field.getText().toString().trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
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

    private static final class SavedMealItemDraft {
        final long foodId;
        final float servings;

        SavedMealItemDraft(long foodId, float servings) {
            this.foodId = foodId;
            this.servings = servings;
        }
    }

    private static final class SavedMealItemRowControls {
        final Spinner foodSpinner;
        final EditText servingsField;

        SavedMealItemRowControls(Spinner foodSpinner, EditText servingsField) {
            this.foodSpinner = foodSpinner;
            this.servingsField = servingsField;
        }
    }
}
