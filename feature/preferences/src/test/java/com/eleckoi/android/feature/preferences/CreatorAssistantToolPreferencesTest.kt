package com.eleckoi.android.feature.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class CreatorAssistantToolPreferencesTest {
    @Test
    fun `missing creator assistant preference keeps product default unresolved`() {
        val preferences = mutablePreferencesOf().toUiPreferences()

        assertEquals(null, preferences.creatorAssistantEnabledToolGroupIds)
    }

    @Test
    fun `creator assistant enabled tool groups round trip through preference mapping`() {
        val enabled = setOf("builtin:creator", "builtin:web")
        val preferences = mutablePreferencesOf(
            CreatorAssistantEnabledToolGroupIds to enabled,
        ).toUiPreferences()

        assertEquals(enabled, preferences.creatorAssistantEnabledToolGroupIds)
    }

    @Test
    fun `creator assistant image model selection round trips independently`() {
        val preferences = mutablePreferencesOf(
            CreatorAssistantImageModelConfigId to "image-config-1",
        ).toUiPreferences()

        assertEquals("image-config-1", preferences.creatorAssistantImageModelConfigId)
    }
}
