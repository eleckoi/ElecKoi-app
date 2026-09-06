package com.eleckoi.android.feature.chat.data.session

import androidx.paging.PagingData
import com.eleckoi.android.engine.agent.eleckoi.conversation.PagedConversationTurn
import com.eleckoi.android.engine.agent.eleckoi.conversation.RoomConversationLedger
import com.eleckoi.android.feature.chat.data.chatSessionFromRoom
import com.eleckoi.android.feature.chat.data.ChatSessionNotFoundException
import com.eleckoi.android.feature.chat.data.toChatMessage
import com.eleckoi.android.feature.chat.data.toRoomRecord
import com.eleckoi.android.feature.chat.data.toLedgerMessage
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.foundation.storage.room.ChatSessionRecord
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import kotlinx.coroutines.flow.Flow

/** The single Room/ledger boundary for persisted raw conversation messages. */
internal class ChatSessionRoomStorage(private val database: ElecKoiDatabase) {
    val dao = database.chatDao()
    val ledger = RoomConversationLedger(database)

    fun pagingTurns(sessionId: String): Flow<PagingData<PagedConversationTurn>> =
        ledger.pagingTurns(sessionId)

    fun databaseTransaction(block: () -> Unit) {
        database.runInTransaction { block() }
    }

    fun activeMessages(sessionId: String): List<ChatMessage> {
        ensureLedger(requireSession(sessionId))
        return ledger.allMessages(sessionId).map { it.toChatMessage() }
    }

    fun writeInTransaction(session: ChatSession) {
        check(database.inTransaction())
        val previous = dao.sessionById(session.id)
        val preliminary = session.toRoomRecord()
        upsertChangedRecord(preliminary, previous)
        ledger.replaceActiveTimelineForImportInTransaction(
            conversationId = session.id,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            messages = session.messages.map { it.toLedgerMessage(session) },
        )
        val summary = session.messages.asReversed()
            .firstOrNull { it.content.isNotBlank() }
            ?.content
            ?.take(42)
            ?: previous?.session?.historySummary.orEmpty()
        val updated = preliminary.copy(
            session = preliminary.session.copy(
                historySummary = summary,
                historyMessageCount = ledger.activeMessageCount(session.id),
                historyUserMessageCount = ledger.activeUserMessageCount(session.id),
            ),
        )
        if (updated.session != preliminary.session) dao.upsertSession(updated.session)
    }

    fun upsertMetadataInTransaction(session: ChatSession) {
        check(database.inTransaction())
        val previous = dao.sessionById(session.id)
        val updated = session.toRoomRecord().let { record ->
            record.copy(session = record.session.copy(
                historySummary = previous?.session?.historySummary.orEmpty(),
                historyMessageCount = previous?.session?.historyMessageCount ?: 0,
                historyUserMessageCount = previous?.session?.historyUserMessageCount ?: 0,
            ))
        }
        upsertChangedRecord(updated, previous)
    }

    /** Persist only changed session components plus the small ledger-derived metadata row. */
    fun upsertMetadataWithHistoryInTransaction(session: ChatSession, summaryCandidate: String) {
        check(database.inTransaction())
        val current = dao.sessionById(session.id)
        val updated = session.toRoomRecord().let { record ->
            record.copy(session = record.session.copy(
                historySummary = summaryCandidate.takeIf(String::isNotBlank)
                    ?.take(42)
                    ?: current?.session?.historySummary.orEmpty(),
                historyMessageCount = ledger.activeMessageCount(session.id),
                historyUserMessageCount = ledger.activeUserMessageCount(session.id),
            ))
        }
        upsertChangedRecord(updated, current)
    }

    fun sessionFromEntity(
        entity: ChatSessionRecord,
        includeAllMessages: Boolean = false,
    ): ChatSession {
        ensureLedger(entity)
        if (includeAllMessages) {
            return chatSessionFromRoom(
                record = entity,
                messages = ledger.allMessages(entity.session.id).map { it.toChatMessage() },
            )
        }
        ledger.displayCache(entity.session.id)?.let { cached ->
            return chatSessionFromRoom(
                record = entity,
                messages = cached.map { it.toChatMessage() },
            )
        }
        val page = ledger.page(entity.session.id, beforeSequence = null, limit = DisplayPageTurns)
        return chatSessionFromRoom(
            record = entity,
            messages = page.messages.map { it.toChatMessage() },
        )
    }

    fun ensureLedger(entity: ChatSessionRecord) {
        if (ledger.containsConversation(entity.session.id)) return
        database.runInTransaction {
            ledger.ensureConversationInTransaction(
                conversationId = entity.session.id,
                createdAt = entity.session.createdAt,
                updatedAt = entity.session.updatedAt,
                initialMessages = emptyList(),
            )
        }
    }

    fun requireSession(sessionId: String): ChatSessionRecord =
        dao.sessionById(sessionId) ?: throw ChatSessionNotFoundException(sessionId)

    private fun upsertChangedRecord(next: ChatSessionRecord, current: ChatSessionRecord?) {
        if (next.session != current?.session) dao.upsertSession(next.session)
        if (next.characterSnapshot != current?.characterSnapshot) {
            next.characterSnapshot?.let(dao::upsertCharacterSnapshot)
        }
        if (next.modelSettings != current?.modelSettings) {
            next.modelSettings?.let(dao::upsertModelSettings)
        }
        val currentVariableStates = current?.variableStates.orEmpty().associateBy { it.kind }
        val changedVariableStates = next.variableStates.filter { it != currentVariableStates[it.kind] }
        if (changedVariableStates.isNotEmpty()) dao.upsertVariableStates(changedVariableStates)
    }

    private companion object {
        const val DisplayPageTurns = 30
    }
}
