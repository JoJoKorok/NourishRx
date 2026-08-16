package com.jojokorok.nourishrx;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.jojokorok.nourishrx.nutrition.NutritionTrackingFlow;
import com.jojokorok.nourishrx.nutrition.OpenFoodFactsFlow;
import com.jojokorok.nourishrx.premium.PremiumManager;
import com.jojokorok.nourishrx.profiles.ProfileManagementFlow;
import com.jojokorok.nourishrx.profiles.ProfilePhotoFlow;
import com.jojokorok.nourishrx.reminders.ReminderAlertsFlow;
import com.jojokorok.nourishrx.reminders.ReminderScheduler;
import com.jojokorok.nourishrx.ui.AppShellFlow;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.time.LocalDate;
import java.time.ZoneId;
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

    private final ZoneId zoneId = ZoneId.systemDefault();

    private MedicationStore store;
    private PremiumManager premiumManager;
    private NourishUi ui;
    private AppShellFlow appShellFlow;
    private AboutPremiumFlow aboutPremiumFlow;
    private BarcodeLookupFlow barcodeLookupFlow;
    private MedicationEditorFlow medicationEditorFlow;
    private MedicationManagementFlow medicationManagementFlow;
    private MedicationScreens medicationScreens;
    private MedicationTodayFlow medicationTodayFlow;
    private NutritionFoodEditorFlow nutritionFoodEditorFlow;
    private NutritionMealFlow nutritionMealFlow;
    private NutritionScreens nutritionScreens;
    private NutritionTrackingFlow nutritionTrackingFlow;
    private OpenFoodFactsFlow openFoodFactsFlow;
    private ProfileManagementFlow profileManagementFlow;
    private ProfilePhotoFlow profilePhotoFlow;
    private ReminderAlertsFlow reminderAlertsFlow;
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
        nutritionTrackingFlow = new NutritionTrackingFlow(this, store, ui, zoneId, nutritionTrackingCallbacks());
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
        reminderAlertsFlow = new ReminderAlertsFlow(this, REQUEST_NOTIFICATIONS, this::renderShell);
        appShellFlow = new AppShellFlow(this, store, ui, zoneId, appShellCallbacks());
        currentProfileId = loadSelectedProfileId();
        currentMode = loadAppMode();
        currentTab = defaultTabForMode(currentMode);
        applyReminderProfileIntent(getIntent());
        reminderAlertsFlow.initialize();
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
            reminderAlertsFlow.refreshSchedules();
            renderShell();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            reminderAlertsFlow.handleNotificationPermissionResult(
                    grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
            );
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
        content = appShellFlow.render();
        renderCurrentTab();
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

    private AppShellFlow.Callbacks appShellCallbacks() {
        return new AppShellFlow.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public String currentMode() {
                return MainActivity.this.currentMode;
            }

            @Override
            public String currentTab() {
                return MainActivity.this.currentTab;
            }

            @Override
            public Profile selectedProfile() {
                return MainActivity.this.selectedProfile();
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
            public int todayDoseCount() {
                return medicationTodayFlow.doseCountFor(LocalDate.now(zoneId));
            }

            @Override
            public String alertsLabel() {
                return reminderAlertsFlow.alertsLabel();
            }

            @Override
            public int alertColor() {
                return reminderAlertsFlow.alertColor();
            }

            @Override
            public void showQuickAdd(boolean nutritionMode) {
                if (nutritionMode) {
                    nutritionMealFlow.showLogFoodDialog("");
                } else {
                    medicationEditorFlow.show(null);
                }
            }

            @Override
            public void showProfiles() {
                profileManagementFlow.showProfilesDialog();
            }

            @Override
            public void selectAbout() {
                currentTab = TAB_ABOUT;
                renderShell();
            }

            @Override
            public void selectMode(String mode) {
                setAppMode(mode);
            }

            @Override
            public void selectTab(String tab) {
                currentTab = tab;
                renderShell();
            }

            @Override
            public void handleAlertsTap() {
                reminderAlertsFlow.handleAlertsTap();
            }
        };
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

    private NutritionTrackingFlow.Callbacks nutritionTrackingCallbacks() {
        return new NutritionTrackingFlow.Callbacks() {
            @Override
            public long currentProfileId() {
                return MainActivity.this.currentProfileId;
            }

            @Override
            public void showLogFoodDialog(String mealName) {
                nutritionMealFlow.showLogFoodDialog(mealName);
            }

            @Override
            public void onTrackingChanged() {
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
                return nutritionTrackingFlow.defaultMealsCard(mealDefaults);
            }

            @Override
            public View waterCard(int waterOunces, long startMillis, long endMillis) {
                return nutritionTrackingFlow.waterCard(waterOunces, startMillis, endMillis);
            }

            @Override
            public View weightCard(List<WeightEntry> weights) {
                return nutritionTrackingFlow.weightCard(weights);
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

    private String formatGrams(float grams) {
        return formatFloatInput(grams) + "g";
    }

    private String formatMg(float value) {
        return formatFloatInput(value) + "mg";
    }

    private String formatMcg(float value) {
        return formatFloatInput(value) + "mcg";
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

    private TextView text(String value, int sp, int color, int style) {
        return ui.text(value, sp, color, style);
    }

    private TextView fieldLabel(String value) {
        return ui.fieldLabel(value);
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

    private int dp(float value) {
        return ui.dp(value);
    }

}
