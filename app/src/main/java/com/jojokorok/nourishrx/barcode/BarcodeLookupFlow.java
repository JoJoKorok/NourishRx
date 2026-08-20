package com.jojokorok.nourishrx.barcode;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.BarcodeScannerActivity;
import com.jojokorok.nourishrx.api.OpenFoodFactsClient;
import com.jojokorok.nourishrx.data.NutritionFood;
import com.jojokorok.nourishrx.premium.PremiumManager;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishShapes;
import com.jojokorok.nourishrx.ui.NourishSpacing;
import com.jojokorok.nourishrx.ui.NourishTypography;
import com.jojokorok.nourishrx.ui.NourishUi;

public class BarcodeLookupFlow {
    public interface ProfileIdProvider {
        long currentProfileId();
    }

    public interface InspectionRenderer {
        void render(AlertDialog dialog, LinearLayout body, NutritionFood food, String sourceLine);
    }

    private final Activity activity;
    private final NourishUi ui;
    private final PremiumManager premiumManager;
    private final ProfileIdProvider profileIdProvider;
    private final InspectionRenderer inspectionRenderer;
    private final Runnable premiumPlanAction;
    private final int cameraPermissionRequestCode;
    private final int scannerRequestCode;

    public BarcodeLookupFlow(
            Activity activity,
            NourishUi ui,
            PremiumManager premiumManager,
            ProfileIdProvider profileIdProvider,
            InspectionRenderer inspectionRenderer,
            Runnable premiumPlanAction,
            int cameraPermissionRequestCode,
            int scannerRequestCode
    ) {
        this.activity = activity;
        this.ui = ui;
        this.premiumManager = premiumManager;
        this.profileIdProvider = profileIdProvider;
        this.inspectionRenderer = inspectionRenderer;
        this.premiumPlanAction = premiumPlanAction;
        this.cameraPermissionRequestCode = cameraPermissionRequestCode;
        this.scannerRequestCode = scannerRequestCode;
    }

    public void showEntryPoint() {
        showLookupDialog("", false);
    }

    public void handleCameraPermissionResult(boolean granted) {
        if (granted) {
            Toast.makeText(activity, "Camera access enabled for barcode scanning.", Toast.LENGTH_SHORT).show();
            launchScanner();
        } else {
            Toast.makeText(
                    activity,
                    "Camera access is off. Manual barcode lookup still works.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    public void handleScannerResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            String barcode = data.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE);
            if (!TextUtils.isEmpty(barcode)) {
                showLookupDialog(barcode.trim(), true);
            }
        }
    }

    private void showLookupDialog(String initialBarcode, boolean autoLookup) {
        LinearLayout body = dialogBody();
        body.addView(dialogHeader(
                "Barcode lookup",
                "Scan a package or enter its UPC/EAN number to review the food before saving."
        ));

        TextView status = ui.text(
                "",
                NourishTypography.LABEL,
                barcodeStatusColor(),
                Typeface.BOLD
        );
        refreshBarcodeStatus(status);
        body.addView(barcodeAccessPanel(status), wrapParams(NourishSpacing.MD));

        body.addView(sectionHeader(
                "Scan with camera",
                "Keep the barcode centered inside the guide until it is detected."
        ));
        Button camera = ui.button("Open camera scanner", NourishColors.ON_ACCENT, NourishColors.GREEN);
        camera.setSingleLine(true);
        camera.setOnClickListener(view -> requestCameraAccess());
        body.addView(camera, matchParams(48, NourishSpacing.SM));

        body.addView(sectionHeader(
                "Enter barcode manually",
                "Use the digits printed beneath the barcode when scanning is not convenient."
        ));
        body.addView(ui.fieldLabel("UPC or EAN number"));
        EditText barcodeField = ui.field(
                "Example: 012345678905",
                initialBarcode,
                InputType.TYPE_CLASS_NUMBER
        );
        body.addView(barcodeField);

        Button lookup = ui.button("Look up barcode", NourishColors.ON_ACCENT, NourishColors.BLUE);
        lookup.setSingleLine(true);
        body.addView(lookup, matchParams(48, NourishSpacing.SM));

        Button premiumPlan = ui.button("View premium access", NourishColors.BLUE, Color.TRANSPARENT);
        premiumPlan.setSingleLine(true);
        premiumPlan.setOnClickListener(view -> premiumPlanAction.run());
        body.addView(premiumPlan, matchParams(44, NourishSpacing.XS));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        lookup.setOnClickListener(view -> startBarcodeLookup(
                dialog,
                body,
                barcodeField,
                lookup,
                status
        ));
        dialog.setOnShowListener(dialogInterface -> {
            styleDialogActions(dialog);
            if (autoLookup) {
                lookup.post(() -> startBarcodeLookup(dialog, body, barcodeField, lookup, status));
            } else {
                barcodeField.requestFocus();
            }
        });
        dialog.show();
        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void requestCameraAccess() {
        if (!premiumManager.canUseBarcodeLookup()) {
            showBarcodeLimitDialog();
            return;
        }

        if (hasCameraPermission()) {
            launchScanner();
            return;
        }

        activity.requestPermissions(
                new String[]{Manifest.permission.CAMERA},
                cameraPermissionRequestCode
        );
    }

    private boolean hasCameraPermission() {
        return activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void launchScanner() {
        try {
            activity.startActivityForResult(
                    new Intent(activity, BarcodeScannerActivity.class),
                    scannerRequestCode
            );
        } catch (Exception exception) {
            Toast.makeText(activity, "Camera scanner could not open.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBarcodeLookup(
            AlertDialog dialog,
            LinearLayout body,
            EditText barcodeField,
            Button lookup,
            TextView status
    ) {
        String code = barcodeField.getText().toString().replaceAll("[^0-9]", "");
        if (code.length() < 6) {
            barcodeField.setError("Enter a barcode");
            return;
        }

        if (!premiumManager.canUseBarcodeLookup()) {
            showBarcodeLimitDialog();
            refreshBarcodeStatus(status);
            return;
        }

        barcodeField.setText(code);
        lookup.setEnabled(false);
        status.setText("Looking up barcode...");
        status.setTextColor(NourishColors.BLUE);

        new Thread(() -> {
            try {
                NutritionFood food = new OpenFoodFactsClient().fetchNutritionFood(
                        code,
                        profileIdProvider.currentProfileId()
                );
                activity.runOnUiThread(() -> {
                    premiumManager.recordBarcodeLookup();
                    inspectionRenderer.render(
                            dialog,
                            body,
                            food,
                            "OpenFoodFacts barcode " + code
                    );
                });
            } catch (Exception exception) {
                activity.runOnUiThread(() -> {
                    lookup.setEnabled(true);
                    refreshBarcodeStatus(status);
                    Toast.makeText(activity, exception.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String barcodeStatusText() {
        if (premiumManager.isPremiumActive()) {
            return "Premium barcode scans are unlimited.";
        }

        int remaining = premiumManager.barcodeLookupsRemaining();
        if (remaining == 0) {
            return "Free barcode lookups used.";
        }

        String lookupLabel = remaining == 1 ? "lookup" : "lookups";
        return remaining + " free barcode " + lookupLabel + " remaining.";
    }

    private int barcodeStatusColor() {
        if (premiumManager.isPremiumActive()) {
            return NourishColors.GREEN_DARK;
        }

        int remaining = premiumManager.barcodeLookupsRemaining();
        if (remaining == 0) {
            return NourishColors.CORAL;
        }
        if (remaining <= 2) {
            return NourishColors.GOLD;
        }
        return NourishColors.INK;
    }

    private void refreshBarcodeStatus(TextView status) {
        status.setText(barcodeStatusText());
        status.setTextColor(barcodeStatusColor());
    }

    private View barcodeAccessPanel(TextView status) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM),
                ui.dp(NourishSpacing.SM)
        );
        panel.setBackground(ui.rounded(
                NourishColors.CARD_SUBTLE,
                NourishColors.BORDER,
                ui.dp(NourishShapes.RADIUS_CONTROL)
        ));

        panel.addView(ui.text(
                "Barcode access",
                NourishTypography.CAPTION,
                NourishColors.MUTED,
                Typeface.BOLD
        ));
        panel.addView(status, wrapParams(NourishSpacing.XXS));
        panel.addView(
                ui.text(
                        "Manual food entry remains available when no lookups are left.",
                        NourishTypography.CAPTION,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                ),
                wrapParams(NourishSpacing.XXS)
        );
        return panel;
    }

    private View dialogHeader(String title, String subtitle) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, 0, ui.dp(NourishSpacing.XS));
        header.addView(ui.displayText(title, NourishTypography.TITLE, NourishColors.INK));
        header.addView(
                ui.text(
                        subtitle,
                        NourishTypography.LABEL,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                ),
                wrapParams(NourishSpacing.XXS)
        );
        return header;
    }

    private View sectionHeader(String title, String subtitle) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, ui.dp(NourishSpacing.LG), 0, 0);

        View divider = new View(activity);
        divider.setBackgroundColor(NourishColors.DIVIDER);
        section.addView(divider, matchParams(1, 0));
        section.addView(
                ui.displayText(title, NourishTypography.BODY_LARGE, NourishColors.INK),
                wrapParams(NourishSpacing.MD)
        );
        section.addView(
                ui.text(
                        subtitle,
                        NourishTypography.CAPTION,
                        NourishColors.MUTED,
                        Typeface.NORMAL
                ),
                wrapParams(NourishSpacing.XXS)
        );
        return section;
    }

    private LinearLayout dialogBody() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.MD),
                ui.dp(NourishSpacing.LG),
                ui.dp(NourishSpacing.SM)
        );
        return body;
    }

    private void styleDialogActions(AlertDialog dialog) {
        Button close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        close.setTextColor(NourishColors.INK_SECONDARY);
        close.setTypeface(Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL));
    }

    private LinearLayout.LayoutParams matchParams(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(height)
        );
        params.topMargin = ui.dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams wrapParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = ui.dp(topMargin);
        return params;
    }

    private void showBarcodeLimitDialog() {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Barcode limit reached")
                .setMessage("Free barcode lookups are used up for this install. You can still add foods manually, or unlock premium for unlimited barcode scans once purchases are connected.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Premium plan", (ignored, which) -> premiumPlanAction.run())
                .create();
        dialog.setOnShowListener(ignored -> {
            styleDialogActions(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(NourishColors.BLUE);
        });
        dialog.show();
    }
}
