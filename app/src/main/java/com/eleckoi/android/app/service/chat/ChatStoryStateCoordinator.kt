package com.eleckoi.android.app.service

import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.feature.chat.model.withVariableState
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.nowIso
import org.json.JSONObject

/** Applies story-only variable and opening mutations as one persistence transaction boundary. */
internal class ChatStoryStateCoordinator(
    private val sessions: ChatSessionStore,
    private val settingLibrary: SettingLibraryRepository,
    private val variableConfig: VariableConfigRepository,
    private val variableRuntime: VariableRuntimeService,
    private val sessionCoordinator: ChatSessionCoordinator,
    private val projectDraft: (ChatSession) -> ChatDraft,
) {
    suspend fun replaceVariableState(sessionId: String, stateJson: String): ChatDraft {
        val session = sessions.load(sessionId, touch = false)
        if (session.messages.lastOrNull()?.pending == true) {
            throw ElecKoiDataException("AI 正在生成，暂时不能修改变量状态")
        }
        val normalizedState = normalizeVariableState(stateJson)
        val config = variableConfig.load(session.characterId)
        if (config.schemaCode.isNotBlank()) {
            val result = variableRuntime.validateState(config.schemaCode, normalizedState)
            if (!result.ok) {
                throw ElecKoiDataException(result.message.ifBlank { "变量状态不符合 Zod 规则" })
            }
        }
        val messages = session.messages.lastOrNull()
            ?.takeUnless { it.id == OpeningMessageId }
            ?.let { latest ->
                session.messages.dropLast(1) + latest.withVariableState(normalizedState)
            }
            ?: session.messages
        val updated = session.copy(
            messages = messages,
            variableStateJson = normalizedState,
            updatedAt = nowIso(),
        )
        updated.messages.lastOrNull()?.let { latest ->
            sessions.updateMessage(updated, latest)
        } ?: sessions.updateMetadata(updated)
        return projectDraft(updated)
    }

    suspend fun resetVariableState(sessionId: String): ChatDraft {
        val session = sessions.load(sessionId, touch = false)
        return replaceVariableState(sessionId, session.initialVariableStateJson)
    }

    suspend fun selectOpening(sessionId: String, openingOptionId: String): ChatDraft {
        val session = sessions.load(sessionId, touch = false)
        val openingEntry = settingLibrary.load(session.characterId).entries
            .firstOrNull { it.isOpeningEntry() && it.enabled }
            ?: throw ElecKoiDataException("当前角色没有启用开场白")
        val opening = openingEntry.openingMessages
            .firstOrNull { it.id == openingOptionId }
            ?: throw ElecKoiDataException("找不到这条开场白")
        val variables = variableConfig.load(session.characterId)
        val requestedState = opening.initialVariableStateJson
            .takeIf(String::isNotBlank)
            ?: variables.initialStateJson.ifBlank { "{}" }
        val initialState = if (variables.schemaCode.isNotBlank()) {
            variableRuntime.validateState(variables.schemaCode, requestedState)
                .takeIf { it.ok }
                ?.normalizedStateJson
                ?.ifBlank { requestedState }
                ?: requestedState
        } else {
            requestedState
        }
        val updated = sessions.selectOpening(
            sessionId = session.id,
            content = opening.content.trim(),
            initialVariableStateJson = initialState,
        )
        return projectDraft(updated)
    }
}

internal fun normalizeVariableState(stateJson: String): String =
    runCatching { JSONObject(stateJson.trim()).toString(2) }
        .getOrElse { throw ElecKoiDataException("变量状态必须是 JSON object：${it.message}") }
