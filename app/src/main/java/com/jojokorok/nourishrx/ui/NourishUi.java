package com.jojokorok.nourishrx.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NourishUi {
    private final Context context;

    public NourishUi(Context context) {
        this.context = context;
    }

    public LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(NourishColors.CARD, NourishColors.BORDER, dp(22)));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    public TextView statusBadge(String label) {
        int textColor = NourishColors.BLUE;
        int background = NourishColors.BLUE_SOFT;
        if ("Taken".equals(label) || "Active".equals(label) || "OK".equals(label)) {
            textColor = NourishColors.GREEN;
            background = NourishColors.GREEN_SOFT;
        } else if ("Due".equals(label) || "Skipped".equals(label) || "Paused".equals(label) || "Refill".equals(label)) {
            textColor = NourishColors.CORAL;
            background = NourishColors.CORAL_SOFT;
        }

        TextView badge = text(label, 12, textColor, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        badge.setBackground(rounded(background, Color.TRANSPARENT, dp(16)));
        return badge;
    }

    public TextView timePill(String value) {
        TextView pill = text(value, 13, NourishColors.BLUE, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(rounded(NourishColors.BLUE_SOFT, Color.TRANSPARENT, dp(18)));
        return pill;
    }

    public TextView summaryPill(String label, int textColor, int background) {
        TextView pill = text(label, 12, textColor, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(7), dp(10), dp(7));
        pill.setBackground(rounded(background, Color.TRANSPARENT, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.rightMargin = dp(8);
        pill.setLayoutParams(params);
        return pill;
    }

    public TextView displayText(String value, int sp, int color) {
        TextView textView = text(value, sp, color, Typeface.BOLD);
        textView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        textView.setIncludeFontPadding(false);
        return textView;
    }

    public TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(context);
        textView.setText(value);
        textView.setTextColor(color);
        textView.setTextSize(sp);
        textView.setTypeface(Typeface.create("sans-serif", style));
        textView.setLineSpacing(dp(2), 1.0f);
        return textView;
    }

    public TextView fieldLabel(String value) {
        TextView label = text(value, 13, NourishColors.MUTED, Typeface.BOLD);
        label.setPadding(0, dp(10), 0, dp(3));
        return label;
    }

    public EditText field(String hint, String value, int inputType) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(inputType);
        field.setSingleLine(!hint.equals("Instructions"));
        field.setTextColor(NourishColors.INK);
        field.setHintTextColor(NourishColors.MUTED);
        field.setTextSize(15);
        field.setPadding(dp(14), dp(10), dp(14), dp(10));
        field.setMinHeight(dp(48));
        field.setBackground(rounded(NourishColors.CARD, NourishColors.BORDER, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        field.setLayoutParams(params);
        return field;
    }

    public Button button(String label, int textColor, int backgroundColor) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setMinHeight(dp(42));
        button.setMinWidth(dp(68));
        button.setPadding(dp(12), 0, dp(12), 0);
        int stroke = backgroundColor == Color.TRANSPARENT || backgroundColor == Color.WHITE
                ? NourishColors.BORDER
                : Color.TRANSPARENT;
        button.setBackground(rounded(backgroundColor, stroke, dp(18)));
        return button;
    }

    public GradientDrawable rounded(int color, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    public GradientDrawable roundedGradient(int[] colors, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public int dp(float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
