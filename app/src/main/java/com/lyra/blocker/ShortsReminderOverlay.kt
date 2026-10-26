// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.accessibilityservice.AccessibilityService

/** No AI launch or countdown: administrators choose a hard boundary or a gentle reminder. */
class ShortsReminderOverlay(
    private val service: AccessibilityService,
    private val isStillShorts: () -> Boolean,
) {
    private val surface = OverlaySurface(service)
    val visible get() = surface.visible
    private var currentLabel=""
    private var currentBlock=false
    fun show(label:String="YouTube Shorts",block:Boolean=false): Boolean {
        if(surface.visible&&currentLabel==label&&currentBlock==block)return true
        hide();currentLabel=label;currentBlock=block
        val message = PopupMessages.shorts.next()
        val card = LyraUi.column(service, 26).apply {
            addView(LyraUi.brand(service))
            addView(LyraUi.badge(service, label.uppercase()))
            addView(LyraUi.text(service, message.title, 28f, bold = true))
            addView(LyraUi.text(service, message.accent, 24f, LyraUi.orange, true))
            addView(LyraUi.text(service, message.body, 14f, LyraUi.muted))
            addView(LyraUi.button(service, "Leave this viewer") {
                if (isStillShorts()) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                hide()
            })
            addView(LyraUi.button(service,"Go to Home",false){if(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))hide()})
            if(!block)addView(LyraUi.link(service, "Dismiss reminder") { hide() })
            if(block)addView(LyraUi.text(service,"Your administrator chose a boundary for this video space. You can still choose something else to watch or do.",13f,LyraUi.muted))
        }
        return surface.show(card, "$label. Take a moment to pause. ${if(block)"Leave this viewer or go Home." else "Leave or dismiss this reminder."}")
    }
    fun hide() { surface.hide() }
}
