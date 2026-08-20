package com.jojokorok.nourishrx.medications

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jojokorok.nourishrx.data.Medication
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.reminders.ReminderScheduler
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishShapes
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi

class MedicationEditorFlow(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun currentProfileId(): Long
        fun onMedicationSaved()
    }

    fun show(existing: Medication?) {
        val medication = editableCopy(existing)
        val selectedDoseMinutes = ArrayList(medication.doseMinutes())
        val selectedRepeatReminderMinutes = intArrayOf(medication.repeatReminderMinutes)

        val nameField = ui.field(
            "Enter medication name",
            medication.name,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )
        val dosageField = ui.field(
            "Enter dosage",
            medication.dosage,
            InputType.TYPE_CLASS_TEXT
        )
        val instructionsField = ui.field(
            "Instructions",
            medication.instructions,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        val quantityField = ui.field(
            "Enter current quantity",
            if (existing == null) "" else medication.quantity.toString(),
            InputType.TYPE_CLASS_NUMBER
        )
        val thresholdField = ui.field(
            "Enter refill level",
            if (existing == null) "" else medication.refillThreshold.toString(),
            InputType.TYPE_CLASS_NUMBER
        )

        val frequencySummary = ui.text(
            "",
            NourishTypography.LABEL,
            NourishColors.MUTED,
            Typeface.NORMAL
        )
        val doseTimesList = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        lateinit var renderDoseTimes: () -> Unit
        renderDoseTimes = {
            renderDoseTimeRows(
                doseTimesList,
                frequencySummary,
                selectedDoseMinutes,
                renderDoseTimes
            )
        }
        renderDoseTimes()

        val repeatSummary = ui.text(
            "",
            NourishTypography.LABEL,
            NourishColors.MUTED,
            Typeface.NORMAL
        )
        val repeatOptions = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        lateinit var renderRepeatOptions: () -> Unit
        renderRepeatOptions = {
            renderRepeatReminderOptions(
                repeatOptions,
                repeatSummary,
                selectedRepeatReminderMinutes,
                renderRepeatOptions
            )
        }
        renderRepeatOptions()

        val activeBox = CheckBox(activity).apply {
            text = "Enable medication reminders"
            setTextColor(NourishColors.INK)
            textSize = NourishTypography.BODY.toFloat()
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL)
            isChecked = medication.active
            setPadding(0, ui.dp(NourishSpacing.XS), 0, 0)
        }

        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.SM)
            )

            addView(
                editorHeader(
                    if (existing == null) "Add medication" else "Edit medication",
                    if (existing == null) {
                        "Create a schedule and keep stock in one place."
                    } else {
                        "Update details without losing dose history."
                    }
                )
            )

            addView(sectionHeader("Medication details", "The name and dosage appear in schedules and alerts."))
            addView(ui.fieldLabel("Name"))
            addView(nameField)
            addView(ui.fieldLabel("Dosage"))
            addView(dosageField)
            addView(ui.fieldLabel("Instructions (optional)"))
            addView(instructionsField)

            addView(sectionHeader("Dose schedule", "Set each time this medication is taken."))
            addView(frequencySummary)
            addView(doseTimesList)
            addView(
                ui.button("Add dose time", NourishColors.GREEN, NourishColors.GREEN_SOFT).apply {
                    setOnClickListener {
                        if (selectedDoseMinutes.size >= Medication.MAX_DOSES_PER_DAY) {
                            Toast.makeText(
                                activity,
                                "Maximum is ${Medication.MAX_DOSES_PER_DAY} doses per day.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            selectedDoseMinutes.add(nextSuggestedDoseTime(selectedDoseMinutes))
                            renderDoseTimes()
                        }
                    }
                },
                fullWidthButtonParams()
            )

            addView(sectionHeader("Repeat alerts", "Choose if an alert should repeat after a scheduled dose."))
            addView(repeatSummary)
            addView(repeatOptions)

            addView(sectionHeader("Inventory", "Stock decreases when a dose is marked taken."))
            addView(ui.fieldLabel("Current quantity"))
            addView(quantityField)
            addView(ui.fieldLabel("Refill level"))
            addView(thresholdField)

            addView(sectionHeader("Reminder status", "Paused medications stay saved but do not send alerts."))
            addView(activeBox)
        }

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
                saveMedication(
                    dialog,
                    medication,
                    nameField,
                    dosageField,
                    instructionsField,
                    selectedDoseMinutes,
                    quantityField,
                    thresholdField,
                    selectedRepeatReminderMinutes[0],
                    activeBox.isChecked
                )
            }
        }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun editableCopy(existing: Medication?): Medication {
        if (existing == null) {
            return Medication.empty().apply { profileId = callbacks.currentProfileId() }
        }
        return Medication(
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
            existing.repeatReminderMinutes,
            existing.active,
            existing.createdAt
        )
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
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ui.dp(NourishSpacing.XXS)
                }
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
            addView(
                ui.displayText(
                    title,
                    NourishTypography.BODY_LARGE,
                    NourishColors.INK
                )
            )
            addView(
                ui.text(
                    subtitle,
                    NourishTypography.CAPTION,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ui.dp(NourishSpacing.XXS)
                }
            )
        }

    private fun saveMedication(
        dialog: AlertDialog,
        medication: Medication,
        nameField: EditText,
        dosageField: EditText,
        instructionsField: EditText,
        selectedDoseMinutes: ArrayList<Int>,
        quantityField: EditText,
        thresholdField: EditText,
        repeatReminderMinutes: Int,
        active: Boolean
    ) {
        val name = nameField.text.toString().trim()
        val dosage = dosageField.text.toString().trim()
        if (name.isEmpty()) {
            nameField.error = "Required"
            return
        }
        if (dosage.isEmpty()) {
            dosageField.error = "Required"
            return
        }

        val toSave = Medication(
            medication.id,
            medication.profileId,
            name,
            dosage,
            instructionsField.text.toString(),
            selectedDoseMinutes.first(),
            selectedDoseMinutes.size,
            selectedDoseMinutes,
            parseInt(quantityField, 0),
            parseInt(thresholdField, 0),
            repeatReminderMinutes,
            active,
            medication.createdAt
        )
        store.saveMedication(toSave)
        if (toSave.active) {
            ReminderScheduler.scheduleNext(activity, toSave)
        } else {
            ReminderScheduler.cancel(activity, toSave.id)
        }
        dialog.dismiss()
        callbacks.onMedicationSaved()
    }

    private fun renderDoseTimeRows(
        container: LinearLayout,
        frequencySummary: TextView,
        doseMinutes: ArrayList<Int>,
        refresh: () -> Unit
    ) {
        normalizeDoseTimes(doseMinutes)
        frequencySummary.text = plural(doseMinutes.size, "dose per day", "doses per day")
        container.removeAllViews()

        doseMinutes.indices.forEach { index ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    ui.dp(NourishSpacing.SM),
                    ui.dp(NourishSpacing.XS),
                    ui.dp(NourishSpacing.XS),
                    ui.dp(NourishSpacing.XS)
                )
                background = ui.rounded(
                    NourishColors.CARD_SUBTLE,
                    NourishColors.BORDER,
                    ui.dp(NourishShapes.RADIUS_CONTROL)
                )
            }
            row.addView(
                ui.text(
                    "Dose ${index + 1}",
                    NourishTypography.LABEL,
                    NourishColors.INK,
                    Typeface.BOLD
                ),
                LinearLayout.LayoutParams(ui.dp(68), ViewGroup.LayoutParams.WRAP_CONTENT)
            )

            val timeButton = ui.button(
                Medication.formatMinutes(doseMinutes[index]),
                NourishColors.BLUE,
                NourishColors.BLUE_SOFT
            ).apply {
                setOnClickListener { showDoseTimePicker(doseMinutes, index, refresh) }
            }
            row.addView(timeButton, LinearLayout.LayoutParams(0, ui.dp(42), 1f))

            val removeButton = ui.button(
                "Remove",
                NourishColors.CORAL,
                Color.TRANSPARENT
            ).apply {
                isEnabled = doseMinutes.size > 1
                alpha = if (doseMinutes.size > 1) 1f else 0.45f
                setOnClickListener {
                    if (doseMinutes.size <= 1) {
                        Toast.makeText(
                            activity,
                            "Keep at least one dose time.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        doseMinutes.removeAt(index)
                        refresh()
                    }
                }
            }
            row.addView(
                removeButton,
                LinearLayout.LayoutParams(ui.dp(92), ui.dp(42)).apply {
                    leftMargin = ui.dp(NourishSpacing.XS)
                }
            )
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ui.dp(NourishSpacing.XS)
                }
            )
        }
    }

    private fun showDoseTimePicker(
        doseMinutes: ArrayList<Int>,
        index: Int,
        refresh: () -> Unit
    ) {
        val existingMinutes = doseMinutes[index]
        TimePickerDialog(
            activity,
            { _, selectedHour, selectedMinute ->
                val newMinutes = Medication.normalizeMinutes((selectedHour * 60) + selectedMinute)
                if (doseMinutes.indices.any { it != index && doseMinutes[it] == newMinutes }) {
                    Toast.makeText(
                        activity,
                        "That dose time is already scheduled.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    doseMinutes[index] = newMinutes
                    refresh()
                }
            },
            existingMinutes / 60,
            existingMinutes % 60,
            false
        ).show()
    }

    private fun renderRepeatReminderOptions(
        container: LinearLayout,
        repeatSummary: TextView,
        repeatReminderMinutes: IntArray,
        refresh: () -> Unit
    ) {
        repeatReminderMinutes[0] = repeatReminderMinutes[0].coerceIn(
            0,
            Medication.MAX_REPEAT_REMINDER_MINUTES
        )
        repeatSummary.text = Medication.repeatReminderLabel(repeatReminderMinutes[0])
        container.removeAllViews()
        addRepeatReminderRow(container, repeatReminderMinutes, refresh, intArrayOf(0, 5, 10))
        addRepeatReminderRow(container, repeatReminderMinutes, refresh, intArrayOf(30, 60, -1))
    }

    private fun addRepeatReminderRow(
        container: LinearLayout,
        repeatReminderMinutes: IntArray,
        refresh: () -> Unit,
        options: IntArray
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        options.forEach { option ->
            val selected = if (option < 0) {
                isCustomRepeatReminder(repeatReminderMinutes[0])
            } else {
                repeatReminderMinutes[0] == option
            }
            val optionButton = ui.button(
                repeatReminderOptionLabel(option),
                if (selected) NourishColors.ON_ACCENT else NourishColors.GREEN_DARK,
                if (selected) NourishColors.GREEN else Color.TRANSPARENT
            ).apply {
                setOnClickListener {
                    if (option < 0) {
                        showCustomRepeatReminderDialog(repeatReminderMinutes, refresh)
                    } else {
                        repeatReminderMinutes[0] = option
                        refresh()
                    }
                }
            }
            row.addView(
                optionButton,
                LinearLayout.LayoutParams(0, ui.dp(42), 1f).apply {
                    if (row.childCount > 0) leftMargin = ui.dp(NourishSpacing.XS)
                }
            )
        }
        container.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ui.dp(NourishSpacing.XS)
            }
        )
    }

    private fun showCustomRepeatReminderDialog(
        repeatReminderMinutes: IntArray,
        refresh: () -> Unit
    ) {
        val currentValue = if (isCustomRepeatReminder(repeatReminderMinutes[0])) {
            repeatReminderMinutes[0].toString()
        } else {
            ""
        }
        val minutesField = ui.field(
            "Minutes between alerts",
            currentValue,
            InputType.TYPE_CLASS_NUMBER
        ).apply {
            setSelectAllOnFocus(true)
        }
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
                    "Custom repeat",
                    "Enter the number of minutes between follow-up alerts."
                )
            )
            addView(ui.fieldLabel("Repeat interval"))
            addView(minutesField)
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            styleDialogActions(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutes = parseInt(minutesField, 0)
                when {
                    minutes <= 0 -> minutesField.error = "Enter minutes"
                    minutes > Medication.MAX_REPEAT_REMINDER_MINUTES -> {
                        minutesField.error = "Use 1440 minutes or less"
                    }
                    else -> {
                        repeatReminderMinutes[0] = minutes
                        refresh()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

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

    private fun fullWidthButtonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)).apply {
            topMargin = ui.dp(NourishSpacing.SM)
        }

    private fun nextSuggestedDoseTime(doseMinutes: List<Int>): Int {
        if (doseMinutes.isEmpty()) return 8 * 60

        val sorted = ArrayList(doseMinutes)
        normalizeDoseTimes(sorted)
        var candidate = Medication.normalizeMinutes(sorted.last() + (4 * 60))
        repeat(Medication.MAX_DOSES_PER_DAY) {
            if (!sorted.contains(candidate)) return candidate
            candidate = Medication.normalizeMinutes(candidate + 60)
        }
        return 8 * 60
    }

    private fun normalizeDoseTimes(doseMinutes: ArrayList<Int>) {
        if (doseMinutes.isEmpty()) doseMinutes.add(8 * 60)
        doseMinutes.indices.forEach { index ->
            doseMinutes[index] = Medication.normalizeMinutes(doseMinutes[index])
        }
        doseMinutes.sort()
        while (doseMinutes.size > Medication.MAX_DOSES_PER_DAY) {
            doseMinutes.removeAt(doseMinutes.lastIndex)
        }
    }

    private fun isCustomRepeatReminder(minutes: Int): Boolean =
        minutes > 0 && minutes !in setOf(5, 10, 30, 60)

    private fun repeatReminderOptionLabel(minutes: Int): String = when {
        minutes < 0 -> "Custom"
        minutes == 0 -> "Off"
        minutes == 60 -> "1 hr"
        else -> "$minutes min"
    }

    private fun parseInt(field: EditText, fallback: Int): Int =
        field.text.toString().trim().toIntOrNull()?.coerceAtLeast(0) ?: fallback

    private fun plural(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"
}
