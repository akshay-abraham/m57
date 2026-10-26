// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout

/** Three stable destinations; feature growth belongs inside Controls, not in extra tabs. */
class AdminDock(context: Context, selected: AdminTab, onSelect: (AdminTab) -> Unit) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(LyraUi.dp(context, 6), LyraUi.dp(context, 6), LyraUi.dp(context, 6), LyraUi.dp(context, 6))
        background = LyraUi.shape(Color.WHITE, LyraUi.dp(context, 28).toFloat(), LyraUi.line)
        elevation = LyraUi.dp(context, 10).toFloat()
        for (tab in AdminTab.values()) {
            val active = tab == selected
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = LyraUi.dp(context, 62)
                setPadding(LyraUi.dp(context, 4), LyraUi.dp(context, 7), LyraUi.dp(context, 4), LyraUi.dp(context, 7))
                background = RippleDrawable(ColorStateList.valueOf(0x15F45B0B),
                    LyraUi.shape(if (active) Color.rgb(255, 236, 223) else Color.TRANSPARENT, LyraUi.dp(context, 22).toFloat()), null)
                addView(DockIcon(context, tab, if (active) LyraUi.orange else LyraUi.muted),
                    LayoutParams(LyraUi.dp(context, 23), LyraUi.dp(context, 23)))
                addView(LyraUi.text(context, tab.title, 12f, if (active) LyraUi.ink else LyraUi.muted, active).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LayoutParams(-1, -2).apply { topMargin = LyraUi.dp(context, 5) }
                })
                isSelected = active
                contentDescription = "${tab.title}${if (active) ", selected" else ""}"
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                descendantFocusability = FOCUS_BLOCK_DESCENDANTS
                isClickable = true
                isFocusable = true
                accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.Button"
                    }
                }
                setOnClickListener { onSelect(tab) }
            }, LayoutParams(0, -2, 1f).apply {
                marginStart = LyraUi.dp(context, 2); marginEnd = LyraUi.dp(context, 2)
            })
        }
    }
}

private class DockIcon(context: Context, private val tab: AdminTab, color: Int) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.7f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 24f, height / 24f)
        when (tab) {
            AdminTab.OVERVIEW -> {
                val path = Path().apply {
                    moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f)
                    moveTo(5f, 10f); lineTo(5f, 21f); lineTo(10f, 21f)
                    lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 10f)
                }
                canvas.drawPath(path, pen)
            }
            AdminTab.CONTROLS -> {
                for (x in listOf(4f, 14f)) for (y in listOf(4f, 14f)) canvas.drawRoundRect(x, y, x + 6, y + 6, 1.5f, 1.5f, pen)
            }
            AdminTab.SETTINGS -> {
                for ((y, x) in listOf(6f to 9f, 12f to 16f, 18f to 7f)) {
                    canvas.drawLine(3f, y, x - 2.5f, y, pen)
                    canvas.drawLine(x + 2.5f, y, 21f, y, pen)
                    canvas.drawCircle(x, y, 2.5f, pen)
                }
            }
        }
        canvas.restore()
    }
}
