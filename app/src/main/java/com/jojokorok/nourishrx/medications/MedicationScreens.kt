package com.jojokorok.nourishrx.medications

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.jojokorok.nourishrx.data.Medication
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishShapes
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi

class MedicationScreens(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun currentProfileId(): Long
        fun showMedicationDialog(medication: Medication?)
        fun toggleMedication(medication: Medication)
        fun confirmDelete(medication: Medication)
        fun adjustInventory(medication: Medication, delta: Int)
        fun showInventoryDialog(medication: Medication)
    }

    fun renderMedications(content: LinearLayout) {
        val medications = store.getAllMedications(callbacks.currentProfileId())
        val activeCount = medications.count { it.active }
        val pausedCount = medications.size - activeCount
        val summary = when {
            medications.isEmpty() -> "No medications saved"
            pausedCount == 0 -> plural(activeCount, "active medication", "active medications")
            else -> "$activeCount active, $pausedCount paused"
        }
        content.addView(sectionHeader("Medication library", summary))

        if (medications.isEmpty()) {
            content.addView(
                emptyState(
                    "Your medication library is empty",
                    "Save medication details, dose times, and reminder settings here."
                )
            )
            return
        }

        medications.forEach { content.addView(medicationCard(it)) }
    }

    fun renderInventory(content: LinearLayout) {
        val medications = store.getAllMedications(callbacks.currentProfileId())
        val lowCount = medications.count { it.isLowStock() }
        val summary = when {
            medications.isEmpty() -> "No stock to track"
            lowCount == 0 -> "All medication stock is above its refill level"
            else -> plural(lowCount, "medication needs a refill", "medications need a refill")
        }
        content.addView(sectionHeader("Medication stock", summary))

        if (medications.isEmpty()) {
            content.addView(
                emptyState(
                    "No inventory yet",
                    "Add a medication to start tracking quantities and refill levels."
                )
            )
            return
        }

        medications.forEach { content.addView(inventoryCard(it)) }
    }

    private fun sectionHeader(title: String, subtitle: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(NourishSpacing.XS), 0, ui.dp(NourishSpacing.XXS))
            addView(ui.displayText(title, NourishTypography.TITLE, NourishColors.INK))
            addView(
                ui.text(
                    subtitle,
                    NourishTypography.LABEL,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                )
            )
        }

    private fun medicationCard(medication: Medication): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
        addView(cardHeader(medication, if (medication.active) "Active" else "Paused"))
        addView(divider(), dividerParams())
        addView(
            metadataRow(
                "Schedule",
                "${medication.doseCountLabel()} at ${medication.scheduleSummary()}"
            ),
            matchWrapParams(topMargin = NourishSpacing.SM)
        )
        addView(
            metadataRow("Alerts", medication.repeatReminderLabel()),
            matchWrapParams(topMargin = NourishSpacing.XS)
        )

        if (medication.instructions.isNotEmpty()) {
            addView(
                metadataRow("Instructions", medication.instructions),
                matchWrapParams(topMargin = NourishSpacing.XS)
            )
        }

        addView(divider(), dividerParams())
        addView(medicationActions(medication))
    }

    private fun inventoryCard(medication: Medication): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
        val status = when {
            !medication.active -> "Paused"
            medication.isLowStock() -> "Refill"
            else -> "Active"
        }
        addView(cardHeader(medication, status))
        addView(divider(), dividerParams())
        addView(stockSummary(medication), matchWrapParams(topMargin = NourishSpacing.SM))
        addView(divider(), dividerParams())
        addView(inventoryActions(medication))
    }

    private fun cardHeader(medication: Medication, status: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP

            val details = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    ui.displayText(
                        medication.name,
                        NourishTypography.BODY_LARGE,
                        NourishColors.INK
                    )
                )
                if (medication.dosage.isNotEmpty()) {
                    addView(
                        ui.text(
                            medication.dosage,
                            NourishTypography.LABEL,
                            NourishColors.INK_SECONDARY,
                            Typeface.NORMAL
                        )
                    )
                }
            }
            addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                ui.statusBadge(status),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = ui.dp(NourishSpacing.SM)
                }
            )
        }

    private fun metadataRow(label: String, value: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(
                ui.text(
                    label,
                    NourishTypography.CAPTION,
                    NourishColors.MUTED,
                    Typeface.BOLD
                ),
                LinearLayout.LayoutParams(ui.dp(88), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = ui.dp(NourishSpacing.XS)
                }
            )
            addView(
                ui.text(
                    value,
                    NourishTypography.LABEL,
                    NourishColors.INK_SECONDARY,
                    Typeface.NORMAL
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

    private fun stockSummary(medication: Medication): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM

            val quantity = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    ui.displayText(
                        medication.quantity.toString(),
                        NourishTypography.SCREEN_TITLE,
                        if (medication.isLowStock()) NourishColors.CORAL else NourishColors.GREEN_DARK
                    )
                )
                addView(
                    ui.text(
                        "remaining",
                        NourishTypography.LABEL,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                    )
                )
            }
            addView(quantity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val threshold = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(
                    ui.text(
                        "Refill at",
                        NourishTypography.CAPTION,
                        NourishColors.MUTED,
                        Typeface.BOLD
                    )
                )
                addView(
                    ui.displayText(
                        medication.refillThreshold.toString(),
                        NourishTypography.BODY_LARGE,
                        NourishColors.INK_SECONDARY
                    )
                )
            }
            addView(threshold, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun medicationActions(medication: Medication): LinearLayout = actionRow().apply {
        addView(
            actionButton("Edit", NourishColors.BLUE, NourishColors.BLUE_SOFT).apply {
                setOnClickListener { callbacks.showMedicationDialog(medication) }
            },
            weightedActionParams(hasTrailingMargin = true)
        )
        addView(
            actionButton(
                if (medication.active) "Pause" else "Resume",
                if (medication.active) NourishColors.INK_SECONDARY else NourishColors.GREEN,
                Color.TRANSPARENT
            ).apply {
                setOnClickListener { callbacks.toggleMedication(medication) }
            },
            weightedActionParams(hasTrailingMargin = true)
        )
        addView(
            actionButton("Delete", NourishColors.CORAL, Color.TRANSPARENT).apply {
                setOnClickListener { callbacks.confirmDelete(medication) }
            },
            weightedActionParams(hasTrailingMargin = false)
        )
    }

    private fun inventoryActions(medication: Medication): LinearLayout = actionRow().apply {
        addView(
            actionButton("-1", NourishColors.CORAL, Color.TRANSPARENT).apply {
                setOnClickListener { callbacks.adjustInventory(medication, -1) }
            },
            compactActionParams()
        )
        addView(
            actionButton("+10", NourishColors.GREEN, Color.TRANSPARENT).apply {
                setOnClickListener { callbacks.adjustInventory(medication, 10) }
            },
            compactActionParams()
        )
        addView(
            actionButton("Update stock", NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                setOnClickListener { callbacks.showInventoryDialog(medication) }
            },
            LinearLayout.LayoutParams(0, ui.dp(44), 1f)
        )
    }

    private fun emptyState(title: String, message: String): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()

        val intro = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        intro.addView(
            ui.text("Rx", NourishTypography.BODY, NourishColors.GREEN_DARK, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                background = ui.rounded(
                    NourishColors.GREEN_SOFT,
                    Color.TRANSPARENT,
                    ui.dp(NourishShapes.RADIUS_CONTROL)
                )
            },
            LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)).apply {
                rightMargin = ui.dp(NourishSpacing.SM)
            }
        )
        intro.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK))
                addView(
                    ui.text(
                        message,
                        NourishTypography.LABEL,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                    )
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(intro)

        addView(
            actionButton("Add medication", NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                setOnClickListener { callbacks.showMedicationDialog(null) }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)).apply {
                topMargin = ui.dp(NourishSpacing.MD)
            }
        )
    }

    private fun actionRow(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ui.dp(NourishSpacing.SM), 0, 0)
    }

    private fun actionButton(label: String, textColor: Int, backgroundColor: Int): Button =
        ui.button(label, textColor, backgroundColor)

    private fun weightedActionParams(hasTrailingMargin: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply {
            if (hasTrailingMargin) rightMargin = ui.dp(NourishSpacing.XS)
        }

    private fun compactActionParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ui.dp(64), ui.dp(44)).apply {
            rightMargin = ui.dp(NourishSpacing.XS)
        }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(NourishColors.DIVIDER)
    }

    private fun dividerParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
            topMargin = ui.dp(NourishSpacing.SM)
        }

    private fun matchWrapParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun plural(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"
}
