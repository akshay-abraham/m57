// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var store: ProtectionStore
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var unlocked = false
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProtectionStore(this)
    }
    override fun onResume() {
        super.onResume()
        showHome()
    }
    override fun onStop() {
        generation++
        unlocked = false
        dialog?.dismiss()
        dialog = null
        super.onStop()
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }

    private fun page(): LinearLayout {
        val content = LyraUi.column(this)
        content.setPadding(content.paddingLeft, LyraUi.dp(this, 24), content.paddingRight, LyraUi.dp(this, 24))
        content.addView(LyraUi.brand(this))
        val scroll = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                LyraUi.background, Color.rgb(255, 247, 240), Color.rgb(247, 242, 235),
            ))
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
        return content
    }
    private fun accessibilityEnabled(): Boolean = getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == packageName }

    private fun showHome() {
        val page = page()
        val ready = accessibilityEnabled() && store.hasPin && store.consented
        val active = ready && !store.paused
        val status = when {
            !store.hasPin -> "LET’S GET STARTED"
            !accessibilityEnabled() -> "ACCESS NEEDED"
            store.paused -> "PAUSED · UP TO 5 MIN"
            active -> "PROTECTION IS ON"
            else -> "CONSENT NEEDED"
        }
        page.addView(LyraUi.badge(this, status,
            LyraUi.orange,
            Color.rgb(255, 240, 228),
        ))
        page.addView(LyraUi.text(this, "Your space to think.", 32f, bold = true))
        page.addView(LyraUi.text(this, "A little more intention.", 24f, LyraUi.orange, true))
        page.addView(LyraUi.text(this, "Thoughtful AI. Fewer distractions. More room for you.", 15f, LyraUi.muted).apply {
            setPadding(0, LyraUi.dp(this@MainActivity, 6), 0, LyraUi.dp(this@MainActivity, 6))
        })
        page.addView(LyraUi.card(this).apply {
            addView(LyraUi.feature(this@MainActivity, "AI", "AI, through Lyra", "${BlockPolicy.apps.size} app rules + supported AI websites"))
            addView(LyraUi.divider(this@MainActivity))
            addView(LyraUi.feature(this@MainActivity, "↗", "WhatsApp stays yours", "Only detected Meta AI chats take a detour."))
            addView(LyraUi.divider(this@MainActivity))
            addView(LyraUi.feature(this@MainActivity, "Ⅱ", "A pause for Shorts", "A gentle reminder. You choose what’s next."))
        })
        page.addView(LyraUi.button(this, "Open Lyra  ↗") {
            if (!LyraLauncher.open(this, store)) toast("Install Chrome or another browser to open Lyra.")
        })
        page.addView(LyraUi.button(this, if (store.hasPin) "Manage protection" else "Set up protection", false) {
            if (!store.hasPin) consentAndSetup() else askPin { showSettings() }
        })
        if (store.paused) page.addView(LyraUi.link(this, "Resume protection now") { store.resume(); showHome() })
        page.addView(LyraUi.link(this, "Set up or find the Lyra PWA") { showPwaGuide() })
        page.addView(LyraUi.text(this, "On your device. Always private.", 12f, LyraUi.muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, LyraUi.dp(this@MainActivity, 24), 0, 0)
        })
        page.addView(LyraUi.link(this, "About & open-source licenses") { showLicenses() })
    }

    private fun showLicenses() {
        val license = listOf("NOTICE", "COPYING", "Apache-2.0.txt").joinToString("\n\n") { file ->
            assets.open(file).bufferedReader().use { it.readText() }
        }
        val prompt = AlertDialog.Builder(this).setTitle("${getString(R.string.app_name)} · 0.3.0")
            .setMessage("Based on Curbox · GNU GPL v3\nhttps://github.com/curbox-app/curbox-android\n\n$license")
            .setPositiveButton("Close", null).show()
        LyraUi.styleDialog(prompt)
    }

    private fun showPwaGuide(notFound: Boolean = false, destination: String = store.destination) {
        val intro = if (notFound) "No matching installed Lyra PWA was found.\n\n" else ""
        val message = intro + "1. Open $destination in Chrome. Use the same Lyra address saved in this app.\n\n" +
            "2. Open Chrome’s menu. Choose Install app, Add to Home screen, or Install and create shortcut → Install (the wording varies by Chrome version).\n\n" +
            "3. Find the Lyra icon in your home screen or app drawer and open it. An installed PWA normally opens without the Chrome address bar. A plain website shortcut may still open a browser tab.\n\n" +
            "4. Return here → Manage protection → enter your PIN → Advanced app rules → Find installed Lyra PWA. Pick Lyra, then tap Save settings.\n\n" +
            "5. Tap Open Lyra to test. You can leave the PWA package field blank for automatic detection. If detection finds nothing, Chrome still opens the saved Lyra address."
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
                if (unlocked) {
                    field.setText(installed.getOrNull(index)?.packageName.orEmpty())
                    toast("PWA selected. Tap Save settings to apply it.")
                }
            }.setNegativeButton("Cancel", null).show().also(LyraUi::styleDialog)
    }

    private fun consentAndSetup() {
        dialog = AlertDialog.Builder(this).setTitle("Choose to protect your learning")
            .setMessage(getString(R.string.accessibility_description) + "\n\nSet a 6–12 digit administrator PIN. It controls pauses, destinations, rules and removal protection. Keep it somewhere safe.")
            .setNegativeButton("Cancel", null).setPositiveButton("I agree") { _, _ -> createPin() }.show()
    }
    private fun pinField(hintText: String) = LyraUi.field(EditText(this)).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(android.text.InputFilter.LengthFilter(12))
        setPadding(LyraUi.dp(this@MainActivity, 12), LyraUi.dp(this@MainActivity, 12), LyraUi.dp(this@MainActivity, 12), LyraUi.dp(this@MainActivity, 12))
    }
    private fun createPin() {
        val first = pinField("Administrator PIN")
        val second = pinField("Confirm PIN")
        val content = LyraUi.column(this).apply { addView(first); addView(second) }
        val prompt = AlertDialog.Builder(this).setTitle("Set administrator PIN").setView(content)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog = prompt
        prompt.setOnShowListener {
            LyraUi.styleDialog(prompt)
            prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = first.text.toString()
                if (!pin.matches(Regex("[0-9]{6,12}")) || pin != second.text.toString()) {
                    first.error = "Use 6–12 digits and make both PINs match."
                } else runPinWork(prompt, { store.setPin(pin); store.consent(); true }) {
                    unlocked = true
                    showSettings()
                }
            }
        }
        prompt.show()
    }
    private fun askPin(after: () -> Unit) {
        if (store.retrySeconds > 0) { toast("Try again in ${store.retrySeconds} seconds."); return }
        val field = pinField("Administrator PIN")
        val prompt = AlertDialog.Builder(this).setTitle("Manage your learning space")
            .setView(LyraUi.column(this).apply { addView(field) })
            .setNegativeButton("Cancel", null).setPositiveButton("Unlock", null).create()
        dialog = prompt
        prompt.setOnShowListener {
            LyraUi.styleDialog(prompt)
            prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = field.text.toString()
                runPinWork(prompt, { store.verifyPin(pin) }) { unlocked = true; after() }
            }
        }
        prompt.show()
    }
    private fun runPinWork(prompt: AlertDialog, work: () -> Boolean, after: () -> Unit) {
        val current = generation
        prompt.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        worker.execute {
            val valid = runCatching(work).getOrDefault(false)
            runOnUiThread {
                if (isDestroyed || generation != current) return@runOnUiThread
                prompt.dismiss()
                if (valid) after() else toast("PIN incorrect. Five attempts trigger a 30-second wait.")
            }
        }
    }

    private fun input(page: LinearLayout, label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        page.addView(LyraUi.text(this, label, 14f, LyraUi.muted, true).apply { setPadding(0, LyraUi.dp(this@MainActivity, 16), 0, 0) })
        return LyraUi.field(EditText(this)).apply { setText(value); inputType = type; page.addView(this) }
    }
    private fun showSettings() {
        if (!unlocked) { showHome(); return }
        val page = page()
        page.addView(LyraUi.text(this, "Make it yours.", 28f, bold = true))
        page.addView(LyraUi.text(this, "Changes and overrides require your administrator PIN. Leaving this app locks these controls.", 14f, LyraUi.muted))
        val destination = LyraUi.card(this).also { page.addView(it) }
        destination.addView(LyraUi.text(this, "YOUR LEARNING SPACE", 11f, LyraUi.orange, true))
        val url = input(destination, "Lyra address", store.destination, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val seconds = input(destination, "Open Lyra after (3–15 seconds)", store.countdown.toString(), InputType.TYPE_CLASS_NUMBER)
        val advanced = LyraUi.card(this).apply { visibility = View.GONE }
        page.addView(LyraUi.link(this, "Advanced app rules  +") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }.apply { gravity = Gravity.START })
        page.addView(advanced)
        val webapk = input(advanced, "PWA package (optional; blank = automatic)", store.pwaPackage)
        advanced.addView(LyraUi.button(this, "Find installed Lyra PWA", false) {
            findInstalledPwa(url.text.toString().trim(), webapk)
        })
        advanced.addView(LyraUi.link(this, "How to install and find the PWA") { showPwaGuide() })
        val extras = input(advanced, "Extra AI package IDs (one per line)", store.extraPackages.joinToString("\n"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        advanced.addView(LyraUi.text(this, "Included apps: ${BlockPolicy.apps.values.distinct().joinToString(", ")}", 12f, LyraUi.muted))
        page.addView(LyraUi.button(this, "Save settings") {
            val destination = url.text.toString().trim()
            val pwa = webapk.text.toString().trim()
            val countdown = seconds.text.toString().toIntOrNull()
            val packages = extras.text.toString().lines().map(String::trim).filter(String::isNotEmpty).toSet()
            when {
                !BlockPolicy.validDestination(destination) -> url.error = "Enter an HTTPS address without credentials or a fragment."
                pwa.isNotEmpty() && !BlockPolicy.validPackage(pwa) -> webapk.error = "Enter an Android package ID."
                countdown == null || countdown !in 3..15 -> seconds.error = "Choose 3–15 seconds."
                !packages.all(BlockPolicy::validPackage) || packageName in packages || "com.android.chrome" in packages || pwa in packages -> extras.error = "Use package IDs; keep Lyra, its PWA and Chrome unblocked."
                else -> {
                    store.destination = destination; store.pwaPackage = pwa
                    store.countdown = countdown; store.extraPackages = packages
                    toast("Settings saved.")
                }
            }
        })
        page.addView(LyraUi.divider(this))
        page.addView(LyraUi.text(this, "Protection & access", 19f, bold = true))
        page.addView(LyraUi.button(this, "Enable accessibility access", false) {
            dialog = AlertDialog.Builder(this).setTitle("Allow ${getString(R.string.app_name)} to protect your focus")
                .setMessage(getString(R.string.accessibility_description))
                .setNegativeButton("Cancel", null).setPositiveButton("Open Android Settings") { _, _ ->
                    store.consent()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }.show()
        })
        page.addView(LyraUi.button(this, "Override: pause for 5 minutes", false) { store.pause(); unlocked = false; showHome() })
        page.addView(LyraUi.button(this, "Add removal protection", false) {
            dialog = AlertDialog.Builder(this).setTitle("Make accidental removal harder")
                .setMessage("Optional administrator access adds a deactivation step before uninstalling ${getString(R.string.app_name)}. Android Settings can still revoke it. No lock, wipe, or password policies are requested.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ ->
                    startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ManagedProtection.component(this@MainActivity))
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Add a confirmation step before removing ${getString(R.string.app_name)}.")
                    })
                }.show()
        })
        if (ManagedProtection.isOwner(this)) {
            page.addView(LyraUi.button(this, "Apply managed-device protection", false) {
                confirm("Apply managed protection?", "Prevent uninstalling, force-stopping and clearing this app’s data using Android device-owner policy. Your PIN can release these restrictions here.") {
                    runCatching { ManagedProtection.apply(this) }.onSuccess { toast("Managed protection applied.") }.onFailure { toast(it.message ?: "Policy could not be applied.") }
                }
            })
        }
        page.addView(LyraUi.button(this, "Release removal protection", false) {
            confirm("Release removal protection?", "Remove ${getString(R.string.app_name)}’s administrator policies and device ownership, if present. App info will be available for 60 seconds. Use this app’s PIN-protected App info button if you need more time.") {
                runCatching { ManagedProtection.release(this) }.onSuccess {
                    store.allowAppInfoAccess()
                    toast("Removal protection released. App info is available for 60 seconds.")
                    showSettings()
                }.onFailure { toast(it.message ?: "Could not release policy.") }
            }
        })
        page.addView(LyraUi.button(this, "Change administrator PIN", false) { createPin() })
        page.addView(LyraUi.button(this, "Open App info · allow 60 seconds", false) {
            store.allowAppInfoAccess()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        page.addView(LyraUi.text(this, "Your App info page redirects to Lyra while protection is on. Use the PIN-protected button above for 60 seconds of maintenance access, or pause protection for five minutes.", 12f, LyraUi.muted))
        page.addView(LyraUi.text(this, "Shorts reminders are dismissible and never open Lyra. App updates may change detection; check protection after updating WhatsApp or YouTube.", 12f, LyraUi.muted).apply {
            setPadding(0, LyraUi.dp(this@MainActivity, 16), 0, 0)
        })
        page.addView(LyraUi.button(this, "Done", false) { unlocked = false; showHome() })
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        if (!unlocked) return
        dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> if (unlocked) action() }.show()
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
