package com.eleckoi.android.feature.studio.ui.assistant

import com.eleckoi.android.feature.modelconfig.api.ModelService
import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.studio.ui.assistant.session.creationAssistantMessage
import com.eleckoi.android.feature.studio.ui.assistant.timeline.toCreationModelChoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class CreationModelController(
    private val scope: CoroutineScope,
    private val modelService: ModelService,
    private val state: () -> AiCreationAssistantUiState,
    private val updateState: ((AiCreationAssistantUiState) -> AiCreationAssistantUiState) -> Unit,
    private val detachSession: () -> Job?,
) {
    fun start() {
        scope.launch {
            modelService.modelCollectionFlow.collect { collection ->
                val defaultSelection = withContext(Dispatchers.IO) {
                    modelService.defaultConversationModelSelection()
                }
                updateState { current ->
                    val selectedConfig = collection.resolveCreationChatConfig(
                        currentConfigId = current.selectedModelConfigId,
                        defaultConfigId = defaultSelection.configId,
                    )
                    ?: ModelConfig()
                    val choices = selectedConfig.toCreationModelChoices()
                    val preferredModel = if (selectedConfig.id == current.selectedModelConfigId) {
                        current.selectedModelId
                    } else if (selectedConfig.id == defaultSelection.configId) {
                        defaultSelection.model
                    } else {
                        selectedConfig.model
                    }
                    val selectedModel = preferredModel.takeIf { candidate ->
                        candidate.isNotBlank() && choices.any { it.id == candidate }
                    } ?: selectedConfig.model.trim()
                    current.copy(
                        // The picker filters chat and image providers by surface. Keep the full
                        // collection here so the independent creator tool can select its image
                        // provider without borrowing the active role preset.
                        modelConfigs = collection.configs,
                        selectedModelConfigId = selectedConfig.id,
                        selectedModelId = selectedModel,
                        modelChoices = choices,
                        modelLabel = choices.firstOrNull { it.id == selectedModel }?.label
                            ?: selectedModel.ifBlank { "未配置模型" },
                    )
                }
            }
        }
    }

    fun change(configId: String, modelId: String) {
        val snapshot = state()
        val config = snapshot.modelConfigs.firstOrNull { it.id == configId } ?: return
        val choices = config.toCreationModelChoices()
        val choice = choices.firstOrNull { it.id == modelId } ?: return
        if (snapshot.selectedModelConfigId == config.id && snapshot.selectedModelId == choice.id) return
        if (snapshot.isRunning) {
            updateState { it.copy(errorMessage = "请先停止当前任务，再切换模型") }
            return
        }
        detachSession()
        updateState {
            it.copy(
                selectedModelConfigId = config.id,
                selectedModelId = choice.id,
                modelLabel = choice.label,
                modelChoices = choices,
                generationStats = it.generationStats.copy(contextWindowUsage = null),
            )
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    modelService.saveDefaultConversationModelSelection(config.id, choice.id)
                }
            }.onFailure { error ->
                updateState {
                    it.copy(errorMessage = error.creationAssistantMessage("模型选择保存失败"))
                }
            }
        }
    }

    fun refreshModels(
        config: ModelConfig,
        onFinished: (Result<ModelConfig>) -> Unit = {},
    ) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { modelService.fetchModelOptions(config) }
            }
            result.onSuccess { saved ->
                if (saved.id == state().selectedModelConfigId) detachSession()
            }
            result.onFailure { error ->
                updateState {
                    it.copy(errorMessage = error.creationAssistantMessage("刷新模型列表失败"))
                }
            }
            onFinished(result)
        }
    }

    fun saveModelConfig(
        config: ModelConfig,
        onFinished: (Result<ModelConfig>) -> Unit = {},
    ) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { modelService.saveModelConfig(config) }
            }
            result.onSuccess { saved ->
                if (saved.id == state().selectedModelConfigId) detachSession()
            }
            result.onFailure { error ->
                updateState {
                    it.copy(errorMessage = error.creationAssistantMessage("保存模型设置失败"))
                }
            }
            onFinished(result)
        }
    }
}

internal fun ModelConfigCollection.resolveCreationChatConfig(
    currentConfigId: String,
    defaultConfigId: String,
): ModelConfig? = chatConfigs.firstOrNull { it.id == currentConfigId }
    ?: chatConfigs.firstOrNull { it.id == defaultConfigId }
    ?: chatConfigs.firstOrNull { it.model.isNotBlank() }
    ?: chatConfigs.firstOrNull()
