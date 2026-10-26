// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import java.util.Set;

public final class MediaMetaChecks {
    public static void main(String[]args){run();System.out.println("Short-video and Meta boundary checks passed");}
    public static void run(){
        String yt="com.google.android.youtube",ig="com.instagram.android";
        check("youtube".equals(MediaPolicy.nativePlatform(yt,Set.of(yt+":id/reel_recycler"),false)),"YouTube viewer missed");
        check(MediaPolicy.nativePlatform(yt,Set.of(yt+":id/shorts_tab"),false)==null,"YouTube tab was blocked");
        check("instagram".equals(MediaPolicy.nativePlatform(ig,Set.of(ig+":id/clips_viewer_view_pager",ig+":id/clips_ufi_component"),false)),"Instagram viewer missed");
        check(MediaPolicy.nativePlatform(ig,Set.of(ig+":id/clips_ufi_component"),false)==null,"Instagram feed preview blocked");
        check(MediaPolicy.nativePlatform("com.snapchat.android",Set.of("com.snapchat.android:id/chat_list"),false)==null,"Snapchat chat blocked");
        check("snapchat".equals(MediaPolicy.nativePlatform("com.snapchat.android",Set.of("com.snapchat.android:id/spotlight_container"),false)),"Spotlight missed");
        check("youtube".equals(MediaPolicy.addressPlatform("https://www.youtube.com/shorts/123")),"Shorts URL missed");
        check(MediaPolicy.addressPlatform("https://youtube.com.example.org/shorts/123")==null,"Shorts hostname boundary failed");
        check(MediaPolicy.addressPlatform("https://instagram.com/")==null,"Instagram home blocked");
        check("instagram".equals(MediaPolicy.addressPlatform("https://instagram.com/reel/abc")),"Reel URL missed");
        check(MetaPolicy.titleMatches("com.whatsapp","com.whatsapp:id/conversation_contact_name","Meta AI"),"Meta title missed");
        check(!MetaPolicy.titleMatches("com.whatsapp","com.whatsapp:id/message_text","Meta AI"),"Message body matched Meta");
        check(!MetaPolicy.titleMatches("com.whatsapp","fake:id/conversation_contact_name","Meta AI"),"Untrusted header matched Meta");
        check(!MetaPolicy.titleMatches(ig,ig+":id/action_bar_title","My friends"),"Ordinary chat matched Meta");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
