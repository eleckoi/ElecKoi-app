package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.feature.chat.model.ChatListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Owns the metadata catalog around a conversation. It never observes or projects message history;
 * Paging and the generation coordinator remain the only owners of persisted rows and the tail.
 */
internal class ChatSupportDataController(
    private val scope: CoroutineScope,
    private val chatService: ChatService,
    private val state: () -> ChatUiState,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
    private val clearSession: () -> Unit,
    private val resetPaging: () -> Unit,
    private val updateRecentChats: (List<ChatListItem>, Map<String, String>) -> Unit,
) {
    fun start() {
        scope.launch {
            combine(
                chatService.chatListFlow,
                chatService.modelCollectionFlow,
                chatService.uiPreferencesFlow,
                chatService.characterCollectionFlow,
            ) { sessions, models, preferences, characters ->
                ChatSupportData(
                    sessions = sessions,
                    modelConfigs = models.chatConfigs,
                    imageModelConfigs = models.configs.filter(ModelConfig::isImageGenerationConfig),
                    historySaveMode = preferences.historySaveMode,
                    activeChatSessionIds = preferences.activeChatSessionIds,
                    characterIds = characters.items.mapTo(hashSetOf()) { it.id },
                )
            }.catch { error ->
                updateState { it.copy(errorMessage = error.message ?: "刷新聊天支持数据失败") }
            }.collectLatest(::apply)
        }
    }

    private fun apply(data: ChatSupportData) {
        val removedCharacterSelected = state().hasRemovedSelectedCharacter(data)
        if (removedCharacterSelected) {
            clearSession()
            resetPaging()
        }
        updateState { current -> current.withSupportData(data, removedCharacterSelected) }
        updateRecentChats(data.sessions, data.activeChatSessionIds)
    }
}

internal data class ChatSupportData(
    val sessions: List<ChatListItem>,
    val modelConfigs: List<ModelConfig>,
    val imageModelConfigs: List<ModelConfig>,
    val historySaveMode: String,
    val activeChatSessionIds: Map<String, String>,
    val characterIds: Set<String>,
)

internal fun ChatUiState.hasRemovedSelectedCharacter(data: ChatSupportData): Boolean =
    draft?.session?.characterId
        ?.takeIf(String::isNotBlank)
        ?.let { characterId -> characterId !in data.characterIds }
        ?: false

internal fun ChatUiState.withSupportData(
    data: ChatSupportData,
    removedCharacterSelected: Boolean = hasRemovedSelectedCharacter(data),
): ChatUiState = copy(
    draft = if (removedCharacterSelected) null else draft,
    isDraftLoading = if (removedCharacterSelected) false else isDraftLoading,
    chatCharacterId = if (removedCharacterSelected) "" else chatCharacterId,
    chatCharacterName = if (removedCharacterSelected) "" else chatCharacterName,
    sessions = data.sessions,
    modelConfigs = data.modelConfigs,
    imageModelConfigs = data.imageModelConfigs,
    historySaveMode = data.historySaveMode,
)
