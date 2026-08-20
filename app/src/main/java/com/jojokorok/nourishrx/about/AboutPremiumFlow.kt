package com.jojokorok.nourishrx.about

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jojokorok.nourishrx.R
import com.jojokorok.nourishrx.premium.PremiumFeature
import com.jojokorok.nourishrx.premium.PremiumManager
import com.jojokorok.nourishrx.premium.PremiumTier
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishShapes
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi

class AboutPremiumFlow(
    private val activity: Activity,
    private val ui: NourishUi,
    private val premiumManager: PremiumManager
) {
    fun renderAbout(content: LinearLayout) {
        content.addView(
            screenHeader(
                "About NourishRx",
                "Project details, privacy, credits, and access"
            )
        )
        content.addView(brandPanel())
        content.addView(projectCard())
        content.addView(accessCard())
    }

    fun requirePremium(feature: PremiumFeature): Boolean {
        if (premiumManager.canUse(feature)) return true
        showPremiumFeatureDialog(feature)
        return false
    }

    fun showPremiumFeatureDialog(feature: PremiumFeature) {
        val accessNote = if (feature.tier == PremiumTier.ONE_TIME_PREMIUM) {
            "This feature is planned for the one-time NourishRx Premium unlock. Google Play Billing is not connected in this build yet."
        } else {
            "This feature is planned for a future sync subscription, separate from the one-time Premium unlock."
        }
        val body = dialogBody().apply {
            addView(dialogHeader(feature.title, feature.description))
            addView(messagePanel(accessNote), matchWrapParams(topMargin = NourishSpacing.MD))
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(body)
            .setNegativeButton("Close", null)
            .setPositiveButton("View plans") { _, _ -> showPremiumOverviewDialog() }
            .create()
        dialog.setOnShowListener { styleDialogActions(dialog) }
        dialog.show()
    }

    fun showPremiumOverviewDialog() {
        val body = dialogBody().apply {
            addView(
                dialogHeader(
                    "NourishRx Premium",
                    "Compare the planned one-time unlock with future sync access."
                )
            )
            addView(premiumAccessSummary(), matchWrapParams(topMargin = NourishSpacing.MD))
            addView(
                featureGroup(
                    "One-time Premium",
                    "Planned as a single Google Play purchase.",
                    PremiumTier.ONE_TIME_PREMIUM
                )
            )
            addView(
                featureGroup(
                    "Future sync subscription",
                    "Reserved for services with ongoing cloud costs.",
                    PremiumTier.SYNC_SUBSCRIPTION
                )
            )
            addView(
                messagePanel("Purchases are not available until Google Play Billing is connected."),
                matchWrapParams(topMargin = NourishSpacing.LG)
            )
        }
        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
            addView(body)
        }
        val builder = AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton("Close", null)
        if (!premiumManager.isPremiumActive()) {
            builder.setPositiveButton("Availability") { _, _ ->
                showPremiumPurchaseUnavailableDialog()
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener { styleDialogActions(dialog) }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun brandPanel(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ui.dp(NourishSpacing.SM), 0, ui.dp(NourishSpacing.XS))

        val identity = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(activity).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "NourishRx app icon"
        }
        identity.addView(
            logo,
            LinearLayout.LayoutParams(ui.dp(58), ui.dp(58)).apply {
                rightMargin = ui.dp(NourishSpacing.MD)
            }
        )
        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.displayText("NourishRx", NourishTypography.SCREEN_TITLE, NourishColors.INK))
            addView(
                ui.text(
                    "Created by Joseph Bekele",
                    NourishTypography.LABEL,
                    NourishColors.GREEN_DARK,
                    Typeface.BOLD
                ),
                matchWrapParams(topMargin = NourishSpacing.XXS)
            )
        }
        identity.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(identity)
        addView(
            ui.text(
                "A local-first Android organizer for medication scheduling, nutrition logging, water intake, weight tracking, and shared profiles.",
                NourishTypography.BODY,
                NourishColors.INK_SECONDARY,
                Typeface.NORMAL
            ),
            matchWrapParams(topMargin = NourishSpacing.MD)
        )
    }

    private fun projectCard(): LinearLayout = flatCard().apply {
        addView(cardHeader("Project", "Source, license, and data handling"))
        addView(infoRow("License", "MIT License"))
        addView(
            infoRow(
                "Privacy",
                "Records stay on this device. OpenFoodFacts is contacted only for online food searches and barcode lookups."
            )
        )
        addView(infoRow("GitHub", "github.com/JoJoKorok"))
        addView(
            ui.button("Open GitHub profile", NourishColors.BLUE, Color.TRANSPARENT).apply {
                setSingleLine(true)
                setOnClickListener { openExternalLink(GITHUB_PROFILE_URL) }
            },
            matchParams(height = 46, topMargin = NourishSpacing.MD)
        )
    }

    private fun accessCard(): LinearLayout = flatCard().apply {
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                cardHeader("Access", "Free today, with clearly separated future plans"),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(planBadge(), wrapWrapParams(startMargin = NourishSpacing.SM))
        }
        addView(top)
        addView(infoRow("Barcode lookups", premiumManager.barcodeAccessLabel()))
        addView(
            infoRow(
                "Premium model",
                "${premiumManager.premiumProductLabel()} - ${premiumManager.purchaseModelLabel()}"
            )
        )
        addView(
            infoRow(
                "Future sync",
                "Cloud backup and cross-device sync remain separate from the one-time unlock."
            )
        )
        addView(
            ui.button("Compare plans", NourishColors.BLUE, Color.TRANSPARENT).apply {
                setSingleLine(true)
                setOnClickListener { showPremiumOverviewDialog() }
            },
            matchParams(height = 46, topMargin = NourishSpacing.MD)
        )
        if (!premiumManager.isPremiumActive()) {
            addView(
                ui.button("Premium availability", NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                    setSingleLine(true)
                    setOnClickListener { showPremiumPurchaseUnavailableDialog() }
                },
                matchParams(height = 46, topMargin = NourishSpacing.XS)
            )
        }
    }

    private fun premiumAccessSummary(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            ui.dp(NourishSpacing.SM),
            ui.dp(NourishSpacing.SM),
            ui.dp(NourishSpacing.SM),
            ui.dp(NourishSpacing.SM)
        )
        background = ui.rounded(
            NourishColors.CARD_SUBTLE,
            NourishColors.BORDER,
            ui.dp(NourishShapes.RADIUS_CONTROL)
        )
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                ui.text(
                    "Current access",
                    NourishTypography.CAPTION,
                    NourishColors.MUTED,
                    Typeface.BOLD
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(planBadge())
        }
        addView(top)
        addView(
            ui.text(
                premiumManager.barcodeAccessLabel(),
                NourishTypography.LABEL,
                NourishColors.INK_SECONDARY,
                Typeface.NORMAL
            ),
            matchWrapParams(topMargin = NourishSpacing.XS)
        )
    }

    private fun featureGroup(
        title: String,
        subtitle: String,
        tier: PremiumTier
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ui.dp(NourishSpacing.LG), 0, 0)
        addView(divider())
        addView(
            ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK),
            matchWrapParams(topMargin = NourishSpacing.MD)
        )
        addView(
            ui.text(
                subtitle,
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.NORMAL
            ),
            matchWrapParams(topMargin = NourishSpacing.XXS)
        )
        PremiumFeature.values()
            .filter { feature -> feature.tier == tier }
            .forEach { feature -> addView(featureRow(feature)) }
    }

    private fun featureRow(feature: PremiumFeature): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ui.dp(NourishSpacing.SM), 0, ui.dp(NourishSpacing.XS))
        addView(
            ui.text(
                feature.title,
                NourishTypography.BODY,
                NourishColors.INK,
                Typeface.BOLD
            )
        )
        addView(
            ui.text(
                feature.description,
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.NORMAL
            ),
            matchWrapParams(topMargin = NourishSpacing.XXS)
        )
    }

    private fun infoRow(label: String, value: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, ui.dp(NourishSpacing.SM), 0, 0)
        addView(divider())
        addView(
            ui.text(
                label,
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.BOLD
            ),
            matchWrapParams(topMargin = NourishSpacing.SM)
        )
        addView(
            ui.text(
                value,
                NourishTypography.LABEL,
                NourishColors.INK_SECONDARY,
                Typeface.NORMAL
            ),
            matchWrapParams(topMargin = NourishSpacing.XXS)
        )
    }

    private fun showPremiumPurchaseUnavailableDialog() {
        val body = dialogBody().apply {
            addView(
                dialogHeader(
                    "Premium availability",
                    premiumManager.purchaseUnavailableMessage()
                )
            )
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(body)
            .setPositiveButton("OK", null)
            .create()
        dialog.setOnShowListener { styleDialogActions(dialog) }
        dialog.show()
    }

    private fun screenHeader(title: String, subtitle: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(NourishSpacing.XS), 0, ui.dp(NourishSpacing.XS))
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

    private fun cardHeader(title: String, subtitle: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
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

    private fun flatCard(): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
    }

    private fun planBadge(): TextView {
        val premium = premiumManager.isPremiumActive()
        return ui.text(
            premiumManager.planLabel(),
            NourishTypography.CAPTION,
            if (premium) NourishColors.GREEN_DARK else NourishColors.BLUE,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            setPadding(
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.XXS),
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.XXS)
            )
            background = ui.rounded(
                if (premium) NourishColors.GREEN_SOFT else NourishColors.BLUE_SOFT,
                Color.TRANSPARENT,
                ui.dp(NourishShapes.RADIUS_CONTROL)
            )
        }
    }

    private fun messagePanel(message: String): TextView =
        ui.text(
            message,
            NourishTypography.LABEL,
            NourishColors.INK_SECONDARY,
            Typeface.NORMAL
        ).apply {
            setPadding(
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM)
            )
            background = ui.rounded(
                NourishColors.CARD_SUBTLE,
                NourishColors.BORDER,
                ui.dp(NourishShapes.RADIUS_CONTROL)
            )
        }

    private fun dialogBody(): LinearLayout = LinearLayout(activity).apply {
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
                matchWrapParams(topMargin = NourishSpacing.XXS)
            )
        }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(NourishColors.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1))
    }

    private fun styleDialogActions(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(NourishColors.GREEN)
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(NourishColors.INK_SECONDARY)
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL)
        }
    }

    private fun openExternalLink(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(activity, "No browser is available for this link.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun matchParams(height: Int, topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(height)).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun matchWrapParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun wrapWrapParams(startMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = ui.dp(startMargin)
        }

    companion object {
        private const val GITHUB_PROFILE_URL = "https://github.com/JoJoKorok"
    }
}
