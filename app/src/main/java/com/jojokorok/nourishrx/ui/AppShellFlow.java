package com.jojokorok.nourishrx.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.Profile;
import com.jojokorok.nourishrx.data.WeightEntry;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class AppShellFlow {
    public interface Callbacks {
        long currentProfileId();

        String currentMode();

        String currentTab();

        Profile selectedProfile();

        View profileAvatar(Profile profile, int sizeDp, int fallbackColor, int textSp);

        int avatarWidthDp(Profile profile, int heightDp);

        int todayDoseCount();

        String alertsLabel();

        int alertColor();

        void showQuickAdd(boolean nutritionMode);

        void showProfiles();

        void selectAbout();

        void selectMode(String mode);

        void selectTab(String tab);

        void handleAlertsTap();
    }

    private static final String MODE_MEDICATION = "medication";
    private static final String MODE_NUTRITION = "nutrition";
    private static final String TAB_ABOUT = "about";

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final ZoneId zoneId;
    private final Callbacks callbacks;
    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault());

    public AppShellFlow(
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

    public LinearLayout render() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NourishColors.SURFACE);
        root.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), 0);

        root.addView(headerPanel());
        root.addView(tabRow());

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, ui.dp(14), 0, ui.dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        activity.setContentView(root);
        return content;
    }

    private View headerPanel() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(14));
        panel.setBackground(ui.roundedGradient(
                new int[]{
                        Color.rgb(222, 244, 231),
                        Color.rgb(255, 237, 215),
                        Color.rgb(236, 242, 255)
                },
                ui.dp(26)
        ));
        panel.setElevation(ui.dp(2));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Profile profile = callbacks.selectedProfile();
        View mark = callbacks.profileAvatar(profile, 54, NourishColors.GREEN, 18);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
                ui.dp(callbacks.avatarWidthDp(profile, 54)),
                ui.dp(54)
        );
        markParams.rightMargin = ui.dp(12);
        top.addView(mark, markParams);

        LinearLayout titleGroup = new LinearLayout(activity);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        TextView headline = ui.displayText(profile.name, 28, NourishColors.INK);
        headline.setSingleLine(true);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        titleGroup.addView(headline);
        titleGroup.addView(ui.text(
                "Today - " + LocalDate.now().format(dateFormatter),
                13,
                NourishColors.MUTED,
                Typeface.BOLD
        ));
        top.addView(titleGroup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        boolean nutritionMode = MODE_NUTRITION.equals(callbacks.currentMode());
        Button add = ui.button(nutritionMode ? "+ Log" : "+ Med", Color.WHITE, NourishColors.GREEN);
        add.setOnClickListener(view -> callbacks.showQuickAdd(nutritionMode));
        top.addView(add, compactButtonParams());
        panel.addView(top);

        LinearLayout utilityActions = new LinearLayout(activity);
        utilityActions.setOrientation(LinearLayout.HORIZONTAL);

        Button profileButton = ui.button("Manage profiles", NourishColors.BLUE, Color.WHITE);
        profileButton.setOnClickListener(view -> callbacks.showProfiles());
        utilityActions.addView(profileButton, weightedActionParams());

        boolean showingAbout = TAB_ABOUT.equals(callbacks.currentTab());
        Button aboutButton = ui.button(
                "About",
                showingAbout ? Color.WHITE : NourishColors.BLUE,
                showingAbout ? NourishColors.BLUE : Color.WHITE
        );
        aboutButton.setOnClickListener(view -> callbacks.selectAbout());
        utilityActions.addView(aboutButton, new LinearLayout.LayoutParams(0, ui.dp(44), 1));

        LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(44)
        );
        utilityParams.topMargin = ui.dp(12);
        panel.addView(utilityActions, utilityParams);

        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        modeParams.topMargin = ui.dp(10);
        panel.addView(modeSwitchRow(), modeParams);

        panel.addView(headerStats(nutritionMode));

        Button alerts = ui.button(callbacks.alertsLabel(), callbacks.alertColor(), Color.WHITE);
        alerts.setOnClickListener(view -> callbacks.handleAlertsTap());
        LinearLayout.LayoutParams alertParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(44)
        );
        alertParams.topMargin = ui.dp(12);
        panel.addView(alerts, alertParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = ui.dp(12);
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout headerStats(boolean nutritionMode) {
        LinearLayout stats = new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, ui.dp(14), 0, 0);
        if (nutritionMode) {
            LocalDate today = LocalDate.now(zoneId);
            long start = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
            long end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
            int foodLogCount = store.getMealFoodLogs(callbacks.currentProfileId(), start, end).size();
            int waterOunces = store.getWaterOunces(callbacks.currentProfileId(), start, end);
            List<WeightEntry> weights = store.getWeightEntries(callbacks.currentProfileId(), 1);
            stats.addView(ui.summaryPill(
                    plural(foodLogCount, "food log", "food logs"),
                    NourishColors.GREEN,
                    NourishColors.GREEN_SOFT
            ));
            stats.addView(ui.summaryPill(
                    waterOunces + " oz water",
                    NourishColors.BLUE,
                    NourishColors.BLUE_SOFT
            ));
            stats.addView(ui.summaryPill(
                    weights.isEmpty() ? "no weight" : formatValue(weights.get(0).pounds) + " lb",
                    NourishColors.GOLD,
                    NourishColors.GOLD_SOFT
            ));
        } else {
            List<Medication> medications = store.getAllMedications(callbacks.currentProfileId());
            long lowCount = medications.stream().filter(Medication::isLowStock).count();
            stats.addView(ui.summaryPill(
                    plural(callbacks.todayDoseCount(), "dose", "doses"),
                    NourishColors.GREEN,
                    NourishColors.GREEN_SOFT
            ));
            stats.addView(ui.summaryPill(
                    plural(medications.size(), "med", "meds"),
                    NourishColors.BLUE,
                    NourishColors.BLUE_SOFT
            ));
            stats.addView(ui.summaryPill(
                    plural(lowCount, "refill", "refills"),
                    NourishColors.GOLD,
                    NourishColors.GOLD_SOFT
            ));
        }
        return stats;
    }

    private LinearLayout modeSwitchRow() {
        LinearLayout modes = new LinearLayout(activity);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        modes.setBackground(ui.rounded(NourishColors.CARD, NourishColors.BORDER, ui.dp(20)));
        modes.addView(modeButton("Medication", MODE_MEDICATION));
        modes.addView(modeButton("Nutrition", MODE_NUTRITION));
        return modes;
    }

    private Button modeButton(String label, String mode) {
        boolean selected = callbacks.currentMode().equals(mode);
        Button button = ui.button(
                label,
                selected ? Color.WHITE : NourishColors.MUTED,
                selected ? NourishColors.BLUE : Color.TRANSPARENT
        );
        button.setOnClickListener(view -> callbacks.selectMode(mode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(38), 1);
        params.leftMargin = ui.dp(2);
        params.rightMargin = ui.dp(2);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout tabRow() {
        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        tabs.setBackground(ui.rounded(NourishColors.TAB_TRACK, Color.TRANSPARENT, ui.dp(20)));
        if (MODE_NUTRITION.equals(callbacks.currentMode())) {
            tabs.addView(tabButton("Today", "nutrition_today"));
            tabs.addView(tabButton("Meals", "nutrition_meals"));
            tabs.addView(tabButton("Saved", "nutrition_saved"));
            tabs.addView(tabButton("Foods", "nutrition_foods"));
            tabs.addView(tabButton("Body", "nutrition_body"));
        } else {
            tabs.addView(tabButton("Today", "today"));
            tabs.addView(tabButton("Meds", "meds"));
            tabs.addView(tabButton("Stock", "stock"));
        }
        return tabs;
    }

    private Button tabButton(String label, String tab) {
        boolean selected = callbacks.currentTab().equals(tab);
        Button button = ui.button(
                label,
                selected ? Color.WHITE : NourishColors.MUTED,
                selected ? NourishColors.GREEN : Color.TRANSPARENT
        );
        button.setTextSize(12);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        button.setOnClickListener(view -> callbacks.selectTab(tab));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(42), 1);
        params.leftMargin = ui.dp(2);
        params.rightMargin = ui.dp(2);
        button.setLayoutParams(params);
        return button;
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

    private String plural(long count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private String formatValue(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }
}
