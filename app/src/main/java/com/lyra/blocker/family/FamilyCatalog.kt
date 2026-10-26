// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker.family

enum class FamilyCategory(val key: String, val title: String, val path: String, val explanation: String) {
    ADULT("adult", "Adult content", "alternates/porn-only/hosts", "Curiosity is normal. Choose a boundary that supports the young person using this device, with understanding and without shame."),
    GAMBLING("gambling", "Gambling", "alternates/gambling-only/hosts", "Help keep betting and gambling websites out of this learning space."),
    BASE("base", "Adware & malware", "hosts", "Block domains on the combined upstream adware and malware list. This is a domain list, not a virus scanner."),
    SOCIAL("social", "Social websites", "alternates/social-only/hosts", "Blocks entire listed social services. Leave this off if you want normal feeds available while configuring Reels separately."),
    FAKENEWS("fakenews", "Upstream misinformation list", "alternates/fakenews-only/hosts", "Uses the upstream fakenews category. List membership is an upstream judgement, not a guarantee about every page's accuracy.");

    val urls: List<String> get() = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/$path",
        "https://cdn.jsdelivr.net/gh/StevenBlack/hosts@master/$path",
        "https://sbc.io/hosts/$path",
    )
    companion object { fun byKey(key: String) = values().firstOrNull { it.key == key } }
}
