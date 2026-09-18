package com.eleckoi.android.app.service

import androidx.paging.PagingData
import androidx.paging.map as mapPagingData
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.api.AgentInputImage
import com.eleckoi.android.engine.agent.api.AgentSessionFactory
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.app.service.chat.ChatGenerationRunCoordinator
import com.eleckoi.android.engine.agent.eleckoi.conversation.PagedConversationTurn
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.engine.display.MessageDisplayCompatibility
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.chat.data.CharacterAgentGenerationService
import com.eleckoi.android.feature.chat.data.ChatSendResult
import com.eleckoi.android.feature.chat.data.ChatDeleteMessagesResult
import com.eleckoi.android.feature.chat.data.PreparedChatRegeneration
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.chat.data.ChatInputImageStore
import com.eleckoi.android.feature.chat.data.GenerationAttemptRepository
import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.model.ChatEncodedImageInput
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.settings.data.appearance.AppearanceRepository
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.feature.modelconfig.api.ModelService
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.nowIso
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class ChatServiceImpl(
    private val characters: CharacterRepository,
    private val sessions: ChatSessionStore,
    private val settings: ModelConfigRepository,
    private val uiPreferences: UiPreferencesRepository,
    private val appearance: AppearanceRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val regexRules: RegexRuleRepository,
    private val variableConfig: VariableConfigRepository,
    private val variableRuntime: VariableRuntimeService,
    private val creatorWorkspaces: CreatorWorkspaceRepository,
    private val modelService: ModelService,
    private val modelSelections: ChatModelSelectionResolver,
    private val replyImageGenerator: ReplyImageGenerator,
    private val generationAttempts: GenerationAttemptRepository,
    private val inputImages: ChatInputImageStore,
    private val displayCompatibility: MessageDisplayCompatibility,
    private val activeAgentPreset: suspend () -> AgentPreset,
    private val captureProviderRequests: Boolean,
) : ChatService {
    @Volatile
    private var characterAgentGeneration: CharacterAgentGenerationService? = null
    @Volatile
    private var agentRunManager: AgentRunManager? = null
    @Volatile
    private var deleteObsoleteRuntimeSessions: (Set<String>) -> Unit = {}

    internal fun setRuntimeSessionCleanup(cleanup: (Set<String>) -> Unit) {
        deleteObsoleteRuntimeSessions = cleanup
    }
    private val sessionCoordinator = ChatSessionCoordinator(
        characters = characters,
        sessions = sessions,
        uiPreferences = uiPreferences,
        settingLibrary = settingLibrary,
        variableConfig = variableConfig,
        variableRuntime = variableRuntime,
        creatorWorkspaces = creatorWorkspaces,
        settleOrphanedPendingResponses = { sessionId ->
            characterAgentGeneration
                ?.settleOrphanedPendingResponses(sessionId)
                ?: sessions.settleOrphanedPendingResponses(sessionId)
        },
    )
    private val draftProjector = ChatDraftProjector(
        sessions = sessions,
        settings = settings,
        modelSelections = modelSelections,
        regexRules = regexRules,
        settingLibrary = settingLibrary,
        displayCompatibility = displayCompatibility,
    )
    private val mediaCoordinator = ChatMediaCoordinator(
        characters = characters,
        sessions = sessions,
        settings = settings,
        appearance = appearance,
        replyImageGenerator = replyImageGenerator,
        generationAttempts = generationAttempts,
        activeAgentPreset = activeAgentPreset,
        projectDraft = { session -> draftProjector.project(session) },
    )
    private val storyStateCoordinator = ChatStoryStateCoordinator(
        sessions = sessions,
        settingLibrary = settingLibrary,
        variableConfig = variableConfig,
        variableRuntime = variableRuntime,
        sessionCoordinator = sessionCoordinator,
        projectDraft = draftProjector::project,
    )
    private val generationRunCoordinator = ChatGenerationRunCoordinator(
        characterAgent = ::characterAgent,
        agentRuns = ::agentRuns,
    )

    override val uiPreferencesFlow: Flow<UiPreferences> = uiPreferences.preferencesFlow
    override val chatListFlow: Flow<List<ChatListItem>> = sessions.chatListFlow()
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
    override val modelCollectionFlow: Flow<ModelConfigCollection> = settings.modelConfigCollectionFlow
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
    override val characterCollectionFlow: Flow<CharactersPayload> = characters.charactersFlow()
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    internal fun attachCharacterAgentRuntime(
        agentSessions: AgentSessionFactory,
        runtime: LocalRuntimeGateway,
        virtualFileSearch: AgentVirtualFileSearch,
        toolContextSnapshot: (Set<String>) -> AgentToolContextSnapshot,
        agentRuns: AgentRunManager,
        publishRemoteDshTurnImages: (String, List<AgentInputImage>) -> Unit,
        activeAgentPreset: suspend () -> AgentPreset,
    ) {
        check(characterAgentGeneration == null) { "角色 Agent 运行时已经绑定" }
        check(agentRunManager == null) { "后台 Agent 运行管理器已经绑定" }
        agentRunManager = agentRuns
        characterAgentGeneration = CharacterAgentGenerationService(
            characters = characters,
            sessions = sessions,
            settings = settings,
            workspaces = creatorWorkspaces,
            settingLibrary = settingLibrary,
            regexRules = regexRules,
            variableConfig = variableConfig,
            variableRuntime = variableRuntime,
            runtime = runtime,
            agentSessions = agentSessions,
            virtualFileSearch = virtualFileSearch,
            toolContextSnapshot = toolContextSnapshot,
            prepareDraftProjection = draftProjector::prepareStreaming,
            replyImageGenerator = replyImageGenerator,
            generationAttempts = generationAttempts,
            activeAgentPreset = activeAgentPreset,
            publishRemoteDshTurnImages = publishRemoteDshTurnImages,
            captureProviderRequests = captureProviderRequests,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun chatDraftFlow(sessionId: String): Flow<ChatDraft> =
        sessions.chatSessionFlow(sessionId).flatMapLatest { session ->
            combine(
                settings.modelConfigCollectionFlow,
                regexRules.revision,
                settingLibrary.libraryFlow(session.characterId),
            ) { _, _, _ -> draftFromSession(session) }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun chatConversationPaging(
        sessionId: String,
    ): Flow<PagingData<PagedConversationTurn>> = combine(
        sessions.chatSessionFlow(sessionId)
            .map { session -> session.characterId }
            .distinctUntilChanged(),
        regexRules.revision,
    ) { characterId, revision -> characterId to revision }
        .flatMapLatest { (characterId, regexRevision) ->
            val regexConfig = regexRules.load(characterId)
            sessions.pagingTurns(sessionId).map { pagingData ->
                pagingData.mapPagingData { turn ->
                    withContext(Dispatchers.Default) {
                        turn.copy(
                            messages = turn.messages.map { message ->
                                draftProjector.projectLedgerMessage(
                                    message = message,
                                    config = regexConfig,
                                    characterId = characterId,
                                    regexRevision = regexRevision,
                                )
                            },
                        )
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun currentDraft(): ChatDraft? {
        sessionCoordinator.lastActiveChatSession()?.let { session ->
            val character = characters.characterById(session.characterId)
            if (character != null) {
                return draftFromSession(sessionCoordinator.rememberChatSession(session))
            }
        }
        val payload = characters.loadCharacters()
        val character = payload.items.firstOrNull { it.id == payload.activeCharacterId }
            ?: payload.items.firstOrNull()
            ?: return null
        val session = sessionCoordinator.rememberedChatSession(character.id)
            ?: sessionCoordinator.latestSession(character)
            ?: sessionCoordinator.createChat(character.id)
        return draftFromSession(sessionCoordinator.rememberChatSession(session))
    }

    override suspend fun loadChatDraft(sessionId: String): ChatDraft {
        // Opening a conversation is navigation, not new activity. Only message mutations should
        // advance the conversation in the root message list.
        return draftFromSession(sessionCoordinator.rememberChatSession(sessionCoordinator.loadChat(sessionId, touch = false)))
    }

    override suspend fun previewChatDraft(sessionId: String): ChatDraft =
        draftFromSession(sessionCoordinator.loadChat(sessionId, touch = false))

    override suspend fun nextChatDraftForCharacter(characterId: String): ChatDraft? {
        return chatList()
            .firstOrNull { it.characterId == characterId }
            ?.let { loadChatDraft(it.id) }
    }

    override suspend fun chatDraftForCharacter(characterId: String): ChatDraft {
        val character = characters.characterById(characterId)
            ?: throw ElecKoiDataException("角色不存在")
        val session = sessionCoordinator.rememberedChatSession(character.id)
            ?: sessionCoordinator.latestSession(character)
            ?: sessionCoordinator.createChat(character.id)
        return draftFromSession(sessionCoordinator.rememberChatSession(session))
    }

    override suspend fun createNewChat(characterId: String): ChatDraft {
        val character = characters.characterById(characterId)
            ?: throw ElecKoiDataException("角色不存在")
        val previous = sessionCoordinator.latestSession(character)
        val inheritedPermissionMode = previous?.permissionMode
        val created = sessionCoordinator.createChat(
            characterId = characterId,
            permissionMode = inheritedPermissionMode,
        )
        inheritedPermissionMode?.let { permissionMode ->
            creatorWorkspaces.saveWorkspacePermissionMode(created.workspaceId, permissionMode)
        }
        return draftFromSession(sessionCoordinator.rememberChatSession(created))
    }

    override suspend fun saveChatModelSelection(
        sessionId: String,
        selection: ChatModelSelection,
    ): ChatDraft {
        val collection = settings.loadModelConfigCollection()
        val selected = collection.chatConfigs.firstOrNull { it.id == selection.configId }
            ?: throw ElecKoiDataException("模型配置不存在")
        val normalized = selection.copy(
            capability = "chat",
            configId = selected.id,
            model = selection.model.ifBlank { selected.model },
        )
        if (normalized.model.isBlank()) throw ElecKoiDataException("模型名称不能为空")
        modelService.saveDefaultConversationModelSelection(
            normalized.configId,
            normalized.model,
        )
        val session = sessions.load(sessionId, touch = false)
        return draftFromSession(session, selected.copy(model = normalized.model))
    }

    override suspend fun saveChatPermissionMode(
        sessionId: String,
        permissionMode: AgentPermissionMode,
    ): ChatDraft {
        val loaded = sessionCoordinator.ensureWorkspaceBinding(
            sessions.load(sessionId, touch = false),
        )
        creatorWorkspaces.saveWorkspacePermissionMode(loaded.workspaceId, permissionMode)
        val session = loaded.copy(
            permissionMode = permissionMode,
            updatedAt = nowIso(),
        )
        sessions.updateMetadata(session)
        return draftFromSession(session)
    }

    override fun saveModelConfig(config: ModelConfig): ModelConfig = settings.saveModelConfig(config)

    override suspend fun refreshModelsForChat(config: ModelConfig): ModelConfig = settings.fetchModelOptions(config)

    override fun saveCharacterImagePrompt(characterId: String, prompt: String): CharacterSlot {
        val character = characters.characterById(characterId)
            ?: throw ElecKoiDataException("角色不存在")
        return characters.saveCharacterPersona(
            characterId,
            character.persona.copy(imagePrompt = prompt.trim().take(4_000)),
        )
    }

    override suspend fun deleteChat(sessionId: String) {
        sessions.delete(sessionId)
    }

    internal fun clearDeletionProjectionCaches() = draftProjector.clearCaches()

    override fun exportChatHistory(
        characterId: String,
        sessionIds: List<String>,
    ): String = sessions.exportHistory(characterId, sessionIds)

    override suspend fun importChatHistory(characterId: String, json: String): Int {
        return sessions.importHistory(characterId, json)
    }

    override suspend fun applyHistoryPolicy(characterId: String) {
        sessions.applyHistorySavePolicy(characterId)
    }

    override fun isStreamCancelled(error: Throwable): Boolean {
        return characterAgent().isStreamCancelled(error)
    }

    override suspend fun prepareInputImages(uriValues: List<String>): List<ChatUserImageAttachment> =
        inputImages.prepare(uriValues)

    override suspend fun prepareEncodedInputImages(
        images: List<ChatEncodedImageInput>,
    ): List<ChatUserImageAttachment> = inputImages.prepareEncoded(images)

    override fun discardInputImage(image: ChatUserImageAttachment) {
        inputImages.delete(image)
    }

    override suspend fun sendMessage(
        runId: String,
        draft: ChatDraft,
        message: String,
        inputImages: List<ChatUserImageAttachment>,
        onDelta: (ChatDraft) -> Unit,
        onUserTurnPersisted: (ChatDraft, String) -> Unit,
    ): ChatSendResult {
        uiPreferences.restoreChatEntry(draft.session.id)
        return generationRunCoordinator.sendMessage(
            runId,
            draft,
            message,
            inputImages,
            onDelta,
            onUserTurnPersisted,
        )
    }

    override suspend fun rawChatMessage(sessionId: String, messageId: String): ChatMessage? =
        withContext(Dispatchers.IO) { sessions.message(sessionId, messageId) }

    override suspend fun editAssistantMessage(sessionId: String, messageId: String, content: String) {
        val obsoleteRuntimeThreadIds = withContext(Dispatchers.IO) {
            sessions.editAssistantMessage(sessionId, messageId, content)
        }
        withContext(Dispatchers.IO) {
            deleteObsoleteRuntimeSessions(obsoleteRuntimeThreadIds)
        }
        draftProjector.clearCaches()
    }

    override suspend fun deleteMessagesFrom(sessionId: String, messageId: String): ChatDeleteMessagesResult {
        val removed = withContext(Dispatchers.IO) { sessions.deleteMessagesFrom(sessionId, messageId) }
        withContext(Dispatchers.IO) {
            deleteObsoleteRuntimeSessions(removed.obsoleteRuntimeThreadIds)
        }
        draftProjector.clearCaches()
        return ChatDeleteMessagesResult(
            draft = draftProjector.project(removed.session),
            deletedMessageIds = removed.deletedMessageIds,
            remainingMessageCount = removed.remainingMessageCount,
        )
    }

    override suspend fun prepareRegeneration(
        draft: ChatDraft,
        targetMessageId: String,
        replacementMessage: String?,
        pendingMessageId: String,
    ): PreparedChatRegeneration {
        val prepared = characterAgent().prepareRegeneration(
            draft = draft,
            targetMessageId = targetMessageId,
            replacementMessage = replacementMessage,
            pendingMessageId = pendingMessageId,
        )
        uiPreferences.restoreChatEntry(prepared.session.id)
        return prepared
    }

    override suspend fun runPreparedRegeneration(
        runId: String,
        prepared: PreparedChatRegeneration,
        onDelta: (ChatDraft) -> Unit,
    ): ChatSendResult = generationRunCoordinator.runPreparedRegeneration(runId, prepared, onDelta)

    override suspend fun regenerateImage(
        sessionId: String,
        messageId: String,
        attachmentId: String,
    ): ChatDraft {
        val regenerated = mediaCoordinator.regenerateImage(sessionId, messageId, attachmentId)
        uiPreferences.restoreChatEntry(sessionId)
        return regenerated
    }

    override suspend fun replaceChatVariableState(sessionId: String, stateJson: String): ChatDraft {
        return storyStateCoordinator.replaceVariableState(sessionId, stateJson)
    }

    override suspend fun resetChatVariableState(sessionId: String): ChatDraft {
        return storyStateCoordinator.resetVariableState(sessionId)
    }

    override suspend fun selectChatOpening(
        sessionId: String,
        openingOptionId: String,
    ): ChatDraft = storyStateCoordinator.selectOpening(sessionId, openingOptionId)

    override fun cancelStream(runId: String): Boolean = generationRunCoordinator.cancelStream(runId)

    override suspend fun setHistorySaveMode(mode: String): UiPreferences {
        return uiPreferences.setHistorySaveMode(mode)
    }

    override fun saveCharacterChatBackground(
        characterId: String,
        backgroundFile: File?,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): CharacterSlot = mediaCoordinator.saveCharacterChatBackground(
        characterId = characterId,
        backgroundFile = backgroundFile,
        opacity = opacity,
        blur = blur,
        scrim = scrim,
    )

    override fun restoreCharacterChatBackgroundDefault(characterId: String): CharacterSlot =
        mediaCoordinator.restoreCharacterChatBackgroundDefault(characterId)

    override fun useCharacterCardChatBackground(characterId: String): CharacterSlot =
        mediaCoordinator.useCharacterCardChatBackground(characterId)

    override fun useCustomChatBackground(characterId: String): CharacterSlot =
        mediaCoordinator.useCustomChatBackground(characterId)

    override fun useGlobalChatBackground(characterId: String): CharacterSlot =
        mediaCoordinator.useGlobalChatBackground(characterId)

    override fun applyGlobalChatBackground(sourceCharacterId: String): CharacterSlot =
        mediaCoordinator.applyGlobalChatBackground(sourceCharacterId)

    override suspend fun saveGlobalChatBackground(
        backgroundFile: File?,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): AppearanceTheme = mediaCoordinator.saveGlobalChatBackground(
        backgroundFile = backgroundFile,
        opacity = opacity,
        blur = blur,
        scrim = scrim,
    )

    override suspend fun clearGlobalChatBackground(): AppearanceTheme =
        mediaCoordinator.clearGlobalChatBackground()

    internal fun chatList(): List<ChatListItem> = sessions.chatList()

    internal suspend fun refreshChatDraft(sessionId: String): ChatDraft {
        return draftFromSession(sessionCoordinator.loadChat(sessionId, touch = false))
    }

    private fun characterAgent(): CharacterAgentGenerationService {
        return characterAgentGeneration
            ?: throw ElecKoiDataException("角色 Agent 运行时尚未初始化")
    }

    private fun agentRuns(): AgentRunManager {
        return agentRunManager
            ?: throw ElecKoiDataException("后台 Agent 运行管理器尚未初始化")
    }

    private fun draftFromSession(
        session: ChatSession,
        config: ModelConfig? = null,
    ): ChatDraft = draftProjector.project(session, config)

}
