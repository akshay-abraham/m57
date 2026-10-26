// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import org.junit.Assert.*
import org.junit.Test

class AppInfoPolicyTest {
    private val settings = AppInfoPolicy.settingsPackage
    private fun label(id: String, text: String = "") = BlockPolicy.Label("$settings:id/$id", text)
    private fun matches(labels: List<BlockPolicy.Label>, packageName: String = settings) =
        AppInfoPolicy.isOwnAppInfo(packageName, labels, "M57", "com.lyra.blocker")

    @Test fun `own App info header and actions qualify repeatedly`() {
        val screen = listOf(label("entity_header_title", "M57"), label("button1"), label("button2"), label("button3"))
        repeat(3) { assertTrue(matches(screen)) }
    }
    @Test fun `own package identity and explicit action controls qualify`() {
        assertTrue(matches(listOf(label("app_name", "com.lyra.blocker"), label("force_stop_button"))))
        assertTrue(matches(listOf(label("app_label", " M57 "), label("uninstall_button"))))
    }
    @Test fun `another apps info does not qualify`() {
        assertFalse(matches(listOf(label("entity_header_title", "Chrome"), label("button1"), label("button2"))))
        assertFalse(matches(listOf(label("entity_header_title", "M57 notes"), label("button1"), label("button2"))))
    }
    @Test fun `app search list and accessibility entries are not App info`() {
        assertFalse(matches(listOf(label("title", "M57"), label("button1"), label("button2"))))
        assertFalse(matches(listOf(label("entity_header_title", "M57"))))
        assertFalse(matches(listOf(label("entity_header_title", "M57"), label("button1"))))
    }
    @Test fun `an unrelated package or dialog controls cannot impersonate Settings app actions`() {
        val screen = listOf(label("entity_header_title", "M57"), label("button1"), label("button2"))
        assertFalse(matches(screen, "com.example.app"))
        assertFalse(matches(listOf(label("entity_header_title", "M57"),
            BlockPolicy.Label("android:id/button1", "OK"), BlockPolicy.Label("android:id/button2", "Cancel"))))
    }
}
