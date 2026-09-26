package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.feature.conversation.markdown.*
import com.eleckoi.android.feature.conversation.timeline.*
import com.eleckoi.android.feature.conversation.timeline.model.*
import com.eleckoi.android.feature.conversation.timeline.ui.*

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCharacterRoot
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryContentMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningMessage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryOpeningEntry
import com.eleckoi.android.feature.studio.api.CreatorAssistantService
import com.eleckoi.android.feature.studio.api.CreatorSettingLibraryMetadata
import com.eleckoi.android.feature.studio.authoring.CreationWorkspaceAgentInstructions
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringContext
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLibraryCreatorCapabilitiesTest {
    @Test
    fun `plain content patch updates the default opening and preserves alternatives`() = runBlocking {
        val opening = settingLibraryOpeningEntry(
            SettingLibraryEntry(
                openingMessages = listOf(
                    SettingLibraryOpeningMessage(id = "default", title = "默认", content = "旧开场"),
                    SettingLibraryOpeningMessage(id = "other", title = "备用", content = "备用开场"),
                ),
                defaultOpeningMessageId = "default",
            ),
        )
        val library = SettingLibrary(characterId = CharacterId, entries = listOf(opening))
        val changes = SettingLibraryCreatorChangeStore()
        val preview = SettingLibraryCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "setting_library.preview_changes" }

        val result = preview.handler(
            context(library, changes),
            buildJsonObject {
                put("base_revision", "stale-setting-library-revision")
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "patch_entry")
                        put("id", opening.id)
                        put("content", "新默认开场")
                    })
                })
            },
        ) as JsonObject

        val updated = changes.get(result.getValue("changeSetId").jsonPrimitive.content)!!
            .nextLibrary.entries.single { it.isOpeningEntry() }
        assertEquals("新默认开场", updated.content)
        assertEquals("新默认开场", updated.openingMessages.single { it.id == "default" }.content)
        assertEquals("备用开场", updated.openingMessages.single { it.id == "other" }.content)
        assertEquals(Revision, result.getValue("baseRevision").jsonPrimitive.content)
    }

    @Test
    fun `preview can modify opening and every trigger field without creating a group`() = runBlocking {
        val opening = settingLibraryOpeningEntry(SettingLibraryEntry(content = "旧开场"))
        val normal = SettingLibraryEntry(
            id = "entry-1",
            title = "基础设定",
            content = "正文",
            triggerMode = SettingLibraryTriggerMode.AgentTool,
        )
        val library = SettingLibrary(
            characterId = CharacterId,
            entries = listOf(opening, normal),
        )
        val changes = SettingLibraryCreatorChangeStore()
        val context = context(library, changes)
        val preview = SettingLibraryCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "setting_library.preview_changes" }

        val result = preview.handler(
            context,
            buildJsonObject {
                put("base_revision", Revision)
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "patch_entry")
                        put("id", opening.id)
                        put("content", "新开场")
                    })
                    add(buildJsonObject {
                        put("op", "create_prompt_position")
                        put("id", "position-1")
                        put("name", "长期设定")
                        put("anchor", "insert_point_1")
                    })
                    add(buildJsonObject {
                        put("op", "patch_entry")
                        put("id", normal.id)
                        put("trigger_mode", "always")
                        put("agent_read_strategy", "keyword")
                        put("dynamic_mode", "standard")
                        put("agent_selection_hint", "涉及城市时读取")
                        put("keyword_scan_depth", 18)
                        put("keyword_condition", "all")
                        put("keyword_use_regex", true)
                        put("keyword_ignore_case", false)
                        put("keyword_whole_word", true)
                        put("keyword_recursion_depth", 3)
                        put("position", "insert_point_1")
                        put("prompt_position_id", "position-1")
                        put("insert_role", "user")
                        put("order", 4)
                        put("tree_view_order", 9)
                    })
                })
            },
        ) as JsonObject

        val next = changes.get(result.getValue("changeSetId").jsonPrimitive.content)!!.nextLibrary
        assertEquals("新开场", next.entries.single { it.isOpeningEntry() }.content)
        assertTrue(next.groups.isEmpty())
        assertEquals("长期设定", next.promptPositions.single().name)
        val updated = next.entries.single { it.id == normal.id }
        assertEquals(SettingLibraryTriggerMode.Always, updated.triggerMode)
        assertEquals(SettingLibraryAgentReadStrategy.Keyword, updated.agentReadStrategy)
        assertEquals(SettingLibraryDynamicMode.Standard, updated.dynamicMode)
        assertEquals(18, updated.keywordScanDepth)
        assertEquals(SettingLibraryKeywordCondition.All, updated.keywordCondition)
        assertEquals(true, updated.keywordUseRegex)
        assertEquals(false, updated.keywordIgnoreCase)
        assertEquals(true, updated.keywordWholeWord)
        assertEquals(3, updated.keywordRecursionDepth)
        assertEquals(SettingLibraryPosition.InsertPoint1, updated.position)
        assertEquals("position-1", updated.promptPositionId)
        assertEquals(SettingLibraryInsertRole.User, updated.insertRole)
        assertEquals(4, updated.order)
        assertEquals(9, updated.treeViewOrder)
    }

    @Test
    fun `authoring guide exposes runtime semantics and forbids persistent test data`() = runBlocking {
        val guide = SettingLibraryCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "setting_library.get_authoring_guide" }
            .handler(context(SettingLibrary(characterId = CharacterId)), buildJsonObject {})
            .toString()

        assertTrue(guide.contains("agent_tool"))
        assertTrue(guide.contains("always"))
        assertTrue(guide.contains("keyword_recursion_depth"))
        assertTrue(guide.contains("关键词和 EJS 同时启用时必须同时成立"))
        assertTrue(guide.contains("引用设定返回空内容"))
        assertTrue(guide.contains("variablePatchFlow"))
        assertTrue(guide.contains("stale_read_paths"))
        assertTrue(guide.contains("promptPositions"))
        assertTrue(guide.contains("测试工具"))
        assertTrue(CreationWorkspaceAgentInstructions.Value.contains("Never create a test group"))
    }

    @Test
    fun `creator tool can create keyword gated EJS with separate plain text reference`() = runBlocking {
        val changes = SettingLibraryCreatorChangeStore()
        val preview = SettingLibraryCreatorCapabilities.capabilities()
            .single { it.definition.capabilityId == "setting_library.preview_changes" }
        val result = preview.handler(
            context(SettingLibrary(characterId = CharacterId), changes),
            buildJsonObject {
                put("operations", buildJsonArray {
                    add(buildJsonObject {
                        put("op", "create_entry")
                        put("id", "scene-control")
                        put("title", "剧情触发")
                        put("content", "<%- await getwi('雨夜设定') %>")
                        put("trigger_mode", "agent_tool")
                        put("agent_read_strategy", "keyword")
                        put("keywords", buildJsonArray { add(JsonPrimitive("雨夜")) })
                        put("content_mode", "ejs")
                    })
                    add(buildJsonObject {
                        put("op", "create_entry")
                        put("id", "scene-text")
                        put("title", "雨夜设定")
                        put("content", "雨夜的街道很安静。")
                        put("trigger_mode", "always")
                        put("agent_read_strategy", "required")
                        put("dynamic_mode", "ejs_reference")
                        put("content_mode", "ejs")
                    })
                })
            },
        ) as JsonObject

        val entries = changes.get(result.getValue("changeSetId").jsonPrimitive.content)!!
            .nextLibrary.entries.associateBy { it.id }
        assertEquals(SettingLibraryAgentReadStrategy.Keyword, entries.getValue("scene-control").agentReadStrategy)
        assertEquals(SettingLibraryContentMode.Ejs, entries.getValue("scene-control").contentMode)
        assertEquals(listOf("雨夜"), entries.getValue("scene-control").keywords)
        assertEquals(SettingLibraryDynamicMode.EjsReference, entries.getValue("scene-text").dynamicMode)
        assertEquals(SettingLibraryTriggerMode.AgentTool, entries.getValue("scene-text").triggerMode)
        assertEquals(SettingLibraryAgentReadStrategy.Normal, entries.getValue("scene-text").agentReadStrategy)
        assertEquals(SettingLibraryContentMode.PlainText, entries.getValue("scene-text").contentMode)
        assertEquals("雨夜的街道很安静。", entries.getValue("scene-text").content)
    }

    private fun context(
        library: SettingLibrary,
        changes: SettingLibraryCreatorChangeStore = SettingLibraryCreatorChangeStore(),
    ): CreatorAuthoringContext {
        val workspace = CreatorWorkspace(
            id = WorkspaceId,
            name = "创作项目",
            linkedCharacterId = CharacterId,
            primaryCharacterRootId = RootId,
            characterRoots = listOf(
                CreatorWorkspaceCharacterRoot(
                    id = RootId,
                    characterId = CharacterId,
                    access = CreatorWorkspaceRootAccess.ReadWrite,
                ),
            ),
            createdAt = "now",
            updatedAt = "now",
        )
        @Suppress("UNCHECKED_CAST")
        val service = Proxy.newProxyInstance(
            CreatorAssistantService::class.java.classLoader,
            arrayOf(CreatorAssistantService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "creatorWorkspace" -> workspace
                "creatorSettingLibraryMetadata" -> CreatorSettingLibraryMetadata(
                    rootId = RootId,
                    characterId = CharacterId,
                    name = "设定库",
                    updatedAt = Revision,
                    entryCount = library.entries.size,
                    groupCount = library.groups.size,
                    promptPositions = library.promptPositions,
                )
                "loadCreatorSettingLibrary" -> library
                else -> error("Unexpected service call: ${method.name}")
            }
        } as CreatorAssistantService
        return CreatorAuthoringContext(
            workspaceId = WorkspaceId,
            permissionModeProvider = { AgentPermissionMode.ApproveForMe },
            service = service,
            settingLibraryChanges = changes,
        )
    }

    private companion object {
        const val WorkspaceId = "workspace-1"
        const val CharacterId = "character-1"
        const val RootId = "root-1"
        const val Revision = "revision-1"
    }
}
