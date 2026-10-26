// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs

/** Insets, keyboard avoidance and scroll-aware navigation shared by the native pages. */
class PageShell(activity: Activity, tab: AdminTab? = null, onTab: (AdminTab) -> Unit = {}) : FrameLayout(activity) {
    val content = LyraUi.column(activity)
    private val scroll = ScrollView(activity)
    private val dock = tab?.let { AdminDock(activity, it, onTab) }
    private var keyboard = false
    private var shown = true
    private var direction = 0
    private var travel = 0
    private val density = resources.displayMetrics.density

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(LyraUi.background, Color.rgb(255, 247, 240), Color.rgb(247, 242, 235)))
        content.addView(LyraUi.brand(activity))
        scroll.isVerticalScrollBarEnabled = false
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.addView(content)
        addView(scroll, LayoutParams(-1, -1))
        dock?.let { navigation ->
            addView(navigation, LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                leftMargin = LyraUi.dp(activity, 20); rightMargin = LyraUi.dp(activity, 20)
                bottomMargin = LyraUi.dp(activity, 12)
            })
            navigation.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateContentPadding() }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val params = navigation.layoutParams as LayoutParams
                val desired = (width - paddingLeft - paddingRight - LyraUi.dp(activity, 40))
                    .coerceAtMost(LyraUi.dp(activity, 480)).coerceAtLeast(0)
                if (params.width != desired) { params.width = desired; navigation.layoutParams = params }
            }
            scroll.setOnScrollChangeListener { _: View, _: Int, y: Int, _: Int, oldY: Int ->
                if (keyboard) return@setOnScrollChangeListener
                val assisted = activity.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
                val remaining = (content.height - scroll.height - y).coerceAtLeast(0)
                if (assisted || y <= 24 * density || remaining <= 72 * density) {
                    travel = 0; setDockVisible(true)
                } else {
                    val delta = y - oldY
                    val nextDirection = delta.compareTo(0)
                    if (nextDirection != 0) {
                        if (nextDirection != direction) travel = 0
                        direction = nextDirection
                        travel += abs(delta)
                        if (travel >= 24 * density) { setDockVisible(delta < 0); travel = 0 }
                    }
                }
            }
        }
        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            val wasKeyboard = keyboard
            keyboard = insets.isVisible(WindowInsets.Type.ime())
            if (keyboard != wasKeyboard) {
                dock?.animate()?.cancel()
                dock?.visibility = if (keyboard) View.GONE else View.VISIBLE
                dock?.alpha = 1f; dock?.translationY = 0f
                shown = true
                dock?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
            updateContentPadding()
            insets
        }
    }

    private fun updateContentPadding() {
        val bottom = if (dock != null && !keyboard) dock.height + LyraUi.dp(context, 36) else LyraUi.dp(context, 24)
        content.setPadding(LyraUi.dp(context, 24), LyraUi.dp(context, 24), LyraUi.dp(context, 24), bottom)
    }

    private fun setDockVisible(visible: Boolean) {
        val navigation = dock ?: return
        if (shown == visible) return
        shown = visible
        navigation.animate().cancel()
        navigation.visibility = View.VISIBLE
        navigation.importantForAccessibility = if (visible) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        navigation.animate().alpha(if (visible) 1f else 0f)
            .translationY(if (visible) 0f else navigation.height + LyraUi.dp(context, 24).toFloat())
            .setDuration(180).withEndAction { if (!shown) navigation.visibility = View.INVISIBLE }.start()
    }

    override fun onDetachedFromWindow() {
        dock?.animate()?.cancel()
        super.onDetachedFromWindow()
    }
}
