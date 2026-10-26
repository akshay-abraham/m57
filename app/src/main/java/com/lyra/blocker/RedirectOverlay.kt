// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.Toast
import kotlin.math.ceil
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.content.Intent
import android.net.Uri

class RedirectOverlay(
    private val service: AccessibilityService,
    private val store: ProtectionStore,
    private val canRedirect: (String) -> Boolean,
) {
    private val surface = OverlaySurface(service)
    private val handler = Handler(Looper.getMainLooper())
    private var timer: Runnable? = null
    private var openButton: Button? = null
    private var animation:ValueAnimator?=null
    val visible get() = surface.visible
    var blockedPackage: String? = null
        private set

    var sourceHint: String? = null
        private set

    fun show(packageName: String, appName: String, source: String) {
        if (visible) return
        blockedPackage = packageName
        sourceHint = source
        val message = PopupMessages.ai.next()
        val card = LyraUi.column(service, 26).apply {
            addView(LyraUi.brand(service))
            addView(LyraUi.badge(service, "$appName → Lyra"))
            addView(LyraUi.text(service, message.title, 35f, bold = true))
            addView(LyraUi.text(service, message.accent, 29f, LyraUi.orange, true))
            addView(LyraUi.text(service, message.body, 17f, LyraUi.muted))
        }
        card.addView(View(service),android.widget.LinearLayout.LayoutParams(-1,0,1f))
        val ring=CountdownRing(service).apply{seconds=store.countdown;layoutParams=android.widget.LinearLayout.LayoutParams(-1,LyraUi.dp(service,150))}
        card.addView(ring)
        val open = LyraUi.button(service, "Open Lyra") { launch() }
        openButton = open
        card.addView(open)
        card.addView(LyraUi.link(service,"Missing a feature? Tell Lyra"){
            timer?.let(handler::removeCallbacks);timer=null;animation?.cancel()
            runCatching{service.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(store.destination).buildUpon().path("/contact").clearQuery().appendQueryParameter("topic","missing-feature").appendQueryParameter("source","m57").build()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                .onSuccess{hide()}
        })
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
        if(ValueAnimator.areAnimatorsEnabled())animation=ValueAnimator.ofFloat(1f,0f).apply{
            this.duration=duration;interpolator=LinearInterpolator();addUpdateListener{ring.fraction=it.animatedValue as Float};start()
        }
        val tick = object : Runnable {
            override fun run() {
                if (!visible) return
                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                ring.seconds=ceil(remaining/1000.0).toInt()
                if(!ValueAnimator.areAnimatorsEnabled())ring.fraction=remaining.toFloat()/duration
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
        animation?.cancel();animation=null
        timer?.let(handler::removeCallbacks)
        timer = null
        openButton?.text = "Open Lyra"
        val target = blockedPackage ?: return
        if (!canRedirect(target)) { hide(); return }
        if (!LyraLauncher.open(service, store, sourceHint)) {
            Toast.makeText(service, "Install Chrome or a browser, then tap Open Lyra again.", Toast.LENGTH_LONG).show()
        }
    }
    fun hide() {
        animation?.cancel();animation=null
        timer?.let(handler::removeCallbacks)
        timer = null
        surface.hide()
        openButton = null
        blockedPackage = null
        sourceHint = null
    }
}
