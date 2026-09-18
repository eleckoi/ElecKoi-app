package com.eleckoi.android.feature.chat.ui.screen

import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRegenerableMessageTest {
    @Test
    fun `pure image user message remains regenerable`() {
        val message = ChatMessage(
            id = "user-image",
            role = MessageRole.User,
            content = "",
            inputImageAttachments = listOf(
                ChatUserImageAttachment(
                    id = "image",
                    localPath = "/input/image.png",
                    mediaType = "image/png",
                ),
            ),
        )

        assertTrue(message.isRegenerableMessage())
    }

    @Test
    fun `empty user and opening assistant are not regenerable`() {
        assertFalse(ChatMessage("empty", MessageRole.User, "").isRegenerableMessage())
        assertFalse(ChatMessage(OpeningMessageId, MessageRole.Assistant, "开场白").isRegenerableMessage())
    }
}
