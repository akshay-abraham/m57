// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import kotlin.math.ceil

class RedirectOverlay(
    private val service: AccessibilityService,
    private val store: ProtectionStore,
    private val canRedirect: (String) -> Boolean,
) {
    private val surface = OverlaySurface(service)
    private val handler = Handler(Looper.getMainLooper())
    private var timer: Runnable? = null
    private var openButton: Button? = null
    val visible get() = surface.visible
    var blockedPackage: String? = null
        private set

    fun show(packageName: String, appName: String) {
        if (visible) return
        blockedPackage = packageName
        val message = PopupMessages.ai.next()
        val card = LyraUi.column(service, 26).apply {
            addView(LyraUi.brand(service))
            addView(LyraUi.badge(service, "$appName → Lyra"))
            addView(LyraUi.text(service, message.title, 28f, bold = true))
            addView(LyraUi.text(service, message.accent, 24f, LyraUi.orange, true))
            addView(LyraUi.text(service, message.body, 14f, LyraUi.muted))
        }
        val open = LyraUi.button(service, "Open Lyra") { launch() }
        openButton = open
        card.addView(open)
        val countdownBar = ProgressBar(service, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 1000
            progressTintList = ColorStateList.valueOf(LyraUi.orange)
            progressBackgroundTintList = ColorStateList.valueOf(LyraUi.line)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, LyraUi.dp(service, 3)).apply {
                topMargin = LyraUi.dp(service, 12); bottomMargin = LyraUi.dp(service, 8)
            }
        }
        card.addView(countdownBar)
        card.addView(LyraUi.link(service, "Go home instead") {
            if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) hide()
        })
        if (!surface.show(card, "Use AI through Lyra. Opening in ${store.countdown} seconds.")) {
            blockedPackage = null
            openButton = null
            return
        }
        val duration = store.countdown * 1000L
        val deadline = SystemClock.elapsedRealtime() + duration
        val tick = object : Runnable {
            override fun run() {
                if (!visible) return
                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                countdownBar.progress = (remaining * 1000 / duration).toInt()
                if (remaining == 0L) launch()
                else {
                    val caption = "Open Lyra · ${ceil(remaining / 1000.0).toInt()}s"
                    if (open.text.toString() != caption) open.text = caption
                    handler.postDelayed(this, 100)
                }
            }
        }
        timer = tick
        handler.post(tick)
    }
    private fun launch() {
        timer?.let(handler::removeCallbacks)
        timer = null
        openButton?.text = "Open Lyra"
        val target = blockedPackage ?: return
        if (!canRedirect(target)) { hide(); return }
        if (!LyraLauncher.open(service, store)) {
            Toast.makeText(service, "Install Chrome or a browser, then tap Open Lyra again.", Toast.LENGTH_LONG).show()
        }
    }
    fun hide() {
        timer?.let(handler::removeCallbacks)
        timer = null
        surface.hide()
        openButton = null
        blockedPackage = null
    }
}
