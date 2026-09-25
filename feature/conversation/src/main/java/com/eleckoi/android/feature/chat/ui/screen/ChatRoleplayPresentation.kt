package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eleckoi.android.engine.immersive.model.FrontendWorkspace
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.ui.ChatRenderingPreferences
import com.eleckoi.android.feature.chat.ui.ChatUiState
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptModel
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptProjectionCache
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.buildRoleplayTranscriptModel

internal data class ChatRoleplayPresentation(
    val messages: List<ChatMessage>,
    val transcript: RoleplayTranscriptModel?,
)

/** Builds the single WebView projection used by every ordinary-chat layout. */
@Composable
internal fun rememberChatRoleplayPresentation(
    active: Boolean,
    sessionId: String,
    draft: ChatDraft?,
    visibleMessages: List<ChatMessage>,
    state: ChatUiState,
    renderingPreferences: ChatRenderingPreferences,
    frontendWorkspace: FrontendWorkspace,
): ChatRoleplayPresentation {
    val projectionCache = remember(sessionId) { RoleplayTranscriptProjectionCache() }
    val inactiveGenerationCache = remember(sessionId) {
        InactiveGenerationPresentationCache()
    }
    if (!active || draft == null) {
        return ChatRoleplayPresentation(messages = emptyList(), transcript = null)
    }
    val presentedMessages = remember(
        visibleMessages,
        draft.session.id,
        state.isSending,
        state.generationPresentation,
    ) {
        inactiveGenerationCache.project(
            messages = visibleMessages,
            conversationId = draft.session.id,
            generationRunning = state.isSending,
            generationSessionId = state.generationPresentation?.sessionId,
            generationMessageId = state.generationPresentation?.assistantMessageId,
            observedAtMillis = System.currentTimeMillis(),
        )
    }
    val transcript = remember(
        presentedMessages,
        draft.session.id,
        draft.session.characterPersona,
        draft.openingOptions,
        draft.selectedOpeningOptionId,
        draft.openingSelectionEnabled,
        state.chatLayoutMode,
        state.appearance,
        state.chatAvatarShape,
        state.chatAvatarSize,
        state.chatNameFontSize,
        state.chatNameAvatarSpacing,
        state.chatAreaHorizontalPadding,
        state.chatReplySpacing,
        state.chatTurnSpacing,
        state.chatMessageFontSize,
        state.chatLineHeightMultiplier,
        state.chatLetterSpacing,
        state.chatParagraphSpacing,
        state.assistantBubbleEnabled,
        state.chatBubbleCornerRadius,
        state.chatRoleplayCardPanel,
        state.chatRoleplayTimestampsEnabled,
        state.chatRoleplayMessageFloorsEnabled,
        renderingPreferences,
        frontendWorkspace.messageRendererEnabled,
        state.historyHasMore,
        state.historyPageLoading,
        state.deleteMessagesOpen,
        state.deleteFromMessageId,
    ) {
        buildRoleplayTranscriptModel(
            draft = draft,
            messages = presentedMessages,
            layoutMode = state.chatLayoutMode,
            appearance = state.appearance,
            avatarShape = state.chatAvatarShape,
            avatarSize = state.chatAvatarSize,
            nameFontSize = state.chatNameFontSize,
            avatarGap = state.chatNameAvatarSpacing,
            horizontalPadding = state.chatAreaHorizontalPadding,
            replySpacing = state.chatReplySpacing,
            turnSpacing = state.chatTurnSpacing,
            messageFontSize = state.chatMessageFontSize,
            lineHeightMultiplier = state.chatLineHeightMultiplier,
            letterSpacing = state.chatLetterSpacing,
            paragraphSpacing = state.chatParagraphSpacing,
            assistantBubbleEnabled = state.assistantBubbleEnabled,
            bubbleCornerRadius = state.chatBubbleCornerRadius,
            cardPanel = state.chatRoleplayCardPanel &&
                state.chatLayoutMode == com.eleckoi.android.feature.preferences.ChatLayoutMode.Roleplay,
            showRoleplayTimestamps = state.chatRoleplayTimestampsEnabled,
            showRoleplayMessageFloors = state.chatRoleplayMessageFloorsEnabled,
            renderingPreferences = renderingPreferences,
            frontendRendererEnabled = frontendWorkspace.messageRendererEnabled,
            historyHasMore = state.historyHasMore,
            historyLoading = state.historyPageLoading,
            deleteMode = state.deleteMessagesOpen,
            deleteFromMessageId = state.deleteFromMessageId.orEmpty(),
            projectionCache = projectionCache,
        )
    }
    return ChatRoleplayPresentation(messages = presentedMessages, transcript = transcript)
}
