package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.ui.screen.shouldReserveWebWaitingReplySlot
import com.eleckoi.android.feature.chat.ui.screen.shouldShowChatWaitingReply

import com.eleckoi.android.feature.chat.model.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatWaitingReplyTest {
    @Test
    fun `waiting tail exists only after a user turn and before real assistant geometry`() {
        assertTrue(
            shouldShowChatWaitingReply(
                providerActive = true,
                latestMessageRole = MessageRole.User,
                liveReplyGeometryActive = false,
            ),
        )
        assertFalse(shouldShowChatWaitingReply(false, MessageRole.User, false))
        assertFalse(shouldShowChatWaitingReply(true, MessageRole.Assistant, false))
        assertFalse(shouldShowChatWaitingReply(true, MessageRole.User, true))
    }

    @Test
    fun `web transcript reserves waiting height from request click through provider handoff`() {
        assertTrue(
            shouldReserveWebWaitingReplySlot(
                webActive = true,
                requestQueued = true,
                waitingForFirstRenderableReply = false,
            ),
        )
        assertTrue(
            shouldReserveWebWaitingReplySlot(
                webActive = true,
                requestQueued = false,
                waitingForFirstRenderableReply = true,
            ),
        )
        assertFalse(shouldReserveWebWaitingReplySlot(true, false, false))
        assertFalse(shouldReserveWebWaitingReplySlot(false, true, true))
    }
}
