package com.eleckoi.android.foundation.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePolarityTest {
    @Test
    fun `veil alpha is zero once the readability target is already met`() {
        assertEquals(0.0, neededAlpha(0.50, target = 0.46, veil = 0.93, brighten = true), 0.0)
        assertEquals(0.0, neededAlpha(0.10, target = 0.14, veil = 0.012, brighten = false), 0.0)
    }

    @Test
    fun `area majority wins polarity before mixed-frame veil cost`() {
        assertTrue(choosePolarity(reading(darkWeight = 10.0, lightWeight = 1.0, luminance = 0.2)).dark)
        assertFalse(choosePolarity(reading(darkWeight = 1.0, lightWeight = 10.0, luminance = 0.8)).dark)
    }

    private fun reading(
        darkWeight: Double,
        lightWeight: Double,
        luminance: Double,
    ) = ImageReading(
        samples = listOf(
            WeightedLab(l = 0.2, a = 0.0, b = 0.0, weight = darkWeight),
            WeightedLab(l = 0.8, a = 0.0, b = 0.0, weight = lightWeight),
        ),
        cells = listOf(Cell(x = 0.5, y = 0.5, luminance = luminance, a = 0.0, b = 0.0)),
        colorfulness = 0.0,
        opaquePixels = IntArray(0),
    )
}
