package com.eleckoi.android.feature.modelconfig.api

import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import kotlinx.coroutines.flow.Flow

interface ModelService {
    val modelCollectionFlow: Flow<ModelConfigCollection>

    suspend fun defaultConversationModelSelection(): ChatModelSelection
    suspend fun saveDefaultConversationModelSelection(configId: String, model: String): ChatModelSelection
    fun blankModelConfig(providerId: String, id: String = ""): ModelConfig
    fun saveModelConfig(config: ModelConfig): ModelConfig
    fun deleteModelConfig(configId: String): ModelConfigCollection
    suspend fun fetchModelOptions(config: ModelConfig): ModelConfig
    suspend fun testModelConnection(config: ModelConfig)
}
