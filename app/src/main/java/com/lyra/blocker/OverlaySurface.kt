// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.function.Consumer

/** Shared visual shell only; each feature owns its actions and lifecycle. */
class OverlaySurface(private val service: AccessibilityService) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var blurListener: Consumer<Boolean>? = null
    val visible get() = view != null

    fun show(card: LinearLayout, announcement: String): Boolean {
        if (visible) return false
        val backdrop = FrameLayout(service).apply { setBackgroundColor(Color.argb(12, 255, 243, 228)) }
        val blurEnabled = manager.isCrossWindowBlurEnabled
        card.background = LyraUi.glass(service, blurEnabled)
        card.elevation = LyraUi.dp(service, 16).toFloat()
        card.outlineAmbientShadowColor = Color.argb(65, 100, 64, 35)
        card.outlineSpotShadowColor = Color.argb(65, 100, 64, 35)
        val scroll = ScrollView(service).apply { isFillViewport = false; addView(card); isVerticalScrollBarEnabled = false }
        val width = service.resources.displayMetrics.widthPixels.coerceAtMost(LyraUi.dp(service, 412)) - LyraUi.dp(service, 40)
        backdrop.addView(scroll, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        backdrop.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top + LyraUi.dp(service, 20), bars.right, bars.bottom + LyraUi.dp(service, 20))
            insets
        }
        val params = WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "${service.getString(R.string.app_name)} focus reminder"
            setBlurBehindRadius(if (blurEnabled) LyraUi.dp(service, 32) else 0)
            dimAmount = if (blurEnabled) 0.18f else 0.38f
        }
        try { manager.addView(backdrop, params) }
        catch (_: WindowManager.BadTokenException) { return false }
        catch (_: IllegalStateException) { return false }
        view = backdrop
        // System blur can change while visible (for example, with battery saver).
        val listener = Consumer<Boolean> { enabled ->
            if (view === backdrop && backdrop.isAttachedToWindow) {
                card.background = LyraUi.glass(service, enabled)
                params.setBlurBehindRadius(if (enabled) LyraUi.dp(service, 32) else 0)
                params.dimAmount = if (enabled) 0.18f else 0.38f
                manager.updateViewLayout(backdrop, params)
            }
        }
        blurListener = listener
        manager.addCrossWindowBlurEnabledListener(service.mainExecutor, listener)
        backdrop.requestApplyInsets()
        backdrop.announceForAccessibility(announcement)
        return true
    }
    fun hide() {
        blurListener?.let(manager::removeCrossWindowBlurEnabledListener)
        blurListener = null
        view?.let { if (it.isAttachedToWindow) manager.removeView(it) }
        view = null
    }
}
