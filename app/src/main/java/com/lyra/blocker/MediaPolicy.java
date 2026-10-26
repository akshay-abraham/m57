// SPDX-License-Identifier: (GPL-3.0-only AND AGPL-3.0-only)
// Viewer IDs adapted from Curbox ReelAppConfig; matching/settings glue is original M57 work.
package com.lyra.blocker;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public final class MediaPolicy {
    private MediaPolicy() {}
    public static final Set<String> YOUTUBE=Set.of("com.google.android.youtube","app.revanced.android.youtube","app.morphe.android.youtube");
    public static final Set<String> INSTAGRAM=Set.of("com.instagram.android","com.myinsta.android");
    public static final Set<String> TIKTOK=Set.of("com.zhiliaoapp.musically","com.ss.android.ugc.trill");
    public static final Set<String> JOSH=Set.of("com.eterno.shortvideos","com.eterno.shortvideos.lite");
    public static final Map<String,String> LABELS=Map.of("youtube","YouTube Shorts","instagram","Instagram Reels","snapchat","Snapchat Spotlight","facebook","Facebook Reels","tiktok","TikTok videos","josh","Josh");
    public static Set<String> inspectedNames(String pkg) {
        if(YOUTUBE.contains(pkg))return Set.of("reel_recycler","reel_player_page_content");
        if(INSTAGRAM.contains(pkg))return Set.of("clips_viewer_view_pager","clips_ufi_component");
        if(pkg.equals("com.snapchat.android"))return Set.of("spotlight_container");
        if(TIKTOK.contains(pkg))return Set.of("view_pager_layout_wrapper","long_press_layout");
        return Set.of();
    }
    public static String nativePlatform(String pkg,Set<String> visibleIds,boolean facebookViewer) {
        if(YOUTUBE.contains(pkg)&&(visibleIds.contains(pkg+":id/reel_recycler")||visibleIds.contains(pkg+":id/reel_player_page_content")))return "youtube";
        if(INSTAGRAM.contains(pkg)&&visibleIds.contains(pkg+":id/clips_viewer_view_pager")&&visibleIds.contains(pkg+":id/clips_ufi_component"))return "instagram";
        if(pkg.equals("com.snapchat.android")&&visibleIds.contains(pkg+":id/spotlight_container"))return "snapchat";
        if(pkg.equals("com.facebook.katana")&&facebookViewer)return "facebook";
        if(TIKTOK.contains(pkg)&&visibleIds.contains(pkg+":id/view_pager_layout_wrapper")&&visibleIds.contains(pkg+":id/long_press_layout"))return "tiktok";
        if(JOSH.contains(pkg))return "josh";
        return null;
    }
    public static String addressPlatform(String address) {
        try {
            URI uri=new URI(address.contains("://")?address:"https://"+address);
            String host=uri.getHost()==null?"":uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if(!Set.of("https","http").contains(uri.getScheme())||uri.getUserInfo()!=null)return null;
            String path=uri.getPath()==null?"":uri.getPath();
            if((host.equals("youtube.com")||host.endsWith(".youtube.com"))&&(path.equals("/shorts")||path.startsWith("/shorts/")))return "youtube";
            if((host.equals("instagram.com")||host.endsWith(".instagram.com"))&&(path.equals("/reels")||path.startsWith("/reels/")||path.startsWith("/reel/")))return "instagram";
            return null;
        } catch(Exception error){return null;}
    }
}
