package com.eleckoi.android.feature.studio.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.engine.agent.api.AgentSessionFactory
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.supportsImageInput
import com.eleckoi.android.engine.immersive.api.FrontendProjectService
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import com.eleckoi.android.engine.workspace.runtime.model.RuntimeMaintenanceOperation
import com.eleckoi.android.feature.chat.data.MaxChatInputImages
import com.eleckoi.android.feature.chat.data.MaxChatInputMessageImageBytes
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineKind
import com.eleckoi.android.feature.conversation.timeline.ui.prewarmCreationTimelineItems
import com.eleckoi.android.feature.modelconfig.api.ModelService
import com.eleckoi.android.feature.studio.api.CreatorAssistantService
import com.eleckoi.android.feature.studio.api.creatorConversationAttachmentAssetId
import com.eleckoi.android.feature.studio.ui.assistant.runtime.CreationRuntimeController
import com.eleckoi.android.feature.studio.ui.assistant.session.CreationAgentSessionCoordinator
import com.eleckoi.android.feature.studio.ui.assistant.timeline.CreationHistoryController
import com.eleckoi.android.feature.studio.ui.assistant.timeline.replaceWorkspace
import com.eleckoi.android.feature.studio.ui.assistant.timeline.toStoredTimeline
import com.eleckoi.android.feature.studio.ui.assistant.workspace.CreationFileEditingController
import com.eleckoi.android.feature.studio.ui.assistant.workspace.CreationPublicationController
import com.eleckoi.android.feature.studio.ui.assistant.workspace.CreationWorkspaceController
import com.eleckoi.android.feature.studio.ui.assistant.workspace.CreationWorkspaceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiCreationAssistantViewModel(
    private val creatorService: CreatorAssistantService,
    frontendProjectService: FrontendProjectService,
    modelService: ModelService,
    agentSessionFactory: AgentSessionFactory,
    agentRuns: AgentRunManager,
    localRuntime: LocalRuntimeGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiCreationAssistantUiState())
    val uiState: StateFlow<AiCreationAssistantUiState> = _uiState.asStateFlow()

    private val historyController = CreationHistoryController(
        scope = viewModelScope,
        creatorService = creatorService,
        updateState = { transform -> _uiState.update(transform) },
        prewarm = ::prewarmCreationTimeline,
    )
    private val fileEditingController = CreationFileEditingController(
        scope = viewModelScope,
        creatorService = creatorService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
    )
    private val workspaceUseCase = CreationWorkspaceUseCase(
        creatorService = creatorService,
        files = fileEditingController,
    )
    private val publicationController = CreationPublicationController(
        scope = viewModelScope,
        creatorService = creatorService,
        frontendProjectService = frontendProjectService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
    )
    private val sessionCoordinator = CreationAgentSessionCoordinator(
        creatorService = creatorService,
        agentSessionFactory = agentSessionFactory,
        agentRuns = agentRuns,
        uiState = _uiState,
        scope = viewModelScope,
        refreshWorkspaceFiles = fileEditingController::refreshWorkspaceFiles,
        rememberCurrentTimeline = historyController::rememberCurrentTimeline,
        setTimelineMutationActive = historyController::setTimelineMutationActive,
        persistCurrentConversationSnapshot = ::persistCurrentConversationSnapshot,
        renameConversation = ::renameConversation,
    )
    private val workspaceController = CreationWorkspaceController(
        scope = viewModelScope,
        creatorService = creatorService,
        workspaceUseCase = workspaceUseCase,
        fileEditingController = fileEditingController,
        historyController = historyController,
        cancelSessionTurn = sessionCoordinator::cancelTurn,
        detachSession = sessionCoordinator::detachAndScheduleShutdown,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        prewarmTimeline = ::prewarmCreationTimeline,
        persistCurrentConversationSnapshot = ::persistCurrentConversationSnapshot,
    )
    private val runtimeController = CreationRuntimeController(
        scope = viewModelScope,
        runtime = localRuntime,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        detachSession = sessionCoordinator::detachAndScheduleShutdown,
    )
    private val modelController = CreationModelController(
        scope = viewModelScope,
        modelService = modelService,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        detachSession = sessionCoordinator::detachAndScheduleShutdown,
    )

    init {
        runtimeController.start()
        modelController.start()
    }

    fun onIntent(intent: AiCreationAssistantIntent) {
        when (intent) {
            AiCreationAssistantIntent.Load -> {
                runtimeController.connect()
                workspaceController.load(_uiState.value.workspace?.id)
            }
            is AiCreationAssistantIntent.CreateWorkspace ->
                workspaceController.createWorkspace(intent.name)
            is AiCreationAssistantIntent.RenameWorkspace ->
                workspaceController.renameWorkspace(intent.workspaceId, intent.name)
            is AiCreationAssistantIntent.TogglePinnedWorkspace ->
                workspaceController.togglePinnedWorkspace(intent.workspaceId)
            is AiCreationAssistantIntent.ToggleWorkspaceExpanded ->
                workspaceController.toggleWorkspaceExpanded(intent.workspaceId)
            is AiCreationAssistantIntent.CreateConversation ->
                workspaceController.createConversation(intent.workspaceId)
            is AiCreationAssistantIntent.SelectConversation ->
                workspaceController.selectConversation(intent.workspaceId, intent.conversationId)
            AiCreationAssistantIntent.LoadOlderTimeline -> historyController.loadOlder()
            is AiCreationAssistantIntent.RenameConversation -> workspaceController.renameConversation(
                intent.workspaceId,
                intent.conversationId,
                intent.title,
            )
            is AiCreationAssistantIntent.DeleteConversation ->
                workspaceController.deleteConversation(intent.workspaceId, intent.conversationId)
            is AiCreationAssistantIntent.DeleteWorkspace ->
                workspaceController.deleteWorkspace(intent.workspaceId)
            is AiCreationAssistantIntent.ChangePermissionMode ->
                sessionCoordinator.updatePermissionMode(intent.value)
            is AiCreationAssistantIntent.ChangeModel ->
                modelController.change(intent.configId, intent.modelId)
            is AiCreationAssistantIntent.ChangeInput -> _uiState.update {
                it.copy(input = intent.value.take(InputLimit))
            }
            is AiCreationAssistantIntent.AddInputImages -> addInputImages(intent.uriValues)
            is AiCreationAssistantIntent.RemoveInputImage -> removeInputImage(intent.imageId)
            is AiCreationAssistantIntent.OpenUserMessageEditor -> openUserMessageEditor(intent.message)
            is AiCreationAssistantIntent.ChangeEditInput -> _uiState.update {
                it.copy(editInput = intent.value.take(InputLimit))
            }
            AiCreationAssistantIntent.CloseUserMessageEditor -> closeUserMessageEditor()
            AiCreationAssistantIntent.SubmitEditedUserMessage -> submitEditedUserMessage()
            AiCreationAssistantIntent.LoadCharacterDirectory -> workspaceController.loadCharacterDirectory(
                query = "",
                reset = true,
            )
            is AiCreationAssistantIntent.ChangeCharacterDirectoryQuery ->
                workspaceController.onCharacterDirectoryQueryChanged(intent.value)
            AiCreationAssistantIntent.LoadMoreCharacters -> workspaceController.loadCharacterDirectory(
                query = _uiState.value.characterDirectoryQuery,
                reset = false,
            )
            is AiCreationAssistantIntent.AttachCharacter ->
                workspaceController.mutateCharacterRoots("添加参考角色失败") { workspaceId ->
                    creatorService.attachCreatorCharacter(
                        workspaceId = workspaceId,
                        characterId = intent.characterId,
                        access = CreatorWorkspaceRootAccess.ReadOnly,
                        makePrimary = false,
                    )
                }
            is AiCreationAssistantIntent.DetachCharacterRoot ->
                workspaceController.mutateCharacterRoots("移除角色失败") { workspaceId ->
                    creatorService.detachCreatorCharacter(workspaceId, intent.rootId)
                }
            is AiCreationAssistantIntent.SetPrimaryCharacterRoot ->
                workspaceController.mutateCharacterRoots("设置主角色失败") { workspaceId ->
                    creatorService.setPrimaryCreatorCharacter(workspaceId, intent.rootId)
                }
            is AiCreationAssistantIntent.SetCharacterRootAccess ->
                workspaceController.mutateCharacterRoots("修改角色权限失败") { workspaceId ->
                    creatorService.setCreatorCharacterAccess(workspaceId, intent.rootId, intent.access)
                }
            is AiCreationAssistantIntent.CreateAndAttachCharacter ->
                workspaceController.createAndAttachCharacter(intent.name, intent.group)
            AiCreationAssistantIntent.Send -> sessionCoordinator.send()
            AiCreationAssistantIntent.RegenerateLatest -> sessionCoordinator.regenerateLatest()
            AiCreationAssistantIntent.Stop -> sessionCoordinator.stop()
            AiCreationAssistantIntent.InstallRuntime ->
                runtimeController.maintain(RuntimeMaintenanceOperation.Install)
            AiCreationAssistantIntent.UpdateRuntime ->
                runtimeController.maintain(RuntimeMaintenanceOperation.Update)
            AiCreationAssistantIntent.RepairRuntime ->
                runtimeController.maintain(RuntimeMaintenanceOperation.Repair)
            AiCreationAssistantIntent.UninstallRuntime ->
                runtimeController.maintain(RuntimeMaintenanceOperation.Uninstall)
            AiCreationAssistantIntent.RefreshRuntime -> runtimeController.refresh()
            AiCreationAssistantIntent.CancelInstall -> runtimeController.cancelInstallation()
            is AiCreationAssistantIntent.ResolveApproval ->
                sessionCoordinator.resolveApproval(intent.requestId, intent.decision)
            AiCreationAssistantIntent.Publish -> publicationController.publish()
            is AiCreationAssistantIntent.OpenFile -> fileEditingController.open(intent.path)
            AiCreationAssistantIntent.CloseFile -> fileEditingController.close()
            is AiCreationAssistantIntent.ChangeFileDraft -> fileEditingController.changeDraft(intent.value)
            AiCreationAssistantIntent.SaveFile -> fileEditingController.save()
            AiCreationAssistantIntent.UndoLastAiChanges -> fileEditingController.undoLastAiChanges()
            AiCreationAssistantIntent.DismissMessage ->
                _uiState.update { it.copy(notice = "", errorMessage = "") }
        }
    }

    private fun openUserMessageEditor(message: CreationTimelineItem) {
        val snapshot = _uiState.value
        val rejectedReason = when {
            snapshot.isRunning -> "running"
            message.kind != CreationTimelineKind.User -> "not-user"
            message.text.isBlank() -> "blank"
            else -> null
        }
        if (rejectedReason != null) {
            return
        }
        _uiState.update {
            it.copy(
                editingUserMessage = message,
                editInput = message.text,
            )
        }
    }

    private fun closeUserMessageEditor() {
        _uiState.update { it.copy(editingUserMessage = null, editInput = "") }
    }

    private fun submitEditedUserMessage() {
        val snapshot = _uiState.value
        val target = snapshot.editingUserMessage ?: return
        val replacement = snapshot.editInput.trim()
        if (replacement.isEmpty()) return
        if (sessionCoordinator.regenerateUserMessage(target.id, replacement)) {
            closeUserMessageEditor()
        }
    }

    private fun addInputImages(uriValues: List<String>) {
        val snapshot = _uiState.value
        val workspaceId = snapshot.workspace?.id ?: return
        if (snapshot.isRunning || snapshot.isPreparingInputImages || uriValues.isEmpty()) return
        val selectedConfig = snapshot.modelConfigs.firstOrNull {
            it.id == snapshot.selectedModelConfigId
        }
        if (selectedConfig?.supportsImageInput(snapshot.selectedModelId) != true) {
            _uiState.update { it.copy(errorMessage = "请先在当前模型设置中开启图片输入") }
            return
        }
        val remaining = (MaxChatInputImages - snapshot.inputImages.size).coerceAtLeast(0)
        if (remaining == 0) {
            _uiState.update { it.copy(errorMessage = "每条消息最多发送 $MaxChatInputImages 张图片") }
            return
        }
        _uiState.update { it.copy(isPreparingInputImages = true, errorMessage = "") }
        viewModelScope.launch {
            val prepared = try {
                withContext(Dispatchers.IO) {
                    creatorService.prepareCreatorInputImages(uriValues.take(remaining))
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isPreparingInputImages = false,
                        errorMessage = error.message ?: "读取图片失败",
                    )
                }
                return@launch
            }
            val current = _uiState.value
            val stillOwnsSelection =
                current.workspace?.id == workspaceId &&
                    current.conversation?.id == snapshot.conversation?.id &&
                    !current.isRunning
            if (!stillOwnsSelection) {
                withContext(Dispatchers.IO) {
                    prepared.forEach(creatorService::discardCreatorInputImage)
                }
                _uiState.update { it.copy(isPreparingInputImages = false) }
                return@launch
            }
            val combined = current.inputImages + prepared
            if (combined.sumOf { it.bytes } > MaxChatInputMessageImageBytes) {
                withContext(Dispatchers.IO) {
                    prepared.forEach(creatorService::discardCreatorInputImage)
                }
                _uiState.update {
                    it.copy(
                        isPreparingInputImages = false,
                        errorMessage = "每条消息的图片总大小不能超过 20 MiB",
                    )
                }
                return@launch
            }
            val referenced = prepared.map { image ->
                image.copy(creatorMediaReference = creatorConversationAttachmentAssetId(image.id))
            }
            val latest = _uiState.value
            val stillCurrent = latest.workspace?.id == workspaceId &&
                latest.conversation?.id == snapshot.conversation?.id &&
                !latest.isRunning
            if (!stillCurrent) {
                withContext(Dispatchers.IO) {
                    prepared.forEach(creatorService::discardCreatorInputImage)
                }
                _uiState.update { it.copy(isPreparingInputImages = false) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    inputImages = it.inputImages + referenced,
                    isPreparingInputImages = false,
                    errorMessage = "",
                )
            }
        }
    }

    private fun removeInputImage(imageId: String) {
        val image = _uiState.value.inputImages.firstOrNull { it.id == imageId } ?: return
        _uiState.update {
            it.copy(inputImages = it.inputImages.filterNot { candidate -> candidate.id == imageId })
        }
        viewModelScope.launch(Dispatchers.IO) {
            creatorService.discardCreatorInputImage(image)
        }
    }

    private fun prewarmCreationTimeline(
        conversationId: String,
        timeline: List<CreationTimelineItem>,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                prewarmCreationTimelineItems(
                    timeline = timeline,
                    conversationId = conversationId,
                )
            }
        }
    }

    private fun renameConversation(workspaceId: String, conversationId: String, title: String) {
        workspaceController.renameConversation(workspaceId, conversationId, title)
    }

    fun refreshModels(
        config: ModelConfig,
        onFinished: (Result<ModelConfig>) -> Unit = {},
    ) = modelController.refreshModels(config, onFinished)

    fun saveModelConfig(
        config: ModelConfig,
        onFinished: (Result<ModelConfig>) -> Unit = {},
    ) = modelController.saveModelConfig(config, onFinished)

    private suspend fun persistCurrentConversationSnapshot() {
        val snapshot = _uiState.value
        val workspaceId = snapshot.workspace?.id ?: return
        val conversationId = snapshot.conversation?.id ?: return
        val timeline = snapshot.timeline.toStoredTimeline()
        runCatching {
            creatorService.saveCreatorConversationTimeline(workspaceId, conversationId, timeline)
        }.onSuccess { updated ->
            _uiState.update { current ->
                current.copy(
                    workspaces = current.workspaces.replaceWorkspace(updated),
                    workspace = if (current.workspace?.id == updated.id) updated else current.workspace,
                    conversation = if (current.conversation?.id == conversationId) {
                        updated.conversations.firstOrNull { it.id == conversationId }
                    } else {
                        current.conversation
                    },
                )
            }
        }
    }

    override fun onCleared() {
        _uiState.value.inputImages.forEach(creatorService::discardCreatorInputImage)
        workspaceController.clear()
        sessionCoordinator.clear()
        super.onCleared()
    }

    companion object {
        private const val InputLimit = 12_000

        fun factory(
            creatorService: CreatorAssistantService,
            frontendProjectService: FrontendProjectService,
            modelService: ModelService,
            agentSessionFactory: AgentSessionFactory,
            agentRuns: AgentRunManager,
            localRuntime: LocalRuntimeGateway,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiCreationAssistantViewModel(
                    creatorService = creatorService,
                    frontendProjectService = frontendProjectService,
                    modelService = modelService,
                    agentSessionFactory = agentSessionFactory,
                    agentRuns = agentRuns,
                    localRuntime = localRuntime,
                ) as T
            }
        }
    }
}
