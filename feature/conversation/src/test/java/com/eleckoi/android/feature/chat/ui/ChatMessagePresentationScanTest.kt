package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ImmutableAppendedList
import com.eleckoi.android.feature.chat.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChatMessagePresentationScanTest {
    @Test
    fun oneScanKeepsRenderableFilteringConsistent() {
        val messages = listOf(
            ChatMessage("user", MessageRole.User, "hello"),
            ChatMessage(
                id = "assistant-1",
                role = MessageRole.Assistant,
                content = "reply",
            ),
            ChatMessage(
                id = "assistant-awaiting",
                role = MessageRole.Assistant,
                content = "",
                pending = true,
            ),
        )

        val scan = scanChatMessages(messages)

        assertEquals(listOf("user", "assistant-1"), scan.renderableMessages.map { it.id })
    }

    @Test
    fun streamedTailReusesTheLongHistoryScan() {
        val history = List(1_000) { index ->
            ChatMessage(
                id = "assistant-$index",
                role = MessageRole.Assistant,
                content = "history",
            )
        }
        val cache = ChatMessagePresentationScanCache()

        val first = cache.scan(
            ImmutableAppendedList(
                history,
                ChatMessage("tail", MessageRole.Assistant, "a", pending = true),
            ),
        )
        val second = cache.scan(
            ImmutableAppendedList(
                history,
                ChatMessage("tail", MessageRole.Assistant, "ab", pending = true),
            ),
        )

        val firstMessages = first.renderableMessages as ImmutableAppendedList
        val secondMessages = second.renderableMessages as ImmutableAppendedList
        assertSame(firstMessages.prefix, secondMessages.prefix)
        assertEquals("ab", secondMessages.tail.content)
    }

    @Test
    fun hidingTheOpeningKeepsAStableStreamedPrefix() {
        val history = listOf(
            ChatMessage("opening", MessageRole.Assistant, "opening"),
            ChatMessage("user", MessageRole.User, "hello"),
        )
        val cache = ChatVisibleMessageWindowCache()

        val first = cache.project(
            ImmutableAppendedList(
                history,
                ChatMessage("tail", MessageRole.Assistant, "a", pending = true),
            ),
            hiddenPrefixCount = 1,
        ) as ImmutableAppendedList
        val second = cache.project(
            ImmutableAppendedList(
                history,
                ChatMessage("tail", MessageRole.Assistant, "ab", pending = true),
            ),
            hiddenPrefixCount = 1,
        ) as ImmutableAppendedList

        assertSame(first.prefix, second.prefix)
        assertEquals(listOf("user", "tail"), second.map { it.id })
    }
}
