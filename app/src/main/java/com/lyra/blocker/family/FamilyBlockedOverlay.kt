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
    fun show(host: String, category: String) {
        if (surface.visible && shownHost == host) return
        hide()
        val source = category == "source"
        val adult = category == FamilyCategory.ADULT.key
        val card = LyraUi.column(service, 26).apply {
            addView(LyraUi.brand(service))
            addView(LyraUi.badge(service, FamilyCategory.byKey(category)?.title ?: if(source) "Administrator boundary" else "Family boundary"))
            addView(LyraUi.text(service, if (source) "Review with your administrator." else if (adult) "Curiosity is part of you." else "A little space to choose.", 28f, bold = true))
            addView(LyraUi.text(service, if (source) "Your administrator has restricted this device’s access to the M57 repository. They can pause protection or change the source-page guard in Settings. M57 remains free and open-source." else if (adult) "There is nothing to be ashamed of. This website is outside the boundaries chosen for this device. You can talk with a trusted adult about what you are curious about."
                else "This website is on a list enabled by your administrator. You can choose another page or ask them to review the boundary.", 15f, LyraUi.muted))
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
        if (surface.show(card, "Website blocked by your family settings")) shownHost = host
    }
    fun hide() { surface.hide(); shownHost = null }
}
