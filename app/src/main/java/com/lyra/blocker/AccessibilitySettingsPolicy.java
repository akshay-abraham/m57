// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import java.util.List;
import java.util.Set;

/** Match our service detail screen, never the entire accessibility menu or another service. */
public final class AccessibilitySettingsPolicy {
    private AccessibilitySettingsPolicy() {}
    private static final Set<String> HEADINGS = Set.of("title", "entity_header_title", "toolbar_title", "action_bar_title", "collapsing_toolbar_title");
    private static final Set<String> DESCRIPTIONS = Set.of("summary", "description", "service_description", "preference_summary");
    private static final Set<String> SWITCHES = Set.of("switch_widget", "switch_bar", "switchbar", "switch_main", "switch_text");
    public static final Set<String> INSPECTED_IDS = Set.of("title", "entity_header_title", "toolbar_title", "action_bar_title", "collapsing_toolbar_title",
        "summary", "description", "service_description", "preference_summary", "switch_widget", "switch_bar", "switchbar", "switch_main", "switch_text");

    public static final class Node {
        public final String id;
        public final String text;
        public final boolean checkable;
        public Node(String id, String text, boolean checkable) { this.id = id; this.text = text; this.checkable = checkable; }
    }

    public static boolean matches(String packageName, List<Node> nodes, String appName, String ownDescription) {
        if (!"com.android.settings".equals(packageName)) return false;
        boolean ownHeading = false, description = false, mainSwitch = false;
        for (Node node : nodes) {
            if (!(node.id.startsWith("com.android.settings:id/") || node.id.startsWith("android:id/"))) continue;
            String name = node.id.substring(node.id.lastIndexOf('/') + 1);
            if (HEADINGS.contains(name) && normalize(node.text).equals(normalize(appName))) ownHeading = true;
            if (DESCRIPTIONS.contains(name) && !ownDescription.isBlank() && normalize(node.text).equals(normalize(ownDescription))) description = true;
            if (SWITCHES.contains(name) && node.checkable) mainSwitch = true;
        }
        return ownHeading && description && mainSwitch;
    }

    private static String normalize(String value) { return value.trim().replaceAll("\\s+", " "); }
}
