// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/** Pure URL policy shared by WebAPK discovery, source hints and the repo guard. */
public final class HandoffPolicy {
    private HandoffPolicy() {}
    private static final Map<String,String> SOURCES = Map.ofEntries(
        Map.entry("chatgpt", "chatgpt"), Map.entry("chatgpt.com", "chatgpt"), Map.entry("chat.openai.com", "chatgpt"),
        Map.entry("gemini", "gemini"), Map.entry("gemini.google.com", "gemini"),
        Map.entry("claude", "claude"), Map.entry("claude.ai", "claude"),
        Map.entry("copilot", "copilot"), Map.entry("copilot.microsoft.com", "copilot"),
        Map.entry("perplexity", "perplexity"), Map.entry("perplexity.ai", "perplexity"),
        Map.entry("deepseek", "deepseek"), Map.entry("chat.deepseek.com", "deepseek"),
        Map.entry("grok", "grok"), Map.entry("grok.com", "grok"),
        Map.entry("mistral", "mistral"), Map.entry("chat.mistral.ai", "mistral"),
        Map.entry("poe", "poe"), Map.entry("poe.com", "poe"),
        Map.entry("meta ai", "meta"), Map.entry("meta.ai", "meta"),
        Map.entry("pi", "pi"), Map.entry("pi.ai", "pi"),
        Map.entry("replika", "replika"), Map.entry("replika.com", "replika"),
        Map.entry("qwen studio", "qwen"), Map.entry("qwen.ai", "qwen")
    );
    public static String source(String labelOrHost) {
        String value = labelOrHost.toLowerCase(Locale.ROOT).trim();
        String exact = SOURCES.get(value);
        if (exact != null) return exact;
        for (var item : SOURCES.entrySet())
            if (item.getKey().contains(".") && value.endsWith("." + item.getKey())) return item.getValue();
        return "other";
    }
    public static boolean inScope(String destination, String startUrl, String scope) {
        try {
            URI target = https(destination), start = https(startUrl);
            URI boundary = scope == null || scope.isBlank() ? start.resolve(".") : https(scope);
            if (!sameOrigin(target, start) || !sameOrigin(start, boundary)) return false;
            String path = target.normalize().getPath();
            if (path.isEmpty()) path = "/";
            String prefix = boundary.normalize().getPath();
            if (prefix.isEmpty()) prefix = "/";
            return path.equals(prefix) || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/");
        } catch (Exception ignored) { return false; }
    }
    private static URI https(String value) throws Exception {
        URI uri = new URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null ||
            (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException();
        // Encoded separators/dot segments have browser-specific interpretation; use the browser fallback.
        String raw = uri.getRawPath().toLowerCase(Locale.ROOT);
        if (raw.contains("%2f") || raw.contains("%5c") || raw.contains("%2e")) throw new IllegalArgumentException();
        return uri;
    }
    private static boolean sameOrigin(URI a, URI b) { return a.getHost().equalsIgnoreCase(b.getHost()); }
    public static boolean repositoryAddress(String address) {
        try {
            URI uri = new URI(address.contains("://") ? address.trim() : "https://" + address.trim());
            if (uri.getHost() == null || uri.getUserInfo() != null) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT), path = uri.normalize().getPath().toLowerCase(Locale.ROOT);
            if (!(host.equals("github.com") || host.equals("www.github.com") || host.equals("raw.githubusercontent.com"))) return false;
            String repo = "/akshay-abraham/m57";
            return path.equals(repo) || path.equals(repo + ".git") || path.startsWith(repo + "/");
        } catch (Exception ignored) { return false; }
    }
}
