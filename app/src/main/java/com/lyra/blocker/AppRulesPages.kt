// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import com.lyra.blocker.family.HostRules

class AppRulesPages(private val activity: Activity, private val authorised: () -> Boolean) {
    private val store = ProtectionStore(activity)
    private fun toggle(page: LinearLayout, title: String, enabled: Boolean, action: (Boolean) -> Unit) {
        page.addView(Switch(activity).apply {
            text = title; textSize = 16f; minimumHeight = LyraUi.dp(activity, 52)
            setTextColor(LyraUi.ink); isChecked = enabled
            setOnCheckedChangeListener { _, value -> if (authorised()) action(value) }
        })
    }
    fun apps(page: LinearLayout) {
        page.addView(LyraUi.text(activity, "Choose the detours.", 31f, bold = true))
        page.addView(LyraUi.text(activity, "Edit built-in app rules individually. Changes apply immediately; custom packages and website rules are below.", 15f, LyraUi.muted))
        val search = LyraUi.field(EditText(activity)).apply { hint = "Find an app"; setSingleLine(true) }
        page.addView(search)
        val rows = LyraUi.column(activity, 0); page.addView(rows)
        fun render(query: String) {
            rows.removeAllViews()
            for ((pkg, label) in BlockPolicy.apps) if ("$label $pkg".contains(query, true)) {
                toggle(rows, label, pkg !in store.disabledApps) { enabled ->
                    store.disabledApps = if (enabled) store.disabledApps - pkg else store.disabledApps + pkg
                }
                rows.addView(LyraUi.text(activity, pkg, 11f, LyraUi.muted))
            }
        }
        render("")
        search.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { render(s?.toString().orEmpty()) }
        })
        page.addView(LyraUi.text(activity, "Additional app packages", 19f, bold = true))
        val extras = LyraUi.field(EditText(activity)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines=3;setText(store.extraPackages.sorted().joinToString("\n")); page.addView(this)
        }
        page.addView(LyraUi.button(activity, "Save custom apps") {
            if (!authorised()) return@button
            val values=extras.text.toString().lines().map(String::trim).filter(String::isNotEmpty).toSet()
            if (values.size>500 || !values.all(BlockPolicy::validPackage) || activity.packageName in values || store.pwaPackage in values || values.any { it in com.lyra.blocker.family.BrowserDomains.BROWSERS }) {
                extras.error="Use valid package IDs; keep M57, browsers and the selected Lyra app available."
            } else { store.extraPackages=values; android.widget.Toast.makeText(activity,"Custom apps saved",android.widget.Toast.LENGTH_SHORT).show() }
        })
        page.addView(LyraUi.text(activity, "AI website rules", 21f, bold = true))
        for (domain in BlockPolicy.domains) toggle(page, domain, domain !in store.disabledDomains) { enabled ->
            store.disabledDomains=if(enabled)store.disabledDomains-domain else store.disabledDomains+domain
        }
        val domains=LyraUi.field(EditText(activity)).apply {
            hint="Additional AI domains, one per line";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines=3;setText(store.extraAiDomains.sorted().joinToString("\n"));page.addView(this)
        }
        page.addView(LyraUi.button(activity,"Save custom websites",false) {
            if(!authorised())return@button
            val values=domains.text.toString().lines().map(String::trim).filter(String::isNotEmpty)
            val valid=values.map(HostRules::domain)
            val own=HostRules.address(store.destination)
            if(values.size>500||valid.any{it==null}||valid.any{it==own})domains.error="Use domains only, keeping your Lyra destination available."
            else {store.extraAiDomains=valid.filterNotNull().toSet();android.widget.Toast.makeText(activity,"Website rules saved",android.widget.Toast.LENGTH_SHORT).show()}
        })
        page.addView(LyraUi.button(activity,"Restore built-in app and website defaults",false) {
            if(authorised()){store.disabledApps=emptySet();store.disabledDomains=emptySet();render(search.text.toString())}
        })
    }
    fun meta(page: LinearLayout) {
        page.addView(LyraUi.badge(activity,"EXPERIMENTAL"))
        page.addView(LyraUi.text(activity,"Keep your chats yours.",31f,bold=true))
        page.addView(LyraUi.text(activity,"Redirect detected Meta AI chat surfaces while keeping ordinary messaging and feeds available. App layouts and renamed contacts can affect recognition.",15f,LyraUi.muted))
        toggle(page,"Meta AI protection",store.metaEnabled){store.metaEnabled=it}
        val detail=LyraUi.column(activity,0).apply{visibility=android.view.View.GONE}
        page.addView(LyraUi.link(activity,"Choose individual Meta services  +") {
            detail.visibility=if(detail.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE
        });page.addView(detail)
        for((key,label) in linkedMapOf("whatsapp" to "WhatsApp / Business","instagram" to "Instagram","facebook" to "Facebook","messenger" to "Messenger","meta" to "Dedicated Meta AI app"))
            toggle(detail,label,key in store.metaServices){enabled->store.metaServices=if(enabled)store.metaServices+key else store.metaServices-key}
        page.addView(LyraUi.text(activity,"The dedicated Meta AI package is also used for Meta glasses; enabling its app rule redirects that entire package. Inline AI prompts and translated/unrecognised headers may not be detected.",13f,LyraUi.muted))
        page.addView(LyraUi.button(activity,"Report a blocking problem",false) {
            runCatching {activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(store.destination).buildUpon().path("/contact").clearQuery().appendQueryParameter("topic","missing-feature").appendQueryParameter("source","m57").build()))}
        })
    }
}
