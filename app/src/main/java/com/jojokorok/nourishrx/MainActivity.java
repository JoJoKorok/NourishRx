package com.jojokorok.nourishrx;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.MealFoodLog;
import com.jojokorok.nourishrx.data.Medication;
import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.NutritionFood;
import com.jojokorok.nourishrx.data.NutritionTotals;
import com.jojokorok.nourishrx.data.Profile;
import com.jojokorok.nourishrx.data.SavedMeal;
import com.jojokorok.nourishrx.data.WeightEntry;
import com.jojokorok.nourishrx.about.AboutPremiumFlow;
import com.jojokorok.nourishrx.barcode.BarcodeLookupFlow;
import com.jojokorok.nourishrx.medications.MedicationEditorFlow;
import com.jojokorok.nourishrx.medications.MedicationManagementFlow;
import com.jojokorok.nourishrx.medications.MedicationScreens;
import com.jojokorok.nourishrx.medications.MedicationTodayFlow;
import com.jojokorok.nourishrx.nutrition.NutritionFoodEditorFlow;
import com.jojokorok.nourishrx.nutrition.NutritionMealFlow;
import com.jojokorok.nourishrx.nutrition.NutritionScreens;
import com.jojokorok.nourishrx.nutrition.OpenFoodFactsFlow;
import com.jojokorok.nourishrx.premium.PremiumFeature;
import com.jojokorok.nourishrx.premium.PremiumManager;
import com.jojokorok.nourishrx.profiles.ProfileManagementFlow;
import com.jojokorok.nourishrx.profiles.ProfilePhotoFlow;
import com.jojokorok.nourishrx.reminders.ReminderScheduler;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 42;
    private static final int REQUEST_PROFILE_PHOTO = 43;
    private static final int REQUEST_BARCODE_CAMERA = 44;
    private static final int REQUEST_BARCODE_SCAN = 45;
    private static final String PREF_SELECTED_PROFILE_ID = "selected_profile_id";
    private static final String PREF_APP_MODE = "app_mode";
    private static final String MODE_MEDICATION = "medication";
    private static final String MODE_NUTRITION = "nutrition";
    private static final String TAB_ABOUT = "about";

    private static final int COLOR_SURFACE = NourishColors.SURFACE;
    private static final int COLOR_CARD = NourishColors.CARD;
    private static final int COLOR_INK = NourishColors.INK;
    private static final int COLOR_MUTED = NourishColors.MUTED;
    private static final int COLOR_GREEN = NourishColors.GREEN;
    private static final int COLOR_GREEN_SOFT = NourishColors.GREEN_SOFT;
    private static final int COLOR_CORAL = NourishColors.CORAL;
    private static final int COLOR_CORAL_SOFT = NourishColors.CORAL_SOFT;
    private static final int COLOR_BLUE = NourishColors.BLUE;
    private static final int COLOR_BLUE_SOFT = NourishColors.BLUE_SOFT;
    private static final int COLOR_GOLD = NourishColors.GOLD;
    private static final int COLOR_GOLD_SOFT = NourishColors.GOLD_SOFT;
    private static final int COLOR_BORDER = NourishColors.BORDER;
    private static final int COLOR_TAB_TRACK = NourishColors.TAB_TRACK;

    private final ZoneId zoneId = ZoneId.systemDefault();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault());
    private final DateTimeFormatter shortDateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault());

    private MedicationStore store;
    private PremiumManager premiumManager;
    private NourishUi ui;
    private AboutPremiumFlow aboutPremiumFlow;
    private BarcodeLookupFlow barcodeLookupFlow;
    private MedicationEditorFlow medicationEditorFlow;
    private MedicationManagementFlow medicationManagementFlow;
    private MedicationScreens medicationScreens;
    private MedicationTodayFlow medicationTodayFlow;
    private NutritionFoodEditorFlow nutritionFoodEditorFlow;
    private NutritionMealFlow nutritionMealFlow;
    private NutritionScreens nutritionScreens;
    private OpenFoodFactsFlow openFoodFactsFlow;
    private ProfileManagementFlow profileManagementFlow;
    private ProfilePhotoFlow profilePhotoFlow;
    private LinearLayout root;
    private LinearLayout content;
    private String currentTab = "today";
    private String currentMode = MODE_MEDICATION;
    private long currentProfileId;
    private long pendingPhotoProfileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new NourishUi(this);
        store = new MedicationStore(this);
        premiumManager = new PremiumManager(this);
        aboutPremiumFlow = new AboutPremiumFlow(this, ui, premiumManager);
        nutritionFoodEditorFlow = new NutritionFoodEditorFlow(this, store, ui, nutritionFoodEditorCallbacks());
        nutritionMealFlow = new NutritionMealFlow(this, store, ui, zoneId, nutritionMealCallbacks());
        openFoodFactsFlow = new OpenFoodFactsFlow(this, store, ui, openFoodFactsCallbacks());
        barcodeLookupFlow = new BarcodeLookupFlow(
                this,
                ui,
                premiumManager,
                () -> currentProfileId,
                openFoodFactsFlow::renderInspection,
                aboutPremiumFlow::showPremiumOverviewDialog,
                REQUEST_BARCODE_CAMERA,
                REQUEST_BARCODE_SCAN
        );
        medicationEditorFlow = new MedicationEditorFlow(this, store, ui, medicationEditorCallbacks());
        medicationManagementFlow = new MedicationManagementFlow(this, store, ui, medicationManagementCallbacks());
        medicationScreens = new MedicationScreens(this, store, ui, medicationCallbacks());
        medicationTodayFlow = new MedicationTodayFlow(this, store, ui, zoneId, medicationTodayCallbacks());
        nutritionScreens = new NutritionScreens(this, store, ui, zoneId, nutritionCallbacks());
        profilePhotoFlow = new ProfilePhotoFlow(this, store, ui, REQUEST_PROFILE_PHOTO, photoCallbacks());
        profileManagementFlow = new ProfileManagementFlow(this, store, ui, profileCallbacks());
        currentProfileId = loadSelectedProfileId();
        currentMode = loadAppMode();
        currentTab = defaultTabForMode(currentMode);
        applyReminderProfileIntent(getIntent());
        ReminderScheduler.ensureNotificationChannel(this);
        ReminderScheduler.scheduleAll(this);
        renderShell();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (store != null && applyReminderProfileIntent(intent)) {
            renderShell();
        }
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
        } else if (requestCode == REQUEST_BARCODE_CAMERA) {
            barcodeLookupFlow.handleCameraPermissionResult(
                    grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
            );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BARCODE_SCAN) {
            barcodeLookupFlow.handleScannerResult(resultCode, data);
            return;
        }

        if (requestCode == REQUEST_PROFILE_PHOTO) {
            profilePhotoFlow.handlePhotoPickerResult(resultCode, data);
        }
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
                nutritionMealFlow.showLogFoodDialog("");
            } else {
                medicationEditorFlow.show(null);
            }
        });
        top.addView(add, compactButtonParams());
        panel.addView(top);

        LinearLayout utilityActions = new LinearLayout(this);
        utilityActions.setOrientation(LinearLayout.HORIZONTAL);

        Button profileButton = button("Manage profiles", COLOR_BLUE, Color.WHITE);
        profileButton.setOnClickListener(view -> profileManagementFlow.showProfilesDialog());
        utilityActions.addView(profileButton, weightedActionParams());

        boolean showingAbout = TAB_ABOUT.equals(currentTab);
        Button aboutButton = button("About", showingAbout ? Color.WHITE : COLOR_BLUE, showingAbout ? COLOR_BLUE : Color.WHITE);
        aboutButton.setOnClickListener(view -> {
            currentTab = TAB_ABOUT;
            renderShell();
        });
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        utilityActions.addView(aboutButton, aboutParams);

        LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        utilityParams.topMargin = dp(12);
        panel.addView(utilityActions, utilityParams);

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
            int todayCount = medicationTodayFlow.doseCountFor(LocalDate.now(zoneId));
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
        boolean selected = currentTab.equals(tab);
        Button button = button(label, selected ? Color.WHITE : COLOR_MUTED, selected ? COLOR_GREEN : Color.TRANSPARENT);
        button.setTextSize(12);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
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
        if (TAB_ABOUT.equals(currentTab)) {
            aboutPremiumFlow.renderAbout(content);
            return;
        }

        if (MODE_NUTRITION.equals(currentMode)) {
            if ("nutrition_meals".equals(currentTab)) {
                nutritionScreens.renderMeals(content);
            } else if ("nutrition_saved".equals(currentTab)) {
                nutritionScreens.renderSavedMeals(content);
            } else if ("nutrition_foods".equals(currentTab)) {
                nutritionScreens.renderFoods(content);
            } else if ("nutrition_body".equals(currentTab)) {
                nutritionScreens.renderBody(content);
            } else {
                nutritionScreens.renderToday(content);
            }
            return;
        }

        if ("meds".equals(currentTab)) {
            medicationScreens.renderMedications(content);
        } else if ("stock".equals(currentTab)) {
            medicationScreens.renderInventory(content);
        } else {
            medicationTodayFlow.renderToday(content);
        }
    }

    private boolean requirePremium(PremiumFeature feature) {
        return aboutPremiumFlow.requirePremium(feature);
    }

    private MedicationEditorFlow.Callbacks medicationEditorCallbacks() {
        return new MedicationEditorFlow.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public void onMedicationSaved() {
                renderShell();
            }
        };
    }

    private MedicationManagementFlow.Callbacks medicationManagementCallbacks() {
        return this::renderShell;
    }

    private MedicationTodayFlow.Callbacks medicationTodayCallbacks() {
        return new MedicationTodayFlow.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public String selectedProfileName() {
                return MainActivity.this.selectedProfileName();
            }

            @Override
            public View sectionTitle(String title, String subtitle) {
                return MainActivity.this.sectionTitle(title, subtitle);
            }

            @Override
            public void emptyState(String message, String action, View.OnClickListener listener) {
                MainActivity.this.emptyState(message, action, listener);
            }

            @Override
            public void showMedicationEditor() {
                medicationEditorFlow.show(null);
            }

            @Override
            public void onDoseChanged() {
                renderShell();
            }
        };
    }

    private NutritionFoodEditorFlow.Callbacks nutritionFoodEditorCallbacks() {
        return new NutritionFoodEditorFlow.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public void onFoodChanged() {
                renderShell();
            }
        };
    }

    private NutritionMealFlow.Callbacks nutritionMealCallbacks() {
        return new NutritionMealFlow.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public void showFoodEditor() {
                nutritionFoodEditorFlow.show(null);
            }

            @Override
            public void onNutritionChanged() {
                renderShell();
            }

            @Override
            public void onSavedMealLogged() {
                currentTab = "nutrition_meals";
                renderShell();
            }
        };
    }

    private OpenFoodFactsFlow.Callbacks openFoodFactsCallbacks() {
        return new OpenFoodFactsFlow.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public void showFoodEditor(NutritionFood food) {
                nutritionFoodEditorFlow.show(food);
            }

            @Override
            public void onFoodSaved() {
                renderShell();
            }
        };
    }

    private MedicationScreens.Callbacks medicationCallbacks() {
        return new MedicationScreens.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public String selectedProfileName() {
                return MainActivity.this.selectedProfileName();
            }

            @Override
            public View sectionTitle(String title, String subtitle) {
                return MainActivity.this.sectionTitle(title, subtitle);
            }

            @Override
            public void emptyState(String message, String action, View.OnClickListener listener) {
                MainActivity.this.emptyState(message, action, listener);
            }

            @Override
            public void showMedicationDialog(Medication medication) {
                medicationEditorFlow.show(medication);
            }

            @Override
            public void toggleMedication(Medication medication) {
                medicationManagementFlow.toggleMedication(medication);
            }

            @Override
            public void confirmDelete(Medication medication) {
                medicationManagementFlow.confirmDelete(medication);
            }

            @Override
            public void adjustInventory(Medication medication, int delta) {
                medicationManagementFlow.adjustInventory(medication, delta);
            }

            @Override
            public void showInventoryDialog(Medication medication) {
                medicationManagementFlow.showInventoryDialog(medication);
            }
        };
    }

    private NutritionScreens.Callbacks nutritionCallbacks() {
        return new NutritionScreens.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public String selectedProfileName() {
                return MainActivity.this.selectedProfileName();
            }

            @Override
            public String plural(long count, String singular, String plural) {
                return MainActivity.this.plural(count, singular, plural);
            }

            @Override
            public int distinctMealCount(List<MealFoodLog> logs) {
                return MainActivity.this.distinctMealCount(logs);
            }

            @Override
            public List<String> mealNamesForLogs(List<MealFoodLog> logs) {
                return MainActivity.this.mealNamesForLogs(logs);
            }

            @Override
            public List<MealFoodLog> logsForMeal(List<MealFoodLog> logs, String mealName) {
                return MainActivity.this.logsForMeal(logs, mealName);
            }

            @Override
            public NutritionTotals totalsFromMealLogs(List<MealFoodLog> logs) {
                return MainActivity.this.totalsFromMealLogs(logs);
            }

            @Override
            public View sectionTitle(String title, String subtitle) {
                return MainActivity.this.sectionTitle(title, subtitle);
            }

            @Override
            public void emptyState(String message, String action, View.OnClickListener listener) {
                MainActivity.this.emptyState(message, action, listener);
            }

            @Override
            public View nutritionSummaryCard(int calories, float protein, float carbs, float fat) {
                return MainActivity.this.nutritionSummaryCard(calories, protein, carbs, fat);
            }

            @Override
            public View dailyNutritionFactsCard(NutritionTotals totals) {
                return MainActivity.this.dailyNutritionFactsCard(totals);
            }

            @Override
            public View mealTotalsCard(String mealName, List<MealFoodLog> logs) {
                return MainActivity.this.mealTotalsCard(mealName, logs);
            }

            @Override
            public View defaultMealsCard(List<String> mealDefaults) {
                return MainActivity.this.defaultMealsCard(mealDefaults);
            }

            @Override
            public View waterCard(int waterOunces, long startMillis, long endMillis) {
                return MainActivity.this.waterCard(waterOunces, startMillis, endMillis);
            }

            @Override
            public View weightCard(List<WeightEntry> weights) {
                return MainActivity.this.weightCard(weights);
            }

            @Override
            public View mealLogCard(MealFoodLog log) {
                return nutritionMealFlow.mealLogCard(log);
            }

            @Override
            public View savedMealCard(SavedMeal savedMeal) {
                return nutritionMealFlow.savedMealCard(savedMeal);
            }

            @Override
            public View foodCard(NutritionFood food) {
                return MainActivity.this.foodCard(food);
            }

            @Override
            public void showLogFoodDialog(String mealName) {
                nutritionMealFlow.showLogFoodDialog(mealName);
            }

            @Override
            public void showSavedMealDialog() {
                nutritionMealFlow.showSavedMealDialog(null);
            }

            @Override
            public void showFoodDialog() {
                nutritionFoodEditorFlow.show(null);
            }

            @Override
            public void showOpenFoodFactsSearchDialog() {
                openFoodFactsFlow.showSearchDialog();
            }

            @Override
            public void showBarcodeEntryPoint() {
                MainActivity.this.barcodeLookupFlow.showEntryPoint();
            }
        };
    }

    private ProfileManagementFlow.Callbacks profileCallbacks() {
        return new ProfileManagementFlow.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public void setSelectedProfileId(long profileId) {
                MainActivity.this.setSelectedProfileId(profileId);
            }

            @Override
            public void renderShell() {
                MainActivity.this.renderShell();
            }

            @Override
            public String plural(long count, String singular, String plural) {
                return MainActivity.this.plural(count, singular, plural);
            }

            @Override
            public View profileAvatar(Profile profile, int sizeDp, int fallbackColor, int textSp) {
                return MainActivity.this.profileAvatar(profile, sizeDp, fallbackColor, textSp);
            }

            @Override
            public int avatarWidthDp(Profile profile, int heightDp) {
                return MainActivity.this.avatarWidthDp(profile, heightDp);
            }

            @Override
            public void chooseProfilePhoto(Profile profile) {
                MainActivity.this.profilePhotoFlow.chooseProfilePhoto(profile);
            }

            @Override
            public void showProfilePhotoEditor(
                    Profile profile,
                    String avatarUri,
                    float zoom,
                    float offsetX,
                    float offsetY,
                    float aspectRatio
            ) {
                MainActivity.this.profilePhotoFlow.showProfilePhotoEditor(profile, avatarUri, zoom, offsetX, offsetY, aspectRatio);
            }
        };
    }

    private ProfilePhotoFlow.Callbacks photoCallbacks() {
        return new ProfilePhotoFlow.Callbacks() {
            @Override
            public long pendingPhotoProfileId() {
                return MainActivity.this.pendingPhotoProfileId;
            }

            @Override
            public void setPendingPhotoProfileId(long profileId) {
                MainActivity.this.pendingPhotoProfileId = profileId;
            }

            @Override
            public void clearPendingPhotoProfileId() {
                MainActivity.this.pendingPhotoProfileId = 0;
            }

            @Override
            public void renderShell() {
                MainActivity.this.renderShell();
            }
        };
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
        addMeal.setOnClickListener(view -> nutritionMealFlow.showLogFoodDialog(""));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        addParams.topMargin = dp(12);
        card.addView(addMeal, addParams);
        return card;
    }

    private View dailyNutritionFactsCard(NutritionTotals totals) {
        LinearLayout card = card();
        card.addView(text("Full-day nutrition", 13, COLOR_MUTED, Typeface.BOLD));
        card.addView(text(totals.calories + " calories total", 24, COLOR_INK, Typeface.BOLD));
        card.addView(text("Combined from all foods logged today", 13, COLOR_MUTED, Typeface.NORMAL));

        LinearLayout macros = new LinearLayout(this);
        macros.setOrientation(LinearLayout.HORIZONTAL);
        macros.setPadding(0, dp(12), 0, 0);
        macros.addView(summaryPill(formatGrams(totals.proteinGrams) + " protein", COLOR_GREEN, COLOR_GREEN_SOFT));
        macros.addView(summaryPill(formatGrams(totals.totalCarbsGrams) + " carbs", COLOR_BLUE, COLOR_BLUE_SOFT));
        macros.addView(summaryPill(formatGrams(totals.totalFatGrams) + " fat", COLOR_GOLD, COLOR_GOLD_SOFT));
        card.addView(macros);

        card.addView(fieldLabel("Nutrition facts"));
        card.addView(nutritionFactRow("Total fat", formatGrams(totals.totalFatGrams)));
        card.addView(nutritionFactRow("Saturated fat", formatGrams(totals.saturatedFatGrams)));
        card.addView(nutritionFactRow("Trans fat", formatGrams(totals.transFatGrams)));
        card.addView(nutritionFactRow("Cholesterol", formatMg(totals.cholesterolMg)));
        card.addView(nutritionFactRow("Sodium", formatMg(totals.sodiumMg)));
        card.addView(nutritionFactRow("Total carbs", formatGrams(totals.totalCarbsGrams)));
        card.addView(nutritionFactRow("Fiber", formatGrams(totals.fiberGrams)));
        card.addView(nutritionFactRow("Total sugars", formatGrams(totals.totalSugarsGrams)));
        card.addView(nutritionFactRow("Added sugars", formatGrams(totals.addedSugarsGrams)));
        card.addView(nutritionFactRow("Protein", formatGrams(totals.proteinGrams)));

        card.addView(fieldLabel("Vitamins and minerals"));
        card.addView(nutritionFactRow("Vitamin D", formatMcg(totals.vitaminDMcg)));
        card.addView(nutritionFactRow("Calcium", formatMg(totals.calciumMg)));
        card.addView(nutritionFactRow("Iron", formatMg(totals.ironMg)));
        card.addView(nutritionFactRow("Potassium", formatMg(totals.potassiumMg)));
        return card;
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

    private View mealTotalsCard(String mealName, List<MealFoodLog> logs) {
        NutritionTotals totals = totalsFromMealLogs(logs);

        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(mealName, 20, COLOR_INK, Typeface.BOLD));
        details.addView(text(plural(logs.size(), "food entry", "food entries") + " in this meal", 13, COLOR_MUTED, Typeface.BOLD));
        details.addView(text(nutritionTotalsLine(totals), 13, COLOR_MUTED, Typeface.NORMAL));
        top.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(totals.calories > 0 ? totals.calories + " cal" : "Meal"));
        card.addView(top);

        LinearLayout macros = new LinearLayout(this);
        macros.setOrientation(LinearLayout.HORIZONTAL);
        macros.setPadding(0, dp(12), 0, 0);
        macros.addView(summaryPill(formatGrams(totals.proteinGrams) + " protein", COLOR_GREEN, COLOR_GREEN_SOFT));
        macros.addView(summaryPill(formatGrams(totals.totalCarbsGrams) + " carbs", COLOR_BLUE, COLOR_BLUE_SOFT));
        macros.addView(summaryPill(formatGrams(totals.totalFatGrams) + " fat", COLOR_GOLD, COLOR_GOLD_SOFT));
        card.addView(macros);

        Button logHere = button("+ Log here", COLOR_GREEN, COLOR_GREEN_SOFT);
        logHere.setOnClickListener(view -> nutritionMealFlow.showLogFoodDialog(mealName));
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        logParams.topMargin = dp(12);
        card.addView(logHere, logParams);
        return card;
    }

    private NutritionTotals totalsFromMealLogs(List<MealFoodLog> logs) {
        NutritionTotals totals = new NutritionTotals();
        for (MealFoodLog log : logs) {
            totals.addFood(log.food, log.servings);
        }
        return totals;
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
            log.setOnClickListener(view -> nutritionMealFlow.showLogFoodDialog(mealName));
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
        log.setOnClickListener(view -> nutritionMealFlow.showLogFoodDialog("", food.id));
        actions.addView(log, weightedActionParams());

        Button edit = button("Edit", COLOR_BLUE, COLOR_BLUE_SOFT);
        edit.setOnClickListener(view -> nutritionFoodEditorFlow.show(food));
        actions.addView(edit, weightedActionParams());

        Button delete = button("Delete", COLOR_CORAL, COLOR_CORAL_SOFT);
        delete.setOnClickListener(view -> nutritionFoodEditorFlow.confirmDelete(food));
        actions.addView(delete, weightedActionParams());
        card.addView(actions);
        return card;
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

    private boolean applyReminderProfileIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(ReminderScheduler.EXTRA_PROFILE_ID)) {
            return false;
        }

        long profileId = intent.getLongExtra(ReminderScheduler.EXTRA_PROFILE_ID, 0);
        if (profileId <= 0 || store.getProfile(profileId) == null) {
            return false;
        }

        setSelectedProfileId(profileId);
        currentMode = MODE_MEDICATION;
        currentTab = defaultTabForMode(currentMode);
        getPreferences(MODE_PRIVATE)
                .edit()
                .putString(PREF_APP_MODE, currentMode)
                .apply();
        return true;
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

    private String formatPounds(float pounds) {
        return formatFloatInput(pounds);
    }

    private String nutritionTotalsLine(NutritionTotals totals) {
        return totals.calories + " cal - " +
                formatGrams(totals.proteinGrams) + " protein - " +
                formatGrams(totals.totalCarbsGrams) + " carbs - " +
                formatGrams(totals.totalFatGrams) + " fat";
    }

    private ArrayList<String> mealNamesForLogs(List<MealFoodLog> logs) {
        ArrayList<String> names = new ArrayList<>();
        for (MealFoodLog log : logs) {
            String name = log.mealName == null || log.mealName.trim().isEmpty()
                    ? "Meal"
                    : log.mealName.trim();
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private ArrayList<MealFoodLog> logsForMeal(List<MealFoodLog> logs, String mealName) {
        ArrayList<MealFoodLog> matchingLogs = new ArrayList<>();
        String targetName = mealName == null || mealName.trim().isEmpty() ? "Meal" : mealName.trim();
        for (MealFoodLog log : logs) {
            String logMealName = log.mealName == null || log.mealName.trim().isEmpty()
                    ? "Meal"
                    : log.mealName.trim();
            if (targetName.equals(logMealName)) {
                matchingLogs.add(log);
            }
        }
        return matchingLogs;
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

    private View infoLine(String label, String value) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, dp(12), 0, 0);

        line.addView(text(label, 13, COLOR_MUTED, Typeface.BOLD));
        TextView valueView = text(value, 15, COLOR_INK, Typeface.BOLD);
        valueView.setPadding(0, dp(2), 0, 0);
        line.addView(valueView);
        return line;
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
        return ui.card();
    }

    private LinearLayout actionRow() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        return actions;
    }

    private TextView statusBadge(String label) {
        return ui.statusBadge(label);
    }

    private TextView summaryPill(String label, int textColor, int background) {
        return ui.summaryPill(label, textColor, background);
    }

    private TextView displayText(String value, int sp, int color) {
        return ui.displayText(value, sp, color);
    }

    private TextView text(String value, int sp, int color, int style) {
        return ui.text(value, sp, color, style);
    }

    private TextView fieldLabel(String value) {
        return ui.fieldLabel(value);
    }

    private EditText field(String hint, String value, int inputType) {
        return ui.field(hint, value, inputType);
    }

    private Button button(String label, int textColor, int backgroundColor) {
        return ui.button(label, textColor, backgroundColor);
    }

    private GradientDrawable rounded(int color, int strokeColor, int radius) {
        return ui.rounded(color, strokeColor, radius);
    }

    private GradientDrawable roundedGradient(int[] colors, int radius) {
        return ui.roundedGradient(colors, radius);
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
            Bitmap source = profilePhotoFlow.loadBitmap(profile.avatarUri);
            Bitmap avatar = source == null
                    ? null
                    : profilePhotoFlow.createCroppedAvatarBitmap(source, Math.max(1, width * 2), Math.max(1, size * 2), profile);
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
        return Math.round(heightDp * profilePhotoFlow.clampedAvatarAspectRatio(profile.avatarAspectRatio));
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
        return ui.dp(value);
    }

}
