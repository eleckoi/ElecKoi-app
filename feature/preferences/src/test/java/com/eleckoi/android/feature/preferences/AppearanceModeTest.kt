package com.eleckoi.android.feature.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeTest {
    @Test
    fun storageValuesAndLightDefaultArePreserved() {
        val defaults = mutablePreferencesOf().toUiPreferences()

        assertEquals(AppearanceMode.Light, defaults.appearanceMode)
        assertEquals(NewCharacterBackground.Character, defaults.newCharacterBackground)
        assertEquals(AppearanceMode.Dark, AppearanceMode.fromStorageKey("dark"))
        assertEquals(AppearanceMode.System, AppearanceMode.fromStorageKey("system"))
        assertEquals(NewCharacterBackground.App, NewCharacterBackground.fromStorageKey("app"))
    }

    @Test
    fun onlySystemModeFollowsTheDevice() {
        assertFalse(AppearanceMode.Light.resolvesDark(systemDark = true))
        assertTrue(AppearanceMode.Dark.resolvesDark(systemDark = false))
        assertTrue(AppearanceMode.System.resolvesDark(systemDark = true))
        assertFalse(AppearanceMode.System.resolvesDark(systemDark = false))
    }

    @Test
    fun storedAppearanceAndBackgroundModesAreMapped() {
        val preferences = mutablePreferencesOf(
            AppearanceModeKey to "dark",
            NewCharacterBackgroundKey to "app",
        ).toUiPreferences()

        assertEquals(AppearanceMode.Dark, preferences.appearanceMode)
        assertEquals(NewCharacterBackground.App, preferences.newCharacterBackground)
    }
}
