// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import org.junit.Assert.*
import org.junit.Test

class BlockPolicyTest {
    @Test fun `major AI apps are blocked but general purpose apps are not`() {
        assertEquals("ChatGPT", BlockPolicy.apps["com.openai.chatgpt"])
        assertEquals("Gemini", BlockPolicy.apps["com.google.android.apps.bard"])
        assertEquals("Perplexity", BlockPolicy.apps["ai.perplexity.app.android"])
        assertEquals("Meta AI", BlockPolicy.apps["com.facebook.stella"])
        assertEquals("Nova", BlockPolicy.apps["com.scaleup.chatai"])
        assertEquals("Genie", BlockPolicy.apps["co.appnation.geniechat"])
        assertEquals("ChatOn", BlockPolicy.apps["ai.chat.gpt.bot"])
        assertEquals("Chat Smith", BlockPolicy.apps["com.smartwidgetlabs.chatgpt"])
        assertEquals("Pi", BlockPolicy.apps["ai.inflection.pi"])
        assertEquals("Replika", BlockPolicy.apps["ai.replika.app"])
        assertEquals("Qwen Studio", BlockPolicy.apps["ai.qwenlm.chat.android"])
        assertFalse(BlockPolicy.apps.containsKey("com.whatsapp"))
        assertFalse(BlockPolicy.apps.containsKey(BlockPolicy.google))
        assertFalse(BlockPolicy.apps.containsKey("com.android.chrome"))
        assertFalse(BlockPolicy.apps.containsKey("com.lyra.blocker"))
    }
    @Test fun `Meta AI conversation headers block in both WhatsApp variants`() {
        for (app in BlockPolicy.whatsapp) {
            assertEquals("Meta AI", BlockPolicy.embeddedAi(app, listOf(
                BlockPolicy.Label("$app:id/conversation_contact_name", "Meta AI"),
            )))
            assertEquals("Meta AI", BlockPolicy.embeddedAi(app, listOf(
                BlockPolicy.Label("$app:id/conversation_title", "  meta ai  "),
            )))
        }
    }
    @Test fun `Meta AI mention in a message or shortcut does not block normal WhatsApp`() {
        val labels = listOf(
            BlockPolicy.Label("com.whatsapp:id/conversation_contact_name", "Alice"),
            BlockPolicy.Label("com.whatsapp:id/message_text", "Meta AI"),
            BlockPolicy.Label("com.whatsapp:id/fab", "Meta AI", "Ask Meta AI"),
            BlockPolicy.Label("com.whatsapp:id/search_bar", "Ask Meta AI or Search"),
        )
        assertNull(BlockPolicy.embeddedAi("com.whatsapp", labels))
        assertNull(BlockPolicy.embeddedAi("com.whatsapp", emptyList()))
        assertNull(BlockPolicy.embeddedAi("com.whatsapp", listOf(
            BlockPolicy.Label("com.whatsapp:id/conversation_contact_name", "Meta AI study group"),
        )))
    }
    @Test fun `Google search results mentioning Gemini do not block search`() {
        assertNull(BlockPolicy.embeddedAi(BlockPolicy.google, listOf(
            BlockPolicy.Label("search_result", "Gemini"),
        )))
        assertEquals("Gemini", BlockPolicy.embeddedAi(BlockPolicy.google, listOf(
            BlockPolicy.Label("google:id/assistant_title", "Gemini"),
        )))
    }
    @Test fun `AI websites and subdomains block but unrelated and deceptive URLs do not`() {
        for (address in listOf("https://chatgpt.com/", "gemini.google.com/app", "https://www.perplexity.ai/search?q=study", "https://sub.claude.ai/chat")) {
            assertTrue(address, BlockPolicy.blockedAddress(address))
        }
        for (address in listOf("https://lyra-edu.vercel.app/chat", "https://lyra-dev.vercel.app/chat", "https://chatgpt.com.evil.example/", "https://example.com/chatgpt.com", "https://example.com/?q=gemini.google.com", "https://notchatgpt.com")) {
            assertFalse(address, BlockPolicy.blockedAddress(address))
        }
    }
    @Test fun `destinations require unambiguous HTTPS URLs`() {
        assertTrue(BlockPolicy.validDestination("https://lyra-edu.vercel.app/chat"))
        assertTrue(BlockPolicy.validDestination("https://school.example/chat?mode=learn"))
        for (url in listOf("http://school.example", "javascript:alert(1)", "intent://lyra", "https://user:pass@school.example/chat", "https:///chat", "https://school.example:1234/", "https://school.example/#fragment", "")) {
            assertFalse(url, BlockPolicy.validDestination(url))
        }
    }
    @Test fun `custom package IDs cannot be intents or malformed names`() {
        assertTrue(BlockPolicy.validPackage("com.example.ai"))
        assertTrue(BlockPolicy.validPackage("org.chromium.webapk.abc123"))
        assertFalse(BlockPolicy.validPackage("com.example.ai;am start"))
        assertFalse(BlockPolicy.validPackage("chrome"))
        assertFalse(BlockPolicy.validPackage(""))
    }
}
