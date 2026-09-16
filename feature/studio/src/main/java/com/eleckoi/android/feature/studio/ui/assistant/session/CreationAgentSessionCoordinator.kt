package com.eleckoi.android.feature.studio.ui.assistant.session

import com.eleckoi.android.engine.agent.api.AgentApprovalDecision
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.agent.api.AgentSession
import com.eleckoi.android.engine.agent.api.AgentSessionFactory
import com.eleckoi.android.engine.agent.api.AgentSessionState
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.engine.agent.background.AgentRunDescriptor
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.engine.agent.background.AgentRunSurface
import com.eleckoi.android.engine.generation.model.supportsImageInput
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.studio.api.CreatorAssistantService
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineKind
import com.eleckoi.android.feature.studio.ui.assistant.approval.CreationApprovalQueueReducer
import com.eleckoi.android.feature.conversation.timeline.CreationAgentTimelineReducer
import com.eleckoi.android.feature.conversation.timeline.CreationActiveTimeline
import com.eleckoi.android.feature.conversation.timeline.activeCreationTurn
import com.eleckoi.android.feature.studio.ui.assistant.timeline.toStoredTimeline
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the volatile Agent session and translates session callbacks into immutable UI state.
 *
 * Keeping this lifecycle outside the ViewModel's workspace/file actions makes session shutdown,
 * turn cancellation, approvals, and event reduction one cohesive responsibility.
 */
internal class CreationAgentSessionCoordinator(
    private val creatorService: CreatorAssistantService,
    private val agentSessionFactory: AgentSessionFactory,
    private val agentRuns: AgentRunManager,
    private val uiState: MutableStateFlow<AiCreationAssistantUiState>,
    private val scope: CoroutineScope,
    private val refreshWorkspaceFiles: suspend (workspaceId: String) -> Unit,
    private val rememberCurrentTimeline: () -> Unit,
    private val setTimelineMutationActive: (Boolean) -> Unit,
    private val persistCurrentConversationSnapshot: suspend () -> Unit,
    private val renameConversation: (workspaceId: String, conversationId: String, title: String) -> Unit,
) {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionLifecycleMutex = Mutex()
    private var turnJob: Job? = null
    private var sessionEventJob: Job? = null
    private var sessionStateJob: Job? = null
    private var activeSession: AgentSession? = null
    private var activeSessionWorkspaceId: String? = null
    private var activeSessionConversationId: String? = null
    private var mutableActiveTurnId: String? = null
    private var activeRunId: String? = null
    private var activeRunCompletion: CompletableDeferred<Unit>? = null
    private var activeRunReporter: AgentRunManager.AgentRunReporter? = null
    private var activeCheckpointWriter: CreationTimelineCheckpointWriter? = null
    private val eventReducer = CreationSessionEventReducer()
    private val pendingSteerQueue = CreationPendingSteerQueue(
        scope = runtimeScope,
        state = { uiState.value },
        updateState = { transform -> uiState.update(transform) },
        activeSession = { activeSession },
        submitCurrentInput = ::send,
    )
    private val permissionModeCoordinator = CreationPermissionModeCoordinator(
        creatorService = creatorService,
        uiState = uiState,
        scope = scope,
        activeSession = { activeSession },
        activeSessionWorkspaceId = { activeSessionWorkspaceId },
        activeSessionConversationId = { activeSessionConversationId },
    )
    private val regenerationCoordinator = CreationRegenerationCoordinator(
        creatorService = creatorService,
        uiState = uiState,
        scope = scope,
        setTimelineMutationActive = setTimelineMutationActive,
        clearReview = eventReducer::clearReview,
        detachAndScheduleShutdown = { scheduleSessionShutdown(detachActiveSession()) },
        launchTurn = { request ->
            launchTurn(
                state = request.state,
                workspaceId = request.workspaceId,
                workspaceName = request.workspaceName,
                conversationId = request.conversationId,
                prompt = request.prompt,
                inputImages = request.inputImages,
                excludeTrailingHistoryUser = request.excludeTrailingHistoryUser,
            )
        },
    )
    private val rootsContextBuilder = CreationRootsContextBuilder(creatorService)
    private val sessionOptionsFactory = CreationAgentSessionOptionsFactory(
        creatorService = creatorService,
        permissionModeProvider = { uiState.value.permissionMode },
    )
    private val sessionEventHandler = CreationSessionEventHandler(
        creatorService = creatorService,
        uiState = uiState,
        eventReducer = eventReducer,
        pendingSteerQueue = pendingSteerQueue,
        refreshWorkspaceFiles = refreshWorkspaceFiles,
        rememberCurrentTimeline = rememberCurrentTimeline,
        persistCurrentConversationSnapshot = persistCurrentConversationSnapshot,
        setTimelineMutationActive = setTimelineMutationActive,
        activeSession = { activeSession },
        activeSessionWorkspaceId = { activeSessionWorkspaceId },
        activeSessionConversationId = { activeSessionConversationId },
        activeTurnId = { mutableActiveTurnId },
        updateActiveTurnId = { mutableActiveTurnId = it },
        activeRunId = { activeRunId },
        activeRunReporter = { activeRunReporter },
        activeRunCompletion = { activeRunCompletion },
        stopActiveCheckpointWriter = ::stopActiveCheckpointWriter,
        releaseSession = ::releaseSession,
        checkpointCurrentTimeline = ::checkpointCurrentTimeline,
    )

    val activeTurnId: String?
        get() = mutableActiveTurnId

    fun send() {
        if (regenerationCoordinator.isActive) return
        val state = uiState.value
        val workspace = state.workspace ?: return
        val conversation = state.conversation ?: return
        val prompt = state.input.trim()
        val inputImages = state.inputImages
        if (prompt.isEmpty() && inputImages.isEmpty()) return
        if (!state.isRuntimeInstalled) {
            val message = when (state.runtimeState) {
                com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeState.Connecting,
                com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeState.Disconnected,
                -> "正在检测本地创作环境，请稍后再试"
                else -> "本地创作环境正在后台准备，请稍后再试"
            }
            uiState.update { it.copy(errorMessage = message) }
            return
        }
        if (state.isRunning) {
            if (inputImages.isNotEmpty()) {
                uiState.update { it.copy(errorMessage = "图片不能追加到正在运行的任务，请等待当前任务结束") }
                return
            }
            pendingSteerQueue.enqueue(prompt)
            return
        }
        val selectedConfig = state.modelConfigs.firstOrNull { it.id == state.selectedModelConfigId }
        if (inputImages.isNotEmpty() && selectedConfig?.supportsImageInput(state.selectedModelId) != true) {
            uiState.update { it.copy(errorMessage = "请先在当前模型设置中开启图片输入") }
            return
        }

        val attemptStartedAtMillis = System.currentTimeMillis()
        setTimelineMutationActive(true)
        uiState.update {
            it.copy(
                input = "",
                inputImages = emptyList(),
                isPreparingInputImages = false,
                isRunning = true,
                undoCheckpoint = null,
                pendingApprovals = emptyList(),
                notice = "",
                errorMessage = "",
                timeline = CreationActiveTimeline.start(
                    current = it.timeline,
                    user = CreationTimelineItem(
                        kind = CreationTimelineKind.User,
                        text = prompt,
                        inputImages = inputImages,
                        createdAtMillis = attemptStartedAtMillis,
                        turnStartedAtMillis = attemptStartedAtMillis,
                    ),
                ),
            )
        }
        eventReducer.clearReview()
        if (conversation.title == "新对话" && conversation.timeline.isEmpty()) {
            val titleSource = prompt.ifBlank {
                inputImages.firstOrNull()?.displayName.orEmpty().ifBlank { "图片创作" }
            }
            renameConversation(workspace.id, conversation.id, titleSource.toCreationConversationTitle())
        }
        launchTurn(
            state = state,
            workspaceId = workspace.id,
            workspaceName = workspace.name,
            conversationId = conversation.id,
            prompt = prompt,
            inputImages = inputImages,
            excludeTrailingHistoryUser = false,
        )
    }

    fun regenerateLatest() {
        regenerationCoordinator.regenerate(targetUserId = null, replacementText = null)
    }

    fun regenerateUserMessage(userMessageId: String, replacementText: String): Boolean =
        regenerationCoordinator.regenerate(
            targetUserId = userMessageId,
            replacementText = replacementText,
        )

    private fun launchTurn(
        state: AiCreationAssistantUiState,
        workspaceId: String,
        workspaceName: String,
        conversationId: String,
        prompt: String,
        inputImages: List<ChatUserImageAttachment>,
        excludeTrailingHistoryUser: Boolean,
    ) {
        val runId = UUID.randomUUID().toString()
        val terminal = CompletableDeferred<Unit>()
        val checkpointWriter = CreationTimelineCheckpointWriter(runtimeScope) { checkpoint ->
            creatorService.checkpointCreatorConversationTurn(
                workspaceId = checkpoint.workspaceId,
                conversationId = checkpoint.conversationId,
                turnTimeline = checkpoint.timeline.activeCreationTurn().toStoredTimeline(),
            )
        }
        activeRunId = runId
        activeRunCompletion = terminal
        activeCheckpointWriter = checkpointWriter
        // Register the application-owned run before send() returns so an immediate stop tap cannot
        // race between UI state becoming running and the manager acquiring the runtime slot.
        turnJob = runtimeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                agentRuns.run(
                    descriptor = AgentRunDescriptor(
                        runId = runId,
                        surface = AgentRunSurface.CreationAssistant,
                        workspaceId = workspaceId,
                        conversationId = conversationId,
                        title = workspaceName.ifBlank { "AI 创作助手" },
                        detail = "AI 助手正在执行任务",
                    ),
                    onStop = { sessionEventHandler.requestBackgroundRunStop(runId) },
                ) {
                    activeRunReporter = this
                    running("AI 助手正在执行任务")
                    val checkpoint = creatorService.checkpointCreatorWorkspace(workspaceId, "AI 修改前")
                    uiState.update { current ->
                        if (current.workspace?.id == workspaceId && current.isRunning) {
                            current.copy(undoCheckpoint = checkpoint)
                        } else {
                            current
                        }
                    }
                    val session = ensureSession(
                        workspaceId = workspaceId,
                        conversationId = conversationId,
                        permissionMode = state.permissionMode,
                        enabledToolGroupIds = state.enabledToolGroupIds,
                        modelConfigId = state.selectedModelConfigId,
                        model = state.selectedModelId,
                        excludeTrailingHistoryUser = excludeTrailingHistoryUser,
                        runtimeThreadId = state.timeline.latestRuntimeThreadId(),
                        obsoleteRuntimeThreadIds = if (excludeTrailingHistoryUser) {
                            state.timeline.runtimeThreadIds()
                        } else {
                            emptySet()
                        },
                    )
                    session.send(
                        prompt = creationAgentPrompt(prompt, inputImages),
                        contextInjections = listOf(rootsContextBuilder.build(workspaceId)),
                    )
                    terminal.await()
                }
            } catch (_: CancellationException) {
                // Stop handling or a terminal event already settled and persisted visible state.
            } catch (error: Throwable) {
                if (uiState.value.isRunning) sessionEventHandler.finishFailedRun(error)
            } finally {
                checkpointWriter.stop()
                if (activeRunId == runId) {
                    activeRunId = null
                    activeRunCompletion = null
                    activeRunReporter = null
                    activeCheckpointWriter = null
                }
                turnJob = null
                pendingSteerQueue.submitNextIfIdle()
            }
        }
    }

    fun stop() {
        if (!uiState.value.isRunning) return
        if (regenerationCoordinator.cancelIfActive()) return
        activeRunId?.let { runId ->
            if (agentRuns.requestStop(runId)) return
        }
        pendingSteerQueue.clear()
        val session = activeSession
        if (session == null || session.state.value !is AgentSessionState.Running) {
            turnJob?.cancel()
            uiState.update {
                it.copy(
                    timeline = CreationAgentTimelineReducer.finishTurn(
                        it.timeline,
                        AgentWorkStatus.Interrupted,
                    ),
                    isRunning = false,
                    pendingSteerInputs = emptyList(),
                    pendingApprovals = emptyList(),
                    notice = "已停止本次任务",
                )
            }
            rememberCurrentTimeline()
            scope.launch {
                persistCurrentConversationSnapshot()
                setTimelineMutationActive(false)
            }
            return
        }
        // Remove approval actions immediately. The session transitions to Stopping and retires
        // the matching server requests before it sends turn/interrupt.
        uiState.update {
            it.copy(
                pendingSteerInputs = emptyList(),
                pendingApprovals = emptyList(),
                notice = "正在停止本次任务",
            )
        }
        runtimeScope.launch {
            try {
                session.interrupt()
            } catch (error: Throwable) {
                sessionEventHandler.finishFailedRun(error)
            }
        }
    }

    fun resolveApproval(requestId: Long, decision: AgentApprovalDecision) {
        val approval = uiState.value.pendingApproval ?: return
        if (approval.requestId != requestId || decision !in approval.availableDecisions) return
        val session = activeSession ?: return
        runtimeScope.launch {
            runCatching { session.resolveApproval(requestId, decision) }
                .onSuccess {
                    uiState.update { state ->
                        state.copy(
                            pendingApprovals = CreationApprovalQueueReducer.remove(
                                state.pendingApprovals,
                                requestId,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    uiState.update {
                        it.copy(errorMessage = error.creationAssistantMessage("处理审批失败"))
                    }
                }
        }
    }

    fun updatePermissionMode(value: AgentPermissionMode) {
        permissionModeCoordinator.update(value)
    }

    fun toolConfigurationChanged() {
        if (!uiState.value.isRunning) {
            scheduleSessionShutdown(detachActiveSession())
        }
    }

    fun cancelTurn() {
        val runId = activeRunId
        if (runId != null && agentRuns.isActive(runId)) {
            agentRuns.requestStop(runId)
        } else {
            turnJob?.cancel()
        }
    }

    fun detachAndScheduleShutdown(): Job? {
        return scheduleSessionShutdown(detachActiveSession())
    }

    fun clear() {
        val runId = activeRunId
        if (runId != null && agentRuns.isActive(runId)) return
        cancelTurn()
        regenerationCoordinator.clear()
        detachAndScheduleShutdown()
        runtimeScope.cancel()
    }

    private suspend fun ensureSession(
        workspaceId: String,
        conversationId: String,
        permissionMode: AgentPermissionMode,
        enabledToolGroupIds: Set<String>,
        modelConfigId: String,
        model: String,
        excludeTrailingHistoryUser: Boolean,
        runtimeThreadId: String,
        obsoleteRuntimeThreadIds: Set<String>,
    ): AgentSession = sessionLifecycleMutex.withLock {
        val reusable = activeSession
            ?.takeUnless { excludeTrailingHistoryUser }
            ?.takeIf { activeSessionWorkspaceId == workspaceId }
            ?.takeIf { activeSessionConversationId == conversationId }
            ?.takeIf { it.state.value is AgentSessionState.Ready || it.state.value is AgentSessionState.Running }
        if (reusable != null) return@withLock reusable

        val stale = detachActiveSession()
        scheduleSessionShutdown(stale)?.join()
        check(uiState.value.isRuntimeInstalled) {
            "本地创作环境尚未准备完成"
        }

        val roomHistory = creatorService.loadCreatorConversationAgentHistory(
            workspaceId = workspaceId,
            conversationId = conversationId,
            excludeTrailingUser = excludeTrailingHistoryUser,
        )
        val created = agentSessionFactory.create(
            sessionOptionsFactory.create(
                workspaceId = workspaceId,
                conversationId = conversationId,
                modelConfigId = modelConfigId,
                model = model,
                permissionMode = permissionMode,
                enabledToolGroupIds = enabledToolGroupIds,
                regenerating = excludeTrailingHistoryUser,
                runtimeThreadId = runtimeThreadId,
                obsoleteRuntimeThreadIds = obsoleteRuntimeThreadIds,
                history = roomHistory,
            ),
        )
        activeSession = created
        activeSessionWorkspaceId = workspaceId
        activeSessionConversationId = conversationId
        sessionEventJob = runtimeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            created.events.collect { event ->
                sessionEventHandler.handleSessionEvent(workspaceId, conversationId, event)
            }
        }
        sessionStateJob = runtimeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            created.state.collect { sessionState ->
                sessionEventHandler.handleSessionStateFailure(
                    workspaceId = workspaceId,
                    conversationId = conversationId,
                    created = created,
                    sessionState = sessionState,
                )
            }
        }
        try {
            created.start()
            return@withLock created
        } catch (error: Throwable) {
            if (activeSession === created) detachActiveSession()
            withContext(NonCancellable) { shutdownSession(created) }
            throw error
        }
    }

    private fun checkpointCurrentTimeline(workspaceId: String, conversationId: String) {
        val snapshot = uiState.value
        if (
            snapshot.workspace?.id != workspaceId ||
            snapshot.conversation?.id != conversationId
        ) {
            return
        }
        activeCheckpointWriter?.offer(
            CreationTimelineCheckpoint(
                workspaceId = workspaceId,
                conversationId = conversationId,
                timeline = snapshot.timeline,
            ),
        )
    }

    private suspend fun stopActiveCheckpointWriter() {
        val writer = activeCheckpointWriter ?: return
        activeCheckpointWriter = null
        writer.stop()
    }

    private fun detachActiveSession(): AgentSession? {
        pendingSteerQueue.clear(cancelDrain = true)
        uiState.update { it.copy(pendingSteerInputs = emptyList()) }
        sessionEventJob?.cancel()
        sessionEventJob = null
        sessionStateJob?.cancel()
        sessionStateJob = null
        val detached = activeSession
        activeSession = null
        activeSessionWorkspaceId = null
        activeSessionConversationId = null
        mutableActiveTurnId = null
        return detached
    }

    /** Releases an unusable session. Healthy Ready sessions stay attached for the next turn. */
    private fun releaseSession(workspaceId: String, conversationId: String) {
        if (
            activeSessionWorkspaceId != workspaceId ||
            activeSessionConversationId != conversationId
        ) {
            return
        }
        scheduleSessionShutdown(detachActiveSession())
    }

    private suspend fun shutdownSession(session: AgentSession?) {
        if (session == null) return
        withContext(NonCancellable) {
            runCatching { session.shutdown() }
        }
    }

    /**
     * Session cleanup must outlive navigation and ViewModel cancellation. Once detached, the
     * session is no longer reachable from onCleared(), so scheduling it on the ViewModel scope can
     * orphan the PRoot child before the coroutine ever starts.
     */
    private fun scheduleSessionShutdown(session: AgentSession?): Job? = session?.let {
        cleanupScope.launch { shutdownSession(it) }
    }

}

private fun List<CreationTimelineItem>.latestRuntimeThreadId(): String = asReversed()
    .firstNotNullOfOrNull { item -> item.runtimeThreadId.takeIf(String::isNotBlank) }
    .orEmpty()

private fun List<CreationTimelineItem>.runtimeThreadIds(): Set<String> = mapNotNullTo(linkedSetOf()) {
    item -> item.runtimeThreadId.takeIf(String::isNotBlank)
}
