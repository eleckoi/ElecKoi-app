package com.eleckoi.android.foundation.design.components

import com.eleckoi.android.foundation.design.AppearanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowUsageControlTest {
    @Test
    fun `context usage trigger opens and closes as a toggle`() {
        assertTrue(toggleContextWindowUsageExpanded(expanded = false))
        assertFalse(toggleContextWindowUsageExpanded(expanded = true))
    }

    @Test
    fun `context popup keeps the theme hairline opacity`() {
        val appearance = AppearanceTheme()

        assertEquals(appearance.mobileLine, contextUsagePopupBorderColor(appearance))
        assertTrue(contextUsagePopupBorderColor(appearance).alpha < 0.06f)
    }

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
    }

    @Test
    fun `missing dsh sample never invents a model window`() {
        val presentation = ContextWindowUsage(totalTokens = 18_000)
            .toContextWindowUsagePresentation()

        assertEquals("—", presentation.percentLabel)
        assertEquals("— / —", presentation.tokenRatioLabel)
        assertFalse(presentation.hasNativeSample)
        assertFalse(presentation.hasBreakdown)
    }

    @Test
    fun `token units use the same uppercase notation as pc`() {
        assertEquals("999", formatTokenCount(999))
        assertEquals("1.4K", formatTokenCount(1_427))
        assertEquals("1M", formatTokenCount(1_000_000))
    }
}
