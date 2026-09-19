package com.eleckoi.android.feature.studio.ui.assistant.session

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.feature.chat.data.ChatSessionGenerationStatsProjector
import com.eleckoi.android.feature.chat.data.ChatTurnMetricsCollector
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Keeps creator statistics aligned with the selected conversation and its durable sidecar. */
internal class CreationGenerationStatsController(
    private val uiState: MutableStateFlow<AiCreationAssistantUiState>,
    private val load: (conversationId: String, runtimeThreadId: String) -> ChatSessionGenerationStats,
    private val persist: (conversationId: String, stats: ChatSessionGenerationStats) -> Unit,
    private val delete: (conversationId: String) -> Unit,
) {
    private var selectedConversationId = ""
    private var currentStats = ChatSessionGenerationStats()
    private var turnMetrics = ChatTurnMetricsCollector()
    private var projector = ChatSessionGenerationStatsProjector(currentStats)

    fun prepareConversation(conversationId: String) {
        selectedConversationId = conversationId
        currentStats = ChatSessionGenerationStats()
        turnMetrics = ChatTurnMetricsCollector()
        projector = ChatSessionGenerationStatsProjector(currentStats)
        publishIfSelected(conversationId, currentStats)
    }

    suspend fun restore(conversationId: String, runtimeThreadId: String) {
        val restored = withContext(Dispatchers.IO) { load(conversationId, runtimeThreadId) }
        if (selectedConversationId != conversationId) return
        currentStats = restored
        turnMetrics = ChatTurnMetricsCollector()
        projector = ChatSessionGenerationStatsProjector(restored)
        publishIfSelected(conversationId, restored)
    }

    suspend fun accept(conversationId: String, event: AgentSessionEvent) {
        if (selectedConversationId != conversationId) prepareConversation(conversationId)
        if (event is AgentSessionEvent.TurnStarted) {
            turnMetrics = ChatTurnMetricsCollector()
            projector = ChatSessionGenerationStatsProjector(currentStats)
        }
        val metricsChanged = turnMetrics.accept(event)
        val projectionChanged = projector.accept(
            event = event,
            turnMetrics = turnMetrics.snapshot(),
            turnContextWindowUsage = turnMetrics.contextWindowUsage(),
        )
        if (!metricsChanged && !projectionChanged) return
        val next = projector.snapshot()
        if (next == currentStats) return
        currentStats = next
        publishIfSelected(conversationId, next)
        runCatching {
            withContext(Dispatchers.IO) { persist(conversationId, next) }
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        runCatching { withContext(Dispatchers.IO) { delete(conversationId) } }
        if (selectedConversationId == conversationId) {
            selectedConversationId = ""
            currentStats = ChatSessionGenerationStats()
            turnMetrics = ChatTurnMetricsCollector()
            projector = ChatSessionGenerationStatsProjector(currentStats)
        }
    }

    suspend fun deleteConversations(conversationIds: Collection<String>) {
        conversationIds.forEach { conversationId -> deleteConversation(conversationId) }
    }

    private fun publishIfSelected(
        conversationId: String,
        stats: ChatSessionGenerationStats,
    ) {
        uiState.update { state ->
            if (state.conversation?.id == conversationId) {
                state.copy(generationStats = stats)
            } else {
                state
            }
        }
    }
}
