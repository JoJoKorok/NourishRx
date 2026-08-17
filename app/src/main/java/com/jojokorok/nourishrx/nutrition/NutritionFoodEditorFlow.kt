package com.jojokorok.nourishrx.nutrition

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.data.NutritionFood
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class NutritionFoodEditorFlow(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun currentProfileId(): Long
        fun onFoodChanged()
    }

    private data class FoodFields(
        val brand: EditText,
        val name: EditText,
        val servingSize: EditText,
        val servingsPerContainer: EditText,
        val calories: EditText,
        val totalFat: EditText,
        val saturatedFat: EditText,
        val transFat: EditText,
        val cholesterol: EditText,
        val sodium: EditText,
        val totalCarbs: EditText,
        val fiber: EditText,
        val totalSugars: EditText,
        val addedSugars: EditText,
        val protein: EditText,
        val vitaminD: EditText,
        val calcium: EditText,
        val iron: EditText,
        val potassium: EditText
    )

    fun show(existing: NutritionFood?) {
        val food = existing ?: emptyFood()
        val fields = createFields(food)
        val form = editorForm(existing, fields)
        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
            addView(form)
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            styleDialogActions(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                saveFood(dialog, food, fields)
            }
        }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun confirmDelete(food: NutritionFood) {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.SM)
            )
            addView(
                editorHeader(
                    "Delete ${food.displayName()}?",
                    "This removes the saved food and any meal logs that use it."
                )
            )
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                store.deleteNutritionFood(food.id)
                callbacks.onFoodChanged()
            }
            .create()
        dialog.setOnShowListener {
            styleDialogActions(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(NourishColors.CORAL)
        }
        dialog.show()
    }

    private fun createFields(food: NutritionFood): FoodFields {
        val decimalInput = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        return FoodFields(
            brand = ui.field(
                "Optional brand",
                food.brand,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            ),
            name = ui.field(
                "Food name",
                food.name,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            ),
            servingSize = ui.field("Example: 1 cup or 30 g", food.servingSize, InputType.TYPE_CLASS_TEXT),
            servingsPerContainer = numberField("Optional", food.servingsPerContainer, decimalInput),
            calories = ui.field(
                "kcal",
                existingIntValue(food.calories),
                InputType.TYPE_CLASS_NUMBER
            ),
            totalFat = numberField("g", food.totalFatGrams, decimalInput),
            saturatedFat = numberField("g", food.saturatedFatGrams, decimalInput),
            transFat = numberField("g", food.transFatGrams, decimalInput),
            cholesterol = numberField("mg", food.cholesterolMg, decimalInput),
            sodium = numberField("mg", food.sodiumMg, decimalInput),
            totalCarbs = numberField("g", food.totalCarbsGrams, decimalInput),
            fiber = numberField("g", food.fiberGrams, decimalInput),
            totalSugars = numberField("g", food.totalSugarsGrams, decimalInput),
            addedSugars = numberField("g", food.addedSugarsGrams, decimalInput),
            protein = numberField("g", food.proteinGrams, decimalInput),
            vitaminD = numberField("mcg", food.vitaminDMcg, decimalInput),
            calcium = numberField("mg", food.calciumMg, decimalInput),
            iron = numberField("mg", food.ironMg, decimalInput),
            potassium = numberField("mg", food.potassiumMg, decimalInput)
        )
    }

    private fun editorForm(existing: NutritionFood?, fields: FoodFields): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.SM)
            )

            addView(
                editorHeader(
                    if (existing == null) "Add food" else "Edit food",
                    "Nutrition values are stored per serving and can be reused in any meal."
                )
            )

            addView(sectionHeader("Food details", "Give this food a clear name for your library."))
            addView(ui.fieldLabel("Food name"))
            addView(fields.name)
            addView(ui.fieldLabel("Brand (optional)"))
            addView(fields.brand)

            addView(sectionHeader("Serving", "Describe the amount represented by the nutrition values."))
            addView(ui.fieldLabel("Serving size"))
            addView(fields.servingSize)
            addView(ui.fieldLabel("Servings per container (optional)"))
            addView(fields.servingsPerContainer)

            addView(sectionHeader("Calories and macros", "Enter the amounts shown for one serving."))
            addView(fieldPair("Calories", fields.calories, "Protein", fields.protein))
            addView(fieldPair("Total carbs", fields.totalCarbs, "Total fat", fields.totalFat))

            addView(sectionHeader("Detailed nutrients", "Leave any value blank when it is not available."))
            addView(fieldPair("Saturated fat", fields.saturatedFat, "Trans fat", fields.transFat))
            addView(fieldPair("Cholesterol", fields.cholesterol, "Sodium", fields.sodium))
            addView(fieldPair("Fiber", fields.fiber, "Total sugars", fields.totalSugars))
            addView(singleField("Added sugars", fields.addedSugars))

            addView(sectionHeader("Vitamins and minerals", "Use the units printed on the nutrition label."))
            addView(fieldPair("Vitamin D", fields.vitaminD, "Calcium", fields.calcium))
            addView(fieldPair("Iron", fields.iron, "Potassium", fields.potassium))
        }

    private fun editorHeader(title: String, subtitle: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, ui.dp(NourishSpacing.XS))
            addView(ui.displayText(title, NourishTypography.TITLE, NourishColors.INK))
            addView(
                ui.text(
                    subtitle,
                    NourishTypography.LABEL,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                ),
                matchWrapParams(topMargin = NourishSpacing.XXS)
            )
        }

    private fun sectionHeader(title: String, subtitle: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(NourishSpacing.LG), 0, 0)
            addView(
                View(activity).apply { setBackgroundColor(NourishColors.DIVIDER) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
                    bottomMargin = ui.dp(NourishSpacing.MD)
                }
            )
            addView(ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK))
            addView(
                ui.text(
                    subtitle,
                    NourishTypography.CAPTION,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                ),
                matchWrapParams(topMargin = NourishSpacing.XXS)
            )
        }

    private fun fieldPair(
        leftLabel: String,
        leftField: EditText,
        rightLabel: String,
        rightField: EditText
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(labeledField(leftLabel, leftField), pairFieldParams(endMargin = NourishSpacing.XS))
        addView(labeledField(rightLabel, rightField), pairFieldParams())
    }

    private fun singleField(label: String, field: EditText): LinearLayout =
        labeledField(label, field).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun labeledField(label: String, field: EditText): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.fieldLabel(label))
            addView(
                field,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

    private fun saveFood(dialog: AlertDialog, food: NutritionFood, fields: FoodFields) {
        val name = fields.name.text.toString().trim()
        if (name.isEmpty()) {
            fields.name.error = "Required"
            return
        }

        val servingSize = fields.servingSize.text.toString().trim()
        if (servingSize.isEmpty()) {
            fields.servingSize.error = "Required"
            return
        }

        store.saveNutritionFood(
            NutritionFood(
                food.id,
                callbacks.currentProfileId(),
                fields.brand.text.toString(),
                name,
                servingSize,
                parseFloat(fields.servingsPerContainer),
                parseInt(fields.calories),
                parseFloat(fields.totalFat),
                parseFloat(fields.saturatedFat),
                parseFloat(fields.transFat),
                parseFloat(fields.cholesterol),
                parseFloat(fields.sodium),
                parseFloat(fields.totalCarbs),
                parseFloat(fields.fiber),
                parseFloat(fields.totalSugars),
                parseFloat(fields.addedSugars),
                parseFloat(fields.protein),
                parseFloat(fields.vitaminD),
                parseFloat(fields.calcium),
                parseFloat(fields.iron),
                parseFloat(fields.potassium),
                food.createdAt
            )
        )
        dialog.dismiss()
        callbacks.onFoodChanged()
    }

    private fun emptyFood(): NutritionFood = NutritionFood(
        0,
        callbacks.currentProfileId(),
        "",
        "",
        "",
        0f,
        0,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        0f,
        System.currentTimeMillis()
    )

    private fun numberField(hint: String, value: Float, inputType: Int): EditText =
        ui.field(hint, existingFloatValue(value), inputType)

    private fun existingIntValue(value: Int): String = if (value > 0) value.toString() else ""

    private fun existingFloatValue(value: Float): String = when {
        value <= 0f -> ""
        abs(value - value.roundToInt()) < 0.05f -> value.roundToInt().toString()
        else -> String.format(Locale.getDefault(), "%.1f", value)
    }

    private fun parseInt(field: EditText): Int =
        field.text.toString().trim().toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun parseFloat(field: EditText): Float =
        field.text.toString().trim().toFloatOrNull()?.coerceAtLeast(0f) ?: 0f

    private fun styleDialogActions(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor(NourishColors.GREEN)
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
            setTextColor(NourishColors.INK_SECONDARY)
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL)
        }
    }

    private fun pairFieldParams(endMargin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = ui.dp(endMargin)
        }

    private fun matchWrapParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = ui.dp(topMargin)
        }
}
