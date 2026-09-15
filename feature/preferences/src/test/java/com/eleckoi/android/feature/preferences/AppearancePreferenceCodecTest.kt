package com.eleckoi.android.feature.preferences

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.eleckoi.android.foundation.design.AppearanceTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearancePreferenceCodecTest {
    @Test
    fun `root chrome colors survive preference round trip`() {
        val expected = AppearanceTheme(
            mobileRootBg = Color(0xFFDED9D2),
            mobileTopbarBg = Color(0xFFE5F0FF),
            mobileTabbarBg = Color(0xFFE9E7EE),
        )
        val preferences = mutablePreferencesOf()

        preferences.writeAppearanceTheme(expected)
        val actual = appearanceThemeFromPreferences(preferences)

        assertEquals(expected.mobileRootBg.toArgb(), actual.mobileRootBg.toArgb())
        assertEquals(expected.mobileTopbarBg.toArgb(), actual.mobileTopbarBg.toArgb())
        assertEquals(expected.mobileTabbarBg.toArgb(), actual.mobileTabbarBg.toArgb())
    }

    @Test
    fun `stored old fixed dark palette keeps original canvas and black chrome without losing wallpaper`() {
        val preferences = mutablePreferencesOf()
        preferences[AppearanceThemeStored] = true
        preferences[AppearanceIsDark] = true
        preferences[MobileBg] = 0xFF13131A.toInt()
        preferences[MobileSurface] = 0xFF1D1D25.toInt()
        preferences[MobileTopbarBg] = 0xFF1A1A21.toInt()
        preferences[MobileTabbarBg] = 0xFF1A1A21.toInt()
        preferences[RootBackgroundImagePath] = "/chosen/background.png"
        preferences[RootBackgroundOpacity] = 0.61f

        val actual = appearanceThemeFromPreferences(preferences)

        assertEquals(Color(0xFF13131A), actual.mobileRootBg)
        assertEquals(Color(0xFF1D1D25), actual.mobileSurface)
        assertEquals(Color.Black, actual.mobileTopbarBg)
        assertEquals(Color.Black, actual.mobileTabbarBg)
        assertEquals("/chosen/background.png", actual.rootBackgroundImagePath)
        assertEquals(0.61f, actual.rootBackgroundOpacity)
    }

    @Test
    fun `old image palette without root token keeps its own canvas`() {
        val preferences = mutablePreferencesOf()
        preferences[AppearanceThemeStored] = true
        preferences[AppearanceIsDark] = true
        preferences[MobileBg] = 0xFF252329.toInt()
        preferences[MobileSurface] = 0xFF302E34.toInt()
        preferences[MobileTopbarBg] = 0xFF30242D.toInt()

        val actual = appearanceThemeFromPreferences(preferences)

        assertEquals(Color(0xFF252329), actual.mobileRootBg)
        assertEquals(Color(0xFF30242D), actual.mobileTopbarBg)
    }

    @Test
    fun `old fixed light palette uses current neutral root canvas`() {
        val preferences = mutablePreferencesOf()
        preferences[AppearanceThemeStored] = true
        preferences[MobileBg] = 0xFFF0F3F6.toInt()
        preferences[MobileSurface] = Color.White.toArgb()

        assertEquals(Color(0xFFF7F7F8), appearanceThemeFromPreferences(preferences).mobileRootBg)
    }
}
