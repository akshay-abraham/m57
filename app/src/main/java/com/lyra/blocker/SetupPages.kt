// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout

/** Resumable permission guidance. Returning from system settings requires re-unlocking. */
class SetupPages(private val activity:Activity,private val authorised:()->Boolean,private val navigate:(AdminPage)->Unit){
    private val store=ProtectionStore(activity)
    private fun accessible()=activity.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any{it.resolveInfo.serviceInfo.packageName==activity.packageName}
    private fun open(intent:Intent){if(authorised())runCatching{store.allowAppInfoAccess();activity.startActivity(intent)}}
    private fun next(){if(authorised()){store.setupStep=(store.setupStep+1).coerceAtMost(3);navigate(AdminPage.SETUP)}}
    fun show(page:LinearLayout){
        val step=store.setupStep
        page.addView(LyraUi.badge(activity,"SETUP · ${step+1} OF 4"))
        page.addView(LyraUi.text(activity,listOf("Give learning a little space.","Let M57 stay ready.","Make removal intentional.","Your space is nearly ready.")[step],31f,bold=true))
        page.addView(LyraUi.text(activity,"Administrator password is set. Keep it private and do not share it with the child.",14f,LyraUi.muted))
        when(step){
            0->{
                page.addView(LyraUi.text(activity,"Accessibility lets M57 recognise supported AI, video and settings screens and display its interventions. Messages and video content are not saved. Screen prompts use this access; a separate draw-over-apps permission is not needed.",15f,LyraUi.muted))
                page.addView(LyraUi.badge(activity,if(accessible())"ACCESSIBILITY ENABLED" else "ACCESS NEEDED"))
                page.addView(LyraUi.button(activity,"Open M57 accessibility settings"){
                    store.consent();open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
                page.addView(LyraUi.text(activity,"If Android says restricted settings are blocked, open App info, use its menu to allow restricted settings, then return to Accessibility. Leaving this app locks admin controls; unlock again to continue here.",14f,LyraUi.muted))
                page.addView(LyraUi.button(activity,"Open App info for restricted settings",false){open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}")))})
                page.addView(LyraUi.button(activity,"Find help for this phone",false){
                    val query="How to allow restricted settings accessibility ${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE}"
                    open(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/results").buildUpon().appendQueryParameter("search_query",query).build()))
                })
                page.addView(LyraUi.button(activity,"Continue"){
                    if(accessible())next() else android.widget.Toast.makeText(activity,"Enable M57 accessibility first",android.widget.Toast.LENGTH_SHORT).show()
                })
            }
            1->{
                val guide=BackgroundHelp.forDevice(Build.MANUFACTURER,Build.BRAND)
                page.addView(LyraUi.text(activity,"Some phones stop apps in the background. Review Android's battery access and the manufacturer's startup controls. These improve reliability but do not make the service impossible to stop.",15f,LyraUi.muted))
                if(guide!=null){
                    page.addView(LyraUi.badge(activity,if(BackgroundAccess.isExempt(activity))"BATTERY EXEMPTION ALLOWED" else "BATTERY ACCESS TO REVIEW"))
                    page.addView(LyraUi.button(activity,"Allow background running"){if(authorised()){store.allowAppInfoAccess();BackgroundAccess.requestExemption(activity)}})
                    page.addView(LyraUi.button(activity,"${guide.manufacturer} startup settings",false){if(authorised()){store.allowAppInfoAccess();BackgroundAccess.openManufacturerSettings(activity,guide.vendor)}})
                    val extra=LyraUi.column(activity,0).apply{visibility=android.view.View.GONE}
                    page.addView(LyraUi.link(activity,"Troubleshooting guide  +"){extra.visibility=if(extra.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE})
                    extra.addView(LyraUi.text(activity,guide.url,13f,LyraUi.muted))
                    extra.addView(LyraUi.button(activity,"Open manufacturer guide",false){open(Intent(Intent.ACTION_VIEW,Uri.parse(guide.url)))})
                    page.addView(extra)
                }else page.addView(LyraUi.text(activity,"No manufacturer-specific helper is configured for this phone. You can review battery settings in Android.",14f,LyraUi.muted))
                page.addView(LyraUi.button(activity,"Continue with current battery settings"){next()})
            }
            2->{
                val manager=activity.getSystemService(DevicePolicyManager::class.java)
                val active=manager.isAdminActive(ManagedProtection.component(activity))
                page.addView(LyraUi.text(activity,"Optional administrator access adds a deactivation step before uninstalling M57. On a normal personal phone, Android can still revoke permissions or remove the app; this is not guaranteed uninstall protection. Managed school devices have stronger controls through intentional device-owner enrolment.",15f,LyraUi.muted))
                page.addView(LyraUi.badge(activity,if(active)"ADMINISTRATOR ACCESS ENABLED" else "OPTIONAL STEP"))
                page.addView(LyraUi.button(activity,"Add removal protection"){
                    open(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply{
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,ManagedProtection.component(activity))
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Add a confirmation step before removing M57. No wipe or lock policies are requested.")
                    })
                })
                page.addView(LyraUi.button(activity,if(active)"Continue" else "Continue without removal protection",false){next()})
            }
            else->{
                page.addView(LyraUi.text(activity,"Choose which AI apps, video spaces and family website categories to protect. Family VPN and browser controls are separate and stay off until configured. Every setting remains available in your administrator space.",15f,LyraUi.muted))
                page.addView(LyraUi.text(activity,"M57 is open source so anyone can inspect, vet and report problems in the code. Source access supports review; it does not replace device testing or promise perfect protection.",15f,LyraUi.muted))
                page.addView(LyraUi.link(activity,"Inspect this build’s source") {
                    if(authorised()){store.allowAppInfoAccess();activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(BuildConfig.SOURCE_URL)))}
                })
                page.addView(LyraUi.button(activity,"Finish setup"){
                    if(!authorised())return@button
                    if(!accessible()){store.setupStep=0;navigate(AdminPage.SETUP)}else{store.setupComplete=true;navigate(AdminPage.OVERVIEW)}
                })
            }
        }
        if(step>0)page.addView(LyraUi.link(activity,"Previous step"){if(authorised()){store.setupStep=step-1;navigate(AdminPage.SETUP)}})
    }
}
