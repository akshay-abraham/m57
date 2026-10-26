// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker.family

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

object FamilyControl {
    fun changed(context: Context) {
        FamilySyncJob.schedule(context, true)
        FamilyRepository.get(context).reload { reconcile(context) }
    }
    fun reconcile(context: Context) {
        val app = context.applicationContext
        val store = FamilyStore(app)
        if (!store.vpnRequested || (store.enabled.isEmpty() && store.block.isEmpty())) {
            app.stopService(Intent(app, FamilyVpnService::class.java))
            return
        }
        if (FamilyVpnService.running) return
        if (FamilyRepository.get(app).index.size() == 0) { store.vpnMessage("Waiting for enabled lists to download"); return }
        if (VpnService.prepare(app) != null) { store.vpnMessage("Android VPN approval needed; start from Family browsing"); return }
        runCatching { app.startForegroundService(Intent(app, FamilyVpnService::class.java)) }
            .onFailure { store.vpnMessage("Could not start in the background. Start from Family browsing.") }
    }
}

class FamilyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        FamilySyncJob.schedule(context)
        FamilyRepository.get(context).reload { FamilyControl.reconcile(context) }
    }
}
