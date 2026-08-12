package com.jojokorok.nourishrx.nutrition;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.NutritionFood;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.util.Locale;

public class NutritionFoodEditorFlow {
    public interface Callbacks {
        long currentProfileId();

        void onFoodChanged();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final Callbacks callbacks;

    public NutritionFoodEditorFlow(
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

    public void show(NutritionFood existing) {
        NutritionFood food = existing == null ? emptyFood() : existing;

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        int decimalInput = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText brandField = ui.field(
                "Brand",
                food.brand,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        EditText nameField = ui.field(
                "Food name",
                food.name,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        EditText servingSizeField = ui.field("Serving size", food.servingSize, InputType.TYPE_CLASS_TEXT);
        boolean prefillNutrition = existing != null;
        EditText servingsPerContainerField = ui.field(
                "Servings per container",
                prefillNutrition ? formatFloatInput(food.servingsPerContainer) : "",
                decimalInput
        );
        EditText caloriesField = ui.field(
                "Calories",
                prefillNutrition ? String.valueOf(food.calories) : "",
                InputType.TYPE_CLASS_NUMBER
        );
        EditText totalFatField = nutritionField("Total fat (g)", food.totalFatGrams, prefillNutrition, decimalInput);
        EditText saturatedFatField = nutritionField("Saturated fat (g)", food.saturatedFatGrams, prefillNutrition, decimalInput);
        EditText transFatField = nutritionField("Trans fat (g)", food.transFatGrams, prefillNutrition, decimalInput);
        EditText cholesterolField = nutritionField("Cholesterol (mg)", food.cholesterolMg, prefillNutrition, decimalInput);
        EditText sodiumField = nutritionField("Sodium (mg)", food.sodiumMg, prefillNutrition, decimalInput);
        EditText carbsField = nutritionField("Total carbs (g)", food.totalCarbsGrams, prefillNutrition, decimalInput);
        EditText fiberField = nutritionField("Fiber (g)", food.fiberGrams, prefillNutrition, decimalInput);
        EditText totalSugarsField = nutritionField("Total sugars (g)", food.totalSugarsGrams, prefillNutrition, decimalInput);
        EditText addedSugarsField = nutritionField("Added sugars (g)", food.addedSugarsGrams, prefillNutrition, decimalInput);
        EditText proteinField = nutritionField("Protein (g)", food.proteinGrams, prefillNutrition, decimalInput);
        EditText vitaminDField = nutritionField("Vitamin D (mcg)", food.vitaminDMcg, prefillNutrition, decimalInput);
        EditText calciumField = nutritionField("Calcium (mg)", food.calciumMg, prefillNutrition, decimalInput);
        EditText ironField = nutritionField("Iron (mg)", food.ironMg, prefillNutrition, decimalInput);
        EditText potassiumField = nutritionField("Potassium (mg)", food.potassiumMg, prefillNutrition, decimalInput);

        form.addView(ui.fieldLabel("Food"));
        form.addView(brandField);
        form.addView(nameField);
        form.addView(ui.fieldLabel("Serving"));
        form.addView(servingSizeField);
        form.addView(servingsPerContainerField);
        form.addView(ui.fieldLabel("Nutrition facts per serving"));
        form.addView(caloriesField);
        form.addView(totalFatField);
        form.addView(saturatedFatField);
        form.addView(transFatField);
        form.addView(cholesterolField);
        form.addView(sodiumField);
        form.addView(carbsField);
        form.addView(fiberField);
        form.addView(totalSugarsField);
        form.addView(addedSugarsField);
        form.addView(proteinField);
        form.addView(ui.fieldLabel("Vitamins and minerals"));
        form.addView(vitaminDField);
        form.addView(calciumField);
        form.addView(ironField);
        form.addView(potassiumField);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? "Add food" : "Edit food")
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

                String servingSize = servingSizeField.getText().toString().trim();
                if (servingSize.isEmpty()) {
                    servingSizeField.setError("Required");
                    return;
                }

                store.saveNutritionFood(new NutritionFood(
                        food.id,
                        callbacks.currentProfileId(),
                        brandField.getText().toString(),
                        name,
                        servingSize,
                        parseFloat(servingsPerContainerField, 0.0f),
                        parseInt(caloriesField, 0),
                        parseFloat(totalFatField, 0.0f),
                        parseFloat(saturatedFatField, 0.0f),
                        parseFloat(transFatField, 0.0f),
                        parseFloat(cholesterolField, 0.0f),
                        parseFloat(sodiumField, 0.0f),
                        parseFloat(carbsField, 0.0f),
                        parseFloat(fiberField, 0.0f),
                        parseFloat(totalSugarsField, 0.0f),
                        parseFloat(addedSugarsField, 0.0f),
                        parseFloat(proteinField, 0.0f),
                        parseFloat(vitaminDField, 0.0f),
                        parseFloat(calciumField, 0.0f),
                        parseFloat(ironField, 0.0f),
                        parseFloat(potassiumField, 0.0f),
                        food.createdAt
                ));
                dialog.dismiss();
                callbacks.onFoodChanged();
            });
        });

        dialog.show();
    }

    public void confirmDelete(NutritionFood food) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete " + food.displayName() + "?")
                .setMessage("This removes the saved food and any meal logs that use it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deleteNutritionFood(food.id);
                    callbacks.onFoodChanged();
                })
                .show();
    }

    private NutritionFood emptyFood() {
        return new NutritionFood(
                0,
                callbacks.currentProfileId(),
                "",
                "",
                "",
                1.0f,
                0,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                System.currentTimeMillis()
        );
    }

    private EditText nutritionField(String hint, float value, boolean prefill, int inputType) {
        return ui.field(hint, prefill ? formatFloatInput(value) : "", inputType);
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

    private String formatFloatInput(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }
}
