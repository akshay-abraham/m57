// SPDX-License-Identifier: (GPL-3.0-only AND AGPL-3.0-only)
// OEM entry points adapted from Curbox OemAutostartIntents; see NOTICE.
package com.lyra.blocker

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BackgroundAccess {
    fun isExempt(activity: Activity): Boolean = activity.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(activity.packageName)

    /** The OS grants/denies this request. Opening a screen never means permission was granted. */
    fun requestExemption(activity: Activity): Boolean {
        if (isExempt(activity)) return true
        return launch(activity, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${activity.packageName}"))) || openBatterySettings(activity)
    }

    fun openManufacturerSettings(activity: Activity, vendor: String): Boolean {
        val colorOs = listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        )
        val candidates = when (vendor) {
            "samsung" -> listOf("com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity")
            "xiaomi" -> listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
            "oppo", "realme" -> colorOs
            "vivo" -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            )
            "oneplus" -> listOf("com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity") + colorOs
            else -> return false
        }
        for ((pkg, name) in candidates) {
            if (launch(activity, Intent().setComponent(ComponentName(pkg, name)))) return true
        }
        return openBatterySettings(activity)
    }

    private fun openBatterySettings(activity: Activity): Boolean =
        launch(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
            launch(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))

    private fun launch(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: android.content.ActivityNotFoundException) { false }
    catch (_: SecurityException) { false }
}
