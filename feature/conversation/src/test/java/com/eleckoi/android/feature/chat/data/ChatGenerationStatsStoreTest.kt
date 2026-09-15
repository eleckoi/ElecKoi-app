package com.eleckoi.android.feature.chat.data

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
}
