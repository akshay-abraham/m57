// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker.family;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.IDN;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict hosts parsing and domain boundaries, shared by VPN and browser protection. */
public final class HostRules {
    public static final int MAX_DOMAINS = 500_000;
    private HostRules() {}

    public static String domain(String value) {
        if (value == null) return null;
        String input = value.trim();
        if (input.endsWith(".")) input = input.substring(0, input.length() - 1);
        if (input.isEmpty() || input.length() > 253 || input.indexOf(':') >= 0 || input.indexOf('/') >= 0 || input.indexOf('@') >= 0) return null;
        try {
            String host = IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (!host.contains(".") || host.length() > 253 || host.matches("[0-9.]+")) return null;
            if (Set.of("localhost.localdomain", "ip6.localhost", "ip6.loopback").contains(host) || host.endsWith(".local")) return null;
            for (String label : host.split("\\.", -1)) {
                if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return null;
            }
            return host;
        } catch (IllegalArgumentException error) { return null; }
    }

    public static String address(String text) {
        if (text == null || text.length() > 8192) return null;
        String input = text.trim();
        if (input.isEmpty() || input.chars().anyMatch(Character::isWhitespace)) return null;
        if (!input.contains("://")) input = "https://" + input;
        try {
            URI uri = new URI(input);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return null;
            if (uri.getRawUserInfo() != null) return null;
            String authority = uri.getRawAuthority();
            if (authority == null || authority.indexOf('@') >= 0 || authority.startsWith("[")) return null;
            String host = authority;
            int colon = host.lastIndexOf(':');
            if (colon >= 0) {
                String port = host.substring(colon + 1);
                if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) > 65535) return null;
                host = host.substring(0, colon);
            }
            return domain(host);
        } catch (Exception error) { return null; }
    }

    public static Set<String> parseHosts(Reader reader) throws IOException {
        Set<String> result = new HashSet<>();
        BufferedReader input = new BufferedReader(reader);
        String line;
        int lines = 0;
        while ((line = input.readLine()) != null) {
            if (++lines > 1_000_000 || line.length() > 8192) throw new IOException("List exceeds limits");
            String trimmed = line.trim();
            if (trimmed.startsWith("\uFEFF")) trimmed = trimmed.substring(1).trim();
            if (trimmed.startsWith("<")) throw new IOException("Expected hosts data, received markup");
            int comment = trimmed.indexOf('#');
            if (comment >= 0) trimmed = trimmed.substring(0, comment).trim();
            if (trimmed.isEmpty()) continue;
            String[] fields = trimmed.split("\\s+");
            if (fields.length < 2 || !Set.of("0.0.0.0", "127.0.0.1", "::", "::1").contains(fields[0])) continue;
            for (int i = 1; i < fields.length; i++) {
                String host = domain(fields[i]);
                if (host != null) result.add(host);
                if (result.size() > MAX_DOMAINS) throw new IOException("Too many domains");
            }
        }
        if (result.isEmpty()) throw new IOException("No valid blocked domains");
        return Collections.unmodifiableSet(result);
    }

    public static Set<String> validatedDownload(String data, int previousCount) throws IOException {
        Set<String> domains = parseHosts(new StringReader(data));
        java.util.regex.Matcher count = java.util.regex.Pattern.compile("(?im)^#\\s*Number of unique domains:\\s*([0-9,]+)\\s*$").matcher(data);
        if (!count.find()) throw new IOException("Missing upstream list count");
        int declared;
        try { declared = Integer.parseInt(count.group(1).replace(",", "")); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid upstream count"); }
        if (domains.size() < 100 || domains.size() < declared * 98L / 100 || domains.size() > (long)declared + 10)
            throw new IOException("Incomplete or unexpectedly small list");
        if (previousCount > 0 && domains.size() < previousCount / 2) throw new IOException("Unexpected list shrinkage");
        return domains;
    }

    public static final class Index {
        private final Map<String, String> blocked;
        private final Set<String> allowed;
        public Index(Map<String, Set<String>> categories, Set<String> allow, Set<String> extra) {
            Map<String, String> next = new HashMap<>();
            for (Map.Entry<String, Set<String>> category : categories.entrySet()) {
                for (String domain : category.getValue()) next.putIfAbsent(domain, category.getKey());
            }
            for (String domain : extra) next.put(domain, "custom");
            blocked = Collections.unmodifiableMap(next);
            allowed = Collections.unmodifiableSet(new HashSet<>(allow));
        }
        public int size() { return blocked.size(); }
        public String match(String value) {
            String host = domain(value);
            if (host == null) return null;
            for (String suffix = host; suffix.contains("."); suffix = suffix.substring(suffix.indexOf('.') + 1)) {
                if (allowed.contains(suffix)) return null;
            }
            for (String suffix = host; suffix.contains("."); suffix = suffix.substring(suffix.indexOf('.') + 1)) {
                String category = blocked.get(suffix);
                if (category != null) return category;
            }
            return null;
        }
    }
}
