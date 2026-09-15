package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.feature.chat.model.ChatContextWindowUsage
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats

/** Folds one live turn onto the persisted DSH-thread projection without reading message history. */
internal class ChatSessionGenerationStatsProjector(
    private val initial: ChatSessionGenerationStats,
) {
    private var activeThreadId = initial.runtimeThreadId
    private var baseMetrics = initial.metrics
    private var baseContextWindowUsage = initial.contextWindowUsage
    private var projected = initial

    fun accept(
        event: AgentSessionEvent,
        turnMetrics: ChatGenerationMetrics,
        turnContextWindowUsage: ChatContextWindowUsage?,
    ): Boolean {
        val identity = event.agentRuntimeIdentity() ?: return false
        if (identity.threadId != activeThreadId) {
            activeThreadId = identity.threadId
            if (identity.threadId == initial.runtimeThreadId) {
                baseMetrics = initial.metrics
                baseContextWindowUsage = initial.contextWindowUsage
            } else {
                baseMetrics = ChatGenerationMetrics()
                baseContextWindowUsage = null
            }
        }
        val next = ChatSessionGenerationStats(
            runtimeThreadId = activeThreadId,
            metrics = baseMetrics + turnMetrics,
            contextWindowUsage = turnContextWindowUsage ?: baseContextWindowUsage,
        )
        if (next == projected) return false
        projected = next
        return true
    }

    fun snapshot(): ChatSessionGenerationStats = projected
}

internal data class AgentRuntimeIdentity(
    val threadId: String,
    val turnId: String = "",
)

internal fun AgentSessionEvent.agentRuntimeIdentity(): AgentRuntimeIdentity? = when (this) {
    is AgentSessionEvent.AssistantDelta -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.ReasoningSummaryDelta -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.ReasoningTextDelta -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.ModelHistoryItemCompleted -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.WorkItemStarted -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.CommandOutput -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.FileChangesUpdated -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.WorkItemProgress -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.WorkItemCompleted -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.DelegatedSessionEvent -> null
    is AgentSessionEvent.Warning -> null
    is AgentSessionEvent.ApprovalRequested -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.ApprovalResolved -> AgentRuntimeIdentity(threadId)
    is AgentSessionEvent.TurnStarted -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.StepStarted -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.StepCompleted -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.HostRequest -> null
    is AgentSessionEvent.TokenUsageUpdated -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.ContextWindowUpdated -> AgentRuntimeIdentity(threadId, turnId.orEmpty())
    is AgentSessionEvent.TurnDiffUpdated -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.TurnCompleted -> AgentRuntimeIdentity(threadId, turnId)
    is AgentSessionEvent.SessionFailed -> null
}
