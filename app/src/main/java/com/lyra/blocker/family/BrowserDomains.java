// SPDX-License-Identifier: (GPL-3.0-only AND AGPL-3.0-only)
// Browser resource/semantic IDs adapted from Curbox BrowserUrlBarIds; see NOTICE.
package com.lyra.blocker.family;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class BrowserDomains {
    private BrowserDomains() {}
    public static final Map<String, Set<String>> BROWSERS;
    public static final Set<String> INSPECTED_IDS = Set.of("url_bar", "location_bar_edit_text", "url_field", "mozac_browser_toolbar_url_view", "ADDRESSBAR_URL_BOX");
    static {
        Map<String, Set<String>> next = new LinkedHashMap<>();
        for (String pkg : new String[]{"com.android.chrome", "com.chrome.beta", "com.chrome.dev", "org.chromium.chrome", "com.brave.browser",
                "com.vivaldi.browser", "org.cromite.cromite", "app.vanadium.browser", "org.calyxos.chromium", "io.github.jqssun.helium"})
            next.put(pkg, Set.of("url_bar"));
        next.put("com.sec.android.app.sbrowser", Set.of("location_bar_edit_text", "url_bar"));
        next.put("com.opera.browser", Set.of("url_field"));
        next.put("org.mozilla.focus", Set.of("mozac_browser_toolbar_url_view"));
        for (String pkg : new String[]{"org.mozilla.firefox", "org.mozilla.fennec_fdroid", "org.ironfoxoss.ironfox", "io.github.forkmaintainers.iceraven"})
            next.put(pkg, Set.of("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view"));
        BROWSERS = java.util.Collections.unmodifiableMap(next);
    }
    public static String host(String packageName, String resourceId, String text) {
        Set<String> names = BROWSERS.get(packageName);
        if (names == null || resourceId == null) return null;
        boolean trusted = resourceId.startsWith(packageName + ":id/") ||
            (resourceId.equals("ADDRESSBAR_URL_BOX") && names.contains("ADDRESSBAR_URL_BOX")) ||
            (Set.of("com.chrome.beta", "com.chrome.dev").contains(packageName) && resourceId.startsWith("com.android.chrome:id/"));
        if (!trusted || !names.contains(resourceId.substring(resourceId.lastIndexOf('/') + 1))) return null;
        return HostRules.address(text);
    }
}
