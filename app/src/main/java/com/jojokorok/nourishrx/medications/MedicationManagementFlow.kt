package com.jojokorok.nourishrx.medications

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.jojokorok.nourishrx.data.Medication
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.reminders.ReminderScheduler
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishShapes
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi

class MedicationManagementFlow(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onMedicationChanged()
    }

    fun showInventoryDialog(medication: Medication) {
        val quantityField = ui.field(
            "Enter current quantity",
            medication.quantity.toString(),
            InputType.TYPE_CLASS_NUMBER
        ).apply {
            setSelectAllOnFocus(true)
        }
        val thresholdField = ui.field(
            "Enter refill level",
            medication.refillThreshold.toString(),
            InputType.TYPE_CLASS_NUMBER
        ).apply {
            setSelectAllOnFocus(true)
        }

        val content = dialogContent().apply {
            addView(
                dialogHeader(
                    "Update stock",
                    "${medication.name}${dosageSuffix(medication)}"
                )
            )
            addView(ui.fieldLabel("Current quantity"))
            addView(quantityField)
            addView(ui.fieldLabel("Refill level"))
            addView(thresholdField)
            addView(
                ui.text(
                    "A refill is flagged when the remaining quantity reaches this level.",
                    NourishTypography.CAPTION,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ui.dp(NourishSpacing.XS)
                }
            )
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            styleDialogActions(dialog, destructive = false)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                medication.quantity = parseInt(quantityField, 0)
                medication.refillThreshold = parseInt(thresholdField, 0)
                store.saveMedication(medication)
                dialog.dismiss()
                callbacks.onMedicationChanged()
            }
        }
        dialog.show()
    }

    fun confirmDelete(medication: Medication) {
        val content = dialogContent().apply {
            val intro = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            intro.addView(
                ui.text("!", NourishTypography.BODY_LARGE, NourishColors.CORAL, Typeface.BOLD).apply {
                    gravity = Gravity.CENTER
                    background = ui.rounded(
                        NourishColors.CORAL_SOFT,
                        Color.TRANSPARENT,
                        ui.dp(NourishShapes.RADIUS_CONTROL)
                    )
                },
                LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)).apply {
                    rightMargin = ui.dp(NourishSpacing.SM)
                }
            )
            intro.addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        ui.displayText(
                            "Delete ${medication.name}?",
                            NourishTypography.TITLE,
                            NourishColors.INK
                        )
                    )
                    addView(
                        ui.text(
                            "This permanently removes the medication and its dose history from this phone.",
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
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(intro)
            addView(
                ui.text(
                    "This action cannot be undone.",
                    NourishTypography.CAPTION,
                    NourishColors.CORAL,
                    Typeface.BOLD
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ui.dp(NourishSpacing.MD)
                }
            )
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", null)
            .create()

        dialog.setOnShowListener {
            styleDialogActions(dialog, destructive = true)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                ReminderScheduler.cancel(activity, medication.id)
                store.deleteMedication(medication.id)
                dialog.dismiss()
                callbacks.onMedicationChanged()
            }
        }
        dialog.show()
    }

    fun toggleMedication(medication: Medication) {
        medication.active = !medication.active
        store.saveMedication(medication)
        if (medication.active) {
            ReminderScheduler.scheduleNext(activity, medication)
        } else {
            ReminderScheduler.cancel(activity, medication.id)
        }
        callbacks.onMedicationChanged()
    }

    fun adjustInventory(medication: Medication, delta: Int) {
        store.adjustInventory(medication.id, delta)
        callbacks.onMedicationChanged()
    }

    private fun dialogContent(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            ui.dp(NourishSpacing.LG),
            ui.dp(NourishSpacing.MD),
            ui.dp(NourishSpacing.LG),
            ui.dp(NourishSpacing.SM)
        )
    }

    private fun dialogHeader(title: String, subtitle: String): LinearLayout =
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

    private fun styleDialogActions(dialog: AlertDialog, destructive: Boolean) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor(if (destructive) NourishColors.CORAL else NourishColors.GREEN)
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
            setTextColor(NourishColors.INK_SECONDARY)
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL)
        }
    }

    private fun dosageSuffix(medication: Medication): String =
        if (medication.dosage.isEmpty()) "" else " - ${medication.dosage}"

    private fun parseInt(field: EditText, fallback: Int): Int =
        field.text.toString().trim().toIntOrNull()?.coerceAtLeast(0) ?: fallback
}
