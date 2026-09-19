package com.eleckoi.android.feature.studio.ui.assistant.session

import com.eleckoi.android.engine.agent.api.AgentFailureReason
import com.eleckoi.android.engine.agent.api.AgentSession
import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentSessionState
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.engine.agent.api.agentWarningNotice
import com.eleckoi.android.engine.agent.background.AgentRunManager
import com.eleckoi.android.feature.conversation.timeline.CreationAgentTimelineReducer
import com.eleckoi.android.feature.conversation.timeline.reconcileCreationTurnDiffWithWorkspaceSnapshot
import com.eleckoi.android.feature.conversation.timeline.toCreationTurns
import com.eleckoi.android.feature.studio.api.CreatorAssistantService
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class CreationSessionEventHandler(
    private val creatorService: CreatorAssistantService,
    private val uiState: MutableStateFlow<AiCreationAssistantUiState>,
    private val eventReducer: CreationSessionEventReducer,
    private val generationStats: CreationGenerationStatsController,
    private val pendingSteerQueue: CreationPendingSteerQueue,
    private val refreshWorkspaceFiles: suspend (workspaceId: String) -> Unit,
    private val rememberCurrentTimeline: () -> Unit,
    private val persistCurrentConversationSnapshot: suspend () -> Unit,
    private val setTimelineMutationActive: (Boolean) -> Unit,
    private val activeSession: () -> AgentSession?,
    private val activeSessionWorkspaceId: () -> String?,
    private val activeSessionConversationId: () -> String?,
    private val activeTurnId: () -> String?,
    private val updateActiveTurnId: (String?) -> Unit,
    private val activeRunId: () -> String?,
    private val activeRunReporter: () -> AgentRunManager.AgentRunReporter?,
    private val activeRunCompletion: () -> CompletableDeferred<Unit>?,
    private val stopActiveCheckpointWriter: suspend () -> Unit,
    private val releaseSession: (workspaceId: String, conversationId: String) -> Unit,
    private val checkpointCurrentTimeline: (workspaceId: String, conversationId: String) -> Unit,
) {
    suspend fun handleSessionEvent(
        workspaceId: String,
        conversationId: String,
        event: AgentSessionEvent,
    ) {
        if (!isActiveSessionContext(workspaceId, conversationId)) return
        handleSessionEventImmediately(workspaceId, conversationId, event)
    }

    suspend fun handleSessionStateFailure(
        workspaceId: String,
        conversationId: String,
        created: AgentSession,
        sessionState: AgentSessionState,
    ) {
        if (sessionState !is AgentSessionState.Failed || activeSession() !== created) return
        uiState.update { state ->
            if (state.isRunning) {
                state.copy(
                    timeline = CreationAgentTimelineReducer.finishTurn(
                        state.timeline,
                        AgentWorkStatus.Failed,
                    ),
                    isRunning = false,
                    pendingApprovals = emptyList(),
                    errorMessage = sessionState.message,
                )
            } else {
                state
            }
        }
        stopActiveCheckpointWriter()
        rememberCurrentTimeline()
        runCatching { persistCurrentConversationSnapshot() }
        setTimelineMutationActive(false)
        eventReducer.clearReview()
        releaseSession(workspaceId, conversationId)
        activeRunCompletion()?.complete(Unit)
    }

    private suspend fun handleSessionEventImmediately(
        workspaceId: String,
        conversationId: String,
        event: AgentSessionEvent,
    ) {
        if (!isActiveSessionContext(workspaceId, conversationId)) return
        updateActiveTurnId(
            when (event) {
                is AgentSessionEvent.TurnStarted -> event.turnId
                is AgentSessionEvent.TurnCompleted -> activeTurnId().takeUnless { it == event.turnId }
                else -> activeTurnId()
            },
        )
        generationStats.accept(conversationId, event)
        when (event) {
            is AgentSessionEvent.TurnStarted -> {
                activeRunReporter()?.running("AI 助手正在执行任务")
                uiState.update { state -> eventReducer.reduce(state, event) }
                pendingSteerQueue.drain()
                checkpointCurrentTimeline(workspaceId, conversationId)
            }
            is AgentSessionEvent.ApprovalRequested -> {
                activeRunReporter()?.waitingForApproval(
                    event.title.ifBlank { "AI 助手等待你确认操作" },
                )
                uiState.update { state -> eventReducer.reduce(state, event) }
                checkpointCurrentTimeline(workspaceId, conversationId)
            }
            is AgentSessionEvent.ApprovalResolved -> {
                activeRunReporter()?.running("AI 助手正在继续执行任务")
                uiState.update { state -> eventReducer.reduce(state, event) }
                checkpointCurrentTimeline(workspaceId, conversationId)
            }
            is AgentSessionEvent.WorkItemStarted,
            is AgentSessionEvent.WorkItemCompleted,
            is AgentSessionEvent.FileChangesUpdated,
            is AgentSessionEvent.TurnDiffUpdated,
            is AgentSessionEvent.TokenUsageUpdated,
            is AgentSessionEvent.ContextWindowUpdated,
            is AgentSessionEvent.StepStarted,
            is AgentSessionEvent.StepCompleted,
            is AgentSessionEvent.DelegatedSessionEvent,
            -> {
                uiState.update { state -> eventReducer.reduce(state, event) }
                checkpointCurrentTimeline(workspaceId, conversationId)
            }
            is AgentSessionEvent.TurnCompleted -> {
                val refreshFailure = runCatching { refreshWorkspaceFiles(workspaceId) }.exceptionOrNull()
                val creatorWorkspaceRefresh = runCatching {
                    creatorService.creatorWorkspace(workspaceId)?.let { refreshed ->
                        refreshed to refreshed.characterRoots.mapNotNull { root ->
                            creatorService.creatorCharacter(root.characterId)
                        }
                    }
                }
                creatorWorkspaceRefresh.getOrNull()?.let { (refreshed, rootCharacters) ->
                    uiState.update { state ->
                        state.copy(
                            workspace = if (state.workspace?.id == refreshed.id) refreshed else state.workspace,
                            workspaces = state.workspaces.map { workspace ->
                                if (workspace.id == refreshed.id) refreshed else workspace
                            },
                            characterId = if (state.workspace?.id == refreshed.id) {
                                refreshed.linkedCharacterId.orEmpty()
                            } else {
                                state.characterId
                            },
                            creatorRootCharacters = if (state.workspace?.id == refreshed.id) {
                                rootCharacters
                            } else {
                                state.creatorRootCharacters
                            },
                        )
                    }
                }
                val observedTurnDiff = eventReducer.turnDiff(event.turnId)
                val turnDiffObserved = observedTurnDiff.observed
                val rawTurnDiff = observedTurnDiff.diff
                val snapshot = uiState.value
                val checkpoint = snapshot.undoCheckpoint
                val finalTurnDiff = if (
                    turnDiffObserved &&
                    refreshFailure == null &&
                    checkpoint?.workspaceId == workspaceId
                ) {
                    reconcileCreationTurnDiffWithWorkspaceSnapshot(
                        diff = rawTurnDiff,
                        afterPaths = snapshot.files.map { it.path },
                    )
                } else {
                    rawTurnDiff
                }
                val timeline = CreationAgentTimelineReducer.finishTurn(
                    timeline = snapshot.timeline,
                    status = event.status,
                    turnId = event.turnId,
                    diff = finalTurnDiff,
                    turnDiffObserved = turnDiffObserved,
                    completedAtMillis = event.completedAtMillis.orCurrentTime(),
                )
                if (event.status == AgentWorkStatus.Completed) {
                    val finalAnswer = timeline
                        .toCreationTurns(isRunning = false)
                        .lastOrNull()
                        ?.finalAnswer
                        ?.text
                        .orEmpty()
                        .ifBlank { "AI 助手任务已完成" }
                    activeRunReporter()?.completed(finalAnswer)
                }
                uiState.update { state ->
                    val contextExhausted = event.failureReason == AgentFailureReason.ContextWindowExceeded
                    state.copy(
                        timeline = timeline,
                        isRunning = false,
                        pendingSteerInputs = state.pendingSteerInputs.filter { preview ->
                            pendingSteerQueue.contains(preview.id)
                        },
                        pendingApprovals = emptyList(),
                        notice = when (event.status) {
                            AgentWorkStatus.Interrupted -> "已停止本次任务"
                            AgentWorkStatus.Declined -> "本次任务未执行"
                            AgentWorkStatus.Failed if contextExhausted ->
                                event.errorMessage.orEmpty()
                                    .ifBlank { "上下文已用尽，下一条消息会自动使用新上下文" }
                            else -> state.notice
                        },
                        errorMessage = if (event.status == AgentWorkStatus.Failed && !contextExhausted) {
                            event.errorMessage.orEmpty().ifBlank { "创作任务失败" }
                        } else {
                            (refreshFailure ?: creatorWorkspaceRefresh.exceptionOrNull())
                                ?.creationAssistantMessage("刷新创作工作区失败")
                                .orEmpty()
                                .ifBlank { state.errorMessage }
                        },
                    )
                }
                stopActiveCheckpointWriter()
                rememberCurrentTimeline()
                runCatching { persistCurrentConversationSnapshot() }
                    .onFailure { error ->
                        uiState.update {
                            it.copy(
                                errorMessage = error.creationAssistantMessage("保存创作对话失败"),
                            )
                        }
                }
                setTimelineMutationActive(false)
                eventReducer.clearReview()
                if (
                    shouldReleaseCreationSessionAfterTurn(
                        sessionState = activeSession()?.state?.value,
                        hasPendingSteer = !pendingSteerQueue.isEmpty(),
                    )
                ) {
                    releaseSession(workspaceId, conversationId)
                }
                activeRunCompletion()?.complete(Unit)
            }
            is AgentSessionEvent.Warning -> agentWarningNotice(event.message)?.let { message ->
                uiState.update { it.copy(notice = message) }
                checkpointCurrentTimeline(workspaceId, conversationId)
            }
            is AgentSessionEvent.SessionFailed -> {
                pendingSteerQueue.clear()
                uiState.update {
                    val contextExhausted = event.reason == AgentFailureReason.ContextWindowExceeded
                    it.copy(
                        timeline = CreationAgentTimelineReducer.finishTurn(
                            it.timeline,
                            AgentWorkStatus.Failed,
                        ),
                        isRunning = false,
                        pendingSteerInputs = emptyList(),
                        pendingApprovals = emptyList(),
                        notice = if (contextExhausted) event.message else it.notice,
                        errorMessage = if (contextExhausted) "" else event.message,
                    )
                }
                stopActiveCheckpointWriter()
                rememberCurrentTimeline()
                runCatching { persistCurrentConversationSnapshot() }
                    .onFailure { error ->
                        uiState.update {
                            it.copy(
                                errorMessage = error.creationAssistantMessage("保存创作对话失败"),
                            )
                        }
                    }
                setTimelineMutationActive(false)
                eventReducer.clearReview()
                releaseSession(workspaceId, conversationId)
                activeRunCompletion()?.complete(Unit)
            }
            else -> {
                uiState.update { state -> eventReducer.reduce(state, event) }
                checkpointCurrentTimeline(workspaceId, conversationId)
            }
        }
    }

    private fun isActiveSessionContext(workspaceId: String, conversationId: String): Boolean =
        workspaceId == activeSessionWorkspaceId() &&
            conversationId == activeSessionConversationId() &&
            uiState.value.workspace?.id == workspaceId &&
            uiState.value.conversation?.id == conversationId

    suspend fun finishFailedRun(error: Throwable) {
        pendingSteerQueue.clear()
        val snapshot = uiState.value
        uiState.update { state ->
            state.copy(
                timeline = CreationAgentTimelineReducer.finishTurn(
                    state.timeline,
                    AgentWorkStatus.Failed,
                ),
                isRunning = false,
                pendingSteerInputs = emptyList(),
                pendingApprovals = emptyList(),
                errorMessage = error.creationAssistantMessage("创作任务失败"),
            )
        }
        stopActiveCheckpointWriter()
        rememberCurrentTimeline()
        runCatching { persistCurrentConversationSnapshot() }
        setTimelineMutationActive(false)
        val workspaceId = snapshot.workspace?.id
        val conversationId = snapshot.conversation?.id
        if (workspaceId != null && conversationId != null) {
            releaseSession(workspaceId, conversationId)
        }
        activeRunCompletion()?.complete(Unit)
    }

    suspend fun requestBackgroundRunStop(runId: String) {
        if (activeRunId() != runId) return
        pendingSteerQueue.clear()
        val session = activeSession()
        if (session != null && session.state.value is AgentSessionState.Running) {
            uiState.update {
                it.copy(
                    pendingSteerInputs = emptyList(),
                    pendingApprovals = emptyList(),
                    notice = "正在停止本次任务",
                )
            }
            try {
                session.interrupt()
            } catch (error: Throwable) {
                finishFailedRun(error)
            }
            return
        }

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
        stopActiveCheckpointWriter()
        rememberCurrentTimeline()
        runCatching { persistCurrentConversationSnapshot() }
        setTimelineMutationActive(false)
        val snapshot = uiState.value
        val workspaceId = snapshot.workspace?.id
        val conversationId = snapshot.conversation?.id
        if (workspaceId != null && conversationId != null) {
            releaseSession(workspaceId, conversationId)
        }
        activeRunCompletion()?.complete(Unit)
    }

}

internal fun Long.orCurrentTime(): Long = takeIf { it > 0L } ?: System.currentTimeMillis()

/**
 * A terminal turn is not a session terminal. Keeping a healthy Ready session avoids rebinding the
 * persistent DSH route, permissions and transcript before every message.
 */
internal fun shouldReleaseCreationSessionAfterTurn(
    sessionState: AgentSessionState?,
    hasPendingSteer: Boolean,
): Boolean = !hasPendingSteer && sessionState !is AgentSessionState.Ready
