// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import java.util.Locale;

/** Only the six requested vendors get manufacturer-specific background guidance. */
public final class BackgroundHelp {
    private BackgroundHelp() {}
    public static final class Guide {
        public final String manufacturer;
        public final String vendor;
        public final String url;
        private Guide(String manufacturer, String slug) {
            this.manufacturer = manufacturer;
            this.vendor = slug;
            this.url = "https://dontkillmyapp.com/" + slug;
        }
    }
    public static Guide forDevice(String manufacturer, String brand) {
        // Some related brands share an OEM value; the more specific recognised brand wins.
        Guide branded = find(brand);
        return branded != null ? branded : find(manufacturer);
    }
    private static Guide find(String value) {
        if (value == null) return null;
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "samsung": return new Guide("Samsung", "samsung");
            case "xiaomi": return new Guide("Xiaomi", "xiaomi");
            case "oppo": return new Guide("Oppo", "oppo");
            case "vivo": return new Guide("Vivo", "vivo");
            case "realme": return new Guide("Realme", "realme");
            case "oneplus": return new Guide("OnePlus", "oneplus");
            default: return null;
        }
    }
}
