package com.eleckoi.android.feature.chat.ui.trajectory

import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTrajectoryBindingTest {
    @Test
    fun `active generation thread wins over the previous completed message`() {
        val threadId = resolveTrajectoryRuntimeThreadId(
            generationThreadId = "active-thread",
            messages = listOf(assistant("old-thread")),
        )

        assertEquals("active-thread", threadId)
    }

    @Test
    fun `latest authoritative message supplies the completed trajectory`() {
        val threadId = resolveTrajectoryRuntimeThreadId(
            generationThreadId = "",
            messages = listOf(assistant("older-thread"), assistant("latest-thread")),
        )

        assertEquals("latest-thread", threadId)
    }

    @Test
    fun `cleared branch binding cannot reopen a deleted or regenerated trajectory`() {
        val threadId = resolveTrajectoryRuntimeThreadId(
            generationThreadId = "",
            messages = listOf(assistant("")),
        )

        assertEquals("", threadId)
    }

    private fun assistant(runtimeThreadId: String) = ChatMessage(
        id = "message-$runtimeThreadId",
        role = MessageRole.Assistant,
        content = "reply",
        runtimeThreadId = runtimeThreadId,
    )
}
