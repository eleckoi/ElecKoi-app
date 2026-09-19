package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.preferences.ChatLayoutMode

/** Ordinary chat has one transcript engine; modes are WebView layout projections, not renderers. */
internal fun ChatLayoutMode.usesUnifiedWebTranscript(): Boolean = when (this) {
    ChatLayoutMode.Social,
    ChatLayoutMode.Agent,
    ChatLayoutMode.Roleplay,
    -> true
}

/** A modal message editor owns focus; the conversation behind it must ignore blank-tap dismissal. */
internal fun shouldEnableChatBlankTapFocusDismiss(editingMessageOpen: Boolean): Boolean =
    !editingMessageOpen

internal fun shouldShowChatWaitingReply(
    providerActive: Boolean,
    latestMessageRole: MessageRole?,
    liveReplyGeometryActive: Boolean,
): Boolean = providerActive &&
    latestMessageRole == MessageRole.User &&
    !liveReplyGeometryActive

internal fun shouldReserveWebWaitingReplySlot(
    webActive: Boolean,
    requestQueued: Boolean,
    waitingForFirstRenderableReply: Boolean,
): Boolean = webActive && (requestQueued || waitingForFirstRenderableReply)

internal val ChatWaitingReplySlotHeight = 28.dp
