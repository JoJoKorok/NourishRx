package com.jojokorok.nourishrx.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jojokorok.nourishrx.R
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.data.Profile
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class AppShellFlow(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val zoneId: ZoneId,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun currentProfileId(): Long
        fun currentMode(): String
        fun currentTab(): String
        fun selectedProfile(): Profile
        fun profileAvatar(profile: Profile, sizeDp: Int, fallbackColor: Int, textSp: Int): View
        fun avatarWidthDp(profile: Profile, heightDp: Int): Int
        fun todayDoseCount(): Int
        fun alertsLabel(): String
        fun alertColor(): Int
        fun showQuickAdd(nutritionMode: Boolean)
        fun showProfiles()
        fun selectAbout()
        fun selectMode(mode: String)
        fun selectTab(tab: String)
        fun handleAlertsTap()
    }

    private data class HeaderStat(
        val value: String,
        val label: String,
        val color: Int
    )

    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

    fun render(): LinearLayout {
        val nutritionMode = callbacks.currentMode() == MODE_NUTRITION
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NourishColors.SURFACE)
            setPadding(
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.MD),
                0
            )
        }
        applySystemBarInsets(root)

        root.addView(headerPanel(nutritionMode))
        root.addView(tabRow())
        root.addView(headerStats(nutritionMode))

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(NourishSpacing.SM), 0, ui.dp(NourishSpacing.XL))
        }
        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        activity.setContentView(root)
        root.requestApplyInsets()
        return content
    }

    private fun headerPanel(nutritionMode: Boolean): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(brandBar())
        addView(profileRow(nutritionMode), matchWrapParams(topMargin = NourishSpacing.LG))
        addView(
            modeSwitchRow(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(56)
            ).apply {
                topMargin = ui.dp(NourishSpacing.MD)
            }
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = ui.dp(NourishSpacing.SM)
        }
    }

    private fun brandBar(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(48)

        val brand = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(activity).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }
        brand.addView(
            logo,
            LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)).apply {
                rightMargin = ui.dp(NourishSpacing.XS)
            }
        )
        brand.addView(ui.displayText("NourishRx", NourishTypography.BODY_LARGE, NourishColors.GREEN_DARK))
        addView(brand, LinearLayout.LayoutParams(0, ui.dp(48), 1f))

        val showingAbout = callbacks.currentTab() == TAB_ABOUT
        val about = ui.button(
            "About",
            if (showingAbout) NourishColors.ON_ACCENT else NourishColors.INK_SECONDARY,
            if (showingAbout) NourishColors.BLUE else Color.TRANSPARENT
        ).apply {
            setOnClickListener { callbacks.selectAbout() }
        }
        addView(
            about,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(48)).apply {
                rightMargin = ui.dp(NourishSpacing.XS)
            }
        )

        val alerts = ui.button(callbacks.alertsLabel(), callbacks.alertColor(), Color.TRANSPARENT).apply {
            setOnClickListener { callbacks.handleAlertsTap() }
        }
        addView(alerts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(48)))
    }

    private fun profileRow(nutritionMode: Boolean): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val profile = callbacks.selectedProfile()
        val identity = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = ui.dp(48)
            contentDescription = "Manage profile for ${profile.name}"
            setOnClickListener { callbacks.showProfiles() }
        }

        val avatarSize = 46
        val avatar = callbacks.profileAvatar(profile, avatarSize, NourishColors.GREEN, 16)
        identity.addView(
            avatar,
            LinearLayout.LayoutParams(
                ui.dp(callbacks.avatarWidthDp(profile, avatarSize)),
                ui.dp(avatarSize)
            ).apply {
                rightMargin = ui.dp(NourishSpacing.SM)
            }
        )

        val titleGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val headline = ui.displayText(
            "${greeting()}, ${profile.name}",
            NourishTypography.TITLE,
            NourishColors.INK
        ).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        titleGroup.addView(headline)
        titleGroup.addView(
            ui.text(
                LocalDate.now(zoneId).format(dateFormatter),
                NourishTypography.LABEL,
                NourishColors.MUTED,
                Typeface.NORMAL
            )
        )
        identity.addView(titleGroup, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val disclosure = ImageView(activity).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            imageAlpha = 120
            contentDescription = null
        }
        identity.addView(disclosure, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)))
        addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val quickAdd = ui.button(
            if (nutritionMode) "+ Log" else "+ Med",
            NourishColors.ON_ACCENT,
            NourishColors.GREEN
        ).apply {
            setOnClickListener { callbacks.showQuickAdd(nutritionMode) }
        }
        addView(
            quickAdd,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(48)).apply {
                leftMargin = ui.dp(NourishSpacing.SM)
            }
        )
    }

    private fun headerStats(nutritionMode: Boolean): LinearLayout {
        val stats = if (nutritionMode) nutritionStats() else medicationStats()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.SM)
            )
            background = ui.rounded(
                NourishColors.CARD,
                NourishColors.BORDER,
                ui.dp(NourishShapes.RADIUS_CARD)
            )
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    addView(
                        View(activity).apply { setBackgroundColor(NourishColors.DIVIDER) },
                        LinearLayout.LayoutParams(ui.dp(1), ui.dp(44))
                    )
                }
                addView(statCell(stat), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            layoutParams = matchWrapParams(topMargin = NourishSpacing.SM)
        }
    }

    private fun medicationStats(): List<HeaderStat> {
        val medications = store.getAllMedications(callbacks.currentProfileId())
        val lowCount = medications.count { it.isLowStock() }
        val doses = callbacks.todayDoseCount()
        return listOf(
            HeaderStat(doses.toString(), if (doses == 1) "dose" else "doses", NourishColors.GREEN),
            HeaderStat(
                medications.size.toString(),
                if (medications.size == 1) "med" else "meds",
                NourishColors.BLUE
            ),
            HeaderStat(lowCount.toString(), if (lowCount == 1) "refill" else "refills", NourishColors.GOLD)
        )
    }

    private fun nutritionStats(): List<HeaderStat> {
        val today = LocalDate.now(zoneId)
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val foodLogCount = store.getMealFoodLogs(callbacks.currentProfileId(), start, end).size
        val waterOunces = store.getWaterOunces(callbacks.currentProfileId(), start, end)
        val weights = store.getWeightEntries(callbacks.currentProfileId(), 1)
        return listOf(
            HeaderStat(
                foodLogCount.toString(),
                if (foodLogCount == 1) "food log" else "food logs",
                NourishColors.GREEN
            ),
            HeaderStat(waterOunces.toString(), "oz water", NourishColors.BLUE),
            HeaderStat(
                if (weights.isEmpty()) "--" else formatValue(weights[0].pounds),
                if (weights.isEmpty()) "weight" else "lb latest",
                NourishColors.GOLD
            )
        )
    }

    private fun statCell(stat: HeaderStat): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(ui.displayText(stat.value, NourishTypography.TITLE, stat.color))
        addView(ui.text(stat.label, NourishTypography.CAPTION, NourishColors.MUTED, Typeface.NORMAL))
    }

    private fun modeSwitchRow(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(
            ui.dp(NourishSpacing.XXS),
            ui.dp(NourishSpacing.XXS),
            ui.dp(NourishSpacing.XXS),
            ui.dp(NourishSpacing.XXS)
        )
        background = ui.rounded(
            NourishColors.SURFACE_SUBTLE,
            NourishColors.BORDER,
            ui.dp(NourishShapes.RADIUS_CONTROL)
        )
        addView(modeButton("Medication", MODE_MEDICATION))
        addView(modeButton("Nutrition", MODE_NUTRITION))
    }

    private fun modeButton(label: String, mode: String): Button {
        val selected = callbacks.currentMode() == mode
        return ui.button(
            label,
            if (selected) NourishColors.ON_ACCENT else NourishColors.INK_SECONDARY,
            if (selected) NourishColors.GREEN else Color.TRANSPARENT
        ).apply {
            setOnClickListener { callbacks.selectMode(mode) }
            layoutParams = LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply {
                leftMargin = ui.dp(2)
                rightMargin = ui.dp(2)
            }
        }
    }

    private fun tabRow(): LinearLayout {
        val tabs = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (callbacks.currentMode() == MODE_NUTRITION) {
            tabs.addView(tabItem("Today", "nutrition_today"))
            tabs.addView(tabItem("Meals", "nutrition_meals"))
            tabs.addView(tabItem("Saved", "nutrition_saved"))
            tabs.addView(tabItem("Foods", "nutrition_foods"))
            tabs.addView(tabItem("Body", "nutrition_body"))
        } else {
            tabs.addView(tabItem("Today", "today"))
            tabs.addView(tabItem("Medications", "meds"))
            tabs.addView(tabItem("Inventory", "stock"))
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                tabs,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48))
            )
            addView(
                View(activity).apply { setBackgroundColor(NourishColors.DIVIDER) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1))
            )
        }
    }

    private fun tabItem(label: String, tab: String): LinearLayout {
        val selected = callbacks.currentTab() == tab
        val labelView = ui.text(
            label,
            NourishTypography.CAPTION,
            if (selected) NourishColors.GREEN_DARK else NourishColors.INK_SECONDARY,
            if (selected) Typeface.BOLD else Typeface.NORMAL
        ).apply {
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(ui.dp(NourishSpacing.XXS), 0, ui.dp(NourishSpacing.XXS), 0)
        }
        val indicator = View(activity).apply {
            setBackgroundColor(if (selected) NourishColors.GREEN else Color.TRANSPARENT)
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            isSelected = selected
            contentDescription = if (selected) "$label tab, selected" else "$label tab"
            val selectableBackground = TypedValue()
            if (activity.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    selectableBackground,
                    true
                )
            ) {
                setBackgroundResource(selectableBackground.resourceId)
            }
            setOnClickListener { callbacks.selectTab(tab) }
            addView(labelView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                indicator,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(3)).apply {
                    leftMargin = ui.dp(NourishSpacing.SM)
                    rightMargin = ui.dp(NourishSpacing.SM)
                }
            )
            layoutParams = LinearLayout.LayoutParams(0, ui.dp(48), 1f)
        }
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarInsets(root: View) {
        root.setOnApplyWindowInsetsListener { view, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                topInset = systemBars.top
                bottomInset = systemBars.bottom
            } else {
                topInset = insets.systemWindowInsetTop
                bottomInset = insets.systemWindowInsetBottom
            }
            view.setPadding(
                ui.dp(NourishSpacing.MD),
                topInset + ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.MD),
                bottomInset
            )
            insets
        }
    }

    private fun greeting(): String = when (LocalTime.now(zoneId).hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    private fun matchWrapParams(topMargin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun formatValue(value: Float): String {
        val rounded = value.roundToInt()
        return if (abs(value - rounded) < 0.05f) {
            rounded.toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private companion object {
        const val MODE_MEDICATION = "medication"
        const val MODE_NUTRITION = "nutrition"
        const val TAB_ABOUT = "about"
    }
}
