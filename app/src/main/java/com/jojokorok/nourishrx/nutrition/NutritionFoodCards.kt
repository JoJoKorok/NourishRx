package com.jojokorok.nourishrx.nutrition

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jojokorok.nourishrx.data.NutritionFood
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishShapes
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class NutritionFoodCards(
    private val activity: Activity,
    private val ui: NourishUi,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun logFood(food: NutritionFood)
        fun editFood(food: NutritionFood)
        fun deleteFood(food: NutritionFood)
    }

    fun foodCard(food: NutritionFood): View = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()

        if (food.brand.isNotEmpty()) {
            addView(
                ui.text(
                    food.brand,
                    NourishTypography.CAPTION,
                    NourishColors.GREEN_DARK,
                    Typeface.BOLD
                )
            )
        }

        val heading = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            if (food.brand.isNotEmpty()) setPadding(0, ui.dp(NourishSpacing.XXS), 0, 0)
        }
        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ui.displayText(
                    food.name.ifEmpty { "Unnamed food" },
                    NourishTypography.BODY_LARGE,
                    NourishColors.INK
                )
            )
            addView(
                ui.text(
                    food.servingSummary(),
                    NourishTypography.LABEL,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                )
            )
        }
        heading.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(
            ui.displayText(
                if (food.calories > 0) "${food.calories} cal" else "-- cal",
                NourishTypography.BODY_LARGE,
                NourishColors.INK_SECONDARY
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = ui.dp(NourishSpacing.SM)
            }
        )
        addView(heading)

        addView(macroRow(food))
        addView(
            ui.text(
                "${formatMilligrams(food.sodiumMg)} sodium  |  " +
                    "${formatGrams(food.totalSugarsGrams)} sugars  |  " +
                    "${formatGrams(food.fiberGrams)} fiber",
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.NORMAL
            ),
            matchWrapParams(topMargin = NourishSpacing.SM)
        )
        addView(divider())

        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            ui.button("Log food", NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                setOnClickListener { callbacks.logFood(food) }
            },
            weightedActionParams()
        )
        actions.addView(
            ui.button("Edit", NourishColors.BLUE, Color.TRANSPARENT).apply {
                setOnClickListener { callbacks.editFood(food) }
            },
            weightedActionParams()
        )
        actions.addView(
            ui.button("Delete", NourishColors.CORAL, Color.TRANSPARENT).apply {
                setOnClickListener { callbacks.deleteFood(food) }
            },
            weightedActionParams(last = true)
        )
        addView(actions)
    }

    private fun macroRow(food: NutritionFood): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ui.dp(NourishSpacing.SM), 0, 0)
        addView(macroValue(formatGrams(food.proteinGrams), "Protein", NourishColors.GREEN), equalParams())
        addView(verticalDivider())
        addView(macroValue(formatGrams(food.totalCarbsGrams), "Carbs", NourishColors.BLUE), equalParams())
        addView(verticalDivider())
        addView(macroValue(formatGrams(food.totalFatGrams), "Fat", NourishColors.GOLD), equalParams())
    }

    private fun macroValue(value: String, label: String, color: Int): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ui.text(value, NourishTypography.BODY, color, Typeface.BOLD).apply { gravity = Gravity.CENTER })
            addView(ui.text(label, NourishTypography.CAPTION, NourishColors.MUTED, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
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

    private fun formatGrams(value: Float): String = "${formatNumber(value)}g"
    private fun formatMilligrams(value: Float): String = "${formatNumber(value)}mg"

    private fun formatNumber(value: Float): String =
        if (abs(value - value.roundToInt()) < 0.05f) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }

    private fun equalParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun matchWrapParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun weightedActionParams(last: Boolean = false): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ui.dp(42), 1f).apply {
            if (!last) rightMargin = ui.dp(NourishSpacing.XS)
        }
}
