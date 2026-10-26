// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import java.util.concurrent.Executors
import com.lyra.blocker.family.FamilyControl
import com.lyra.blocker.family.FamilyRepository
import com.lyra.blocker.family.FamilyStore
import com.lyra.blocker.family.FamilySyncJob

class MainActivity : Activity() {
    companion object {
        const val SETTINGS_GUARD_EXTRA = "com.lyra.blocker.SETTINGS_GUARD_NOTICE"
        private const val FAMILY_VPN_REQUEST = 5703
    }
    private lateinit var store: ProtectionStore
    private val worker = Executors.newSingleThreadExecutor()
    private val refresh = Handler(Looper.getMainLooper())
    private var generation = 0
    private var unlocked = false
    private var controlsQuery = ""
    private var returningFromBattery = false
    private var pendingFamilyVpn = false
    private val familyPages by lazy { FamilyPages(this, { unlocked }, { navigate(it) }, { requestFamilyVpn() }) }
    private var dialog: AlertDialog? = null
    private var backAction: (() -> Unit)? = null
    private var backRegistered = false
    private val backCallback = OnBackInvokedCallback { backAction?.invoke() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProtectionStore(this)
        FamilySyncJob.schedule(this)
        FamilyRepository.get(this).reload { FamilyControl.reconcile(this) }
    }
    override fun onResume() {
        super.onResume()
        val guarded = intent.getBooleanExtra(SETTINGS_GUARD_EXTRA, false)
        intent.removeExtra(SETTINGS_GUARD_EXTRA)
        val refreshBattery = returningFromBattery && unlocked
        returningFromBattery = false
        if (guarded) showSettingsGuardNotice()
        else if (refreshBattery) navigate(AdminPage.DEVICE_ACCESS)
        else showHome()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onStop() {
        generation++
        refresh.removeCallbacksAndMessages(null)
        unlocked = false
        controlsQuery = ""
        dialog?.dismiss()
        dialog = null
        super.onStop()
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }

    private fun setBack(action: (() -> Unit)?) {
        backAction = action
        if (backRegistered) onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        backRegistered = action != null
        if (backRegistered) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
    }

    private fun page(destination: AdminPage? = null): LinearLayout {
        generation++
        refresh.removeCallbacksAndMessages(null)
        val shell = PageShell(this, destination?.tab) { tab ->
            navigate(when (tab) {
                AdminTab.OVERVIEW -> AdminPage.OVERVIEW
                AdminTab.CONTROLS -> AdminPage.CONTROLS
                AdminTab.SETTINGS -> AdminPage.SETTINGS
            })
        }
        setContentView(shell)
        shell.requestApplyInsets()
        if (destination != null) {
            val parent = destination.parent ?: AdminPage.OVERVIEW
            setBack { if (destination == AdminPage.OVERVIEW) lockAndHome() else navigate(parent) }
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(LyraUi.link(this, if (destination.parent != null) "‹  ${parent.title}" else "ADMIN SPACE") {
                if (destination.parent != null) navigate(parent)
            }.apply {
                gravity = Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                if (destination.parent == null) { isClickable = false; isFocusable = false }
            })
            header.addView(LyraUi.link(this, "Lock & leave") { lockAndHome() })
            shell.content.addView(header)
        } else setBack(null)
        return shell.content
    }

    private fun accessibilityEnabled(): Boolean = getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == packageName }

    private fun lockAndHome() { unlocked = false; showHome() }
    private fun openLyra() {
        if (!LyraLauncher.open(this, store)) toast("Install Chrome or another browser to open Lyra.")
    }
    private fun title(page: LinearLayout, heading: String, description: String) {
        page.addView(LyraUi.text(this, heading, 32f, bold = true))
        page.addView(LyraUi.text(this, description, 15f, LyraUi.muted))
    }
    private fun routeCard(page: LinearLayout, icon: String, heading: String, description: String, action: () -> Unit) {
        page.addView(LyraUi.card(this).apply {
            background = RippleDrawable(ColorStateList.valueOf(0x15F45B0B), LyraUi.glass(this@MainActivity), null)
            addView(LyraUi.feature(this@MainActivity, icon, "$heading  ›", description))
            isClickable = true; isFocusable = true
            contentDescription = "$heading. $description"
            descendantFocusability = LinearLayout.FOCUS_BLOCK_DESCENDANTS
            setOnClickListener { action() }
        })
    }

    private fun showHome() {
        unlocked = false
        val page = page()
        val ready = accessibilityEnabled() && store.hasCredential && store.consented
        page.addView(LyraUi.badge(this, when {
            !store.hasCredential -> "YOUR NEXT CHAPTER"
            !ready -> "SETUP NEEDS ATTENTION"
            store.paused -> "PROTECTION PAUSED"
            else -> "PROTECTION IS ON"
        }))
        title(page, "Your space to think.", "A little more intention. Thoughtful AI, fewer distractions, and more room for you.")
        if (!ready) {
            routeCard(page, "↗", "M57 is not set up", "Take a few guided steps to protect this learning space.") {
                if (store.hasCredential) askPassword() else consentAndSetup()
            }
        }
        page.addView(LyraUi.card(this).apply {
            addView(LyraUi.feature(this@MainActivity, "AI", "AI, through Lyra", "Guided learning across supported AI apps and websites."))
            addView(LyraUi.divider(this@MainActivity))
            addView(LyraUi.feature(this@MainActivity, "Ⅱ", "A little less scrolling", "A gentle pause when YouTube Shorts takes over."))
        })
        page.addView(LyraUi.button(this, "Open Lyra  ↗") { openLyra() })
        page.addView(LyraUi.button(this, if (store.hasCredential) "Administrator access" else "Set up M57", false) {
            if (store.hasCredential) askPassword() else consentAndSetup()
        })
        page.addView(LyraUi.link(this, "Set up or find the Lyra PWA") { showPwaGuide() })
        page.addView(LyraUi.link(this, "About & open-source licenses") { showLicenses() })
    }

    private fun showSettingsGuardNotice() {
        unlocked = false
        val page = page()
        setBack { showHome() }
        page.addView(LyraUi.badge(this, "ADMINISTRATOR ACCESS NEEDED"))
        title(page, "Make changes from M57.", "To change M57's accessibility or App info settings, unlock the administrator space first.")
        page.addView(LyraUi.text(this, "You can pause protection from Overview, or allow 60 seconds of settings access under Settings → Device access. Protection stays in place until an administrator authorises a change.", 15f, LyraUi.muted))
        page.addView(LyraUi.button(this, "Administrator access") { askPassword() })
        page.addView(LyraUi.link(this, "Back to Home") { showHome() })
    }

    /** Every administrator route goes through this guard; no Intent can unlock it. */
    private fun navigate(destination: AdminPage) {
        if (!unlocked || !store.hasPassword) { lockAndHome(); return }
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(window.decorView.windowToken, 0)
        when (destination) {
            AdminPage.OVERVIEW -> showOverview()
            AdminPage.CONTROLS -> showControls()
            AdminPage.SETTINGS -> showAdminSettings()
            AdminPage.AI -> showAiSettings()
            AdminPage.SHORT_VIDEOS -> showShortVideos()
            AdminPage.FAMILY -> showFamily()
            AdminPage.FAMILY_CATEGORY -> familyPages.category(page(AdminPage.FAMILY_CATEGORY))
            AdminPage.FAMILY_EXCEPTIONS -> familyPages.exceptions(page(AdminPage.FAMILY_EXCEPTIONS))
            AdminPage.FAMILY_SOURCES -> familyPages.sources(page(AdminPage.FAMILY_SOURCES))
            AdminPage.DEVICE_ACCESS -> showDeviceAccess()
            AdminPage.PAUSE -> showPauseOptions()
            AdminPage.CUSTOM_PAUSE -> showCustomPause()
        }
    }

    private fun showOverview() {
        val page = page(AdminPage.OVERVIEW)
        title(page, "A little more balance.", "Your family's learning space, with the controls close at hand.")
        val access = accessibilityEnabled()
        page.addView(LyraUi.card(this).apply {
            addView(LyraUi.badge(this@MainActivity, when {
                !access -> "ACCESSIBILITY NEEDED"
                store.paused -> "PAUSED"
                else -> "PROTECTION ACTIVE"
            }))
            addView(LyraUi.text(this@MainActivity, when {
                !access -> "One more step to get ready."
                store.paused -> "Taking a short break."
                else -> "More room for learning."
            }, 23f, bold = true))
            addView(LyraUi.text(this@MainActivity, pauseDescription(), 14f, LyraUi.muted))
            if (store.paused) addView(LyraUi.button(this@MainActivity, "Resume protection") {
                if (unlocked) { store.resume(); navigate(AdminPage.OVERVIEW) }
            })
        })
        page.addView(Switch(this).apply {
            text = "Protection enabled"
            textSize = 17f
            setTextColor(LyraUi.ink)
            minimumHeight = LyraUi.dp(this@MainActivity, 56)
            isChecked = !store.paused
            setOnClickListener {
                if (!unlocked) return@setOnClickListener
                if (store.paused) { store.resume(); navigate(AdminPage.OVERVIEW) }
                else { isChecked = true; navigate(AdminPage.PAUSE) }
            }
        })
        if (!access) routeCard(page, "⌘", "Finish device setup", "Enable accessibility and review removal protection.") { navigate(AdminPage.DEVICE_ACCESS) }
        routeCard(page, "↗", "Make it yours", "Find AI, short-video and family controls in one place.") { navigate(AdminPage.CONTROLS) }
        routeCard(page, "Ⅱ", "Protection schedule", "Pause for a while, choose an end time or resume protection.") { navigate(AdminPage.PAUSE) }
        page.addView(LyraUi.button(this, "Open Lyra", false) { openLyra() })
        if (store.paused && !store.pauseIndefinite) {
            val current = generation
            refresh.postDelayed({ if (current == generation && unlocked && !isDestroyed) navigate(AdminPage.OVERVIEW) },
                store.pauseRemaining.coerceAtMost(86_400_000) + 100)
        }
    }

    private fun pauseDescription(): String = when {
        store.pauseIndefinite -> "Protection is off until an administrator turns it back on."
        store.paused -> "Resumes ${java.time.format.DateTimeFormatter.ofPattern("EEE d MMM · HH:mm z").format(java.time.Instant.ofEpochMilli(store.pauseEndsAt).atZone(java.time.ZoneId.systemDefault()))}."
        else -> "Manage destinations, reminders and device access from your admin space."
    }

    private fun showPauseOptions() {
        val page = page(AdminPage.PAUSE)
        title(page, "Make room for a break.", "Pause protection on your terms. AI rules, Shorts reminders and the App info guard pause together.")
        page.addView(LyraUi.text(this, pauseDescription(), 15f, LyraUi.muted))
        listOf(15 to "15 minutes", 60 to "1 hour", 180 to "3 hours").forEach { (minutes, label) ->
            page.addView(LyraUi.button(this, label, false) {
                if (unlocked) { store.pauseFor(minutes); navigate(AdminPage.OVERVIEW) }
            })
        }
        routeCard(page, "◷", "Choose a date & time", "Set exactly when protection should return.") { navigate(AdminPage.CUSTOM_PAUSE) }
        page.addView(LyraUi.button(this, "Until I turn it back on", false) {
            if (unlocked) { store.pauseAlways(); navigate(AdminPage.OVERVIEW) }
        })
        if (store.paused) page.addView(LyraUi.button(this, "Resume protection now") {
            if (unlocked) { store.resume(); navigate(AdminPage.OVERVIEW) }
        })
        page.addView(LyraUi.text(this, "Your chosen pause survives a restart. Returning here to change it requires the administrator password.", 13f, LyraUi.muted))
    }

    private fun showCustomPause() {
        val page = page(AdminPage.CUSTOM_PAUSE)
        title(page, "Pick the moment.", "Choose when this learning space should settle back into its routine.")
        val picker = CustomPausePicker(this)
        page.addView(picker)
        val feedback = LyraUi.text(this, "", 14f, LyraUi.muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        page.addView(feedback)
        page.addView(LyraUi.button(this, "Pause until this time") {
            if (!unlocked) return@button
            val deadline = picker.selectedEpoch()
            if (deadline == null || deadline <= System.currentTimeMillis()) {
                feedback.text = "Choose a valid date and time in the future."
            } else { store.pauseUntil(deadline); navigate(AdminPage.OVERVIEW) }
        })
    }

    private fun showControls() {
        val page = page(AdminPage.CONTROLS)
        title(page, "Find your balance.", "One place for every kind of protection. Choose a category or search for a setting.")
        val search = LyraUi.field(EditText(this)).apply {
            hint = "Search AI, Shorts, websites, permissions…"
            contentDescription = "Search protection controls"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(controlsQuery)
        }
        page.addView(search)
        val results = LyraUi.column(this, 0)
        page.addView(results)
        fun render(query: String) {
            results.removeAllViews()
            val matches = controlCategories.filter { it.matches(query) }
            if (matches.isEmpty()) results.addView(LyraUi.text(this, "No matching controls. Try AI, Shorts, websites or permissions.", 15f, LyraUi.muted))
            matches.forEach { category ->
                routeCard(results, category.icon, category.page.title, category.description) { navigate(category.page) }
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                controlsQuery = s?.toString().orEmpty()
                render(controlsQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        render(controlsQuery)
    }

    private fun showAdminSettings() {
        val page = page(AdminPage.SETTINGS)
        title(page, "The essentials.", "Your administrator password, device access and app information.")
        routeCard(page, "⌘", "Device access", "Accessibility, removal protection and authorised App info access.") { navigate(AdminPage.DEVICE_ACCESS) }
        routeCard(page, "✳", "Change password", "Choose a long, memorable password that stays with the administrator.") { showPasswordSetup() }
        routeCard(page, "↗", "Connect the Lyra PWA", "Installation help and the saved destination.") { showPwaGuide() }
        routeCard(page, "i", "About & licenses", "M57, its Curbox roots and open-source notices.") { showLicenses() }
        page.addView(LyraUi.button(this, "Lock administrator space", false) { lockAndHome() })
    }

    private fun showShortVideos() {
        val page = page(AdminPage.SHORT_VIDEOS)
        title(page, "A pause in the scroll.", "A little space to choose what comes next.")
        page.addView(LyraUi.card(this).apply {
            addView(LyraUi.badge(this@MainActivity, "CURRENT BEHAVIOUR"))
            addView(LyraUi.text(this@MainActivity, "YouTube Shorts", 22f, bold = true))
            addView(LyraUi.text(this@MainActivity, "M57 shows a gentle, dismissible reminder on supported Shorts screens. Ordinary videos stay available. This build does not hard-block Shorts.", 15f, LyraUi.muted))
        })
        page.addView(LyraUi.text(this, "Next in this space", 20f, bold = true))
        page.addView(LyraUi.text(this, "A dedicated toggle, administrator-selected behaviour, Instagram Reels with the home feed allowed, and experimental controls for other short-video apps are still to come.", 15f, LyraUi.muted))
    }

    private fun showFamily() {
        familyPages.home(page(AdminPage.FAMILY))
    }

    private fun requestFamilyVpn() {
        if (!unlocked) return
        val family = FamilyStore(this)
        if (family.enabled.isEmpty() && family.block.isEmpty()) { toast("Enable a category or add blocked domains first."); return }
        val consent = VpnService.prepare(this)
        if (consent != null) {
            pendingFamilyVpn = true
            runCatching { startActivityForResult(consent, FAMILY_VPN_REQUEST) }
                .onFailure { pendingFamilyVpn = false; toast("Android could not open VPN approval.") }
        } else {
            family.vpnRequested = true
            FamilySyncJob.schedule(this, true)
            FamilyControl.reconcile(this)
            navigate(AdminPage.FAMILY)
        }
    }

    @Deprecated("Platform result callback retained without an AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FAMILY_VPN_REQUEST || !pendingFamilyVpn) return
        pendingFamilyVpn = false
        if (resultCode == RESULT_OK && VpnService.prepare(this) == null) {
            FamilyStore(this).vpnRequested = true
            FamilySyncJob.schedule(this, true)
            FamilyControl.reconcile(this)
        } else FamilyStore(this).vpnMessage("VPN approval was not granted")
    }

    private fun input(page: LinearLayout, label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        page.addView(LyraUi.text(this, label, 14f, LyraUi.muted, true).apply { setPadding(0, LyraUi.dp(this@MainActivity, 16), 0, 0) })
        return LyraUi.field(EditText(this)).apply { setText(value); inputType = type; page.addView(this) }
    }

    private fun showAiSettings() {
        val page = page(AdminPage.AI)
        title(page, "AI, through Lyra.", "Choose the learning space M57 opens and how the handoff feels.")
        val destination = LyraUi.card(this).also { page.addView(it) }
        val url = input(destination, "Lyra HTTPS address", store.destination, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val seconds = input(destination, "Open Lyra after (3–15 seconds)", store.countdown.toString(), InputType.TYPE_CLASS_NUMBER)
        val advanced = LyraUi.card(this).apply { visibility = View.GONE }
        page.addView(LyraUi.link(this, "PWA and advanced app rules  +") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }.apply { gravity = Gravity.START })
        page.addView(advanced)
        val webapk = input(advanced, "PWA package (optional; blank = automatic)", store.pwaPackage)
        advanced.addView(LyraUi.button(this, "Find installed Lyra PWA", false) { findInstalledPwa(url.text.toString().trim(), webapk) })
        advanced.addView(LyraUi.link(this, "How to install and find the PWA") { showPwaGuide() })
        val extras = input(advanced, "Extra AI package IDs (one per line)", store.extraPackages.joinToString("\n"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        advanced.addView(LyraUi.text(this, "Included apps: ${BlockPolicy.apps.values.distinct().joinToString(", ")}", 12f, LyraUi.muted))
        page.addView(LyraUi.button(this, "Save AI settings") {
            if (!unlocked) return@button
            val address = url.text.toString().trim()
            val pwa = webapk.text.toString().trim()
            val countdown = seconds.text.toString().toIntOrNull()
            val packages = extras.text.toString().lines().map(String::trim).filter(String::isNotEmpty).toSet()
            when {
                !BlockPolicy.validDestination(address) -> url.error = "Enter an HTTPS address without credentials or a fragment."
                pwa.isNotEmpty() && !BlockPolicy.validPackage(pwa) -> webapk.error = "Enter an Android package ID."
                countdown == null || countdown !in 3..15 -> seconds.error = "Choose 3–15 seconds."
                !packages.all(BlockPolicy::validPackage) || packageName in packages || "com.android.chrome" in packages || pwa in packages -> extras.error = "Use package IDs; keep M57, its PWA and Chrome unblocked."
                else -> {
                    store.destination = address; store.pwaPackage = pwa
                    store.countdown = countdown; store.extraPackages = packages
                    toast("AI settings saved.")
                }
            }
        })
    }

    private fun showDeviceAccess() {
        val page = page(AdminPage.DEVICE_ACCESS)
        title(page, "Ready on this device.", "Review access and make intentional changes to protection.")
        page.addView(LyraUi.badge(this, if (accessibilityEnabled()) "ACCESSIBILITY ENABLED" else "ACCESSIBILITY NEEDED"))
        page.addView(LyraUi.button(this, "Open accessibility settings", false) {
            confirm("Allow M57 to protect your focus", getString(R.string.accessibility_description)) {
                store.consent()
                store.allowAppInfoAccess()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        page.addView(LyraUi.text(this, "Leaving M57 locks the administrator space. Return and unlock to continue.", 13f, LyraUi.muted))
        BackgroundHelp.forDevice(Build.MANUFACTURER, Build.BRAND)?.let { guide ->
            val exempt = BackgroundAccess.isExempt(this)
            page.addView(LyraUi.card(this).apply {
                addView(LyraUi.text(this@MainActivity, "Keep M57 running.", 22f, bold = true))
                addView(LyraUi.badge(this@MainActivity, if (exempt) "ANDROID BATTERY EXEMPTION ALLOWED" else "BATTERY ACCESS TO REVIEW"))
                addView(LyraUi.text(this@MainActivity, "Detected ${guide.manufacturer}. Allow M57 to run without Android battery optimisation, then review any extra startup restrictions on this phone.", 14f, LyraUi.muted))
                if (!exempt) addView(LyraUi.button(this@MainActivity, "Allow background running") {
                    startBatteryAction { BackgroundAccess.requestExemption(this@MainActivity) }
                })
                addView(LyraUi.button(this@MainActivity, "${guide.manufacturer} background settings", false) {
                    startBatteryAction { BackgroundAccess.openManufacturerSettings(this@MainActivity, guide.vendor) }
                })
                val troubleshooting = LyraUi.column(this@MainActivity, 0).apply { visibility = View.GONE }
                addView(LyraUi.link(this@MainActivity, "Troubleshooting & extra help  +") {
                    troubleshooting.visibility = if (troubleshooting.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                })
                addView(troubleshooting)
                troubleshooting.addView(LyraUi.text(this@MainActivity, "If M57 still stops in the background, the manufacturer's guide can help. Choose M57 where it asks for an app; instructions vary by model and software version.", 14f, LyraUi.muted))
                troubleshooting.addView(LyraUi.text(this@MainActivity, guide.url, 13f, LyraUi.muted))
                troubleshooting.addView(LyraUi.button(this@MainActivity, "Open ${guide.manufacturer} troubleshooting guide", false) {
                    if (unlocked) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guide.url))) }
                        .onFailure { toast("No browser could open the troubleshooting guide.") }
                })
                addView(LyraUi.text(this@MainActivity, "Android's exemption and manufacturer startup controls are separate. An exemption can help, but does not guarantee the phone will never stop M57.", 13f, LyraUi.muted))
            })
        }
        page.addView(LyraUi.button(this, "Add removal protection", false) {
            confirm("Make accidental removal harder", "Optional administrator access adds a deactivation step before uninstalling M57. Android Settings can still revoke it. No lock, wipe or password policies are requested.") {
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ManagedProtection.component(this@MainActivity))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Add a confirmation step before removing M57.")
                })
            }
        })
        if (ManagedProtection.isOwner(this)) page.addView(LyraUi.button(this, "Apply managed-device protection", false) {
            confirm("Apply managed protection?", "Prevent uninstalling, force-stopping and clearing M57 using Android device-owner policy. Your administrator password releases these restrictions here.") {
                runCatching { ManagedProtection.apply(this) }.onSuccess { toast("Managed protection applied.") }.onFailure { toast("Policy could not be applied.") }
            }
        })
        page.addView(LyraUi.button(this, "Release removal protection", false) {
            confirm("Release removal protection?", "Remove M57's administrator policies and device ownership, if present. App info will be available for 60 seconds.") {
                runCatching { ManagedProtection.release(this) }.onSuccess {
                    store.allowAppInfoAccess(); toast("Removal protection released. App info is available for 60 seconds.")
                    navigate(AdminPage.DEVICE_ACCESS)
                }.onFailure { toast("Could not release policy.") }
            }
        })
        page.addView(LyraUi.button(this, "Open App info · allow 60 seconds", false) {
            if (unlocked) {
                store.allowAppInfoAccess()
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })
        page.addView(LyraUi.text(this, "Opening accessibility settings or App info from this page allows 60 seconds of maintenance access. The guard resumes afterwards. Detection depends on the device's Settings layout and still needs verification on your phone.", 13f, LyraUi.muted))
    }

    private fun consentAndSetup() {
        if (store.hasCredential) { askPassword(); return }
        val page = page()
        setBack { showHome() }
        title(page, "A thoughtful start.", "First, understand what M57 can see and do.")
        page.addView(LyraUi.card(this).apply { addView(LyraUi.text(this@MainActivity, getString(R.string.accessibility_description), 15f, LyraUi.muted)) })
        page.addView(LyraUi.text(this, "Next, create an administrator password. Keep it private and do not share it with the child using this device.", 15f, LyraUi.muted))
        page.addView(LyraUi.button(this, "About the open-source app", false) { showLicenses() })
        page.addView(LyraUi.button(this, "I agree · create password") { showPasswordSetup(initialConsent = true) })
        page.addView(LyraUi.link(this, "Back to Home") { showHome() })
    }

    private fun startBatteryAction(action: () -> Boolean) {
        if (!unlocked) return
        store.allowAppInfoAccess()
        returningFromBattery = true
        if (!action()) {
            returningFromBattery = false
            toast("This phone could not open that battery setting. Try the troubleshooting guide.")
        }
    }

    private fun passwordField(label: String, legacy: Boolean = false) = LyraUi.field(EditText(this)).apply {
        hint = label
        setSingleLine(true)
        inputType = if (legacy) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        filters = arrayOf(android.text.InputFilter.LengthFilter(if (legacy) 12 else 256))
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        isSaveEnabled = false
    }

    private fun askPassword() {
        if (!store.hasCredential) { consentAndSetup(); return }
        val page = page()
        setBack { showHome() }
        val legacy = store.needsPasswordUpgrade
        title(page, if (legacy) "A stronger next step." else "Your admin space.",
            if (legacy) "Verify your existing PIN once, then replace it with a stronger administrator password."
            else "A little privacy for the person looking after this device.")
        val field = passwordField(if (legacy) "Current administrator PIN" else "Administrator password", legacy)
        page.addView(field)
        val feedback = LyraUi.text(this, "", 14f, LyraUi.muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        page.addView(feedback)
        fun waitMessage() = "Please wait ${store.retrySeconds} seconds before trying again."
        if (store.retrySeconds > 0) feedback.text = waitMessage()
        lateinit var submit: Button
        submit = LyraUi.button(this, if (legacy) "Verify PIN" else "Unlock administrator space") {
            if (store.retrySeconds > 0) { feedback.text = waitMessage(); return@button }
            val value = field.text.toString()
            field.text.clear()
            backgroundWork(submit, { store.verifyCredential(value) }) { valid ->
                if (valid) {
                    unlocked = true
                    if (legacy) showPasswordSetup() else navigate(AdminPage.OVERVIEW)
                } else feedback.text = if (store.retrySeconds > 0) waitMessage() else "That ${if (legacy) "PIN" else "password"} did not match. Please try again."
            }
        }
        page.addView(submit)
        page.addView(LyraUi.text(this, "Keep your password safe. Developer-assisted online recovery is planned; this build has no password-reset service or master password.", 13f, LyraUi.muted))
        page.addView(LyraUi.link(this, "Back to Home") { showHome() })
    }

    private fun showPasswordSetup(initialConsent: Boolean = false) {
        if (store.hasCredential && !unlocked) { lockAndHome(); return }
        if (!store.hasCredential && !initialConsent) { consentAndSetup(); return }
        val page = page()
        setBack { if (store.hasPassword && unlocked) navigate(AdminPage.SETTINGS) else lockAndHome() }
        title(page, if (store.needsPasswordUpgrade) "Make the switch." else "Keep the keys with you.", "Use at least 12 characters. A long, memorable passphrase is welcome.")
        val first = passwordField("Administrator password")
        val second = passwordField("Confirm password")
        page.addView(first); page.addView(second)
        val feedback = LyraUi.text(this, "12–128 characters · spaces and passphrases supported", 14f, LyraUi.muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        page.addView(feedback)
        first.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString().orEmpty()
                feedback.text = PasswordCredential.problem(value) ?: if (value.codePointCount(0, value.length) >= 20)
                    "Good length. Make it unique and hard to guess." else "Meets the minimum. A longer, unique passphrase is even better."
            }
        })
        val reveal = CheckBox(this).apply {
            text = "Show passwords"; setTextColor(LyraUi.muted)
            setOnCheckedChangeListener { _, checked ->
                listOf(first, second).forEach { field ->
                    val position = field.selectionStart.coerceAtLeast(0)
                    field.transformationMethod = if (checked) null else android.text.method.PasswordTransformationMethod.getInstance()
                    field.setSelection(position.coerceAtMost(field.length()))
                }
            }
        }
        page.addView(reveal)
        page.addView(LyraUi.card(this).apply {
            addView(LyraUi.text(this@MainActivity, "For the administrator only.", 19f, bold = true))
            addView(LyraUi.text(this@MainActivity, "Do not forget this password or share it with the child. Keep a private copy somewhere safe. Online assisted recovery is still being designed and is not available in this build.", 14f, LyraUi.muted))
        })
        lateinit var submit: Button
        submit = LyraUi.button(this, "Save administrator password") {
            if (store.hasCredential && !unlocked) return@button
            val value = first.text.toString()
            val problem = PasswordCredential.problem(value)
            when {
                problem != null -> first.error = problem
                value != second.text.toString() -> second.error = "Both passwords need to match."
                else -> {
                    first.text.clear(); second.text.clear()
                    backgroundWork(submit, { PasswordCredential.create(value) }) { hash ->
                        runCatching { store.savePassword(hash); store.consent() }.onSuccess {
                            unlocked = true; navigate(AdminPage.OVERVIEW)
                        }.onFailure { feedback.text = "Your password could not be saved. Please try again." }
                    }
                }
            }
        }
        page.addView(submit)
    }

    /** Compute slowly off-thread; page/lifecycle changes invalidate results before any password save. */
    private fun <T> backgroundWork(button: Button, work: () -> T, after: (T) -> Unit) {
        val current = generation
        button.isEnabled = false
        worker.execute {
            val result = runCatching(work)
            runOnUiThread {
                if (isDestroyed || generation != current) return@runOnUiThread
                button.isEnabled = true
                result.onSuccess(after).onFailure { toast("That operation could not finish. Please try again.") }
            }
        }
    }

    private fun showLicenses() {
        val license = listOf("NOTICE", "COPYING", "Apache-2.0.txt").joinToString("\n\n") { file -> assets.open(file).bufferedReader().use { it.readText() } }
        dialog = AlertDialog.Builder(this).setTitle("${getString(R.string.app_name)} · ${packageManager.getPackageInfo(packageName, 0).versionName}")
            .setMessage("Based on Curbox · GNU GPL v3\nhttps://github.com/curbox-app/curbox-android\n\n$license")
            .setPositiveButton("Close", null).show().also(LyraUi::styleDialog)
    }
    private fun showPwaGuide(notFound: Boolean = false, destination: String = store.destination) {
        val message = (if (notFound) "No matching installed Lyra PWA was found.\n\n" else "") +
            "1. Open $destination in Chrome.\n\n2. Use Chrome's menu to install Lyra as an app.\n\n" +
            "3. Return to M57 → Administrator access → Controls → AI redirection → PWA and advanced app rules.\n\n" +
            "4. Select Find installed Lyra PWA, choose Lyra and save the AI settings.\n\n5. Use Open Lyra to test. Chrome is the fallback if discovery fails."
        dialog = AlertDialog.Builder(this).setTitle("Find your Lyra PWA").setMessage(message)
            .setNegativeButton("Close", null).setPositiveButton("Open in Chrome") { _, _ ->
                if (!LyraLauncher.openInChrome(this, destination)) toast("Install or enable Google Chrome first.")
            }.show().also(LyraUi::styleDialog)
    }
    private fun findInstalledPwa(destination: String, field: EditText) {
        if (!unlocked) return
        if (!BlockPolicy.validDestination(destination)) { toast("Enter a valid Lyra HTTPS address first."); return }
        val installed = LyraLauncher.installedPwas(this, destination)
        if (installed.isEmpty()) { showPwaGuide(true, destination); return }
        val names = installed.map { "${it.label}\n${it.packageName}" } + "Use automatic detection"
        dialog = AlertDialog.Builder(this).setTitle("Choose the installed Lyra PWA")
            .setItems(names.toTypedArray()) { _, index ->
                if (unlocked) { field.setText(installed.getOrNull(index)?.packageName.orEmpty()); toast("PWA selected. Save AI settings to apply it.") }
            }.setNegativeButton("Cancel", null).show().also(LyraUi::styleDialog)
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        if (!unlocked) return
        dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> if (unlocked) action() }.show().also(LyraUi::styleDialog)
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
