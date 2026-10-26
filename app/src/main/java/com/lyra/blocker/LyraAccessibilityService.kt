// SPDX-License-Identifier: (GPL-3.0-only AND AGPL-3.0-only)
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
import com.lyra.blocker.family.BrowserDomains
import com.lyra.blocker.family.FamilyStore
import com.lyra.blocker.family.FamilyRepository
import com.lyra.blocker.family.FamilyBlockedOverlay
import com.lyra.blocker.family.HostRules

class LyraAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var store: ProtectionStore
    private lateinit var overlay: RedirectOverlay
    private lateinit var shortsOverlay: ShortsReminderOverlay
    private lateinit var familyOverlay: FamilyBlockedOverlay
    private lateinit var familyStore: FamilyStore
    private lateinit var familyRepository: FamilyRepository
    private var shortsVisit = ShortsVisit()
    private var lastVideoKey:String?=null
    private data class Target(val packageName: String, val label: String, val reminderOnly: Boolean = false, val appInfo: Boolean = false, val familyCategory: String? = null, val videoKey:String?=null, val sourceHint:String="other")
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { checkCurrentWindow() }
    private val pauseExpiry = Runnable { checkCurrentWindow(); schedulePauseExpiry() }
    private val appInfoExpiry = Runnable { checkCurrentWindow() }
    private val appInfoRetry = Runnable { checkCurrentWindow() }
    private var lastAppInfoRedirect = -1_000L
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(recheck)
            handler.removeCallbacks(appInfoRetry)
            overlay.hide()
            shortsOverlay.hide()
            familyOverlay.hide()
            shortsVisit.shouldRemind(false, SystemClock.elapsedRealtime())
        }
    }
    private var receiverRegistered = false
    override fun onServiceConnected() {
        if (::overlay.isInitialized) overlay.hide()
        if (::shortsOverlay.isInitialized) shortsOverlay.hide()
        if (::familyOverlay.isInitialized) familyOverlay.hide()
        if (::familyStore.isInitialized) familyStore.prefs.unregisterOnSharedPreferenceChangeListener(this)
        shortsVisit = ShortsVisit()
        if (::store.isInitialized) store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (receiverRegistered) unregisterReceiver(screenOff)
        store = ProtectionStore(this)
        familyStore = FamilyStore(this)
        familyRepository = FamilyRepository.get(this)
        familyOverlay = FamilyBlockedOverlay(this)
        overlay = RedirectOverlay(this, store) { expected ->
            currentTarget()?.let { !it.reminderOnly && !it.appInfo && it.familyCategory == null && it.packageName == expected && it.sourceHint == overlay.sourceHint } == true
        }
        shortsOverlay = ShortsReminderOverlay(this) { currentTarget()?.reminderOnly == true }
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        familyStore.prefs.registerOnSharedPreferenceChangeListener(this)
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
        if (event.packageName?.toString() == packageName && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        // Coalesce bursts without postponing the check indefinitely on busy screens.
        if (!handler.hasCallbacks(recheck)) handler.postDelayed(recheck, 80)
    }

    private fun checkCurrentWindow() {
        if (!::overlay.isInitialized) return
        val target = currentTarget()
        if(target?.videoKey!=null&&target.videoKey!=lastVideoKey){shortsVisit=ShortsVisit();lastVideoKey=target.videoKey}
        handler.removeCallbacks(appInfoRetry)
        val remind = shortsVisit.shouldRemind(target?.reminderOnly == true, SystemClock.elapsedRealtime())
        if (target == null) { overlay.hide(); shortsOverlay.hide(); familyOverlay.hide(); return }
        if (target.familyCategory == null) familyOverlay.hide()
        if (target.familyCategory != null) {
            overlay.hide(); shortsOverlay.hide()
            familyOverlay.show(target.label, target.familyCategory)
            return
        }
        if (target.appInfo) {
            overlay.hide()
            shortsOverlay.hide()
            val now = SystemClock.elapsedRealtime()
            if (now - lastAppInfoRedirect >= 1_000) {
                lastAppInfoRedirect = now
                performGlobalAction(GLOBAL_ACTION_BACK)
                val opened = runCatching {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.SETTINGS_GUARD_EXTRA, true)
                    })
                    true
                }.getOrDefault(false)
                if (!opened && !LyraLauncher.open(this, store)) performGlobalAction(GLOBAL_ACTION_HOME)
            }
            // Recheck the actual foreground before every retry, never redirect on a stale event.
            handler.postDelayed(appInfoRetry, 1_000)
        } else if (target.reminderOnly) {
            overlay.hide()
            if ((store.videoMode=="block"||remind) && !shortsOverlay.show(target.label,store.videoMode=="block")) shortsVisit.retry()
        } else {
            shortsOverlay.hide()
            if (overlay.visible && (overlay.blockedPackage != target.packageName || overlay.sourceHint != target.sourceHint)) overlay.hide()
            overlay.show(target.packageName, target.label, target.sourceHint)
        }
    }

    private fun currentTarget(): Target? {
        if (!store.consented || !store.hasCredential || store.paused ||
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
            val appInfo = AppInfoPolicy.isOwnAppInfo(activePackage, labels, getString(R.string.app_name), packageName)
            val accessibility = AccessibilitySettingsPolicy.matches(activePackage, labels.map {
                AccessibilitySettingsPolicy.Node(it.id, it.text, it.checkable)
            }, getString(R.string.app_name), getString(R.string.accessibility_description))
            return if (appInfo || accessibility) Target(activePackage, "M57 settings", appInfo = true) else null
        }
        val mediaIds=MediaPolicy.inspectedNames(activePackage).flatMap { id -> root.findAccessibilityNodeInfosByViewId("$activePackage:id/$id") }
            .filter { node ->
                val bounds=Rect();node.getBoundsInScreen(bounds)
                (node.isVisibleToUser&&Rect.intersects(bounds,Rect(0,0,resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels))) ||
                    (activePackage in MediaPolicy.TIKTOK&&node.viewIdResourceName=="$activePackage:id/long_press_layout")
            }.mapNotNull {it.viewIdResourceName}.toSet()
        val facebookViewer=activePackage=="com.facebook.katana"&&root.findAccessibilityNodeInfosByText("Reels tab details").any {it.isVisibleToUser&&it.contentDescription?.toString()=="Reels tab details"}
        val nativeVideo=MediaPolicy.nativePlatform(activePackage,mediaIds,facebookViewer)
        if(nativeVideo!=null&&videoEnabled(nativeVideo))return Target(activePackage,MediaPolicy.LABELS[nativeVideo]?:"Short videos",reminderOnly=true,videoKey=nativeVideo)
        val browserLabels = if (activePackage in BrowserDomains.BROWSERS) collectLabels(root, true) else emptyList()
        if (store.repoGuard) for (item in browserLabels) {
            if (BrowserDomains.host(activePackage,item.id,item.text) != null && HandoffPolicy.repositoryAddress(item.text))
                return Target(activePackage,"M57 source repository",familyCategory="source")
        }
        if (familyStore.browserEnabled) for (item in browserLabels) {
            val host = BrowserDomains.host(activePackage, item.id, item.text) ?: continue
            val category = familyRepository.index.match(host) ?: continue
            return Target(activePackage, host, familyCategory = category)
        }
        val webVideo=browserLabels.firstNotNullOfOrNull {MediaPolicy.addressPlatform(it.text)}
        if(webVideo!=null&&videoEnabled(webVideo))return Target(activePackage,MediaPolicy.LABELS[webVideo]?:"Short videos",reminderOnly=true,videoKey=webVideo)
        if (!store.aiEnabled) return null
        val metaService = MetaPolicy.SERVICES[activePackage]
        val metaAllowed = metaService!=null && store.metaEnabled && metaService in store.metaServices
        val label = BlockPolicy.apps[activePackage]?.takeIf {
            activePackage !in store.disabledApps && (metaService != "meta" || metaAllowed)
        } ?: activePackage.takeIf { it in store.extraPackages && it !in BlockPolicy.apps }?.let { "This AI app" }
        if (label != null) return Target(activePackage, label, sourceHint=HandoffPolicy.source(label))
        if (activePackage !in MetaPolicy.SERVICES && activePackage != BlockPolicy.google && activePackage !in BrowserDomains.BROWSERS) {
            return null
        }
        val labels = if (activePackage in BrowserDomains.BROWSERS) browserLabels else collectLabels(root, false)
        val embedded = if (metaAllowed && labels.any { MetaPolicy.titleMatches(activePackage,it.id,it.text) || MetaPolicy.titleMatches(activePackage,it.id,it.description) }) "Meta AI"
            else if(activePackage==BlockPolicy.google)BlockPolicy.embeddedAi(activePackage,labels) else null
        val ownHost = HostRules.address(store.destination)
        val website = if (activePackage in BrowserDomains.BROWSERS) labels.firstOrNull {
            val host=BrowserDomains.host(activePackage,it.id,it.text)
            host!=null && host!=ownHost &&
                (BlockPolicy.domains.any { domain -> domain !in store.disabledDomains && (host==domain || host.endsWith(".$domain")) } ||
                    store.extraAiDomains.any { domain -> host==domain || host.endsWith(".$domain") })
        } else null
        val reason = embedded ?: website?.let { "This AI website" }
        return reason?.let { Target(activePackage, it, sourceHint=HandoffPolicy.source(website?.let { address -> BrowserDomains.host(activePackage,address.id,address.text) } ?: it)) }
    }

    private fun videoEnabled(key:String):Boolean=when(key){
        "youtube"->store.youtubeEnabled
        "instagram"->store.instagramEnabled
        else->store.extraVideosEnabled&&key in store.extraVideoApps
    }

    private fun collectLabels(root: AccessibilityNodeInfo, browser: Boolean, settings: Boolean = false): List<BlockPolicy.Label> {
        val result = mutableListOf<BlockPolicy.Label>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        // Bound traversal. Only title/address-bar fields are read, never message bodies.
        val ids = if (settings) AppInfoPolicy.inspectedIds + AccessibilitySettingsPolicy.INSPECTED_IDS else if (browser) BrowserDomains.INSPECTED_IDS else setOf(
            "conversation_contact_name", "conversation_title", "toolbar_title", "action_bar_title", "assistant_title", "thread_title", "thread_header_title", "thread_name",
        )
        while (queue.isNotEmpty() && visited++ < 200) {
            val node = queue.removeFirst()
            val id = if (browser && node.uniqueId == "ADDRESSBAR_URL_BOX") "${root.packageName}:id/ADDRESSBAR_URL_BOX" else node.viewIdResourceName.orEmpty()
            if (node.isVisibleToUser && id.substringAfterLast('/') in ids) {
                result.add(BlockPolicy.Label(id, node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty(), node.isCheckable))
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
        if (key == "pause_until" || key == "pause_boot" || key?.startsWith("protection_pause_") == true) schedulePauseExpiry()
        if (key == "app_info_until" || key == "app_info_boot") scheduleAppInfoExpiry()
    }
    private fun schedulePauseExpiry() {
        handler.removeCallbacks(pauseExpiry)
        if (store.paused && !store.pauseIndefinite) handler.postDelayed(pauseExpiry, store.pauseRemaining.coerceAtMost(86_400_000) + 100)
    }
    private fun scheduleAppInfoExpiry() {
        handler.removeCallbacks(appInfoExpiry)
        if (store.appInfoAccessRemaining > 0) handler.postDelayed(appInfoExpiry, store.appInfoAccessRemaining + 100)
    }
    override fun onInterrupt() {
        if (::overlay.isInitialized) overlay.hide()
        if (::shortsOverlay.isInitialized) shortsOverlay.hide()
        if (::familyOverlay.isInitialized) familyOverlay.hide()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        if (::shortsOverlay.isInitialized) shortsOverlay.hide()
        if (::familyOverlay.isInitialized) familyOverlay.hide()
        if (::familyStore.isInitialized) familyStore.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (::store.isInitialized) store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (receiverRegistered) unregisterReceiver(screenOff)
        super.onDestroy()
    }
}
