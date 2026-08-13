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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.api.OpenFoodFactsClient;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.NutritionFood;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OpenFoodFactsFlow {
    public interface Callbacks {
        long currentProfileId();

        void showFoodEditor(NutritionFood food);

        void onFoodSaved();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final Callbacks callbacks;

    public OpenFoodFactsFlow(
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

    public void showSearchDialog() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        EditText queryField = ui.field(
                "Search food name",
                "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        form.addView(queryField);

        Button search = ui.button("Search OpenFoodFacts", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        searchParams.topMargin = ui.dp(10);
        form.addView(search, searchParams);

        TextView status = ui.text("Ready to search.", 13, NourishColors.MUTED, Typeface.BOLD);
        status.setPadding(0, ui.dp(12), 0, ui.dp(4));
        form.addView(status);

        LinearLayout results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        form.addView(results);

        Button loadMore = ui.button("Load more", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        loadMore.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadMoreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        loadMoreParams.topMargin = ui.dp(12);
        form.addView(loadMore, loadMoreParams);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Find food")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        final int[] nextPage = {1};
        final String[] activeQuery = {""};
        search.setOnClickListener(view -> startSearch(
                scrollView,
                queryField,
                search,
                loadMore,
                status,
                results,
                nextPage,
                activeQuery,
                true
        ));
        loadMore.setOnClickListener(view -> startSearch(
                scrollView,
                queryField,
                search,
                loadMore,
                status,
                results,
                nextPage,
                activeQuery,
                false
        ));

        dialog.setOnShowListener(dialogInterface -> queryField.requestFocus());
        dialog.show();
    }

    public void renderInspection(
            AlertDialog dialog,
            LinearLayout body,
            NutritionFood food,
            String sourceLine
    ) {
        body.removeAllViews();
        body.addView(ui.text(food.displayName(), 21, NourishColors.INK, Typeface.BOLD));
        body.addView(ui.text(sourceLine, 12, NourishColors.MUTED, Typeface.NORMAL));
        body.addView(ui.fieldLabel("Serving"));
        body.addView(nutritionFactRow(
                "Serving size",
                food.servingSize.isEmpty() ? "Not listed" : food.servingSize
        ));
        body.addView(nutritionFactRow(
                "Servings per container",
                food.servingsPerContainer > 0.0f
                        ? formatValue(food.servingsPerContainer)
                        : "Not listed"
        ));

        body.addView(ui.fieldLabel("Nutrition facts"));
        body.addView(nutritionFactRow("Calories", String.valueOf(food.calories)));
        body.addView(nutritionFactRow("Total fat", formatValue(food.totalFatGrams) + "g"));
        body.addView(nutritionFactRow("Saturated fat", formatValue(food.saturatedFatGrams) + "g"));
        body.addView(nutritionFactRow("Trans fat", formatValue(food.transFatGrams) + "g"));
        body.addView(nutritionFactRow("Cholesterol", formatValue(food.cholesterolMg) + "mg"));
        body.addView(nutritionFactRow("Sodium", formatValue(food.sodiumMg) + "mg"));
        body.addView(nutritionFactRow("Total carbs", formatValue(food.totalCarbsGrams) + "g"));
        body.addView(nutritionFactRow("Fiber", formatValue(food.fiberGrams) + "g"));
        body.addView(nutritionFactRow("Total sugars", formatValue(food.totalSugarsGrams) + "g"));
        body.addView(nutritionFactRow("Added sugars", formatValue(food.addedSugarsGrams) + "g"));
        body.addView(nutritionFactRow("Protein", formatValue(food.proteinGrams) + "g"));

        body.addView(ui.fieldLabel("Vitamins and minerals"));
        body.addView(nutritionFactRow("Vitamin D", formatValue(food.vitaminDMcg) + "mcg"));
        body.addView(nutritionFactRow("Calcium", formatValue(food.calciumMg) + "mg"));
        body.addView(nutritionFactRow("Iron", formatValue(food.ironMg) + "mg"));
        body.addView(nutritionFactRow("Potassium", formatValue(food.potassiumMg) + "mg"));

        LinearLayout actions = actionRow();
        Button save = ui.button("Save food", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        save.setOnClickListener(view -> {
            store.saveNutritionFood(food);
            dialog.dismiss();
            Toast.makeText(activity, "Saved " + food.displayName(), Toast.LENGTH_SHORT).show();
            callbacks.onFoodSaved();
        });
        actions.addView(save, weightedActionParams());

        Button edit = ui.button("Edit first", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        edit.setOnClickListener(view -> {
            dialog.dismiss();
            callbacks.showFoodEditor(food);
        });
        actions.addView(edit, weightedActionParams());
        body.addView(actions);
    }

    private void startSearch(
            ScrollView scrollView,
            EditText queryField,
            Button search,
            Button loadMore,
            TextView status,
            LinearLayout results,
            int[] nextPage,
            String[] activeQuery,
            boolean reset
    ) {
        String typedQuery = queryField.getText().toString().trim();
        if (reset) {
            activeQuery[0] = typedQuery;
        }
        String query = reset ? typedQuery : activeQuery[0];
        if (query.length() < 2) {
            queryField.setError("Enter a food name");
            return;
        }

        int page = reset ? 1 : Math.max(1, nextPage[0]);
        int previousScrollY = reset ? 0 : scrollView.getScrollY();
        search.setEnabled(false);
        loadMore.setEnabled(false);
        status.setText(reset ? "Searching OpenFoodFacts..." : "Loading more results...");
        if (reset) {
            nextPage[0] = 1;
            results.removeAllViews();
            loadMore.setVisibility(View.GONE);
        }

        new Thread(() -> {
            try {
                List<OpenFoodFactsClient.SearchResult> found = new OpenFoodFactsClient().searchFoods(query, page);
                activity.runOnUiThread(() -> {
                    search.setEnabled(true);
                    boolean hasMore = found.size() >= OpenFoodFactsClient.PAGE_SIZE;
                    if (found.isEmpty() && reset) {
                        status.setText("No matching foods found.");
                    } else if (found.isEmpty()) {
                        status.setText("No more results.");
                    } else {
                        status.setText(reset
                                ? plural(found.size(), "result", "results")
                                : "Added " + plural(found.size(), "more result", "more results"));
                        appendResults(results, found);
                        if (!reset) {
                            scrollView.post(() -> scrollView.scrollTo(0, previousScrollY));
                        }
                    }
                    nextPage[0] = page + 1;
                    loadMore.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                    loadMore.setEnabled(hasMore);
                });
            } catch (Exception exception) {
                activity.runOnUiThread(() -> {
                    search.setEnabled(true);
                    loadMore.setEnabled(true);
                    status.setText("Search failed. Check connection and try again.");
                    Toast.makeText(activity, exception.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void appendResults(
            LinearLayout container,
            List<OpenFoodFactsClient.SearchResult> results
    ) {
        for (OpenFoodFactsClient.SearchResult result : results) {
            container.addView(resultCard(result));
        }
    }

    private View resultCard(OpenFoodFactsClient.SearchResult result) {
        LinearLayout card = ui.card();
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.text(result.displayName(), 18, NourishColors.INK, Typeface.BOLD));
        details.addView(ui.text(resultSummary(result), 13, NourishColors.MUTED, Typeface.NORMAL));
        details.addView(ui.text("Barcode " + result.code, 12, NourishColors.MUTED, Typeface.NORMAL));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(ui.statusBadge(
                result.nutritionGrade.isEmpty()
                        ? "OFF"
                        : result.nutritionGrade.toUpperCase(Locale.US)
        ));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button inspect = ui.button("Inspect", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        inspect.setOnClickListener(view -> showInspectDialog(result));
        actions.addView(inspect, weightedActionParams());

        Button add = ui.button("Add", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        add.setOnClickListener(view -> importFood(result));
        actions.addView(add, weightedActionParams());
        card.addView(actions);
        return card;
    }

    private String resultSummary(OpenFoodFactsClient.SearchResult result) {
        ArrayList<String> details = new ArrayList<>();
        if (!result.brand.isEmpty()) {
            details.add(result.brand);
        }
        if (!result.quantity.isEmpty()) {
            details.add(result.quantity);
        }
        if (details.isEmpty()) {
            return "OpenFoodFacts product";
        }
        return String.join(" - ", details);
    }

    private void importFood(OpenFoodFactsClient.SearchResult result) {
        Toast.makeText(activity, "Loading food details...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NutritionFood food = new OpenFoodFactsClient().fetchNutritionFood(
                        result.code,
                        callbacks.currentProfileId()
                );
                activity.runOnUiThread(() -> {
                    store.saveNutritionFood(food);
                    Toast.makeText(activity, "Saved " + food.displayName(), Toast.LENGTH_SHORT).show();
                    callbacks.onFoodSaved();
                });
            } catch (Exception exception) {
                activity.runOnUiThread(() -> Toast.makeText(
                        activity,
                        exception.getMessage(),
                        Toast.LENGTH_SHORT
                ).show());
            }
        }).start();
    }

    private void showInspectDialog(OpenFoodFactsClient.SearchResult result) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        body.addView(ui.text("Loading nutrition facts...", 15, NourishColors.MUTED, Typeface.BOLD));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Inspect food")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        dialog.show();

        new Thread(() -> {
            try {
                NutritionFood food = new OpenFoodFactsClient().fetchNutritionFood(
                        result.code,
                        callbacks.currentProfileId()
                );
                activity.runOnUiThread(() -> renderInspection(
                        dialog,
                        body,
                        food,
                        "OpenFoodFacts barcode " + result.code
                ));
            } catch (Exception exception) {
                activity.runOnUiThread(() -> {
                    body.removeAllViews();
                    body.addView(ui.text("Could not load this food.", 16, NourishColors.CORAL, Typeface.BOLD));
                    body.addView(ui.text(exception.getMessage(), 13, NourishColors.MUTED, Typeface.NORMAL));
                });
            }
        }).start();
    }

    private View nutritionFactRow(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(6), 0, ui.dp(6));

        TextView labelView = ui.text(label, 14, NourishColors.INK, Typeface.BOLD);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView valueView = ui.text(value, 14, NourishColors.MUTED, Typeface.BOLD);
        valueView.setGravity(Gravity.RIGHT);
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
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

    private String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private String formatValue(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }
}
