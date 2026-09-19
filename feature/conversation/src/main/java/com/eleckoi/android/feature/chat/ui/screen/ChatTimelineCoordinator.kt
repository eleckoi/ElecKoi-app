package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.ui.ChatIntent
import com.eleckoi.android.feature.chat.ui.ChatPresentationReadinessState
import com.eleckoi.android.feature.chat.ui.ChatUiState
import com.eleckoi.android.feature.chat.ui.ChatVisualReplyState
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayRendererFailure
import com.eleckoi.android.feature.chat.ui.roleplay.web.surface.RoleplayWebChatController
import com.eleckoi.android.feature.chat.ui.roleplay.web.surface.rememberRoleplayWebChatController
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.foundation.design.components.ContextWindowUsage
import kotlinx.coroutines.delay

/**
 * Screen-local state for the single ordinary-chat WebView transcript.
 *
 * Room/Paging remains the owner of persisted history. This coordinator only owns WebView viewport
 * state and the transient generation hand-off; it does not build a second Compose message list.
 */
internal class ChatTimelineRuntime(
    val sessionId: String,
    val generationMetrics: ChatGenerationMetrics,
    val contextWindowUsage: ContextWindowUsage?,
    val presentedMessages: List<ChatMessage>,
    val visibleMessages: List<ChatMessage>,
    val roleplay: Boolean,
    val roleplayWebActive: Boolean,
    val roleplayWebRendererFailure: RoleplayRendererFailure?,
    val roleplayWebRendererRevision: Int,
    val roleplayWebController: RoleplayWebChatController,
    val roleplayWebCanScrollForward: Boolean,
    val userBrowsedAwayFromBottom: Boolean,
    val presentationReadiness: ChatPresentationReadinessState,
    val waitingIndicatorVisible: Boolean,
    val waitingReplySlotReserved: Boolean,
    val replyPresentationActive: Boolean,
    val latestRegenerableMessage: ChatMessage?,
    val onRoleplayScrollStateChanged: (Boolean, Boolean) -> Unit,
    val onRoleplayRendererUnavailable: (RoleplayRendererFailure) -> Unit,
    val retryRoleplayRenderer: () -> Unit,
    val onRoleplayMessageRendered: (String) -> Unit,
    val resumeToEnd: () -> Unit,
    val submit: () -> Unit,
    val stop: () -> Unit,
    val regenerate: (ChatMessage) -> Unit,
)

@Composable
internal fun rememberChatTimelineRuntime(
    state: ChatUiState,
    onIntent: (ChatIntent) -> Unit,
    onSubmittedMessageInserted: () -> Unit,
): ChatTimelineRuntime {
    val draft = state.draft
    val sessionId = draft?.session?.id.orEmpty()
    val messages = draft?.session?.messages.orEmpty()
    val roleplay = state.chatLayoutMode == ChatLayoutMode.Roleplay
    val webController = rememberRoleplayWebChatController()
    var webBrowsingHistory by remember(sessionId) { mutableStateOf(false) }
    var webCanScrollForward by remember(sessionId) { mutableStateOf(false) }
    var webRendererFailure by remember(sessionId, state.chatLayoutMode) {
        mutableStateOf<RoleplayRendererFailure?>(null)
    }
    var webRendererRevision by remember(sessionId, state.chatLayoutMode) {
        mutableIntStateOf(0)
    }
    val webActive = sessionId.isNotBlank() && state.chatLayoutMode.usesUnifiedWebTranscript()
    val resumeConversationToEnd = {
        webController.scrollToBottom()
        webBrowsingHistory = false
        webCanScrollForward = false
    }
    val contentProjection = rememberChatTimelineContentProjection(
        state = state,
        sessionId = sessionId,
        messages = messages,
    )
    val presentedMessages = contentProjection.presentedMessages
    val visibleMessages = contentProjection.visibleMessages
    val latestMessage = contentProjection.latestMessage
    val generationReplyKey = contentProjection.generationReplyKey
    val assistantReplyPublished =
        latestMessage?.role == MessageRole.Assistant && latestMessage.pending
    val waitingForFirstRenderableReply = shouldShowChatWaitingReply(
        providerActive = state.isSending,
        latestMessageRole = latestMessage?.role,
        liveReplyGeometryActive = assistantReplyPublished,
    )
    var waitingIndicatorVisible by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(sessionId, waitingForFirstRenderableReply) {
        waitingIndicatorVisible = false
        if (waitingForFirstRenderableReply) {
            delay(250)
            waitingIndicatorVisible = true
        }
    }

    var pendingSubmitMessageCount by remember(sessionId) { mutableStateOf<Int?>(null) }
    var pendingReplySlotReservation by remember(sessionId) { mutableStateOf(false) }
    val waitingReplySlotReserved = shouldReserveWebWaitingReplySlot(
        webActive = webActive,
        requestQueued = pendingReplySlotReservation,
        waitingForFirstRenderableReply = waitingForFirstRenderableReply,
    )

    var visualReplyState by remember(sessionId) { mutableStateOf(ChatVisualReplyState()) }
    val presentedVisualReplyState = when {
        generationReplyKey != null -> visualReplyState.begin(generationReplyKey)
        state.generationPresentation == null -> visualReplyState.cancel()
        else -> visualReplyState
    }
    if (presentedVisualReplyState != visualReplyState) {
        SideEffect { visualReplyState = presentedVisualReplyState }
    }
    val acknowledgeMessageRendered: (String) -> Unit = { messageId ->
        val completedKey = presentedVisualReplyState.activeKey?.takeIf { key ->
            key.messageId == messageId &&
                visibleMessages.firstOrNull { it.id == messageId }?.pending == false
        }
        if (completedKey != null) {
            val nextVisualState = presentedVisualReplyState.complete(completedKey)
            if (nextVisualState != presentedVisualReplyState) {
                visualReplyState = nextVisualState
                onIntent(
                    ChatIntent.AcknowledgeGenerationPresentation(
                        generation = completedKey.generation,
                    ),
                )
            }
        }
    }
    val replyPresentationActive = presentedVisualReplyState.showStopButton(state.isSending)
    val regenerateMessage: (ChatMessage) -> Unit = { message ->
        resumeConversationToEnd()
        pendingReplySlotReservation = true
        onIntent(ChatIntent.RegenerateFrom(message))
    }

    val submitMessageInserted = pendingSubmitMessageCount?.let { messages.size > it } == true
    LaunchedEffect(state.isSending, pendingSubmitMessageCount) {
        if (!state.isSending && pendingSubmitMessageCount != null) {
            pendingSubmitMessageCount = null
        }
    }
    LaunchedEffect(sessionId, submitMessageInserted) {
        if (submitMessageInserted) {
            resumeConversationToEnd()
            withFrameNanos { }
            onSubmittedMessageInserted()
            pendingSubmitMessageCount = null
        }
    }
    LaunchedEffect(
        sessionId,
        pendingReplySlotReservation,
        waitingForFirstRenderableReply,
        assistantReplyPublished,
        state.isSending,
    ) {
        if (!pendingReplySlotReservation) return@LaunchedEffect
        if (waitingForFirstRenderableReply || assistantReplyPublished) {
            pendingReplySlotReservation = false
        } else if (!state.isSending) {
            delay(2_000)
            pendingReplySlotReservation = false
        }
    }

    return ChatTimelineRuntime(
        sessionId = sessionId,
        generationMetrics = contentProjection.generationMetrics,
        contextWindowUsage = contentProjection.contextWindowUsage,
        presentedMessages = presentedMessages,
        visibleMessages = visibleMessages,
        roleplay = roleplay,
        roleplayWebActive = webActive,
        roleplayWebRendererFailure = webRendererFailure,
        roleplayWebRendererRevision = webRendererRevision,
        roleplayWebController = webController,
        roleplayWebCanScrollForward = webCanScrollForward,
        userBrowsedAwayFromBottom = webBrowsingHistory,
        presentationReadiness = contentProjection.presentationReadiness,
        waitingIndicatorVisible = waitingIndicatorVisible,
        waitingReplySlotReserved = waitingReplySlotReserved,
        replyPresentationActive = replyPresentationActive,
        latestRegenerableMessage = contentProjection.latestRegenerableMessage,
        onRoleplayScrollStateChanged = { browsing, canScroll ->
            webBrowsingHistory = browsing
            webCanScrollForward = canScroll
        },
        onRoleplayRendererUnavailable = { failure -> webRendererFailure = failure },
        retryRoleplayRenderer = {
            webRendererFailure = null
            webRendererRevision += 1
        },
        onRoleplayMessageRendered = acknowledgeMessageRendered,
        resumeToEnd = resumeConversationToEnd,
        submit = {
            if (
                (state.input.trim().isNotBlank() || state.inputImages.isNotEmpty()) &&
                !replyPresentationActive &&
                draft != null
            ) {
                pendingSubmitMessageCount = messages.size
                pendingReplySlotReservation = true
                onIntent(ChatIntent.SendMessage)
            }
        },
        stop = {
            visualReplyState = presentedVisualReplyState.cancel()
            presentedVisualReplyState.activeKey?.let { key ->
                onIntent(ChatIntent.AcknowledgeGenerationPresentation(key.generation))
            }
            pendingSubmitMessageCount = null
            pendingReplySlotReservation = false
            onIntent(ChatIntent.StopSending)
        },
        regenerate = regenerateMessage,
    )
}
