package com.eleckoi.android.feature.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ListCharacterArtworkTest {
    @Test
    fun `cover artwork is the mobile default`() {
        assertEquals(ListCharacterArtwork.Cover, UiPreferences().listCharacterArtwork)
        assertEquals(ListCharacterArtwork.Cover, mutablePreferencesOf().toUiPreferences().listCharacterArtwork)
    }

    @Test
    fun `pc compatible storage values map to list artwork`() {
        assertEquals(
            ListCharacterArtwork.Cover,
            mutablePreferencesOf(SidebarCharacterArtwork to "cover").toUiPreferences().listCharacterArtwork,
        )
        assertEquals(
            ListCharacterArtwork.Avatar,
            mutablePreferencesOf(SidebarCharacterArtwork to "avatar").toUiPreferences().listCharacterArtwork,
        )
        assertEquals(ListCharacterArtwork.Cover, ListCharacterArtwork.fromStorageKey("unknown"))
    }
}
