package com.jojokorok.nourishrx;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.List;

public class BarcodeScannerActivity extends Activity {
    public static final String EXTRA_BARCODE = "barcode";

    private static final int COLOR_BACKGROUND = Color.rgb(22, 28, 34);
    private static final int COLOR_PANEL = Color.rgb(244, 248, 244);
    private static final int COLOR_INK = Color.rgb(31, 38, 46);
    private static final int COLOR_MUTED = Color.rgb(92, 103, 110);
    private static final int COLOR_GREEN = Color.rgb(33, 137, 108);

    private TextureView previewView;
    private TextView statusView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private BarcodeScanner barcodeScanner;
    private String cameraId;
    private int sensorOrientation;
    private boolean frontFacing;
    private boolean processingFrame;
    private boolean finished;
    private Size previewSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_CODABAR,
                        Barcode.FORMAT_ITF
                )
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        setContentView(scannerContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (previewView.isAvailable()) {
            openCamera();
        } else {
            previewView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopCameraThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (barcodeScanner != null) {
            barcodeScanner.close();
            barcodeScanner = null;
        }
        super.onDestroy();
    }

    private View scannerContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        FrameLayout previewPanel = new FrameLayout(this);
        previewView = new TextureView(this);
        previewPanel.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView frame = new TextView(this);
        GradientDrawable frameDrawable = new GradientDrawable();
        frameDrawable.setColor(Color.TRANSPARENT);
        frameDrawable.setStroke(dp(3), Color.argb(225, 255, 255, 255));
        frameDrawable.setCornerRadius(dp(22));
        frame.setBackground(frameDrawable);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(dp(250), dp(160));
        frameParams.gravity = Gravity.CENTER;
        previewPanel.addView(frame, frameParams);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(previewPanel, previewParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(18), dp(14), dp(18), dp(18));
        GradientDrawable controlsBackground = new GradientDrawable();
        controlsBackground.setColor(COLOR_PANEL);
        controlsBackground.setCornerRadii(new float[]{
                dp(26), dp(26), dp(26), dp(26),
                0, 0, 0, 0
        });
        controls.setBackground(controlsBackground);

        TextView title = text("Scan barcode", 22, COLOR_INK, Typeface.BOLD);
        controls.addView(title);

        statusView = text("Center a UPC or EAN barcode inside the frame.", 14, COLOR_MUTED, Typeface.BOLD);
        statusView.setPadding(0, dp(6), 0, dp(14));
        controls.addView(statusView);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(14);
        cancel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable cancelBackground = new GradientDrawable();
        cancelBackground.setColor(COLOR_GREEN);
        cancelBackground.setCornerRadius(dp(18));
        cancel.setBackground(cancelBackground);
        cancel.setAllCaps(false);
        cancel.setOnClickListener(view -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        controls.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            closeCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (cameraDevice != null || finished) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission is required for scanning.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        if (cameraManager == null || !chooseCamera(cameraManager)) {
            Toast.makeText(this, "No usable camera was found.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        try {
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler);
        } catch (CameraAccessException exception) {
            Toast.makeText(this, "Camera could not open.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private boolean chooseCamera(CameraManager cameraManager) {
        try {
            String fallbackId = null;
            Size fallbackSize = null;
            int fallbackOrientation = 0;
            boolean fallbackFrontFacing = false;

            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size size = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
                if (size == null) {
                    continue;
                }

                Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean isFrontFacing = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
                if (fallbackId == null) {
                    fallbackId = id;
                    fallbackSize = size;
                    fallbackOrientation = orientation == null ? 0 : orientation;
                    fallbackFrontFacing = isFrontFacing;
                }

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    previewSize = size;
                    sensorOrientation = orientation == null ? 0 : orientation;
                    frontFacing = false;
                    return true;
                }
            }

            if (fallbackId != null) {
                cameraId = fallbackId;
                previewSize = fallbackSize;
                sensorOrientation = fallbackOrientation;
                frontFacing = fallbackFrontFacing;
                return true;
            }
        } catch (CameraAccessException ignored) {
        }
        return false;
    }

    private Size choosePreviewSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }

        Size best = null;
        for (Size size : sizes) {
            int area = size.getWidth() * size.getHeight();
            if (size.getWidth() <= 1280 && size.getHeight() <= 720) {
                if (best == null || area > best.getWidth() * best.getHeight()) {
                    best = size;
                }
            }
        }
        return best == null ? sizes[0] : best;
    }

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            runOnUiThread(() -> {
                Toast.makeText(BarcodeScannerActivity.this, "Camera scanner stopped.", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            });
        }
    };

    private void startPreview() {
        if (cameraDevice == null || previewSize == null || !previewView.isAvailable()) {
            return;
        }

        try {
            SurfaceTexture texture = previewView.getSurfaceTexture();
            if (texture == null) {
                return;
            }

            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            imageReader = ImageReader.newInstance(
                    previewSize.getWidth(),
                    previewSize.getHeight(),
                    ImageFormat.YUV_420_888,
                    2
            );
            imageReader.setOnImageAvailableListener(reader -> processImage(reader.acquireLatestImage()), cameraHandler);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(imageReader.getSurface());
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(imageReader.getSurface());
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler);
                        runOnUiThread(() -> statusView.setText("Scanning for a barcode..."));
                    } catch (CameraAccessException exception) {
                        runOnUiThread(() -> statusView.setText("Camera preview could not start."));
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    runOnUiThread(() -> statusView.setText("Camera preview could not start."));
                }
            }, cameraHandler);
        } catch (CameraAccessException exception) {
            runOnUiThread(() -> statusView.setText("Camera preview could not start."));
        }
    }

    private void processImage(Image image) {
        if (image == null) {
            return;
        }
        if (processingFrame || finished || barcodeScanner == null) {
            image.close();
            return;
        }

        processingFrame = true;
        InputImage inputImage;
        try {
            inputImage = InputImage.fromMediaImage(image, rotationDegrees());
        } catch (Exception exception) {
            processingFrame = false;
            image.close();
            return;
        }

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String value = barcode.getRawValue();
                        if (!TextUtils.isEmpty(value)) {
                            finishWithBarcode(value);
                            return;
                        }
                    }
                })
                .addOnFailureListener(exception ->
                        runOnUiThread(() -> statusView.setText("Still scanning...")))
                .addOnCompleteListener(task -> {
                    processingFrame = false;
                    image.close();
                });
    }

    private int rotationDegrees() {
        int deviceRotation;
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                deviceRotation = 90;
                break;
            case Surface.ROTATION_180:
                deviceRotation = 180;
                break;
            case Surface.ROTATION_270:
                deviceRotation = 270;
                break;
            case Surface.ROTATION_0:
            default:
                deviceRotation = 0;
                break;
        }

        if (frontFacing) {
            return (sensorOrientation + deviceRotation) % 360;
        }
        return (sensorOrientation - deviceRotation + 360) % 360;
    }

    private void finishWithBarcode(String value) {
        if (finished) {
            return;
        }

        finished = true;
        Intent result = new Intent();
        result.putExtra(EXTRA_BARCODE, value);
        setResult(RESULT_OK, result);
        finish();
    }

    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception ignored) {
        }
    }

    private void startCameraThread() {
        if (cameraThread != null) {
            return;
        }
        cameraThread = new HandlerThread("NourishRxBarcodeCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread == null) {
            return;
        }
        cameraThread.quitSafely();
        try {
            cameraThread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        cameraThread = null;
        cameraHandler = null;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
