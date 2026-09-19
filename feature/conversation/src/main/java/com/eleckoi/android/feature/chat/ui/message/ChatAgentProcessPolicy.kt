package com.eleckoi.android.feature.chat.ui.message

import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId

internal fun ChatMessage.shouldShowProcessedTimeline(): Boolean =
    role == MessageRole.Assistant && id != OpeningMessageId

/** The live process row disappears as soon as the final answer begins streaming. */
internal fun shouldShowInlineAgentProcess(
    message: ChatMessage,
    displayedText: String,
): Boolean =
    message.pending &&
        message.role == MessageRole.Assistant &&
        message.shouldShowProcessedTimeline() &&
        message.hasVisibleLiveAgentProcessRecord() &&
        displayedText.isBlank()
