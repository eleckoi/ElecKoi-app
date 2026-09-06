package com.eleckoi.android.engine.agent.eleckoi.conversation

import kotlinx.serialization.Serializable

/** UI-neutral message projection exchanged between the Room ledger and today's chat models. */
@Serializable
data class LedgerMessage(
    val id: String,
    val role: String,
    val content: String,
    val reasoningContent: String = "",
    val provider: String = "",
    val model: String = "",
    val createdAt: String = "",
    val pending: Boolean = false,
    val variableStateJson: String = "",
    val toolCallsJson: String = "[]",
    val imageAttachmentsJson: String = "[]",
    val inputImageAttachmentsJson: String = "[]",
    /** Complete surface-specific detail used by history pages. */
    val surfaceTimelineJson: String = "",
    /** Exact native Agent history items used by the full assistant projection. */
    val modelHistoryItems: List<String> = emptyList(),
    val runtimeThreadId: String = "",
    val runtimeTurnId: String = "",
    val turnStartedAtMillis: Long = 0L,
    val turnCompletedAtMillis: Long? = null,
    /** Speaker identity is independent from provider/model execution identity. */
    val speakerId: String = "",
    val speakerKind: String = "",
    val speakerName: String = "",
    val speakerAvatarAssetId: String = "",
)

data class LedgerPage(
    val messages: List<LedgerMessage>,
    val beforeSequence: Int?,
    val hasMore: Boolean,
)

/** Shared paging item used by both role chat and the AI assistant. */
data class PagedConversationTurn(
    val stableTurnId: String,
    val sequence: Int,
    val messages: List<LedgerMessage>,
)

