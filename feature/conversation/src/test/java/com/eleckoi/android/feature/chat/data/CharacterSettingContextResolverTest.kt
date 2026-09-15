package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSettingContextResolverTest {
    @Test
    fun `EJS controllers remain controllers when another controller reads them`() {
        fun source(
            id: String,
            title: String,
            mode: SettingLibraryDynamicMode,
        ) = SettingLibraryAgentEntry(
            id = id,
            title = title,
            groupPath = "",
            path = title,
            content = "<$title>",
            readStrategy = SettingLibraryAgentReadStrategy.VariableCondition,
            dynamicMode = mode,
        )
        val root = source("root", "主控制器", SettingLibraryDynamicMode.EjsController)
        val nestedController = source("nested", "复用控制器", SettingLibraryDynamicMode.EjsController)
        val reference = source("reference", "共享引用", SettingLibraryDynamicMode.EjsReference)

        val sources = ejsTemplateSources(
            candidates = listOf(root, nestedController, reference),
            targets = listOf(root),
        )

        assertEquals(setOf("主控制器", "复用控制器", "共享引用"), sources.map { it.title }.toSet())
        assertTrue(sources.all { it.controllerId == root.id })
        assertTrue(sources.single { it.title == "复用控制器" }.id != nestedController.id)
        assertEquals(SettingLibraryDynamicMode.EjsController, nestedController.dynamicMode)
    }

    @Test
    fun `maps every supported setting position to its runtime anchor`() {
        val positions = SettingLibraryPosition.entries
        val library = SettingLibrary(
            characterId = "character",
            entries = positions.mapIndexed { index, position ->
                SettingLibraryEntry(
                    id = "entry-$index",
                    content = "content-$index",
                    triggerMode = SettingLibraryTriggerMode.Always,
                    position = position,
                    insertRole = when (index % 3) {
                        0 -> SettingLibraryInsertRole.System
                        1 -> SettingLibraryInsertRole.User
                        else -> SettingLibraryInsertRole.Assistant
                    },
                    order = index + 1,
                )
            },
        )

        val resolved = CharacterSettingContextResolver.resolve(library, emptyList())

        assertEquals(
            listOf(
                AgentContextAnchor.Instructions,
                AgentContextAnchor.BeforeToolContext,
                AgentContextAnchor.BeforeHistory,
                AgentContextAnchor.AfterHistory,
                AgentContextAnchor.BeforeLatestUserInput,
                AgentContextAnchor.AfterLatestUserInput,
                AgentContextAnchor.BeforeToolFlow,
                AgentContextAnchor.AfterToolFlow,
            ),
            resolved.map { it.anchor },
        )
        assertEquals(
            listOf(
                AgentContextRole.System,
                AgentContextRole.User,
                AgentContextRole.Assistant,
                AgentContextRole.User,
                AgentContextRole.User,
                AgentContextRole.Assistant,
                AgentContextRole.User,
                AgentContextRole.User,
            ),
            resolved.map { it.role },
        )
        assertEquals(
            List(positions.size) { AgentContextActivation.Immediate },
            resolved.map { it.activation },
        )
    }

    @Test
    fun `keyword priority matches recent conversation without semantic scanning`() {
        val entry = SettingLibraryEntry(
            id = "cat-rule",
            content = "猫相关设定",
            keywords = listOf("小猫"),
            keywordScanDepth = 1,
            triggerMode = SettingLibraryTriggerMode.AgentTool,
        )

        val matched = CharacterSettingContextResolver.run {
            entry.matchesKeywords(
            listOf(ChatMessage(id = "u1", role = MessageRole.User, content = "聊聊小猫")),
            )
        }

        assertTrue(matched)
    }

    @Test
    fun `keyword priority supports tavern regex keys without degrading the entry`() {
        val entry = SettingLibraryEntry(
            id = "regex-rule",
            content = "酒馆正则关键词设定",
            keywords = listOf("/cat(?:girl)?/i"),
            keywordUseRegex = true,
            keywordIgnoreCase = false,
            triggerMode = SettingLibraryTriggerMode.AgentTool,
        )

        val matched = CharacterSettingContextResolver.run {
            entry.matchesKeywords(
                listOf(ChatMessage(id = "u1", role = MessageRole.User, content = "A CATGIRL appears")),
            )
        }

        assertTrue(matched)
    }

    @Test
    fun `invalid tavern regex key stays safe and does not match`() {
        val entry = SettingLibraryEntry(
            id = "invalid-regex",
            content = "坏正则不会破坏上下文",
            keywords = listOf("/(/g"),
            keywordUseRegex = true,
            triggerMode = SettingLibraryTriggerMode.AgentTool,
        )

        val matched = CharacterSettingContextResolver.run {
            entry.matchesKeywords(
                listOf(ChatMessage(id = "u1", role = MessageRole.User, content = "anything")),
            )
        }

        assertFalse(matched)
    }

    @Test
    fun `keyword priority stays hidden until a message matches then becomes required`() {
        val keywordEntry = SettingLibraryEntry(
            id = "cat-rule",
            content = "猫相关设定",
            keywords = listOf("小猫"),
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            agentReadStrategy = SettingLibraryAgentReadStrategy.Keyword,
        )
        val context = SettingLibraryAgentTurnContext(
            automaticLibrary = SettingLibrary(characterId = "character"),
            readableEntries = listOf(
                SettingLibraryAgentEntry(
                    id = keywordEntry.id,
                    title = "猫",
                    groupPath = "",
                    path = "猫",
                    content = keywordEntry.content,
                    readStrategy = SettingLibraryAgentReadStrategy.Keyword,
                ),
                SettingLibraryAgentEntry(
                    id = "visible",
                    title = "公开设定",
                    groupPath = "",
                    path = "公开设定",
                    content = "总能搜索到",
                ),
            ),
            groups = emptyList(),
            keywordStrategyEntries = listOf(keywordEntry),
        )

        val hidden = context.withKeywordPromotions(emptyList())
        val promoted = context.withKeywordPromotions(
            listOf(ChatMessage(id = "u1", role = MessageRole.User, content = "聊聊小猫")),
        )

        assertEquals(listOf("visible"), hidden.readableEntries.map { it.id })
        assertEquals(listOf("cat-rule", "visible"), promoted.readableEntries.map { it.id })
        assertEquals(SettingLibraryAgentReadStrategy.Keyword, promoted.readableEntries.first().readStrategy)
        assertTrue(promoted.readableEntries.first().promotedToRequiredThisTurn)
    }

    @Test
    fun `keyword priority follows the configured association rounds`() {
        val campus = SettingLibraryEntry(
            id = "campus",
            content = "校园里有小林。",
            keywords = listOf("学校"),
            keywordRecursionDepth = 1,
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            agentReadStrategy = SettingLibraryAgentReadStrategy.Keyword,
        )
        val xiaolin = SettingLibraryEntry(
            id = "xiaolin",
            content = "小林的角色设定",
            keywords = listOf("小林"),
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            agentReadStrategy = SettingLibraryAgentReadStrategy.Keyword,
        )

        val matches = CharacterSettingContextResolver.run {
            matchingKeywordEntryIds(
                entries = listOf(campus, xiaolin),
                messages = listOf(ChatMessage(id = "u1", role = MessageRole.User, content = "说说学校")),
            )
        }

        assertEquals(setOf("campus", "xiaolin"), matches)
    }

    @Test
    fun `normalizes local entry orders after resolving the full placement sequence`() {
        val library = SettingLibrary(
            characterId = "character",
            entries = listOf(
                SettingLibraryEntry(
                    id = "after-history",
                    content = "隐藏工具时间线",
                    triggerMode = SettingLibraryTriggerMode.Always,
                    position = SettingLibraryPosition.AfterHistory,
                    order = Int.MAX_VALUE,
                ),
                SettingLibraryEntry(
                    id = "before-tool-flow",
                    content = "测试设定",
                    triggerMode = SettingLibraryTriggerMode.Always,
                    position = SettingLibraryPosition.BeforeToolFlow,
                    order = 1,
                ),
            ),
        )

        val resolved = CharacterSettingContextResolver.resolve(library, emptyList())

        assertEquals(listOf("after-history", "before-tool-flow"), resolved.map { it.id })
        assertEquals(listOf(1, 2), resolved.map { it.order })
        assertEquals(
            listOf(AgentContextAnchor.AfterHistory, AgentContextAnchor.BeforeToolFlow),
            resolved.map { it.anchor },
        )
    }

    @Test
    fun `custom prompt position controls the runtime anchor independently`() {
        val customPosition = SettingLibraryPromptPosition(
            id = "after-tools-custom",
            name = "工具完成后的约束",
            anchor = SettingLibraryPosition.AfterToolFlow,
            order = 1,
        )
        val library = SettingLibrary(
            characterId = "character",
            promptPositions = listOf(customPosition),
            entries = listOf(
                SettingLibraryEntry(
                    id = "custom-entry",
                    content = "始终插入",
                    triggerMode = SettingLibraryTriggerMode.Always,
                    position = SettingLibraryPosition.BeforeHistory,
                    promptPositionId = customPosition.id,
                ),
            ),
        )

        val resolved = CharacterSettingContextResolver.resolve(library, emptyList())

        assertEquals(AgentContextAnchor.AfterToolFlow, resolved.single().anchor)
        assertEquals("custom-entry", resolved.single().id)
    }
}
