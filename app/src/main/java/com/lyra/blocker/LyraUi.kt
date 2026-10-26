// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Lightweight native components using warm neutrals and Lyra orange. */
object LyraUi {
    val background = Color.rgb(251, 250, 249)
    val ink = Color.rgb(39, 34, 30)
    val muted = Color.rgb(117, 107, 99)
    val orange = Color.rgb(244, 91, 11)
    val line = Color.rgb(235, 232, 230)
    val soft = Color.rgb(247, 245, 243)
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun shape(color: Int, radius: Float = 24f, border: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (border != null) setStroke(1, border)
    }
    fun glass(context: Context, blurEnabled: Boolean = true) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.argb(if (blurEnabled) 232 else 252, 255, 255, 255),
            Color.argb(if (blurEnabled) 224 else 250, 255, 248, 241),
        ),
    ).apply {
        cornerRadius = dp(context, 28).toFloat()
        setStroke(dp(context, 1), Color.argb(210, 255, 255, 255))
    }
    fun column(context: Context, padding: Int = 24) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
    }
    fun text(context: Context, content: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(context).apply {
        text = content
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        setLineSpacing(dp(context, 3).toFloat(), 1.08f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) }
    }
    fun button(context: Context, title: String, primary: Boolean = true, action: () -> Unit) = Button(context).apply {
        text = title
        isAllCaps = false
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0f
        setTextColor(if (primary) Color.WHITE else ink)
        // Clear the platform Material tint/insets before drawing our button.
        backgroundTintList = null
        stateListAnimator = null
        background = RippleDrawable(
            ColorStateList.valueOf(if (primary) 0x30FFFFFF else 0x10000000),
            shape(if (primary) orange else Color.WHITE, dp(context, 16).toFloat(), if (primary) null else line), null,
        )
        minimumHeight = dp(context, 52)
        minHeight = dp(context, 52)
        setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8) }
        setOnClickListener { action() }
    }
    fun link(context: Context, title: String, action: () -> Unit) = text(context, title, 13f, muted, true).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(context, 48)
        setPadding(0, dp(context, 14), 0, dp(context, 14))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }
    fun brand(context: Context) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply {
            setImageResource(R.drawable.lyra_logo)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)))
        addView(text(context, context.getString(R.string.app_name), 25f, ink, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(context, 4) }
        })
        addView(badge(context, "FOCUS", orange, Color.rgb(255, 240, 228)).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 28) }
    }
    fun badge(context: Context, title: String, color: Int = orange, fill: Int = Color.rgb(255, 240, 228)) = text(context, title, 10f, color, true).apply {
        letterSpacing = 0.09f
        background = shape(fill, dp(context, 20).toFloat())
        setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(context, 18) }
    }
    fun card(context: Context) = column(context, 20).apply {
        background = glass(context)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12); bottomMargin = dp(context, 12) }
    }
    fun feature(context: Context, icon: String, title: String, detail: String) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        val tint = orange
        addView(text(context, icon, 14f, tint, true).apply {
            gravity = Gravity.CENTER
            background = shape(Color.rgb(255, 241, 232), dp(context, 14).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)).apply { marginEnd = dp(context, 14) }
        })
        addView(column(context, 0).apply {
            addView(text(context, title, 15f, ink, true).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 5) }
            })
            addView(text(context, detail, 12f, muted).apply { layoutParams = LinearLayout.LayoutParams(-1, -2) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 9); bottomMargin = dp(context, 9) }
    }
    fun divider(context: Context) = View(context).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 1)).apply { topMargin = dp(context, 12); bottomMargin = dp(context, 12) }
    }
    fun field(view: EditText) = view.apply {
        textSize = 15f
        setTextColor(ink)
        setHintTextColor(muted)
        backgroundTintList = null
        background = shape(soft, dp(context, 14).toFloat(), line)
        setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
        minimumHeight = dp(context, 52)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 10) }
    }
    fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(shape(background, dp(dialog.context, 24).toFloat()))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(orange)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(muted)
    }
}
