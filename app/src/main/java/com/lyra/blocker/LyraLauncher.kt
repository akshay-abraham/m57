// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object LyraLauncher {
    data class InstalledPwa(val packageName: String, val label: String)

    fun installedPwas(context: Context, destination: String): List<InstalledPwa> {
        if (!BlockPolicy.validDestination(destination)) return emptyList()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destination)).addCategory(Intent.CATEGORY_BROWSABLE)
        return context.packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName.startsWith("org.chromium.webapk.") }
            .distinctBy { it.activityInfo.packageName }
            .map { InstalledPwa(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
    }

    fun openInChrome(context: Context, destination: String): Boolean {
        if (!BlockPolicy.validDestination(destination)) return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: ActivityNotFoundException) { false }
        catch (_: SecurityException) { false }
    }
    /** Resolve the installed WebAPK by its URL filter; never infer its generated package ID. */
    fun open(context: Context, store: ProtectionStore): Boolean {
        val base = Intent(Intent.ACTION_VIEW, Uri.parse(store.destination)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pwa = installedPwas(context, store.destination).firstOrNull()?.packageName
        val candidates = listOfNotNull(store.pwaPackage.takeIf { it.isNotBlank() }, pwa, "com.android.chrome")
        for (packageName in candidates.distinct()) {
            try {
                context.startActivity(Intent(base).apply { setPackage(packageName) })
                return true
            } catch (_: ActivityNotFoundException) {
                // Try Chrome, then the system HTTPS handler.
            } catch (_: SecurityException) {
                // Installed handler can be disabled or restricted by device policy.
            }
        }
        // A device without Chrome can still have another browser.
        return try { context.startActivity(base); true }
        catch (_: ActivityNotFoundException) { false }
        catch (_: SecurityException) { false }
    }
}
