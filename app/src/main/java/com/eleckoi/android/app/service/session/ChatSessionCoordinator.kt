package com.eleckoi.android.app.service

import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.defaultOpeningMessage
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.chat.data.ChatSessionNotFoundException
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.nowIso

internal class ChatSessionCoordinator(
    private val characters: CharacterRepository,
    private val sessions: ChatSessionStore,
    private val uiPreferences: UiPreferencesRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val variableConfig: VariableConfigRepository,
    private val variableRuntime: VariableRuntimeService,
    private val creatorWorkspaces: CreatorWorkspaceRepository,
    private val settleOrphanedPendingResponses: suspend (String) -> Unit,
) {
    suspend fun createChat(
        characterId: String,
        permissionMode: AgentPermissionMode? = null,
    ): ChatSession {
        val character = characters.characterById(characterId)
            ?: throw ElecKoiDataException("角色不存在")
        val now = nowIso()
        val persona = sessions.personaSnapshot(character)
        val variables = variableConfig.load(character.id)
        val openingMessage = settingLibrary.load(character.id).entries
            .firstOrNull { it.isOpeningEntry() && it.enabled }
            ?.defaultOpeningMessage()
        val opening = openingMessage?.content?.trim().orEmpty()
        val selectedInitialState = openingMessage?.initialVariableStateJson
            ?.takeIf(String::isNotBlank)
            ?: variables.initialStateJson.ifBlank { "{}" }
        val initialVariableState = if (variables.schemaCode.isNotBlank()) {
            variableRuntime.validateState(variables.schemaCode, selectedInitialState)
                .takeIf { it.ok }
                ?.normalizedStateJson
                ?.ifBlank { selectedInitialState }
                ?: selectedInitialState
        } else {
            selectedInitialState
        }
        val workspace = creatorWorkspaces.ensureCharacterWorkspace(
            characterId = character.id,
            name = "${character.name.ifBlank { "角色" }} · 剧情小说",
        )
        val initialMessages = opening.takeIf { it.isNotBlank() }?.let { content ->
            listOf(
                ChatMessage(
                    id = OpeningMessageId,
                    role = MessageRole.Assistant,
                    content = content,
                    createdAt = now,
                    variableStateJson = initialVariableState,
                ),
            )
        }.orEmpty()
        val session = ChatSession(
            id = newId(12),
            workspaceId = workspace.id,
            title = character.name.ifBlank { "新对话" },
            characterId = character.id,
            characterName = character.name,
            characterAvatar = character.avatar,
            characterPersona = persona,
            permissionMode = permissionMode ?: workspace.permissionMode,
            messages = initialMessages,
            createdAt = now,
            updatedAt = now,
            initialVariableStateJson = initialVariableState,
            variableStateJson = initialVariableState,
        )
        sessions.replaceUnstartedWith(session)
        sessions.applyHistorySavePolicy(character.id)
        return session
    }

    suspend fun latestSession(character: CharacterSlot): ChatSession? =
        sessions.latest(character)?.let { ensureWorkspaceBinding(it) }

    suspend fun lastActiveChatSession(): ChatSession? {
        val sessionId = uiPreferences.lastActiveChatSessionId()
        if (sessionId.isBlank()) return null
        return loadRememberedSessionOrNull(sessionId)
    }

    suspend fun rememberedChatSession(characterId: String): ChatSession? {
        val sessionId = uiPreferences.activeChatSessionId(characterId)
        if (sessionId.isBlank()) return null
        return loadRememberedSessionOrNull(sessionId)
            ?.takeIf { session -> session.characterId == characterId }
    }

    private suspend fun loadRememberedSessionOrNull(sessionId: String): ChatSession? {
        return try {
            loadChat(sessionId, touch = false)
        } catch (_: ChatSessionNotFoundException) {
            // A remembered id is allowed to become stale after deletion or import. Clear only
            // that pointer; database, filesystem, and decoding failures must reach the caller.
            uiPreferences.removeActiveChatSessionId(sessionId)
            null
        }
    }

    suspend fun rememberChatSession(session: ChatSession): ChatSession {
        uiPreferences.setActiveChatSessionId(
            characterId = session.characterId,
            sessionId = session.id,
        )
        return session
    }

    suspend fun loadChat(sessionId: String, touch: Boolean): ChatSession {
        settleOrphanedPendingResponses(sessionId)
        return ensureWorkspaceBinding(sessions.load(sessionId, touch))
    }

    suspend fun ensureWorkspaceBinding(session: ChatSession): ChatSession {
        val existing = session.workspaceId.takeIf(String::isNotBlank)
            ?.let { creatorWorkspaces.get(it) }
            ?.takeIf { workspace ->
                workspace.linkedCharacterId == session.characterId &&
                    workspace.characterOwned
            }
        if (existing != null) return session

        val workspace = creatorWorkspaces.ensureCharacterWorkspace(
            characterId = session.characterId,
            name = "${session.characterName.ifBlank { session.title }} · 剧情小说",
        )
        return session.copy(workspaceId = workspace.id, updatedAt = nowIso()).also(sessions::write)
    }

}
