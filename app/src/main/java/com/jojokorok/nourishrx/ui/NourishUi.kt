package com.jojokorok.nourishrx.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class NourishUi(private val context: Context) {
    fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            dp(NourishSpacing.MD),
            dp(NourishSpacing.MD),
            dp(NourishSpacing.MD),
            dp(NourishSpacing.MD)
        )
        background = rounded(
            NourishColors.CARD,
            NourishColors.BORDER,
            dp(NourishShapes.RADIUS_CARD)
        )
        elevation = dp(NourishShapes.ELEVATION_RAISED).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(NourishSpacing.SM)
        }
    }

    fun statusBadge(label: String): TextView {
        var textColor = NourishColors.BLUE
        var backgroundColor = NourishColors.BLUE_SOFT

        if (label == "Taken" || label == "Active" || label == "OK") {
            textColor = NourishColors.GREEN
            backgroundColor = NourishColors.GREEN_SOFT
        } else if (
            label == "Due" ||
            label == "Skipped" ||
            label == "Paused" ||
            label == "Refill"
        ) {
            textColor = NourishColors.CORAL
            backgroundColor = NourishColors.CORAL_SOFT
        }

        return text(label, NourishTypography.CAPTION, textColor, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(
                dp(NourishSpacing.SM),
                dp(NourishSpacing.XXS),
                dp(NourishSpacing.SM),
                dp(NourishSpacing.XXS)
            )
            background = rounded(
                backgroundColor,
                Color.TRANSPARENT,
                dp(NourishShapes.RADIUS_PILL)
            )
        }
    }

    fun timePill(value: String): TextView =
        text(value, NourishTypography.LABEL, NourishColors.BLUE, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(NourishSpacing.XS), dp(NourishSpacing.XXS), dp(NourishSpacing.XS), dp(NourishSpacing.XXS))
            background = rounded(
                NourishColors.BLUE_SOFT,
                Color.TRANSPARENT,
                dp(NourishShapes.RADIUS_CONTROL)
            )
        }

    fun summaryPill(label: String, textColor: Int, backgroundColor: Int): TextView =
        text(label, NourishTypography.CAPTION, textColor, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(NourishSpacing.XS), dp(NourishSpacing.XS), dp(NourishSpacing.XS), dp(NourishSpacing.XS))
            background = rounded(
                backgroundColor,
                Color.TRANSPARENT,
                dp(NourishShapes.RADIUS_CONTROL)
            )
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                rightMargin = dp(NourishSpacing.XS)
            }
        }

    fun displayText(value: String, sp: Int, color: Int): TextView =
        text(value, sp, color, Typeface.BOLD).apply {
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.BOLD)
            includeFontPadding = false
        }

    fun text(value: String, sp: Int, color: Int, style: Int): TextView =
        TextView(context).apply {
            setText(value)
            setTextColor(color)
            textSize = sp.toFloat()
            typeface = Typeface.create(NourishTypography.FAMILY_BODY, style)
            setLineSpacing(dp(2).toFloat(), 1f)
            includeFontPadding = false
        }

    fun fieldLabel(value: String): TextView =
        text(value, NourishTypography.LABEL, NourishColors.INK_SECONDARY, Typeface.BOLD).apply {
            setPadding(0, dp(NourishSpacing.SM), 0, dp(NourishSpacing.XXS))
        }

    fun field(hint: String, value: String, inputType: Int): EditText = EditText(context).apply {
        setHint(hint)
        setText(value)
        setInputType(inputType)
        setSingleLine(hint != "Instructions")
        setTextColor(NourishColors.INK)
        setHintTextColor(NourishColors.MUTED)
        textSize = NourishTypography.BODY.toFloat()
        typeface = Typeface.create(NourishTypography.FAMILY_BODY, Typeface.NORMAL)
        setPadding(
            dp(NourishSpacing.SM),
            dp(NourishSpacing.XS),
            dp(NourishSpacing.SM),
            dp(NourishSpacing.XS)
        )
        minHeight = dp(48)
        background = rounded(
            NourishColors.CARD,
            NourishColors.BORDER,
            dp(NourishShapes.RADIUS_CONTROL)
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(NourishSpacing.XS)
        }
    }

    fun button(label: String, textColor: Int, backgroundColor: Int): Button = Button(context).apply {
        isAllCaps = false
        text = label
        setTextColor(textColor)
        textSize = NourishTypography.LABEL.toFloat()
        typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL)
        minHeight = dp(48)
        minWidth = dp(68)
        setPadding(dp(NourishSpacing.SM), 0, dp(NourishSpacing.SM), 0)

        val strokeColor = if (backgroundColor == Color.TRANSPARENT || backgroundColor == Color.WHITE) {
            NourishColors.BORDER
        } else {
            Color.TRANSPARENT
        }
        background = rounded(
            backgroundColor,
            strokeColor,
            dp(NourishShapes.RADIUS_CONTROL)
        )
    }

    fun rounded(color: Int, strokeColor: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }

    fun roundedGradient(colors: IntArray, radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = radius.toFloat()
        }

    fun dp(value: Int): Int = dp(value.toFloat())

    fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
