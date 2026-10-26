// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

public final class BackgroundHelpChecks {
    public static void main(String[] args) { run(); System.out.println("Manufacturer background-help checks passed"); }
    public static void run() {
        for (String maker : new String[]{"samsung", "xiaomi", "oppo", "vivo", "realme", "oneplus"}) {
            BackgroundHelp.Guide guide = BackgroundHelp.forDevice(maker, "unknown");
            check(guide != null && guide.url.equals("https://dontkillmyapp.com/" + maker), "Wrong vendor URL: " + maker);
            check(guide.vendor.equals(maker), "Wrong native-settings vendor: " + maker);
        }
        check(BackgroundHelp.forDevice(" SAMSUNG ", null).manufacturer.equals("Samsung"), "Case/whitespace matching failed");
        check(BackgroundHelp.forDevice("OPPO", "realme").manufacturer.equals("Realme"), "Specific brand did not win");
        check(BackgroundHelp.forDevice("Xiaomi", "POCO").manufacturer.equals("Xiaomi"), "OEM fallback failed");
        check(BackgroundHelp.forDevice("Google", "google") == null, "Unsupported vendor got a guide");
        check(BackgroundHelp.forDevice("Huawei", "honor") == null, "Unrequested vendor got a guide");
        check(BackgroundHelp.forDevice(null, null) == null, "Missing identity got a guide");
        check(BackgroundHelp.forDevice("samsung.example", "unknown") == null, "Substring matched an unsupported vendor");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
