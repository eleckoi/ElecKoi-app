package com.eleckoi.android.feature.studio.ui.assistant.session

import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.conversation.timeline.CreationActiveTimeline
import com.eleckoi.android.feature.conversation.timeline.CreationAgentTimelineReducer
import com.eleckoi.android.feature.studio.api.CreatorAssistantService
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import com.eleckoi.android.feature.studio.ui.assistant.timeline.toStoredTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CreationTurnLaunchRequest(
    val state: AiCreationAssistantUiState,
    val workspaceId: String,
    val workspaceName: String,
    val conversationId: String,
    val prompt: String,
    val inputImages: List<ChatUserImageAttachment>,
    val excludeTrailingHistoryUser: Boolean,
)

/** Owns regeneration truncation, rollback, and the single in-flight regeneration job. */
internal class CreationRegenerationCoordinator(
    private val creatorService: CreatorAssistantService,
    private val generationStats: CreationGenerationStatsController,
    private val uiState: MutableStateFlow<AiCreationAssistantUiState>,
    private val scope: CoroutineScope,
    private val setTimelineMutationActive: (Boolean) -> Unit,
    private val clearReview: () -> Unit,
    private val detachAndScheduleShutdown: () -> Job?,
    private val launchTurn: (CreationTurnLaunchRequest) -> Unit,
) {
    private var job: Job? = null

    val isActive: Boolean
        get() = job?.isActive == true

    fun regenerate(
        targetUserId: String?,
        replacementText: String?,
    ): Boolean {
        if (isActive || uiState.value.isRunning) return false
        val state = uiState.value
        val workspace = state.workspace ?: return false
        val conversation = state.conversation ?: return false
        val attemptStartedAtMillis = System.currentTimeMillis()
        val plan = planCreationRegeneration(
            timeline = state.timeline,
            restartedAtMillis = attemptStartedAtMillis,
            targetUserId = targetUserId,
            replacementText = replacementText,
        ) ?: return false
        if (!state.isRuntimeInstalled) {
            uiState.update { it.copy(errorMessage = "本地创作环境正在后台准备，请稍后再试") }
            return false
        }

        setTimelineMutationActive(true)
        uiState.update { current ->
            if (
                current.workspace?.id != workspace.id ||
                current.conversation?.id != conversation.id ||
                current.isRunning
            ) {
                current
            } else {
                current.copy(
                    input = "",
                    isRunning = true,
                    undoCheckpoint = null,
                    pendingApprovals = emptyList(),
                    pendingSteerInputs = emptyList(),
                    notice = "",
                    errorMessage = "",
                    timeline = CreationActiveTimeline.start(
                        current = plan.stableHistory,
                        user = plan.retainedUser,
                    ),
                )
            }
        }
        clearReview()
        // Start from the UI scope just like role-chat regeneration. Starting UNDISPATCHED from
        // the runtime scope could let the service's same-dispatcher fast path run Room on main.
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var truncated = false
            try {
                val updated = creatorService.truncateCreatorConversationForRegeneration(
                    workspaceId = workspace.id,
                    conversationId = conversation.id,
                    retainedUser = listOf(plan.retainedUser).toStoredTimeline().single(),
                )
                truncated = true
                generationStats.prepareRegeneration(conversation.id, plan.retainedTurns)
                detachAndScheduleShutdown()?.join()
                val updatedConversation = updated.conversations
                    .firstOrNull { it.id == conversation.id }
                    ?: error("创作助手对话不存在")
                uiState.update { current ->
                    if (
                        current.workspace?.id != workspace.id ||
                        current.conversation?.id != conversation.id
                    ) {
                        current
                    } else {
                        current.copy(
                            workspaces = current.workspaces.map { candidate ->
                                if (candidate.id == updated.id) updated else candidate
                            },
                            workspace = updated,
                            conversation = updatedConversation,
                        )
                    }
                }
                launchTurn(
                    CreationTurnLaunchRequest(
                        state = state,
                        workspaceId = workspace.id,
                        workspaceName = workspace.name,
                        conversationId = conversation.id,
                        prompt = plan.prompt,
                        inputImages = plan.retainedUser.inputImages,
                        // The retained user row is already in Room so truncation can be transactional;
                        // the new native turn owns adding it to the Harness exactly once.
                        excludeTrailingHistoryUser = true,
                    ),
                )
            } catch (cancellation: CancellationException) {
                val restoredTimeline = if (truncated) {
                    CreationAgentTimelineReducer.finishTurn(
                        timeline = plan.stableHistory + plan.retainedUser,
                        status = AgentWorkStatus.Interrupted,
                    )
                } else {
                    state.timeline
                }
                uiState.update { current ->
                    current.copy(
                        isRunning = false,
                        timeline = restoredTimeline,
                    )
                }
                setTimelineMutationActive(false)
                throw cancellation
            } catch (error: Throwable) {
                val restoredTimeline = if (truncated) {
                    CreationAgentTimelineReducer.finishTurn(
                        timeline = plan.stableHistory + plan.retainedUser,
                        status = AgentWorkStatus.Failed,
                    )
                } else {
                    state.timeline
                }
                uiState.update { current ->
                    current.copy(
                        isRunning = false,
                        timeline = restoredTimeline,
                        errorMessage = error.creationAssistantMessage("重新生成失败"),
                    )
                }
                setTimelineMutationActive(false)
            } finally {
                job = null
            }
        }
        return true
    }

    fun cancelIfActive(): Boolean {
        val activeJob = job?.takeIf(Job::isActive) ?: return false
        activeJob.cancel()
        return true
    }

    fun clear() {
        job?.cancel()
    }
}
