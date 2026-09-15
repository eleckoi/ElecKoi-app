package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.nowIso

internal class CharacterAgentGenerationEnvironment(
    private val settings: ModelConfigRepository,
    private val workspaces: CreatorWorkspaceRepository,
    private val sessions: ChatSessionStore,
) {
    suspend fun ensureWorkspace(session: ChatSession): ChatSession {
        val existing = session.workspaceId.takeIf(String::isNotBlank)
            ?.let { workspaces.get(it) }
            ?.takeIf { workspace ->
                workspace.linkedCharacterId == session.characterId &&
                    workspace.characterOwned
            }
        val workspace = existing ?: workspaces.ensureCharacterWorkspace(
            characterId = session.characterId,
            name = "${session.characterName.ifBlank { session.title }} · 剧情小说",
        )
        return if (session.workspaceId == workspace.id) {
            session
        } else {
            session.copy(workspaceId = workspace.id, updatedAt = nowIso()).also(sessions::updateMetadata)
        }
    }

    fun selectedConfig(draft: ChatDraft): ModelConfig {
        val collection = settings.loadModelConfigCollection()
        val configured = collection.chatConfigs.firstOrNull { it.id == draft.selectedModelConfig.id }
            ?: draft.selectedModelConfig
        val selectedModel = draft.selectedModel
        val result = if (selectedModel.isNotBlank()) configured.copy(model = selectedModel) else configured
        if (result.id.isBlank() || result.model.isBlank()) {
            throw ElecKoiDataException("请先选择可用的 Agent 模型配置")
        }
        return result
    }
}
