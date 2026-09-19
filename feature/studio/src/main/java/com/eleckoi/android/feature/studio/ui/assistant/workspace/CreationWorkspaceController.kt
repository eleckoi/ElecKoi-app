package com.eleckoi.android.feature.studio.ui.assistant.workspace

import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.feature.studio.api.CreatorAssistantService
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import com.eleckoi.android.feature.studio.ui.assistant.latestCreationRuntimeThreadId
import com.eleckoi.android.feature.studio.ui.assistant.session.CreationGenerationStatsController
import com.eleckoi.android.feature.studio.ui.assistant.session.creationAssistantMessage
import com.eleckoi.android.feature.studio.ui.assistant.timeline.CreationHistoryController
import com.eleckoi.android.feature.studio.ui.assistant.timeline.replaceWorkspace
import com.eleckoi.android.feature.studio.ui.assistant.timeline.toUiTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the selected creator workspace and its conversation lifecycle.
 *
 * Character-root editing lives here as well because those mutations are scoped to the selected
 * workspace. The ViewModel remains an intent router and state owner; this controller only applies
 * changes through the supplied state reducer.
 */
internal class CreationWorkspaceController(
    private val scope: CoroutineScope,
    private val creatorService: CreatorAssistantService,
    private val workspaceUseCase: CreationWorkspaceUseCase,
    private val fileEditingController: CreationFileEditingController,
    private val historyController: CreationHistoryController,
    private val cancelSessionTurn: () -> Unit,
    private val detachSession: () -> Job?,
    private val state: () -> AiCreationAssistantUiState,
    private val updateState: ((AiCreationAssistantUiState) -> AiCreationAssistantUiState) -> Unit,
    private val prewarmTimeline: (String, List<CreationTimelineItem>) -> Unit,
    private val persistCurrentConversationSnapshot: suspend () -> Unit,
    private val generationStats: CreationGenerationStatsController,
) {
    private var initialized = false
    private var loadJob: Job? = null
    private val characterRoots = CreationCharacterRootController(
        scope = scope,
        creatorService = creatorService,
        state = state,
        updateState = updateState,
    )

    fun load(previousWorkspaceId: String?) {
        if (initialized || loadJob?.isActive == true) return
        initialized = true
        updateState { it.copy(isLoading = true, isRunning = false, pendingApprovals = emptyList()) }
        loadJob = scope.launch {
            runCatching {
                workspaceUseCase.load(state().workspace?.id ?: previousWorkspaceId)
            }.onSuccess { result ->
                result.workspace?.let { creatorService.setLastCreatorWorkspaceId(it.id) }
                val initialTimeline = result.conversation?.timeline?.toUiTimeline().orEmpty()
                updateState {
                    it.copy(
                        characterId = result.workspace?.linkedCharacterId.orEmpty(),
                        characterName = result.workspace?.name.orEmpty(),
                        workspaces = result.workspaces,
                        pinnedWorkspaceIds = result.pinnedWorkspaceIds,
                        workspaceExpansionOverrides = result.workspaceExpansionOverrides,
                        workspace = result.workspace,
                        conversation = result.conversation,
                        projectDirectory = result.projectDirectory,
                        files = result.files,
                        previewEntryFile = result.previewEntryFile,
                        modelLabel = result.modelLabel,
                        selectedModelConfigId = result.selectedModelConfigId,
                        selectedModelId = result.selectedModelId,
                        modelChoices = result.modelChoices,
                        permissionMode = result.workspace?.permissionMode
                            ?: com.eleckoi.android.engine.agent.api.AgentPermissionMode.AskForApproval,
                        generationStats = ChatSessionGenerationStats(),
                        timeline = initialTimeline,
                        historyHasMore = false,
                        historyPageLoading = result.conversation != null,
                        isLoading = false,
                    )
                }
                result.conversation?.let { conversation ->
                    generationStats.prepareConversation(conversation.id)
                    generationStats.restore(
                        conversation.id,
                        initialTimeline.latestCreationRuntimeThreadId(),
                    )
                    prewarmTimeline(conversation.id, initialTimeline)
                    result.workspace?.let { workspace ->
                        historyController.loadInitial(workspace.id, conversation.id)
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                initialized = false
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.creationAssistantMessage("加载创作项目失败"),
                    )
                }
            }
        }
    }

    fun loadCharacterDirectory(query: String, reset: Boolean) {
        characterRoots.loadDirectory(query, reset)
    }

    fun onCharacterDirectoryQueryChanged(value: String) {
        characterRoots.onDirectoryQueryChanged(value)
    }

    fun mutateCharacterRoots(
        failureMessage: String,
        mutation: suspend (workspaceId: String) -> CreatorWorkspace,
    ) {
        characterRoots.mutateRoots(failureMessage, mutation)
    }

    fun createAndAttachCharacter(name: String, group: String) {
        characterRoots.createAndAttach(name, group)
    }

    fun createWorkspace(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return
        scope.launch {
            runCatching { creatorService.createCreatorWorkspace(normalizedName, linkedCharacterId = null) }
                .onSuccess { workspace ->
                    updateState {
                        it.copy(workspaces = listOf(workspace) + it.workspaces.filterNot { item -> item.id == workspace.id })
                    }
                    workspace.activeConversationId?.let { selectConversation(workspace.id, it) }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(errorMessage = error.creationAssistantMessage("新建项目失败"))
                    }
                }
        }
    }

    fun renameWorkspace(workspaceId: String, name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return
        scope.launch {
            runCatching { creatorService.renameCreatorWorkspace(workspaceId, normalizedName) }
                .onSuccess { renamed ->
                    updateState { current ->
                        current.copy(
                            workspaces = current.workspaces.map { if (it.id == renamed.id) renamed else it },
                            workspace = if (current.workspace?.id == renamed.id) renamed else current.workspace,
                            characterName = if (current.workspace?.id == renamed.id) renamed.name else current.characterName,
                        )
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(errorMessage = error.creationAssistantMessage("重命名项目失败"))
                    }
                }
        }
    }

    fun togglePinnedWorkspace(workspaceId: String) {
        if (state().workspaces.none { it.id == workspaceId }) return
        val previous = state().pinnedWorkspaceIds
        val next = if (workspaceId in previous) previous - workspaceId else listOf(workspaceId) + previous
        updateState { it.copy(pinnedWorkspaceIds = next) }
        scope.launch {
            runCatching { creatorService.setPinnedCreatorWorkspaceIds(next) }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            pinnedWorkspaceIds = previous,
                            errorMessage = error.creationAssistantMessage("保存项目置顶失败"),
                        )
                    }
                }
        }
    }

    fun toggleWorkspaceExpanded(workspaceId: String) {
        val snapshot = state()
        if (snapshot.workspaces.none { it.id == workspaceId }) return
        val previous = snapshot.workspaceExpansionOverrides
        val currentlyExpanded = previous[workspaceId] ?: (snapshot.workspace?.id == workspaceId)
        val next = previous + (workspaceId to !currentlyExpanded)
        updateState { it.copy(workspaceExpansionOverrides = next) }
        scope.launch {
            runCatching { creatorService.setCreatorWorkspaceExpansionOverrides(next) }
                .onFailure { error ->
                    updateState { current ->
                        if (current.workspaceExpansionOverrides == next) {
                            current.copy(
                                workspaceExpansionOverrides = previous,
                                errorMessage = error.creationAssistantMessage("保存项目折叠状态失败"),
                            )
                        } else {
                            current.copy(errorMessage = error.creationAssistantMessage("保存项目折叠状态失败"))
                        }
                    }
                }
        }
    }

    fun createConversation(workspaceId: String) {
        if (state().isRunning) {
            updateState { it.copy(errorMessage = "请先停止当前任务，再新建对话") }
            return
        }
        scope.launch {
            runCatching { creatorService.createCreatorConversation(workspaceId) }
                .onSuccess { updated ->
                    updateState { current ->
                        current.copy(workspaces = current.workspaces.replaceWorkspace(updated))
                    }
                    updated.activeConversationId?.let { selectConversation(updated.id, it) }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(errorMessage = error.creationAssistantMessage("新建对话失败"))
                    }
                }
        }
    }

    fun selectConversation(workspaceId: String, conversationId: String) {
        val selectedWorkspace = state().workspaces.firstOrNull { it.id == workspaceId } ?: return
        val selectedConversation = selectedWorkspace.conversations.firstOrNull { it.id == conversationId } ?: return
        if (state().workspace?.id == workspaceId && state().conversation?.id == conversationId) return
        if (state().isRunning) {
            updateState { it.copy(errorMessage = "请先停止当前任务，再切换对话") }
            return
        }

        discardPendingInputImages()
        historyController.rememberCurrentTimeline()
        scope.launch { persistCurrentConversationSnapshot() }
        loadJob?.cancel()
        cancelSessionTurn()
        val previousSessionShutdown = detachSession()
        val projectChanged = state().workspace?.id != workspaceId
        val selectedTimeline = selectedConversation.timeline.toUiTimeline()
        updateState { current ->
            val selectedPermissionMode = if (current.workspace?.id == workspaceId) {
                current.permissionMode
            } else {
                selectedWorkspace.permissionMode
            }
            current.copy(
                characterId = selectedWorkspace.linkedCharacterId.orEmpty(),
                characterName = selectedWorkspace.name,
                workspace = selectedWorkspace,
                conversation = selectedConversation,
                projectDirectory = if (projectChanged) null else current.projectDirectory,
                files = if (projectChanged) emptyList() else current.files,
                previewEntryFile = if (projectChanged) null else current.previewEntryFile,
                timeline = selectedTimeline,
                historyHasMore = false,
                historyPageLoading = true,
                permissionMode = selectedPermissionMode,
                generationStats = ChatSessionGenerationStats(),
                isLoading = projectChanged,
                isRunning = false,
                pendingApprovals = emptyList(),
                editingUserMessage = null,
                editInput = "",
                selectedFilePath = null,
                fileDraft = "",
                fileDraftDirty = false,
            )
        }
        generationStats.prepareConversation(conversationId)
        loadJob = scope.launch {
            previousSessionShutdown?.join()
            generationStats.restore(
                conversationId,
                selectedTimeline.latestCreationRuntimeThreadId(),
            )
            runCatching {
                val updated = creatorService.selectCreatorConversation(workspaceId, conversationId)
                val details = if (projectChanged) fileEditingController.loadWorkspaceDetails(updated) else null
                updated to details
            }.onSuccess { (updated, details) ->
                creatorService.setLastCreatorWorkspaceId(updated.id)
                updateState { current ->
                    if (current.conversation?.id != conversationId) return@updateState current
                    current.copy(
                        workspace = updated,
                        workspaces = current.workspaces.replaceWorkspace(updated),
                        permissionMode = if (projectChanged) updated.permissionMode else current.permissionMode,
                        projectDirectory = details?.projectDirectory ?: current.projectDirectory,
                        files = details?.files ?: current.files,
                        previewEntryFile = details?.previewEntryFile ?: current.previewEntryFile,
                        isLoading = false,
                    )
                }
                historyController.loadInitial(updated.id, conversationId)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.creationAssistantMessage("打开对话失败"),
                    )
                }
            }
        }
    }

    fun renameConversation(workspaceId: String, conversationId: String, title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return
        scope.launch {
            runCatching {
                creatorService.renameCreatorConversation(workspaceId, conversationId, normalizedTitle)
            }.onSuccess { updated ->
                updateState { current ->
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
            }.onFailure { error ->
                updateState {
                    it.copy(errorMessage = error.creationAssistantMessage("重命名对话失败"))
                }
            }
        }
    }

    fun deleteConversation(workspaceId: String, conversationId: String) {
        if (state().isRunning && state().conversation?.id == conversationId) {
            updateState { it.copy(errorMessage = "请先停止当前任务，再删除对话") }
            return
        }
        val deletingActive = state().conversation?.id == conversationId
        if (deletingActive) detachSession()
        scope.launch {
            runCatching { creatorService.deleteCreatorConversation(workspaceId, conversationId) }
                .onSuccess { updated ->
                    generationStats.deleteConversation(conversationId)
                    historyController.removeConversation(conversationId)
                    val next = updated.conversations.firstOrNull { it.id == updated.activeConversationId }
                    updateState { current ->
                        current.copy(
                            workspaces = current.workspaces.replaceWorkspace(updated),
                            workspace = if (current.workspace?.id == updated.id) updated else current.workspace,
                            conversation = if (deletingActive) next else current.conversation,
                            timeline = if (deletingActive) next?.timeline?.toUiTimeline().orEmpty() else current.timeline,
                            pendingApprovals = if (deletingActive) emptyList() else current.pendingApprovals,
                            generationStats = if (deletingActive) {
                                ChatSessionGenerationStats()
                            } else {
                                current.generationStats
                            },
                            historyHasMore = if (deletingActive) false else current.historyHasMore,
                            historyPageLoading = if (deletingActive) next != null else current.historyPageLoading,
                        )
                    }
                    if (deletingActive && next != null) {
                        val nextTimeline = next.timeline.toUiTimeline()
                        generationStats.prepareConversation(next.id)
                        generationStats.restore(
                            next.id,
                            nextTimeline.latestCreationRuntimeThreadId(),
                        )
                        historyController.loadInitial(updated.id, next.id)
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(errorMessage = error.creationAssistantMessage("删除对话失败"))
                    }
                }
        }
    }

    fun deleteWorkspace(workspaceId: String) {
        if (state().isRunning && state().workspace?.id == workspaceId) {
            updateState { it.copy(errorMessage = "请先停止当前任务，再删除项目") }
            return
        }
        val deletingCurrent = state().workspace?.id == workspaceId
        val deletingConversationIds = state().workspaces
            .firstOrNull { it.id == workspaceId }
            ?.conversations
            .orEmpty()
            .map { it.id }
        if (deletingCurrent) detachSession()
        scope.launch {
            runCatching { creatorService.deleteCreatorWorkspace(workspaceId) }
                .onSuccess {
                    generationStats.deleteConversations(deletingConversationIds)
                    val remaining = state().workspaces.filterNot { it.id == workspaceId }
                    val nextPinned = state().pinnedWorkspaceIds - workspaceId
                    val nextExpansionOverrides = state().workspaceExpansionOverrides - workspaceId
                    creatorService.setPinnedCreatorWorkspaceIds(nextPinned)
                    creatorService.setCreatorWorkspaceExpansionOverrides(nextExpansionOverrides)
                    updateState { current ->
                        current.copy(
                            workspaces = remaining,
                            pinnedWorkspaceIds = nextPinned,
                            workspaceExpansionOverrides = nextExpansionOverrides,
                            workspace = if (deletingCurrent) null else current.workspace,
                            conversation = if (deletingCurrent) null else current.conversation,
                            projectDirectory = if (deletingCurrent) null else current.projectDirectory,
                            files = if (deletingCurrent) emptyList() else current.files,
                            previewEntryFile = if (deletingCurrent) null else current.previewEntryFile,
                            timeline = if (deletingCurrent) emptyList() else current.timeline,
                            generationStats = if (deletingCurrent) {
                                ChatSessionGenerationStats()
                            } else {
                                current.generationStats
                            },
                        )
                    }
                    if (deletingCurrent) {
                        remaining.firstOrNull()?.let { next ->
                            val conversation = next.conversations.firstOrNull { it.id == next.activeConversationId }
                                ?: next.conversations.firstOrNull()
                            conversation?.let { selectConversation(next.id, it.id) }
                        }
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(errorMessage = error.creationAssistantMessage("删除项目失败"))
                    }
                }
        }
    }

    private fun discardPendingInputImages() {
        val images = state().inputImages
        if (images.isEmpty()) return
        updateState { it.copy(inputImages = emptyList(), isPreparingInputImages = false) }
        scope.launch(Dispatchers.IO) {
            images.forEach(creatorService::discardCreatorInputImage)
        }
    }

    fun clear() {
        loadJob?.cancel()
        characterRoots.clear()
    }
}
