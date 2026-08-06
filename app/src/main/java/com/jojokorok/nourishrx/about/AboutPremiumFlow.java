package com.jojokorok.nourishrx.about;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.premium.PremiumFeature;
import com.jojokorok.nourishrx.premium.PremiumManager;
import com.jojokorok.nourishrx.premium.PremiumTier;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

public class AboutPremiumFlow {
    private static final String GITHUB_PROFILE_URL = "https://github.com/JoJoKorok";

    private final Activity activity;
    private final NourishUi ui;
    private final PremiumManager premiumManager;

    public AboutPremiumFlow(Activity activity, NourishUi ui, PremiumManager premiumManager) {
        this.activity = activity;
        this.ui = ui;
        this.premiumManager = premiumManager;
    }

    public void renderAbout(LinearLayout content) {
        content.addView(sectionTitle("About", "Created by Joseph Bekele"));

        LinearLayout hero = ui.card();
        hero.setBackground(ui.roundedGradient(
                new int[]{
                        Color.rgb(229, 244, 238),
                        Color.rgb(236, 242, 255),
                        Color.rgb(255, 251, 239)
                },
                ui.dp(24)
        ));

        TextView mark = ui.text("NR", 24, Color.WHITE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(ui.rounded(NourishColors.GREEN, Color.TRANSPARENT, ui.dp(28)));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(ui.dp(58), ui.dp(58));
        markParams.bottomMargin = ui.dp(12);
        hero.addView(mark, markParams);

        TextView title = ui.displayText("NourishRx", 32, NourishColors.INK);
        hero.addView(title);
        TextView byline = ui.text("Created by Joseph Bekele", 16, NourishColors.GREEN, Typeface.BOLD);
        byline.setPadding(0, ui.dp(4), 0, 0);
        hero.addView(byline);
        TextView summary = ui.text(
                "A local-first Android organizer for medication scheduling, nutrition logging, water intake, weight tracking, and shared profiles.",
                15,
                NourishColors.MUTED,
                Typeface.BOLD
        );
        summary.setPadding(0, ui.dp(12), 0, 0);
        hero.addView(summary);
        content.addView(hero);

        LinearLayout project = ui.card();
        project.addView(ui.text("Project", 19, NourishColors.INK, Typeface.BOLD));
        project.addView(infoLine("License", "MIT License"));
        project.addView(infoLine("Privacy", "Local device storage; OpenFoodFacts is contacted only when searching online foods."));
        project.addView(infoLine("GitHub", "github.com/JoJoKorok"));

        Button github = ui.button("Open GitHub", Color.WHITE, NourishColors.BLUE);
        github.setOnClickListener(view -> openExternalLink(GITHUB_PROFILE_URL));
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        githubParams.topMargin = ui.dp(14);
        project.addView(github, githubParams);
        content.addView(project);

        LinearLayout plan = ui.card();
        plan.addView(ui.text("Plan", 19, NourishColors.INK, Typeface.BOLD));
        plan.addView(infoLine("Current access", premiumManager.planLabel()));
        plan.addView(infoLine("Barcode lookups", premiumManager.barcodeAccessLabel()));
        plan.addView(infoLine("Premium model", premiumManager.premiumProductLabel() + " - " + premiumManager.purchaseModelLabel()));
        plan.addView(infoLine("Future sync", "Cloud backup and cross-device sync will stay separate from the one-time unlock."));

        Button premium = ui.button("View premium plan", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        premium.setOnClickListener(view -> showPremiumOverviewDialog());
        LinearLayout.LayoutParams premiumParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        premiumParams.topMargin = ui.dp(14);
        plan.addView(premium, premiumParams);

        if (!premiumManager.isPremiumActive()) {
            Button unlock = ui.button("Unlock premium", Color.WHITE, NourishColors.GREEN);
            unlock.setOnClickListener(view -> showPremiumPurchaseUnavailableDialog());
            LinearLayout.LayoutParams unlockParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(46)
            );
            unlockParams.topMargin = ui.dp(10);
            plan.addView(unlock, unlockParams);
        }

        content.addView(plan);
    }

    public boolean requirePremium(PremiumFeature feature) {
        if (premiumManager.canUse(feature)) {
            return true;
        }

        showPremiumFeatureDialog(feature);
        return false;
    }

    public void showPremiumFeatureDialog(PremiumFeature feature) {
        String accessNote = feature.tier == PremiumTier.ONE_TIME_PREMIUM
                ? "This is planned for NourishRx Premium, a one-time purchase. Google Play Billing is not connected in this build yet."
                : "This is planned for a future sync subscription, separate from the one-time Premium unlock.";

        new AlertDialog.Builder(activity)
                .setTitle(feature.title)
                .setMessage(feature.description + "\n\n" + accessNote)
                .setNegativeButton("Close", null)
                .setPositiveButton("Premium plan", (dialog, which) -> showPremiumOverviewDialog())
                .show();
    }

    public void showPremiumOverviewDialog() {
        StringBuilder message = new StringBuilder();
        message.append("Current access: ").append(premiumManager.planLabel()).append("\n");
        message.append("Barcode lookups: ").append(premiumManager.barcodeAccessLabel()).append("\n");
        message.append("Premium model: ")
                .append(premiumManager.premiumProductLabel())
                .append(" - ")
                .append(premiumManager.purchaseModelLabel())
                .append("\n\n");
        appendPremiumFeatureGroup(message, PremiumTier.ONE_TIME_PREMIUM);
        message.append("\n");
        appendPremiumFeatureGroup(message, PremiumTier.SYNC_SUBSCRIPTION);
        message.append("\nPurchases are not available until Google Play Billing is added.");

        new AlertDialog.Builder(activity)
                .setTitle("NourishRx Premium")
                .setMessage(message.toString())
                .setNegativeButton("Close", null)
                .setPositiveButton("Unlock premium", (dialog, which) -> showPremiumPurchaseUnavailableDialog())
                .show();
    }

    private View infoLine(String label, String value) {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, ui.dp(12), 0, 0);

        line.addView(ui.text(label, 13, NourishColors.MUTED, Typeface.BOLD));
        TextView valueView = ui.text(value, 15, NourishColors.INK, Typeface.BOLD);
        valueView.setPadding(0, ui.dp(2), 0, 0);
        line.addView(valueView);
        return line;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setPadding(0, ui.dp(8), 0, ui.dp(8));

        View marker = new View(activity);
        marker.setBackground(ui.rounded(NourishColors.GREEN, Color.TRANSPARENT, ui.dp(3)));
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(ui.dp(5), ui.dp(42));
        markerParams.rightMargin = ui.dp(10);
        group.addView(marker, markerParams);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(ui.text(title, 21, NourishColors.INK, Typeface.BOLD));
        labels.addView(ui.text(subtitle, 13, NourishColors.MUTED, Typeface.BOLD));
        group.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return group;
    }

    private void showPremiumPurchaseUnavailableDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("Unlock premium")
                .setMessage(premiumManager.purchaseUnavailableMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private void appendPremiumFeatureGroup(StringBuilder message, PremiumTier tier) {
        message.append(tier.label).append(":\n");
        for (PremiumFeature feature : PremiumFeature.values()) {
            if (feature.tier == tier) {
                message.append("- ").append(feature.title).append(": ").append(feature.description).append("\n");
            }
        }
    }

    private void openExternalLink(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            activity.startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(activity, "No browser is available for this link.", Toast.LENGTH_SHORT).show();
        }
    }
}
