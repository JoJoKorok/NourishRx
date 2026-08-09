package com.jojokorok.nourishrx.profiles;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jojokorok.nourishrx.data.MedicationStore;
import com.jojokorok.nourishrx.data.Profile;
import com.jojokorok.nourishrx.ui.NourishColors;
import com.jojokorok.nourishrx.ui.NourishUi;

import java.io.IOException;
import java.io.InputStream;

public class ProfilePhotoFlow {
    public interface Callbacks {
        long pendingPhotoProfileId();

        void setPendingPhotoProfileId(long profileId);

        void clearPendingPhotoProfileId();

        void renderShell();
    }

    private final Activity activity;
    private final MedicationStore store;
    private final NourishUi ui;
    private final int requestProfilePhoto;
    private final Callbacks callbacks;

    public ProfilePhotoFlow(
            Activity activity,
            MedicationStore store,
            NourishUi ui,
            int requestProfilePhoto,
            Callbacks callbacks
    ) {
        this.activity = activity;
        this.store = store;
        this.ui = ui;
        this.requestProfilePhoto = requestProfilePhoto;
        this.callbacks = callbacks;
    }

    public void chooseProfilePhoto(Profile profile) {
        callbacks.setPendingPhotoProfileId(profile.id);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            activity.startActivityForResult(intent, requestProfilePhoto);
        } catch (Exception exception) {
            callbacks.clearPendingPhotoProfileId();
            Toast.makeText(activity, "No photo picker is available.", Toast.LENGTH_LONG).show();
        }
    }

    public void handlePhotoPickerResult(int resultCode, Intent data) {
        long profileId = callbacks.pendingPhotoProfileId();
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null && profileId > 0) {
            Uri photoUri = data.getData();
            try {
                activity.getContentResolver().takePersistableUriPermission(photoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Some providers grant a temporary read URI instead of a persistable one.
            }
            Profile profile = store.getProfile(profileId);
            if (profile != null) {
                showProfilePhotoEditor(profile, photoUri.toString(), 1.0f, 0.0f, 0.0f, 1.0f);
            }
        }
        callbacks.clearPendingPhotoProfileId();
    }

    public void showProfilePhotoEditor(
            Profile profile,
            String avatarUri,
            float zoom,
            float offsetX,
            float offsetY,
            float aspectRatio
    ) {
        Bitmap bitmap = loadBitmap(avatarUri);
        if (bitmap == null) {
            Toast.makeText(activity, "That photo could not be opened.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);

        ProfilePhotoEditorView editor = new ProfilePhotoEditorView(bitmap);
        editor.setFrame(zoom, offsetX, offsetY, aspectRatio);
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(280)
        );
        form.addView(editor, editorParams);

        TextView zoomLabel = ui.fieldLabel("Zoom");
        form.addView(zoomLabel);
        SeekBar zoomSlider = new SeekBar(activity);
        zoomSlider.setMax(200);
        zoomSlider.setProgress(Math.round((editor.getZoom() - 1.0f) * 100.0f));
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                editor.setZoom(1.0f + progress / 100.0f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editor.setOnFrameChangedListener(() -> {
            int progress = Math.round((editor.getZoom() - 1.0f) * 100.0f);
            if (zoomSlider.getProgress() != progress) {
                zoomSlider.setProgress(progress);
            }
        });
        form.addView(zoomSlider);

        form.addView(ui.fieldLabel("Frame"));
        LinearLayout aspectActions = actionRow();
        Button square = button("Square", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        square.setOnClickListener(view -> editor.setAspectRatio(1.0f));
        aspectActions.addView(square, weightedActionParams());

        Button portrait = button("Portrait", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        portrait.setOnClickListener(view -> editor.setAspectRatio(0.8f));
        aspectActions.addView(portrait, weightedActionParams());

        Button wide = button("Wide", NourishColors.GOLD, NourishColors.GOLD_SOFT);
        wide.setOnClickListener(view -> editor.setAspectRatio(1.6f));
        aspectActions.addView(wide, weightedActionParams());
        form.addView(aspectActions);

        form.addView(ui.fieldLabel("Position"));
        LinearLayout horizontalActions = actionRow();
        Button left = button("Left", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        left.setOnClickListener(view -> editor.nudge(-0.12f, 0.0f));
        horizontalActions.addView(left, weightedActionParams());

        Button center = button("Center", NourishColors.GREEN, NourishColors.GREEN_SOFT);
        center.setOnClickListener(view -> editor.center());
        horizontalActions.addView(center, weightedActionParams());

        Button right = button("Right", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        right.setOnClickListener(view -> editor.nudge(0.12f, 0.0f));
        horizontalActions.addView(right, weightedActionParams());
        form.addView(horizontalActions);

        LinearLayout verticalActions = actionRow();
        Button up = button("Up", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        up.setOnClickListener(view -> editor.nudge(0.0f, -0.12f));
        verticalActions.addView(up, weightedActionParams());

        Button down = button("Down", NourishColors.BLUE, NourishColors.BLUE_SOFT);
        down.setOnClickListener(view -> editor.nudge(0.0f, 0.12f));
        verticalActions.addView(down, weightedActionParams());
        form.addView(verticalActions);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Frame " + profile.name)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                store.updateProfileAvatar(
                        profile.id,
                        avatarUri,
                        editor.getZoom(),
                        editor.getOffsetX(),
                        editor.getOffsetY(),
                        editor.getAspectRatio()
                );
                Toast.makeText(activity, "Profile photo updated.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                callbacks.renderShell();
            });
        });

        dialog.show();
    }

    public Bitmap loadBitmap(String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            return null;
        }

        Uri uri;
        try {
            uri = Uri.parse(uriString);
        } catch (Exception exception) {
            return null;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (IOException | RuntimeException exception) {
            return null;
        }

        int sampleSize = 1;
        int largestSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (largestSide / sampleSize > 1600) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sampleSize;
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, decode);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    public Bitmap createCroppedAvatarBitmap(Bitmap source, int width, int height, Profile profile) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        RectF frame = new RectF(0, 0, width, height);
        Path clip = new Path();
        float radius = Math.min(width, height) / 2.0f;
        clip.addRoundRect(frame, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        drawCroppedBitmap(
                canvas,
                source,
                frame,
                profile.avatarZoom,
                profile.avatarOffsetX,
                profile.avatarOffsetY
        );
        canvas.restore();
        return output;
    }

    public float clampedAvatarAspectRatio(float value) {
        return clamp(value, 0.75f, 1.65f, 1.0f);
    }

    private void drawCroppedBitmap(
            Canvas canvas,
            Bitmap source,
            RectF frame,
            float zoom,
            float offsetX,
            float offsetY
    ) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return;
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        float safeZoom = clamp(zoom, 1.0f, 3.0f, 1.0f);
        float scale = Math.max(frame.width() / source.getWidth(), frame.height() / source.getHeight()) * safeZoom;
        float destinationWidth = source.getWidth() * scale;
        float destinationHeight = source.getHeight() * scale;
        float panX = Math.max(0.0f, (destinationWidth - frame.width()) / 2.0f);
        float panY = Math.max(0.0f, (destinationHeight - frame.height()) / 2.0f);
        float left = frame.centerX() - destinationWidth / 2.0f + clamp(offsetX, -1.0f, 1.0f, 0.0f) * panX;
        float top = frame.centerY() - destinationHeight / 2.0f + clamp(offsetY, -1.0f, 1.0f, 0.0f) * panY;
        RectF destination = new RectF(left, top, left + destinationWidth, top + destinationHeight);
        canvas.drawColor(NourishColors.CARD);
        canvas.drawBitmap(source, null, destination, paint);
    }

    private float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0 && min > 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private LinearLayout actionRow() {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, ui.dp(12), 0, 0);
        return actions;
    }

    private LinearLayout.LayoutParams weightedActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(44), 1);
        params.rightMargin = ui.dp(8);
        return params;
    }

    private Button button(String label, int textColor, int backgroundColor) {
        return ui.button(label, textColor, backgroundColor);
    }

    private final class ProfilePhotoEditorView extends View {
        private final Bitmap bitmap;
        private float zoom = 1.0f;
        private float offsetX = 0.0f;
        private float offsetY = 0.0f;
        private float aspectRatio = 1.0f;
        private float lastX;
        private float lastY;
        private float pinchStartDistance;
        private float pinchStartZoom;
        private Runnable onFrameChanged;

        ProfilePhotoEditorView(Bitmap bitmap) {
            super(activity);
            this.bitmap = bitmap;
            setBackground(ui.rounded(NourishColors.CARD, NourishColors.BORDER, ui.dp(22)));
        }

        void setOnFrameChangedListener(Runnable listener) {
            onFrameChanged = listener;
        }

        void setFrame(float zoom, float offsetX, float offsetY, float aspectRatio) {
            this.zoom = clamp(zoom, 1.0f, 3.0f, 1.0f);
            this.offsetX = clamp(offsetX, -1.0f, 1.0f, 0.0f);
            this.offsetY = clamp(offsetY, -1.0f, 1.0f, 0.0f);
            this.aspectRatio = clamp(aspectRatio, 0.75f, 1.65f, 1.0f);
            invalidate();
            notifyFrameChanged();
        }

        void setZoom(float value) {
            zoom = clamp(value, 1.0f, 3.0f, 1.0f);
            invalidate();
            notifyFrameChanged();
        }

        void setAspectRatio(float value) {
            aspectRatio = clamp(value, 0.75f, 1.65f, 1.0f);
            invalidate();
            notifyFrameChanged();
        }

        void nudge(float deltaX, float deltaY) {
            offsetX = clamp(offsetX + deltaX, -1.0f, 1.0f, 0.0f);
            offsetY = clamp(offsetY + deltaY, -1.0f, 1.0f, 0.0f);
            invalidate();
            notifyFrameChanged();
        }

        void center() {
            offsetX = 0.0f;
            offsetY = 0.0f;
            invalidate();
            notifyFrameChanged();
        }

        float getZoom() {
            return zoom;
        }

        float getOffsetX() {
            return offsetX;
        }

        float getOffsetY() {
            return offsetY;
        }

        float getAspectRatio() {
            return aspectRatio;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF frame = editorFrame();
            float radius = Math.min(frame.width(), frame.height()) / 2.0f;

            Path clip = new Path();
            clip.addRoundRect(frame, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            drawCroppedBitmap(canvas, bitmap, frame, zoom, offsetX, offsetY);
            canvas.restore();

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(ui.dp(2));
            border.setColor(NourishColors.GREEN);
            canvas.drawRoundRect(frame, radius, radius, border);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX();
                lastY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
                pinchStartDistance = pointerDistance(event);
                pinchStartZoom = zoom;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (event.getPointerCount() >= 2) {
                    float distance = pointerDistance(event);
                    if (pinchStartDistance > 0.0f) {
                        setZoom(pinchStartZoom * distance / pinchStartDistance);
                    }
                } else {
                    float x = event.getX();
                    float y = event.getY();
                    panByPixels(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return true;
        }

        private RectF editorFrame() {
            float padding = ui.dp(14);
            float availableWidth = Math.max(1.0f, getWidth() - padding * 2.0f);
            float availableHeight = Math.max(1.0f, getHeight() - padding * 2.0f);
            float frameWidth = availableWidth;
            float frameHeight = frameWidth / aspectRatio;
            if (frameHeight > availableHeight) {
                frameHeight = availableHeight;
                frameWidth = frameHeight * aspectRatio;
            }
            float left = (getWidth() - frameWidth) / 2.0f;
            float top = (getHeight() - frameHeight) / 2.0f;
            return new RectF(left, top, left + frameWidth, top + frameHeight);
        }

        private void panByPixels(float deltaX, float deltaY) {
            RectF frame = editorFrame();
            float scale = Math.max(frame.width() / bitmap.getWidth(), frame.height() / bitmap.getHeight()) * zoom;
            float destinationWidth = bitmap.getWidth() * scale;
            float destinationHeight = bitmap.getHeight() * scale;
            float panX = Math.max(1.0f, (destinationWidth - frame.width()) / 2.0f);
            float panY = Math.max(1.0f, (destinationHeight - frame.height()) / 2.0f);
            offsetX = clamp(offsetX + deltaX / panX, -1.0f, 1.0f, 0.0f);
            offsetY = clamp(offsetY + deltaY / panY, -1.0f, 1.0f, 0.0f);
            invalidate();
            notifyFrameChanged();
        }

        private float pointerDistance(MotionEvent event) {
            float deltaX = event.getX(0) - event.getX(1);
            float deltaY = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        }

        private void notifyFrameChanged() {
            if (onFrameChanged != null) {
                onFrameChanged.run();
            }
        }
    }
}
