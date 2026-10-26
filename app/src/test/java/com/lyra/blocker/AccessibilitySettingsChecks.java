// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import java.util.List;

public final class AccessibilitySettingsChecks {
    private static final String DESCRIPTION = "M57 redirects supported AI apps and shows Shorts reminders.";
    private static AccessibilitySettingsPolicy.Node node(String id, String text, boolean checkable) {
        return new AccessibilitySettingsPolicy.Node(id, text, checkable);
    }
    private static boolean matches(String pkg, List<AccessibilitySettingsPolicy.Node> nodes) {
        return AccessibilitySettingsPolicy.matches(pkg, nodes, "M57", DESCRIPTION);
    }
    public static void main(String[] args) { run(); System.out.println("Accessibility settings policy checks passed"); }
    public static void run() {
        List<AccessibilitySettingsPolicy.Node> own = List.of(
            node("com.android.settings:id/title", "M57", false),
            node("android:id/summary", DESCRIPTION, false),
            node("com.android.settings:id/switch_widget", "", true));
        check(matches("com.android.settings", own), "Own service detail not recognised");
        check(!matches("com.example.app", own), "An app impersonated Settings");
        check(!matches("com.android.settings", List.of(
            node("com.android.settings:id/title", "TalkBack", false), own.get(1), own.get(2))), "Other service was blocked");
        check(!matches("com.android.settings", List.of(
            own.get(0), node("android:id/summary", "Notifications for M57", false), own.get(2))), "Unrelated app settings were blocked");
        check(!matches("com.android.settings", List.of(own.get(0), own.get(1))), "List item was treated as service controls");
        check(!matches("com.android.settings", List.of(own.get(0), own.get(1),
            node("com.android.settings:id/switch_widget", "", false))), "A non-toggle placeholder was matched");
        check(!matches("com.android.settings", List.of(own.get(0), own.get(1),
            node("com.example:id/switch_widget", "", true))), "Untrusted control ID was matched");
        check(!matches("com.android.settings", List.of(
            node("com.android.settings:id/title", "M57 notes", false), own.get(1), own.get(2))), "Substring heading was matched");
        check(matches("com.android.settings", List.of(own.get(0),
            node("android:id/summary", " M57 redirects supported AI apps\nand shows Shorts reminders. ", false), own.get(2))), "Wrapped description was rejected");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
