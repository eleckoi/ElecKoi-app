package com.eleckoi.android.foundation.storage.room.agent.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import androidx.paging.PagingSource
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentBranchEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentBranchTurnEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentContentPartEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentConversationEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentConversationDisplayCacheEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.ConversationSpeakerEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentResponseEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentTurnEntity

@Dao
interface AgentLedgerDao {
    /** Only attachment metadata, with bounded rows and a stable keyset across storage chunks. */
    @Query("""
        SELECT * FROM agent_content_parts
        WHERE kind IN ('input_images', 'images')
          AND (ownerType, ownerId, partIndex, chunkIndex) >
              (:ownerType, :ownerId, :partIndex, :chunkIndex)
        ORDER BY ownerType, ownerId, partIndex, chunkIndex
        LIMIT :limit
    """)
    fun attachmentPartsPage(
        ownerType: String, ownerId: String,
        partIndex: Int, chunkIndex: Int, limit: Int,
    ): List<AgentContentPartEntity>

    @Query("SELECT * FROM agent_conversations WHERE id = :conversationId LIMIT 1")
    fun conversation(conversationId: String): AgentConversationEntity?

    @Upsert
    fun upsertConversation(conversation: AgentConversationEntity)

    @Query(
        "UPDATE agent_conversations SET updatedAt = :updatedAt, revision = revision + 1 " +
            "WHERE id = :conversationId",
    )
    fun advanceConversationRevision(conversationId: String, updatedAt: String)

    @Query("SELECT revision FROM agent_conversations WHERE id = :conversationId LIMIT 1")
    fun conversationRevision(conversationId: String): Long?

    @Query(
        "SELECT * FROM agent_conversation_display_cache " +
            "WHERE conversationId = :conversationId ORDER BY chunkIndex ASC",
    )
    fun displayCache(conversationId: String): List<AgentConversationDisplayCacheEntity>

    @Upsert
    fun upsertDisplayCache(cache: List<AgentConversationDisplayCacheEntity>)

    @Query("DELETE FROM agent_conversation_display_cache WHERE conversationId = :conversationId")
    fun deleteDisplayCache(conversationId: String)

    @Query("DELETE FROM agent_conversations WHERE id = :conversationId")
    fun deleteConversation(conversationId: String)

    @Upsert
    fun upsertBranch(branch: AgentBranchEntity)

    @Upsert
    fun upsertSpeakers(speakers: List<ConversationSpeakerEntity>)

    @Query("SELECT * FROM conversation_speakers WHERE id IN (:ids)")
    fun speakers(ids: List<String>): List<ConversationSpeakerEntity>

    @Upsert
    fun upsertTurns(turns: List<AgentTurnEntity>)

    @Upsert
    fun upsertResponses(responses: List<AgentResponseEntity>)

    @Upsert
    fun upsertBranchTurns(turns: List<AgentBranchTurnEntity>)

    @Upsert
    fun upsertContentParts(parts: List<AgentContentPartEntity>)

    /** Deletes only obsolete chunks; recovery checkpoints retain unchanged response rows. */
    @Delete
    fun deleteContentPartRows(parts: List<AgentContentPartEntity>)

    @Query("SELECT * FROM agent_branch_turns WHERE branchId = :branchId ORDER BY sequence ASC")
    fun branchTurns(branchId: String): List<AgentBranchTurnEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM agent_branch_turns AS path
        INNER JOIN agent_conversations AS conversation
            ON conversation.activeBranchId = path.branchId
        WHERE conversation.id = :conversationId
        """,
    )
    fun activeTurnCount(conversationId: String): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM agent_branch_turns AS path
            INNER JOIN agent_conversations AS conversation
                ON conversation.activeBranchId = path.branchId
            INNER JOIN agent_responses AS response ON response.turnId = path.turnId
            WHERE conversation.id = :conversationId AND response.status = :status
        )
        """,
    )
    fun hasActiveResponseWithStatus(conversationId: String, status: String): Boolean

    @Query(
        """
        SELECT path.sequence AS sequence, path.turnId AS turnId
        FROM agent_branch_turns AS path
        INNER JOIN agent_conversations AS conversation
            ON conversation.activeBranchId = path.branchId
        WHERE conversation.id = :conversationId
        ORDER BY path.sequence ASC
        """,
    )
    fun pagingTurnRefs(conversationId: String): PagingSource<Int, AgentPagedTurnRef>

    @Query(
        "SELECT * FROM agent_branch_turns WHERE branchId = :branchId AND turnId = :turnId LIMIT 1",
    )
    fun branchTurn(branchId: String, turnId: String): AgentBranchTurnEntity?

    @Query(
        "SELECT * FROM agent_branch_turns WHERE branchId = :branchId ORDER BY sequence DESC LIMIT 1",
    )
    fun lastBranchTurn(branchId: String): AgentBranchTurnEntity?

    @Query(
        """
        SELECT * FROM agent_branch_turns
        WHERE branchId = :branchId AND sequence < :beforeSequence
        ORDER BY sequence DESC
        LIMIT :limit
        """,
    )
    fun pageBefore(
        branchId: String,
        beforeSequence: Int,
        limit: Int,
    ): List<AgentBranchTurnEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM agent_branch_turns
            WHERE branchId = :branchId AND sequence < :beforeSequence
        )
        """,
    )
    fun hasBefore(branchId: String, beforeSequence: Int): Boolean

    @Query(
        """
        SELECT COUNT(DISTINCT path.turnId) + COUNT(response.id)
        FROM agent_branch_turns AS path
        LEFT JOIN agent_responses AS response ON response.turnId = path.turnId
        WHERE path.branchId = :branchId
        """,
    )
    fun branchMessageCount(branchId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM agent_branch_turns AS path
        INNER JOIN agent_turns AS turn ON turn.id = path.turnId
        WHERE path.branchId = :branchId AND turn.kind = 'user'
        """,
    )
    fun branchUserMessageCount(branchId: String): Int

    @Query("SELECT * FROM agent_turns WHERE id IN (:ids)")
    fun turns(ids: List<String>): List<AgentTurnEntity>

    @Query(
        "SELECT * FROM agent_turns WHERE conversationId = :conversationId " +
            "AND sourceMessageId = :sourceMessageId LIMIT 1",
    )
    fun turnBySourceMessageId(
        conversationId: String,
        sourceMessageId: String,
    ): AgentTurnEntity?

    @Query(
        "SELECT * FROM agent_responses WHERE conversationId = :conversationId " +
            "AND sourceMessageId = :sourceMessageId LIMIT 1",
    )
    fun responseBySourceMessageId(conversationId: String, sourceMessageId: String): AgentResponseEntity?

    @Query(
        "SELECT DISTINCT runtimeThreadId FROM agent_responses " +
            "WHERE conversationId = :conversationId AND runtimeThreadId != ''",
    )
    fun runtimeThreadIds(conversationId: String): List<String>

    @Query(
        "UPDATE agent_responses SET runtimeThreadId = '', runtimeTurnId = '' " +
            "WHERE conversationId = :conversationId",
    )
    fun clearRuntimeAssociations(conversationId: String)

    /** Public IDs and runtime references only: never materialize message bodies for suffix deletion. */
    @Query("""
        SELECT path.sequence AS sequence, turn.id AS turnId,
            turn.sourceMessageId AS turnMessageId,
            turn.variableStateJson AS turnVariableStateJson,
            response.id AS responseId,
            response.responseIndex AS responseIndex,
            response.sourceMessageId AS responseMessageId,
            response.variableStateJson AS responseVariableStateJson
        FROM agent_branch_turns AS path
        INNER JOIN agent_turns AS turn ON turn.id = path.turnId
        LEFT JOIN agent_responses AS response ON response.turnId = turn.id
        WHERE path.branchId = :branchId AND path.sequence >= :fromSequence
        ORDER BY path.sequence ASC, response.responseIndex ASC
    """)
    fun publicMessagesFrom(branchId: String, fromSequence: Int): List<AgentPublicMessageRef>

    @Query("SELECT * FROM agent_responses WHERE turnId IN (:turnIds)")
    fun responsesForTurns(turnIds: List<String>): List<AgentResponseEntity>

    @Query("SELECT * FROM agent_responses WHERE turnId = :turnId ORDER BY responseIndex ASC LIMIT 1")
    fun responseForTurn(turnId: String): AgentResponseEntity?

    @Query(
        """
        SELECT * FROM agent_content_parts
        WHERE ownerType = :ownerType AND ownerId IN (:ownerIds)
        ORDER BY ownerId ASC, partIndex ASC, chunkIndex ASC
        """,
    )
    fun contentParts(ownerType: String, ownerIds: List<String>): List<AgentContentPartEntity>

    @Query(
        "DELETE FROM agent_content_parts WHERE ownerType = :ownerType AND ownerId IN (:ownerIds)",
    )
    fun deleteContentParts(ownerType: String, ownerIds: List<String>)

    @Query("DELETE FROM agent_responses WHERE turnId IN (:turnIds)")
    fun deleteResponsesForTurns(turnIds: List<String>)

    @Query("DELETE FROM agent_responses WHERE turnId = :turnId AND responseIndex >= :fromResponseIndex")
    fun deleteResponsesFromIndex(turnId: String, fromResponseIndex: Int)

    @Query("DELETE FROM agent_responses WHERE id IN (:responseIds)")
    fun deleteResponses(responseIds: List<String>)

    @Query("DELETE FROM agent_branch_turns WHERE branchId = :branchId AND sequence >= :fromSequence")
    fun deleteBranchTurnsFrom(branchId: String, fromSequence: Int)

    @Query(
        """
        DELETE FROM agent_turns
        WHERE conversationId = :conversationId
            AND NOT EXISTS (
                SELECT 1 FROM agent_branch_turns AS path
                WHERE path.turnId = agent_turns.id
            )
        """,
    )
    fun deleteUnreferencedTurns(conversationId: String)

    @Query(
        """
        DELETE FROM agent_content_parts
        WHERE conversationId = :conversationId AND (
            (ownerType = 'turn' AND ownerId NOT IN (SELECT id FROM agent_turns)) OR
            (ownerType = 'response' AND ownerId NOT IN (SELECT id FROM agent_responses))
        )
        """,
    )
    fun deleteUnreferencedContentParts(conversationId: String)
}

/** Lightweight positional row; large bodies are loaded in bounded chunks after Paging selects IDs. */
data class AgentPagedTurnRef(
    val sequence: Int,
    val turnId: String,
)

data class AgentPublicMessageRef(
    val sequence: Int,
    val turnId: String,
    val turnMessageId: String,
    val turnVariableStateJson: String,
    val responseId: String?,
    val responseIndex: Int?,
    val responseMessageId: String?,
    val responseVariableStateJson: String?,
)
