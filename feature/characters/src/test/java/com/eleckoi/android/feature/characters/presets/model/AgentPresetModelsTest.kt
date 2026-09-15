package com.eleckoi.android.feature.characters.presets.model

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.DefaultHiddenToolTimelineContent
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.DefaultHistoryCompactionContent
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHiddenToolTimelineEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHistoryCompactionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPresetModelsTest {
    @Test
    fun `default preset includes and enables variables and setting library tools`() {
        val tools = defaultAgentPreset().toolConfiguration.normalized()

        assertEquals(DefaultAgentPresetToolGroupIds, tools.includedGroupIds)
        assertEquals(DefaultAgentPresetToolGroupIds.toSet(), tools.enabledGroupIds)
    }

    @Test
    fun `default preset starts ungrouped without creating a library group`() {
        val preset = defaultAgentPreset()
        val catalog = defaultAgentPresetCatalog()

        assertEquals("", preset.libraryGroupId)
        assertTrue(catalog.groups.isEmpty())
        assertEquals("", catalog.presets.single().libraryGroupId)
        assertEquals(2, catalog.presets.single().entryCount)
        assertEquals(
            DefaultHistoryCompactionContent,
            preset.entries.first { it.isHistoryCompactionEntry() }.content,
        )
        val hiddenTimeline = preset.entries.first { it.isHiddenToolTimelineEntry() }
        assertEquals(DefaultHiddenToolTimelineContent, hiddenTimeline.content)
        assertEquals(SettingLibraryInsertRole.User, hiddenTimeline.insertRole)
        assertEquals(SettingLibraryPosition.AfterToolFlow, hiddenTimeline.position)
        assertEquals("", hiddenTimeline.promptPositionId)
        assertEquals(1, hiddenTimeline.order)
        assertTrue(preset.promptPositions.isEmpty())
    }

    @Test
    fun `required hidden timeline preserves its editable insertion settings`() {
        val customized = defaultAgentPreset().let { preset ->
            preset.copy(
                entries = preset.entries.map { entry ->
                    if (entry.isHiddenToolTimelineEntry()) {
                        entry.copy(
                            position = SettingLibraryPosition.BeforeHistory,
                            insertRole = SettingLibraryInsertRole.Assistant,
                            order = 4,
                        )
                    } else {
                        entry
                    }
                },
            )
        }.withRequiredBuiltIns()

        val hiddenTimeline = customized.entries.single { it.isHiddenToolTimelineEntry() }
        assertEquals(SettingLibraryPosition.BeforeHistory, hiddenTimeline.position)
        assertEquals(SettingLibraryInsertRole.Assistant, hiddenTimeline.insertRole)
        assertEquals(4, hiddenTimeline.order)
    }

    @Test
    fun `compaction template is editable but never joins ordinary preset prompts`() {
        val preset = defaultAgentPreset().let { source ->
            source.copy(
                entries = source.entries.map { entry ->
                    if (entry.isHistoryCompactionEntry()) entry.copy(content = "只保留角色剧情") else entry
                },
            )
        }.withRequiredBuiltIns()

        assertEquals("只保留角色剧情", preset.historyCompactionInstructions())
        assertTrue(preset.asRuntimeSettingLibrary().entries.none { it.isHistoryCompactionEntry() })
        assertTrue(preset.asRuntimeSettingLibrary().entries.none { it.content == "只保留角色剧情" })
    }

    @Test
    fun `normalization preserves ordinary authored entries`() {
        val normalized = defaultAgentPreset().copy(
            entries = defaultAgentPreset().entries + SettingLibraryEntry(
                id = "author-instructions",
                content = "作者自定义提示词",
            ),
        ).withRequiredBuiltIns()

        assertEquals(
            "作者自定义提示词",
            normalized.entries.single { it.id == "author-instructions" }.content,
        )
    }

    @Test
    fun `runtime library namespaces ids without changing the authored root layout`() {
        val preset = AgentPreset(
            id = "claude-longform",
            name = "长篇故事",
            groups = listOf(SettingLibraryGroup(id = "style", name = "文风")),
            entries = listOf(
                SettingLibraryEntry(id = "voice", groupId = "style", title = "叙事声音", enabled = false),
                SettingLibraryEntry(id = "root-note", title = "根目录设定"),
            ),
        )

        val runtime = preset.asRuntimeSettingLibrary()

        assertTrue(runtime.groups.all { it.id.startsWith("agent-preset:claude-longform:") })
        assertEquals(1, runtime.groups.size)
        assertEquals("文风", runtime.groups.single().name)
        assertEquals("", runtime.groups.single().parentId)
        assertEquals(
            "agent-preset:claude-longform:style",
            runtime.entries.first { it.id.endsWith(":voice") }.groupId,
        )
        assertEquals(
            "",
            runtime.entries.first { it.id.endsWith(":root-note") }.groupId,
        )
        assertEquals(false, runtime.entries.first { it.id.endsWith(":voice") }.enabled)
    }
}
