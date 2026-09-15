package com.eleckoi.android.feature.chat.api

import androidx.paging.PagingData
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.eleckoi.conversation.PagedConversationTurn
import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.chat.data.ChatSendResult
import com.eleckoi.android.feature.chat.data.ChatDeleteMessagesResult
import com.eleckoi.android.feature.chat.data.PreparedChatRegeneration
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.model.ChatEncodedImageInput
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.foundation.design.AppearanceTheme
import java.io.File
import kotlinx.coroutines.flow.Flow

/** Operations owned by the chat feature and implemented by the application composition layer. */
interface ChatService {
    val uiPreferencesFlow: Flow<UiPreferences>
    val chatListFlow: Flow<List<ChatListItem>>
    val modelCollectionFlow: Flow<ModelConfigCollection>
    val characterCollectionFlow: Flow<CharactersPayload>

    fun chatDraftFlow(sessionId: String): Flow<ChatDraft>
    fun chatConversationPaging(sessionId: String): Flow<PagingData<PagedConversationTurn>>
    suspend fun currentDraft(): ChatDraft?
    suspend fun loadChatDraft(sessionId: String): ChatDraft
    /** Read-only first-frame projection used by the bounded navigation preloader. */
    suspend fun previewChatDraft(sessionId: String): ChatDraft = loadChatDraft(sessionId)
    suspend fun nextChatDraftForCharacter(characterId: String): ChatDraft?
    suspend fun chatDraftForCharacter(characterId: String): ChatDraft
    suspend fun createNewChat(characterId: String): ChatDraft
    suspend fun saveChatModelSelection(sessionId: String, selection: ChatModelSelection): ChatDraft
    suspend fun saveChatPermissionMode(
        sessionId: String,
        permissionMode: AgentPermissionMode,
    ): ChatDraft
    fun saveModelConfig(config: ModelConfig): ModelConfig
    fun refreshModelsForChat(config: ModelConfig): ModelConfig
    fun saveCharacterImagePrompt(characterId: String, prompt: String): CharacterSlot
    suspend fun deleteChat(sessionId: String)
    fun exportChatHistory(characterId: String, sessionIds: List<String>): String
    suspend fun importChatHistory(characterId: String, json: String): Int
    suspend fun applyHistoryPolicy(characterId: String)
    fun isStreamCancelled(error: Throwable): Boolean
    suspend fun prepareInputImages(uriValues: List<String>): List<ChatUserImageAttachment>
    suspend fun prepareEncodedInputImages(images: List<ChatEncodedImageInput>): List<ChatUserImageAttachment>
    fun discardInputImage(image: ChatUserImageAttachment)
    suspend fun sendMessage(
        draft: ChatDraft,
        message: String,
        inputImages: List<ChatUserImageAttachment> = emptyList(),
        onDelta: (ChatDraft) -> Unit,
        onUserTurnPersisted: (ChatDraft, String) -> Unit = { _, _ -> },
    ): ChatSendResult
    suspend fun deleteMessagesFrom(sessionId: String, messageId: String): ChatDeleteMessagesResult
    suspend fun prepareRegeneration(
        draft: ChatDraft,
        targetMessageId: String,
        replacementMessage: String?,
        pendingMessageId: String,
    ): PreparedChatRegeneration
    suspend fun runPreparedRegeneration(
        prepared: PreparedChatRegeneration,
        onDelta: (ChatDraft) -> Unit,
    ): ChatSendResult
    /** Redraws one persisted story frame from its stored prompt without another chat-model turn. */
    suspend fun regenerateImage(
        sessionId: String,
        messageId: String,
        attachmentId: String,
    ): ChatDraft
    suspend fun replaceChatVariableState(sessionId: String, stateJson: String): ChatDraft
    suspend fun resetChatVariableState(sessionId: String): ChatDraft
    suspend fun selectChatOpening(sessionId: String, openingOptionId: String): ChatDraft
    fun cancelActiveStream()
    suspend fun setHistorySaveMode(mode: String): UiPreferences
    fun saveCharacterChatBackground(
        characterId: String,
        backgroundFile: File?,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): CharacterSlot
    fun restoreCharacterChatBackgroundDefault(characterId: String): CharacterSlot
    fun useCharacterCardChatBackground(characterId: String): CharacterSlot
    fun useCustomChatBackground(characterId: String): CharacterSlot
    fun useGlobalChatBackground(characterId: String): CharacterSlot
    fun applyGlobalChatBackground(sourceCharacterId: String): CharacterSlot
    suspend fun saveGlobalChatBackground(
        backgroundFile: File?,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): AppearanceTheme
    suspend fun clearGlobalChatBackground(): AppearanceTheme
}
