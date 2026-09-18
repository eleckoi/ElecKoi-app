package com.eleckoi.android.feature.appfont.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFontDefaultsTest {

    @Test
    fun absentPreferenceUsesBundled975Yuan() {
        assertEquals(AppFontCatalog.DefaultFontId, resolveSelectedFontId(null))
    }

    @Test
    fun explicitSystemPreferenceSurvivesUpgrade() {
        assertEquals(AppFontCatalog.SystemFontId, resolveSelectedFontId(""))
    }

    @Test
    fun explicitCustomPreferenceSurvivesUpgrade() {
        assertEquals("my-font.ttf", resolveSelectedFontId("my-font.ttf"))
    }

    @Test
    fun defaultFontIsBundledAndNotOfferedAsADownload() {
        val defaultEntry = AppFontCatalog.entryFor(AppFontCatalog.DefaultFontId)

        assertNotNull(defaultEntry)
        assertTrue(defaultEntry?.bundledAssetPath?.isNotBlank() == true)
        assertFalse(AppFontCatalog.downloadableEntries.any { it.id == AppFontCatalog.DefaultFontId })
    }
}
