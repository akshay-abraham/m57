// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class ProtectionAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Removing administrator access makes ${context.getString(R.string.app_name)} easier to uninstall. Manage protection in the app with the administrator PIN."
}

object ManagedProtection {
    fun component(context: Context) = ComponentName(context, ProtectionAdminReceiver::class.java)
    fun isOwner(context: Context) = context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)
    fun apply(context: Context) {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        check(manager.isDeviceOwnerApp(context.packageName)) { "Device-owner provisioning is required." }
        manager.setUninstallBlocked(component(context), context.packageName, true)
        manager.setUserControlDisabledPackages(component(context), listOf(context.packageName))
    }
    @Suppress("DEPRECATION")
    fun release(context: Context) {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        if (manager.isDeviceOwnerApp(context.packageName)) {
            manager.setUserControlDisabledPackages(component(context), emptyList())
            manager.setUninstallBlocked(component(context), context.packageName, false)
            manager.clearDeviceOwnerApp(context.packageName)
        } else if (manager.isAdminActive(component(context))) {
            manager.removeActiveAdmin(component(context))
        }
    }
}
