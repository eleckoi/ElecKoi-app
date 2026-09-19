package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.feature.chat.ui.ChatMessagePresentationScanCache
import com.eleckoi.android.feature.chat.ui.ChatPresentationReadinessState
import com.eleckoi.android.feature.chat.ui.ChatUiState
import com.eleckoi.android.feature.chat.ui.ChatVisibleMessageWindowCache
import com.eleckoi.android.feature.chat.ui.ChatVisualReplyKey
import com.eleckoi.android.feature.chat.ui.chatPresentationContentRevision
import com.eleckoi.android.feature.chat.ui.composer.retainVisibleGenerationMetrics
import com.eleckoi.android.feature.chat.ui.generationVisualReplyKey
import com.eleckoi.android.feature.chat.ui.presentationSignature
import com.eleckoi.android.feature.chat.ui.rememberChatPresentationReadiness
import com.eleckoi.android.foundation.design.components.ContextWindowUsage

/** Data projection shared by every ordinary-chat WebView layout. */
internal data class ChatTimelineContentProjection(
    val presentedMessages: List<ChatMessage>,
    val visibleMessages: List<ChatMessage>,
    val latestMessage: ChatMessage?,
    val generationReplyKey: ChatVisualReplyKey?,
    val generationMetrics: ChatGenerationMetrics,
    val contextWindowUsage: ContextWindowUsage?,
    val latestRegenerableMessage: ChatMessage?,
    val presentationReadiness: ChatPresentationReadinessState,
)

private class ChatGenerationStatsPresentationCache {
    var metrics: ChatGenerationMetrics = ChatGenerationMetrics()
        private set

    var contextWindowUsage: ContextWindowUsage? = null
        private set

    fun commit(
        metrics: ChatGenerationMetrics,
        contextWindowUsage: ContextWindowUsage?,
    ) {
        this.metrics = metrics
        this.contextWindowUsage = contextWindowUsage
    }
}

@Composable
internal fun rememberChatTimelineContentProjection(
    state: ChatUiState,
    sessionId: String,
    messages: List<ChatMessage>,
): ChatTimelineContentProjection {
    val messageScanCache = remember(sessionId) { ChatMessagePresentationScanCache() }
    val messageScan = remember(messages) { messageScanCache.scan(messages) }
    val presentedMessages = messageScan.renderableMessages
    val latestMessage = presentedMessages.lastOrNull()
    val generationReplyKey = generationVisualReplyKey(
        presentation = state.generationPresentation,
        sessionId = sessionId,
        latestAssistantMessageId = latestMessage
            ?.takeIf { it.role == MessageRole.Assistant }
            ?.id,
    )

    val visibleHistoryStart = if (
        state.historyHasMore &&
        presentedMessages.size > 1 &&
        presentedMessages.firstOrNull()?.id == OpeningMessageId
    ) 1 else 0
    val visibleMessageWindow = remember(sessionId) { ChatVisibleMessageWindowCache() }
    val visibleMessages = visibleMessageWindow.project(presentedMessages, visibleHistoryStart)
    val routeEntryContentRevision = remember(sessionId) {
        chatPresentationContentRevision(messages)
    }
    val presentationSignature = remember(sessionId, routeEntryContentRevision) {
        state.presentationSignature(
            sessionId = sessionId,
            contentRevision = routeEntryContentRevision,
        )
    }
    val presentationReadiness = rememberChatPresentationReadiness(
        signature = presentationSignature,
        allowCachedReveal = false,
    )

    val sessionGenerationStats = state.draft?.session?.generationStats
    val nextGenerationMetrics = sessionGenerationStats?.metrics ?: ChatGenerationMetrics()
    val nextContextWindowUsage = sessionGenerationStats?.contextWindowUsage?.let { usage ->
        ContextWindowUsage(
            latestTokens = usage.latestTokens,
            totalTokens = usage.totalTokens,
            modelContextWindow = usage.modelContextWindow,
            systemTokens = usage.systemTokens,
            toolsTokens = usage.toolsTokens,
            messageTokens = usage.messageTokens,
        )
    }
    val generationStatsPresentationCache = remember(sessionId) {
        ChatGenerationStatsPresentationCache()
    }
    val generationMetrics = retainVisibleGenerationMetrics(
        previous = generationStatsPresentationCache.metrics,
        next = nextGenerationMetrics,
    )
    val contextWindowUsage = nextContextWindowUsage
        ?: generationStatsPresentationCache.contextWindowUsage
    SideEffect {
        generationStatsPresentationCache.commit(
            metrics = generationMetrics,
            contextWindowUsage = contextWindowUsage,
        )
    }

    return ChatTimelineContentProjection(
        presentedMessages = presentedMessages,
        visibleMessages = visibleMessages,
        latestMessage = latestMessage,
        generationReplyKey = generationReplyKey,
        generationMetrics = generationMetrics,
        contextWindowUsage = contextWindowUsage,
        latestRegenerableMessage = latestMessage?.takeIf(ChatMessage::isRegenerableMessage),
        presentationReadiness = presentationReadiness,
    )
}

internal fun ChatMessage.isRegenerableMessage(): Boolean = when (role) {
    MessageRole.User -> content.isNotBlank() || inputImageAttachments.isNotEmpty()
    MessageRole.Assistant -> id != OpeningMessageId
    MessageRole.System -> false
}
