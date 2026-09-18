package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentTokenUsage
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnMetricsCollectorTest {
    @Test
    fun `keeps native timing and explicit cache accounting separate`() {
        val collector = ChatTurnMetricsCollector()
        collector.accept(AgentSessionEvent.StepStarted("thread", "turn", step = 1, startedAtMillis = 100))
        collector.accept(
            AgentSessionEvent.AssistantDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "assistant-turn-1",
                delta = "首个 token",
                step = 1,
                observedAtMillis = 150,
            ),
        )
        collector.accept(
            AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "assistant-turn-1",
                type = AgentWorkItemType.AssistantMessage,
                status = AgentWorkStatus.Completed,
                completedAtMillis = 350,
                step = 1,
            ),
        )
        collector.accept(
            AgentSessionEvent.TokenUsageUpdated(
                threadId = "thread",
                turnId = "turn",
                step = 1,
                total = usage(input = 10, cacheRead = 90, output = 20, cacheReported = true),
                last = usage(input = 10, cacheRead = 90, output = 20, cacheReported = true),
                modelContextWindow = 128_000L,
            ),
        )
        assertEquals(100L, collector.snapshot().billedInputTokens)
        assertEquals(20L, collector.snapshot().outputTokens)
        collector.accept(
            AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "tool-1",
                type = AgentWorkItemType.Tool,
                label = "查询",
                startedAtMillis = 360,
            ),
        )
        collector.accept(
            AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "tool-1",
                type = AgentWorkItemType.Tool,
                status = AgentWorkStatus.Completed,
                completedAtMillis = 460,
            ),
        )
        collector.accept(AgentSessionEvent.StepCompleted("thread", "turn", step = 1, completedAtMillis = 470))

        val metrics = collector.snapshot()
        assertEquals(1, metrics.turns)
        assertEquals(1, metrics.steps)
        assertEquals(250L, metrics.llmDurationMillis)
        assertEquals(100L, metrics.toolDurationMillis)
        assertEquals(50L, metrics.firstTokenDelayMillis)
        assertEquals(200L, metrics.decodeDurationMillis)
        assertEquals(20L, metrics.decodeOutputTokens)
        assertEquals("90", metrics.cacheHitPercent)
        assertEquals(100L, collector.contextWindowUsage()?.latestTokens)
        assertEquals(120L, collector.contextWindowUsage()?.totalTokens)
        assertEquals(128_000L, collector.contextWindowUsage()?.modelContextWindow)
    }

    @Test
    fun `native DSH projection replaces stale request pressure after compaction`() {
        val collector = ChatTurnMetricsCollector()
        collector.accept(
            AgentSessionEvent.TokenUsageUpdated(
                threadId = "thread",
                turnId = "turn",
                step = 1,
                total = usage(input = 5_000, cacheRead = 22_000, output = 400, cacheReported = true),
                last = usage(input = 5_000, cacheRead = 22_000, output = 400, cacheReported = true),
                modelContextWindow = 1_000_000L,
            ),
        )

        collector.accept(
            AgentSessionEvent.ContextWindowUpdated(
                threadId = "thread",
                turnId = "turn",
                pressureTokens = 27_000L,
                projectedTokens = 5_100L,
                modelContextWindow = 1_000_000L,
                systemTokens = 21L,
                toolsTokens = 551L,
                messageTokens = 4_528L,
            ),
        )

        assertEquals(5_100L, collector.contextWindowUsage()?.latestTokens)
        assertEquals(27_400L, collector.contextWindowUsage()?.totalTokens)
        assertEquals(1_000_000L, collector.contextWindowUsage()?.modelContextWindow)
        assertEquals(21L, collector.contextWindowUsage()?.systemTokens)
        assertEquals(551L, collector.contextWindowUsage()?.toolsTokens)
        assertEquals(4_528L, collector.contextWindowUsage()?.messageTokens)
    }

    @Test
    fun `matches DSH zero cache percentage when cache buckets are absent`() {
        val collector = ChatTurnMetricsCollector()
        collector.accept(AgentSessionEvent.StepStarted("thread", "turn", step = 1, startedAtMillis = 100))
        collector.accept(
            AgentSessionEvent.TokenUsageUpdated(
                threadId = "thread",
                turnId = "turn",
                step = 1,
                total = usage(input = 10, cacheRead = 0, output = 2, cacheReported = false),
                last = usage(input = 10, cacheRead = 0, output = 2, cacheReported = false),
                modelContextWindow = null,
            ),
        )
        collector.accept(AgentSessionEvent.StepCompleted("thread", "turn", step = 1, completedAtMillis = 200))

        assertEquals("0", collector.snapshot().cacheHitPercent)
        assertTrue(collector.snapshot().billedInputTokens > 0L)
    }

    @Test
    fun `reasoning delta starts DSH first token timing`() {
        val collector = ChatTurnMetricsCollector()
        collector.accept(AgentSessionEvent.StepStarted("thread", "turn", step = 1, startedAtMillis = 100))
        collector.accept(
            AgentSessionEvent.ReasoningTextDelta(
                threadId = "thread",
                turnId = "turn",
                itemId = "reasoning-turn-1",
                contentIndex = 0,
                delta = "先检查",
                step = 1,
                observedAtMillis = 140,
            ),
        )
        collector.accept(
            AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "assistant-turn-1",
                type = AgentWorkItemType.AssistantMessage,
                status = AgentWorkStatus.Completed,
                completedAtMillis = 300,
                step = 1,
            ),
        )
        collector.accept(
            AgentSessionEvent.TokenUsageUpdated(
                threadId = "thread",
                turnId = "turn",
                step = 1,
                total = usage(input = 100, cacheRead = 0, output = 25, cacheReported = false),
                last = usage(input = 100, cacheRead = 0, output = 25, cacheReported = false),
                modelContextWindow = null,
            ),
        )
        collector.accept(AgentSessionEvent.StepCompleted("thread", "turn", step = 1, completedAtMillis = 310))

        assertEquals(40L, collector.snapshot().firstTokenDelayMillis)
        assertEquals(160L, collector.snapshot().decodeDurationMillis)
        assertEquals(25L, collector.snapshot().decodeOutputTokens)
    }

    @Test
    fun `DSH compaction duration is not reported as tool duration`() {
        val collector = ChatTurnMetricsCollector()
        collector.accept(
            AgentSessionEvent.WorkItemStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "compaction",
                type = AgentWorkItemType.ContextCompaction,
                label = "正在自动压缩",
                startedAtMillis = 100,
            ),
        )
        collector.accept(
            AgentSessionEvent.WorkItemCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "compaction",
                type = AgentWorkItemType.ContextCompaction,
                status = AgentWorkStatus.Completed,
                completedAtMillis = 500,
            ),
        )

        assertEquals(0L, collector.snapshot().toolDurationMillis)
    }

    @Test
    fun `replaces repeated usage samples for the same DSH step`() {
        val collector = ChatTurnMetricsCollector()
        collector.accept(AgentSessionEvent.StepStarted("thread", "turn", step = 1, startedAtMillis = 100))
        collector.accept(
            AgentSessionEvent.TokenUsageUpdated(
                "thread",
                "turn",
                step = 1,
                total = usage(input = 100, cacheRead = 900, output = 10, cacheReported = true),
                last = usage(input = 100, cacheRead = 900, output = 10, cacheReported = true),
                modelContextWindow = 1_000_000,
            ),
        )
        collector.accept(
            AgentSessionEvent.TokenUsageUpdated(
                "thread",
                "turn",
                step = 1,
                total = usage(input = 120, cacheRead = 980, output = 20, cacheReported = true),
                last = usage(input = 120, cacheRead = 980, output = 20, cacheReported = true),
                modelContextWindow = 1_000_000,
            ),
        )

        assertEquals(1_100L, collector.snapshot().billedInputTokens)
        assertEquals(20L, collector.snapshot().outputTokens)
    }

    @Test
    fun `near complete cache hits do not round to a false one hundred percent`() {
        val metrics = ChatGenerationMetrics(
            inputTokens = 5,
            cacheReadTokens = 9_995,
            cacheUsageReported = true,
        )

        assertEquals("99.95", metrics.cacheHitPercent)
        assertEquals(
            "100",
            metrics.copy(inputTokens = 0, cacheReadTokens = 10_000).cacheHitPercent,
        )
    }

    private fun usage(
        input: Long,
        cacheRead: Long,
        output: Long,
        cacheReported: Boolean,
    ) = AgentTokenUsage(
        totalTokens = input + cacheRead + output,
        inputTokens = input,
        cacheReadTokens = cacheRead,
        cacheWriteTokens = 0,
        cacheUsageReported = cacheReported,
        outputTokens = output,
        reasoningOutputTokens = 0,
    )
}
