// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

/** Identify our App info page, without treating the app list/search as App info. */
object AppInfoPolicy {
    const val settingsPackage = "com.android.settings"
    val headerIds = setOf("entity_header_title", "app_name", "app_label", "application_name")
    val actionIds = setOf("button1", "button2", "button3", "force_stop_button", "uninstall_button")
    val inspectedIds = headerIds + actionIds

    fun isOwnAppInfo(
        packageName: String,
        labels: List<BlockPolicy.Label>,
        appName: String,
        ownPackage: String,
    ): Boolean {
        if (packageName != settingsPackage) return false
        val trusted = labels.filter { it.id.startsWith("$settingsPackage:id/") }
        val header = trusted.any {
            it.id.substringAfterLast('/') in headerIds &&
                (it.text.trim().equals(appName, true) || it.text.trim() == ownPackage)
        }
        if (!header) return false
        val controls = trusted.map { it.id.substringAfterLast('/') }.filter { it in actionIds }.toSet()
        // The AOSP action row uses button1/2/3. Explicit OEM action IDs also qualify.
        return "force_stop_button" in controls || "uninstall_button" in controls || controls.size >= 2
    }
}
