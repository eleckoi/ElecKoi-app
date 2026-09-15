package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ChatSettingsController(
    private val scope: CoroutineScope,
    private val chatService: ChatService,
    private val state: () -> ChatUiState,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
    private val selectSession: (String) -> Unit,
) {
    fun updatePermissionMode(mode: AgentPermissionMode) {
        val current = state().draft ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    chatService.saveChatPermissionMode(current.session.id, mode)
                }
            }.onSuccess { next ->
                updateState { it.copy(draft = next, errorMessage = "") }
            }.onRealFailure { error ->
                updateState { it.copy(errorMessage = error.message ?: "权限模式保存失败") }
            }
        }
    }

    fun selectModel(configId: String, model: String) {
        val current = state().draft ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    chatService.saveChatModelSelection(
                        current.session.id,
                        ChatModelSelection(
                            capability = "chat",
                            configId = configId,
                            model = model,
                        ),
                    )
                }
            }.onSuccess { next ->
                updateState { it.copy(draft = next) }
                selectSession(next.session.id)
            }.onRealFailure { error ->
                updateState { it.copy(errorMessage = error.message ?: "模型选择保存失败") }
            }
        }
    }

    fun refreshModels(config: ModelConfig, onFinished: (Result<ModelConfig>) -> Unit = {}) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { chatService.refreshModelsForChat(config) }
            }
            result.onRealFailure { error ->
                updateState { it.copy(errorMessage = error.message ?: "刷新模型列表失败") }
            }
            onFinished(result)
        }
    }

    fun saveModelConfig(config: ModelConfig, onFinished: (Result<ModelConfig>) -> Unit = {}) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { chatService.saveModelConfig(config) }
            }
            result.onRealFailure { error ->
                updateState { it.copy(errorMessage = error.message ?: "模型设置保存失败") }
            }
            onFinished(result)
        }
    }

    fun changeHistorySaveMode(mode: String) {
        val normalized = if (mode == "recent10") "recent10" else "all"
        val snapshot = state()
        val characterId = snapshot.draft?.session?.characterId ?: snapshot.chatCharacterId
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    chatService.setHistorySaveMode(normalized)
                    if (normalized == "recent10") chatService.applyHistoryPolicy(characterId)
                }
            }.onRealFailure { error ->
                updateState { it.copy(errorMessage = error.message ?: "历史策略保存失败") }
            }
        }
    }

}
