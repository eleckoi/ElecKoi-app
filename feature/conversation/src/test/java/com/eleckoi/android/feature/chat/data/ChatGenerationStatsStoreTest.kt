package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.feature.chat.model.ChatContextWindowUsage
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatGenerationStatsStoreTest {
    @Test
    fun `persists and restores one projection per DSH runtime thread`() {
        val root = Files.createTempDirectory("chat-generation-stats").toFile()
        try {
            val store = ChatGenerationStatsStore(root)
            val expected = ChatSessionGenerationStats(
                runtimeThreadId = "thread-1",
                metrics = ChatGenerationMetrics(
                    turns = 120,
                    steps = 360,
                    inputTokens = 400_000,
                    cacheReadTokens = 3_000_000,
                    outputTokens = 37_300,
                ),
                contextWindowUsage = ChatContextWindowUsage(850_000, 3_437_300, 1_000_000),
                stepTotalsByTurn = mapOf("119" to 357, "120" to 360),
            )

            store.persist("conversation-1", expected)

            assertEquals(expected, ChatGenerationStatsStore(root).load("conversation-1", "thread-1"))
            assertEquals("thread-2", store.load("conversation-1", "thread-2").runtimeThreadId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `discard removes only obsolete thread snapshots`() {
        val root = Files.createTempDirectory("chat-generation-stats-discard").toFile()
        try {
            val store = ChatGenerationStatsStore(root)
            store.persist("conversation", ChatSessionGenerationStats("old"))
            store.persist("conversation", ChatSessionGenerationStats("selected"))

            store.discardThreads("conversation", setOf("old", "selected"), "selected")

            assertEquals("selected", store.load("conversation", "selected").runtimeThreadId)
            assertFalse(root.walkTopDown().any { it.isFile && it.name == "old.json" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `regeneration baseline survives reload and transfers to the new DSH thread`() {
        val root = Files.createTempDirectory("chat-generation-regeneration").toFile()
        try {
            val store = ChatGenerationStatsStore(root)
            val old = ChatSessionGenerationStats(
                runtimeThreadId = "old-thread",
                metrics = ChatGenerationMetrics(turns = 58, steps = 367, inputTokens = 3_000_000),
            )
            store.persist("conversation", old)
            val baseline = old.forRegeneration(retainedTurns = 58, retainedSteps = 365)
            store.replaceWithRegenerationBaseline("conversation", baseline)

            val reopened = ChatGenerationStatsStore(root)
            assertEquals(baseline, reopened.load("conversation", ""))
            assertEquals(
                ChatGenerationMetrics(),
                reopened.load("conversation", "old-thread").metrics,
            )

            val replacement = ChatSessionGenerationStatsProjector(reopened.load("conversation", ""))
            replacement.accept(
                AgentSessionEvent.StepCompleted("new-thread", "replacement-turn", 1, 100),
                ChatGenerationMetrics(turns = 1, steps = 1),
                null,
            )
            val next = replacement.snapshot()
            assertEquals(58, next.metrics.turns)
            assertEquals(366, next.metrics.steps)
            reopened.persist("conversation", next)
            assertEquals(next, ChatGenerationStatsStore(root).load("conversation", "new-thread"))
            assertEquals(ChatSessionGenerationStats(), reopened.load("conversation", ""))

            val continued = ChatSessionGenerationStatsProjector(
                ChatGenerationStatsStore(root).load("conversation", "new-thread"),
            )
            continued.accept(
                AgentSessionEvent.StepCompleted("rotated-thread", "ordinary-turn", 1, 200),
                ChatGenerationMetrics(turns = 1, steps = 1),
                null,
            )
            reopened.persist("conversation", continued.snapshot())
            val afterOrdinaryTurn = ChatGenerationStatsStore(root).load("conversation", "rotated-thread")
            assertEquals(59, afterOrdinaryTurn.metrics.turns)
            assertEquals(367, afterOrdinaryTurn.metrics.steps)
            assertEquals(367, afterOrdinaryTurn.stepTotalsByTurn["59"])
        } finally {
            root.deleteRecursively()
        }
    }
}
