// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import org.junit.Assert.*
import org.junit.Test

/** Regression coverage for the Curbox matcher actually used by the redirect policy. */
class KeywordMatcherTest {
    @Test fun `domain rules respect URL boundaries`() {
        val patterns = KeywordMatcher.compileKeywords(listOf("claude.ai"))
        assertTrue(KeywordMatcher.matchesPatterns(patterns, "https://www.claude.ai/chat"))
        assertTrue(KeywordMatcher.matchesPatterns(patterns, "claude.ai?query=learn"))
        assertFalse(KeywordMatcher.matchesPatterns(patterns, "claude.ai.example.com"))
    }
    @Test fun `wildcards support subdomains without matching domain suffix tricks`() {
        val patterns = KeywordMatcher.compileKeywords(listOf("*.perplexity.ai"))
        assertTrue(KeywordMatcher.matchesPatterns(patterns, "perplexity.ai/search"))
        assertTrue(KeywordMatcher.matchesPatterns(patterns, "labs.perplexity.ai/search"))
        assertFalse(KeywordMatcher.matchesPatterns(patterns, "perplexity.ai.example.com/search"))
    }
    @Test fun `invalid regex input does not crash matching`() {
        val patterns = KeywordMatcher.compileKeywords(listOf("r:[", "", "chatgpt.com"))
        assertTrue(KeywordMatcher.matchesPatterns(patterns, "chatgpt.com"))
        assertFalse(KeywordMatcher.matchesPatterns(patterns, "example.com"))
    }
}
