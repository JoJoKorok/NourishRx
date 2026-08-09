package com.jojokorok.nourishrx.nutrition;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.jojokorok.nourishrx.data.MealFoodLog;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.NutritionFood;
import com.jojokorok.nourishrx.data.NutritionTotals;
import com.jojokorok.nourishrx.data.SavedMeal;
import com.jojokorok.nourishrx.data.WeightEntry;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public class NutritionScreens {
    public interface Callbacks {
        long currentProfileId();

        String selectedProfileName();

        String plural(long count, String singular, String plural);

        int distinctMealCount(List<MealFoodLog> logs);

        List<String> mealNamesForLogs(List<MealFoodLog> logs);

        List<MealFoodLog> logsForMeal(List<MealFoodLog> logs, String mealName);

        NutritionTotals totalsFromMealLogs(List<MealFoodLog> logs);

        View sectionTitle(String title, String subtitle);

        void emptyState(String message, String action, View.OnClickListener listener);

        View nutritionSummaryCard(int calories, float protein, float carbs, float fat);

        View dailyNutritionFactsCard(NutritionTotals totals);

        View mealTotalsCard(String mealName, List<MealFoodLog> logs);

        View defaultMealsCard(List<String> mealDefaults);

        View waterCard(int waterOunces, long startMillis, long endMillis);

        View weightCard(List<WeightEntry> weights);

        View mealLogCard(MealFoodLog log);

        View savedMealCard(SavedMeal savedMeal);

        View foodCard(NutritionFood food);

        void showLogFoodDialog(String mealName);

        void showSavedMealDialog();

        void showFoodDialog();

        void showOpenFoodFactsSearchDialog();

        void showBarcodeEntryPoint();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final ZoneId zoneId;
    private final Callbacks callbacks;

    public NutritionScreens(
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
        LocalDate today = LocalDate.now(zoneId);
        long start = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        List<MealFoodLog> logs = store.getMealFoodLogs(callbacks.currentProfileId(), start, end);
        List<WeightEntry> weights = store.getWeightEntries(callbacks.currentProfileId(), 5);
        int waterOunces = store.getWaterOunces(callbacks.currentProfileId(), start, end);
        NutritionTotals totals = callbacks.totalsFromMealLogs(logs);

        content.addView(callbacks.sectionTitle("Nutrition", callbacks.selectedProfileName() + " has " + logs.size() + " foods logged today"));
        content.addView(callbacks.nutritionSummaryCard(totals.calories, totals.proteinGrams, totals.totalCarbsGrams, totals.totalFatGrams));
        content.addView(callbacks.dailyNutritionFactsCard(totals));
        content.addView(callbacks.defaultMealsCard(store.getMealDefaults(callbacks.currentProfileId())));
        content.addView(callbacks.waterCard(waterOunces, start, end));
        content.addView(callbacks.weightCard(weights));

        content.addView(callbacks.sectionTitle("Meal log", logs.isEmpty() ? "No foods logged yet" : callbacks.plural(callbacks.distinctMealCount(logs), "meal", "meals") + " today"));
        if (logs.isEmpty()) {
            callbacks.emptyState("Create foods once, then log them into any meal.", "Log food", view -> callbacks.showLogFoodDialog(""));
            return;
        }

        for (MealFoodLog log : logs) {
            content.addView(callbacks.mealLogCard(log));
        }
    }

    public void renderMeals(LinearLayout content) {
        LocalDate today = LocalDate.now(zoneId);
        long start = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        List<MealFoodLog> logs = store.getMealFoodLogs(callbacks.currentProfileId(), start, end);

        content.addView(callbacks.sectionTitle("Meals", logs.isEmpty() ? "No foods logged today" : callbacks.plural(logs.size(), "food entry", "food entries") + " today"));
        content.addView(callbacks.defaultMealsCard(store.getMealDefaults(callbacks.currentProfileId())));

        Button addMeal = ui.button("+ Log food", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        addMeal.setOnClickListener(view -> callbacks.showLogFoodDialog(""));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        addParams.topMargin = ui.dp(4);
        content.addView(addMeal, addParams);

        if (logs.isEmpty()) {
            callbacks.emptyState("Pick a saved food and add it into breakfast, lunch, dinner, or any meal you name.", "Log food", view -> callbacks.showLogFoodDialog(""));
            return;
        }

        for (String mealName : callbacks.mealNamesForLogs(logs)) {
            List<MealFoodLog> mealLogs = callbacks.logsForMeal(logs, mealName);
            content.addView(callbacks.mealTotalsCard(mealName, mealLogs));
            for (MealFoodLog log : mealLogs) {
                content.addView(callbacks.mealLogCard(log));
            }
        }
    }

    public void renderSavedMeals(LinearLayout content) {
        List<SavedMeal> savedMeals = store.getSavedMeals(callbacks.currentProfileId());
        List<NutritionFood> foods = store.getNutritionFoods(callbacks.currentProfileId());

        content.addView(callbacks.sectionTitle("Saved", savedMeals.isEmpty() ? "No saved meal combinations yet" : callbacks.plural(savedMeals.size(), "saved meal", "saved meals")));

        Button create = ui.button("+ Saved meal", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        create.setOnClickListener(view -> callbacks.showSavedMealDialog());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        createParams.topMargin = ui.dp(4);
        content.addView(create, createParams);

        if (foods.isEmpty()) {
            callbacks.emptyState("Save food items first, then combine them into reusable meals.", "Add food", view -> callbacks.showFoodDialog());
            return;
        }

        if (savedMeals.isEmpty()) {
            callbacks.emptyState("Build a reusable meal from foods already saved in the app.", "Create saved meal", view -> callbacks.showSavedMealDialog());
            return;
        }

        for (SavedMeal savedMeal : savedMeals) {
            content.addView(callbacks.savedMealCard(savedMeal));
        }
    }

    public void renderFoods(LinearLayout content) {
        List<NutritionFood> foods = store.getNutritionFoods(callbacks.currentProfileId());

        content.addView(callbacks.sectionTitle("Foods", foods.isEmpty() ? "No saved foods yet" : callbacks.plural(foods.size(), "saved food", "saved foods")));
        LinearLayout actions = actionRow();
        actions.setPadding(0, ui.dp(4), 0, 0);

        Button addFood = ui.button("+ Manual", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        addFood.setOnClickListener(view -> callbacks.showFoodDialog());
        actions.addView(addFood, weightedActionParams());

        Button searchFood = ui.button("Find online", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        searchFood.setOnClickListener(view -> callbacks.showOpenFoodFactsSearchDialog());
        actions.addView(searchFood, weightedActionParams());

        Button scanBarcode = ui.button("Barcode", NourishColors.GOLD, NourishColors.GOLD_SOFT);
        scanBarcode.setOnClickListener(view -> callbacks.showBarcodeEntryPoint());
        actions.addView(scanBarcode, weightedActionParams());
        content.addView(actions);

        if (foods.isEmpty()) {
            callbacks.emptyState("Save food items manually or import inspectable options from OpenFoodFacts.", "Search foods", view -> callbacks.showOpenFoodFactsSearchDialog());
            return;
        }

        for (NutritionFood food : foods) {
            content.addView(callbacks.foodCard(food));
        }
    }

    public void renderBody(LinearLayout content) {
        LocalDate today = LocalDate.now(zoneId);
        long start = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        int waterOunces = store.getWaterOunces(callbacks.currentProfileId(), start, end);
        List<WeightEntry> weights = store.getWeightEntries(callbacks.currentProfileId(), 10);

        content.addView(callbacks.sectionTitle("Body", "Track water intake and weight"));
        content.addView(callbacks.waterCard(waterOunces, start, end));
        content.addView(callbacks.weightCard(weights));
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
}
