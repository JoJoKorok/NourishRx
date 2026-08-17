package com.jojokorok.nourishrx.nutrition;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
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
import com.jojokorok.nourishrx.ui.NourishShapes;
import com.jojokorok.nourishrx.ui.NourishSpacing;
import com.jojokorok.nourishrx.ui.NourishTypography;
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
        LinearLayout form = dialogBody();
        form.addView(dialogHeader(
                "Search foods",
                "Look up packaged foods by name, review the label, and save only what you need."
        ));

        form.addView(ui.fieldLabel("Food name"));
        EditText queryField = ui.field(
                "Example: Greek yogurt",
                "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        form.addView(queryField);

        Button search = ui.button("Search foods", NourishColors.ON_ACCENT, NourishColors.GREEN);
        search.setSingleLine(true);
        form.addView(search, matchParams(48, NourishSpacing.SM));

        TextView status = ui.text(
                "Enter at least two characters to search OpenFoodFacts.",
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.NORMAL
        );
        styleStatus(status);
        form.addView(status, wrapParams(NourishSpacing.MD));

        LinearLayout results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        form.addView(results);

        Button loadMore = ui.button("Load more results", NourishColors.BLUE, Color.TRANSPARENT);
        loadMore.setSingleLine(true);
        loadMore.setVisibility(View.GONE);
        form.addView(loadMore, matchParams(46, NourishSpacing.SM));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        final int[] nextPage = {1};
        final String[] activeQuery = {""};
        search.setOnClickListener(view -> startSearch(
                scrollView, queryField, search, loadMore, status, results,
                nextPage, activeQuery, true
        ));
        loadMore.setOnClickListener(view -> startSearch(
                scrollView, queryField, search, loadMore, status, results,
                nextPage, activeQuery, false
        ));

        dialog.setOnShowListener(dialogInterface -> {
            styleDialogActions(dialog);
            queryField.requestFocus();
        });
        dialog.show();
        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    public void renderInspection(
            AlertDialog dialog,
            LinearLayout body,
            NutritionFood food,
            String sourceLine
    ) {
        body.removeAllViews();
        body.setPadding(
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.SM)
        );
        body.addView(dialogHeader(food.displayName(), sourceLine));
        body.addView(nutritionSummary(food));

        addNutritionSection(body, "Serving", new String[][]{
                {"Serving size", food.servingSize.isEmpty() ? "Not listed" : food.servingSize},
                {"Servings per container", food.servingsPerContainer > 0.0f
                        ? formatValue(food.servingsPerContainer)
                        : "Not listed"}
        });
        addNutritionSection(body, "Nutrition facts", new String[][]{
                {"Calories", String.valueOf(food.calories)},
                {"Total fat", formatValue(food.totalFatGrams) + " g"},
                {"Saturated fat", formatValue(food.saturatedFatGrams) + " g"},
                {"Trans fat", formatValue(food.transFatGrams) + " g"},
                {"Cholesterol", formatValue(food.cholesterolMg) + " mg"},
                {"Sodium", formatValue(food.sodiumMg) + " mg"},
                {"Total carbs", formatValue(food.totalCarbsGrams) + " g"},
                {"Fiber", formatValue(food.fiberGrams) + " g"},
                {"Total sugars", formatValue(food.totalSugarsGrams) + " g"},
                {"Added sugars", formatValue(food.addedSugarsGrams) + " g"},
                {"Protein", formatValue(food.proteinGrams) + " g"}
        });
        addNutritionSection(body, "Vitamins and minerals", new String[][]{
                {"Vitamin D", formatValue(food.vitaminDMcg) + " mcg"},
                {"Calcium", formatValue(food.calciumMg) + " mg"},
                {"Iron", formatValue(food.ironMg) + " mg"},
                {"Potassium", formatValue(food.potassiumMg) + " mg"}
        });

        LinearLayout actions = actionRow();
        Button edit = ui.button("Edit before saving", NourishColors.BLUE, Color.TRANSPARENT);
        edit.setSingleLine(true);
        edit.setOnClickListener(view -> {
            dialog.dismiss();
            callbacks.showFoodEditor(food);
        });
        actions.addView(edit, weightedActionParams(false));

        Button save = ui.button("Save food", NourishColors.ON_ACCENT, NourishColors.GREEN);
        save.setSingleLine(true);
        save.setOnClickListener(view -> {
            store.saveNutritionFood(food);
            dialog.dismiss();
            Toast.makeText(activity, "Saved " + food.displayName(), Toast.LENGTH_SHORT).show();
            callbacks.onFoodSaved();
        });
        actions.addView(save, weightedActionParams(true));
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
        status.setTextColor(NourishColors.BLUE);
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
                        status.setText("No matching foods found. Try a broader name.");
                    } else if (found.isEmpty()) {
                        status.setText("You have reached the end of these results.");
                    } else {
                        status.setText(reset
                                ? plural(found.size(), "result", "results") + " found"
                                : plural(found.size(), "more result", "more results") + " added");
                        appendResults(results, found);
                        if (!reset) {
                            scrollView.post(() -> scrollView.scrollTo(0, previousScrollY));
                        }
                    }
                    status.setTextColor(found.isEmpty() ? NourishColors.MUTED : NourishColors.GREEN_DARK);
                    nextPage[0] = page + 1;
                    loadMore.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                    loadMore.setEnabled(hasMore);
                });
            } catch (Exception exception) {
                activity.runOnUiThread(() -> {
                    search.setEnabled(true);
                    loadMore.setEnabled(true);
                    status.setText("Search failed. Check your connection and try again.");
                    status.setTextColor(NourishColors.CORAL);
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
        card.setElevation(ui.dp(NourishShapes.ELEVATION_FLAT));
        card.setPadding(
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.SM)
        );

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.displayText(
                result.displayName(),
                NourishTypography.BODY_LARGE,
                NourishColors.INK
        ));
        details.addView(
                ui.text(
                        resultSummary(result),
                        NourishTypography.LABEL,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                ),
                wrapParams(NourishSpacing.XXS)
        );
        details.addView(
                ui.text(
                        "Barcode " + result.code,
                        NourishTypography.CAPTION,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                ),
                wrapParams(NourishSpacing.XXS)
        );
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(nutritionGradeBadge(result.nutritionGrade));
        card.addView(top);
        card.addView(divider(), matchParams(1, NourishSpacing.SM));

        LinearLayout actions = actionRow();
        actions.setPadding(0, ui.dp(NourishSpacing.XS), 0, 0);
        Button inspect = ui.button("View facts", NourishColors.BLUE, Color.TRANSPARENT);
        inspect.setSingleLine(true);
        inspect.setOnClickListener(view -> showInspectDialog(result));
        actions.addView(inspect, weightedActionParams(false));

        Button add = ui.button("Save", NourishColors.GREEN_DARK, NourishColors.GREEN_SOFT);
        add.setSingleLine(true);
        add.setOnClickListener(view -> importFood(result));
        actions.addView(add, weightedActionParams(true));
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
        LinearLayout body = dialogBody();
        body.addView(dialogHeader(result.displayName(), "Loading nutrition facts from OpenFoodFacts..."));

        TextView loading = ui.text(
                "Retrieving the product label",
                NourishTypography.LABEL,
                NourishColors.BLUE,
                Typeface.BOLD
        );
        styleStatus(loading);
        body.addView(loading, wrapParams(NourishSpacing.MD));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> styleDialogActions(dialog));
        dialog.show();
        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

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
                    body.addView(dialogHeader("Could not load this food", result.displayName()));
                    TextView error = ui.text(
                            exception.getMessage(),
                            NourishTypography.LABEL,
                            NourishColors.CORAL,
                            Typeface.NORMAL
                    );
                    styleStatus(error);
                    body.addView(error, wrapParams(NourishSpacing.MD));
                });
            }
        }).start();
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

    private View nutritionSummary(NutritionFood food) {
        LinearLayout summary = new LinearLayout(activity);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setPadding(
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM)
        );
        summary.setBackground(ui.rounded(
                NourishColors.SURFACE_SUBTLE,
                NourishColors.BORDER,
                ui.dp(NourishShapes.RADIUS_CONTROL)
        ));
        summary.addView(summaryMetric(String.valueOf(food.calories), "kcal"), metricParams(false));
        summary.addView(summaryMetric(formatValue(food.proteinGrams), "protein"), metricParams(false));
        summary.addView(summaryMetric(formatValue(food.totalCarbsGrams), "carbs"), metricParams(false));
        summary.addView(summaryMetric(formatValue(food.totalFatGrams), "fat"), metricParams(true));
        summary.setLayoutParams(wrapParams(NourishSpacing.MD));
        return summary;
    }

    private View summaryMetric(String value, String label) {
        LinearLayout metric = new LinearLayout(activity);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.addView(ui.displayText(value, NourishTypography.BODY_LARGE, NourishColors.INK));
        TextView labelView = ui.text(
                label,
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.NORMAL
        );
        labelView.setGravity(Gravity.CENTER);
        metric.addView(labelView);
        return metric;
    }

    private LinearLayout.LayoutParams metricParams(boolean last) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        if (!last) {
            params.rightMargin = ui.dp(NourishSpacing.XS);
        }
        return params;
    }

    private void addNutritionSection(LinearLayout body, String title, String[][] rows) {
        body.addView(sectionHeader(title));
        for (int index = 0; index < rows.length; index++) {
            body.addView(nutritionFactRow(rows[index][0], rows[index][1]));
            if (index < rows.length - 1) {
                body.addView(divider(), matchParams(1, 0));
            }
        }
    }

    private View sectionHeader(String title) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, ui.dp(NourishSpacing.LG), 0, ui.dp(NourishSpacing.XS));
        section.addView(divider(), matchParams(1, 0));
        section.addView(
                ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK),
                wrapParams(NourishSpacing.MD)
        );
        return section;
    }

    private View nutritionFactRow(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(NourishSpacing.XS), 0, ui.dp(NourishSpacing.XS));

        TextView labelView = ui.text(
                label,
                NourishTypography.LABEL,
                NourishColors.INK_SECONDARY,
                Typeface.NORMAL
        );
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView valueView = ui.text(
                value,
                NourishTypography.LABEL,
                NourishColors.INK,
                Typeface.BOLD
        );
        valueView.setGravity(Gravity.RIGHT);
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private TextView nutritionGradeBadge(String grade) {
        String normalized = grade == null ? "" : grade.trim().toUpperCase(Locale.US);
        int textColor;
        int backgroundColor;
        if (normalized.equals("A") || normalized.equals("B")) {
            textColor = NourishColors.GREEN_DARK;
            backgroundColor = NourishColors.GREEN_SOFT;
        } else if (normalized.equals("C")) {
            textColor = NourishColors.GOLD;
            backgroundColor = NourishColors.GOLD_SOFT;
        } else if (normalized.equals("D") || normalized.equals("E")) {
            textColor = NourishColors.CORAL;
            backgroundColor = NourishColors.CORAL_SOFT;
        } else {
            textColor = NourishColors.MUTED;
            backgroundColor = NourishColors.SURFACE_SUBTLE;
        }

        TextView badge = ui.text(
                normalized.isEmpty() ? "No grade" : "Grade " + normalized,
                NourishTypography.CAPTION,
                textColor,
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
                backgroundColor,
                Color.TRANSPARENT,
                ui.dp(NourishShapes.RADIUS_CONTROL)
        ));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = ui.dp(NourishSpacing.SM);
        badge.setLayoutParams(params);
        return badge;
    }

    private View divider() {
        View divider = new View(activity);
        divider.setBackgroundColor(NourishColors.DIVIDER);
        return divider;
    }

    private void styleStatus(TextView status) {
        status.setPadding(
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.XS)
        );
        status.setBackground(ui.rounded(
                NourishColors.CARD_SUBTLE,
                NourishColors.BORDER,
                ui.dp(NourishShapes.RADIUS_CONTROL)
        ));
    }

    private void styleDialogActions(AlertDialog dialog) {
        Button close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        close.setTextColor(NourishColors.INK_SECONDARY);
        close.setTypeface(Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL));
    }

    private LinearLayout actionRow() {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, ui.dp(NourishSpacing.LG), 0, 0);
        return actions;
    }

    private LinearLayout.LayoutParams weightedActionParams(boolean last) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(44), 1);
        if (!last) {
            params.rightMargin = ui.dp(NourishSpacing.XS);
        }
        return params;
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
