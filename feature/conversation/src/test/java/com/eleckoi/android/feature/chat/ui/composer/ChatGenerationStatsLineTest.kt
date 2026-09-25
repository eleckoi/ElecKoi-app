package com.eleckoi.android.feature.chat.ui.composer

import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGenerationStatsLineTest {
    @Test
    fun `shows two compact statistics while the context circle stays in the composer`() {
        val groups = generationStatsGroups(
            ChatGenerationMetrics(
                turns = 1,
                steps = 2,
                llmDurationMillis = 31_600,
                toolDurationMillis = 800,
                firstTokenDelayMillis = 26_000,
                firstTokenSamples = 2,
                decodeDurationMillis = 5_645,
                decodeOutputTokens = 429,
                inputTokens = 18_700,
                cacheReadTokens = 18_300,
                cacheUsageReported = true,
                outputTokens = 429,
            ),
        )

        assertEquals(
            listOf(
                "1 轮 2 步 · 76 tok/s",
                "37.4K tok · 缓存命中 49%",
            ),
            groups,
        )
    }

    @Test
    fun `retains the last visible statistics while regeneration warms up`() {
        val previous = ChatGenerationMetrics(
            turns = 1,
            steps = 5,
            llmDurationMillis = 4_800,
            toolDurationMillis = 500,
        )

        assertEquals(
            previous,
            retainVisibleGenerationMetrics(
                previous = previous,
                next = ChatGenerationMetrics(),
            ),
        )
    }

    @Test
    fun `replaces retained statistics as soon as the next run becomes visible`() {
        val previous = ChatGenerationMetrics(turns = 1, steps = 5)
        val next = ChatGenerationMetrics(turns = 1, steps = 1, llmDurationMillis = 900)

        assertEquals(
            next,
            retainVisibleGenerationMetrics(previous = previous, next = next),
        )
    }

    @Test
    fun `does not invent a statistics line before the session has visible metrics`() {
        val empty = ChatGenerationMetrics()

        assertEquals(
            empty,
            retainVisibleGenerationMetrics(previous = empty, next = empty),
        )
        assertEquals(emptyList<String>(), generationStatsGroups(empty))
    }
}
