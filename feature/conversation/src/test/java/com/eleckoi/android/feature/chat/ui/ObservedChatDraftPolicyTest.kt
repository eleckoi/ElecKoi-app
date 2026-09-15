package com.eleckoi.android.feature.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedChatDraftPolicyTest {
    @Test
    fun `generation ignores every observed draft while provider is active`() {
        assertFalse(
            shouldAcceptObservedChatDraft(
                currentSessionId = "session-1",
                currentUpdatedAt = "2026-07-16T10:00:01Z",
                observedSessionId = "session-1",
                observedUpdatedAt = "2026-07-16T10:00:02Z",
                isSending = true,
            ),
        )
    }

    @Test
    fun `delayed pre-completion snapshot cannot replace the local final reply`() {
        assertFalse(
            shouldAcceptObservedChatDraft(
                currentSessionId = "session-1",
                currentUpdatedAt = "2026-07-16T10:00:02Z",
                observedSessionId = "session-1",
                observedUpdatedAt = "2026-07-16T10:00:01Z",
                isSending = false,
            ),
        )
    }

    @Test
    fun `matching persisted revision is accepted`() {
        assertTrue(
            shouldAcceptObservedChatDraft(
                currentSessionId = "session-1",
                currentUpdatedAt = "2026-07-16T10:00:02.123Z",
                observedSessionId = "session-1",
                observedUpdatedAt = "2026-07-16T10:00:02.123Z",
                isSending = false,
            ),
        )
    }

    @Test
    fun `newer observed revision remains eligible for legitimate edits`() {
        assertTrue(
            shouldAcceptObservedChatDraft(
                currentSessionId = "session-1",
                currentUpdatedAt = "2026-07-16T10:00:02Z",
                observedSessionId = "session-1",
                observedUpdatedAt = "2026-07-16T10:00:02.1Z",
                isSending = false,
            ),
        )
    }
}
