package com.jojokorok.nourishrx.nutrition

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import com.jojokorok.nourishrx.data.MealFoodLog
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.data.NutritionFood
import com.jojokorok.nourishrx.data.NutritionTotals
import com.jojokorok.nourishrx.data.SavedMeal
import com.jojokorok.nourishrx.data.WeightEntry
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishShapes
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class NutritionScreens(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val zoneId: ZoneId,
    private val callbacks: Callbacks
) {
    @JvmSuppressWildcards
    interface Callbacks {
        fun currentProfileId(): Long
        fun plural(count: Long, singular: String, plural: String): String
        fun distinctMealCount(logs: List<MealFoodLog>): Int
        fun mealNamesForLogs(logs: List<MealFoodLog>): List<String>
        fun logsForMeal(logs: List<MealFoodLog>, mealName: String): List<MealFoodLog>
        fun sectionTitle(title: String, subtitle: String): View
        fun emptyState(message: String, action: String, listener: View.OnClickListener)
        fun mealTotalsCard(mealName: String, logs: List<MealFoodLog>): View
        fun waterCard(waterOunces: Int, startMillis: Long, endMillis: Long): View
        fun weightCard(weights: List<WeightEntry>): View
        fun mealLogCard(log: MealFoodLog): View
        fun savedMealCard(savedMeal: SavedMeal): View
        fun foodCard(food: NutritionFood): View
        fun showLogFoodDialog(mealName: String)
        fun showSavedMealDialog()
        fun showFoodDialog()
        fun showOpenFoodFactsSearchDialog()
        fun showBarcodeEntryPoint()
        fun showMealDefaultsDialog()
        fun showWaterDialog()
        fun showWeightDialog()
        fun onNutritionChanged()
    }

    private val shortDateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

    fun renderToday(content: LinearLayout) {
        val today = LocalDate.now(zoneId)
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val logs = store.getMealFoodLogs(callbacks.currentProfileId(), start, end)
        val totals = NutritionTotals.fromLogs(logs)
        val waterOunces = store.getWaterOunces(callbacks.currentProfileId(), start, end)
        val weights = store.getWeightEntries(callbacks.currentProfileId(), 1)
        val mealCount = callbacks.distinctMealCount(logs)

        content.addView(dashboardHeader(logs.size, mealCount))
        content.addView(dailyOverviewCard(totals))
        content.addView(quickLogCard(store.getMealDefaults(callbacks.currentProfileId())))
        content.addView(sectionHeader("Daily tracking", "Water and weight at a glance"))
        content.addView(trackingCard(waterOunces, weights))
        content.addView(
            sectionHeader(
                "Meal activity",
                if (logs.isEmpty()) "Nothing logged yet" else callbacks.plural(logs.size.toLong(), "food entry", "food entries")
            )
        )

        if (logs.isEmpty()) {
            content.addView(emptyMealState())
            return
        }

        logs.forEach { content.addView(callbacks.mealLogCard(it)) }
    }

    fun renderMeals(content: LinearLayout) {
        val today = LocalDate.now(zoneId)
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val logs = store.getMealFoodLogs(callbacks.currentProfileId(), start, end)

        val mealNames = callbacks.mealNamesForLogs(logs)
        content.addView(
            screenHeader(
                "Meals today",
                if (logs.isEmpty()) "Build today's meal log from your saved foods" else
                    callbacks.plural(mealNames.size.toLong(), "meal", "meals") + " with " +
                        callbacks.plural(logs.size.toLong(), "food entry", "food entries"),
                "Log food"
            ) { callbacks.showLogFoodDialog("") }
        )
        content.addView(quickLogCard(store.getMealDefaults(callbacks.currentProfileId())))

        if (logs.isEmpty()) {
            content.addView(
                emptyCollectionState(
                    "No meals logged yet",
                    "Choose a saved food and add it to breakfast, lunch, dinner, or any meal you name.",
                    "Log the first food"
                ) { callbacks.showLogFoodDialog("") }
            )
            return
        }

        content.addView(sectionHeader("Logged meals", "Nutrition totals are combined within each meal"))
        mealNames.forEach { mealName ->
            val mealLogs = callbacks.logsForMeal(logs, mealName)
            content.addView(callbacks.mealTotalsCard(mealName, mealLogs))
            mealLogs.forEach { content.addView(callbacks.mealLogCard(it)) }
        }
    }

    fun renderSavedMeals(content: LinearLayout) {
        val savedMeals = store.getSavedMeals(callbacks.currentProfileId())
        val foods = store.getNutritionFoods(callbacks.currentProfileId())

        content.addView(
            screenHeader(
                "Saved meals",
                if (savedMeals.isEmpty()) "Combine foods once and reuse them whenever you log" else
                    callbacks.plural(savedMeals.size.toLong(), "reusable meal", "reusable meals"),
                "Create meal"
            ) { callbacks.showSavedMealDialog() }
        )

        if (foods.isEmpty()) {
            content.addView(
                emptyCollectionState(
                    "Add a food first",
                    "Saved meals are assembled from foods in your personal food library.",
                    "Add food"
                ) { callbacks.showFoodDialog() }
            )
            return
        }

        if (savedMeals.isEmpty()) {
            content.addView(
                emptyCollectionState(
                    "No saved meals yet",
                    "Group foods you eat together so the whole meal can be logged in one step.",
                    "Create saved meal"
                ) { callbacks.showSavedMealDialog() }
            )
            return
        }

        content.addView(sectionHeader("Your meal library", "Log, edit, or remove a reusable meal"))
        savedMeals.forEach { content.addView(callbacks.savedMealCard(it)) }
    }

    fun renderFoods(content: LinearLayout) {
        val foods = store.getNutritionFoods(callbacks.currentProfileId())

        content.addView(
            callbacks.sectionTitle(
                "Foods",
                if (foods.isEmpty()) "No saved foods yet" else callbacks.plural(foods.size.toLong(), "saved food", "saved foods")
            )
        )
        val actions = actionRow().apply { setPadding(0, ui.dp(NourishSpacing.XXS), 0, 0) }

        actions.addView(
            ui.button("+ Manual", NourishColors.GREEN, NourishColors.GREEN_SOFT).apply {
                setOnClickListener { callbacks.showFoodDialog() }
            },
            weightedActionParams()
        )
        actions.addView(
            ui.button("Find online", NourishColors.BLUE, NourishColors.BLUE_SOFT).apply {
                setOnClickListener { callbacks.showOpenFoodFactsSearchDialog() }
            },
            weightedActionParams()
        )
        actions.addView(
            ui.button("Barcode", NourishColors.GOLD, NourishColors.GOLD_SOFT).apply {
                setOnClickListener { callbacks.showBarcodeEntryPoint() }
            },
            weightedActionParams(last = true)
        )
        content.addView(actions)

        if (foods.isEmpty()) {
            callbacks.emptyState(
                "Save food items manually or import inspectable options from OpenFoodFacts.",
                "Search foods"
            ) { callbacks.showOpenFoodFactsSearchDialog() }
            return
        }

        foods.forEach { content.addView(callbacks.foodCard(it)) }
    }

    fun renderBody(content: LinearLayout) {
        val today = LocalDate.now(zoneId)
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val waterOunces = store.getWaterOunces(callbacks.currentProfileId(), start, end)
        val weights = store.getWeightEntries(callbacks.currentProfileId(), 10)

        content.addView(callbacks.sectionTitle("Body", "Track water intake and weight"))
        content.addView(callbacks.waterCard(waterOunces, start, end))
        content.addView(callbacks.weightCard(weights))
    }

    private fun dashboardHeader(foodCount: Int, mealCount: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ui.dp(NourishSpacing.XS), 0, ui.dp(NourishSpacing.XXS))
        addView(ui.displayText("Today's nutrition", NourishTypography.TITLE, NourishColors.INK))
        addView(
            ui.text(
                when {
                    foodCount == 0 -> "Start with a food, meal, or quick water update"
                    mealCount == 0 -> callbacks.plural(foodCount.toLong(), "food logged", "foods logged")
                    else -> callbacks.plural(foodCount.toLong(), "food", "foods") + " across " +
                        callbacks.plural(mealCount.toLong(), "meal", "meals")
                },
                NourishTypography.LABEL,
                NourishColors.MUTED,
                Typeface.NORMAL
            )
        )
    }

    private fun dailyOverviewCard(totals: NutritionTotals): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()

        val heading = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val calories = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text("DAILY INTAKE", NourishTypography.CAPTION, NourishColors.GREEN_DARK, Typeface.BOLD))
            addView(ui.displayText(totals.calories.toString(), 32, NourishColors.INK))
        }
        heading.addView(calories, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(
            ui.text("calories", NourishTypography.LABEL, NourishColors.MUTED, Typeface.NORMAL).apply {
                gravity = Gravity.END
                setPadding(0, 0, 0, ui.dp(NourishSpacing.XXS))
            }
        )
        addView(heading)

        addView(divider(NourishSpacing.SM))

        val macros = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(macroMetric(formatGrams(totals.proteinGrams), "Protein", NourishColors.GREEN), equalParams())
            addView(verticalDivider())
            addView(macroMetric(formatGrams(totals.totalCarbsGrams), "Carbs", NourishColors.BLUE), equalParams())
            addView(verticalDivider())
            addView(macroMetric(formatGrams(totals.totalFatGrams), "Fat", NourishColors.GOLD), equalParams())
        }
        addView(macros)

        val actions = actionRow()
        actions.addView(
            ui.button("Log food", NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                setOnClickListener { callbacks.showLogFoodDialog("") }
            },
            weightedActionParams()
        )
        actions.addView(
            ui.button("Nutrition facts", NourishColors.INK_SECONDARY, Color.TRANSPARENT).apply {
                setOnClickListener { showDailyNutritionFacts(totals) }
            },
            weightedActionParams(last = true)
        )
        addView(actions)
    }

    private fun macroMetric(value: String, label: String, color: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(ui.displayText(value, NourishTypography.BODY_LARGE, color).apply { gravity = Gravity.CENTER })
        addView(ui.text(label, NourishTypography.CAPTION, NourishColors.MUTED, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
    }

    private fun quickLogCard(mealDefaults: List<String>): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
        val heading = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                ui.displayText("Quick log", NourishTypography.BODY_LARGE, NourishColors.INK),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                ui.button("Edit defaults", NourishColors.BLUE, Color.TRANSPARENT).apply {
                    setSingleLine(true)
                    setOnClickListener { callbacks.showMealDefaultsDialog() }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(40))
            )
        }
        addView(heading)
        addView(
            ui.text(
                "Choose a usual meal or log without a preset.",
                NourishTypography.LABEL,
                NourishColors.MUTED,
                Typeface.NORMAL
            )
        )

        mealDefaults.forEach { mealName ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(NourishSpacing.SM), 0, 0)
                addView(
                    ui.text(mealName, NourishTypography.BODY, NourishColors.INK, Typeface.BOLD),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    ui.button("Log", NourishColors.GREEN, Color.TRANSPARENT).apply {
                        setOnClickListener { callbacks.showLogFoodDialog(mealName) }
                    },
                    LinearLayout.LayoutParams(ui.dp(80), ui.dp(40))
                )
            }
            addView(row)
        }
    }

    private fun screenHeader(
        title: String,
        subtitle: String,
        actionLabel: String,
        action: () -> Unit
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, ui.dp(NourishSpacing.XS), 0, ui.dp(NourishSpacing.XXS))

        val copy = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.displayText(title, NourishTypography.TITLE, NourishColors.INK))
            addView(ui.text(subtitle, NourishTypography.LABEL, NourishColors.MUTED, Typeface.NORMAL))
        }
        addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            ui.button(actionLabel, NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                setSingleLine(true)
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(44)).apply {
                leftMargin = ui.dp(NourishSpacing.SM)
            }
        )
    }

    private fun emptyCollectionState(
        title: String,
        message: String,
        actionLabel: String,
        action: () -> Unit
    ): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK).apply { gravity = Gravity.CENTER })
        addView(
            ui.text(message, NourishTypography.LABEL, NourishColors.MUTED, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ui.dp(NourishSpacing.XS)
            }
        )
        addView(
            ui.button(actionLabel, NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)).apply {
                topMargin = ui.dp(NourishSpacing.MD)
            }
        )
    }

    private fun trackingCard(waterOunces: Int, weights: List<WeightEntry>): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
        addView(waterTrackingRow(waterOunces))
        addView(divider(NourishSpacing.MD))
        addView(weightTrackingRow(weights.firstOrNull()))
    }

    private fun waterTrackingRow(waterOunces: Int): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL

        val heading = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                trackingLabel("Water", "$waterOunces oz today"),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(ui.statusBadge(if (waterOunces >= 64) "Hydrated" else "Track"))
        }
        addView(heading)

        val actions = actionRow()
        actions.addView(waterButton("+8 oz", 8), weightedActionParams())
        actions.addView(waterButton("+16 oz", 16), weightedActionParams())
        actions.addView(
            ui.button("Custom", NourishColors.BLUE, Color.TRANSPARENT).apply {
                setSingleLine(true)
                setOnClickListener { callbacks.showWaterDialog() }
            },
            weightedActionParams(last = true)
        )
        addView(actions)
    }

    private fun weightTrackingRow(latest: WeightEntry?): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            trackingLabel(
                "Weight",
                latest?.let { "${formatNumber(it.pounds)} lb - ${formatShortDateTime(it.loggedAt)}" }
                    ?: "No weight logged yet"
            ),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(
            ui.button("Log weight", NourishColors.GREEN, Color.TRANSPARENT).apply {
                setSingleLine(true)
                setOnClickListener { callbacks.showWeightDialog() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(42))
        )
    }

    private fun trackingLabel(title: String, detail: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK))
        addView(ui.text(detail, NourishTypography.LABEL, NourishColors.MUTED, Typeface.NORMAL))
    }

    private fun waterButton(label: String, ounces: Int): Button =
        ui.button(label, NourishColors.BLUE, NourishColors.BLUE_SOFT).apply {
            setSingleLine(true)
            setOnClickListener {
                store.addWater(callbacks.currentProfileId(), ounces)
                callbacks.onNutritionChanged()
            }
        }

    private fun sectionHeader(title: String, subtitle: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ui.dp(NourishSpacing.LG), 0, 0)
        addView(ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK))
        addView(ui.text(subtitle, NourishTypography.LABEL, NourishColors.MUTED, Typeface.NORMAL))
    }

    private fun emptyMealState(): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ui.displayText("Your day is ready", NourishTypography.BODY_LARGE, NourishColors.INK).apply { gravity = Gravity.CENTER })
        addView(
            ui.text(
                "Foods you log today will appear here with their meal and nutrition.",
                NourishTypography.LABEL,
                NourishColors.MUTED,
                Typeface.NORMAL
            ).apply { gravity = Gravity.CENTER }
        )
        addView(
            ui.button("Log the first food", NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                setOnClickListener { callbacks.showLogFoodDialog("") }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)).apply {
                topMargin = ui.dp(NourishSpacing.MD)
            }
        )
    }

    private fun showDailyNutritionFacts(totals: NutritionTotals) {
        val facts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.LG)
            )
            addView(ui.displayText("${totals.calories} calories", NourishTypography.TITLE, NourishColors.INK))
            addView(ui.text("Combined from today's food logs", NourishTypography.LABEL, NourishColors.MUTED, Typeface.NORMAL))
            addView(divider(NourishSpacing.MD))
            addView(factGroupTitle("Macronutrients"))
            addView(factRow("Total fat", formatGrams(totals.totalFatGrams)))
            addView(factRow("Saturated fat", formatGrams(totals.saturatedFatGrams)))
            addView(factRow("Trans fat", formatGrams(totals.transFatGrams)))
            addView(factRow("Cholesterol", formatMilligrams(totals.cholesterolMg)))
            addView(factRow("Sodium", formatMilligrams(totals.sodiumMg)))
            addView(factRow("Total carbs", formatGrams(totals.totalCarbsGrams)))
            addView(factRow("Fiber", formatGrams(totals.fiberGrams)))
            addView(factRow("Total sugars", formatGrams(totals.totalSugarsGrams)))
            addView(factRow("Added sugars", formatGrams(totals.addedSugarsGrams)))
            addView(factRow("Protein", formatGrams(totals.proteinGrams)))
            addView(factGroupTitle("Vitamins and minerals"))
            addView(factRow("Vitamin D", formatMicrograms(totals.vitaminDMcg)))
            addView(factRow("Calcium", formatMilligrams(totals.calciumMg)))
            addView(factRow("Iron", formatMilligrams(totals.ironMg)))
            addView(factRow("Potassium", formatMilligrams(totals.potassiumMg)))
        }
        val scroll = ScrollView(activity).apply { addView(facts) }
        AlertDialog.Builder(activity)
            .setTitle("Today's nutrition")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun factGroupTitle(value: String): View =
        ui.text(value, NourishTypography.LABEL, NourishColors.INK_SECONDARY, Typeface.BOLD).apply {
            setPadding(0, ui.dp(NourishSpacing.MD), 0, ui.dp(NourishSpacing.XXS))
        }

    private fun factRow(label: String, value: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, ui.dp(NourishSpacing.XS), 0, ui.dp(NourishSpacing.XS))
        addView(
            ui.text(label, NourishTypography.BODY, NourishColors.INK, Typeface.NORMAL),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(ui.text(value, NourishTypography.BODY, NourishColors.INK_SECONDARY, Typeface.BOLD))
    }

    private fun divider(topMargin: Int): View = View(activity).apply {
        setBackgroundColor(NourishColors.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
            this.topMargin = ui.dp(topMargin)
            bottomMargin = ui.dp(topMargin)
        }
    }

    private fun verticalDivider(): View = View(activity).apply {
        setBackgroundColor(NourishColors.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(ui.dp(1), ui.dp(42)).apply {
            leftMargin = ui.dp(NourishSpacing.XS)
            rightMargin = ui.dp(NourishSpacing.XS)
        }
    }

    private fun actionRow(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ui.dp(NourishSpacing.SM), 0, 0)
    }

    private fun equalParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun weightedActionParams(last: Boolean = false): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply {
            if (!last) rightMargin = ui.dp(NourishSpacing.XS)
        }

    private fun formatShortDateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(shortDateTimeFormatter)

    private fun formatGrams(value: Float): String = "${formatNumber(value)}g"
    private fun formatMilligrams(value: Float): String = "${formatNumber(value)}mg"
    private fun formatMicrograms(value: Float): String = "${formatNumber(value)}mcg"

    private fun formatNumber(value: Float): String =
        if (abs(value - value.roundToInt()) < 0.05f) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
}
