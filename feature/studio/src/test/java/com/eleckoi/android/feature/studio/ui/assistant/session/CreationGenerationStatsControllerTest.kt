package com.eleckoi.android.feature.studio.ui.assistant.session

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentTokenUsage
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.engine.workspace.model.CreatorConversation
import com.eleckoi.android.feature.chat.data.ChatGenerationStatsStore
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CreationGenerationStatsControllerTest {
    @Test
    fun `live creator measurements survive a controller restart`() = runBlocking {
        val root = Files.createTempDirectory("creator-generation-stats").toFile()
        try {
            val store = ChatGenerationStatsStore(root)
            val firstState = MutableStateFlow(stateFor("conversation-1"))
            val first = controller(firstState, store)
            first.prepareConversation("conversation-1")
            first.restore("conversation-1", "thread-1")

            val events = listOf(
                AgentSessionEvent.TurnStarted("thread-1", "turn-1", startedAtMillis = 50L),
                AgentSessionEvent.StepStarted("thread-1", "turn-1", step = 1, startedAtMillis = 100L),
                AgentSessionEvent.AssistantDelta(
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = "assistant-1",
                    delta = "完成",
                    step = 1,
                    observedAtMillis = 150L,
                ),
                AgentSessionEvent.TokenUsageUpdated(
                    threadId = "thread-1",
                    turnId = "turn-1",
                    step = 1,
                    total = usage(total = 170L, input = 100L, output = 20L),
                    last = usage(total = 170L, input = 100L, output = 20L),
                    modelContextWindow = 1_000L,
                ),
                AgentSessionEvent.ContextWindowUpdated(
                    threadId = "thread-1",
                    turnId = "turn-1",
                    pressureTokens = 120L,
                    projectedTokens = 140L,
                    modelContextWindow = 1_000L,
                    systemTokens = 10L,
                    toolsTokens = 20L,
                    messageTokens = 110L,
                ),
                AgentSessionEvent.WorkItemCompleted(
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = "assistant-1",
                    type = AgentWorkItemType.AssistantMessage,
                    status = AgentWorkStatus.Completed,
                    completedAtMillis = 300L,
                    step = 1,
                ),
                AgentSessionEvent.StepCompleted(
                    threadId = "thread-1",
                    turnId = "turn-1",
                    step = 1,
                    completedAtMillis = 310L,
                ),
            )
            events.forEach { event -> first.accept("conversation-1", event) }

            val expected = firstState.value.generationStats
            assertEquals(1, expected.metrics.turns)
            assertEquals(1, expected.metrics.steps)
            assertEquals(200L, expected.metrics.llmDurationMillis)
            assertEquals(50L, expected.metrics.firstTokenDelayMillis)
            assertEquals(140L, expected.contextWindowUsage?.latestTokens)

            val restoredState = MutableStateFlow(stateFor("conversation-1"))
            val restored = controller(restoredState, ChatGenerationStatsStore(root))
            restored.prepareConversation("conversation-1")
            restored.restore("conversation-1", "thread-1")

            assertEquals(expected, restoredState.value.generationStats)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `creator regeneration keeps cumulative turns across a fresh DSH thread and restart`() = runBlocking {
        val root = Files.createTempDirectory("creator-regeneration-stats").toFile()
        try {
            val store = ChatGenerationStatsStore(root)
            store.persist(
                "conversation-1",
                ChatSessionGenerationStats(
                    runtimeThreadId = "old-thread",
                    metrics = ChatGenerationMetrics(turns = 58, steps = 367, inputTokens = 3_000_000),
                    stepTotalsByTurn = mapOf("57" to 364, "58" to 367),
                ),
            )
            val state = MutableStateFlow(stateFor("conversation-1"))
            val controller = controller(state, store)
            controller.prepareConversation("conversation-1")
            controller.restore("conversation-1", "old-thread")
            controller.prepareRegeneration("conversation-1", retainedTurns = 58)
            assertEquals(58, state.value.generationStats.metrics.turns)
            assertEquals(364, state.value.generationStats.metrics.steps)

            val reopenedState = MutableStateFlow(stateFor("conversation-1"))
            val reopened = controller(reopenedState, ChatGenerationStatsStore(root))
            reopened.prepareConversation("conversation-1")
            reopened.restore("conversation-1", "")
            assertEquals(58, reopenedState.value.generationStats.metrics.turns)
            assertEquals(364, reopenedState.value.generationStats.metrics.steps)

            reopened.accept(
                "conversation-1",
                AgentSessionEvent.TurnStarted("new-thread", "new-turn", startedAtMillis = 100L),
            )
            assertEquals(58, reopenedState.value.generationStats.metrics.turns)
            reopened.accept(
                "conversation-1",
                AgentSessionEvent.StepStarted("new-thread", "new-turn", step = 1, startedAtMillis = 110L),
            )
            assertEquals(58, reopenedState.value.generationStats.metrics.turns)
            reopened.accept(
                "conversation-1",
                AgentSessionEvent.StepCompleted("new-thread", "new-turn", step = 1, completedAtMillis = 200L),
            )
            assertEquals(58, reopenedState.value.generationStats.metrics.turns)
            assertEquals(3_000_000L, reopenedState.value.generationStats.metrics.billedInputTokens)
            assertEquals(
                58,
                ChatGenerationStatsStore(root).load("conversation-1", "new-thread").metrics.turns,
            )

            reopened.accept(
                "conversation-1",
                AgentSessionEvent.TurnStarted("new-thread", "next-turn", startedAtMillis = 300L),
            )
            reopened.accept(
                "conversation-1",
                AgentSessionEvent.StepCompleted("new-thread", "next-turn", step = 1, completedAtMillis = 400L),
            )
            assertEquals(59, reopenedState.value.generationStats.metrics.turns)
            assertEquals(366, reopenedState.value.generationStats.metrics.steps)
            reopened.prepareRegeneration("conversation-1", retainedTurns = 58)
            assertEquals(364, reopenedState.value.generationStats.metrics.steps)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun controller(
        state: MutableStateFlow<AiCreationAssistantUiState>,
        store: ChatGenerationStatsStore,
    ) = CreationGenerationStatsController(
        uiState = state,
        load = store::load,
        persist = store::persist,
        delete = store::deleteConversation,
    )

    private fun stateFor(conversationId: String) = AiCreationAssistantUiState(
        conversation = CreatorConversation(
            id = conversationId,
            title = "合成创作对话",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        ),
    )

    private fun usage(
        total: Long,
        input: Long,
        output: Long,
    ) = AgentTokenUsage(
        totalTokens = total,
        inputTokens = input,
        cacheReadTokens = 50L,
        cacheWriteTokens = 0L,
        cacheUsageReported = true,
        outputTokens = output,
        reasoningOutputTokens = 0L,
    )
}
