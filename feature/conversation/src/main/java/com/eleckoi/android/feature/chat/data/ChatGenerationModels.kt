package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment

data class ChatSendResult(
    val draft: ChatDraft,
)

data class ChatDeleteMessagesResult(
    val draft: ChatDraft,
    val deletedMessageIds: List<String>,
    val remainingMessageCount: Int,
)

data class ChatStoredSuffixDeletion(
    val session: ChatSession,
    val deletedMessageIds: List<String>,
    val remainingMessageCount: Int,
    val obsoleteRuntimeThreadIds: Set<String>,
)

/**
 * A destructive regeneration commit followed by its cancellable model turn.
 *
 * [truncatedDraft] is the durable truth after the user chose regenerate and is published before
 * model work begins. Transport preparation must never manufacture an empty assistant row for
 * presentation.
 */
data class PreparedChatRegeneration(
    val truncatedDraft: ChatDraft,
    val session: ChatSession,
    internal val prompt: String,
    internal val config: ModelConfig,
    internal val pendingMessageId: String,
    internal val userMessageId: String,
    internal val inputImages: List<ChatUserImageAttachment> = emptyList(),
    internal val obsoleteRuntimeThreadIds: Set<String> = emptySet(),
)
