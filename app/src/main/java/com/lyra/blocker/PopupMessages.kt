// SPDX-License-Identifier: GPL-3.0-only
package com.lyra.blocker

import kotlin.random.Random

data class PopupMessage(val title: String, val accent: String, val body: String)

/** Pick once per appearance, like the chat greeting, without consecutive repeats. */
class MessageRotation(private val messages: List<PopupMessage>, private val random: Random = Random.Default) {
    init { require(messages.isNotEmpty()) }
    private var previous = -1
    fun next(): PopupMessage {
        val choices = messages.indices.filter { it != previous }.ifEmpty { messages.indices.toList() }
        previous = choices[random.nextInt(choices.size)]
        return messages[previous]
    }
}

object PopupMessages {
    val ai = MessageRotation(listOf(
        PopupMessage("Same models.", "A safer way to ask.", "Access the AI models you know through Lyra’s safer, guided learning environment."),
        PopupMessage("Keep the curiosity.", "Bring it to Lyra.", "Your next question belongs in a space built for learning. Explore leading AI models with guidance that helps you think."),
        PopupMessage("A better way", "to use AI.", "The models you want, through Lyra’s safer learning space. A little guidance can take your thinking further."),
        PopupMessage("Big questions.", "Thoughtful answers.", "Bring your question to Lyra and explore AI through a safer environment that puts your learning first."),
        PopupMessage("Your ideas first.", "AI by your side.", "Use leading AI models through Lyra, with learning safeguards and guidance that keeps you in the conversation."),
        PopupMessage("More than answers.", "A way to understand.", "Let Lyra turn the models you know into learning partners, inside a safer, guided environment."),
    ))
    val shorts = MessageRotation(listOf(
        PopupMessage("One more scroll?", "Maybe a little pause.", "Take a breath. Is this how you wanted to spend your time? You can step away whenever you choose."),
        PopupMessage("Your time is yours.", "Keep a little for you.", "A quick check-in before the next Short. Is there something else you’d rather be doing?"),
        PopupMessage("Pause the scroll.", "Find your next thing.", "Look up for a moment. A stretch, a breath, or a small break might feel better than another video."),
        PopupMessage("Still watching?", "Make it a choice.", "You came here for a moment. Take a second to decide whether you want to keep scrolling."),
    ))
}
