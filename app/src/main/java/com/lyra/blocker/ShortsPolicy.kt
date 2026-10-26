// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import java.net.URI

/** A reminder policy, deliberately separate from the AI redirect catalog. */
object ShortsPolicy {
    val packages = setOf(
        "com.google.android.youtube", "app.revanced.android.youtube", "app.morphe.android.youtube",
    )
    // From Curbox ReelAppConfig.youtubeScript; visible player IDs, not the Shorts tab.
    val playerIds = setOf("reel_recycler", "reel_player_page_content")

    fun isNativeShorts(packageName: String, visibleIds: Collection<String>): Boolean =
        packageName in packages && playerIds.any { "$packageName:id/$it" in visibleIds }

    fun isShortsAddress(address: String): Boolean = runCatching {
        val input = address.trim()
        val uri = URI(if (input.contains("://")) input else "https://$input")
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme in setOf("http", "https") && uri.userInfo == null &&
            (host == "youtube.com" || host.endsWith(".youtube.com")) &&
            (uri.path == "/shorts" || uri.path.startsWith("/shorts/"))
    }.getOrDefault(false)
}

/** Swipes and brief accessibility-tree gaps must not reshow a dismissed reminder. */
class ShortsVisit {
    private var reminded = false
    private var outsideSince: Long? = null

    fun retry() { reminded = false }

    fun shouldRemind(inShorts: Boolean, now: Long): Boolean {
        if (!inShorts) {
            if (outsideSince == null) outsideSince = now
            return false
        }
        if (outsideSince?.let { now - it >= 2_000 } == true) reminded = false
        outsideSince = null
        if (reminded) return false
        reminded = true
        return true
    }
}
