package com.eleckoi.android.foundation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.eleckoi.android.foundation.design.components.mobileRootContentColor
import com.eleckoi.android.foundation.design.components.mobileRootTopBarContainerColor
import com.eleckoi.android.foundation.design.components.mobileTabBarContainerColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RootChromeColorsTest {
    @Test
    fun `default root surface and chrome match the approved mobile samples`() {
        val theme = AppearanceTheme()

        assertEquals(Color.White.toArgb(), theme.mobileSurface.toArgb())
        assertEquals(Color(0xFFF7F7F8).toArgb(), theme.mobileRootBg.toArgb())
        assertEquals(Color(0xFFFCFCFC).toArgb(), theme.mobileTopbarBg.toArgb())
        assertEquals(Color(0xFFFCFCFC).toArgb(), theme.mobileTabbarBg.toArgb())
        assertEquals(Color(0xFFF7F7F8).toArgb(), mobileRootContentColor(theme).toArgb())
        assertEquals(Color(0xFFFCFCFC).toArgb(), mobileRootTopBarContainerColor(theme).toArgb())
        assertEquals(Color(0xFFFCFCFC).toArgb(), mobileTabBarContainerColor(theme).toArgb())
        assertEquals("", theme.rootBackgroundImagePath)
    }

    @Test
    fun `image palettes allocate independent seed-aware root chrome colors`() {
        val blue = generatedTheme(0xFF2859D7.toInt())
        val rose = generatedTheme(0xFFC53F73.toInt())

        assertNotEquals(blue.mobileTopbarBg.toArgb(), blue.mobileTabbarBg.toArgb())
        assertNotEquals(blue.mobileTopbarBg.toArgb(), rose.mobileTopbarBg.toArgb())
        assertNotEquals(blue.mobileTabbarBg.toArgb(), rose.mobileTabbarBg.toArgb())
    }

    @Test
    fun `dark appearance keeps original canvas and solid black chrome`() {
        val theme = AppearanceTheme().withDarkAppearance(true)

        assertEquals(Color(0xFF13131A).toArgb(), mobileRootContentColor(theme).toArgb())
        assertEquals(Color.Black.toArgb(), mobileRootTopBarContainerColor(theme).toArgb())
        assertEquals(Color.Black.toArgb(), mobileTabBarContainerColor(theme).toArgb())
        assertEquals(Color(0xFF1D1D25).toArgb(), theme.mobileSurface.toArgb())
    }

    private fun generatedTheme(seed: Int): AppearanceTheme = buildTheme(
        seed = Seed(seed, achromatic = false),
        polarity = Polarity(dark = false, alphas = DoubleArray(0)),
        veil = Veil(
            angleDegrees = 90f,
            strength = 0.22f,
            start = 1f,
            mid = 1f,
            end = 1f,
            startHue = 0.0,
            startChroma = 0.0,
            midHue = 0.0,
            midChroma = 0.0,
            endHue = 0.0,
            endChroma = 0.0,
        ),
    )
}
