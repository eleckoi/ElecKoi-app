package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntryKind
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPositionSide
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingLibraryJsonCodecTest {
    private val promptPosition = SettingLibraryPromptPosition(
        id = "prompt-position-one",
        name = "工具完成后的提示词",
        anchor = SettingLibraryPosition.InsertPoint5,
        side = SettingLibraryPromptPositionSide.BeforeSettingPosition,
        order = 1,
        createdAt = "created",
        updatedAt = "updated",
    )
    private val group = SettingLibraryGroup(
        id = "group-lore",
        name = "世界观",
        order = 2,
        treeViewOrder = 3,
        createdAt = "created",
        updatedAt = "updated",
    )
    private val entry = SettingLibraryEntry(
        id = "entry-city",
        title = "城邦",
        iconId = "location",
        kind = SettingLibraryEntryKind.Normal,
        groupId = group.id,
        content = "海边城邦",
        agentSelectionHint = "谈到故乡时读取",
        agentReadStrategy = SettingLibraryAgentReadStrategy.Keyword,
        keywords = listOf("故乡", "海港"),
        keywordScanDepth = 4,
        conditionKeywords = listOf("旅行"),
        keywordCondition = SettingLibraryKeywordCondition.Any,
        keywordUseRegex = true,
        keywordIgnoreCase = false,
        keywordWholeWord = true,
        keywordRecursionDepth = 2,
        triggerMode = SettingLibraryTriggerMode.AgentTool,
        enabled = false,
        position = SettingLibraryPosition.InsertPoint1,
        promptPositionId = promptPosition.id,
        insertRole = SettingLibraryInsertRole.User,
        order = 6,
        viewOrder = 7,
        groupViewOrder = 8,
        treeViewOrder = 9,
        createdAt = "created",
        updatedAt = "updated",
    )
    private val version = SettingLibraryVersion(
        id = "version-one",
        name = "第一版",
        entries = listOf(entry),
        groups = listOf(group),
        promptPositions = listOf(promptPosition),
        listAllExpanded = false,
        expandedGroupIds = listOf(group.id),
        createdAt = "created",
        updatedAt = "updated",
    )
    private val library = SettingLibrary(
        characterId = "character-one",
        name = "设定库",
        entries = listOf(entry),
        groups = listOf(group),
        promptPositions = listOf(promptPosition),
        activeVersionId = version.id,
        versions = listOf(version),
        listAllExpanded = false,
        expandedGroupIds = listOf(group.id),
    )

    @Test
    fun `Room entity round trip retains the complete library`() {
        val entity = SettingLibraryJsonCodec.toEntity(library, updatedAt = "persisted")

        assertEquals(library, SettingLibraryJsonCodec.fromEntity(entity))
        assertEquals("persisted", entity.library.updatedAt)
    }

    @Test
    fun `large imported world book is split into independent Room rows`() {
        val entries = List(240) { index ->
            SettingLibraryEntry(
                id = "world-entry-$index",
                title = "世界书条目 $index",
                content = "设定正文 $index：" + "潮汐与群岛。".repeat(2_000),
            )
        }
        val largeLibrary = library.copy(
            entries = entries,
            versions = emptyList(),
            activeVersionId = "",
        )

        val record = SettingLibraryJsonCodec.toEntity(largeLibrary, updatedAt = "persisted")

        assertEquals(240, record.entries.size)
        assertEquals(entries.map { it.id }, record.entries.map { it.entryId })
        assertEquals(largeLibrary, SettingLibraryJsonCodec.fromEntity(record))
    }

    @Test
    fun `export parse retains editor content and assigns import identity`() {
        val imported = SettingLibraryJsonCodec.parseExport(
            json = SettingLibraryJsonCodec.exportLibrary(library),
            versionId = "imported-version",
        )

        assertEquals("imported-version", imported.id)
        assertEquals(library.name, imported.name)
        assertEquals(library.entries, imported.entries)
        assertEquals(library.groups, imported.groups)
        assertEquals(library.promptPositions, imported.promptPositions)
        assertEquals(library.listAllExpanded, imported.listAllExpanded)
        assertEquals(library.expandedGroupIds, imported.expandedGroupIds)
    }

    @Test
    fun `snapshot round trip retains versions and active selection`() {
        val snapshot = SettingLibraryJsonCodec.parseSnapshot(
            SettingLibraryJsonCodec.exportSnapshot(library),
        )

        assertEquals(library.activeVersionId, snapshot.activeVersionId)
        assertEquals(library.versions, snapshot.versions)
    }

    @Test
    fun `entry json retains independent reference mode`() {
        val reference = entry.copy(
            agentReadStrategy = SettingLibraryAgentReadStrategy.VariableCondition,
            dynamicMode = SettingLibraryDynamicMode.EjsReference,
        )

        val restored = SettingLibraryJsonCodec.entryFromJson(
            SettingLibraryJsonCodec.entryToJson(reference),
        )

        assertEquals(SettingLibraryDynamicMode.EjsReference, restored.dynamicMode)
    }

}
