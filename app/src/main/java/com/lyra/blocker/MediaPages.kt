// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch

class MediaPages(private val activity:Activity,private val authorised:()->Boolean) {
    private val store=ProtectionStore(activity)
    private fun toggle(page:LinearLayout,title:String,enabled:Boolean,change:(Boolean)->Unit){
        page.addView(Switch(activity).apply{text=title;textSize=16f;minimumHeight=LyraUi.dp(activity,56);setTextColor(LyraUi.ink);isChecked=enabled
            setOnCheckedChangeListener{_,value->if(authorised())change(value)}})
    }
    fun show(page:LinearLayout){
        page.addView(LyraUi.text(activity,"Make the next scroll a choice.",30f,bold=true))
        page.addView(LyraUi.text(activity,"Protect full-screen short-video spaces while keeping ordinary YouTube videos and the Instagram home feed available.",15f,LyraUi.muted))
        toggle(page,"YouTube Shorts",store.youtubeEnabled){store.youtubeEnabled=it}
        toggle(page,"Instagram full-screen Reels",store.instagramEnabled){store.instagramEnabled=it}
        page.addView(LyraUi.text(activity,"When a protected video opens",19f,bold=true))
        val modes=RadioGroup(activity)
        val block=RadioButton(activity).apply{id=View.generateViewId();text="Block scrolling · leave the viewer";setTextColor(LyraUi.ink)}
        val remind=RadioButton(activity).apply{id=View.generateViewId();text="Gentle reminder · allow dismiss";setTextColor(LyraUi.ink)}
        modes.addView(block);modes.addView(remind);modes.check(if(store.videoMode=="block")block.id else remind.id)
        modes.setOnCheckedChangeListener{_,id->if(authorised())store.videoMode=if(id==remind.id)"remind" else "block"};page.addView(modes)
        page.addView(LyraUi.badge(activity,"EXPERIMENTAL EXTRA PLATFORMS"))
        toggle(page,"Other short-video platforms",store.extraVideosEnabled){store.extraVideosEnabled=it}
        val extra=LyraUi.column(activity,0).apply{visibility=View.GONE}
        page.addView(LyraUi.link(activity,"Choose extra platforms  +"){extra.visibility=if(extra.visibility==View.VISIBLE)View.GONE else View.VISIBLE});page.addView(extra)
        for((key,label)in linkedMapOf("snapchat" to "Snapchat Spotlight only","facebook" to "Facebook Reels","tiktok" to "TikTok full-screen videos","josh" to "Josh / Josh Lite · entire app"))
            toggle(extra,label,key in store.extraVideoApps){enabled->store.extraVideoApps=if(enabled)store.extraVideoApps+key else store.extraVideoApps-key}
        page.addView(LyraUi.text(activity,"Extra-platform recognition depends on app versions and is not fully supported. Josh protection covers its entire package, including chat/calls; leave it off if those features must stay available. Instagram feed previews and normal messaging are not intentionally blocked by the full-screen viewer rules.",13f,LyraUi.muted))
        page.addView(LyraUi.button(activity,"Report a detection problem",false){
            runCatching{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(store.destination).buildUpon().path("/contact").clearQuery().appendQueryParameter("topic","missing-feature").appendQueryParameter("source","m57").build()))}
        })
    }
}
