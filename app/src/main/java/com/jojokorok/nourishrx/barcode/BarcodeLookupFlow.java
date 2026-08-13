package com.jojokorok.nourishrx.barcode;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
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
            Toast.makeText(activity, "Camera access is off. Manual barcode lookup still works.", Toast.LENGTH_LONG).show();
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
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        TextView status = ui.text("", 15, barcodeStatusColor(), Typeface.BOLD);
        refreshBarcodeStatus(status);
        body.addView(barcodeAccessPanel(status));

        EditText barcodeField = ui.field("Barcode number", initialBarcode, InputType.TYPE_CLASS_NUMBER);
        body.addView(barcodeField);

        Button camera = ui.button("Use camera scanner", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        camera.setOnClickListener(view -> requestCameraAccess());
        LinearLayout.LayoutParams cameraParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        cameraParams.topMargin = ui.dp(12);
        body.addView(camera, cameraParams);

        Button premiumPlan = ui.button("Premium plan", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        premiumPlan.setOnClickListener(view -> premiumPlanAction.run());
        LinearLayout.LayoutParams premiumParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(44)
        );
        premiumParams.topMargin = ui.dp(8);
        body.addView(premiumPlan, premiumParams);

        body.addView(ui.fieldLabel("Manual lookup"));

        Button lookup = ui.button("Lookup barcode", NourishColors.GOLD, NourishColors.GOLD_SOFT);
        LinearLayout.LayoutParams lookupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46)
        );
        lookupParams.topMargin = ui.dp(12);
        body.addView(lookup, lookupParams);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Barcode lookup")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        lookup.setOnClickListener(view -> startBarcodeLookup(dialog, body, barcodeField, lookup, status));
        dialog.setOnShowListener(dialogInterface -> {
            if (autoLookup) {
                lookup.post(() -> startBarcodeLookup(dialog, body, barcodeField, lookup, status));
            } else {
                barcodeField.requestFocus();
            }
        });
        dialog.show();
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, cameraPermissionRequestCode);
        }
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void launchScanner() {
        try {
            activity.startActivityForResult(new Intent(activity, BarcodeScannerActivity.class), scannerRequestCode);
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
            return NourishColors.GREEN;
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
        panel.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        panel.setBackground(ui.rounded(NourishColors.CARD, NourishColors.BORDER, ui.dp(20)));

        TextView label = ui.text("Barcode access", 12, NourishColors.MUTED, Typeface.BOLD);
        panel.addView(label);

        status.setPadding(0, ui.dp(2), 0, ui.dp(4));
        panel.addView(status);

        TextView note = ui.text("Scan or type a UPC/EAN code to inspect nutrition facts before saving. Manual food entry stays available.", 12, NourishColors.MUTED, Typeface.BOLD);
        panel.addView(note);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = ui.dp(10);
        panel.setLayoutParams(params);
        return panel;
    }

    private void showBarcodeLimitDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("Barcode limit reached")
                .setMessage("Free barcode lookups are used up for this install. You can still add foods manually, or unlock premium for unlimited barcode scans once purchases are connected.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Premium plan", (dialog, which) -> premiumPlanAction.run())
                .show();
    }
}
