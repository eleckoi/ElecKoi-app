package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningMessage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.withOpeningMessages
import com.eleckoi.android.foundation.storage.room.ConversationSettingChangeDao
import com.eleckoi.android.foundation.storage.room.ConversationSettingChangeEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryDao
import com.eleckoi.android.foundation.storage.room.SettingLibraryEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryEntryEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryGroupEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryRecord
import com.eleckoi.android.foundation.storage.room.SettingLibraryVersionEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryVersionEntryEntity
import com.eleckoi.android.foundation.storage.room.SettingLibraryVersionGroupEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLibraryRepositoryTest {
    @Test
    fun `creator row queries page through entries without loading one oversized library row`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                entries = initial.entries + (0 until 55).map { index ->
                    SettingLibraryEntry(
                        id = "paged-$index",
                        title = "分页设定-${index.toString().padStart(2, '0')}",
                        content = "正文-$index",
                        triggerMode = SettingLibraryTriggerMode.AgentTool,
                    )
                },
                promptPositions = listOf(
                    SettingLibraryPromptPosition(
                        id = "creator-context",
                        name = "创作上下文",
                        anchor = SettingLibraryPosition.InsertPoint1,
                    ),
                ),
            ),
        )

        val first = repository.entryRows(character.id, "分页设定-", -1, "", 20)
        val second = repository.entryRows(
            characterId = character.id,
            query = "分页设定-",
            afterSortIndex = first.last().sortIndex,
            afterId = first.last().entry.id,
            limit = 20,
        )
        val third = repository.entryRows(
            characterId = character.id,
            query = "分页设定-",
            afterSortIndex = second.last().sortIndex,
            afterId = second.last().entry.id,
            limit = 20,
        )

        assertEquals(20, first.size)
        assertEquals(20, second.size)
        assertEquals(15, third.size)
        assertEquals(55, (first + second + third).map { it.entry.id }.distinct().size)
        val metadata = repository.rowMetadata(character.id)
        assertTrue(metadata.entryCount >= 55)
        assertEquals("creator-context", metadata.promptPositions.single().id)
        assertEquals(SettingLibraryPosition.InsertPoint1, metadata.promptPositions.single().anchor)
        assertEquals("正文-54", repository.entryRow(character.id, "paged-54")?.content)
    }

    @Test
    fun `multiple openings and selected default survive Room json round trip`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val initial = repository.load(character.id)
        val openings = listOf(
            SettingLibraryOpeningMessage(id = "daily", title = "日常开场", content = "吃饭吧。"),
            SettingLibraryOpeningMessage(
                id = "mission",
                title = "任务开场",
                content = "准备出发。",
                initialVariableStateJson = "{\"任务\":\"进行中\"}",
            ),
        )

        repository.save(
            character.id,
            initial.copy(
                entries = initial.entries.map { entry ->
                    if (entry.isOpeningEntry()) {
                        entry.withOpeningMessages(openings, defaultMessageId = "mission")
                    } else {
                        entry
                    }
                },
            ),
        )

        val loaded = repository.load(character.id).entries.single(SettingLibraryEntry::isOpeningEntry)
        assertEquals(openings, loaded.openingMessages)
        assertEquals("mission", loaded.defaultOpeningMessageId)
        assertEquals("准备出发。", loaded.content)
    }

    @Test
    fun `new character exposes only the opening fixed entry without creating workspace files`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })

        val library = repository.load(character.id)

        assertEquals(1, library.entries.size)
        assertTrue(library.entries.single().isOpeningEntry())
        assertNull(dao.library(character.id))
    }

    @Test
    fun `imported library keeps imported entries without adding preset tool configuration`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val imported = SettingLibrary(
            characterId = "source-card",
            entries = listOf(
                SettingLibraryEntry(
                    id = "tavern-world-entry",
                    title = "酒馆世界书",
                    content = "世界设定",
                    agentReadStrategy = SettingLibraryAgentReadStrategy.Required,
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                    enabled = true,
                ),
            ),
        )

        val saved = repository.save(character.id, imported.copy(characterId = character.id))

        assertEquals(2, saved.entries.size)
        assertTrue(saved.entries.any(SettingLibraryEntry::isOpeningEntry))
        assertTrue(saved.entries.single { it.id == "tavern-world-entry" }.enabled)
    }

    @Test
    fun `save and load round trip only through Room`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val initial = repository.load(character.id)

        repository.save(
            character.id,
            initial.copy(
                name = "鸣潮世界",
                groups = listOf(
                    SettingLibraryGroup(id = "world", name = "世界观"),
                ),
                entries = initial.entries + SettingLibraryEntry(
                    id = "tacet",
                    title = "无音区",
                    groupId = "world",
                    content = "无音区会残留异常频率。",
                    agentReadStrategy = SettingLibraryAgentReadStrategy.Required,
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                    position = SettingLibraryPosition.InsertPoint1,
                ),
            ),
        )

        val loaded = repository.load(character.id)
        assertEquals("鸣潮世界", loaded.name)
        assertEquals("无音区会残留异常频率。", loaded.entries.single { it.id == "tacet" }.content)
        assertEquals(
            SettingLibraryAgentReadStrategy.Required,
            loaded.entries.single { it.id == "tacet" }.agentReadStrategy,
        )
        assertEquals("世界观", loaded.groups.single().name)
        val persistedEntry = dao.library(character.id)?.entries?.single { it.entryId == "tacet" }
        assertTrue(persistedEntry?.payloadJson?.contains("无音区") == true)
        assertFalse(persistedEntry?.payloadJson?.contains("insertion_timing") == true)
    }

    @Test
    fun `save preserves author content whitespace and trailing newlines`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val initial = repository.load(character.id)
        val authoredContent = "\n并行任务\n1. 调研世界书\n2. \n"

        repository.save(
            character.id,
            initial.copy(
                entries = initial.entries + SettingLibraryEntry(
                    id = "parallel-task",
                    title = "并行任务",
                    content = authoredContent,
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                ),
            ),
        )

        assertEquals(
            authoredContent,
            repository.load(character.id).entries.single { it.id == "parallel-task" }.content,
        )
    }

    @Test
    fun `save preserves explicit tree order lower than source list position`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val initial = repository.load(character.id)

        repository.save(
            character.id,
            initial.copy(
                groups = listOf(
                    SettingLibraryGroup(id = "preset", name = "预设", treeViewOrder = 2),
                    SettingLibraryGroup(id = "world", name = "世界书", treeViewOrder = 1),
                ),
                entries = initial.entries + listOf(
                    SettingLibraryEntry(
                        id = "first",
                        title = "第一项",
                        triggerMode = SettingLibraryTriggerMode.AgentTool,
                        treeViewOrder = 2,
                    ),
                    SettingLibraryEntry(
                        id = "moved-up",
                        title = "上移项",
                        triggerMode = SettingLibraryTriggerMode.AgentTool,
                        treeViewOrder = 1,
                    ),
                ),
            ),
        )

        val loaded = repository.load(character.id)
        assertEquals(1, loaded.groups.single { it.id == "world" }.treeViewOrder)
        assertEquals(2, loaded.groups.single { it.id == "preset" }.treeViewOrder)
        assertEquals(1, loaded.entries.single { it.id == "moved-up" }.treeViewOrder)
        assertEquals(2, loaded.entries.single { it.id == "first" }.treeViewOrder)
    }

    @Test
    fun `agent context builds human readable logical paths without markdown suffixes`() = runBlocking {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                groups = listOf(SettingLibraryGroup(id = "world", name = "世界观")),
                entries = initial.entries + listOf(
                    SettingLibraryEntry(
                        id = "always",
                        title = "核心规则",
                        content = "固定规则",
                        triggerMode = SettingLibraryTriggerMode.Always,
                        position = SettingLibraryPosition.InsertPoint1,
                    ),
                    SettingLibraryEntry(
                        id = "cache",
                        title = "缓存规则",
                        content = "稳定前缀",
                        triggerMode = SettingLibraryTriggerMode.Cache,
                    ),
                    SettingLibraryEntry(
                        id = "agent-entry",
                        title = "潮汐之门",
                        groupId = "world",
                        content = "门位于灯塔下。",
                        agentSelectionHint = "涉及灯塔地下时读取。",
                        agentReadStrategy = SettingLibraryAgentReadStrategy.Required,
                        triggerMode = SettingLibraryTriggerMode.AgentTool,
                    ),
                ),
            ),
        )

        val context = repository.loadAgentTurnContext(
            characterId = character.id,
            additionalLibrary = SettingLibrary(
                characterId = "agent-preset",
                promptPositions = listOf(
                    SettingLibraryPromptPosition(
                        id = "preset-position",
                        name = "预设位置",
                        anchor = SettingLibraryPosition.InsertPoint2,
                    ),
                ),
                entries = listOf(
                    SettingLibraryEntry(
                        id = "preset-always",
                        content = "预设规则",
                        triggerMode = SettingLibraryTriggerMode.Always,
                        position = SettingLibraryPosition.InsertPoint2,
                        promptPositionId = "preset-position",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("always", "cache", "preset-always"),
            context.automaticLibrary.entries.map { it.id },
        )
        assertEquals(
            SettingLibraryPosition.InsertPoint1,
            context.automaticLibrary.entries.single { it.id == "cache" }.position,
        )
        assertEquals("preset-position", context.automaticLibrary.promptPositions.single().id)
        assertEquals("agent-entry", context.readableEntries.single().id)
        assertEquals("世界观", context.readableEntries.single().groupPath)
        assertEquals("世界观/潮汐之门", context.readableEntries.single().path)
        assertEquals(SettingLibraryAgentReadStrategy.Required, context.readableEntries.single().readStrategy)
        assertFalse(context.readableEntries.single().title.contains(".md"))
    }

    @Test
    fun `save rejects duplicate setting names in the same folder`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val initial = repository.load(character.id)

        val error = runCatching {
            repository.save(
                character.id,
                initial.copy(
                    groups = listOf(SettingLibraryGroup(id = "world", name = "世界书")),
                    entries = initial.entries + listOf(
                        SettingLibraryEntry(id = "village-a", title = "山村", groupId = "world"),
                        SettingLibraryEntry(id = "village-b", title = "山村", groupId = "world"),
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("同一文件夹下已存在同名设定：山村"))
        assertNull(dao.library(character.id))
    }

    @Test
    fun `save rejects duplicate folder names under the same parent`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        val initial = repository.load(character.id)

        val error = runCatching {
            repository.save(
                character.id,
                initial.copy(
                    groups = listOf(
                        SettingLibraryGroup(id = "world-a", name = "世界书"),
                        SettingLibraryGroup(id = "world-b", name = "世界书"),
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("同一文件夹下已存在同名文件夹：世界书"))
        assertNull(dao.library(character.id))
    }

    @Test
    fun `delete operations remove Room rows`() {
        val dao = FakeSettingLibraryDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, FakeConversationSettingChangeDao(), { character })
        repository.save(character.id, repository.load(character.id))

        repository.deleteForCharacters(listOf(character.id))

        assertNull(dao.library(character.id))
    }

    @Test
    fun `conversation changes override only the bound session`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                groups = listOf(
                    SettingLibraryGroup(id = "people", name = "人物"),
                ),
                entries = initial.entries + SettingLibraryEntry(
                    id = "relationship",
                    title = "关系阶段",
                    groupId = "people",
                    content = "两人仍然陌生。",
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                ),
            ),
        )

        val result = repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(
                SettingLibrarySessionMutation.UpdateEntry(
                    entryId = "relationship",
                    groupId = null,
                    title = null,
                    content = "两人已经建立信任。",
                    selectionHint = null,
                ),
                SettingLibrarySessionMutation.CreateEntry(
                    groupId = "people",
                    title = "共同秘密",
                    content = "两人共同发现了地下入口。",
                    selectionHint = "涉及地下入口时读取。",
                ),
            ),
        )

        assertEquals(2, result.applied.size)
        assertEquals(
            "两人已经建立信任。",
            repository.loadEffective(character.id, "session-a").entries
                .single { it.id == "relationship" }.content,
        )
        assertTrue(repository.loadEffective(character.id, "session-a").entries.any { it.title == "共同秘密" })
        assertEquals(
            "两人仍然陌生。",
            repository.loadEffective(character.id, "session-b").entries
                .single { it.id == "relationship" }.content,
        )
        assertEquals(
            "两人仍然陌生。",
            repository.load(character.id).entries.single { it.id == "relationship" }.content,
        )
    }

    @Test
    fun `repeating the same conversation setting update performs no Room write`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(entries = initial.entries + SettingLibraryEntry(
                id = "relationship",
                title = "关系阶段",
                content = "仍然陌生。",
                triggerMode = SettingLibraryTriggerMode.AgentTool,
            )),
        )
        val mutation = SettingLibrarySessionMutation.UpdateEntry(
            entryId = "relationship",
            groupId = null,
            title = null,
            content = "已经建立信任。",
            selectionHint = null,
        )

        repository.applySessionMutations(character.id, "session-a", listOf(mutation))
        val writesAfterChange = changes.upsertedRowCount
        repository.applySessionMutations(character.id, "session-a", listOf(mutation))

        assertEquals(writesAfterChange, changes.upsertedRowCount)
    }

    @Test
    fun `conversation library exposes the complete effective directory without changing the base library`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                groups = listOf(SettingLibraryGroup(id = "people", name = "人物")),
                entries = initial.entries + SettingLibraryEntry(
                    id = "relationship",
                    title = "关系阶段",
                    groupId = "people",
                    content = "仍然陌生。",
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                ),
            ),
        )

        repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(
                SettingLibrarySessionMutation.UpdateEntry(
                    entryId = "relationship",
                    groupId = null,
                    title = null,
                    content = "已经建立信任。",
                    selectionHint = null,
                ),
                SettingLibrarySessionMutation.CreateEntry(
                    groupId = "people",
                    title = "共同秘密",
                    content = "发现地下入口。",
                    selectionHint = "",
                ),
            ),
        )

        val conversationLibrary = repository.conversationLibraries(character.id).getValue("session-a")

        assertEquals("已经建立信任。", conversationLibrary.entries.single { it.title == "关系阶段" }.content)
        assertEquals("发现地下入口。", conversationLibrary.entries.single { it.title == "共同秘密" }.content)
        assertEquals("人物", conversationLibrary.groups.single().name)
        assertEquals("仍然陌生。", repository.load(character.id).entries.single { it.id == "relationship" }.content)
    }

    @Test
    fun `saving a conversation as a version copies its effective library without switching the base`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                name = "原始版本",
                groups = listOf(SettingLibraryGroup(id = "people", name = "人物")),
                entries = initial.entries + SettingLibraryEntry(
                    id = "relationship",
                    title = "关系阶段",
                    groupId = "people",
                    content = "仍然陌生。",
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                ),
            ),
        )
        repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(
                SettingLibrarySessionMutation.UpdateEntry(
                    entryId = "relationship",
                    groupId = null,
                    title = null,
                    content = "已经建立信任。",
                    selectionHint = null,
                ),
                SettingLibrarySessionMutation.CreateEntry(
                    groupId = "people",
                    title = "共同秘密",
                    content = "发现地下入口。",
                    selectionHint = "",
                ),
            ),
        )
        val before = repository.load(character.id)

        val saved = repository.saveConversationAsVersion(
            characterId = character.id,
            sessionId = "session-a",
            name = "地下入口线",
        )

        assertEquals(before.activeVersionId, saved.activeVersionId)
        assertEquals(before.name, saved.name)
        assertEquals("仍然陌生。", saved.entries.single { it.id == "relationship" }.content)
        val version = saved.versions.single { it.name == "地下入口线" }
        assertEquals("已经建立信任。", version.entries.single { it.id == "relationship" }.content)
        assertEquals("发现地下入口。", version.entries.single { it.title == "共同秘密" }.content)
    }

    @Test
    fun `conversation tool may create a root setting without a prepared folder`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        repository.save(character.id, repository.load(character.id))

        repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(
                SettingLibrarySessionMutation.CreateEntry(
                    groupId = "",
                    title = "当前天气",
                    content = "夜雨。",
                    selectionHint = "",
                ),
            ),
        )

        assertTrue(repository.loadEffective(character.id, "session-a").entries.any { it.title == "当前天气" })
        assertFalse(repository.load(character.id).entries.any { it.title == "当前天气" })
    }

    @Test
    fun `conversation tool rejects duplicate setting names in the same folder`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                groups = listOf(
                    SettingLibraryGroup(id = "world", name = "世界书"),
                ),
                entries = initial.entries + SettingLibraryEntry(
                    id = "village",
                    title = "山村",
                    groupId = "world",
                    content = "原有山村设定。",
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                ),
            ),
        )

        val error = runCatching {
            repository.applySessionMutations(
                characterId = character.id,
                sessionId = "session-a",
                mutations = listOf(
                    SettingLibrarySessionMutation.CreateEntry(
                        groupId = "world",
                        title = "山村",
                        content = "不应写入。",
                        selectionHint = "",
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("同一文件夹下已存在同名设定：山村"))
        assertTrue(changes.changes("session-a").isEmpty())
    }

    @Test
    fun `AI may delete its own conversation group without deleting the author folder`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                groups = listOf(
                    SettingLibraryGroup(id = "world", name = "世界"),
                ),
            ),
        )

        val createdGroup = repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(
                SettingLibrarySessionMutation.CreateGroup(parentId = "world", name = "临时城市"),
            ),
        ).applied.single().targetId
        repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(
                SettingLibrarySessionMutation.CreateEntry(
                    groupId = createdGroup,
                    title = "港口",
                    content = "港口终年有雾。",
                    selectionHint = "",
                ),
            ),
        )
        repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(SettingLibrarySessionMutation.DeleteGroup(createdGroup)),
        )

        val effective = repository.loadEffective(character.id, "session-a")
        assertEquals(listOf("world"), effective.groups.map { it.id })
        assertFalse(effective.entries.any { it.title == "港口" })
        assertEquals(listOf("world"), repository.load(character.id).groups.map { it.id })
    }

    @Test
    fun `conversation tool writes differences inside any existing author folder`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                groups = listOf(SettingLibraryGroup(id = "private", name = "作者只读区")),
            ),
        )

        repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(
                SettingLibrarySessionMutation.CreateEntry(
                    groupId = "private",
                    title = "对话内容",
                    content = "只写入当前对话。",
                    selectionHint = "",
                ),
            ),
        )

        assertTrue(repository.loadEffective(character.id, "session-a").entries.any { it.title == "对话内容" })
        assertFalse(repository.load(character.id).entries.any { it.title == "对话内容" })
    }

    @Test
    fun `deleting conversation changes restores the untouched base library`() {
        val dao = FakeSettingLibraryDao()
        val changes = FakeConversationSettingChangeDao()
        val character = testCharacter()
        val repository = SettingLibraryRepository(dao, changes, { character })
        val initial = repository.load(character.id)
        repository.save(
            character.id,
            initial.copy(
                entries = initial.entries + SettingLibraryEntry(
                    id = "base-entry",
                    title = "母设定",
                    content = "原始正文",
                    triggerMode = SettingLibraryTriggerMode.AgentTool,
                ),
            ),
        )
        repository.applySessionMutations(
            characterId = character.id,
            sessionId = "session-a",
            mutations = listOf(
                SettingLibrarySessionMutation.UpdateEntry(
                    entryId = "base-entry",
                    groupId = null,
                    title = "当前对话设定",
                    content = "对话正文",
                    selectionHint = null,
                ),
            ),
        )

        repository.deleteConversationChanges(character.id, "session-a")

        assertEquals("母设定", repository.loadEffective(character.id, "session-a").entries.single {
            it.id == "base-entry"
        }.title)
        assertEquals("原始正文", repository.load(character.id).entries.single { it.id == "base-entry" }.content)
        assertTrue(changes.changes("session-a").isEmpty())
    }

    private fun testCharacter() = CharacterSlot(
        id = "character-1",
        name = "守岸人",
        avatar = "",
        group = "",
        folder = "character-1",
        persona = CharacterCard(characterId = "character-1", characterName = "守岸人"),
    )
}

private class FakeSettingLibraryDao : SettingLibraryDao {
    private val values = linkedMapOf<String, SettingLibraryRecord>()
    private val flows = linkedMapOf<String, MutableStateFlow<SettingLibraryRecord?>>()

    override fun library(characterId: String): SettingLibraryRecord? = values[characterId]

    override fun libraryFlow(characterId: String): Flow<SettingLibraryRecord?> =
        flows.getOrPut(characterId) { MutableStateFlow(values[characterId]) }

    override fun metadata(characterId: String): SettingLibraryEntity? = values[characterId]?.library

    override fun entryCount(characterId: String): Int = values[characterId]?.entries?.size ?: 0

    override fun groupCount(characterId: String): Int = values[characterId]?.groups?.size ?: 0

    override fun entryPage(
        characterId: String,
        query: String,
        afterSortIndex: Int,
        afterId: String,
        limit: Int,
    ): List<SettingLibraryEntryEntity> = values[characterId]?.entries.orEmpty()
        .asSequence()
        .filter { it.sortIndex > afterSortIndex || (it.sortIndex == afterSortIndex && it.entryId > afterId) }
        .filter { query.isBlank() || it.payloadJson.contains(query.likeNeedle(), ignoreCase = true) }
        .sortedWith(compareBy(SettingLibraryEntryEntity::sortIndex, SettingLibraryEntryEntity::entryId))
        .take(limit)
        .toList()

    override fun groupPage(
        characterId: String,
        query: String,
        afterSortIndex: Int,
        afterId: String,
        limit: Int,
    ): List<SettingLibraryGroupEntity> = values[characterId]?.groups.orEmpty()
        .asSequence()
        .filter { it.sortIndex > afterSortIndex || (it.sortIndex == afterSortIndex && it.groupId > afterId) }
        .filter { query.isBlank() || it.payloadJson.contains(query.likeNeedle(), ignoreCase = true) }
        .sortedWith(compareBy(SettingLibraryGroupEntity::sortIndex, SettingLibraryGroupEntity::groupId))
        .take(limit)
        .toList()

    override fun entry(characterId: String, entryId: String): SettingLibraryEntryEntity? =
        values[characterId]?.entries?.firstOrNull { it.entryId == entryId }

    override fun upsert(library: SettingLibraryRecord) {
        values[library.library.characterId] = library
        flows.getOrPut(library.library.characterId) { MutableStateFlow(null) }.value = library
    }

    override fun upsertMetadata(library: SettingLibraryEntity) = Unit

    override fun upsertEntryRows(entries: List<SettingLibraryEntryEntity>) = Unit

    override fun insertContent(content: com.eleckoi.android.foundation.storage.room.SettingEntryContentEntity): Long =
        error("Repository fake stores complete logical records")

    override fun revisionForContent(characterId: String, entryId: String, payload: String): String? = null

    override fun upsertEntryLinks(entries: List<com.eleckoi.android.foundation.storage.room.SettingLibraryEntryLinkEntity>) = Unit

    override fun upsertVersionEntryLinks(entries: List<com.eleckoi.android.foundation.storage.room.SettingLibraryVersionEntryLinkEntity>) = Unit

    override fun deleteUnusedContents(characterId: String) = Unit

    override fun upsertGroupRows(groups: List<SettingLibraryGroupEntity>) = Unit

    override fun upsertVersionRows(versions: List<SettingLibraryVersionEntity>) = Unit

    override fun upsertVersionEntryRows(entries: List<SettingLibraryVersionEntryEntity>) = Unit

    override fun upsertVersionGroupRows(groups: List<SettingLibraryVersionGroupEntity>) = Unit

    override fun deleteEntryRows(characterId: String, entryIds: List<String>) = Unit

    override fun deleteGroupRows(characterId: String, groupIds: List<String>) = Unit

    override fun deleteVersionRows(characterId: String, versionIds: List<String>) = Unit

    override fun deleteVersionEntryRows(characterId: String, versionId: String, entryIds: List<String>) = Unit

    override fun deleteVersionGroupRows(characterId: String, versionId: String, groupIds: List<String>) = Unit

    override fun deleteForCharacters(characterIds: List<String>) {
        characterIds.forEach { id ->
            values.remove(id)
            flows[id]?.value = null
        }
    }

    override fun deleteExceptCharacters(characterIds: List<String>) {
        deleteForCharacters(values.keys.filterNot { it in characterIds })
    }

    override fun deleteAll() {
        deleteForCharacters(values.keys.toList())
    }

    private fun String.likeNeedle(): String = trim('%')
        .replace("\\%", "%")
        .replace("\\_", "_")
        .replace("\\\\", "\\")
}

private class FakeConversationSettingChangeDao : ConversationSettingChangeDao {
    private val values = linkedMapOf<Triple<String, String, String>, ConversationSettingChangeEntity>()
    var upsertedRowCount: Int = 0
        private set

    override fun changes(sessionId: String): List<ConversationSettingChangeEntity> = values.values
        .filter { it.sessionId == sessionId }
        .sortedWith(compareBy({ it.updatedAt }, { it.targetType }, { it.targetId }))

    override fun changesForCharacter(characterId: String): List<ConversationSettingChangeEntity> = values.values
        .sortedWith(compareByDescending<ConversationSettingChangeEntity> { it.updatedAt }
            .thenBy { it.sessionId }
            .thenBy { it.targetType }
            .thenBy { it.targetId })

    override fun upsertChanges(changes: List<ConversationSettingChangeEntity>) {
        upsertedRowCount += changes.size
        changes.forEach { change ->
            values[Triple(change.sessionId, change.targetType, change.targetId)] = change
        }
    }

    override fun deleteForSession(sessionId: String) {
        values.keys.filter { it.first == sessionId }.forEach(values::remove)
    }
}
