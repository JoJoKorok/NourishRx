package com.jojokorok.nourishrx.nutrition

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jojokorok.nourishrx.data.MealFoodLog
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.data.NutritionTotals
import com.jojokorok.nourishrx.data.SavedMeal
import com.jojokorok.nourishrx.data.SavedMealItem
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishShapes
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class NutritionMealCards(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val zoneId: ZoneId,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun logFood(mealName: String)
        fun editFoodLog(log: MealFoodLog)
        fun deleteFoodLog(log: MealFoodLog)
        fun logSavedMeal(savedMeal: SavedMeal)
        fun editSavedMeal(savedMeal: SavedMeal)
        fun deleteSavedMeal(savedMeal: SavedMeal)
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun mealSummaryCard(mealName: String, logs: List<MealFoodLog>): View {
        val totals = NutritionTotals.fromLogs(logs)
        return ui.card().apply {
            elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
            addView(
                cardHeading(
                    mealName,
                    plural(logs.size, "food entry", "food entries"),
                    if (totals.calories > 0) "${totals.calories} cal" else "Meal"
                )
            )
            addView(macroRow(totals))
            addView(
                ui.button("Log food here", NourishColors.GREEN, Color.TRANSPARENT).apply {
                    setOnClickListener { callbacks.logFood(mealName) }
                },
                matchWrapParams(topMargin = NourishSpacing.SM, height = 42)
            )
        }
    }

    fun mealLogCard(log: MealFoodLog): View {
        val foodName = log.food?.displayName() ?: "Saved food"
        val servingLabel = "${formatNumber(log.servings)} serving${if (abs(log.servings - 1f) < 0.05f) "" else "s"}"

        return ui.card().apply {
            elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()

            val contextRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    ui.text(log.mealName, NourishTypography.CAPTION, NourishColors.GREEN_DARK, Typeface.BOLD),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    ui.text(formatTime(log.eatenAt), NourishTypography.CAPTION, NourishColors.MUTED, Typeface.NORMAL)
                )
            }
            addView(contextRow)

            val heading = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(0, ui.dp(NourishSpacing.XXS), 0, 0)
            }
            val details = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(ui.displayText(foodName, NourishTypography.BODY_LARGE, NourishColors.INK))
                addView(
                    ui.text(
                        servingLabel,
                        NourishTypography.LABEL,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                    )
                )
            }
            heading.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            heading.addView(
                ui.displayText("${log.calories()} cal", NourishTypography.BODY_LARGE, NourishColors.INK_SECONDARY),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = ui.dp(NourishSpacing.SM)
                }
            )
            addView(heading)

            addView(
                ui.text(
                    "${formatGrams(log.proteinGrams())} protein  |  " +
                        "${formatGrams(log.totalCarbsGrams())} carbs  |  " +
                        "${formatGrams(log.totalFatGrams())} fat",
                    NourishTypography.LABEL,
                    NourishColors.INK_SECONDARY,
                    Typeface.NORMAL
                ),
                matchWrapParams(topMargin = NourishSpacing.SM)
            )
            addView(divider())

            val actions = actionRow()
            actions.addView(
                ui.button("Edit", NourishColors.BLUE, Color.TRANSPARENT).apply {
                    setOnClickListener { callbacks.editFoodLog(log) }
                },
                weightedActionParams()
            )
            actions.addView(
                ui.button("Delete", NourishColors.CORAL, Color.TRANSPARENT).apply {
                    setOnClickListener { callbacks.deleteFoodLog(log) }
                },
                weightedActionParams(last = true)
            )
            addView(actions)
        }
    }

    fun savedMealCard(savedMeal: SavedMeal): View {
        val items = store.getSavedMealItems(savedMeal.id)
        val totals = totalsFor(items)

        return ui.card().apply {
            elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
            addView(
                cardHeading(
                    savedMeal.name,
                    plural(items.size, "saved food", "saved foods"),
                    if (totals.calories > 0) "${totals.calories} cal" else "Meal"
                )
            )
            if (savedMeal.notes.isNotEmpty()) {
                addView(
                    ui.text(
                        savedMeal.notes,
                        NourishTypography.LABEL,
                        NourishColors.INK_SECONDARY,
                        Typeface.NORMAL
                    ),
                    matchWrapParams(topMargin = NourishSpacing.XS)
                )
            }
            addView(
                ui.text(
                    itemsSummary(items),
                    NourishTypography.LABEL,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                ),
                matchWrapParams(topMargin = NourishSpacing.XS)
            )
            addView(macroRow(totals))
            addView(divider())

            val actions = actionRow()
            actions.addView(
                ui.button("Log meal", NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                    setOnClickListener { callbacks.logSavedMeal(savedMeal) }
                },
                weightedActionParams()
            )
            actions.addView(
                ui.button("Edit", NourishColors.BLUE, Color.TRANSPARENT).apply {
                    setOnClickListener { callbacks.editSavedMeal(savedMeal) }
                },
                weightedActionParams()
            )
            actions.addView(
                ui.button("Delete", NourishColors.CORAL, Color.TRANSPARENT).apply {
                    setOnClickListener { callbacks.deleteSavedMeal(savedMeal) }
                },
                weightedActionParams(last = true)
            )
            addView(actions)
        }
    }

    private fun cardHeading(title: String, subtitle: String, value: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP

            val copy = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK))
                addView(ui.text(subtitle, NourishTypography.LABEL, NourishColors.MUTED, Typeface.NORMAL))
            }
            addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                ui.text(value, NourishTypography.LABEL, NourishColors.INK_SECONDARY, Typeface.BOLD).apply {
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = ui.dp(NourishSpacing.SM)
                }
            )
        }

    private fun macroRow(totals: NutritionTotals): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ui.dp(NourishSpacing.SM), 0, 0)
        addView(macroValue(formatGrams(totals.proteinGrams), "Protein", NourishColors.GREEN), equalParams())
        addView(verticalDivider())
        addView(macroValue(formatGrams(totals.totalCarbsGrams), "Carbs", NourishColors.BLUE), equalParams())
        addView(verticalDivider())
        addView(macroValue(formatGrams(totals.totalFatGrams), "Fat", NourishColors.GOLD), equalParams())
    }

    private fun macroValue(value: String, label: String, color: Int): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ui.text(value, NourishTypography.BODY, color, Typeface.BOLD).apply { gravity = Gravity.CENTER })
            addView(ui.text(label, NourishTypography.CAPTION, NourishColors.MUTED, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
        }

    private fun actionRow(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(NourishColors.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
            topMargin = ui.dp(NourishSpacing.SM)
            bottomMargin = ui.dp(NourishSpacing.SM)
        }
    }

    private fun verticalDivider(): View = View(activity).apply {
        setBackgroundColor(NourishColors.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(ui.dp(1), ui.dp(38)).apply {
            leftMargin = ui.dp(NourishSpacing.XS)
            rightMargin = ui.dp(NourishSpacing.XS)
        }
    }

    private fun totalsFor(items: List<SavedMealItem>): NutritionTotals = NutritionTotals().apply {
        items.forEach { addFood(it.food, it.servings) }
    }

    private fun itemsSummary(items: List<SavedMealItem>): String {
        if (items.isEmpty()) return "No foods added"
        val visible = items.take(3).joinToString(", ") { item ->
            "${item.food?.displayName() ?: "Food"} x${formatNumber(item.servings)}"
        }
        return if (items.size > 3) "$visible + ${items.size - 3} more" else visible
    }

    private fun formatTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormatter)

    private fun formatGrams(value: Float): String = "${formatNumber(value)}g"

    private fun formatNumber(value: Float): String =
        if (abs(value - value.roundToInt()) < 0.05f) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }

    private fun plural(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"

    private fun equalParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun matchWrapParams(topMargin: Int, height: Int? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height?.let { ui.dp(it) } ?: ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun weightedActionParams(last: Boolean = false): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ui.dp(42), 1f).apply {
            if (!last) rightMargin = ui.dp(NourishSpacing.XS)
        }
}
