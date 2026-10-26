// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import org.junit.Assert.*
import org.junit.Test

class ShortsPolicyTest {
    @Test fun `visible Shorts players match in supported YouTube variants`() {
        for (app in ShortsPolicy.packages) {
            assertTrue(ShortsPolicy.isNativeShorts(app, listOf("$app:id/reel_recycler")))
            assertTrue(ShortsPolicy.isNativeShorts(app, listOf("$app:id/reel_player_page_content")))
            assertFalse(BlockPolicy.apps.containsKey(app))
        }
    }
    @Test fun `home tabs video titles and ordinary watch pages are not Shorts players`() {
        val app = "com.google.android.youtube"
        for (ids in listOf(emptyList(), listOf("$app:id/shorts_tab"), listOf("$app:id/player_view"), listOf("$app:id/video_title"))) {
            assertFalse(ShortsPolicy.isNativeShorts(app, ids))
        }
        assertFalse(ShortsPolicy.isNativeShorts("com.example.video", listOf("com.example.video:id/reel_recycler")))
    }
    @Test fun `browser Shorts detection requires the real YouTube host and path`() {
        for (url in listOf("https://www.youtube.com/shorts/abc", "m.youtube.com/shorts/abc?feature=share", "youtube.com/shorts")) {
            assertTrue(url, ShortsPolicy.isShortsAddress(url))
            assertFalse(url, BlockPolicy.blockedAddress(url))
        }
        for (url in listOf("https://youtube.com/watch?v=abc", "https://youtube.com/", "https://youtube.com/results?search_query=shorts", "https://youtube.com.evil.example/shorts/abc", "https://example.com/youtube.com/shorts/abc", "https://youtube.com/shortstop", "https://user:pass@youtube.com/shorts/abc")) {
            assertFalse(url, ShortsPolicy.isShortsAddress(url))
        }
    }
    @Test fun `dismissed reminder does not return on each video swipe`() {
        val visit = ShortsVisit()
        assertTrue(visit.shouldRemind(true, 0))
        for (now in listOf(100L, 1_000L, 30_000L, 120_000L)) assertFalse(visit.shouldRemind(true, now))
    }
    @Test fun `brief player tree gaps do not restart the reminder`() {
        val visit = ShortsVisit()
        assertTrue(visit.shouldRemind(true, 0))
        assertFalse(visit.shouldRemind(false, 100))
        assertFalse(visit.shouldRemind(true, 500))
    }
    @Test fun `leaving Shorts and returning starts a new visit`() {
        val visit = ShortsVisit()
        assertTrue(visit.shouldRemind(true, 0))
        assertFalse(visit.shouldRemind(false, 100))
        assertTrue(visit.shouldRemind(true, 2_100))
        assertFalse(visit.shouldRemind(true, 2_200))
    }
    @Test fun `failed overlay attachment may be retried`() {
        val visit = ShortsVisit()
        assertTrue(visit.shouldRemind(true, 0))
        visit.retry()
        assertTrue(visit.shouldRemind(true, 100))
    }
}
