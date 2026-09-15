package com.eleckoi.android.foundation.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeThemeTest {
    @Test
    fun darkModeUsesPcTokensAndRetainsWallpaperState() {
        val dark = AppearanceTheme(
            textureImagePath = "/shared/background.png",
            textureScrim = 0.58f,
        ).withDarkAppearance(true)

        assertTrue(dark.isDark)
        assertEquals(Color(0xFF13131A), dark.mobileChatBg)
        assertEquals(Color(0xFFF2F2F5), dark.mobileText)
        assertEquals("/shared/background.png", dark.textureImagePath)
        assertEquals(0.58f, dark.textureScrim)
    }

    @Test
    fun lightModeRestoresPcLightTokens() {
        val light = AppearanceTheme(isDark = true).withDarkAppearance(false)

        assertFalse(light.isDark)
        assertEquals(Color.White, light.mobileChatBg)
        assertEquals(Color(0xFF111111), light.mobileText)
    }
}
