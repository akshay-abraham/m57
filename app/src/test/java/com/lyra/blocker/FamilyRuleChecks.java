// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import com.lyra.blocker.family.BrowserDomains;
import com.lyra.blocker.family.HostRules;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class FamilyRuleChecks {
    public static void main(String[] args) throws Exception {
        run();
        if (args.length > 0) {
            Path root = Path.of(args[0]);
            for (String list : new String[]{"hosts", "alternates/porn-only/hosts", "alternates/gambling-only/hosts", "alternates/social-only/hosts", "alternates/fakenews-only/hosts"}) {
                try (java.io.Reader reader = Files.newBufferedReader(root.resolve(list))) {
                    Set<String> domains = HostRules.parseHosts(reader);
                    check(domains.size() >= 100, "Real list unexpectedly empty");
                    String text = Files.readString(root.resolve(list));
                    check(HostRules.validatedDownload(text, 0).size() == domains.size(), "Real download validator disagreed with parser");
                    java.util.regex.Matcher declared = java.util.regex.Pattern.compile("(?m)^# Number of unique domains: ([0-9,]+)").matcher(text);
                    check(declared.find(), "Missing real-list count header");
                    int expected = Integer.parseInt(declared.group(1).replace(",", ""));
                    check(domains.size() >= expected * 98L / 100 && domains.size() <= expected + 10, "Real-list count failed download validation");
                    System.out.println("Parsed " + list + ": " + domains.size() + " valid domains");
                }
            }
        }
        System.out.println("Family hosts and browser policy checks passed");
    }
    public static void run() throws Exception {
        Set<String> parsed = HostRules.parseHosts(new StringReader("# comments\n127.0.0.1 localhost localhost.localdomain\n0.0.0.0 0.0.0.0\n0.0.0.0 BAD.example ads.example # end\n:: blocked.example\n8.8.8.8 should-not-block.example\n0.0.0.0 bad.example\n"));
        check(parsed.equals(Set.of("bad.example", "ads.example", "blocked.example")), "Parser included system/non-block records or duplicates");
        check("bad.example".equals(HostRules.domain(" BAD.Example. ")), "Domain normalisation failed");
        check("xn--bcher-kva.example".equals(HostRules.domain("bücher.example")), "IDN normalisation failed");
        check(HostRules.domain("192.168.1.1") == null && HostRules.domain("localhost") == null, "IP/system host accepted");
        check(HostRules.domain("a..example") == null && HostRules.domain("-bad.example") == null, "Invalid domain accepted");
        check("bad.example".equals(HostRules.address("https://bad.example:8443/path?q=x#fragment")), "Browser URL parse failed");
        check("bad.example".equals(HostRules.address("bad.example/path")), "Schemeless browser address failed");
        check(HostRules.address("https://bad.example@safe.example/") == null, "Credential confusion accepted");
        check(HostRules.address("Search for bad.example") == null, "Search text treated as a URL");
        check(HostRules.address("javascript:bad.example") == null, "Non-web URL accepted");
        Map<String,Set<String>> categories = new LinkedHashMap<>();
        categories.put("adult", Set.of("bad.example")); categories.put("base", Set.of("ads.example"));
        HostRules.Index index = new HostRules.Index(categories, Set.of("ok.bad.example"), Set.of("extra.example"));
        check("adult".equals(index.match("sub.bad.example")), "Blocked subdomain missed");
        check(index.match("notbad.example") == null && index.match("bad.example.safe.example") == null, "Domain boundary false positive");
        check(index.match("child.ok.bad.example") == null, "Allow override ignored");
        check("custom".equals(index.match("extra.example")), "Custom block ignored");
        HostRules.Index disabled = new HostRules.Index(Map.of(), Set.of(), Set.of());
        check(disabled.match("bad.example") == null, "Disabled category retained rules");
        boolean rejected = false;
        try { HostRules.parseHosts(new StringReader("<html>bad response</html>")); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "HTML response accepted");
        rejected = false;
        try { HostRules.parseHosts(new StringReader("# empty list")); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "Empty response accepted");
        StringBuilder download = new StringBuilder("# Number of unique domains: 120\n");
        for (int i=0;i<120;i++) download.append("0.0.0.0 blocked").append(i).append(".example\n");
        check(HostRules.validatedDownload(download.toString(),0).size()==120, "Complete download rejected");
        rejected=false;
        try { HostRules.validatedDownload(download.toString().replace(": 120",": 1000"),0); } catch(java.io.IOException expected){rejected=true;}
        check(rejected,"Truncated download count accepted");
        rejected=false;
        try { HostRules.validatedDownload(download.toString(),1000); } catch(java.io.IOException expected){rejected=true;}
        check(rejected,"Unexpected shrinking update accepted");
        check("bad.example".equals(BrowserDomains.host("com.android.chrome", "com.android.chrome:id/url_bar", "bad.example/path")), "Chrome address missed");
        check("bad.example".equals(BrowserDomains.host("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text", "https://bad.example")), "Samsung address missed");
        check(BrowserDomains.host("com.android.chrome", "com.android.chrome:id/body", "bad.example") == null, "Page content was used as an address");
        check(BrowserDomains.host("com.unknown", "com.unknown:id/url_bar", "bad.example") == null, "Unknown browser claimed supported");
        check(BrowserDomains.host("com.android.chrome", "com.fake:id/url_bar", "bad.example") == null, "Untrusted address resource accepted");
        check("bad.example".equals(BrowserDomains.host("org.mozilla.firefox", "ADDRESSBAR_URL_BOX", "bad.example")), "Firefox semantic address ID missed");
        check(BrowserDomains.host("com.android.chrome", "ADDRESSBAR_URL_BOX", "bad.example") == null, "Semantic ID accepted for the wrong browser");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
