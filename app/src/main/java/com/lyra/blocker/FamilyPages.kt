// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.lyra.blocker.family.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Family category screens; mutation callbacks always check the current administrator session. */
class FamilyPages(private val activity: Activity, private val authorised: () -> Boolean,
                  private val navigate: (AdminPage) -> Unit, private val requestVpn: () -> Unit) {
    private val store = FamilyStore(activity)
    private val repository = FamilyRepository.get(activity)
    var selectedCategory = FamilyCategory.ADULT

    private fun heading(page: LinearLayout, title: String, detail: String) {
        page.addView(LyraUi.text(activity, title, 31f, bold = true))
        page.addView(LyraUi.text(activity, detail, 15f, LyraUi.muted))
    }
    private fun link(page: LinearLayout, title: String, detail: String, action: () -> Unit) {
        page.addView(LyraUi.card(activity).apply {
            addView(LyraUi.text(activity, "$title  ›", 19f, bold = true))
            addView(LyraUi.text(activity, detail, 14f, LyraUi.muted))
            isClickable = true; isFocusable = true
            setOnClickListener { if (authorised()) action() }
        })
    }
    private fun stamp(time: Long) = if (time == 0L) "Not synced yet" else DateTimeFormatter.ofPattern("d MMM · HH:mm z").format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
    private fun switch(page: LinearLayout, label: String, checked: Boolean, changed: (Boolean) -> Unit) {
        page.addView(Switch(activity).apply {
            text = label; textSize = 17f; setTextColor(LyraUi.ink)
            minimumHeight = LyraUi.dp(activity, 56); isChecked = checked
            setOnCheckedChangeListener { _, value -> if (authorised()) changed(value) }
        })
    }
    private fun updateWhileVisible(view: TextView, content: () -> String) {
        view.text = content()
        view.postDelayed(object : Runnable {
            override fun run() {
                if (!view.isAttachedToWindow) return
                val next = content()
                if (view.text.toString() != next) view.text = next
                view.postDelayed(this, 1_000)
            }
        }, 1_000)
    }

    fun home(page: LinearLayout) {
        heading(page, "Care, with understanding.", "Choose the boundaries for this device. The same domain lists power local DNS filtering and browser protection.")
        val status = LyraUi.text(activity, "", 14f, LyraUi.muted)
        page.addView(LyraUi.card(activity).apply { addView(status) })
        var stopAction = FamilyVpnService.running
        val vpnButton = LyraUi.button(activity, if (stopAction) "Stop VPN filter" else "Start / retry local VPN filter") {
            if (!authorised()) return@button
            if (stopAction) { store.vpnRequested = false; FamilyControl.reconcile(activity); navigate(AdminPage.FAMILY) }
            else requestVpn()
        }
        page.addView(vpnButton)
        val cancelButton = LyraUi.button(activity, "Cancel requested VPN start", false) {
            if (authorised()) { store.vpnRequested = false; FamilyControl.reconcile(activity); navigate(AdminPage.FAMILY) }
        }
        page.addView(cancelButton)
        updateWhileVisible(status) {
            stopAction = FamilyVpnService.running
            vpnButton.text = if (stopAction) "Stop VPN filter" else "Start / retry local VPN filter"
            cancelButton.visibility = if (store.vpnRequested && !stopAction) View.VISIBLE else View.GONE
            val vpn = if (stopAction) store.vpnMessage() else "VPN off · ${store.vpnMessage()}"
            val sync = when {
                repository.busy -> "Sync in progress…"
                store.enabled.isEmpty() -> "Enable a category to schedule list updates."
                else -> "Daily sync scheduled; last working lists stay available offline."
            }
            "$vpn\n${if (repository.loading) "Loading saved lists…" else "${repository.index.size()} loaded domain rules"}${if (!repository.ready) " · list setup incomplete" else ""}\n$sync"
        }
        page.addView(LyraUi.text(activity, "Android will ask for VPN approval. This filter uses the phone's network DNS resolver, without an M57 VPN server. Another VPN cannot be active in the same profile. Browser Secure DNS, Private DNS and direct IP connections may bypass DNS rules; use the browser layer too. This is not a traffic-encryption VPN.", 13f, LyraUi.muted))
        switch(page, "Block listed domains in browsers", store.browserEnabled) { store.browserEnabled = it }
        page.addView(LyraUi.text(activity, "The browser layer needs M57 accessibility access and a readable address bar. Supported detection rules cover Chrome and related browsers, Samsung Internet, Firefox variants, Firefox Focus and Opera; versions and hidden URLs need device checks.", 13f, LyraUi.muted))
        for (category in FamilyCategory.values()) {
            link(page, category.title, "${if (category.key in store.enabled) "Enabled" else "Off"} · ${store.count(category)} saved domains · ${stamp(store.lastSync(category))}") {
                selectedCategory = category; navigate(AdminPage.FAMILY_CATEGORY)
            }
        }
        page.addView(LyraUi.button(activity, "Resync enabled lists", false) {
            if (authorised()) { FamilySyncJob.schedule(activity, true); Toast.makeText(activity, "Sync requested when a network is available", Toast.LENGTH_SHORT).show() }
        })
        link(page, "Allow & block exceptions", "Add domains one per line. Allow entries take priority and include subdomains.") { navigate(AdminPage.FAMILY_EXCEPTIONS) }
        link(page, "Sources, licenses & coverage", "StevenBlack and contributing sources, mirrors and browser/DNS limits.") { navigate(AdminPage.FAMILY_SOURCES) }
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            page.addView(LyraUi.button(activity, "Allow protection notifications", false) {
                if (authorised()) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5704)
            })
        }
    }

    fun category(page: LinearLayout) {
        val category = selectedCategory
        heading(page, category.title, category.explanation)
        switch(page, "Enable this category", category.key in store.enabled) { checked ->
            store.enabled = if (checked) store.enabled + category.key else store.enabled - category.key
            FamilyControl.changed(activity)
        }
        val detail = LyraUi.text(activity, "", 14f, LyraUi.muted)
        page.addView(detail)
        updateWhileVisible(detail) {
            val stale = store.lastSync(category) == 0L || System.currentTimeMillis() - store.lastSync(category) > 86_400_000
            "${store.count(category)} saved domains\nLast successful sync: ${stamp(store.lastSync(category))}${if (stale) " · refresh needed" else ""}\n${store.error(category)}\n${store.source(category)}"
        }
        page.addView(LyraUi.button(activity, "Resync enabled lists", false) { if (authorised()) FamilySyncJob.schedule(activity, true) })
        page.addView(LyraUi.text(activity, "Listed website examples", 19f, bold = true))
        val examples = LyraUi.text(activity, "Available after the first successful download.", 13f, LyraUi.muted)
        page.addView(examples)
        repository.sample(category) { domains ->
            activity.runOnUiThread { examples.post { if (examples.isAttachedToWindow && domains.isNotEmpty()) examples.text = domains.joinToString("\n") } }
        }
        page.addView(LyraUi.text(activity, "Examples show list coverage, not a complete list. Domain blocking includes subdomains. A domain list cannot block just Reels while leaving the rest of the same website available.", 13f, LyraUi.muted))
        link(page, "List sources & licenses", "View upstream contributors and the HTTPS download chain.") { navigate(AdminPage.FAMILY_SOURCES) }
    }

    fun exceptions(page: LinearLayout) {
        heading(page, "Make a thoughtful exception.", "Enter domains, not links or wildcards. A domain also covers its subdomains. Allow entries win over block entries and category lists.")
        fun field(label: String, domains: Set<String>): EditText {
            page.addView(LyraUi.text(activity, label, 18f, bold = true))
            return LyraUi.field(EditText(activity)).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3; maxLines = 8; gravity = Gravity.TOP
                filters = arrayOf(android.text.InputFilter.LengthFilter(50_000))
                setText(domains.sorted().joinToString("\n")); page.addView(this)
            }
        }
        val allow = field("Always allow", store.allow)
        val block = field("Also block", store.block)
        page.addView(LyraUi.button(activity, "Save exceptions") {
            if (!authorised()) return@button
            fun parse(input: EditText): Set<String>? {
                val lines = input.text.toString().lines().map(String::trim).filter(String::isNotEmpty)
                if (lines.size > 1000) { input.error = "Use at most 1,000 domains"; return null }
                val normalised = lines.map { HostRules.domain(it) }
                if (normalised.any { it == null }) { input.error = "Use valid domains, one per line, without URLs or wildcards"; return null }
                return normalised.filterNotNull().toSet()
            }
            val allowed = parse(allow) ?: return@button
            val blocked = parse(block) ?: return@button
            store.prefs.edit().putStringSet("allow_domains", allowed).putStringSet("block_domains", blocked).apply()
            FamilyControl.changed(activity)
            navigate(AdminPage.FAMILY)
        })
    }

    fun sources(page: LinearLayout) {
        heading(page, "Know the sources.", "StevenBlack combines community-maintained domain lists. Each contributing source keeps its own terms.")
        page.addView(LyraUi.button(activity, "Show source licenses & attribution", false) {
            val metadata = activity.assets.open("HOSTS-SOURCES.txt").bufferedReader().use { it.readText() }
            val license = activity.assets.open("StevenBlack-MIT.txt").bufferedReader().use { it.readText() }
            AlertDialog.Builder(activity).setTitle("Source terms & attribution").setMessage("$license\n\n$metadata")
                .setPositiveButton("Close", null).show().also(LyraUi::styleDialog)
        })
        page.addView(LyraUi.button(activity, "Open StevenBlack source repository", false) {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/StevenBlack/hosts"))) }
        })
        for (category in FamilyCategory.values()) {
            page.addView(LyraUi.text(activity, category.title, 18f, bold = true))
            page.addView(LyraUi.text(activity, category.urls.joinToString("\n"), 12f, LyraUi.muted))
        }
        page.addView(LyraUi.text(activity, "Sources are tried in order over HTTPS. A mirror is used only after its downloaded content passes validation. Failed, empty or unexpectedly shrunken updates keep the last working copy. The upstream mirror and CDN still need live download checks on your network.", 14f, LyraUi.muted))
        page.addView(LyraUi.text(activity, "Private DNS / Secure DNS, cached addresses and direct IP connections can bypass the DNS layer. This first version handles UDP DNS; DNS-over-TCP is not supported by the local interface. Browser interception works only where an address bar is exposed. List membership can be incomplete or mistaken; use exceptions when needed. No browsing queries or blocked visits are saved.", 14f, LyraUi.muted))
    }
}
