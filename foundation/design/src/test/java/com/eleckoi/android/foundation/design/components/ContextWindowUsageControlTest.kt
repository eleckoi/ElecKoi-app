package com.eleckoi.android.foundation.design.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowUsageControlTest {
    @Test
    fun `native dsh usage is formatted like the pc inspector`() {
        val presentation = ContextWindowUsage(
            latestTokens = 5_400,
            modelContextWindow = 1_000_000,
            systemTokens = 21,
            toolsTokens = 551,
            messageTokens = 4_828,
        ).toContextWindowUsagePresentation()

        assertEquals("0.5%", presentation.percentLabel)
        assertEquals("~5.4K / 1M", presentation.tokenRatioLabel)
        assertEquals(0.0054f, presentation.usedFraction, 0.00001f)
        assertTrue(presentation.hasNativeSample)
        assertTrue(presentation.hasBreakdown)
        assertEquals(0L, presentation.unclassifiedTokens ?: 0L)
    }

    @Test
    fun `missing dsh sample never invents a model window`() {
        val presentation = ContextWindowUsage(totalTokens = 18_000)
            .toContextWindowUsagePresentation()

        assertEquals("—", presentation.percentLabel)
        assertEquals("— / —", presentation.tokenRatioLabel)
        assertFalse(presentation.hasNativeSample)
        assertFalse(presentation.hasBreakdown)
        assertEquals(null, presentation.unclassifiedTokens)
    }

    @Test
    fun `provider calibrated total exposes the unclassified remainder`() {
        val presentation = ContextWindowUsage(
            latestTokens = 17_091,
            modelContextWindow = 1_000_000,
            systemTokens = 0,
            toolsTokens = 1_427,
            messageTokens = 6_386,
        ).toContextWindowUsagePresentation()

        assertEquals(9_278L, presentation.unclassifiedTokens)
    }

    @Test
    fun `token units use the same uppercase notation as pc`() {
        assertEquals("999", formatTokenCount(999))
        assertEquals("1.4K", formatTokenCount(1_427))
        assertEquals("1M", formatTokenCount(1_000_000))
    }
}
