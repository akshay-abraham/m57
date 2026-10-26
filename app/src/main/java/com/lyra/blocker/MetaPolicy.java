// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import java.util.Map;
import java.util.Set;

/** Experimental title-only detection. Never match message bodies, feed text or search prompts. */
public final class MetaPolicy {
    private MetaPolicy() {}
    public static final Map<String,String> SERVICES=Map.of(
        "com.whatsapp","whatsapp","com.whatsapp.w4b","whatsapp",
        "com.instagram.android","instagram","com.myinsta.android","instagram",
        "com.facebook.katana","facebook","com.facebook.orca","messenger",
        "com.facebook.stella","meta");
    public static final Set<String> HEADERS=Set.of("conversation_contact_name","conversation_title","toolbar_title","action_bar_title",
        "thread_title","thread_header_title","thread_name");
    public static boolean titleMatches(String pkg,String id,String text) {
        if(!SERVICES.containsKey(pkg)||"meta".equals(SERVICES.get(pkg))) return false;
        if(id==null||!id.startsWith(pkg+":id/")||!HEADERS.contains(id.substring(id.lastIndexOf('/')+1))) return false;
        return text!=null&&text.trim().equalsIgnoreCase("Meta AI");
    }
}
