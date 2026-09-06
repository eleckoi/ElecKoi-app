package com.eleckoi.android.feature.chat.data.session

import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.chat.data.ChatInputImageStore
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.PersistentCleanupRunner

/** One boundary for history transfer, retention policy, and history-owned media cleanup. */
internal class ChatSessionHistoryCoordinator(
    database: ElecKoiDatabase,
    room: ChatSessionRoomStorage,
    characters: CharacterRepository,
    historySaveModeProvider: suspend () -> String,
    replyImageGenerator: ReplyImageGenerator?,
    inputImageStore: ChatInputImageStore?,
    cleanupRunner: PersistentCleanupRunner,
    onSessionsDeleted: suspend (List<String>) -> Unit,
) {
    private val cleanup = ChatSessionCleanupCoordinator(
        database = database,
        room = room,
        historySaveModeProvider = historySaveModeProvider,
        replyImageGenerator = replyImageGenerator,
        inputImageStore = inputImageStore,
        cleanupRunner = cleanupRunner,
        onSessionsDeleted = onSessionsDeleted,
    )
    private val transfer = ChatHistoryTransferCoordinator(room, characters, cleanup)

    suspend fun delete(sessionId: String) = cleanup.delete(sessionId)

    suspend fun deleteForCharacters(characterIds: List<String>): List<String> {
        return cleanup.deleteForCharacters(characterIds)
    }

    suspend fun deleteExceptCharacters(characterIds: List<String>) {
        cleanup.deleteExceptCharacters(characterIds)
    }

    suspend fun resumeDelete(sessionId: String) = cleanup.deleteNow(sessionId)

    fun export(characterId: String, sessionIds: List<String>): String {
        return transfer.export(characterId, sessionIds)
    }

    suspend fun import(characterId: String, json: String): Int {
        return transfer.import(characterId, json)
    }

    suspend fun applySavePolicy(characterId: String) {
        cleanup.applyHistorySavePolicy(characterId)
    }
}
