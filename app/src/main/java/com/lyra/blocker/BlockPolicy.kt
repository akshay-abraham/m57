// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

/** Pure policy. Never match arbitrary chat text or the WhatsApp chat-list AI shortcut. */
object BlockPolicy {
    val apps = linkedMapOf(
        "com.openai.chatgpt" to "ChatGPT",
        "com.google.android.apps.bard" to "Gemini",
        "com.anthropic.claude" to "Claude",
        "com.microsoft.copilot" to "Copilot",
        "com.microsoft.office.officehubrow" to "Copilot",
        "ai.perplexity.app.android" to "Perplexity",
        "com.deepseek.chat" to "DeepSeek",
        "ai.x.grok" to "Grok",
        "ai.mistral.chat" to "Mistral",
        "com.poe.android" to "Poe",
        "ai.character.app" to "Character.AI",
        "com.foyer.merlin" to "Merlin",
        "com.facebook.stella" to "Meta AI",
        "com.scaleup.chatai" to "Nova",
        "co.appnation.geniechat" to "Genie",
        "ai.chat.gpt.bot" to "ChatOn",
        "com.smartwidgetlabs.chatgpt" to "Chat Smith",
        "ai.inflection.pi" to "Pi",
        "ai.replika.app" to "Replika",
        "ai.qwenlm.chat.android" to "Qwen Studio",
    )
    val whatsapp = setOf("com.whatsapp", "com.whatsapp.w4b")
    const val google = "com.google.android.googlequicksearchbox"
    val browsers = setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "org.chromium.chrome")
    val domains = listOf(
        "chatgpt.com", "chat.openai.com", "gemini.google.com", "claude.ai",
        "copilot.microsoft.com", "perplexity.ai", "chat.deepseek.com", "grok.com",
        "chat.mistral.ai", "poe.com", "character.ai", "meta.ai",
        "qwen.ai", "pi.ai", "replika.com", "novaapp.ai", "chaton.ai", "chatsmith.io",
        "kimi.com", "kimi.moonshot.cn", "you.com",
    )
    private val patterns = KeywordMatcher.compileKeywords(domains.flatMap { listOf(it, "*.$it") })
    private val headerIds = setOf(
        "conversation_contact_name", "conversation_title", "toolbar_title", "action_bar_title",
    )
    data class Label(val id: String, val text: String, val description: String = "", val checkable: Boolean = false)

    fun embeddedAi(packageName: String, labels: List<Label>): String? {
        if (packageName in whatsapp) {
            // Only conversation header IDs qualify. A message, search result, home-screen
            // shortcut, or a button inviting the user to ask Meta AI must not block WhatsApp.
            return if (labels.any {
                it.id.substringAfterLast('/') in headerIds &&
                    (isMetaTitle(it.text) || isMetaTitle(it.description))
            }) "Meta AI" else null
        }
        if (packageName == google) {
            return if (labels.any {
                it.id.substringAfterLast('/') in setOf("toolbar_title", "action_bar_title", "assistant_title") &&
                    it.text.trim().equals("Gemini", ignoreCase = true)
            }) "Gemini" else null
        }
        return null
    }

    private fun isMetaTitle(text: String) = text.trim().equals("Meta AI", ignoreCase = true)

    fun blockedAddress(address: String): Boolean = KeywordMatcher.matchesPatterns(patterns, address)

    fun validDestination(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.port in listOf(-1, 443) && uri.fragment == null
    }.getOrDefault(false)

    fun validPackage(value: String) = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(value)
}
