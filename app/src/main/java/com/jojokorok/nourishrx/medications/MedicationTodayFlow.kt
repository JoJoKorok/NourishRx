package com.jojokorok.nourishrx.medications

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.jojokorok.nourishrx.data.Medication
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.reminders.ReminderScheduler
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

class MedicationTodayFlow(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val zoneId: ZoneId,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun currentProfileId(): Long
        fun showMedicationEditor()
        fun onDoseChanged()
    }

    private data class DoseRow(
        val medication: Medication,
        val scheduledAt: Long,
        val status: String?
    )

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun renderToday(content: LinearLayout) {
        val activeMedications = store.getActiveMedications(callbacks.currentProfileId())
        val rows = doseRowsFor(LocalDate.now(zoneId), activeMedications)
        content.addView(scheduleHeader(rows))

        if (activeMedications.isEmpty()) {
            content.addView(
                emptyState(
                    "No medications yet",
                    "Add a medication to build today's schedule."
                )
            )
            return
        }

        if (rows.isEmpty()) {
            content.addView(
                emptyState(
                    "No doses today",
                    "Your medications are saved, but no doses are scheduled for today."
                )
            )
            return
        }

        val nextPendingIndex = rows.indexOfFirst { it.status == null }
        rows.forEachIndexed { index, row ->
            content.addView(doseCard(row, index == nextPendingIndex))
        }

        content.addView(
            ui.text(
                "Always follow your prescriber's directions.",
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.NORMAL
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(NourishSpacing.SM), 0, 0)
            }
        )
    }

    fun doseCountFor(date: LocalDate): Int = doseRowsFor(
        date,
        store.getActiveMedications(callbacks.currentProfileId())
    ).size

    private fun scheduleHeader(rows: List<DoseRow>): View {
        val taken = rows.count { it.status == MedicationStore.STATUS_TAKEN }
        val remaining = rows.count { it.status == null }
        val skipped = rows.count { it.status == MedicationStore.STATUS_SKIPPED }
        val summary = if (rows.isEmpty()) {
            "No doses scheduled"
        } else {
            buildList {
                add(plural(taken, "taken", "taken"))
                add(plural(remaining, "remaining", "remaining"))
                if (skipped > 0) add(plural(skipped, "skipped", "skipped"))
            }.joinToString(", ")
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(NourishSpacing.XS), 0, ui.dp(NourishSpacing.XXS))
            addView(
                ui.displayText(
                    "Today's schedule",
                    NourishTypography.TITLE,
                    NourishColors.INK
                )
            )
            addView(
                ui.text(
                    summary,
                    NourishTypography.LABEL,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                )
            )
        }
    }

    private fun emptyState(title: String, message: String): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()

        val intro = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = ui.text("Rx", NourishTypography.BODY, NourishColors.GREEN_DARK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = ui.rounded(
                NourishColors.GREEN_SOFT,
                Color.TRANSPARENT,
                ui.dp(NourishShapes.RADIUS_CONTROL)
            )
        }
        intro.addView(
            mark,
            LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)).apply {
                rightMargin = ui.dp(NourishSpacing.SM)
            }
        )

        val copy = LinearLayout(activity).apply {
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
        }
        intro.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(intro)

        val add = ui.button(
            "Add medication",
            NourishColors.ON_ACCENT,
            NourishColors.GREEN
        ).apply {
            setOnClickListener { callbacks.showMedicationEditor() }
        }
        addView(
            add,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)).apply {
                topMargin = ui.dp(NourishSpacing.MD)
            }
        )
    }

    private fun doseCard(row: DoseRow, isNext: Boolean): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
        if (isNext) {
            background = ui.rounded(
                NourishColors.CARD,
                NourishColors.GREEN,
                ui.dp(NourishShapes.RADIUS_CARD)
            )
            addView(
                ui.text(
                    "Up next",
                    NourishTypography.CAPTION,
                    NourishColors.GREEN_DARK,
                    Typeface.BOLD
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = ui.dp(NourishSpacing.XS)
                }
            )
        }

        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ui.displayText(
                    row.medication.name,
                    NourishTypography.BODY_LARGE,
                    NourishColors.INK
                )
            )
            addView(
                ui.text(
                    row.medication.dosage,
                    NourishTypography.LABEL,
                    NourishColors.INK_SECONDARY,
                    Typeface.NORMAL
                )
            )
        }
        top.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val timing = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(
                ui.displayText(
                    formatTime(row.scheduledAt),
                    NourishTypography.BODY_LARGE,
                    NourishColors.GREEN_DARK
                )
            )
            addView(
                ui.statusBadge(rowStatus(row)),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ui.dp(NourishSpacing.XXS)
                    gravity = Gravity.END
                }
            )
        }
        top.addView(
            timing,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = ui.dp(NourishSpacing.SM)
            }
        )
        addView(top)

        if (row.medication.instructions.isNotEmpty()) {
            addView(
                ui.text(
                    row.medication.instructions,
                    NourishTypography.LABEL,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                ),
                matchWrapParams(topMargin = NourishSpacing.XS)
            )
        }

        if (row.medication.isLowStock()) {
            addView(
                ui.text(
                    "Low stock: ${row.medication.quantity} left",
                    NourishTypography.LABEL,
                    NourishColors.CORAL,
                    Typeface.BOLD
                ),
                matchWrapParams(topMargin = NourishSpacing.XS)
            )
        }

        if (row.status == null) {
            addView(
                View(activity).apply { setBackgroundColor(NourishColors.DIVIDER) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)).apply {
                    topMargin = ui.dp(NourishSpacing.SM)
                }
            )
            addView(actionRow(row))
        }
    }

    private fun actionRow(row: DoseRow): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ui.dp(NourishSpacing.SM), 0, 0)

        val taken = ui.button(
            "Mark taken",
            NourishColors.ON_ACCENT,
            NourishColors.GREEN
        ).apply {
            setOnClickListener { markDose(row, MedicationStore.STATUS_TAKEN) }
        }
        addView(
            taken,
            LinearLayout.LayoutParams(0, ui.dp(44), 1f).apply {
                rightMargin = ui.dp(NourishSpacing.XS)
            }
        )

        val skip = ui.button("Skip", NourishColors.CORAL, Color.TRANSPARENT).apply {
            setOnClickListener { markDose(row, MedicationStore.STATUS_SKIPPED) }
        }
        addView(skip, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
    }

    private fun markDose(row: DoseRow, status: String) {
        store.logDose(row.medication.id, row.scheduledAt, status)
        if (status == MedicationStore.STATUS_TAKEN) {
            store.adjustInventory(row.medication.id, -1)
        }
        ReminderScheduler.scheduleNext(activity, store.getMedication(row.medication.id))
        callbacks.onDoseChanged()
    }

    private fun doseRowsFor(date: LocalDate, medications: List<Medication>): List<DoseRow> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val logs = store.getDoseLogsBetween(start, end)
        return buildList {
            medications.forEach { medication ->
                medication.scheduledDoseTimes(date, zoneId).forEach { scheduledAt ->
                    add(
                        DoseRow(
                            medication,
                            scheduledAt,
                            logs[MedicationStore.doseKey(medication.id, scheduledAt)]
                        )
                    )
                }
            }
        }.sortedBy { it.scheduledAt }
    }

    private fun rowStatus(row: DoseRow): String = when (row.status) {
        MedicationStore.STATUS_TAKEN -> "Taken"
        MedicationStore.STATUS_SKIPPED -> "Skipped"
        else -> {
            val now = System.currentTimeMillis()
            when {
                row.scheduledAt < now - FIFTEEN_MINUTES -> "Due"
                row.scheduledAt <= now + THIRTY_MINUTES -> "Next"
                else -> "Upcoming"
            }
        }
    }

    private fun formatTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormatter)

    private fun matchWrapParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun plural(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"

    private companion object {
        const val FIFTEEN_MINUTES = 15 * 60_000L
        const val THIRTY_MINUTES = 30 * 60_000L
    }
}
