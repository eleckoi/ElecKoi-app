package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentThreadStart
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CharacterAgentThreadStartPolicyTest {
    @Test
    fun `continues the latest successfully committed runtime thread`() {
        val start = continuationThreadStart(
            conversationId = "chat",
            messages = listOf(runtimeReply("answer", "thread-ok")),
        ) { _, messageId ->
            assertEquals("answer", messageId)
            GenerationAttemptState.Succeeded
        }

        assertEquals(AgentThreadStart.Resume("thread-ok"), start)
    }

    @Test
    fun `normal message after regeneration resumes the replacement thread`() {
        val start = continuationThreadStart(
            conversationId = "chat",
            messages = listOf(
                ChatMessage("earlier-user", MessageRole.User, "first"),
                runtimeReply("earlier-answer", ""),
                ChatMessage("replacement-user", MessageRole.User, "second"),
                runtimeReply("replacement-answer", "regenerated-thread"),
                ChatMessage("next-user", MessageRole.User, "third"),
            ),
        ) { _, messageId ->
            assertEquals("replacement-answer", messageId)
            GenerationAttemptState.Succeeded
        }

        assertEquals(AgentThreadStart.Resume("regenerated-thread"), start)
    }

    @Test
    fun `cancelled or failed latest runtime thread forces a fresh DSH session`() {
        listOf(GenerationAttemptState.Cancelled, GenerationAttemptState.Failed).forEach { state ->
            val start = continuationThreadStart(
                conversationId = "chat",
                messages = listOf(
                    runtimeReply("successful", "thread-old"),
                    runtimeReply("latest", "thread-tainted"),
                ),
            ) { _, messageId ->
                if (messageId == "latest") state else GenerationAttemptState.Succeeded
            }

            assertSame(AgentThreadStart.Fresh, start)
        }
    }

    @Test
    fun `conversation without a runtime reply uses its bound initial thread`() {
        val start = continuationThreadStart(
            conversationId = "chat",
            messages = listOf(ChatMessage("user", MessageRole.User, "hello")),
        ) { _, _ -> error("attempt lookup must not run") }

        assertSame(AgentThreadStart.BoundOrNew, start)
    }

    private fun runtimeReply(id: String, threadId: String) = ChatMessage(
        id = id,
        role = MessageRole.Assistant,
        content = "reply",
        runtimeThreadId = threadId,
    )
}
