// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager

object LyraLauncher {
    data class InstalledPwa(val packageName: String, val label: String)

    fun installedPwas(context: Context, destination: String): List<InstalledPwa> {
        if (!BlockPolicy.validDestination(destination)) return emptyList()
        val pm = context.packageManager
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(destination)).addCategory(Intent.CATEGORY_BROWSABLE)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return (pm.queryIntentActivities(view, 0) + pm.queryIntentActivities(launcher, 0))
            .filter { it.activityInfo.packageName.startsWith("org.chromium.webapk.") }
            .distinctBy { it.activityInfo.packageName }
            .filter { result ->
                runCatching {
                    val metadata = pm.getApplicationInfo(result.activityInfo.packageName, PackageManager.GET_META_DATA).metaData
                    HandoffPolicy.inScope(destination,
                        metadata?.getString("org.chromium.webapk.shell_apk.startUrl").orEmpty(),
                        metadata?.getString("org.chromium.webapk.shell_apk.scope"))
                }.getOrDefault(false)
            }
            .map { InstalledPwa(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
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
    fun open(context: Context, store: ProtectionStore, source: String? = null): Boolean {
        if (!BlockPolicy.validDestination(store.destination)) return false
        val destination = Uri.parse(store.destination).buildUpon().apply {
            if (source != null) {
                clearQuery()
                appendQueryParameter("m57_source", source)
                appendQueryParameter("m57_intent", "new")
            }
        }.build()
        val base = Intent(Intent.ACTION_VIEW, destination).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pwas = installedPwas(context, store.destination).map { it.packageName }
        val candidates = listOfNotNull(store.pwaPackage.takeIf { it in pwas }) + pwas + "com.android.chrome"
        for (packageName in candidates.distinct()) {
            try {
                context.startActivity(Intent(base).apply { setPackage(packageName) })
                return true
            } catch (_: ActivityNotFoundException) {
                // Older WebAPKs may expose only their launcher activity. Scope was verified above.
                if (packageName in pwas) {
                    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launch != null) try {
                        context.startActivity(launch.apply { data = destination; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        return true
                    } catch (_: ActivityNotFoundException) { } catch (_: SecurityException) { }
                }
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
