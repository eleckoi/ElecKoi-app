package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLibraryAgentCatalogTest {
    @Test
    fun `author preview contains no markdown suffix`() {
        val preview = renderSettingLibraryAgentCatalogPreviewTree(
            listOf(
                SettingLibraryAgentCatalogItem(
                    id = "language",
                    path = "世界观/语言与行为",
                    selectionHint = "涉及角色说话方式时启用。",
                    readStrategy = SettingLibraryAgentReadStrategy.Required,
                ),
            ),
        )

        assertTrue(preview.contains("语言与行为"))
        assertTrue(preview.contains("[必读]"))
        assertFalse(preview.contains(".md"))
        assertFalse(preview.contains("language"))
    }
}
