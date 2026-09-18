package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
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
import com.eleckoi.android.feature.chat.ui.message.ChatTimelineItem
import com.eleckoi.android.feature.chat.ui.message.ChatTimelinePresentation
import com.eleckoi.android.feature.chat.ui.message.rememberChatTimelineItems
import com.eleckoi.android.foundation.design.components.ContextWindowUsage

internal data class ChatTimelineContentProjection(
    val presentedMessages: List<ChatMessage>,
    val visibleMessages: List<ChatMessage>,
    val timelinePresentation: ChatTimelinePresentation,
    val timelineItems: List<ChatTimelineItem>,
    val latestMessage: ChatMessage?,
    val generationReplyKey: ChatVisualReplyKey?,
    val markdownCacheScopeKey: String,
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
    roleplay: Boolean,
    roleplayWebActive: Boolean,
    userBrowsedAwayFromBottom: Boolean,
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
    val liveReplyWholeMessageIds = remember(sessionId) { mutableStateMapOf<String, Unit>() }
    val generationReplyMessageId = generationReplyKey?.messageId
    val liveReplyStructureRevision = liveReplyWholeMessageIds.size +
        if (
            generationReplyMessageId != null &&
            !liveReplyWholeMessageIds.containsKey(generationReplyMessageId)
        ) 1 else 0
    val isLiveReplyStructurePinned = remember(sessionId, generationReplyMessageId) {
        { messageId: String ->
            shouldPinLiveReplyStructure(
                messageId = messageId,
                activeMessageId = generationReplyMessageId,
                watchedMessageIds = liveReplyWholeMessageIds.keys,
            )
        }
    }
    if (
        generationReplyMessageId != null &&
        !liveReplyWholeMessageIds.containsKey(generationReplyMessageId)
    ) {
        SideEffect { liveReplyWholeMessageIds[generationReplyMessageId] = Unit }
    }

    val visibleHistoryStart = if (
        state.historyHasMore &&
        presentedMessages.size > 1 &&
        presentedMessages.firstOrNull()?.id == OpeningMessageId
    ) 1 else 0
    val visibleMessageWindow = remember(sessionId) { ChatVisibleMessageWindowCache() }
    val visibleMessages = visibleMessageWindow.project(presentedMessages, visibleHistoryStart)
    val markdownCacheScopeKey = "chat:$sessionId"
    val timelinePresentation = if (roleplayWebActive) {
        EmptyChatTimelinePresentation
    } else {
        rememberChatTimelineItems(
            messages = visibleMessages,
            preparationMessages = visibleMessages,
            cacheScopeKey = markdownCacheScopeKey,
            messageIndexOffset = visibleHistoryStart,
            preparedFragmentsEnabled = !roleplay,
            allowPreparedSplitsToPublish = !userBrowsedAwayFromBottom,
            pinnedWholeMessageRevision = liveReplyStructureRevision,
            isWholeMessagePinned = isLiveReplyStructurePinned,
        )
    }
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
        allowCachedReveal = !roleplayWebActive,
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
    val contextWindowUsage = nextContextWindowUsage ?: generationStatsPresentationCache.contextWindowUsage
    SideEffect {
        generationStatsPresentationCache.commit(
            metrics = generationMetrics,
            contextWindowUsage = contextWindowUsage,
        )
    }
    val latestRegenerableMessage = latestMessage?.takeIf(ChatMessage::isRegenerableMessage)
    return ChatTimelineContentProjection(
        presentedMessages = presentedMessages,
        visibleMessages = visibleMessages,
        timelinePresentation = timelinePresentation,
        timelineItems = timelinePresentation.items,
        latestMessage = latestMessage,
        generationReplyKey = generationReplyKey,
        markdownCacheScopeKey = markdownCacheScopeKey,
        generationMetrics = generationMetrics,
        contextWindowUsage = contextWindowUsage,
        latestRegenerableMessage = latestRegenerableMessage,
        presentationReadiness = presentationReadiness,
    )
}

internal fun ChatMessage.isRegenerableMessage(): Boolean = when (role) {
    MessageRole.User -> content.isNotBlank() || inputImageAttachments.isNotEmpty()
    MessageRole.Assistant -> id != OpeningMessageId
    MessageRole.System -> false
}

private val EmptyChatTimelinePresentation = ChatTimelinePresentation(
    items = emptyList(),
    preparationComplete = true,
)
