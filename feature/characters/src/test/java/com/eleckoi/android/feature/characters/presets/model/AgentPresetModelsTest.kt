package com.eleckoi.android.feature.characters.presets.model

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.DefaultHiddenToolTimelineContent
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.DefaultHistoryCompactionContent
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.HiddenToolTimelinePromptPositionId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPositionSide
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
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
        assertEquals(SettingLibraryPosition.InsertPoint4, hiddenTimeline.position)
        assertEquals(HiddenToolTimelinePromptPositionId, hiddenTimeline.promptPositionId)
        assertEquals(1, hiddenTimeline.order)
        val hiddenTimelinePosition = preset.promptPositions.single()
        assertEquals(HiddenToolTimelinePromptPositionId, hiddenTimelinePosition.id)
        assertEquals("隐藏工具时间线", hiddenTimelinePosition.name)
        assertEquals(SettingLibraryPosition.InsertPoint4, hiddenTimelinePosition.anchor)
        assertEquals(SettingLibraryPromptPositionSide.BeforeSettingPosition, hiddenTimelinePosition.side)
        assertEquals(1, hiddenTimelinePosition.order)
    }

    @Test
    fun `required hidden timeline preserves its editable insertion settings`() {
        val customized = defaultAgentPreset().let { preset ->
            preset.copy(
                entries = preset.entries.map { entry ->
                    if (entry.isHiddenToolTimelineEntry()) {
                        entry.copy(
                            position = SettingLibraryPosition.InsertPoint1,
                            promptPositionId = "",
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
        assertEquals(SettingLibraryPosition.InsertPoint1, hiddenTimeline.position)
        assertEquals(SettingLibraryInsertRole.Assistant, hiddenTimeline.insertRole)
        assertEquals(4, hiddenTimeline.order)
    }

    @Test
    fun `legacy hidden timeline receives an editable position below latest user input`() {
        val normalized = AgentPreset(
            id = "legacy",
            name = "旧预设",
            entries = listOf(
                SettingLibraryEntry(
                    id = "built-in-hidden-tool-timeline",
                    title = "隐藏工具时间线",
                    triggerMode = SettingLibraryTriggerMode.Always,
                    position = SettingLibraryPosition.InsertPoint5,
                    insertRole = SettingLibraryInsertRole.User,
                ),
            ),
        ).withRequiredBuiltIns()

        val hiddenTimeline = normalized.entries.single { it.isHiddenToolTimelineEntry() }
        val position = normalized.promptPositions.single()
        assertEquals(HiddenToolTimelinePromptPositionId, hiddenTimeline.promptPositionId)
        assertEquals(SettingLibraryPosition.InsertPoint4, hiddenTimeline.position)
        assertEquals(HiddenToolTimelinePromptPositionId, position.id)
        assertEquals(SettingLibraryPosition.InsertPoint4, position.anchor)
        assertEquals(SettingLibraryPromptPositionSide.BeforeSettingPosition, position.side)
    }

    @Test
    fun `hidden timeline position remains user editable and is not recreated after deletion`() {
        val customized = defaultAgentPreset().let { preset ->
            preset.copy(
                promptPositions = preset.promptPositions.map { position ->
                    position.copy(
                        name = "模型工具记录",
                        anchor = SettingLibraryPosition.InsertPoint2,
                        side = SettingLibraryPromptPositionSide.AfterSettingPosition,
                    )
                },
            )
        }.withRequiredBuiltIns()

        val customPosition = customized.promptPositions.single()
        val customizedEntry = customized.entries.single { it.isHiddenToolTimelineEntry() }
        assertEquals("模型工具记录", customPosition.name)
        assertEquals(SettingLibraryPosition.InsertPoint2, customPosition.anchor)
        assertEquals(SettingLibraryPromptPositionSide.AfterSettingPosition, customPosition.side)
        assertEquals(SettingLibraryPosition.InsertPoint2, customizedEntry.position)

        val deleted = customized.copy(
            promptPositions = emptyList(),
            entries = customized.entries.map { entry ->
                if (entry.isHiddenToolTimelineEntry()) {
                    entry.copy(
                        enabled = false,
                        position = null,
                        promptPositionId = "",
                    )
                } else {
                    entry
                }
            },
        ).withRequiredBuiltIns()
        val deletedEntry = deleted.entries.single { it.isHiddenToolTimelineEntry() }
        assertTrue(deleted.promptPositions.isEmpty())
        assertEquals(false, deletedEntry.enabled)
        assertEquals(null, deletedEntry.position)
        assertEquals("", deletedEntry.promptPositionId)
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

    @Test
    fun `runtime library preserves custom position side and local order`() {
        val preset = AgentPreset(
            id = "positioned",
            name = "位置测试",
            promptPositions = listOf(
                SettingLibraryPromptPosition(
                    id = "before-setting",
                    name = "设定前",
                    anchor = SettingLibraryPosition.InsertPoint3,
                    side = SettingLibraryPromptPositionSide.BeforeSettingPosition,
                    order = 4,
                ),
            ),
        )

        val position = preset.asRuntimeSettingLibrary().promptPositions.single()

        assertEquals(SettingLibraryPromptPositionSide.BeforeSettingPosition, position.side)
        assertEquals(4, position.order)
        assertTrue(position.id.startsWith("agent-preset:positioned:"))
    }

    @Test
    fun `ordinary preset prompts require a preset owned custom position`() {
        val position = SettingLibraryPromptPosition(
            id = "preset-position",
            name = "预设位置",
            anchor = SettingLibraryPosition.InsertPoint2,
        )
        val normalized = AgentPreset(
            id = "placement",
            name = "位置测试",
            promptPositions = listOf(position),
            entries = listOf(
                SettingLibraryEntry(
                    id = "direct-setting-slot",
                    title = "错误直连",
                    triggerMode = SettingLibraryTriggerMode.Always,
                    position = SettingLibraryPosition.InsertPoint1,
                    enabled = true,
                ),
                SettingLibraryEntry(
                    id = "custom-position",
                    title = "自定义位置",
                    triggerMode = SettingLibraryTriggerMode.Always,
                    position = SettingLibraryPosition.InsertPoint1,
                    promptPositionId = position.id,
                    enabled = true,
                ),
                SettingLibraryEntry(
                    id = "system-instructions",
                    title = "系统指令",
                    triggerMode = SettingLibraryTriggerMode.Always,
                    position = SettingLibraryPosition.Instructions,
                    enabled = true,
                ),
            ),
        ).withRequiredBuiltIns()

        val direct = normalized.entries.single { it.id == "direct-setting-slot" }
        val custom = normalized.entries.single { it.id == "custom-position" }
        val system = normalized.entries.single { it.id == "system-instructions" }
        assertEquals(false, direct.enabled)
        assertEquals(null, direct.position)
        assertEquals(true, custom.enabled)
        assertEquals(SettingLibraryPosition.InsertPoint2, custom.position)
        assertEquals(true, system.enabled)
        assertEquals(SettingLibraryPosition.Instructions, system.position)
    }
}
