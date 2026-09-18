package com.eleckoi.android.app.service

import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.api.ModelService
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

internal class ModelServiceImpl(
    private val settings: ModelConfigRepository,
    private val uiPreferences: UiPreferencesRepository,
    private val selections: ChatModelSelectionResolver,
) : ModelService {
    override val modelCollectionFlow: Flow<ModelConfigCollection> = settings.modelConfigCollectionFlow
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override suspend fun defaultConversationModelSelection(): ChatModelSelection = selections.default()

    override suspend fun saveDefaultConversationModelSelection(
        configId: String,
        model: String,
    ): ChatModelSelection {
        val collection = settings.loadModelConfigCollection()
        val config = collection.chatConfigs.firstOrNull { it.id == configId }
            ?: throw ElecKoiDataException("模型配置不存在")
        val selectedModel = model.trim().ifBlank { config.model }
        if (selectedModel.isBlank()) throw ElecKoiDataException("模型名称不能为空")
        uiPreferences.setDefaultChatModel(config.id, selectedModel)
        return ChatModelSelection(
            capability = "chat",
            configId = config.id,
            model = selectedModel,
        )
    }

    override fun blankModelConfig(providerId: String, id: String): ModelConfig {
        return settings.blankConfig(providerId, id)
    }

    override fun saveModelConfig(config: ModelConfig): ModelConfig = settings.saveModelConfig(config)

    override fun deleteModelConfig(configId: String): ModelConfigCollection {
        return settings.deleteModelConfig(configId)
    }

    override suspend fun fetchModelOptions(config: ModelConfig): ModelConfig = settings.fetchModelOptions(config)

    override suspend fun testModelConnection(config: ModelConfig) = settings.testConnection(config)
}
