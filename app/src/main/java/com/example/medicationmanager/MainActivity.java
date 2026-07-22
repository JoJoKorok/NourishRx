package com.example.medicationmanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.medicationmanager.api.OpenFoodFactsClient;
import com.example.medicationmanager.data.MealFoodLog;
import com.example.medicationmanager.data.Medication;
import com.example.medicationmanager.data.MedicationStore;
import com.example.medicationmanager.data.NutritionFood;
import com.example.medicationmanager.data.Profile;
import com.example.medicationmanager.data.WeightEntry;
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
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 42;
    private static final int REQUEST_PROFILE_PHOTO = 43;
    private static final String PREF_SELECTED_PROFILE_ID = "selected_profile_id";
    private static final String PREF_APP_MODE = "app_mode";
    private static final String MODE_MEDICATION = "medication";
    private static final String MODE_NUTRITION = "nutrition";

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
    private final DateTimeFormatter shortDateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault());

    private MedicationStore store;
    private LinearLayout root;
    private LinearLayout content;
    private String currentTab = "today";
    private String currentMode = MODE_MEDICATION;
    private long currentProfileId;
    private long pendingPhotoProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new MedicationStore(this);
        currentProfileId = loadSelectedProfileId();
        currentMode = loadAppMode();
        currentTab = defaultTabForMode(currentMode);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROFILE_PHOTO) {
            return;
        }

        if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingPhotoProfileId > 0) {
            Uri photoUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(photoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Some providers grant a temporary read URI instead of a persistable one.
            }
            Profile profile = store.getProfile(pendingPhotoProfileId);
            if (profile != null) {
                showProfilePhotoEditor(profile, photoUri.toString(), 1.0f, 0.0f, 0.0f, 1.0f);
            }
        }
        pendingPhotoProfileId = 0;
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

        Profile profile = selectedProfile();
        String profileName = profile.name;
        View mark = profileAvatar(profile, 54, COLOR_GREEN, 18);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(avatarWidthDp(profile, 54)), dp(54));
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

        boolean nutritionMode = MODE_NUTRITION.equals(currentMode);
        Button add = button(nutritionMode ? "+ Log" : "+ Med", Color.WHITE, COLOR_GREEN);
        add.setOnClickListener(view -> {
            if (nutritionMode) {
                showLogFoodDialog("");
            } else {
                showMedicationDialog(null);
            }
        });
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

        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        modeParams.topMargin = dp(10);
        panel.addView(modeSwitchRow(), modeParams);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, 0);
        if (nutritionMode) {
            LocalDate today = LocalDate.now(zoneId);
            long start = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
            long end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
            int foodLogCount = store.getMealFoodLogs(currentProfileId, start, end).size();
            int waterOunces = store.getWaterOunces(currentProfileId, start, end);
            List<WeightEntry> weights = store.getWeightEntries(currentProfileId, 1);
            stats.addView(summaryPill(plural(foodLogCount, "food log", "food logs"), COLOR_GREEN, COLOR_GREEN_SOFT));
            stats.addView(summaryPill(waterOunces + " oz water", COLOR_BLUE, COLOR_BLUE_SOFT));
            stats.addView(summaryPill(weights.isEmpty() ? "no weight" : formatPounds(weights.get(0).pounds) + " lb", COLOR_GOLD, COLOR_GOLD_SOFT));
        } else {
            List<Medication> medications = store.getAllMedications(currentProfileId);
            int todayCount = doseRowsFor(LocalDate.now(zoneId)).size();
            long lowCount = medications.stream().filter(Medication::isLowStock).count();
            stats.addView(summaryPill(plural(todayCount, "dose", "doses"), COLOR_GREEN, COLOR_GREEN_SOFT));
            stats.addView(summaryPill(plural(medications.size(), "med", "meds"), COLOR_BLUE, COLOR_BLUE_SOFT));
            stats.addView(summaryPill(plural(lowCount, "refill", "refills"), COLOR_GOLD, COLOR_GOLD_SOFT));
        }
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

    private LinearLayout modeSwitchRow() {
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(dp(4), dp(4), dp(4), dp(4));
        modes.setBackground(rounded(COLOR_CARD, COLOR_BORDER, dp(20)));
        modes.addView(modeButton("Medication", MODE_MEDICATION));
        modes.addView(modeButton("Nutrition", MODE_NUTRITION));
        return modes;
    }

    private Button modeButton(String label, String mode) {
        boolean selected = currentMode.equals(mode);
        Button button = button(label, selected ? Color.WHITE : COLOR_MUTED, selected ? COLOR_BLUE : Color.TRANSPARENT);
        button.setOnClickListener(view -> setAppMode(mode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout tabRow() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(rounded(COLOR_TAB_TRACK, Color.TRANSPARENT, dp(20)));
        if (MODE_NUTRITION.equals(currentMode)) {
            tabs.addView(tabButton("Today", "nutrition_today"));
            tabs.addView(tabButton("Meals", "nutrition_meals"));
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
        if (MODE_NUTRITION.equals(currentMode)) {
            if ("nutrition_meals".equals(currentTab)) {
                renderNutritionMeals();
            } else if ("nutrition_foods".equals(currentTab)) {
                renderNutritionFoods();
            } else if ("nutrition_body".equals(currentTab)) {
                renderNutritionBody();
            } else {
                renderNutritionToday();
            }
            return;
        }

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

    private void renderNutritionToday() {
        LocalDate today = LocalDate.now(zoneId);
        long start = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        List<MealFoodLog> logs = store.getMealFoodLogs(currentProfileId, start, end);
        List<WeightEntry> weights = store.getWeightEntries(currentProfileId, 5);
        int waterOunces = store.getWaterOunces(currentProfileId, start, end);

        int calories = 0;
        float protein = 0.0f;
        float carbs = 0.0f;
        float fat = 0.0f;
        for (MealFoodLog log : logs) {
            calories += log.calories();
            protein += log.proteinGrams();
            carbs += log.totalCarbsGrams();
            fat += log.totalFatGrams();
        }

        content.addView(sectionTitle("Nutrition", selectedProfileName() + " has " + logs.size() + " foods logged today"));
        content.addView(nutritionSummaryCard(calories, protein, carbs, fat));
        content.addView(defaultMealsCard(store.getMealDefaults(currentProfileId)));
        content.addView(waterCard(waterOunces, start, end));
        content.addView(weightCard(weights));

        content.addView(sectionTitle("Meal log", logs.isEmpty() ? "No foods logged yet" : plural(distinctMealCount(logs), "meal", "meals") + " today"));
        if (logs.isEmpty()) {
            emptyState("Create foods once, then log them into any meal.", "Log food", view -> showLogFoodDialog(""));
            return;
        }

        for (MealFoodLog log : logs) {
            content.addView(mealLogCard(log));
        }
    }

    private void renderNutritionMeals() {
        LocalDate today = LocalDate.now(zoneId);
        long start = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        List<MealFoodLog> logs = store.getMealFoodLogs(currentProfileId, start, end);

        content.addView(sectionTitle("Meals", logs.isEmpty() ? "No foods logged today" : plural(logs.size(), "food entry", "food entries") + " today"));
        content.addView(defaultMealsCard(store.getMealDefaults(currentProfileId)));
        Button addMeal = button("+ Log food", COLOR_GREEN, COLOR_GREEN_SOFT);
        addMeal.setOnClickListener(view -> showLogFoodDialog(""));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        addParams.topMargin = dp(4);
        content.addView(addMeal, addParams);

        if (logs.isEmpty()) {
            emptyState("Pick a saved food and add it into breakfast, lunch, dinner, or any meal you name.", "Log food", view -> showLogFoodDialog(""));
            return;
        }

        for (MealFoodLog log : logs) {
            content.addView(mealLogCard(log));
        }
    }

    private void renderNutritionFoods() {
        List<NutritionFood> foods = store.getNutritionFoods(currentProfileId);

        content.addView(sectionTitle("Foods", foods.isEmpty() ? "No saved foods yet" : plural(foods.size(), "saved food", "saved foods")));
        LinearLayout actions = actionRow();
        actions.setPadding(0, dp(4), 0, 0);

        Button addFood = button("+ Manual", COLOR_GREEN, COLOR_GREEN_SOFT);
        addFood.setOnClickListener(view -> showFoodDialog(null));
        actions.addView(addFood, weightedActionParams());

        Button searchFood = button("Find online", COLOR_BLUE, COLOR_BLUE_SOFT);
        searchFood.setOnClickListener(view -> showOpenFoodFactsSearchDialog());
        actions.addView(searchFood, weightedActionParams());
        content.addView(actions);

        if (foods.isEmpty()) {
            emptyState("Save food items manually or import inspectable options from OpenFoodFacts.", "Search foods", view -> showOpenFoodFactsSearchDialog());
            return;
        }

        for (NutritionFood food : foods) {
            content.addView(foodCard(food));
        }
    }

    private void renderNutritionBody() {
        LocalDate today = LocalDate.now(zoneId);
        long start = today.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        int waterOunces = store.getWaterOunces(currentProfileId, start, end);
        List<WeightEntry> weights = store.getWeightEntries(currentProfileId, 10);

        content.addView(sectionTitle("Body", "Track water intake and weight"));
        content.addView(waterCard(waterOunces, start, end));
        content.addView(weightCard(weights));
    }

    private View nutritionSummaryCard(int calories, float protein, float carbs, float fat) {
        LinearLayout card = card();
        TextView title = text("Daily intake", 13, COLOR_MUTED, Typeface.BOLD);
        card.addView(title);
        card.addView(text(calories + " calories", 26, COLOR_INK, Typeface.BOLD));

        LinearLayout macros = new LinearLayout(this);
        macros.setOrientation(LinearLayout.HORIZONTAL);
        macros.setPadding(0, dp(12), 0, 0);
        macros.addView(summaryPill(formatGrams(protein) + " protein", COLOR_GREEN, COLOR_GREEN_SOFT));
        macros.addView(summaryPill(formatGrams(carbs) + " carbs", COLOR_BLUE, COLOR_BLUE_SOFT));
        macros.addView(summaryPill(formatGrams(fat) + " fat", COLOR_GOLD, COLOR_GOLD_SOFT));
        card.addView(macros);

        Button addMeal = button("+ Log food", COLOR_GREEN, COLOR_GREEN_SOFT);
        addMeal.setOnClickListener(view -> showLogFoodDialog(""));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        addParams.topMargin = dp(12);
        card.addView(addMeal, addParams);
        return card;
    }

    private View defaultMealsCard(List<String> mealDefaults) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text("Default meals", 19, COLOR_INK, Typeface.BOLD));
        details.addView(text(plural(mealDefaults.size(), "saved meal name", "saved meal names"), 13, COLOR_MUTED, Typeface.BOLD));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button edit = button("Edit", COLOR_BLUE, COLOR_BLUE_SOFT);
        edit.setOnClickListener(view -> showMealDefaultsDialog());
        top.addView(edit, compactButtonParams());
        card.addView(top);

        for (String mealName : mealDefaults) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, 0);

            TextView name = text(mealName, 15, COLOR_INK, Typeface.BOLD);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(42), 1));

            Button log = button("Log", COLOR_GREEN, COLOR_GREEN_SOFT);
            log.setOnClickListener(view -> showLogFoodDialog(mealName));
            row.addView(log, new LinearLayout.LayoutParams(dp(88), dp(42)));
            card.addView(row);
        }
        return card;
    }

    private View waterCard(int waterOunces, long startMillis, long endMillis) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text("Water", 19, COLOR_INK, Typeface.BOLD));
        details.addView(text(waterOunces + " oz today", 14, COLOR_MUTED, Typeface.BOLD));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(waterOunces >= 64 ? "Hydrated" : "Track"));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button addEight = button("+8 oz", COLOR_BLUE, COLOR_BLUE_SOFT);
        addEight.setOnClickListener(view -> addWaterAndRefresh(8));
        actions.addView(addEight, weightedActionParams());

        Button addSixteen = button("+16 oz", COLOR_GREEN, COLOR_GREEN_SOFT);
        addSixteen.setOnClickListener(view -> addWaterAndRefresh(16));
        actions.addView(addSixteen, weightedActionParams());

        Button custom = button("Custom", COLOR_GOLD, COLOR_GOLD_SOFT);
        custom.setOnClickListener(view -> showWaterDialog());
        actions.addView(custom, weightedActionParams());
        card.addView(actions);

        if (waterOunces > 0) {
            Button clear = button("Clear today", COLOR_CORAL, COLOR_CORAL_SOFT);
            clear.setOnClickListener(view -> confirmClearWater(startMillis, endMillis));
            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44)
            );
            clearParams.topMargin = dp(10);
            card.addView(clear, clearParams);
        }
        return card;
    }

    private View weightCard(List<WeightEntry> weights) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text("Weight", 19, COLOR_INK, Typeface.BOLD));
        if (weights.isEmpty()) {
            details.addView(text("No weight logged yet", 14, COLOR_MUTED, Typeface.BOLD));
        } else {
            WeightEntry latest = weights.get(0);
            details.addView(text(formatPounds(latest.pounds) + " lb latest", 14, COLOR_MUTED, Typeface.BOLD));
            details.addView(text(formatShortDateTime(latest.loggedAt), 13, COLOR_MUTED, Typeface.NORMAL));
        }
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button add = button("+ Weight", COLOR_GREEN, COLOR_GREEN_SOFT);
        add.setOnClickListener(view -> showWeightDialog());
        top.addView(add, compactButtonParams());
        card.addView(top);

        for (WeightEntry entry : weights) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, 0);

            LinearLayout label = new LinearLayout(this);
            label.setOrientation(LinearLayout.VERTICAL);
            label.addView(text(formatPounds(entry.pounds) + " lb", 15, COLOR_INK, Typeface.BOLD));
            label.addView(text(formatShortDateTime(entry.loggedAt), 12, COLOR_MUTED, Typeface.NORMAL));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button delete = button("Delete", COLOR_CORAL, COLOR_CORAL_SOFT);
            delete.setOnClickListener(view -> {
                store.deleteWeightEntry(entry.id);
                renderShell();
            });
            row.addView(delete, new LinearLayout.LayoutParams(dp(94), dp(40)));
            card.addView(row);
        }
        return card;
    }

    private View mealLogCard(MealFoodLog log) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        NutritionFood food = log.food;
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(log.mealName, 19, COLOR_INK, Typeface.BOLD));
        details.addView(text((food == null ? "Saved food" : food.displayName()) +
                " - " + formatServings(log.servings) + " serving" + (Math.abs(log.servings - 1.0f) < 0.05f ? "" : "s"),
                13,
                COLOR_MUTED,
                Typeface.BOLD));
        details.addView(text(log.calories() + " cal - " +
                formatGrams(log.proteinGrams()) + " protein - " +
                formatGrams(log.totalCarbsGrams()) + " carbs - " +
                formatGrams(log.totalFatGrams()) + " fat", 13, COLOR_MUTED, Typeface.NORMAL));
        details.addView(text(formatTime(log.eatenAt), 12, COLOR_MUTED, Typeface.NORMAL));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(log.calories() > 0 ? log.calories() + " cal" : "Food"));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button edit = button("Edit", COLOR_BLUE, COLOR_BLUE_SOFT);
        edit.setOnClickListener(view -> showLogFoodDialog(log));
        actions.addView(edit, weightedActionParams());

        Button delete = button("Delete", COLOR_CORAL, COLOR_CORAL_SOFT);
        delete.setOnClickListener(view -> confirmDeleteMealLog(log));
        actions.addView(delete, weightedActionParams());
        card.addView(actions);
        return card;
    }

    private View foodCard(NutritionFood food) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(food.displayName(), 19, COLOR_INK, Typeface.BOLD));
        details.addView(text(food.servingSummary(), 13, COLOR_MUTED, Typeface.BOLD));
        details.addView(text(food.calories + " cal - " +
                formatGrams(food.proteinGrams) + " protein - " +
                formatGrams(food.totalCarbsGrams) + " carbs - " +
                formatGrams(food.totalFatGrams) + " fat", 13, COLOR_MUTED, Typeface.NORMAL));
        details.addView(text(formatMg(food.sodiumMg) + " sodium - " +
                formatGrams(food.totalSugarsGrams) + " sugars - " +
                formatMg(food.potassiumMg) + " potassium", 12, COLOR_MUTED, Typeface.NORMAL));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(food.calories > 0 ? food.calories + " cal" : "Food"));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button log = button("Log", COLOR_GREEN, COLOR_GREEN_SOFT);
        log.setOnClickListener(view -> showLogFoodDialog("", food.id));
        actions.addView(log, weightedActionParams());

        Button edit = button("Edit", COLOR_BLUE, COLOR_BLUE_SOFT);
        edit.setOnClickListener(view -> showFoodDialog(food));
        actions.addView(edit, weightedActionParams());

        Button delete = button("Delete", COLOR_CORAL, COLOR_CORAL_SOFT);
        delete.setOnClickListener(view -> confirmDeleteFood(food));
        actions.addView(delete, weightedActionParams());
        card.addView(actions);
        return card;
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
        View avatar = profileAvatar(profile, 38, selected ? COLOR_GREEN : COLOR_BLUE, 14);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(avatarWidthDp(profile, 38)), dp(38));
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

        Button photo = button("Photo", COLOR_GOLD, COLOR_GOLD_SOFT);
        photo.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showProfilePhotoOptions(profile);
        });
        actions.addView(photo, weightedActionParams());
        row.addView(actions);

        LinearLayout editActions = actionRow();
        Button rename = button("Rename", COLOR_BLUE, COLOR_BLUE_SOFT);
        rename.setOnClickListener(view -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showRenameProfileDialog(profile);
        });
        editActions.addView(rename, weightedActionParams());

        Button delete = button("Delete", COLOR_CORAL, COLOR_CORAL_SOFT);
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
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private void showProfilePhotoOptions(Profile profile) {
        if (!profile.hasAvatar()) {
            chooseProfilePhoto(profile);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(profile.name + " photo")
                .setItems(new CharSequence[]{"Edit framing", "Change photo", "Remove photo"}, (dialog, which) -> {
                    if (which == 0) {
                        showProfilePhotoEditor(
                                profile,
                                profile.avatarUri,
                                profile.avatarZoom,
                                profile.avatarOffsetX,
                                profile.avatarOffsetY,
                                profile.avatarAspectRatio
                        );
                    } else if (which == 1) {
                        chooseProfilePhoto(profile);
                    } else {
                        store.clearProfileAvatar(profile.id);
                        Toast.makeText(this, "Profile photo removed.", Toast.LENGTH_SHORT).show();
                        renderShell();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseProfilePhoto(Profile profile) {
        pendingPhotoProfileId = profile.id;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PROFILE_PHOTO);
        } catch (Exception exception) {
            pendingPhotoProfileId = 0;
            Toast.makeText(this, "No photo picker is available.", Toast.LENGTH_LONG).show();
        }
    }

    private void showProfilePhotoEditor(
            Profile profile,
            String avatarUri,
            float zoom,
            float offsetX,
            float offsetY,
            float aspectRatio
    ) {
        Bitmap bitmap = loadBitmap(avatarUri);
        if (bitmap == null) {
            Toast.makeText(this, "That photo could not be opened.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        ProfilePhotoEditorView editor = new ProfilePhotoEditorView(bitmap);
        editor.setFrame(zoom, offsetX, offsetY, aspectRatio);
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(280)
        );
        form.addView(editor, editorParams);

        TextView zoomLabel = fieldLabel("Zoom");
        form.addView(zoomLabel);
        SeekBar zoomSlider = new SeekBar(this);
        zoomSlider.setMax(200);
        zoomSlider.setProgress(Math.round((editor.getZoom() - 1.0f) * 100.0f));
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                editor.setZoom(1.0f + progress / 100.0f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editor.setOnFrameChangedListener(() -> {
            int progress = Math.round((editor.getZoom() - 1.0f) * 100.0f);
            if (zoomSlider.getProgress() != progress) {
                zoomSlider.setProgress(progress);
            }
        });
        form.addView(zoomSlider);

        form.addView(fieldLabel("Frame"));
        LinearLayout aspectActions = actionRow();
        Button square = button("Square", COLOR_GREEN, COLOR_GREEN_SOFT);
        square.setOnClickListener(view -> editor.setAspectRatio(1.0f));
        aspectActions.addView(square, weightedActionParams());

        Button portrait = button("Portrait", COLOR_BLUE, COLOR_BLUE_SOFT);
        portrait.setOnClickListener(view -> editor.setAspectRatio(0.8f));
        aspectActions.addView(portrait, weightedActionParams());

        Button wide = button("Wide", COLOR_GOLD, COLOR_GOLD_SOFT);
        wide.setOnClickListener(view -> editor.setAspectRatio(1.6f));
        aspectActions.addView(wide, weightedActionParams());
        form.addView(aspectActions);

        form.addView(fieldLabel("Position"));
        LinearLayout horizontalActions = actionRow();
        Button left = button("Left", COLOR_BLUE, COLOR_BLUE_SOFT);
        left.setOnClickListener(view -> editor.nudge(-0.12f, 0.0f));
        horizontalActions.addView(left, weightedActionParams());

        Button center = button("Center", COLOR_GREEN, COLOR_GREEN_SOFT);
        center.setOnClickListener(view -> editor.center());
        horizontalActions.addView(center, weightedActionParams());

        Button right = button("Right", COLOR_BLUE, COLOR_BLUE_SOFT);
        right.setOnClickListener(view -> editor.nudge(0.12f, 0.0f));
        horizontalActions.addView(right, weightedActionParams());
        form.addView(horizontalActions);

        LinearLayout verticalActions = actionRow();
        Button up = button("Up", COLOR_BLUE, COLOR_BLUE_SOFT);
        up.setOnClickListener(view -> editor.nudge(0.0f, -0.12f));
        verticalActions.addView(up, weightedActionParams());

        Button down = button("Down", COLOR_BLUE, COLOR_BLUE_SOFT);
        down.setOnClickListener(view -> editor.nudge(0.0f, 0.12f));
        verticalActions.addView(down, weightedActionParams());
        form.addView(verticalActions);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Frame " + profile.name)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                store.updateProfileAvatar(
                        profile.id,
                        avatarUri,
                        editor.getZoom(),
                        editor.getOffsetX(),
                        editor.getOffsetY(),
                        editor.getAspectRatio()
                );
                Toast.makeText(this, "Profile photo updated.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                renderShell();
            });
        });

        dialog.show();
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
                existing.doseMinutes(),
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

        ArrayList<Integer> selectedDoseMinutes = new ArrayList<>(medication.doseMinutes());
        TextView frequencySummary = text("", 13, COLOR_MUTED, Typeface.BOLD);
        LinearLayout doseTimesList = new LinearLayout(this);
        doseTimesList.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] renderDoseTimes = new Runnable[1];
        renderDoseTimes[0] = () -> renderDoseTimeRows(doseTimesList, frequencySummary, selectedDoseMinutes, renderDoseTimes[0]);
        renderDoseTimes[0].run();

        Button addDoseTime = button("+ Dose time", COLOR_GREEN, COLOR_GREEN_SOFT);
        addDoseTime.setOnClickListener(view -> {
            if (selectedDoseMinutes.size() >= Medication.MAX_DOSES_PER_DAY) {
                Toast.makeText(this, "Maximum is " + Medication.MAX_DOSES_PER_DAY + " doses per day.", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedDoseMinutes.add(nextSuggestedDoseTime(selectedDoseMinutes));
            renderDoseTimes[0].run();
        });

        EditText quantityField = field("Current quantity", existing == null ? "" : String.valueOf(medication.quantity), InputType.TYPE_CLASS_NUMBER);
        EditText thresholdField = field("Refill threshold", existing == null ? "" : String.valueOf(medication.refillThreshold), InputType.TYPE_CLASS_NUMBER);
        CheckBox activeBox = new CheckBox(this);
        activeBox.setText("Active reminders");
        activeBox.setTextColor(COLOR_INK);
        activeBox.setTextSize(15);
        activeBox.setChecked(medication.active);

        form.addView(nameField);
        form.addView(dosageField);
        form.addView(instructionsField);
        form.addView(fieldLabel("Frequency"));
        form.addView(frequencySummary);
        form.addView(doseTimesList);
        LinearLayout.LayoutParams addDoseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        addDoseParams.topMargin = dp(8);
        form.addView(addDoseTime, addDoseParams);
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
                        selectedDoseMinutes.get(0),
                        selectedDoseMinutes.size(),
                        selectedDoseMinutes,
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

    private void showLogFoodDialog(String presetName) {
        showLogFoodDialog(null, presetName, 0);
    }

    private void showLogFoodDialog(String presetName, long selectedFoodId) {
        showLogFoodDialog(null, presetName, selectedFoodId);
    }

    private void showLogFoodDialog(MealFoodLog existing) {
        showLogFoodDialog(existing, existing == null ? "" : existing.mealName, existing == null ? 0 : existing.foodId);
    }

    private void showLogFoodDialog(MealFoodLog existing, String presetName, long selectedFoodId) {
        List<NutritionFood> foods = store.getNutritionFoods(currentProfileId);
        if (foods.isEmpty()) {
            Toast.makeText(this, "Create a food before logging it.", Toast.LENGTH_SHORT).show();
            showFoodDialog(null);
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
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        form.addView(fieldLabel("Food"));
        Spinner foodSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, foodNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        foodSpinner.setAdapter(adapter);
        foodSpinner.setSelection(selectedIndex);
        foodSpinner.setPadding(dp(10), 0, dp(10), 0);
        foodSpinner.setBackground(rounded(COLOR_CARD, COLOR_BORDER, dp(18)));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        spinnerParams.topMargin = dp(8);
        form.addView(foodSpinner, spinnerParams);

        EditText mealNameField = field("Meal name", presetName, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText servingsField = field("Servings eaten", existing == null ? "" : formatFloatInput(existing.servings), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final boolean[] customMealTime = {existing != null};
        final int[] mealMinutes = {minuteOfDay(baseTime)};
        LinearLayout timeActions = actionRow();
        Button timeButton = button(customMealTime[0] ? "Ate at " + Medication.formatMinutes(mealMinutes[0]) : "Time: now", COLOR_BLUE, COLOR_BLUE_SOFT);
        timeButton.setOnClickListener(view -> showMealTimePicker(timeButton, mealMinutes, customMealTime));
        timeActions.addView(timeButton, weightedActionParams());

        Button nowButton = button("Use now", COLOR_GREEN, COLOR_GREEN_SOFT);
        nowButton.setOnClickListener(view -> {
            customMealTime[0] = false;
            mealMinutes[0] = minuteOfDay(System.currentTimeMillis());
            timeButton.setText("Time: now");
        });
        timeActions.addView(nowButton, weightedActionParams());

        form.addView(mealNameField);
        form.addView(servingsField);
        form.addView(fieldLabel("Time eaten"));
        form.addView(timeActions);

        AlertDialog dialog = new AlertDialog.Builder(this)
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
                        currentProfileId,
                        food.id,
                        mealName,
                        servings,
                        customMealTime[0] ? millisForMealTime(baseTime, mealMinutes[0]) : System.currentTimeMillis(),
                        food
                ));
                dialog.dismiss();
                renderShell();
            });
        });

        dialog.show();
    }

    private void showFoodDialog(NutritionFood existing) {
        NutritionFood food = existing == null
                ? new NutritionFood(
                        0,
                        currentProfileId,
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
                )
                : existing;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        int decimalInput = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText brandField = field("Brand", food.brand, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText nameField = field("Food name", food.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText servingSizeField = field("Serving size", food.servingSize, InputType.TYPE_CLASS_TEXT);
        boolean prefillNutrition = existing != null;
        EditText servingsPerContainerField = field("Servings per container", prefillNutrition ? formatFloatInput(food.servingsPerContainer) : "", decimalInput);
        EditText caloriesField = field("Calories", prefillNutrition ? String.valueOf(food.calories) : "", InputType.TYPE_CLASS_NUMBER);
        EditText totalFatField = field("Total fat (g)", prefillNutrition ? formatFloatInput(food.totalFatGrams) : "", decimalInput);
        EditText saturatedFatField = field("Saturated fat (g)", prefillNutrition ? formatFloatInput(food.saturatedFatGrams) : "", decimalInput);
        EditText transFatField = field("Trans fat (g)", prefillNutrition ? formatFloatInput(food.transFatGrams) : "", decimalInput);
        EditText cholesterolField = field("Cholesterol (mg)", prefillNutrition ? formatFloatInput(food.cholesterolMg) : "", decimalInput);
        EditText sodiumField = field("Sodium (mg)", prefillNutrition ? formatFloatInput(food.sodiumMg) : "", decimalInput);
        EditText carbsField = field("Total carbs (g)", prefillNutrition ? formatFloatInput(food.totalCarbsGrams) : "", decimalInput);
        EditText fiberField = field("Fiber (g)", prefillNutrition ? formatFloatInput(food.fiberGrams) : "", decimalInput);
        EditText totalSugarsField = field("Total sugars (g)", prefillNutrition ? formatFloatInput(food.totalSugarsGrams) : "", decimalInput);
        EditText addedSugarsField = field("Added sugars (g)", prefillNutrition ? formatFloatInput(food.addedSugarsGrams) : "", decimalInput);
        EditText proteinField = field("Protein (g)", prefillNutrition ? formatFloatInput(food.proteinGrams) : "", decimalInput);
        EditText vitaminDField = field("Vitamin D (mcg)", prefillNutrition ? formatFloatInput(food.vitaminDMcg) : "", decimalInput);
        EditText calciumField = field("Calcium (mg)", prefillNutrition ? formatFloatInput(food.calciumMg) : "", decimalInput);
        EditText ironField = field("Iron (mg)", prefillNutrition ? formatFloatInput(food.ironMg) : "", decimalInput);
        EditText potassiumField = field("Potassium (mg)", prefillNutrition ? formatFloatInput(food.potassiumMg) : "", decimalInput);

        form.addView(fieldLabel("Food"));
        form.addView(brandField);
        form.addView(nameField);
        form.addView(fieldLabel("Serving"));
        form.addView(servingSizeField);
        form.addView(servingsPerContainerField);
        form.addView(fieldLabel("Nutrition facts per serving"));
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
        form.addView(fieldLabel("Vitamins and minerals"));
        form.addView(vitaminDField);
        form.addView(calciumField);
        form.addView(ironField);
        form.addView(potassiumField);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
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
                        currentProfileId,
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
                renderShell();
            });
        });

        dialog.show();
    }

    private void showOpenFoodFactsSearchDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        EditText queryField = field("Search food name", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(queryField);

        Button search = button("Search OpenFoodFacts", COLOR_BLUE, COLOR_BLUE_SOFT);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        searchParams.topMargin = dp(10);
        form.addView(search, searchParams);

        TextView status = text("Ready to search.", 13, COLOR_MUTED, Typeface.BOLD);
        status.setPadding(0, dp(12), 0, dp(4));
        form.addView(status);

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        form.addView(results);

        Button loadMore = button("Load more", COLOR_GREEN, COLOR_GREEN_SOFT);
        loadMore.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadMoreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        loadMoreParams.topMargin = dp(12);
        form.addView(loadMore, loadMoreParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Find food")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        final int[] nextPage = {1};
        final String[] activeQuery = {""};
        search.setOnClickListener(view -> startOpenFoodFactsSearch(scrollView, queryField, search, loadMore, status, results, nextPage, activeQuery, true));
        loadMore.setOnClickListener(view -> startOpenFoodFactsSearch(scrollView, queryField, search, loadMore, status, results, nextPage, activeQuery, false));

        dialog.setOnShowListener(dialogInterface -> queryField.requestFocus());
        dialog.show();
    }

    private void startOpenFoodFactsSearch(
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
                runOnUiThread(() -> {
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
                        appendOpenFoodFactsResults(results, found);
                        if (!reset) {
                            scrollView.post(() -> scrollView.scrollTo(0, previousScrollY));
                        }
                    }
                    nextPage[0] = page + 1;
                    loadMore.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                    loadMore.setEnabled(hasMore);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    search.setEnabled(true);
                    loadMore.setEnabled(true);
                    status.setText("Search failed. Check connection and try again.");
                    Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void appendOpenFoodFactsResults(
            LinearLayout container,
            List<OpenFoodFactsClient.SearchResult> results
    ) {
        for (OpenFoodFactsClient.SearchResult result : results) {
            container.addView(openFoodFactsResultCard(result));
        }
    }

    private View openFoodFactsResultCard(OpenFoodFactsClient.SearchResult result) {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(result.displayName(), 18, COLOR_INK, Typeface.BOLD));
        details.addView(text(openFoodFactsResultSummary(result), 13, COLOR_MUTED, Typeface.NORMAL));
        details.addView(text("Barcode " + result.code, 12, COLOR_MUTED, Typeface.NORMAL));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(result.nutritionGrade.isEmpty() ? "OFF" : result.nutritionGrade.toUpperCase(Locale.US)));
        card.addView(top);

        LinearLayout actions = actionRow();
        Button inspect = button("Inspect", COLOR_BLUE, COLOR_BLUE_SOFT);
        inspect.setOnClickListener(view -> showOpenFoodFactsInspectDialog(result));
        actions.addView(inspect, weightedActionParams());

        Button add = button("Add", COLOR_GREEN, COLOR_GREEN_SOFT);
        add.setOnClickListener(view -> importOpenFoodFactsFood(result, false));
        actions.addView(add, weightedActionParams());
        card.addView(actions);
        return card;
    }

    private String openFoodFactsResultSummary(OpenFoodFactsClient.SearchResult result) {
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

    private void importOpenFoodFactsFood(OpenFoodFactsClient.SearchResult result, boolean editBeforeSaving) {
        Toast.makeText(this, "Loading food details...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NutritionFood food = new OpenFoodFactsClient().fetchNutritionFood(result.code, currentProfileId);
                runOnUiThread(() -> {
                    if (editBeforeSaving) {
                        showFoodDialog(food);
                        return;
                    }
                    store.saveNutritionFood(food);
                    Toast.makeText(this, "Saved " + food.displayName(), Toast.LENGTH_SHORT).show();
                    renderShell();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showOpenFoodFactsInspectDialog(OpenFoodFactsClient.SearchResult result) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(8), dp(18), 0);
        body.addView(text("Loading nutrition facts...", 15, COLOR_MUTED, Typeface.BOLD));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Inspect food")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        dialog.show();

        new Thread(() -> {
            try {
                NutritionFood food = new OpenFoodFactsClient().fetchNutritionFood(result.code, currentProfileId);
                runOnUiThread(() -> renderOpenFoodFactsInspection(dialog, body, food, result));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    body.removeAllViews();
                    body.addView(text("Could not load this food.", 16, COLOR_CORAL, Typeface.BOLD));
                    body.addView(text(exception.getMessage(), 13, COLOR_MUTED, Typeface.NORMAL));
                });
            }
        }).start();
    }

    private void renderOpenFoodFactsInspection(
            AlertDialog dialog,
            LinearLayout body,
            NutritionFood food,
            OpenFoodFactsClient.SearchResult result
    ) {
        body.removeAllViews();
        body.addView(text(food.displayName(), 21, COLOR_INK, Typeface.BOLD));
        body.addView(text("OpenFoodFacts barcode " + result.code, 12, COLOR_MUTED, Typeface.NORMAL));
        body.addView(fieldLabel("Serving"));
        body.addView(nutritionFactRow("Serving size", food.servingSize.isEmpty() ? "Not listed" : food.servingSize));
        body.addView(nutritionFactRow("Servings per container", food.servingsPerContainer > 0.0f ? formatServings(food.servingsPerContainer) : "Not listed"));

        body.addView(fieldLabel("Nutrition facts"));
        body.addView(nutritionFactRow("Calories", String.valueOf(food.calories)));
        body.addView(nutritionFactRow("Total fat", formatGrams(food.totalFatGrams)));
        body.addView(nutritionFactRow("Saturated fat", formatGrams(food.saturatedFatGrams)));
        body.addView(nutritionFactRow("Trans fat", formatGrams(food.transFatGrams)));
        body.addView(nutritionFactRow("Cholesterol", formatMg(food.cholesterolMg)));
        body.addView(nutritionFactRow("Sodium", formatMg(food.sodiumMg)));
        body.addView(nutritionFactRow("Total carbs", formatGrams(food.totalCarbsGrams)));
        body.addView(nutritionFactRow("Fiber", formatGrams(food.fiberGrams)));
        body.addView(nutritionFactRow("Total sugars", formatGrams(food.totalSugarsGrams)));
        body.addView(nutritionFactRow("Added sugars", formatGrams(food.addedSugarsGrams)));
        body.addView(nutritionFactRow("Protein", formatGrams(food.proteinGrams)));

        body.addView(fieldLabel("Vitamins and minerals"));
        body.addView(nutritionFactRow("Vitamin D", formatMcg(food.vitaminDMcg)));
        body.addView(nutritionFactRow("Calcium", formatMg(food.calciumMg)));
        body.addView(nutritionFactRow("Iron", formatMg(food.ironMg)));
        body.addView(nutritionFactRow("Potassium", formatMg(food.potassiumMg)));

        LinearLayout actions = actionRow();
        Button save = button("Save food", COLOR_GREEN, COLOR_GREEN_SOFT);
        save.setOnClickListener(view -> {
            store.saveNutritionFood(food);
            dialog.dismiss();
            Toast.makeText(this, "Saved " + food.displayName(), Toast.LENGTH_SHORT).show();
            renderShell();
        });
        actions.addView(save, weightedActionParams());

        Button edit = button("Edit first", COLOR_BLUE, COLOR_BLUE_SOFT);
        edit.setOnClickListener(view -> {
            dialog.dismiss();
            showFoodDialog(food);
        });
        actions.addView(edit, weightedActionParams());
        body.addView(actions);
    }

    private View nutritionFactRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView labelView = text(label, 14, COLOR_INK, Typeface.BOLD);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView valueView = text(value, 14, COLOR_MUTED, Typeface.BOLD);
        valueView.setGravity(Gravity.RIGHT);
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void showMealDefaultsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        LinearLayout defaultsList = new LinearLayout(this);
        defaultsList.setOrientation(LinearLayout.VERTICAL);
        renderMealDefaultRows(defaultsList, new ArrayList<>(store.getMealDefaults(currentProfileId)));
        form.addView(defaultsList);

        Button addDefault = button("+ Default meal", COLOR_GREEN, COLOR_GREEN_SOFT);
        addDefault.setOnClickListener(view -> {
            ArrayList<String> names = mealDefaultNamesFrom(defaultsList);
            names.add("Meal " + (names.size() + 1));
            renderMealDefaultRows(defaultsList, names);
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        addParams.topMargin = dp(10);
        form.addView(addDefault, addParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
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
                    Toast.makeText(this, "Keep at least one default meal.", Toast.LENGTH_SHORT).show();
                    return;
                }
                store.saveMealDefaults(currentProfileId, names);
                dialog.dismiss();
                renderShell();
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
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);

            EditText nameField = field("Meal name", mealNames.get(i), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            row.addView(nameField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button remove = button("Remove", COLOR_CORAL, COLOR_CORAL_SOFT);
            remove.setEnabled(mealNames.size() > 1);
            remove.setAlpha(mealNames.size() > 1 ? 1.0f : 0.45f);
            remove.setOnClickListener(view -> {
                ArrayList<String> names = mealDefaultNamesFrom(container);
                if (names.size() <= 1) {
                    Toast.makeText(this, "Keep at least one default meal.", Toast.LENGTH_SHORT).show();
                    return;
                }
                names.remove(index);
                renderMealDefaultRows(container, names);
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(96), dp(48));
            removeParams.leftMargin = dp(8);
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

    private void confirmDeleteMealLog(MealFoodLog log) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this food log?")
                .setMessage("This removes the entry from the meal log.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deleteMealFoodLog(log.id);
                    renderShell();
                })
                .show();
    }

    private void confirmDeleteFood(NutritionFood food) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + food.displayName() + "?")
                .setMessage("This removes the saved food and any meal logs that use it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deleteNutritionFood(food.id);
                    renderShell();
                })
                .show();
    }

    private void addWaterAndRefresh(int ounces) {
        store.addWater(currentProfileId, ounces);
        renderShell();
    }

    private void showWaterDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText ouncesField = field("Ounces", "", InputType.TYPE_CLASS_NUMBER);
        form.addView(ouncesField);

        AlertDialog dialog = new AlertDialog.Builder(this)
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
                store.addWater(currentProfileId, ounces);
                dialog.dismiss();
                renderShell();
            });
        });

        dialog.show();
    }

    private void confirmClearWater(long startMillis, long endMillis) {
        new AlertDialog.Builder(this)
                .setTitle("Clear today's water?")
                .setMessage("This removes all water entries for today.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    store.clearWater(currentProfileId, startMillis, endMillis);
                    renderShell();
                })
                .show();
    }

    private void showWeightDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText weightField = field("Weight in pounds", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(weightField);

        AlertDialog dialog = new AlertDialog.Builder(this)
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
                store.saveWeightEntry(new WeightEntry(0, currentProfileId, pounds, System.currentTimeMillis()));
                dialog.dismiss();
                renderShell();
            });
        });

        dialog.show();
    }

    private void renderDoseTimeRows(
            LinearLayout container,
            TextView frequencySummary,
            ArrayList<Integer> doseMinutes,
            Runnable refresh
    ) {
        normalizeDoseTimes(doseMinutes);
        frequencySummary.setText("Frequency: " + plural(doseMinutes.size(), "dose/day", "doses/day"));
        container.removeAllViews();

        for (int i = 0; i < doseMinutes.size(); i++) {
            int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);

            TextView label = text("Dose " + (index + 1), 14, COLOR_INK, Typeface.BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(72), dp(44));
            row.addView(label, labelParams);

            Button time = button(Medication.formatMinutes(doseMinutes.get(index)), COLOR_BLUE, COLOR_BLUE_SOFT);
            time.setOnClickListener(view -> showDoseTimePicker(doseMinutes, index, refresh));
            row.addView(time, new LinearLayout.LayoutParams(0, dp(44), 1));

            Button remove = button("Remove", COLOR_CORAL, COLOR_CORAL_SOFT);
            remove.setEnabled(doseMinutes.size() > 1);
            remove.setAlpha(doseMinutes.size() > 1 ? 1.0f : 0.45f);
            remove.setOnClickListener(view -> {
                if (doseMinutes.size() <= 1) {
                    Toast.makeText(this, "Keep at least one dose time.", Toast.LENGTH_SHORT).show();
                    return;
                }
                doseMinutes.remove(index);
                refresh.run();
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(96), dp(44));
            removeParams.leftMargin = dp(8);
            row.addView(remove, removeParams);

            container.addView(row);
        }
    }

    private void showDoseTimePicker(ArrayList<Integer> doseMinutes, int index, Runnable refresh) {
        int existingMinutes = doseMinutes.get(index);
        int hour = existingMinutes / 60;
        int minute = existingMinutes % 60;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    int newMinutes = Medication.normalizeMinutes((selectedHour * 60) + selectedMinute);
                    for (int i = 0; i < doseMinutes.size(); i++) {
                        if (i != index && doseMinutes.get(i) == newMinutes) {
                            Toast.makeText(this, "That dose time is already scheduled.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    doseMinutes.set(index, newMinutes);
                    refresh.run();
                },
                hour,
                minute,
                false
        );
        dialog.show();
    }

    private void showMealTimePicker(Button timeButton, int[] mealMinutes, boolean[] customMealTime) {
        int hour = mealMinutes[0] / 60;
        int minute = mealMinutes[0] % 60;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
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

    private int nextSuggestedDoseTime(List<Integer> doseMinutes) {
        if (doseMinutes.isEmpty()) {
            return 8 * 60;
        }

        ArrayList<Integer> sorted = new ArrayList<>(doseMinutes);
        normalizeDoseTimes(sorted);
        int candidate = Medication.normalizeMinutes(sorted.get(sorted.size() - 1) + (4 * 60));
        for (int attempt = 0; attempt < Medication.MAX_DOSES_PER_DAY; attempt++) {
            if (!sorted.contains(candidate)) {
                return candidate;
            }
            candidate = Medication.normalizeMinutes(candidate + 60);
        }
        return 8 * 60;
    }

    private void normalizeDoseTimes(ArrayList<Integer> doseMinutes) {
        if (doseMinutes.isEmpty()) {
            doseMinutes.add(8 * 60);
        }
        for (int i = 0; i < doseMinutes.size(); i++) {
            doseMinutes.set(i, Medication.normalizeMinutes(doseMinutes.get(i)));
        }
        doseMinutes.sort(Integer::compareTo);
        while (doseMinutes.size() > Medication.MAX_DOSES_PER_DAY) {
            doseMinutes.remove(doseMinutes.size() - 1);
        }
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

    private String loadAppMode() {
        String savedMode = getPreferences(MODE_PRIVATE).getString(PREF_APP_MODE, MODE_MEDICATION);
        if (MODE_NUTRITION.equals(savedMode)) {
            return MODE_NUTRITION;
        }
        return MODE_MEDICATION;
    }

    private void setAppMode(String mode) {
        currentMode = MODE_NUTRITION.equals(mode) ? MODE_NUTRITION : MODE_MEDICATION;
        currentTab = defaultTabForMode(currentMode);
        getPreferences(MODE_PRIVATE)
                .edit()
                .putString(PREF_APP_MODE, currentMode)
                .apply();
        renderShell();
    }

    private String defaultTabForMode(String mode) {
        return MODE_NUTRITION.equals(mode) ? "nutrition_today" : "today";
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

    private Profile selectedProfile() {
        Profile profile = store.getProfile(currentProfileId);
        if (profile == null) {
            currentProfileId = resolveProfileId(currentProfileId);
            profile = store.getProfile(currentProfileId);
        }
        if (profile == null) {
            return new Profile(currentProfileId, "Me", "", 1.0f, 0.0f, 0.0f, 1.0f, System.currentTimeMillis());
        }
        return profile;
    }

    private String selectedProfileName() {
        Profile profile = selectedProfile();
        return profile == null ? "Me" : profile.name;
    }

    private String formatTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormatter);
    }

    private String formatShortDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(shortDateTimeFormatter);
    }

    private String formatGrams(float grams) {
        return formatFloatInput(grams) + "g";
    }

    private String formatMg(float value) {
        return formatFloatInput(value) + "mg";
    }

    private String formatMcg(float value) {
        return formatFloatInput(value) + "mcg";
    }

    private String formatServings(float servings) {
        return formatFloatInput(servings);
    }

    private String formatPounds(float pounds) {
        return formatFloatInput(pounds);
    }

    private String formatFloatInput(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private int distinctMealCount(List<MealFoodLog> logs) {
        ArrayList<String> names = new ArrayList<>();
        for (MealFoodLog log : logs) {
            String name = log.mealName == null ? "" : log.mealName.trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names.size();
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

    private View profileAvatar(Profile profile, int sizeDp, int fallbackColor, int textSp) {
        int size = dp(sizeDp);
        if (profile != null && profile.hasAvatar()) {
            int width = dp(avatarWidthDp(profile, sizeDp));
            Bitmap source = loadBitmap(profile.avatarUri);
            Bitmap avatar = source == null
                    ? null
                    : createCroppedAvatarBitmap(source, Math.max(1, width * 2), Math.max(1, size * 2), profile);
            if (avatar != null) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.FIT_XY);
                image.setImageBitmap(avatar);
                image.setContentDescription(profile.name + " profile photo");
                return image;
            }
        }

        TextView avatar = text(profileInitials(profile == null ? "" : profile.name), textSp, Color.WHITE, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(fallbackColor, Color.TRANSPARENT, size / 2));
        avatar.setContentDescription(profile == null ? "Profile initials" : profile.name + " initials");
        return avatar;
    }

    private int avatarWidthDp(Profile profile, int heightDp) {
        if (profile == null || !profile.hasAvatar()) {
            return heightDp;
        }
        return Math.round(heightDp * clamp(profile.avatarAspectRatio, 0.75f, 1.65f, 1.0f));
    }

    private Bitmap loadBitmap(String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            return null;
        }

        Uri uri;
        try {
            uri = Uri.parse(uriString);
        } catch (Exception exception) {
            return null;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (IOException | RuntimeException exception) {
            return null;
        }

        int sampleSize = 1;
        int largestSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (largestSide / sampleSize > 1600) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sampleSize;
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, decode);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private Bitmap createCroppedAvatarBitmap(Bitmap source, int width, int height, Profile profile) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        RectF frame = new RectF(0, 0, width, height);
        Path clip = new Path();
        float radius = Math.min(width, height) / 2.0f;
        clip.addRoundRect(frame, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        drawCroppedBitmap(
                canvas,
                source,
                frame,
                profile.avatarZoom,
                profile.avatarOffsetX,
                profile.avatarOffsetY
        );
        canvas.restore();
        return output;
    }

    private void drawCroppedBitmap(
            Canvas canvas,
            Bitmap source,
            RectF frame,
            float zoom,
            float offsetX,
            float offsetY
    ) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return;
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        float safeZoom = clamp(zoom, 1.0f, 3.0f, 1.0f);
        float scale = Math.max(frame.width() / source.getWidth(), frame.height() / source.getHeight()) * safeZoom;
        float destinationWidth = source.getWidth() * scale;
        float destinationHeight = source.getHeight() * scale;
        float panX = Math.max(0.0f, (destinationWidth - frame.width()) / 2.0f);
        float panY = Math.max(0.0f, (destinationHeight - frame.height()) / 2.0f);
        float left = frame.centerX() - destinationWidth / 2.0f + clamp(offsetX, -1.0f, 1.0f, 0.0f) * panX;
        float top = frame.centerY() - destinationHeight / 2.0f + clamp(offsetY, -1.0f, 1.0f, 0.0f) * panY;
        RectF destination = new RectF(left, top, left + destinationWidth, top + destinationHeight);
        canvas.drawColor(COLOR_CARD);
        canvas.drawBitmap(source, null, destination, paint);
    }

    private float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0 && min > 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ProfilePhotoEditorView extends View {
        private final Bitmap bitmap;
        private float zoom = 1.0f;
        private float offsetX = 0.0f;
        private float offsetY = 0.0f;
        private float aspectRatio = 1.0f;
        private float lastX;
        private float lastY;
        private float pinchStartDistance;
        private float pinchStartZoom;
        private Runnable onFrameChanged;

        ProfilePhotoEditorView(Bitmap bitmap) {
            super(MainActivity.this);
            this.bitmap = bitmap;
            setBackground(rounded(COLOR_CARD, COLOR_BORDER, dp(22)));
        }

        void setOnFrameChangedListener(Runnable listener) {
            onFrameChanged = listener;
        }

        void setFrame(float zoom, float offsetX, float offsetY, float aspectRatio) {
            this.zoom = clamp(zoom, 1.0f, 3.0f, 1.0f);
            this.offsetX = clamp(offsetX, -1.0f, 1.0f, 0.0f);
            this.offsetY = clamp(offsetY, -1.0f, 1.0f, 0.0f);
            this.aspectRatio = clamp(aspectRatio, 0.75f, 1.65f, 1.0f);
            invalidate();
            notifyFrameChanged();
        }

        void setZoom(float value) {
            zoom = clamp(value, 1.0f, 3.0f, 1.0f);
            invalidate();
            notifyFrameChanged();
        }

        void setAspectRatio(float value) {
            aspectRatio = clamp(value, 0.75f, 1.65f, 1.0f);
            invalidate();
            notifyFrameChanged();
        }

        void nudge(float deltaX, float deltaY) {
            offsetX = clamp(offsetX + deltaX, -1.0f, 1.0f, 0.0f);
            offsetY = clamp(offsetY + deltaY, -1.0f, 1.0f, 0.0f);
            invalidate();
            notifyFrameChanged();
        }

        void center() {
            offsetX = 0.0f;
            offsetY = 0.0f;
            invalidate();
            notifyFrameChanged();
        }

        float getZoom() {
            return zoom;
        }

        float getOffsetX() {
            return offsetX;
        }

        float getOffsetY() {
            return offsetY;
        }

        float getAspectRatio() {
            return aspectRatio;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF frame = editorFrame();
            float radius = Math.min(frame.width(), frame.height()) / 2.0f;

            Path clip = new Path();
            clip.addRoundRect(frame, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            drawCroppedBitmap(canvas, bitmap, frame, zoom, offsetX, offsetY);
            canvas.restore();

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(dp(2));
            border.setColor(COLOR_GREEN);
            canvas.drawRoundRect(frame, radius, radius, border);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX();
                lastY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
                pinchStartDistance = pointerDistance(event);
                pinchStartZoom = zoom;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (event.getPointerCount() >= 2) {
                    float distance = pointerDistance(event);
                    if (pinchStartDistance > 0.0f) {
                        setZoom(pinchStartZoom * distance / pinchStartDistance);
                    }
                } else {
                    float x = event.getX();
                    float y = event.getY();
                    panByPixels(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return true;
        }

        private RectF editorFrame() {
            float padding = dp(14);
            float availableWidth = Math.max(1.0f, getWidth() - padding * 2.0f);
            float availableHeight = Math.max(1.0f, getHeight() - padding * 2.0f);
            float frameWidth = availableWidth;
            float frameHeight = frameWidth / aspectRatio;
            if (frameHeight > availableHeight) {
                frameHeight = availableHeight;
                frameWidth = frameHeight * aspectRatio;
            }
            float left = (getWidth() - frameWidth) / 2.0f;
            float top = (getHeight() - frameHeight) / 2.0f;
            return new RectF(left, top, left + frameWidth, top + frameHeight);
        }

        private void panByPixels(float deltaX, float deltaY) {
            RectF frame = editorFrame();
            float scale = Math.max(frame.width() / bitmap.getWidth(), frame.height() / bitmap.getHeight()) * zoom;
            float destinationWidth = bitmap.getWidth() * scale;
            float destinationHeight = bitmap.getHeight() * scale;
            float panX = Math.max(1.0f, (destinationWidth - frame.width()) / 2.0f);
            float panY = Math.max(1.0f, (destinationHeight - frame.height()) / 2.0f);
            offsetX = clamp(offsetX + deltaX / panX, -1.0f, 1.0f, 0.0f);
            offsetY = clamp(offsetY + deltaY / panY, -1.0f, 1.0f, 0.0f);
            invalidate();
            notifyFrameChanged();
        }

        private float pointerDistance(MotionEvent event) {
            float deltaX = event.getX(0) - event.getX(1);
            float deltaY = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        }

        private void notifyFrameChanged() {
            if (onFrameChanged != null) {
                onFrameChanged.run();
            }
        }
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
