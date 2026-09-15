package com.eleckoi.android.app.service

import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the fallback rules shared by chat and creator model selection.
 *
 * Keeping this policy outside the facade prevents the two feature services from
 * slowly developing different interpretations of the same persisted defaults.
 */
internal class ChatModelSelectionResolver(
    private val settings: ModelConfigRepository,
    private val uiPreferences: UiPreferencesRepository,
) {
    suspend fun default(
        collection: ModelConfigCollection = settings.loadModelConfigCollection(),
    ): ChatModelSelection = defaultFor(uiPreferences.read(), collection)

    fun defaultCached(
        collection: ModelConfigCollection = settings.loadModelConfigCollection(),
    ): ChatModelSelection = defaultFor(uiPreferences.preferencesFlow.value, collection)

    private fun defaultFor(
        preferences: com.eleckoi.android.feature.preferences.UiPreferences,
        collection: ModelConfigCollection,
    ): ChatModelSelection {
        val storedDefault = ChatModelSelection(
            capability = "chat",
            configId = preferences.defaultChatConfigId,
            model = preferences.defaultChatModel,
        )
        ChatModelSelectionPolicy.validated(storedDefault, collection)?.let { return it }
        return ChatModelSelectionPolicy.bootstrap(collection)
    }

    fun validated(
        selection: ChatModelSelection?,
        collection: ModelConfigCollection,
    ): ChatModelSelection? = ChatModelSelectionPolicy.validated(selection, collection)

    suspend fun defaultCreatorModelConfig(): ModelConfig = withContext(Dispatchers.IO) {
        val collection = settings.loadModelConfigCollection()
        val selection = default(collection)
        val config = collection.chatConfigs.firstOrNull { it.id == selection.configId }
            ?: collection.chatConfigs.firstOrNull { it.model.isNotBlank() }
            ?: collection.chatConfigs.firstOrNull()
            ?: ModelConfig()
        config.copy(model = selection.model.ifBlank { config.model })
    }

    suspend fun creatorModelConfig(configId: String?): ModelConfig = withContext(Dispatchers.IO) {
        val collection = settings.loadModelConfigCollection()
        val selection = default(collection)
        collection.chatConfigs.firstOrNull { it.id == configId }
            ?: collection.chatConfigs.firstOrNull { it.id == selection.configId }
            ?: collection.chatConfigs.firstOrNull { it.model.isNotBlank() }
            ?: collection.chatConfigs.firstOrNull()
            ?: ModelConfig()
    }
}

internal object ChatModelSelectionPolicy {
    fun validated(
        selection: ChatModelSelection?,
        collection: ModelConfigCollection,
    ): ChatModelSelection? {
        selection ?: return null
        val config = collection.chatConfigs.firstOrNull { it.id == selection.configId } ?: return null
        val model = selection.model.ifBlank { config.model }
        if (model.isBlank()) return null
        return selection.copy(capability = "chat", configId = config.id, model = model)
    }

    fun bootstrap(collection: ModelConfigCollection): ChatModelSelection {
        val config = collection.chatConfigs.firstOrNull { it.model.isNotBlank() }
            ?: collection.chatConfigs.firstOrNull()
            ?: ModelConfig()
        return ChatModelSelection(
            capability = "chat",
            configId = config.id,
            model = config.model,
        )
    }
}
