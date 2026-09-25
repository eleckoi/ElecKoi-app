package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.foundation.storage.room.ChatSessionEntity
import com.eleckoi.android.foundation.storage.room.ChatSessionCharacterSnapshotEntity
import com.eleckoi.android.foundation.storage.room.ChatSessionRecord
import com.eleckoi.android.foundation.storage.room.ChatSessionVariableStateEntity

internal fun ChatSession.toRoomRecord(): ChatSessionRecord = ChatSessionRecord(
    session = ChatSessionEntity(
        id = id,
        workspaceId = workspaceId,
        title = title,
        characterId = characterId,
        characterName = characterName,
        characterAvatar = characterAvatar,
        permissionMode = permissionMode.name,
        historySummary = messages.asReversed().firstOrNull { it.content.isNotBlank() }
            ?.content.orEmpty().take(42),
        historyMessageCount = messages.size,
        historyUserMessageCount = messages.count { it.role == MessageRole.User },
        createdAt = createdAt,
        updatedAt = updatedAt,
    ),
    characterSnapshot = ChatSessionCharacterSnapshotEntity(
        sessionId = id,
        personaJson = characterPersonaJsonString(characterPersona),
    ),
    variableStates = listOf(
        ChatSessionVariableStateEntity(id, ChatVariableStateInitial, initialVariableStateJson),
        ChatSessionVariableStateEntity(id, ChatVariableStateCurrent, variableStateJson),
    ),
)

internal fun chatSessionFromRoom(
    record: ChatSessionRecord,
    messages: List<ChatMessage>,
): ChatSession {
    val session = record.session
    return ChatSession(
    id = session.id,
    workspaceId = session.workspaceId,
    title = session.title.ifBlank { session.characterName.ifBlank { "新对话" } },
    characterId = session.characterId,
    characterName = session.characterName,
    characterAvatar = session.characterAvatar,
    characterPersona = characterPersonaFromJsonString(
        value = record.characterSnapshot?.personaJson.orEmpty(),
        characterName = session.characterName,
        characterAvatar = session.characterAvatar,
    ),
    permissionMode = AgentPermissionMode.entries.firstOrNull {
        it.name.equals(session.permissionMode, ignoreCase = true)
    } ?: AgentPermissionMode.AskForApproval,
    messages = messages,
    historyMessageCount = session.historyMessageCount,
    createdAt = session.createdAt,
    updatedAt = session.updatedAt,
    initialVariableStateJson = record.variableStates
        .firstOrNull { it.kind == ChatVariableStateInitial }?.stateJson.orEmpty(),
    variableStateJson = record.variableStates
        .firstOrNull { it.kind == ChatVariableStateCurrent }?.stateJson.orEmpty(),
)
}

internal const val ChatVariableStateInitial = "initial"
internal const val ChatVariableStateCurrent = "current"
