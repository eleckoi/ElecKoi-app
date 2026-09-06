package com.eleckoi.android.foundation.storage.room.agent.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Product-owned conversation root shared by role chat and the AI assistant. */
@Entity(
    tableName = "agent_conversations",
    primaryKeys = ["id"],
    indices = [Index("activeBranchId")],
)
data class AgentConversationEntity(
    val id: String,
    val surface: String,
    val activeBranchId: String,
    val createdAt: String,
    val updatedAt: String,
    /** Monotonic ledger revision. It changes in the same transaction as every timeline mutation. */
    val revision: Long = 0L,
)

/**
 * Rebuildable first-frame projection. This is never used as history or model context; Room's
 * normalized ledger above remains the only source of truth.
 */
@Entity(
    tableName = "agent_conversation_display_cache",
    primaryKeys = ["conversationId", "chunkIndex"],
    foreignKeys = [
        ForeignKey(
            entity = AgentConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AgentConversationDisplayCacheEntity(
    val conversationId: String,
    /** Bounded chunk of the serialized first-frame projection; no row may contain the whole cache. */
    val chunkIndex: Int,
    val ledgerRevision: Long,
    val payloadJson: String,
    val rendererVersion: Int,
    val updatedAt: String,
)

@Entity(
    tableName = "agent_branches",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = AgentConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId"),
        Index(value = ["conversationId", "createdAt"]),
    ],
)
data class AgentBranchEntity(
    val id: String,
    val conversationId: String,
    val parentBranchId: String?,
    val forkedFromTurnId: String?,
    val headSequence: Int,
    val name: String,
    val reason: String,
    val createdAt: String,
)

/** Stable speaker identity inside one saved conversation, separate from the model that produced it. */
@Entity(
    tableName = "conversation_speakers",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = AgentConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId"),
        Index(value = ["conversationId", "sourceSpeakerId"], unique = true),
    ],
)
data class ConversationSpeakerEntity(
    val id: String,
    val conversationId: String,
    /** Product identity such as user, narrator, or a card-internal character id. */
    val sourceSpeakerId: String,
    val kind: String,
    val displayName: String,
    val avatarAssetId: String,
)

/** Stable user/system/opening turn. Editing updates its content while preserving this identity. */
@Entity(
    tableName = "agent_turns",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = AgentConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationSpeakerEntity::class,
            parentColumns = ["id"],
            childColumns = ["speakerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("conversationId"),
        Index("speakerId"),
        Index(value = ["conversationId", "sourceMessageId"]),
    ],
)
data class AgentTurnEntity(
    val id: String,
    val conversationId: String,
    val speakerId: String,
    val sourceMessageId: String,
    val kind: String,
    val provider: String,
    val model: String,
    val createdAt: String,
    val variableStateJson: String,
)

/** One visible reply in a turn. Several ordered speakers may answer the same turn. */
@Entity(
    tableName = "agent_responses",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = AgentConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AgentTurnEntity::class,
            parentColumns = ["id"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationSpeakerEntity::class,
            parentColumns = ["id"],
            childColumns = ["speakerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("conversationId"),
        Index("speakerId"),
        Index(value = ["turnId", "responseIndex"], unique = true),
    ],
)
data class AgentResponseEntity(
    val id: String,
    val conversationId: String,
    val turnId: String,
    val responseIndex: Int,
    val speakerId: String,
    val sourceMessageId: String,
    val status: String,
    val provider: String,
    val model: String,
    val createdAt: String,
    val variableStateJson: String,
    val runtimeThreadId: String,
    val runtimeTurnId: String,
    val turnStartedAtMillis: Long,
    val turnCompletedAtMillis: Long?,
)

/**
 * Durable execution history for every generated reply and every generated image.
 *
 * A response row is the currently selected conversation projection. It may be overwritten by
 * regeneration, so it cannot also be the execution log. Attempts are append-only identities:
 * regeneration supersedes the previous attempt and late completions can be rejected by id.
 */
@Entity(
    tableName = "generation_attempts",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = AgentConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId"),
        Index(value = ["conversationId", "kind", "ownerId", "attemptNumber"], unique = true),
        Index(value = ["conversationId", "state"]),
        Index("parentAttemptId"),
        Index("outputMessageId"),
    ],
)
data class GenerationAttemptEntity(
    val id: String,
    val conversationId: String,
    /** `reply` or `image`. */
    val kind: String,
    /** Stable logical owner: user-message id for replies, attachment id for images. */
    val ownerId: String,
    /** Image attempts point at the reply attempt that planned them. */
    val parentAttemptId: String?,
    /** Visible assistant message that receives this attempt's projection. */
    val outputMessageId: String,
    val attemptNumber: Int,
    /** queued/running/succeeded/failed/cancelled/interrupted/superseded. */
    val state: String,
    val createdAtMillis: Long,
    val startedAtMillis: Long?,
    val finishedAtMillis: Long?,
    val errorMessage: String,
    val outputPath: String,
    val supersededByAttemptId: String?,
)

/** Small branch path projection used for keyset pagination. Message bodies are never copied here. */
@Entity(
    tableName = "agent_branch_turns",
    primaryKeys = ["branchId", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = AgentBranchEntity::class,
            parentColumns = ["id"],
            childColumns = ["branchId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AgentTurnEntity::class,
            parentColumns = ["id"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["branchId", "turnId"], unique = true),
        Index("turnId"),
    ],
)
data class AgentBranchTurnEntity(
    val branchId: String,
    val sequence: Int,
    val turnId: String,
)

/** Normalized visible and model-native payload owned by exactly one turn or response. */
@Entity(
    tableName = "agent_content_parts",
    primaryKeys = ["ownerType", "ownerId", "partIndex", "chunkIndex"],
    foreignKeys = [
        ForeignKey(
            entity = AgentConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId"),
        Index(value = ["ownerType", "ownerId"]),
    ],
)
data class AgentContentPartEntity(
    val conversationId: String,
    val ownerType: String,
    val ownerId: String,
    val partIndex: Int,
    val kind: String,
    val text: String,
    val payloadJson: String,
    /** One logical content part may span several bounded Room rows. */
    val chunkIndex: Int = 0,
)
