package com.eleckoi.android.feature.studio.ui.assistant.session

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import com.eleckoi.android.feature.studio.ui.assistant.CreationApprovalRequest
import com.eleckoi.android.feature.studio.ui.assistant.CreationContextWindowUsage
import com.eleckoi.android.feature.studio.ui.assistant.approval.CreationApprovalQueueReducer
import com.eleckoi.android.feature.conversation.timeline.CreationAgentTimelineReducer

internal class CreationSessionEventReducer {
    private val itemReviewContent = mutableMapOf<String, String>()
    private val turnReviewContent = mutableMapOf<String, String>()

    fun reduce(
        state: AiCreationAssistantUiState,
        event: AgentSessionEvent,
    ): AiCreationAssistantUiState = when (event) {
        is AgentSessionEvent.ApprovalRequested -> {
            val request = CreationApprovalRequest(
                requestId = event.requestId,
                kind = event.kind,
                threadId = event.threadId,
                turnId = event.turnId,
                itemId = event.itemId,
                title = event.title,
                detail = event.detail,
                reviewContent = itemReviewContent[event.itemId]
                    .orEmpty()
                    .ifBlank { turnReviewContent[event.turnId].orEmpty() },
                availableDecisions = event.availableDecisions,
                rawCommand = event.rawCommand,
                commandActions = event.commandActions,
            )
            state.copy(
                pendingApprovals = CreationApprovalQueueReducer.enqueue(
                    state.pendingApprovals,
                    request,
                ),
            )
        }
        is AgentSessionEvent.ApprovalResolved -> state.copy(
            pendingApprovals = CreationApprovalQueueReducer.remove(
                state.pendingApprovals,
                event.requestId,
            ),
        )
        is AgentSessionEvent.WorkItemStarted -> {
            if (event.type == AgentWorkItemType.FileChange && event.diff.isNotBlank()) {
                itemReviewContent[event.itemId] = event.diff
            }
            state.copy(
                pendingApprovals = CreationApprovalQueueReducer.updateReviewForItem(
                    current = state.pendingApprovals,
                    threadId = event.threadId,
                    turnId = event.turnId,
                    itemId = event.itemId,
                    reviewContent = event.diff,
                ),
                pendingSteerInputs = if (event.type == AgentWorkItemType.UserMessage) {
                    state.pendingSteerInputs.withoutCommittedSteer(
                        clientUserMessageId = event.clientUserMessageId,
                        text = event.label,
                    )
                } else {
                    state.pendingSteerInputs
                },
                timeline = CreationAgentTimelineReducer.apply(state.timeline, event),
            )
        }
        is AgentSessionEvent.WorkItemCompleted -> state.copy(
            pendingSteerInputs = if (event.type == AgentWorkItemType.UserMessage) {
                state.pendingSteerInputs.withoutCommittedSteer(
                    clientUserMessageId = event.clientUserMessageId,
                    text = event.summary,
                )
            } else {
                state.pendingSteerInputs
            },
            timeline = CreationAgentTimelineReducer.apply(state.timeline, event),
        )
        is AgentSessionEvent.FileChangesUpdated -> {
            if (event.diff.isNotBlank()) itemReviewContent[event.itemId] = event.diff
            state.copy(
                pendingApprovals = CreationApprovalQueueReducer.updateReviewForItem(
                    state.pendingApprovals,
                    event.threadId,
                    event.turnId,
                    event.itemId,
                    event.diff,
                ),
                timeline = CreationAgentTimelineReducer.apply(state.timeline, event),
            )
        }
        is AgentSessionEvent.TurnDiffUpdated -> {
            turnReviewContent[event.turnId] = event.diff
            state.copy(
                pendingApprovals = CreationApprovalQueueReducer.updateReviewForTurn(
                    state.pendingApprovals,
                    event.threadId,
                    event.turnId,
                    event.diff,
                ),
                timeline = CreationAgentTimelineReducer.apply(state.timeline, event),
            )
        }
        is AgentSessionEvent.TokenUsageUpdated -> state.copy(
            contextWindowUsage = CreationContextWindowUsage(
                threadId = event.threadId,
                turnId = event.turnId,
                latestTokens = (
                    event.last.inputTokens +
                        event.last.cacheReadTokens +
                        event.last.cacheWriteTokens
                    ).coerceAtLeast(0L),
                totalTokens = event.total.totalTokens.coerceAtLeast(0L),
                modelContextWindow = event.modelContextWindow
                    ?: state.contextWindowUsage?.modelContextWindow,
                systemTokens = state.contextWindowUsage?.systemTokens,
                toolsTokens = state.contextWindowUsage?.toolsTokens,
                messageTokens = state.contextWindowUsage?.messageTokens,
            ),
        )
        is AgentSessionEvent.ContextWindowUpdated -> {
            val latestTokens = event.projectedTokens ?: event.pressureTokens
            if (latestTokens == null) {
                state
            } else {
                state.copy(
                    contextWindowUsage = CreationContextWindowUsage(
                        threadId = event.threadId,
                        turnId = event.turnId,
                        latestTokens = latestTokens,
                        totalTokens = state.contextWindowUsage?.totalTokens ?: 0L,
                        modelContextWindow = event.modelContextWindow
                            ?: state.contextWindowUsage?.modelContextWindow,
                        systemTokens = event.systemTokens
                            ?: state.contextWindowUsage?.systemTokens,
                        toolsTokens = event.toolsTokens
                            ?: state.contextWindowUsage?.toolsTokens,
                        messageTokens = event.messageTokens
                            ?: state.contextWindowUsage?.messageTokens,
                    ),
                )
            }
        }
        else -> state.copy(
            timeline = CreationAgentTimelineReducer.apply(state.timeline, event),
        )
    }

    fun turnDiff(turnId: String): ObservedCreationTurnDiff = ObservedCreationTurnDiff(
        observed = turnReviewContent.containsKey(turnId),
        diff = turnReviewContent[turnId].orEmpty(),
    )

    fun clearReview() {
        itemReviewContent.clear()
        turnReviewContent.clear()
    }
}

internal data class ObservedCreationTurnDiff(
    val observed: Boolean,
    val diff: String,
)
