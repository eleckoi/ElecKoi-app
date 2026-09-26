package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryContentMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingLibraryEjsCatalogTest {
    @Test
    fun `EJS references stay out of the Agent visible catalog`() {
        val ordinary = dynamicEntry(
            id = "setting",
            title = "剧情设定",
            mode = SettingLibraryDynamicMode.Standard,
        )
        val reference = dynamicEntry(
            id = "reference",
            title = "第一章",
            mode = SettingLibraryDynamicMode.EjsReference,
        )

        val catalog = settingLibraryAgentCatalogPreview(
            entries = listOf(ordinary, reference),
            groups = emptyList(),
        )

        assertEquals(listOf(ordinary.id), catalog.map { it.id })
    }

    @Test
    fun `selection hints remain only on unconditional plain selectable entries`() {
        fun selectable(id: String) = SettingLibraryEntry(
            id = id,
            title = id,
            content = "正文",
            agentSelectionHint = "由 AI 判断",
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            agentReadStrategy = SettingLibraryAgentReadStrategy.Normal,
        )
        val plain = selectable("普通")
        val keyword = selectable("关键词").copy(agentReadStrategy = SettingLibraryAgentReadStrategy.Keyword)
        val ejs = selectable("变量").copy(contentMode = SettingLibraryContentMode.Ejs)
        val entries = listOf(plain, keyword, ejs)

        val preview = settingLibraryAgentCatalogPreview(entries, emptyList())
        assertEquals(listOf("由 AI 判断", "", ""), preview.map { it.selectionHint })
        assertEquals(listOf("由 AI 判断", "", ""),
            SettingLibraryAgentContextProjector.project("character", SettingLibrary("character", entries = entries))
                .readableEntries.map { it.selectionHint })
        val tree = renderSettingLibraryAgentCatalogPreviewTree(preview)
        org.junit.Assert.assertTrue(tree.contains("变量 [EJS 条件]"))
    }

    private fun dynamicEntry(
        id: String,
        title: String,
        mode: SettingLibraryDynamicMode,
    ) = SettingLibraryEntry(
        id = id,
        title = title,
        content = "正文",
        enabled = true,
        triggerMode = SettingLibraryTriggerMode.AgentTool,
        agentReadStrategy = SettingLibraryAgentReadStrategy.Keyword,
        dynamicMode = mode,
    )
}
