package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.eleckoi.conversation.PagedConversationTurn
import com.eleckoi.android.engine.agent.eleckoi.conversation.ConversationAttachmentCleanup
import androidx.paging.PagingData
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterMode
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.chat.data.session.ChatSessionImageCoordinator
import com.eleckoi.android.feature.chat.data.session.ChatOpeningCoordinator
import com.eleckoi.android.feature.chat.data.session.ChatSessionHistoryCoordinator
import com.eleckoi.android.feature.chat.data.session.ChatSessionRoomStorage
import com.eleckoi.android.feature.chat.data.session.characterPersonaSnapshot
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatImageStatus
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.settleAbortedGeneration
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.ImmediateCleanupRunner
import com.eleckoi.android.foundation.storage.PersistentCleanupRunner
import com.eleckoi.android.foundation.storage.nowIso
import com.eleckoi.android.foundation.storage.room.ChatListRoomRow
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/** Signals that a remembered session id no longer exists; callers may clear that stale pointer. */
class ChatSessionNotFoundException(sessionId: String) :
    ElecKoiDataException("对话不存在：$sessionId")

/** Product chat repository. Room's normalized ledger is the only owner of message bodies. */
class ChatSessionStore(
    private val database: ElecKoiDatabase,
    private val characters: CharacterRepository,
    private val generationAttempts: GenerationAttemptRepository,
    private val historySaveModeProvider: suspend () -> String = { "all" },
    private val replyImageGenerator: ReplyImageGenerator? = null,
    private val inputImageStore: ChatInputImageStore? = null,
    cleanupRunner: PersistentCleanupRunner = ImmediateCleanupRunner,
    onSessionsDeleted: suspend (List<String>) -> Unit = {},
) {
    private val room = ChatSessionRoomStorage(database)
    private val dao = room.dao
    private val ledger = room.ledger
    private val images = ChatSessionImageCoordinator(database, room, generationAttempts)
    private val opening = ChatOpeningCoordinator(room)
    private val attachmentCleanup = ConversationAttachmentCleanup(
        database, { inputImageStore?.deletePath(it) }, replyImageGenerator,
    )
    private val history = ChatSessionHistoryCoordinator(
        database = database,
        room = room,
        characters = characters,
        historySaveModeProvider = historySaveModeProvider,
        replyImageGenerator = replyImageGenerator,
        inputImageStore = inputImageStore,
        cleanupRunner = cleanupRunner,
        onSessionsDeleted = onSessionsDeleted,
    )

    fun chatList(): List<ChatListItem> = chatListFromRows(dao.chatListRows())

    fun sessionsForCharacter(characterId: String): List<ChatSession> = dao
        .sessionsForCharacter(characterId)
        .map(room::sessionFromEntity)

    fun chatListFlow(): Flow<List<ChatListItem>> = combine(
        dao.chatListRowsFlow(),
        characters.charactersFlow(),
    ) { sessions, _ -> chatListFromRows(sessions) }

    fun chatSessionFlow(sessionId: String): Flow<ChatSession> =
        dao.sessionFlow(sessionId).mapNotNull { entity ->
            entity?.let(room::sessionFromEntity)
                ?.let(::refreshCharacterPersona)
        }

    fun pagingTurns(sessionId: String): Flow<PagingData<PagedConversationTurn>> =
        room.pagingTurns(sessionId)

    /** Complete selected branch used for the next role-chat dialogue projection. */
    fun activeMessages(sessionId: String): List<ChatMessage> = room.activeMessages(sessionId)

    fun latest(
        character: CharacterSlot,
        characterMode: String = CharacterMode.Agent.storageValue,
    ): ChatSession? = dao.latestSession(character.id, characterMode)
        ?.let(room::sessionFromEntity)
        ?.let(::refreshCharacterPersona)

    /**
     * Loads one session. A missing row is reported as [ChatSessionNotFoundException] so callers
     * can distinguish a stale remembered id from a database or decoding failure.
     */
    fun load(sessionId: String, touch: Boolean): ChatSession {
        val entity = room.requireSession(sessionId)
        val original = room.sessionFromEntity(entity)
        val refreshed = refreshCharacterPersona(original)
        val session = if (touch) refreshed.copy(updatedAt = nowIso()) else refreshed
        if (session != original) {
            database.runInTransaction { room.upsertMetadataInTransaction(session) }
        }
        return session
    }

    /**
     * Finalizes checkpoints whose process-local generation owner no longer exists.
     *
     * The caller serializes this against generation lease creation, so a live background turn can
     * never be mistaken for an orphan merely because the conversation was opened again.
     */
    fun settleOrphanedPendingResponses(
        sessionId: String,
        reason: String = "生成已停止",
        completedAtMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val entity = room.requireSession(sessionId)
        room.ensureLedger(entity)
        generationAttempts.interruptOrphans(
            conversationId = sessionId,
            reason = reason,
            nowMillis = completedAtMillis,
        )
        val messages = ledger.allMessages(sessionId).map { it.toChatMessage() }
        val settled = messages.mapIndexedNotNull { index, original ->
            if (original.role != MessageRole.Assistant) return@mapIndexedNotNull null
            var message = original
            if (message.pending) {
                message = message.settleAbortedGeneration(reason, completedAtMillis)
            }
            val recoveredImages = message.imageAttachments.map { image ->
                images.recoverProjection(sessionId, image, reason)
            }
            if (recoveredImages != message.imageAttachments) {
                message = message.copy(imageAttachments = recoveredImages)
            }
            if (message == original) return@mapIndexedNotNull null
            val user = messages.subList(0, index).lastOrNull { it.role == MessageRole.User }
                ?: return@mapIndexedNotNull null
            user.id to message
        }
        if (settled.isEmpty()) return false
        database.runInTransaction {
            settled.forEach { (userMessageId, response) ->
                ledger.upsertResponseInTransaction(
                    conversationId = sessionId,
                    updatedAt = entity.session.updatedAt,
                    turnSourceMessageId = userMessageId,
                    response = response.toLedgerMessage(
                        entity.session.characterId,
                        entity.session.characterName,
                        entity.session.characterAvatar,
                    ),
                )
            }
        }
        return true
    }

    /** Startup repair for every conversation; individual failures cannot block the remaining rows. */
    fun settleAllOrphanedGenerations() {
        dao.sessions().forEach { session ->
            runCatching { settleOrphanedPendingResponses(session.id) }
        }
    }

    fun write(session: ChatSession) {
        database.runInTransaction { room.writeInTransaction(session) }
    }

    /** Normal send path: append exactly one user turn without rewriting the loaded window. */
    fun appendUserTurn(session: ChatSession, message: ChatMessage) {
        database.runInTransaction {
            ledger.upsertTurnInTransaction(
                conversationId = session.id,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
                turn = message.toLedgerMessage(session),
            )
            room.upsertMetadataWithHistoryInTransaction(session, message.content)
        }
    }

    /** Destructive regeneration path: retain the selected user turn and remove everything below. */
    fun truncateForRegeneration(session: ChatSession, retainedMessage: ChatMessage) {
        attachmentCleanup.discardMessages(session.id) {
            ledger.truncateAfterTurnInTransaction(
                conversationId = session.id,
                updatedAt = session.updatedAt,
                retainedTurn = retainedMessage.toLedgerMessage(session),
            )
            room.upsertMetadataWithHistoryInTransaction(session, retainedMessage.content)
        }
    }

    /** Complete/failed/image-refreshed response path: update only the selected turn's one reply. */
    internal fun commitAssistantResponse(
        session: ChatSession,
        userMessageId: String,
        response: ChatMessage,
        terminalAttemptId: String? = null,
        terminalAttemptState: GenerationAttemptState? = null,
        terminalAttemptError: String = "",
    ) {
        database.runInTransaction {
            ledger.upsertResponseInTransaction(
                conversationId = session.id,
                updatedAt = session.updatedAt,
                turnSourceMessageId = userMessageId,
                response = response.toLedgerMessage(session),
            )
            if (terminalAttemptId != null && terminalAttemptState != null) {
                generationAttempts.finishInTransaction(
                    attemptId = terminalAttemptId,
                    state = terminalAttemptState,
                    errorMessage = terminalAttemptError,
                )
            }
            room.upsertMetadataWithHistoryInTransaction(session, response.content)
        }
    }

    internal fun finishGenerationAttempt(
        attemptId: String,
        state: GenerationAttemptState,
        errorMessage: String = "",
    ) {
        database.runInTransaction {
            generationAttempts.finishInTransaction(
                attemptId = attemptId,
                state = state,
                errorMessage = errorMessage,
            )
        }
    }

    /** Installs a new image attempt without replacing any sibling attachment. */
    fun installImageAttempt(
        sessionId: String,
        messageId: String,
        replacement: ChatImageAttachment,
    ): Boolean = images.installAttempt(sessionId, messageId, replacement)

    /** Commits one terminal image only when the attachment still points at this exact attempt. */
    fun settleImageAttempt(
        sessionId: String,
        messageId: String,
        completed: ChatImageAttachment,
    ): Boolean = images.settleAttempt(sessionId, messageId, completed)

    /** Best-effort crash checkpoint. It persists the pending response without publishing UI data. */
    fun checkpointAssistantResponse(session: ChatSession) {
        val response = session.messages.lastOrNull()
            ?.takeIf { it.role == MessageRole.Assistant && it.pending }
            ?: return
        val user = checkpointOwnerUserMessage(session.messages) ?: return
        database.runInTransaction {
            ledger.upsertResponseInTransaction(
                conversationId = session.id,
                updatedAt = session.updatedAt,
                turnSourceMessageId = user.id,
                response = response.toLedgerMessage(session),
                rebuildDisplayCache = false,
            )
        }
    }

    /** Session settings/persona/workspace changes must never rewrite message history. */
    fun updateMetadata(session: ChatSession) {
        database.runInTransaction { room.upsertMetadataInTransaction(session) }
    }

    /** Update one already-persisted message, preserving its stable turn/response identity. */
    fun updateMessage(session: ChatSession, message: ChatMessage) {
        val all = activeMessages(session.id)
        val index = all.indexOfFirst { it.id == message.id }
        if (index < 0) throw ElecKoiDataException("要更新的消息不存在")
        database.runInTransaction {
            if (message.role == MessageRole.Assistant && index > 0) {
                val user = all.subList(0, index).lastOrNull { it.role == MessageRole.User }
                    ?: throw ElecKoiDataException("AI 回复没有对应的用户回合")
                ledger.upsertResponseInTransaction(
                    conversationId = session.id,
                    updatedAt = session.updatedAt,
                    turnSourceMessageId = user.id,
                    response = message.toLedgerMessage(session),
                )
            } else {
                ledger.upsertTurnInTransaction(
                    conversationId = session.id,
                    createdAt = session.createdAt,
                    updatedAt = session.updatedAt,
                    turn = message.toLedgerMessage(session),
                )
            }
            room.upsertMetadataWithHistoryInTransaction(session, message.content)
        }
    }

    fun replaceUnstartedWith(session: ChatSession) {
        opening.replaceUnstartedWith(session)
    }

    fun replaceUnstartedOpening(
        characterId: String,
        characterMode: String,
        content: String,
    ) {
        opening.replaceUnstartedOpening(characterId, characterMode, content)
    }

    fun selectOpening(
        sessionId: String,
        content: String,
        initialVariableStateJson: String,
    ): ChatSession = opening.selectOpening(sessionId, content, initialVariableStateJson)

    fun hasUserMessages(sessionId: String): Boolean =
        opening.hasUserMessages(sessionId)

    suspend fun delete(sessionId: String) = history.delete(sessionId)

    suspend fun deleteForCharacters(characterIds: List<String>): List<String> =
        history.deleteForCharacters(characterIds)

    suspend fun deleteExceptCharacters(characterIds: List<String>) =
        history.deleteExceptCharacters(characterIds)

    suspend fun resumeDelete(sessionId: String) = history.resumeDelete(sessionId)

    fun exportHistory(characterId: String, sessionIds: List<String>): String =
        history.export(characterId, sessionIds)

    suspend fun importHistory(characterId: String, json: String): Int =
        history.import(characterId, json)

    /** Complete per-character history snapshots used by the app-level backup package. */
    fun exportBackupHistories(): Map<String, String> = buildMap {
        characters.loadCharacters().items.forEach { character ->
            val sessionIds = dao.sessionsForCharacter(character.id).map { it.session.id }
            if (sessionIds.isNotEmpty()) {
                put(character.id, history.export(character.id, sessionIds))
            }
        }
    }

    suspend fun restoreBackupHistories(histories: Map<String, String>): Int {
        var imported = 0
        histories.forEach { (characterId, json) ->
            imported += history.import(characterId, json)
        }
        return imported
    }

    /**
     * Streaming app backups keep each conversation in its own ZIP entry. This bounds memory to one
     * conversation instead of materializing every character history at the same time.
     */
    fun backupHistoryTargets(): List<ChatBackupTarget> =
        characters.loadCharacters().items.flatMap { character ->
            dao.sessionsForCharacter(character.id).map { session ->
                ChatBackupTarget(characterId = character.id, sessionId = session.session.id)
            }
        }

    fun exportBackupHistory(target: ChatBackupTarget): String =
        history.export(target.characterId, listOf(target.sessionId))

    suspend fun restoreBackupHistory(characterId: String, json: String): Int =
        history.import(characterId, json)

    fun saveModelSelection(sessionId: String, selection: ChatModelSelection): ChatSession {
        val session = load(sessionId, touch = false)
        val capability = selection.capability.trim().ifBlank { "chat" }
        val updated = session.copy(
            modelSettings = session.modelSettings + (
                capability to selection.copy(capability = capability)
            ),
            updatedAt = nowIso(),
        )
        updateMetadata(updated)
        return updated
    }

    suspend fun applyHistorySavePolicy(characterId: String) =
        history.applySavePolicy(characterId)

    fun personaSnapshot(character: CharacterSlot): CharacterCard =
        characterPersonaSnapshot(character)

    fun personaSnapshot(character: CharacterSlot, characterMode: String): CharacterCard =
        characterPersonaSnapshot(character, characterMode)

    private fun chatListFromRows(rows: List<ChatListRoomRow>): List<ChatListItem> {
        val characterById = characters.loadCharacters().items.associateBy { it.id }
        return rows.map { row ->
            val character = characterById[row.session.characterId]
            row.toChatListItem(
                character = character,
                snapshot = character?.let(::personaSnapshot),
            )
        }.sortedByDescending(ChatListItem::updatedAt)
    }

    private fun refreshCharacterPersona(session: ChatSession): ChatSession {
        val character = characters.characterById(session.characterId) ?: return session
        val snapshot = personaSnapshot(character)
        val mode = CharacterMode.fromStorage(session.characterMode).storageValue
        val persona = personaSnapshot(character, mode)
        if (
            session.characterName == snapshot.assistantName &&
            session.characterAvatar == snapshot.assistantAvatar &&
            session.characterMode == mode &&
            session.characterPersona == persona
        ) {
            return session
        }
        return session.copy(
            characterName = snapshot.assistantName,
            characterAvatar = snapshot.assistantAvatar,
            characterPersona = persona,
            characterMode = mode,
        )
    }

}

data class ChatBackupTarget(
    val characterId: String,
    val sessionId: String,
)

/**
 * Maps one persisted session to the message-home projection.
 *
 * A session keeps the character name/avatar that were current when it was created. That snapshot
 * is the safe fallback when the live character table no longer contains the referenced ID (for
 * example after importing the same card again and receiving a new local ID). The conversation is
 * still valid and must remain openable by its own session ID.
 */
internal fun ChatListRoomRow.toChatListItem(
    character: CharacterSlot?,
    snapshot: CharacterCard?,
): ChatListItem {
    val entity = session
    val displayName = snapshot?.assistantName.orEmpty().ifBlank {
        character?.name.orEmpty().ifBlank { entity.characterName }
    }
    val displayAvatar = snapshot?.assistantAvatar.orEmpty().ifBlank {
        character?.avatar.orEmpty().ifBlank { entity.characterAvatar }
    }
    return ChatListItem(
        id = entity.id,
        title = entity.title.ifBlank { displayName.ifBlank { "新对话" } },
        characterId = entity.characterId,
        characterMode = entity.characterMode,
        characterName = displayName,
        characterAvatar = displayAvatar,
        summary = summary.take(42),
        updatedAt = entity.updatedAt,
        messageCount = messageCount,
    )
}
