package com.eleckoi.android.feature.chat.data.session

import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.CharacterMode
import com.eleckoi.android.feature.chat.data.chatHistoryJsonString
import com.eleckoi.android.feature.chat.data.chatSessionsFromHistoryJson
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.nowIso

internal class ChatHistoryTransferCoordinator(
    private val room: ChatSessionRoomStorage,
    private val characters: CharacterRepository,
    private val cleanup: ChatSessionCleanupCoordinator,
) {
    fun export(characterId: String, sessionIds: List<String>): String {
        val character = characters.characterById(characterId)
            ?: throw ElecKoiDataException("角色不存在")
        val idSet = sessionIds.filter(String::isNotBlank).toSet()
        if (idSet.isEmpty()) throw ElecKoiDataException("没有可导出的聊天记录")
        val exported = room.dao.sessionsForCharacter(character.id)
            .filter { it.session.id in idSet }
            .map { room.sessionFromEntity(it, includeAllMessages = true) }
            .sortedByDescending(ChatSession::updatedAt)
        if (exported.isEmpty()) throw ElecKoiDataException("没有找到要导出的聊天记录")
        return chatHistoryJsonString(
            exportedAt = nowIso(),
            characterId = character.id,
            characterName = character.name,
            sessions = exported,
        )
    }

    suspend fun import(characterId: String, json: String): Int {
        val character = characters.characterById(characterId)
            ?: throw ElecKoiDataException("角色不存在")
        val sourceSessions = runCatching { chatSessionsFromHistoryJson(json) }
            .getOrElse { throw ElecKoiDataException("聊天记录文件格式不正确", it) }
        var imported = 0
        sourceSessions.forEach { chat ->
            val id = uniqueSessionId(chat.id)
            val now = nowIso()
            val mode = CharacterMode.fromStorage(chat.characterMode).storageValue
            val snapshot = characterPersonaSnapshot(character)
            val normalized = chat.copy(
                id = id,
                workspaceId = "",
                title = chat.title.ifBlank { snapshot.assistantName.ifBlank { character.name } },
                characterId = character.id,
                characterName = snapshot.assistantName.ifBlank { character.name },
                characterAvatar = snapshot.assistantAvatar.ifBlank { character.avatar },
                characterPersona = characterPersonaSnapshot(character, mode),
                characterMode = mode,
                createdAt = chat.createdAt.ifBlank { now },
                updatedAt = chat.updatedAt.ifBlank { now },
            )
            room.databaseTransaction { room.writeInTransaction(normalized) }
            imported += 1
        }
        if (imported == 0) throw ElecKoiDataException("没有可导入的聊天记录")
        cleanup.applyHistorySavePolicy(character.id)
        return imported
    }

    private fun uniqueSessionId(sourceId: String): String {
        var candidate = sourceId.ifBlank { newId(12) }
        while (room.dao.sessionCount(candidate) > 0) candidate = newId(12)
        return candidate
    }
}
