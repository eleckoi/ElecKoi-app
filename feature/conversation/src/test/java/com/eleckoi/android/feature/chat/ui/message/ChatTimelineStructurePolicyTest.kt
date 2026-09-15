package com.eleckoi.android.feature.chat.ui.message

import com.eleckoi.android.feature.chat.ui.screen.shouldPinLiveReplyStructure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineStructurePolicyTest {
    @Test
    fun `visibly streamed reply keeps its whole-message structure for the current visit`() {
        assertFalse(
            shouldUsePreparedTimelineFragments(
                messageId = "assistant-live",
                isWholeMessagePinned = setOf("assistant-live")::contains,
            ),
        )
    }

    @Test
    fun `prepared fragments become available after the conversation is reopened`() {
        assertTrue(
            shouldUsePreparedTimelineFragments(
                messageId = "assistant-live",
                isWholeMessagePinned = emptySet<String>()::contains,
            ),
        )
    }

    @Test
    fun `roleplay reply remains one row after the conversation is reopened`() {
        assertFalse(
            shouldUsePreparedTimelineFragments(
                messageId = "assistant-roleplay",
                preparedFragmentsEnabled = false,
                isWholeMessagePinned = emptySet<String>()::contains,
            ),
        )
    }

    @Test
    fun `pinning one live reply does not disable prepared history fragments`() {
        assertTrue(
            shouldUsePreparedTimelineFragments(
                messageId = "assistant-history",
                isWholeMessagePinned = setOf("assistant-live")::contains,
            ),
        )
    }

    @Test
    fun `last image completion cannot unpin the reply watched in this visit`() {
        assertTrue(
            shouldPinLiveReplyStructure(
                messageId = "assistant-with-images",
                activeMessageId = null,
                watchedMessageIds = setOf("assistant-with-images"),
            ),
        )
    }
}
