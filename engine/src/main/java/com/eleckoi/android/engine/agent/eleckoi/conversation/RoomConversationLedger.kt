package com.eleckoi.android.engine.agent.eleckoi.conversation

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.agent.dao.AgentLedgerDao
import com.eleckoi.android.foundation.storage.room.agent.dao.AgentPagedTurnRef
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentBranchEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentBranchTurnEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentContentPartEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentConversationEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentConversationDisplayCacheEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentResponseEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentTurnEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.ConversationSpeakerEntity
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn


/**
 * Owns the active product timeline in Room.
 *
 * Callers mutate it inside the same Room transaction that updates chat_sessions. Rust runtime
 * threads never participate in these transactions.
 */
class RoomConversationLedger(
    private val database: ElecKoiDatabase,
    private val dao: AgentLedgerDao = database.agentLedgerDao(),
) {
    fun ensureConversationInTransaction(
        conversationId: String,
        createdAt: String,
        updatedAt: String,
        initialMessages: List<LedgerMessage>,
        surface: String = SurfaceRole,
    ) {
        requireTransaction()
        if (dao.conversation(conversationId) != null) return
        val branchId = stableLedgerId("branch", conversationId, "main")
        dao.upsertConversation(
            AgentConversationEntity(
                id = conversationId,
                surface = surface,
                activeBranchId = branchId,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
        )
        dao.upsertBranch(
            AgentBranchEntity(
                id = branchId,
                conversationId = conversationId,
                parentBranchId = null,
                forkedFromTurnId = null,
                headSequence = -1,
                name = "主分支",
                reason = "conversation_created",
                createdAt = createdAt,
            ),
        )
        replaceTimelineInTransaction(
            conversationId = conversationId,
            updatedAt = updatedAt,
            messages = initialMessages,
            replaceAll = true,
        )
    }

    /** Full replacement reserved for import/bootstrap; normal generation uses incremental APIs. */
    fun replaceActiveTimelineForImportInTransaction(
        conversationId: String,
        createdAt: String,
        updatedAt: String,
        messages: List<LedgerMessage>,
        surface: String = SurfaceRole,
    ) {
        requireTransaction()
        ensureConversationInTransaction(
            conversationId,
            createdAt,
            updatedAt,
            emptyList(),
            surface,
        )
        replaceTimelineInTransaction(
            conversationId = conversationId,
            updatedAt = updatedAt,
            messages = messages,
            replaceAll = false,
        )
    }

    /** Append a new stable turn, or update that exact turn without touching its neighbours. */
    fun upsertTurnInTransaction(
        conversationId: String,
        createdAt: String,
        updatedAt: String,
        turn: LedgerMessage,
        response: LedgerMessage? = null,
        clearResponseWhenMissing: Boolean = false,
        surface: String = SurfaceRole,
        rebuildDisplayCache: Boolean = true,
    ) {
        requireTransaction()
        ensureConversationInTransaction(
            conversationId = conversationId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            initialMessages = emptyList(),
            surface = surface,
        )
        val conversation = requireNotNull(dao.conversation(conversationId))
        val existing = dao.turnBySourceMessageId(conversationId, turn.id)
        val entry = ledgerEntries(
            conversationId = conversationId,
            messages = listOfNotNull(turn, response),
            existingTurnId = { existing?.id },
        ).single()
        val currentRef = dao.branchTurn(conversation.activeBranchId, entry.turn.id)
        persistEntry(entry, clearResponseWhenMissing)
        if (currentRef == null) {
            val nextSequence = (dao.lastBranchTurn(conversation.activeBranchId)?.sequence ?: -1) + 1
            dao.upsertBranchTurns(
                listOf(
                    AgentBranchTurnEntity(
                        branchId = conversation.activeBranchId,
                        sequence = nextSequence,
                        turnId = entry.turn.id,
                    ),
                ),
            )
            dao.updateBranchHead(conversation.activeBranchId, nextSequence)
        }
        finishMutation(conversationId, updatedAt, rebuildDisplayCache)
    }

    /** Update the unique response belonging to an existing user turn. */
    fun upsertResponseInTransaction(
        conversationId: String,
        updatedAt: String,
        turnSourceMessageId: String,
        response: LedgerMessage,
        rebuildDisplayCache: Boolean = true,
    ) {
        requireTransaction()
        val conversation = requireNotNull(dao.conversation(conversationId))
        val turn = requireNotNull(dao.turnBySourceMessageId(conversationId, turnSourceMessageId)) {
            "找不到回复所属的用户回合：$turnSourceMessageId"
        }
        check(dao.branchTurn(conversation.activeBranchId, turn.id) != null) {
            "回复所属回合不在当前分支：$turnSourceMessageId"
        }
        val speaker = response.toSpeakerEntity(conversationId)
        val responseEntity = response.toResponseEntity(
            conversationId = conversationId,
            turnId = turn.id,
            speakerInternalId = speaker.id,
        )
        if (dao.speakers(listOf(speaker.id)).singleOrNull() != speaker) {
            dao.upsertSpeakers(listOf(speaker))
        }
        if (dao.responsesForTurns(listOf(turn.id)).singleOrNull() != responseEntity) {
            dao.upsertResponses(listOf(responseEntity))
        }
        replaceContentParts(
            ownerType = OwnerResponse,
            ownerId = responseEntity.id,
            incoming = response.toResponseParts(conversationId, responseEntity.id),
        )
        finishMutation(conversationId, updatedAt, rebuildDisplayCache)
    }

    /**
     * Destructive regeneration: keep and optionally edit the selected user turn, remove its old
     * response, and delete every later turn in one transaction.
     */
    fun truncateAfterTurnInTransaction(
        conversationId: String,
        updatedAt: String,
        retainedTurn: LedgerMessage,
    ) {
        requireTransaction()
        val conversation = requireNotNull(dao.conversation(conversationId))
        val existing = requireNotNull(
            dao.turnBySourceMessageId(conversationId, retainedTurn.id),
        ) { "找不到要重新生成的用户回合：${retainedTurn.id}" }
        val ref = requireNotNull(dao.branchTurn(conversation.activeBranchId, existing.id)) {
            "要重新生成的回合不在当前分支：${retainedTurn.id}"
        }
        val updatedEntry = ledgerEntries(
            conversationId = conversationId,
            messages = listOf(retainedTurn),
            existingTurnId = { existing.id },
        ).single()
        persistEntry(updatedEntry, clearResponseWhenMissing = true)
        dao.deleteBranchTurnsFrom(conversation.activeBranchId, ref.sequence + 1)
        dao.updateBranchHead(conversation.activeBranchId, ref.sequence)
        dao.deleteUnreferencedTurns(conversationId)
        dao.deleteUnreferencedContentParts(conversationId)
        finishMutation(conversationId, updatedAt)
    }

    fun page(
        conversationId: String,
        beforeSequence: Int?,
        limit: Int,
    ): LedgerPage {
        if (conversationId.isBlank() || limit <= 0) return LedgerPage(emptyList(), null, false)
        val conversation = dao.conversation(conversationId)
            ?: return LedgerPage(emptyList(), null, false)
        val cursor = beforeSequence ?: Int.MAX_VALUE
        val refs = dao.pageBefore(conversation.activeBranchId, cursor, limit)
            .sortedBy(AgentBranchTurnEntity::sequence)
        if (refs.isEmpty()) return LedgerPage(emptyList(), null, false)
        val firstSequence = refs.first().sequence
        return LedgerPage(
            messages = materialize(refs),
            beforeSequence = firstSequence,
            hasMore = dao.hasBefore(conversation.activeBranchId, firstSequence),
        )
    }

    fun allMessages(conversationId: String): List<LedgerMessage> {
        val conversation = dao.conversation(conversationId) ?: return emptyList()
        return materialize(dao.branchTurns(conversation.activeBranchId))
    }

    fun activeMessageCount(conversationId: String): Int {
        val conversation = dao.conversation(conversationId) ?: return 0
        return dao.branchMessageCount(conversation.activeBranchId)
    }

    fun containsConversation(conversationId: String): Boolean =
        conversationId.isNotBlank() && dao.conversation(conversationId) != null

    fun hasActivePendingResponse(conversationId: String): Boolean =
        conversationId.isNotBlank() && dao.hasActiveResponseWithStatus(conversationId, StatusPending)

    /** One-query first-frame projection stored as bounded rows. A mismatch discards it as stale. */
    fun displayCache(conversationId: String): List<LedgerMessage>? {
        val conversation = dao.conversation(conversationId) ?: return null
        val chunks = runCatching { dao.displayCache(conversationId) }.getOrNull()
            ?.takeIf(List<AgentConversationDisplayCacheEntity>::isNotEmpty)
            ?: return null
        val valid = chunks.size <= MaxDisplayCacheChunks && chunks.withIndex().all { (index, chunk) ->
            chunk.chunkIndex == index &&
                chunk.ledgerRevision == conversation.revision &&
                chunk.rendererVersion == DisplayCacheRendererVersion
        }
        if (!valid) return null
        return decodeDisplayCacheChunks(chunks.map(AgentConversationDisplayCacheEntity::payloadJson))
    }

    /**
     * One Room-backed Paging source for every product surface. The initial key opens at the tail;
     * scrolling upward requests older pages and every ledger mutation invalidates the full source.
     */
    fun pagingTurns(conversationId: String): Flow<PagingData<PagedConversationTurn>> = flow {
        val turnCount = dao.activeTurnCount(conversationId)
        val initialOffset = (turnCount - InitialPagingTurns).coerceAtLeast(0)
        emitAll(
            Pager(
                config = PagingConfig(
                    pageSize = PagingTurnsPerLoad,
                    initialLoadSize = InitialPagingTurns,
                    prefetchDistance = PagingPrefetchTurns,
                    enablePlaceholders = false,
                ),
                initialKey = initialOffset,
                pagingSourceFactory = {
                    MaterializingTurnPagingSource(
                        delegate = dao.pagingTurnRefs(conversationId),
                        materialize = { refs ->
                            withContext(Dispatchers.IO) { materializePagedTurns(refs) }
                        },
                    )
                },
            ).flow,
        )
    }.flowOn(Dispatchers.IO)

    fun activeUserMessageCount(conversationId: String): Int {
        val conversation = dao.conversation(conversationId) ?: return 0
        return dao.branchUserMessageCount(conversation.activeBranchId)
    }

    fun deleteConversationInTransaction(conversationId: String) {
        requireTransaction()
        dao.deleteConversation(conversationId)
    }

    private fun replaceTimelineInTransaction(
        conversationId: String,
        updatedAt: String,
        messages: List<LedgerMessage>,
        replaceAll: Boolean,
    ) {
        requireTransaction()
        val conversation = requireNotNull(dao.conversation(conversationId))
        val branchId = conversation.activeBranchId
        val current = dao.branchTurns(branchId)
        val incoming = ledgerEntries(
            conversationId = conversationId,
            messages = messages,
            existingTurnId = { message ->
                dao.turnBySourceMessageId(conversationId, message.id)?.id
            },
        )

        val (fromSequence, incomingStart) = when {
            replaceAll || current.isEmpty() || incoming.isEmpty() -> 0 to 0
            incoming.size == 1 && incoming.single().turn.kind == KindOpening -> 0 to 0
            else -> {
                val existingSequenceByTurn = current.associate { it.turnId to it.sequence }
                val matched = incoming.withIndex().firstOrNull { (_, entry) ->
                    entry.turn.kind != KindOpening && entry.turn.id in existingSequenceByTurn
                }
                when {
                    matched != null -> existingSequenceByTurn.getValue(matched.value.turn.id) to matched.index
                    incoming.firstOrNull()?.turn?.kind == KindOpening &&
                        current.firstOrNull()?.let { ref ->
                            dao.turns(listOf(ref.turnId)).firstOrNull()?.kind == KindOpening
                        } == true -> 1 to 1
                    else -> (current.last().sequence + 1) to 0
                }
            }
        }

        dao.deleteBranchTurnsFrom(branchId, fromSequence)
        val selected = incoming.drop(incomingStart)
        persistEntries(selected)
        dao.upsertBranchTurns(
            selected.mapIndexed { index, entry ->
                AgentBranchTurnEntity(
                    branchId = branchId,
                    sequence = fromSequence + index,
                    turnId = entry.turn.id,
                )
            },
        )
        val headSequence = if (selected.isEmpty()) {
            (fromSequence - 1).coerceAtLeast(-1)
        } else {
            fromSequence + selected.lastIndex
        }
        dao.updateBranchHead(branchId, headSequence)
        dao.deleteUnreferencedTurns(conversationId)
        dao.deleteUnreferencedContentParts(conversationId)
        finishMutation(conversationId, updatedAt)
    }

    private fun persistEntries(entries: List<LedgerEntry>) {
        if (entries.isEmpty()) return
        dao.upsertSpeakers(entries.flatMap(LedgerEntry::speakers).distinctBy(ConversationSpeakerEntity::id))
        dao.upsertTurns(entries.map(LedgerEntry::turn))
        val turnIds = entries.map { it.turn.id }.distinct()
        val oldResponseIds = dao.responsesForTurns(turnIds).map(AgentResponseEntity::id)
        if (oldResponseIds.isNotEmpty()) dao.deleteContentParts(OwnerResponse, oldResponseIds)
        dao.deleteResponsesForTurns(turnIds)
        val responses = entries.flatMap(LedgerEntry::responses)
        if (responses.isNotEmpty()) dao.upsertResponses(responses)

        dao.deleteContentParts(OwnerTurn, turnIds)
        val responseIds = responses.map { it.id }.distinct()
        if (responseIds.isNotEmpty()) dao.deleteContentParts(OwnerResponse, responseIds)
        val parts = entries.flatMap(LedgerEntry::parts)
        if (parts.isNotEmpty()) dao.upsertContentParts(parts.toStorageChunks())
    }

    private fun persistEntry(entry: LedgerEntry, clearResponseWhenMissing: Boolean) {
        val speakers = entry.speakers.distinctBy(ConversationSpeakerEntity::id)
        if (speakers.isNotEmpty()) {
            val currentSpeakers = dao.speakers(speakers.map(ConversationSpeakerEntity::id))
                .associateBy(ConversationSpeakerEntity::id)
            val changedSpeakers = speakers.filter { it != currentSpeakers[it.id] }
            if (changedSpeakers.isNotEmpty()) dao.upsertSpeakers(changedSpeakers)
        }
        if (dao.turns(listOf(entry.turn.id)).singleOrNull() != entry.turn) {
            dao.upsertTurns(listOf(entry.turn))
        }
        if (entry.responses.isNotEmpty() || clearResponseWhenMissing) {
            val currentResponses = dao.responsesForTurns(listOf(entry.turn.id))
            val currentById = currentResponses.associateBy(AgentResponseEntity::id)
            val incomingIds = entry.responses.mapTo(mutableSetOf(), AgentResponseEntity::id)
            val obsolete = currentResponses.filter { it.id !in incomingIds }
            if (obsolete.isNotEmpty()) {
                dao.deleteContentParts(OwnerResponse, obsolete.map(AgentResponseEntity::id))
                dao.deleteResponses(obsolete.map(AgentResponseEntity::id))
            }
            val changedResponses = entry.responses.filter { it != currentById[it.id] }
            if (changedResponses.isNotEmpty()) dao.upsertResponses(changedResponses)
            entry.responses.forEach { response ->
                replaceContentParts(
                    ownerType = OwnerResponse,
                    ownerId = response.id,
                    incoming = entry.parts.filter { part ->
                        part.ownerType == OwnerResponse && part.ownerId == response.id
                    },
                )
            }
        }
        replaceContentParts(
            ownerType = OwnerTurn,
            ownerId = entry.turn.id,
            incoming = entry.parts.filter { part ->
                part.ownerType == OwnerTurn && part.ownerId == entry.turn.id
            },
        )
    }

    private fun replaceContentParts(
        ownerType: String,
        ownerId: String,
        incoming: List<AgentContentPartEntity>,
    ) {
        val plan = contentPartWritePlan(
            current = dao.contentParts(ownerType, listOf(ownerId)),
            incoming = incoming.toStorageChunks(),
        )
        if (plan.deletes.isNotEmpty()) dao.deleteContentPartRows(plan.deletes)
        if (plan.upserts.isNotEmpty()) dao.upsertContentParts(plan.upserts)
    }

    private fun finishMutation(
        conversationId: String,
        updatedAt: String,
        rebuildDisplayCache: Boolean = true,
    ) {
        val plan = ledgerMutationPublicationPlan(rebuildDisplayCache)
        if (plan.advanceConversationRevision) {
            dao.advanceConversationRevision(conversationId, updatedAt)
        }
        if (plan.rebuildDisplayCache) {
            rebuildDisplayCacheInTransaction(conversationId, updatedAt)
        } else if (plan.clearDisplayCache) {
            // A crash-recovery checkpoint must be visible after process restart, but advancing the
            // conversation row would invalidate the active PagingSource every 750 ms. Clearing the
            // disposable first-frame cache preserves recovery without publishing a UI mutation.
            dao.deleteDisplayCache(conversationId)
        }
    }

    private fun rebuildDisplayCacheInTransaction(conversationId: String, updatedAt: String) {
        val conversation = dao.conversation(conversationId) ?: return
        val refs = dao.pageBefore(
            branchId = conversation.activeBranchId,
            beforeSequence = Int.MAX_VALUE,
            limit = DisplayCacheTurnLimit,
        ).sortedBy(AgentBranchTurnEntity::sequence)
        val chunks = encodeDisplayCacheChunks(materialize(refs))
        dao.deleteDisplayCache(conversationId)
        if (chunks.size > MaxDisplayCacheChunks) return
        dao.upsertDisplayCache(
            chunks.mapIndexed { index, payload ->
                AgentConversationDisplayCacheEntity(
                    conversationId = conversationId,
                    chunkIndex = index,
                    ledgerRevision = conversation.revision,
                    payloadJson = payload,
                    rendererVersion = DisplayCacheRendererVersion,
                    updatedAt = updatedAt,
                )
            },
        )
    }

    private fun materialize(refs: List<AgentBranchTurnEntity>): List<LedgerMessage> {
        return materializeTurns(
            refs.map { ref -> LedgerTurnRef(ref.sequence, ref.turnId) },
        ).flatMap(PagedConversationTurn::messages)
    }

    private fun materializePagedTurns(refs: List<AgentPagedTurnRef>): List<PagedConversationTurn> {
        return materializeTurns(
            refs.map { ref -> LedgerTurnRef(ref.sequence, ref.turnId) },
        )
    }

    private fun materializeTurns(refs: List<LedgerTurnRef>): List<PagedConversationTurn> {
        if (refs.isEmpty()) return emptyList()
        val turnIds = refs.map(LedgerTurnRef::turnId).distinct()
        val turns = dao.turns(turnIds).associateBy(AgentTurnEntity::id)
        val responses = dao.responsesForTurns(turnIds)
            .groupBy(AgentResponseEntity::turnId)
            .mapValues { (_, rows) -> rows.sortedBy(AgentResponseEntity::responseIndex) }
        val turnParts = dao.contentParts(OwnerTurn, turnIds)
            .mergeStorageChunks()
            .groupBy(AgentContentPartEntity::ownerId)
        val responseIds = responses.values.flatten().map(AgentResponseEntity::id)
        val responseParts = if (responseIds.isEmpty()) {
            emptyMap()
        } else {
            dao.contentParts(OwnerResponse, responseIds)
                .mergeStorageChunks()
                .groupBy(AgentContentPartEntity::ownerId)
        }
        val speakerIds = buildSet {
            addAll(turns.values.map(AgentTurnEntity::speakerId))
            addAll(responses.values.flatten().map(AgentResponseEntity::speakerId))
        }
        val speakers = if (speakerIds.isEmpty()) emptyMap() else {
            dao.speakers(speakerIds.toList()).associateBy(ConversationSpeakerEntity::id)
        }
        return buildList {
            refs.forEach { ref ->
                val turn = turns[ref.turnId] ?: return@forEach
                val turnResponses = responses[turn.id].orEmpty()
                add(
                    PagedConversationTurn(
                        stableTurnId = turn.id,
                        sequence = ref.sequence,
                        messages = buildList {
                            add(turn.toLedgerMessage(turnParts[turn.id].orEmpty(), speakers[turn.speakerId]))
                            turnResponses.forEach { response ->
                                add(response.toLedgerMessage(
                                    responseParts[response.id].orEmpty(),
                                    speakers[response.speakerId],
                                ))
                            }
                        },
                    ),
                )
            }
        }
    }

    private fun requireTransaction() {
        check(database.inTransaction()) { "Room 聊天主账本必须与会话元数据在同一事务中写入" }
    }
}
