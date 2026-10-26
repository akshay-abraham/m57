// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import android.accessibilityservice.AccessibilityService

/** No launcher and no timer: Shorts receives a dismissible reminder only. */
class ShortsReminderOverlay(
    private val service: AccessibilityService,
    private val isStillShorts: () -> Boolean,
) {
    private val surface = OverlaySurface(service)
    val visible get() = surface.visible
    fun show(): Boolean {
        val message = PopupMessages.shorts.next()
        val card = LyraUi.column(service, 26).apply {
            addView(LyraUi.brand(service))
            addView(LyraUi.badge(service, "YOUTUBE SHORTS"))
            addView(LyraUi.text(service, message.title, 28f, bold = true))
            addView(LyraUi.text(service, message.accent, 24f, LyraUi.orange, true))
            addView(LyraUi.text(service, message.body, 14f, LyraUi.muted))
            addView(LyraUi.button(service, "Leave Shorts") {
                if (isStillShorts()) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                hide()
            })
            addView(LyraUi.link(service, "Dismiss") { hide() })
        }
        return surface.show(card, "YouTube Shorts. Take a moment to pause. Leave Shorts, or dismiss this reminder.")
    }
    fun hide() { surface.hide() }
}
