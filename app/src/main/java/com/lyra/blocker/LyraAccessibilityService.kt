// SPDX-License-Identifier: GPL-3.0-only
// Curbox's event-driven app/keyword blocking, reduced to the Lyra redirect policy.
package com.lyra.blocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.ArrayDeque

class LyraAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var store: ProtectionStore
    private lateinit var overlay: RedirectOverlay
    private lateinit var shortsOverlay: ShortsReminderOverlay
    private var shortsVisit = ShortsVisit()
    private data class Target(val packageName: String, val label: String, val reminderOnly: Boolean = false, val appInfo: Boolean = false)
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { checkCurrentWindow() }
    private val pauseExpiry = Runnable { checkCurrentWindow() }
    private val appInfoExpiry = Runnable { checkCurrentWindow() }
    private val appInfoRetry = Runnable { checkCurrentWindow() }
    private var lastAppInfoRedirect = -1_000L
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(recheck)
            handler.removeCallbacks(appInfoRetry)
            overlay.hide()
            shortsOverlay.hide()
            shortsVisit.shouldRemind(false, SystemClock.elapsedRealtime())
        }
    }
    private var receiverRegistered = false
    override fun onServiceConnected() {
        if (::overlay.isInitialized) overlay.hide()
        if (::shortsOverlay.isInitialized) shortsOverlay.hide()
        shortsVisit = ShortsVisit()
        if (::store.isInitialized) store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (receiverRegistered) unregisterReceiver(screenOff)
        store = ProtectionStore(this)
        overlay = RedirectOverlay(this, store) { expected ->
            currentTarget()?.let { !it.reminderOnly && !it.appInfo && it.packageName == expected } == true
        }
        shortsOverlay = ShortsReminderOverlay(this) { currentTarget()?.reminderOnly == true }
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        schedulePauseExpiry()
        scheduleAppInfoExpiry()
        handler.post(recheck)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::overlay.isInitialized) return
        // Window events from every app let us remove a shield as soon as the user
        // leaves the blocked app. Trees are read only for the supported packages.
        if (event.packageName?.toString() == packageName) return
        // Coalesce bursts without postponing the check indefinitely on busy screens.
        if (!handler.hasCallbacks(recheck)) handler.postDelayed(recheck, 80)
    }

    private fun checkCurrentWindow() {
        if (!::overlay.isInitialized) return
        val target = currentTarget()
        handler.removeCallbacks(appInfoRetry)
        val remind = shortsVisit.shouldRemind(target?.reminderOnly == true, SystemClock.elapsedRealtime())
        if (target == null) { overlay.hide(); shortsOverlay.hide(); return }
        if (target.appInfo) {
            overlay.hide()
            shortsOverlay.hide()
            val now = SystemClock.elapsedRealtime()
            if (now - lastAppInfoRedirect >= 1_000) {
                lastAppInfoRedirect = now
                performGlobalAction(GLOBAL_ACTION_BACK)
                if (!LyraLauncher.open(this, store)) performGlobalAction(GLOBAL_ACTION_HOME)
            }
            // Recheck the actual foreground before every retry, never redirect on a stale event.
            handler.postDelayed(appInfoRetry, 1_000)
        } else if (target.reminderOnly) {
            overlay.hide()
            if (remind && !shortsOverlay.show()) shortsVisit.retry()
        } else {
            shortsOverlay.hide()
            if (overlay.visible && overlay.blockedPackage != target.packageName) overlay.hide()
            overlay.show(target.packageName, target.label)
        }
    }

    private fun currentTarget(): Target? {
        if (!store.consented || !store.hasPin || store.paused ||
            !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        // Select an application window so our non-focusable shield and keyboards
        // cannot become the inspected root. API 34 no longer needs node recycling.
        val root = windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused)
        }?.root ?: rootInActiveWindow ?: return null
        val activePackage = root.packageName?.toString() ?: return null
        if (activePackage == packageName || activePackage == "com.android.systemui") return null
        if (activePackage == AppInfoPolicy.settingsPackage) {
            if (store.appInfoAccessRemaining > 0) return null
            val labels = collectLabels(root, false, true)
            return if (AppInfoPolicy.isOwnAppInfo(activePackage, labels, getString(R.string.app_name), packageName))
                Target(activePackage, "App info", appInfo = true) else null
        }
        // Shorts always takes the reminder path, including when the user has
        // added YouTube to their custom package list. Never launch Lyra for it.
        if (activePackage in ShortsPolicy.packages) {
            val visible = ShortsPolicy.playerIds.flatMap { id ->
                root.findAccessibilityNodeInfosByViewId("$activePackage:id/$id")
            }.filter { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                node.isVisibleToUser && Rect.intersects(bounds, Rect(0, 0,
                    resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels))
            }.mapNotNull { it.viewIdResourceName }
            return if (ShortsPolicy.isNativeShorts(activePackage, visible)) Target(activePackage, "YouTube Shorts", true) else null
        }
        val browserLabels = if (activePackage in BlockPolicy.browsers) collectLabels(root, true) else emptyList()
        if (browserLabels.any { ShortsPolicy.isShortsAddress(it.text) }) return Target(activePackage, "YouTube Shorts", true)
        val label = BlockPolicy.apps[activePackage]
            ?: activePackage.takeIf { it in store.extraPackages }?.let { "This AI app" }
        if (label != null) return Target(activePackage, label)
        if (activePackage !in BlockPolicy.whatsapp && activePackage != BlockPolicy.google && activePackage !in BlockPolicy.browsers) {
            return null
        }
        val labels = if (activePackage in BlockPolicy.browsers) browserLabels else collectLabels(root, false)
        val embedded = BlockPolicy.embeddedAi(activePackage, labels)
        val website = if (activePackage in BlockPolicy.browsers) labels.firstOrNull {
            it.id.substringAfterLast('/') in setOf("url_bar", "location_bar_edit_text") &&
                BlockPolicy.blockedAddress(it.text)
        } else null
        val reason = embedded ?: website?.let { "This AI website" }
        return reason?.let { Target(activePackage, it) }
    }

    private fun collectLabels(root: AccessibilityNodeInfo, browser: Boolean, settings: Boolean = false): List<BlockPolicy.Label> {
        val result = mutableListOf<BlockPolicy.Label>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        // Bound traversal. Only title/address-bar fields are read, never message bodies.
        val ids = if (settings) AppInfoPolicy.inspectedIds else if (browser) setOf("url_bar", "location_bar_edit_text") else setOf(
            "conversation_contact_name", "conversation_title", "toolbar_title", "action_bar_title", "assistant_title",
        )
        while (queue.isNotEmpty() && visited++ < 200) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty()
            if (node.isVisibleToUser && id.substringAfterLast('/') in ids) {
                result.add(BlockPolicy.Label(id, node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty()))
            }
            for (index in 0 until node.childCount.coerceAtMost(80)) {
                if (queue.size >= 200) break
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return result
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        handler.removeCallbacks(recheck)
        handler.post(recheck)
        // A pause expires even if the blocked app emits no new accessibility event.
        if (key == "pause_until" || key == "pause_boot") schedulePauseExpiry()
        if (key == "app_info_until" || key == "app_info_boot") scheduleAppInfoExpiry()
    }
    private fun schedulePauseExpiry() {
        handler.removeCallbacks(pauseExpiry)
        if (store.paused) handler.postDelayed(pauseExpiry, store.pauseRemaining + 100)
    }
    private fun scheduleAppInfoExpiry() {
        handler.removeCallbacks(appInfoExpiry)
        if (store.appInfoAccessRemaining > 0) handler.postDelayed(appInfoExpiry, store.appInfoAccessRemaining + 100)
    }
    override fun onInterrupt() {
        if (::overlay.isInitialized) overlay.hide()
        if (::shortsOverlay.isInitialized) shortsOverlay.hide()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        if (::shortsOverlay.isInitialized) shortsOverlay.hide()
        if (::store.isInitialized) store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (receiverRegistered) unregisterReceiver(screenOff)
        super.onDestroy()
    }
}
