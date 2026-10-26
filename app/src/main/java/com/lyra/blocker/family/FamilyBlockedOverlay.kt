// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker.family

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import com.lyra.blocker.LyraUi
import com.lyra.blocker.MainActivity
import com.lyra.blocker.OverlaySurface

class FamilyBlockedOverlay(private val service: AccessibilityService) {
    private val surface = OverlaySurface(service)
    private var shownHost: String? = null
    private var shownCategory: String? = null
    fun show(host: String, category: String) {
        if (surface.visible && shownHost == host && shownCategory == category) return
        hide()
        val (heading, explanation) = when(category) {
            "adult" -> "Curiosity is part of you." to "There is nothing to be ashamed of. This website is outside the boundaries chosen for this device. You can talk with a trusted adult about what you are curious about."
            "gambling" -> "Keep the next choice yours." to "Your administrator has put betting and gambling websites outside this space. Step away from the pressure to play or spend, and ask someone you trust if you want to talk about it."
            "base" -> "A safer route from here." to "This domain appears on the enabled adware and malware list. Choose another website. A list can make mistakes; your administrator can review an exception."
            "social" -> "Room for something else." to "Your administrator has paused access to this listed social website. This boundary covers the service, including ordinary feeds. Ask them to review it if you need access."
            "fakenews" -> "Give the information a second look." to "This domain is included in the enabled upstream misinformation list. That is a list judgement, not proof about every page. Try another source or ask your administrator to review it."
            "source" -> "Review with your administrator." to "Your administrator has restricted this device’s access to the M57 repository. They can pause protection or change the source-page guard in Settings. M57 remains free and open-source."
            else -> "A little space to choose." to "Your administrator has added a website boundary for this device. Choose another page or ask them to review this custom rule."
        }
        val card = LyraUi.column(service, 26).apply {
            addView(LyraUi.brand(service))
            addView(LyraUi.badge(service, FamilyCategory.byKey(category)?.title ?: "Administrator boundary"))
            addView(LyraUi.text(service, heading, 28f, bold = true))
            addView(LyraUi.text(service, explanation, 15f, LyraUi.muted))
            addView(LyraUi.text(service, host, 13f, LyraUi.muted))
            addView(LyraUi.button(service, "Leave this page") {
                if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) hide()
            })
            addView(LyraUi.button(service, "Go to Home", false) {
                if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) hide()
            })
            addView(LyraUi.link(service, "Ask an administrator") {
                runCatching { service.startActivity(Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onSuccess { hide() }
            })
        }
        if (surface.show(card, "Website blocked by administrator settings")) { shownHost = host; shownCategory = category }
    }
    fun hide() { surface.hide(); shownHost = null; shownCategory = null }
}
