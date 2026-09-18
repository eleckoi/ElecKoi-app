package com.eleckoi.android.feature.conversation.timeline

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.reducer.CreationTimelineMessageReducer
import com.eleckoi.android.feature.conversation.timeline.reducer.CreationTimelineTurnReducer
import com.eleckoi.android.feature.conversation.timeline.reducer.CreationTimelineWorkItemReducer
import com.eleckoi.android.feature.conversation.timeline.reducer.MaxCommandOutputDetailLength
import com.eleckoi.android.feature.conversation.timeline.reducer.appendTimelineStream
import com.eleckoi.android.feature.conversation.timeline.reducer.appendUniqueDetail
import com.eleckoi.android.feature.conversation.timeline.reducer.orCurrentTime
import com.eleckoi.android.feature.conversation.timeline.reducer.replaceTimelineItemAt

/** Pure event projection kept separate from the ViewModel so streaming behavior stays deterministic and testable. */
object CreationAgentTimelineReducer {
    fun apply(
        timeline: List<CreationTimelineItem>,
        event: AgentSessionEvent,
    ): List<CreationTimelineItem> {
        if (timeline is CreationActiveTimeline) {
            return timeline.withActiveTurn(apply(timeline.activeTurn, event))
        }
        return when (event) {
            is AgentSessionEvent.DelegatedSessionEvent -> updateDelegatedTimeline(timeline, event)
            is AgentSessionEvent.TurnStarted -> CreationTimelineTurnReducer.attachTurnId(
                timeline = timeline,
                threadId = event.threadId,
                turnId = event.turnId,
                startedAtMillis = event.startedAtMillis,
            )
            is AgentSessionEvent.StepStarted -> CreationTimelineTurnReducer.startStep(timeline, event)
            is AgentSessionEvent.StepCompleted -> CreationTimelineTurnReducer.finishStep(
                timeline = timeline,
                turnId = event.turnId,
                step = event.step,
                completedAtMillis = event.completedAtMillis,
            )
            is AgentSessionEvent.AssistantDelta -> if (event.visible) {
                CreationTimelineMessageReducer.appendAssistantDelta(
                    timeline = timeline,
                    turnId = event.turnId,
                    itemId = event.itemId,
                    delta = event.delta,
                    messagePhase = event.phase,
                    phaseHeader = event.phaseHeader,
                )
            } else {
                timeline
            }
            is AgentSessionEvent.ReasoningSummaryDelta -> CreationTimelineMessageReducer.updateReasoning(
                timeline = timeline,
                turnId = event.turnId,
                itemId = event.itemId,
                updateText = { current -> appendTimelineStream(current, event.delta) },
            )
            is AgentSessionEvent.ReasoningTextDelta -> CreationTimelineMessageReducer.updateReasoning(
                timeline = timeline,
                turnId = event.turnId,
                itemId = event.itemId,
                updateDetail = { current ->
                    if (event.completeSnapshot) event.delta else appendTimelineStream(current, event.delta)
                },
            )
            is AgentSessionEvent.ModelHistoryItemCompleted -> CreationTimelineTurnReducer.appendModelHistoryItem(
                timeline = timeline,
                turnId = event.turnId,
                responseItemJson = event.responseItemJson,
            )
            is AgentSessionEvent.WorkItemStarted -> CreationTimelineWorkItemReducer.startWorkItem(timeline, event)
            is AgentSessionEvent.CommandOutput -> CreationTimelineWorkItemReducer.updateDetail(
                timeline = timeline,
                itemId = event.itemId,
            ) { detail ->
                appendTimelineStream(detail, event.delta, MaxCommandOutputDetailLength)
            }
            is AgentSessionEvent.FileChangesUpdated ->
                CreationTimelineWorkItemReducer.updateFileChanges(timeline, event)
            is AgentSessionEvent.WorkItemProgress -> CreationTimelineWorkItemReducer.updateDetail(
                timeline = timeline,
                itemId = event.itemId,
            ) { detail ->
                appendUniqueDetail(detail, listOf(event.message))
            }
            is AgentSessionEvent.TurnDiffUpdated -> CreationTimelineTurnReducer.updateTurnDiff(
                timeline = timeline,
                turnId = event.turnId,
                diff = event.diff,
            )
            is AgentSessionEvent.WorkItemCompleted -> CreationTimelineWorkItemReducer.completeWorkItem(timeline, event)
            else -> timeline
        }
    }

    fun finishTurn(
        timeline: List<CreationTimelineItem>,
        status: AgentWorkStatus,
        turnId: String? = null,
        diff: String = "",
        turnDiffObserved: Boolean = diff.isNotBlank(),
        completedAtMillis: Long = System.currentTimeMillis(),
    ): List<CreationTimelineItem> = CreationTimelineTurnReducer.finishTurn(
        timeline = timeline,
        status = status,
        turnId = turnId,
        diff = diff,
        turnDiffObserved = turnDiffObserved,
        completedAtMillis = completedAtMillis,
    )

    private fun updateDelegatedTimeline(
        timeline: List<CreationTimelineItem>,
        delegated: AgentSessionEvent.DelegatedSessionEvent,
    ): List<CreationTimelineItem> {
        val parentItemId = delegated.lineage.firstOrNull() ?: return timeline
        val index = timeline.indexOfLast { item -> item.workItemId == parentItemId }
        if (index < 0) return timeline
        return timeline.replaceTimelineItemAt(index) { parent ->
            if (delegated.lineage.size == 1) {
                val updatedChildren = when (val childEvent = delegated.event) {
                    is AgentSessionEvent.TurnCompleted -> finishTurn(
                        timeline = parent.childTimeline,
                        status = childEvent.status,
                        turnId = childEvent.turnId,
                        completedAtMillis = childEvent.completedAtMillis.orCurrentTime(),
                    )
                    else -> apply(parent.childTimeline, childEvent)
                }
                parent.copy(
                    childTimeline = updatedChildren,
                    delegatedSessionId = delegated.childSessionId,
                )
            } else {
                parent.copy(
                    childTimeline = updateDelegatedTimeline(
                        timeline = parent.childTimeline,
                        delegated = delegated.copy(lineage = delegated.lineage.drop(1)),
                    ),
                )
            }
        }
    }
}
