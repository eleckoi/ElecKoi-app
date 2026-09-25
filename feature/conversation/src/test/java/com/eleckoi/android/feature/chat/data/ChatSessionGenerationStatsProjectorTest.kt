package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentTokenUsage
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
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
                    toolDurationMillis = 30_000,
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
        val toolCall = AgentSessionEvent.WorkItemStarted(
            threadId = "thread",
            turnId = "turn-1001",
            itemId = "tool-1",
            type = AgentWorkItemType.Tool,
            label = "lookup",
            startedAtMillis = 120,
        )
        turn.accept(toolCall)
        projector.accept(toolCall, turn.snapshot(), turn.contextWindowUsage())
        val toolResult = AgentSessionEvent.WorkItemCompleted(
            threadId = "thread",
            turnId = "turn-1001",
            itemId = "tool-1",
            type = AgentWorkItemType.Tool,
            status = AgentWorkStatus.Completed,
            completedAtMillis = 160,
        )
        turn.accept(toolResult)
        projector.accept(toolResult, turn.snapshot(), turn.contextWindowUsage())
        val stepEnd = AgentSessionEvent.StepCompleted("thread", "turn-1001", 1, 200)
        turn.accept(stepEnd)
        projector.accept(stepEnd, turn.snapshot(), turn.contextWindowUsage())

        val snapshot = projector.snapshot()
        assertEquals(1_001, snapshot.metrics.turns)
        assertEquals(2_501, snapshot.metrics.steps)
        assertEquals(2_501, snapshot.stepTotalsByTurn["1001"])
        val secondStep = AgentSessionEvent.StepCompleted("thread", "turn-1001", 2, 240)
        turn.accept(secondStep)
        projector.accept(secondStep, turn.snapshot(), turn.contextWindowUsage())
        assertEquals(2_502, projector.snapshot().stepTotalsByTurn["1001"])
        assertEquals(30_040L, snapshot.metrics.toolDurationMillis)
        assertEquals(10_500_000L, snapshot.metrics.billedInputTokens)
        assertEquals(52_000L, snapshot.metrics.outputTokens)
    }

    @Test
    fun `a fresh DSH runtime thread keeps the visible conversation totals`() {
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
        assertEquals(ChatGenerationMetrics(turns = 50, inputTokens = 3_000_000), projector.snapshot().metrics)
    }

    @Test
    fun `regeneration keeps long conversation totals when DSH starts a new thread`() {
        val before = ChatSessionGenerationStats(
            runtimeThreadId = "old-thread",
            metrics = ChatGenerationMetrics(
                turns = 58,
                steps = 367,
                inputTokens = 300_000,
                cacheReadTokens = 2_700_000,
                toolDurationMillis = 30_000,
            ),
            contextWindowUsage = com.eleckoi.android.feature.chat.model.ChatContextWindowUsage(
                latestTokens = 70_000,
                totalTokens = 3_000_000,
                modelContextWindow = 1_000_000,
            ),
        )
        val baseline = before.forRegeneration(retainedTurns = 58, retainedSteps = 364)
        assertEquals("", baseline.runtimeThreadId)
        assertEquals(null, baseline.contextWindowUsage)
        assertEquals(before.metrics.copy(steps = 364), baseline.metrics)

        val projector = ChatSessionGenerationStatsProjector(baseline)
        val turn = ChatTurnMetricsCollector()
        val started = AgentSessionEvent.TurnStarted("new-thread", "new-turn", 100)
        turn.accept(started)
        projector.accept(started, turn.snapshot(), turn.contextWindowUsage())
        assertEquals(58, projector.snapshot().metrics.turns)
        assertEquals(364, projector.snapshot().metrics.steps)

        val step = AgentSessionEvent.StepStarted("new-thread", "new-turn", 1, 110)
        turn.accept(step)
        projector.accept(step, turn.snapshot(), turn.contextWindowUsage())
        assertEquals(58, projector.snapshot().metrics.turns)
        val usage = usage(input = 2_000, cacheRead = 28_000, output = 500)
        val usageEvent = AgentSessionEvent.TokenUsageUpdated(
            "new-thread", "new-turn", 1, usage, usage, 1_000_000,
        )
        turn.accept(usageEvent)
        projector.accept(usageEvent, turn.snapshot(), turn.contextWindowUsage())
        assertEquals(58, projector.snapshot().metrics.turns)
        val completed = AgentSessionEvent.StepCompleted("new-thread", "new-turn", 1, 200)
        turn.accept(completed)
        projector.accept(completed, turn.snapshot(), turn.contextWindowUsage())

        val after = projector.snapshot()
        assertEquals("new-thread", after.runtimeThreadId)
        assertEquals(58, after.metrics.turns)
        assertEquals(365, after.metrics.steps)
        assertEquals(3_030_000L, after.metrics.billedInputTokens)
        assertEquals(30_000L, after.metrics.toolDurationMillis)
        assertEquals(30_000L, after.contextWindowUsage?.latestTokens)

        val nextTurn = ChatSessionGenerationStatsProjector(after)
        nextTurn.accept(
            AgentSessionEvent.StepCompleted("another-thread", "later-turn", 1, 300),
            ChatGenerationMetrics(turns = 1, steps = 1),
            null,
        )
        assertEquals(59, nextTurn.snapshot().metrics.turns)
        assertEquals(366, nextTurn.snapshot().metrics.steps)
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
