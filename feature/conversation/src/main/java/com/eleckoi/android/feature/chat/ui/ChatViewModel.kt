package com.eleckoi.android.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.sdk.author.AuthorChatGateway
import com.eleckoi.android.sdk.author.AuthorSendImageAttachment
import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryPage
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryReadOptions
import com.eleckoi.android.engine.immersive.api.FrontendProjectService
import com.eleckoi.android.engine.immersive.model.FrontendWorkspace
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatEncodedImageInput
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.prewarm.RecentChatPrewarmer
import com.eleckoi.android.engine.generation.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val chatService: ChatService,
    private val frontendProjectService: FrontendProjectService,
    initialAppearance: AppearanceTheme = AppearanceTheme(),
    private val isSettingLibraryToolEnabled: (characterId: String) -> Boolean = { true },
    private val enableSettingLibraryTool: suspend (characterId: String) -> Unit = {},
    private val readDshTrajectory: (String, DshTrajectoryReadOptions) -> DshTrajectoryPage =
        { runtimeThreadId, _ -> DshTrajectoryPage.empty(runtimeThreadId) },
) : ViewModel(), AuthorChatGateway {
    private val _uiState = MutableStateFlow(
        ChatUiState(
            appearance = initialAppearance,
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<ChatEffect>()
    val effects: SharedFlow<ChatEffect> = _effects.asSharedFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val frontendWorkspace: StateFlow<FrontendWorkspace> = uiState
        .map { state: ChatUiState -> state.chatCharacterId }
        .distinctUntilChanged()
        .flatMapLatest { characterId ->
            if (characterId.isBlank()) {
                flowOf(FrontendWorkspace(characterId))
            } else {
                frontendProjectService.frontendWorkspaceFlow(characterId)
                    .onStart { emit(FrontendWorkspace(characterId)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = FrontendWorkspace(""),
        )
    private val initialPreferencesReady = CompletableDeferred<Unit>()
    private val recentChatPrewarmer = RecentChatPrewarmer(
        scope = viewModelScope,
        chatService = chatService,
    )
    private val historyController = ChatHistoryController(
        scope = viewModelScope,
        chatService = chatService,
        updateState = { transform -> _uiState.update(transform) },
    )
    private val draftController = ChatDraftController(
        scope = viewModelScope,
        chatService = chatService,
        states = uiState,
        updateState = { transform -> _uiState.update(transform) },
        historyController = historyController,
        recentChatPrewarmer = recentChatPrewarmer,
        initialPreferencesReady = initialPreferencesReady,
    )
    private val settingsController = ChatSettingsController(
        scope = viewModelScope,
        chatService = chatService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        selectSession = draftController::selectSession,
    )
    private val backgroundController = ChatBackgroundController(
        scope = viewModelScope,
        chatService = chatService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
    )
    private val inputImageController = ChatInputImageController(
        scope = viewModelScope,
        chatService = chatService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
    )
    private val supportDataController = ChatSupportDataController(
        scope = viewModelScope,
        chatService = chatService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        clearSession = draftController::clearSession,
        resetPaging = historyController::resetPaging,
        updateRecentChats = recentChatPrewarmer::updateCatalog,
    )
    private val historyTransferController = ChatHistoryTransferController(
        scope = viewModelScope,
        chatService = chatService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        emitEffect = _effects::emit,
    )
    private val draftMutationController = ChatDraftMutationController(
        scope = viewModelScope,
        chatService = chatService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        isCurrentSession = draftController::isCurrent,
    )
    private val authorEventPublisher = ChatAuthorEventPublisher(
        scope = viewModelScope,
        states = uiState,
    )
    private val generationCoordinator = ChatGenerationCoordinator(
        scope = viewModelScope,
        chatService = chatService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        onStopRequested = authorEventPublisher::markStopRequested,
        onMessagesChanged = authorEventPublisher::publishMessagesChanged,
        onGenerationCompleted = ::inspectRequiredTools,
    )
    private val authorGateway = ChatAuthorGatewayAdapter(
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        actions = ChatAuthorActions(
            send = ::startSending,
            stopGeneration = ::stopSending,
            regenerate = ::regenerateFrom,
            submitEditedMessage = ::submitEditedMessage,
            createChat = ::createChat,
            openChat = ::loadDraft,
            deleteChat = ::deleteHistoryChat,
            selectModel = settingsController::selectModel,
            selectOpening = { sessionId, openingOptionId ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        chatService.selectChatOpening(sessionId, openingOptionId)
                    }
                }
            },
            replaceVariableState = { sessionId, stateJson ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        chatService.replaceChatVariableState(sessionId, stateJson)
                    }
                }
            },
            resetVariableState = { sessionId ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        chatService.resetChatVariableState(sessionId)
                    }
                }
            },
            deleteMessagesFrom = { sessionId, messageId ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        chatService.deleteMessagesFrom(sessionId, messageId)
                    }
                }
            },
        ),
        publisher = authorEventPublisher,
    )
    override val authorEvents = authorGateway.authorEvents

    init {
        viewModelScope.launch {
            chatService.uiPreferencesFlow.collectLatest { preferences ->
                _uiState.update {
                    it.copy(
                        assistantBubbleEnabled = preferences.assistantBubbleEnabled,
                        chatLayoutMode = preferences.chatLayoutMode,
                        chatRoleplayCardPanel = preferences.chatRoleplayCardPanel,
                        chatBubbleWideLayout = preferences.chatBubbleWideLayout,
                        chatBubbleCornerRadius = preferences.chatBubbleCornerRadius,
                        chatAvatarSize = preferences.chatAvatarSize,
                        chatAvatarShape = preferences.resolvedChatAvatarShape,
                        chatNameFontSize = preferences.chatNameFontSize,
                        chatNameAvatarSpacing = preferences.chatNameAvatarSpacing,
                        chatAreaHorizontalPadding = preferences.chatAreaHorizontalPadding,
                        chatReplySpacing = preferences.chatReplySpacing,
                        chatTurnSpacing = preferences.chatTurnSpacing,
                        chatMessageFontSize = preferences.chatMessageFontSize,
                        chatLineHeightMultiplier = preferences.chatLineHeightMultiplier,
                        chatLetterSpacing = preferences.chatLetterSpacing,
                        chatParagraphSpacing = preferences.chatParagraphSpacing,
                        chatGenerationStatsEnabled = preferences.chatGenerationStatsEnabled,
                        historySaveMode = preferences.historySaveMode,
                    )
                }
                initialPreferencesReady.complete(Unit)
            }
        }
        supportDataController.start()
        draftController.start()
        authorEventPublisher.start()
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            ChatIntent.LoadInitialDraft -> loadInitialDraft()
            is ChatIntent.LoadDraft -> loadDraft(intent.sessionId)
            ChatIntent.LoadOlderMessages -> loadOlderMessages()
            is ChatIntent.OpenCharacterChat -> openCharacterChat(intent.characterId)
            is ChatIntent.ApplyAppearanceTheme -> applyAppearanceTheme(intent.theme)
            is ChatIntent.InputChanged -> setInput(intent.value)
            is ChatIntent.AddInputImages -> inputImageController.add(intent.uriValues)
            is ChatIntent.RemoveInputImage -> inputImageController.remove(intent.imageId)
            ChatIntent.SendMessage -> sendMessage()
            ChatIntent.StopSending -> stopSending()
            is ChatIntent.AcknowledgeGenerationPresentation -> _uiState.update { current ->
                if (current.generationPresentation?.generation == intent.generation) {
                    current.copy(generationPresentation = null)
                } else {
                    current
                }
            }
            ChatIntent.CreateChat -> createChat()
            is ChatIntent.CreateChatForCharacter -> createChat(intent.characterId)
            is ChatIntent.OpenEditMessage -> openEditMessage(intent.message)
            ChatIntent.CloseEditMessage -> closeEditMessage()
            is ChatIntent.EditInputChanged -> _uiState.update { it.copy(editInput = intent.value) }
            ChatIntent.SubmitEditedMessage -> submitEditedMessage()
            ChatIntent.SaveEditedMessage -> saveEditedMessage()
            ChatIntent.OpenDeleteMessages -> openDeleteMessages()
            ChatIntent.CloseDeleteMessages -> closeDeleteMessages()
            is ChatIntent.SelectDeleteFromMessage -> selectDeleteFromMessage(intent.messageId)
            ChatIntent.RequestDeleteMessages -> requestDeleteMessages()
            ChatIntent.DismissDeleteMessagesConfirmation -> _uiState.update {
                it.copy(deleteMessagesConfirmationOpen = false)
            }
            ChatIntent.ConfirmDeleteMessages -> confirmDeleteMessages()
            is ChatIntent.RegenerateFrom -> regenerateFrom(intent.message)
            is ChatIntent.RegenerateImage -> draftMutationController.regenerateImage(intent.messageId, intent.attachmentId)
            is ChatIntent.SelectOpeningOption -> draftMutationController.selectOpeningOption(intent.openingOptionId)
            is ChatIntent.ChangePermissionMode -> settingsController.updatePermissionMode(intent.mode)
            is ChatIntent.SelectModel -> settingsController.selectModel(intent.configId, intent.model)
            is ChatIntent.ChangeHistorySaveMode -> settingsController.changeHistorySaveMode(intent.mode)
            is ChatIntent.SaveChatBackground -> backgroundController.save(
                intent.backgroundFile,
                intent.opacity,
                intent.blur,
                intent.scrim,
                intent.global,
            )
            is ChatIntent.SetGlobalChatBackground -> backgroundController.setGlobal(
                intent.backgroundFile,
                intent.opacity,
                intent.blur,
                intent.scrim,
            )
            ChatIntent.UseAppDefaultChatBackground -> backgroundController.useAppDefault()
            ChatIntent.UseCharacterCardChatBackground -> backgroundController.useCharacterCard()
            ChatIntent.UseCustomChatBackground -> backgroundController.useCustom()
            ChatIntent.UseExistingGlobalChatBackground -> backgroundController.useExistingGlobal()
            is ChatIntent.DeleteHistoryChat -> deleteHistoryChat(intent.sessionId)
            is ChatIntent.ExportHistoryChats -> exportHistoryChats(intent.sessionIds)
            is ChatIntent.ImportHistoryChats -> importHistoryChats(intent.json)
            is ChatIntent.SetHistoryOpen -> _uiState.update { it.copy(historyOpen = intent.open) }
            is ChatIntent.SetModelPickerOpen -> _uiState.update { it.copy(modelPickerOpen = intent.open) }
            ChatIntent.DismissError -> _uiState.update { it.copy(errorMessage = "") }
            ChatIntent.DismissRequiredToolPrompt ->
                _uiState.update { it.copy(requiredToolPrompt = null) }
            ChatIntent.EnableRequiredSettingLibraryTool -> enableRequiredSettingLibraryTool()
            ChatIntent.DismissChatBackgroundError ->
                _uiState.update { it.copy(chatBackgroundErrorMessage = "") }
            is ChatIntent.ReportError -> _uiState.update { it.copy(errorMessage = intent.message) }
            ChatIntent.ToggleMoreTools -> _uiState.update { it.copy(moreToolsOpen = !it.moreToolsOpen) }
            ChatIntent.DismissMoreTools -> _uiState.update { it.copy(moreToolsOpen = false) }
        }
    }

    override fun snapshot() = authorGateway.snapshot()

    override fun setInput(value: String) = authorGateway.setInput(value)

    override suspend fun send(
        text: String,
        attachments: List<AuthorSendImageAttachment>,
    ) = authorGateway.send(text, attachments)

    override fun stopGeneration() = authorGateway.stopGeneration()

    override fun regenerate(messageId: String) = authorGateway.regenerate(messageId)

    override fun editAndRegenerate(messageId: String, text: String) =
        authorGateway.editAndRegenerate(messageId, text)

    override fun createNewChat(characterId: String) = authorGateway.createNewChat(characterId)

    override fun openChat(sessionId: String) = authorGateway.openChat(sessionId)

    override fun deleteChat(sessionId: String) = authorGateway.deleteChat(sessionId)

    override suspend fun deleteMessagesFrom(messageId: String) =
        authorGateway.deleteMessagesFrom(messageId)

    override fun selectModel(
        configId: String,
        model: String,
    ) = authorGateway.selectModel(configId, model)

    override suspend fun selectOpening(openingOptionId: String) =
        authorGateway.selectOpening(openingOptionId)

    override suspend fun replaceVariableState(stateJson: String) =
        authorGateway.replaceVariableState(stateJson)

    override suspend fun resetVariableState() = authorGateway.resetVariableState()

    fun applyAppearanceTheme(theme: AppearanceTheme) {
        _uiState.update { it.copy(appearance = theme) }
    }

    fun loadInitialDraft() = draftController.loadInitialDraft()

    fun loadDraft(sessionId: String) = draftController.loadDraft(sessionId)

    private fun loadOlderMessages() = historyController.loadOlderMessages()

    fun sendMessage() {
        val state = _uiState.value
        generationCoordinator.send(state.input, state.inputImages)
    }

    private fun inspectRequiredTools(draft: ChatDraft, assistantMessageId: String?) {
        val prompt = requiredSettingLibraryToolPrompt(
            draft = draft,
            assistantMessageId = assistantMessageId,
            settingLibraryToolEnabled = isSettingLibraryToolEnabled(draft.session.characterId),
        ) ?: return
        _uiState.update { current ->
            if (current.draft?.session?.id == draft.session.id) {
                current.copy(requiredToolPrompt = prompt)
            } else {
                current
            }
        }
    }

    private fun enableRequiredSettingLibraryTool() {
        val prompt = _uiState.value.requiredToolPrompt?.takeIf { !it.enabling } ?: return
        _uiState.update { current ->
            if (current.requiredToolPrompt?.assistantMessageId == prompt.assistantMessageId) {
                current.copy(requiredToolPrompt = prompt.copy(enabling = true))
            } else {
                current
            }
        }
        viewModelScope.launch {
            runCatching { enableSettingLibraryTool(prompt.characterId) }
                .onSuccess {
                    _uiState.update { current ->
                        if (current.requiredToolPrompt?.assistantMessageId == prompt.assistantMessageId) {
                            current.copy(requiredToolPrompt = null)
                        } else {
                            current
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        if (current.requiredToolPrompt?.assistantMessageId == prompt.assistantMessageId) {
                            current.copy(
                                requiredToolPrompt = null,
                                errorMessage = error.message ?: "开启角色设定库工具失败",
                            )
                        } else {
                            current
                        }
                    }
                }
        }
    }

    private suspend fun startSending(
        rawContent: String,
        attachments: List<AuthorSendImageAttachment>,
    ): Result<Unit> = runCatching {
        val inputImages = if (attachments.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                chatService.prepareEncodedInputImages(
                    attachments.map { attachment ->
                        ChatEncodedImageInput(
                            mediaType = attachment.mediaType,
                            data = attachment.data,
                            displayName = attachment.name,
                        )
                    },
                )
            }
        }
        withContext(Dispatchers.Main.immediate) {
            generationCoordinator.send(rawContent, inputImages)
        }
    }

    fun stopSending() {
        generationCoordinator.stop()
    }

    fun openEditMessage(message: ChatMessage) {
        val snapshot = _uiState.value
        val rejectedReason = when {
            message.role == MessageRole.System -> "system"
            message.pending -> "pending"
            snapshot.isSending -> "sending"
            else -> null
        }
        if (rejectedReason != null) {
            return
        }
        val sessionId = snapshot.draft?.session?.id ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { chatService.rawChatMessage(sessionId, message.id) }
            }.onSuccess { rawMessage ->
                if (rawMessage == null) return@onSuccess
                _uiState.update { current ->
                    if (
                        current.draft?.session?.id == sessionId &&
                        !current.isSending &&
                        rawMessage.role != MessageRole.System &&
                        !rawMessage.pending
                    ) {
                        current.copy(editingMessage = rawMessage, editInput = rawMessage.content)
                    } else {
                        current
                    }
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    if (current.draft?.session?.id == sessionId) {
                        current.copy(errorMessage = error.message ?: "读取原始消息失败")
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun closeEditMessage() {
        _uiState.update { current ->
            if (current.isSavingEditedMessage) current else {
                current.copy(editingMessage = null, editInput = "", isSavingEditedMessage = false)
            }
        }
    }

    fun submitEditedMessage() {
        generationCoordinator.submitEditedMessage()
    }

    fun saveEditedMessage() {
        generationCoordinator.saveEditedMessage()
    }

    private fun openDeleteMessages() {
        val snapshot = _uiState.value
        if (snapshot.isSending || snapshot.isDeletingMessages || snapshot.draft == null) return
        _uiState.update {
            it.copy(
                moreToolsOpen = false,
                deleteMessagesOpen = true,
                deleteFromMessageId = null,
                deleteMessagesConfirmationOpen = false,
            )
        }
    }

    private fun closeDeleteMessages() {
        _uiState.update { current ->
            if (current.isDeletingMessages) current else {
                current.copy(
                    deleteMessagesOpen = false,
                    deleteFromMessageId = null,
                    deleteMessagesConfirmationOpen = false,
                )
            }
        }
    }

    private fun selectDeleteFromMessage(messageId: String) {
        val snapshot = _uiState.value
        val message = snapshot.draft?.session?.messages?.firstOrNull { it.id == messageId } ?: return
        if (
            !snapshot.deleteMessagesOpen ||
            snapshot.isDeletingMessages ||
            message.role == MessageRole.System ||
            message.pending
        ) return
        _uiState.update { it.copy(deleteFromMessageId = messageId) }
    }

    private fun requestDeleteMessages() {
        _uiState.update { current ->
            if (current.deleteMessagesOpen && current.deleteFromMessageId != null && !current.isDeletingMessages) {
                current.copy(deleteMessagesConfirmationOpen = true)
            } else {
                current
            }
        }
    }

    private fun confirmDeleteMessages() {
        val snapshot = _uiState.value
        val messageId = snapshot.deleteFromMessageId ?: return
        if (!snapshot.deleteMessagesOpen || snapshot.isDeletingMessages || snapshot.isSending) return
        _uiState.update {
            it.copy(
                deleteMessagesConfirmationOpen = false,
                isDeletingMessages = true,
                errorMessage = "",
            )
        }
        viewModelScope.launch {
            runCatching { authorGateway.deleteMessagesFrom(messageId) }
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            deleteMessagesOpen = false,
                            deleteFromMessageId = null,
                            deleteMessagesConfirmationOpen = false,
                            isDeletingMessages = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isDeletingMessages = false,
                            errorMessage = error.message ?: "删除消息失败",
                        )
                    }
                }
        }
    }

    fun regenerateFrom(message: ChatMessage) {
        generationCoordinator.regenerateFrom(message)
    }

    fun frontendProjectDirectory(projectId: String) =
        frontendProjectService.frontendProjectDirectory(projectId)

    fun clearFrontendProject() {
        val characterId = _uiState.value.chatCharacterId
        if (characterId.isBlank()) return
        viewModelScope.launch {
            frontendProjectService.selectFrontendProject(characterId, null)
        }
    }

    fun createChat() {
        val state = _uiState.value
        val characterId = state.draft?.session?.characterId ?: state.chatCharacterId
        if (characterId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先选择角色") }
            return
        }
        createChat(characterId)
    }

    fun createChat(characterId: String) {
        if (characterId.isBlank()) return
        draftController.createChat(characterId)
    }

    fun openCharacterChat(characterId: String) {
        if (characterId.isBlank()) return
        draftController.openCharacterChat(characterId)
    }

    fun refreshCurrentDraft() = draftController.refreshCurrentDraft()

    fun refreshModels(config: ModelConfig, onFinished: (Result<ModelConfig>) -> Unit = {}) =
        settingsController.refreshModels(config, onFinished)

    fun saveModelConfig(config: ModelConfig, onFinished: (Result<ModelConfig>) -> Unit = {}) =
        settingsController.saveModelConfig(config, onFinished)

    fun saveCharacterImagePrompt(
        prompt: String,
        onFinished: (Result<String>) -> Unit = {},
    ) = draftMutationController.saveCharacterImagePrompt(prompt, onFinished)

    fun changeHistorySaveMode(mode: String) = settingsController.changeHistorySaveMode(mode)

    fun deleteHistoryChat(sessionId: String) = draftController.deleteHistoryChat(sessionId)

    fun exportHistoryChats(sessionIds: List<String>) =
        historyTransferController.export(sessionIds)

    fun importHistoryChats(json: String) = historyTransferController.import(json)

    suspend fun loadDshTrajectory(
        runtimeThreadId: String,
        options: DshTrajectoryReadOptions = DshTrajectoryReadOptions(),
    ): DshTrajectoryPage = withContext(Dispatchers.IO) {
        readDshTrajectory(runtimeThreadId, options)
    }

    companion object {
        fun factory(
            chatService: ChatService,
            frontendProjectService: FrontendProjectService,
            initialAppearance: AppearanceTheme = AppearanceTheme(),
            isSettingLibraryToolEnabled: (characterId: String) -> Boolean = { true },
            enableSettingLibraryTool: suspend (characterId: String) -> Unit = {},
            readDshTrajectory: (String, DshTrajectoryReadOptions) -> DshTrajectoryPage =
                { runtimeThreadId, _ -> DshTrajectoryPage.empty(runtimeThreadId) },
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        return ChatViewModel(
                            chatService,
                            frontendProjectService,
                            initialAppearance,
                            isSettingLibraryToolEnabled,
                            enableSettingLibraryTool,
                            readDshTrajectory,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
