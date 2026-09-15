package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentTokenUsage
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSessionGenerationStatsProjectorTest {
    @Test
    fun `adds a live turn to a long persisted DSH session without reading paged messages`() {
        val projector = ChatSessionGenerationStatsProjector(
            ChatSessionGenerationStats(
                runtimeThreadId = "thread",
                metrics = ChatGenerationMetrics(
                    turns = 1_000,
                    steps = 2_500,
                    inputTokens = 2_000_000,
                    cacheReadTokens = 8_000_000,
                    outputTokens = 50_000,
                ),
            ),
        )
        val turn = ChatTurnMetricsCollector()
        turn.accept(AgentSessionEvent.StepStarted("thread", "turn-1001", 1, 100))
        val usage = usage(input = 10_000, cacheRead = 490_000, output = 2_000)
        val usageEvent = AgentSessionEvent.TokenUsageUpdated(
            "thread",
            "turn-1001",
            1,
            total = usage,
            last = usage,
            modelContextWindow = 1_000_000,
        )
        turn.accept(usageEvent)
        projector.accept(usageEvent, turn.snapshot(), turn.contextWindowUsage())
        val stepEnd = AgentSessionEvent.StepCompleted("thread", "turn-1001", 1, 200)
        turn.accept(stepEnd)
        projector.accept(stepEnd, turn.snapshot(), turn.contextWindowUsage())

        val snapshot = projector.snapshot()
        assertEquals(1_001, snapshot.metrics.turns)
        assertEquals(2_501, snapshot.metrics.steps)
        assertEquals(10_500_000L, snapshot.metrics.billedInputTokens)
        assertEquals(52_000L, snapshot.metrics.outputTokens)
    }

    @Test
    fun `a fresh DSH runtime thread does not inherit the discarded branch totals`() {
        val projector = ChatSessionGenerationStatsProjector(
            ChatSessionGenerationStats(
                runtimeThreadId = "old-thread",
                metrics = ChatGenerationMetrics(turns = 50, inputTokens = 3_000_000),
            ),
        )
        projector.accept(
            AgentSessionEvent.TurnStarted("new-thread", "new-turn", 100),
            turnMetrics = ChatGenerationMetrics(),
            turnContextWindowUsage = null,
        )

        assertEquals("new-thread", projector.snapshot().runtimeThreadId)
        assertEquals(ChatGenerationMetrics(), projector.snapshot().metrics)
    }

    private fun usage(input: Long, cacheRead: Long, output: Long) = AgentTokenUsage(
        totalTokens = input + cacheRead + output,
        inputTokens = input,
        cacheReadTokens = cacheRead,
        cacheWriteTokens = 0,
        cacheUsageReported = true,
        outputTokens = output,
        reasoningOutputTokens = 0,
    )
}
