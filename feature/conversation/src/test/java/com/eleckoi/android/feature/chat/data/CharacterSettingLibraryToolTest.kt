package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentMoveSettingFileTool
import com.eleckoi.android.engine.agent.api.AgentApplySettingPatchTool
import com.eleckoi.android.engine.agent.api.AgentEditSettingFileTool
import com.eleckoi.android.engine.agent.api.AgentSettingFileMutationTools
import com.eleckoi.android.engine.agent.api.AgentSettingLibraryTools
import com.eleckoi.android.engine.agent.api.AgentWriteSettingFileTool
import com.eleckoi.android.engine.agent.api.AgentGlobSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGrepSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentReadSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentVirtualGlobResult
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepLine
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepResult
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAppliedMutation
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryResolvedReference
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibrarySessionMutation
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibrarySessionMutationResult
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSettingLibraryToolTest {
    @Test
    fun `glob returns human readable virtual paths without internal ids or content`() = runBlocking {
        val search = FakeAgentVirtualFileSearch(
            globHandler = { corpus, request ->
                assertEquals("**", request.pattern)
                assertEquals(listOf("莉亚"), corpus.map { it.path })
                AgentVirtualGlobResult(paths = listOf("莉亚"), omitted = 0)
            },
        )
        val tool = requireNotNull(
            characterSettingLibraryGlobTool(
                listOf(
                    agentEntry(
                        id = "draft-1786043925836-9",
                        title = "莉亚",
                        groupPath = "人物/主要角色",
                        content = "不应出现在列表结果",
                        selectionHint = "涉及王都调查时读取。",
                    ),
                    agentEntry(
                        id = "rules",
                        title = "核心规则",
                        groupPath = "",
                        content = "规则正文",
                        readStrategy = SettingLibraryAgentReadStrategy.Required,
                    ),
                ),
                search,
            ),
        )

        val result = tool.handler.execute(
            buildJsonObject {
                put("pattern", "**")
                put("path", "人物/主要角色")
            },
        )
        val payload = Json.parseToJsonElement(result.content).jsonObject
        val entry = payload.getValue("entries").jsonArray.single().jsonObject
        val required = payload.getValue("required_entries").jsonArray.single().jsonObject

        assertEquals(AgentGlobSettingFilesTool, tool.definition.name)
        assertEquals("人物/主要角色/莉亚", entry.getValue("path").jsonPrimitive.content)
        assertEquals("人物/主要角色", entry.getValue("group_path").jsonPrimitive.content)
        assertEquals("涉及王都调查时读取。", entry.getValue("selection_hint").jsonPrimitive.content)
        assertFalse(entry.containsKey("entry_id"))
        assertFalse(entry.containsKey("content"))
        assertFalse(result.content.contains("draft-1786043925836-9"))
        assertFalse(result.content.contains("不应出现在列表结果"))
        assertEquals("核心规则", required.getValue("path").jsonPrimitive.content)
        assertEquals("required", required.getValue("read_strategy").jsonPrimitive.content)
        assertEquals("", required.getValue("selection_hint").jsonPrimitive.content)
        assertFalse(required.containsKey("content"))
    }

    @Test
    fun `glob without a pattern lists the complete setting library`() = runBlocking {
        val search = FakeAgentVirtualFileSearch(
            globHandler = { corpus, request ->
                assertEquals("**", request.pattern)
                AgentVirtualGlobResult(paths = corpus.map { it.path }, omitted = 0)
            },
        )
        val tool = requireNotNull(
            characterSettingLibraryGlobTool(
                listOf(agentEntry("world", "世界背景", "夜之灯塔", "灯塔立于雾海。")),
                search,
            ),
        )

        val result = tool.handler.execute(buildJsonObject {})

        assertTrue(result.success)
        assertTrue(result.content.contains("夜之灯塔/世界背景"))
        assertEquals(
            emptyList<String>(),
            tool.definition.parameters["required"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                .orEmpty(),
        )
    }

    @Test
    fun `grep returns virtual paths from an in-memory Room snapshot`() = runBlocking {
        val search = FakeAgentVirtualFileSearch(
            grepHandler = { corpus, request ->
                assertEquals("雨天|骤雨", request.pattern)
                assertTrue(corpus.single { it.path == "剧情线索/雨天放学事件" }.content.contains("放学时骤雨来临"))
                AgentVirtualGrepResult(
                    paths = listOf("剧情线索/雨天放学事件"),
                    counts = mapOf("剧情线索/雨天放学事件" to 2),
                    lines = emptyList(),
                    omittedPaths = 0,
                    omittedLines = 0,
                )
            },
        )
        val tool = requireNotNull(
            characterSettingLibraryGrepTool(
                listOf(
                    agentEntry(
                        id = "rain",
                        title = "雨天放学事件",
                        groupPath = "剧情线索",
                        content = "放学时骤雨来临，绫音会在旧教学楼门口等待。",
                    ),
                    agentEntry(
                        id = "style",
                        title = "叙事规则",
                        groupPath = "语料文风",
                        content = "保持克制的第三人称叙事。",
                        readStrategy = SettingLibraryAgentReadStrategy.Required,
                    ),
                ),
                search,
            ),
        )

        val result = tool.handler.execute(
            buildJsonObject { put("pattern", "雨天|骤雨") },
        )
        val payload = Json.parseToJsonElement(result.content).jsonObject
        val match = payload.getValue("matches").jsonArray.single().jsonObject
        val required = payload.getValue("required_entries").jsonArray.single().jsonObject

        assertEquals(AgentGrepSettingFilesTool, tool.definition.name)
        assertEquals("剧情线索/雨天放学事件", match.getValue("path").jsonPrimitive.content)
        assertFalse(match.containsKey("entry_id"))
        assertFalse(match.containsKey("content"))
        assertEquals("语料文风/叙事规则", required.getValue("path").jsonPrimitive.content)
        assertFalse(required.containsKey("content"))
    }

    @Test
    fun `grep content mode maps matched lines to virtual paths`() = runBlocking {
        val search = FakeAgentVirtualFileSearch(
            grepHandler = { _, _ ->
                AgentVirtualGrepResult(
                    paths = listOf("人物/关系阶段"),
                    counts = mapOf("人物/关系阶段" to 1),
                    lines = listOf(AgentVirtualGrepLine("人物/关系阶段", 6, "好感度决定亲近倾向", 1)),
                    omittedPaths = 0,
                    omittedLines = 0,
                )
            },
        )
        val tool = requireNotNull(
            characterSettingLibraryGrepTool(
                listOf(agentEntry("relationship", "关系阶段", "人物", "正文")),
                search,
            ),
        )

        val result = tool.handler.execute(
            buildJsonObject {
                put("pattern", "好感度")
                put("output_mode", "content")
            },
        )
        val match = Json.parseToJsonElement(result.content)
            .jsonObject.getValue("matches").jsonArray.single().jsonObject

        assertEquals("人物/关系阶段", match.getValue("path").jsonPrimitive.content)
        assertEquals("6", match.getValue("line").jsonPrimitive.content)
    }

    @Test
    fun `read requires logical paths and returns complete setting body`() = runBlocking {
        val tool = requireNotNull(
            characterSettingLibraryReadTool(
                listOf(
                    agentEntry(
                        id = "magic",
                        title = "魔法体系",
                        groupPath = "世界观",
                        content = "魔法必须支付记忆作为代价。",
                    ),
                ),
            ),
        )

        val result = tool.handler.execute(
            buildJsonObject {
                put(
                    SettingLibraryPathsArgument,
                    buildJsonArray { add(JsonPrimitive("世界观/魔法体系")) },
                )
            },
        )
        val payload = Json.parseToJsonElement(result.content).jsonObject
        val entry = payload.getValue("files").jsonArray.single().jsonObject

        assertEquals(AgentReadSettingFilesTool, tool.definition.name)
        assertEquals("世界观/魔法体系", entry.getValue("path").jsonPrimitive.content)
        assertEquals("魔法必须支付记忆作为代价。", entry.getValue("content").jsonPrimitive.content)
        assertFalse(entry.containsKey("entry_id"))
        assertFalse(result.content.contains("magic"))
    }

    @Test
    fun `fixed required read cites frozen body while promoted required returns body`() = runBlocking {
        val fixed = agentEntry("fixed-rule", "魔法体系", "世界观", "魔法以记忆为代价。",
            readStrategy = SettingLibraryAgentReadStrategy.Required)
        val promoted = agentEntry("keyword-rule", "城门规则", "世界观", "午夜城门关闭。",
            readStrategy = SettingLibraryAgentReadStrategy.Keyword,
            promotedToRequiredThisTurn = true)
        val entries = listOf(fixed, promoted)
        val cache = RequiredSettingLibraryCache(entries)
        assertEquals("#S01", cache.entries.single().reference)
        val provider: suspend () -> SettingLibraryAgentTurnContext = { emptyTurnContext(entries) }
        val search = FakeAgentVirtualFileSearch(globHandler = { corpus, _ ->
            AgentVirtualGlobResult(paths = corpus.map { it.path }, omitted = 0)
        })
        val glob = characterSettingLibraryGlobTool(provider, search, cache)
        val read = characterSettingLibraryReadTool(provider, cache)

        val required = Json.parseToJsonElement(glob.handler.execute(buildJsonObject {}).content)
            .jsonObject.getValue("required_entries").jsonArray.map { it.jsonObject }
        assertEquals(2, required.size)
        assertEquals("cached_reference", required[0].getValue("content_delivery").jsonPrimitive.content)
        assertEquals(cache.entries.single().reference,
            required[0].getValue("cached_reference").jsonPrimitive.content)
        assertEquals("tool_result", required[1].getValue("content_delivery").jsonPrimitive.content)
        assertFalse(required[1].containsKey("cached_reference"))

        val payload = Json.parseToJsonElement(read.handler.execute(buildJsonObject {
            put(SettingLibraryPathsArgument, buildJsonArray {
                add(JsonPrimitive(fixed.path))
                add(JsonPrimitive(promoted.path))
            })
        }).content).jsonObject
        val files = payload.getValue("files").jsonArray.map { it.jsonObject }
        assertEquals(cache.entries.single().readReceipt, files[0].getValue("content").jsonPrimitive.content)
        assertFalse(files[0].getValue("content").jsonPrimitive.content.contains(fixed.content))
        assertEquals(cache.entries.single().reference,
            files[0].getValue("cached_reference").jsonPrimitive.content)
        assertEquals(promoted.content, files[1].getValue("content").jsonPrimitive.content)
        assertFalse(files[1].containsKey("cached_reference"))
    }

    @Test
    fun `changed fixed required body falls back to live tool content`() = runBlocking {
        val original = agentEntry("fixed-rule", "魔法体系", "世界观", "旧规则",
            readStrategy = SettingLibraryAgentReadStrategy.Required)
        val cache = RequiredSettingLibraryCache(listOf(original))
        var current = original
        val provider: suspend () -> SettingLibraryAgentTurnContext = { emptyTurnContext(listOf(current)) }
        val read = characterSettingLibraryReadTool(provider, cache)
        val arguments = buildJsonObject {
            put(SettingLibraryPathsArgument, buildJsonArray { add(JsonPrimitive(original.path)) })
        }

        current = original.copy(content = "新规则")
        val file = Json.parseToJsonElement(read.handler.execute(arguments).content)
            .jsonObject.getValue("files").jsonArray.single().jsonObject
        assertEquals("新规则", file.getValue("content").jsonPrimitive.content)
        assertEquals("tool_result", file.getValue("content_delivery").jsonPrimitive.content)
        assertFalse(file.containsKey("cached_reference"))
    }

    @Test
    fun `read returns only explicitly requested entries`() = runBlocking {
        val tool = requireNotNull(
            characterSettingLibraryReadTool(
                listOf(
                    agentEntry(
                        id = "required",
                        title = "核心规则",
                        groupPath = "世界书",
                        content = "始终生效。",
                        readStrategy = SettingLibraryAgentReadStrategy.Required,
                    ),
                    agentEntry(
                        id = "controller",
                        title = "剧情控制器",
                        groupPath = "世界书",
                        content = "第一集正文。",
                        readStrategy = SettingLibraryAgentReadStrategy.Keyword,
                        promotedToRequiredThisTurn = true,
                        resolvedReferences = listOf(
                            SettingLibraryResolvedReference(
                                id = "episode-1",
                                title = "第一集",
                                path = "世界书/第一集",
                            ),
                        ),
                    ),
                    agentEntry("optional", "当前人物", "人物", "人物正文。"),
                ),
            ),
        )

        val result = tool.handler.execute(
            buildJsonObject {
                put(
                    SettingLibraryPathsArgument,
                    buildJsonArray { add(JsonPrimitive("人物/当前人物")) },
                )
            },
        )
        val payload = Json.parseToJsonElement(result.content).jsonObject
        val files = payload.getValue("files").jsonArray.map { it.jsonObject }

        assertEquals(listOf("当前人物"), files.map { file ->
            file.getValue("title").jsonPrimitive.content
        })
        assertFalse(payload.containsKey("auto_included_count"))
        assertFalse(files.single().containsKey("auto_included"))
    }

    @Test
    fun `read returns complete setting bodies without a shared character budget`() = runBlocking {
        val firstContent = "甲".repeat(70_000)
        val secondContent = "乙".repeat(70_000)
        val tool = requireNotNull(
            characterSettingLibraryReadTool(
                listOf(
                    agentEntry("first", "第一份", "资料", firstContent),
                    agentEntry("second", "第二份", "资料", secondContent),
                ),
            ),
        )

        val result = tool.handler.execute(
            buildJsonObject {
                put(
                    SettingLibraryPathsArgument,
                    buildJsonArray {
                        add(JsonPrimitive("资料/第一份"))
                        add(JsonPrimitive("资料/第二份"))
                    },
                )
            },
        )
        val files = Json.parseToJsonElement(result.content)
            .jsonObject
            .getValue("files")
            .jsonArray
            .map { it.jsonObject }

        assertEquals(firstContent, files[0].getValue("content").jsonPrimitive.content)
        assertEquals(secondContent, files[1].getValue("content").jsonPrimitive.content)
        assertTrue(files.all { it.getValue("truncated").jsonPrimitive.content == "false" })
    }

    @Test
    fun `read rejects a guessed markdown path`() = runBlocking {
        val tool = requireNotNull(
            characterSettingLibraryReadTool(
                listOf(agentEntry("role", "人设", "核心", "正文")),
            ),
        )

        val result = tool.handler.execute(
            buildJsonObject {
                put(
                    SettingLibraryPathsArgument,
                    buildJsonArray { add(JsonPrimitive("核心/人设.md")) },
                )
            },
        )

        assertFalse(result.success)
        assertTrue(result.content.contains("not_found"))
    }

    @Test
    fun `same titles in different folders remain unambiguous by logical path`() = runBlocking {
        val tool = requireNotNull(
            characterSettingLibraryReadTool(
                listOf(
                    agentEntry("lia-a", "莉亚", "人物/王都", "版本 A"),
                    agentEntry("lia-b", "莉亚", "人物/山村", "版本 B"),
                ),
            ),
        )

        val result = tool.handler.execute(
            buildJsonObject {
                put(
                    SettingLibraryPathsArgument,
                    buildJsonArray { add(JsonPrimitive("人物/山村/莉亚")) },
                )
            },
        )

        assertTrue(result.success)
        assertTrue(result.content.contains("版本 B"))
        assertFalse(result.content.contains("版本 A"))
    }

    @Test
    fun `read accepts more than eight setting paths in one call`() = runBlocking {
        val entries = (1..9).map { index ->
            agentEntry("entry-$index", "设定$index", "批量", "正文$index")
        }
        val tool = requireNotNull(characterSettingLibraryReadTool(entries))

        val result = tool.handler.execute(
            buildJsonObject {
                put(
                    SettingLibraryPathsArgument,
                    buildJsonArray {
                        (1..9).forEach { index -> add(JsonPrimitive("批量/设定$index")) }
                    },
                )
            },
        )

        assertTrue(result.success)
        val files = Json.parseToJsonElement(result.content)
            .jsonObject.getValue("files").jsonArray
        assertEquals(9, files.size)
        assertEquals(null, tool.definition.parameters
            .getValue("properties").jsonObject
            .getValue(SettingLibraryPathsArgument).jsonObject["maxItems"])
    }

    @Test
    fun `mutation interface exposes file operations without database CRUD arguments`() {
        val tools = characterSettingLibraryMutationTools(
            contextProvider = {
                SettingLibraryAgentTurnContext(
                    automaticLibrary = SettingLibrary(characterId = "character-1"),
                    readableEntries = emptyList(),
                    groups = emptyList(),
                )
            },
            applyChanges = { error("not called") },
        )

        assertEquals(AgentSettingFileMutationTools, tools.map { it.definition.name }.toSet())
        tools.forEach { tool ->
            val properties = tool.definition.parameters.getValue("properties").jsonObject
            assertFalse(properties.containsKey("operation"))
            assertFalse(properties.containsKey("group_id"))
            assertFalse(properties.containsKey("entry_id"))
        }
    }

    @Test
    fun `setting library exposes independent search read and mutation tools`() {
        val tools = characterSettingLibraryTools(
            contextProvider = { emptyTurnContext() },
            virtualFileSearch = FakeAgentVirtualFileSearch(),
            applyChanges = { error("not called") },
        )

        assertEquals(AgentSettingLibraryTools, tools.map { it.definition.name }.toSet())
        assertTrue(AgentGlobSettingFilesTool in tools.map { it.definition.name })
        assertTrue(AgentGrepSettingFilesTool in tools.map { it.definition.name })
        assertTrue(AgentReadSettingFilesTool in tools.map { it.definition.name })
        assertTrue(tools.none { it.definition.name.contains("bash", ignoreCase = true) })
    }

    @Test
    fun `setting library patch facade keeps mutation operations behind one tool`() = runBlocking {
        var received: List<SettingLibrarySessionMutation> = emptyList()
        val tool = characterSettingLibraryPatchTool(
            contextProvider = {
                emptyTurnContext(
                    groups = listOf(SettingLibraryAgentGroup("people", "人物", "", "人物")),
                )
            },
            applyChanges = { mutations ->
                received = mutations
                SettingLibrarySessionMutationResult(
                    applied = emptyList(),
                    effectiveLibrary = SettingLibrary(characterId = "character-1"),
                )
            },
        )

        assertEquals(AgentApplySettingPatchTool, tool.definition.name)
        val result = tool.handler.execute(buildJsonObject {
            put("operation", "write_file")
            put("path", "人物/共同秘密")
            put("content", "两人共同发现了地下入口。")
        })

        assertTrue(result.success)
        assertEquals(1, received.size)
        assertTrue(received.single() is SettingLibrarySessionMutation.CreateEntry)
    }

    @Test
    fun `write file creates a session scoped setting`() = runBlocking {
        var received: List<SettingLibrarySessionMutation> = emptyList()
        val contextProvider: suspend () -> SettingLibraryAgentTurnContext = {
            SettingLibraryAgentTurnContext(
                automaticLibrary = SettingLibrary(characterId = "character-1"),
                readableEntries = emptyList(),
                groups = listOf(
                    SettingLibraryAgentGroup(
                        id = "people",
                        name = "人物",
                        parentId = "",
                        path = "人物",
                    ),
                ),
            )
        }
        val tool = characterSettingLibraryMutationTools(contextProvider) { mutations ->
            received = mutations
            SettingLibrarySessionMutationResult(
                applied = listOf(
                    SettingLibraryAppliedMutation(
                        operation = "create_entry",
                        targetType = "entry",
                        targetId = "session-setting-1",
                        title = "共同秘密",
                    ),
                ),
                effectiveLibrary = SettingLibrary(characterId = "character-1"),
            )
        }.single { it.definition.name == AgentWriteSettingFileTool }

        val result = tool.handler.execute(
            buildJsonObject {
                put("path", "人物/共同秘密")
                put("content", "两人共同发现了地下入口。")
                put("selection_hint", "涉及地下入口时读取。")
            },
        )

        assertEquals(AgentWriteSettingFileTool, tool.definition.name)
        assertTrue(tool.definition.description.contains("父目录不存在时自动创建"))
        val required = tool.definition.parameters.getValue("required").jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(listOf("path", "content"), required)
        assertTrue(result.success)
        val mutation = received.single() as SettingLibrarySessionMutation.CreateEntry
        assertEquals("people", mutation.groupId)
        assertEquals("共同秘密", mutation.title)
        assertEquals(
            "<path>人物/共同秘密</path>\n" +
                "<type>file</type>\n" +
                "<content>\n" +
                "Created file\n" +
                "</content>",
            result.content,
        )
    }

    @Test
    fun `write file overwrites an existing setting without recreating it`() = runBlocking {
        var received: List<SettingLibrarySessionMutation> = emptyList()
        val contextProvider: suspend () -> SettingLibraryAgentTurnContext = {
            SettingLibraryAgentTurnContext(
                automaticLibrary = SettingLibrary(characterId = "character-1"),
                readableEntries = listOf(
                    SettingLibraryAgentEntry(
                        id = "hall",
                        title = "体验馆大厅",
                        groupId = "current-scene",
                        groupPath = "当前场景",
                        path = "当前场景/体验馆大厅",
                        content = "旧正文",
                        selectionHint = "旧注释",
                    ),
                ),
                groups = listOf(
                    SettingLibraryAgentGroup(
                        id = "current-scene",
                        name = "当前场景",
                        parentId = "",
                        path = "当前场景",
                    ),
                ),
            )
        }
        val tool = characterSettingLibraryMutationTools(contextProvider) { mutations ->
            received = mutations
            SettingLibrarySessionMutationResult(
                applied = emptyList(),
                effectiveLibrary = SettingLibrary(characterId = "character-1"),
            )
        }.single { it.definition.name == AgentWriteSettingFileTool }

        val result = tool.handler.execute(
            buildJsonObject {
                put("path", "当前场景/体验馆大厅")
                put("content", "新正文")
            },
        )

        assertTrue(result.success)
        val mutation = received.single() as SettingLibrarySessionMutation.UpdateEntry
        assertEquals("hall", mutation.entryId)
        assertEquals("新正文", mutation.content)
        assertEquals(null, mutation.selectionHint)
        assertEquals(null, mutation.groupId)
        assertEquals(null, mutation.title)
        assertTrue(result.content.contains("Updated file"))
    }

    @Test
    fun `edit file replaces one exact literal like native DSH edit`() = runBlocking {
        var received: List<SettingLibrarySessionMutation> = emptyList()
        val contextProvider: suspend () -> SettingLibraryAgentTurnContext = {
            SettingLibraryAgentTurnContext(
                automaticLibrary = SettingLibrary(characterId = "character-1"),
                readableEntries = listOf(
                    SettingLibraryAgentEntry(
                        id = "archive",
                        title = "剧情存档",
                        groupId = "",
                        groupPath = "",
                        path = "剧情存档",
                        content = "第一幕：抵达大厅。\n第二幕：尚未开始。",
                    ),
                ),
                groups = emptyList(),
            )
        }
        val tool = characterSettingLibraryMutationTools(contextProvider) { mutations ->
            received = mutations
            SettingLibrarySessionMutationResult(
                applied = emptyList(),
                effectiveLibrary = SettingLibrary(characterId = "character-1"),
            )
        }.single { it.definition.name == AgentEditSettingFileTool }

        val result = tool.handler.execute(
            buildJsonObject {
                put("path", "剧情存档")
                put("old_string", "第二幕：尚未开始。")
                put("new_string", "第二幕：进入展厅。")
            },
        )

        assertTrue(result.success)
        assertEquals("The file 剧情存档 has been updated successfully.", result.content)
        val mutation = received.single() as SettingLibrarySessionMutation.UpdateEntry
        assertEquals("第一幕：抵达大厅。\n第二幕：进入展厅。", mutation.content)
    }

    @Test
    fun `edit file rejects a guaranteed no-op like native DSH edit`() = runBlocking {
        var applyCalls = 0
        val contextProvider: suspend () -> SettingLibraryAgentTurnContext = {
            SettingLibraryAgentTurnContext(
                automaticLibrary = SettingLibrary(characterId = "character-1"),
                readableEntries = listOf(
                    SettingLibraryAgentEntry(
                        id = "scene",
                        title = "当前场景",
                        groupId = "",
                        groupPath = "",
                        path = "当前场景",
                        content = "仍在大厅。",
                    ),
                ),
                groups = emptyList(),
            )
        }
        val tool = characterSettingLibraryMutationTools(contextProvider) {
            applyCalls += 1
            error("not called")
        }.single { it.definition.name == AgentEditSettingFileTool }

        val result = tool.handler.execute(
            buildJsonObject {
                put("path", "当前场景")
                put("old_string", "仍在大厅。")
                put("new_string", "仍在大厅。")
            },
        )

        assertFalse(result.success)
        assertTrue(result.content.contains("old_string and new_string must differ"))
        assertEquals(0, applyCalls)
    }

    @Test
    fun `write file automatically creates nested parent directories`() = runBlocking {
        var received: List<SettingLibrarySessionMutation> = emptyList()
        val contextProvider: suspend () -> SettingLibraryAgentTurnContext = {
            SettingLibraryAgentTurnContext(
                automaticLibrary = SettingLibrary(characterId = "character-1"),
                readableEntries = emptyList(),
                groups = emptyList(),
            )
        }
        val tool = characterSettingLibraryMutationTools(contextProvider) { mutations ->
            received = mutations
            SettingLibrarySessionMutationResult(
                applied = emptyList(),
                effectiveLibrary = SettingLibrary(characterId = "character-1"),
            )
        }.single { it.definition.name == AgentWriteSettingFileTool }

        val result = tool.handler.execute(
            buildJsonObject {
                put("path", "夜之灯塔/背景/世界背景")
                put("content", "灯塔立于雾海。")
            },
        )

        assertTrue(result.success)
        val root = received[0] as SettingLibrarySessionMutation.CreateGroup
        val child = received[1] as SettingLibrarySessionMutation.CreateGroup
        assertEquals(root.groupId, child.parentId)
        assertEquals(child.groupId, (received[2] as SettingLibrarySessionMutation.CreateEntry).groupId)
    }

    @Test
    fun `move file automatically creates the target parent directory`() = runBlocking {
        var received: List<SettingLibrarySessionMutation> = emptyList()
        val contextProvider: suspend () -> SettingLibraryAgentTurnContext = {
            SettingLibraryAgentTurnContext(
                automaticLibrary = SettingLibrary(characterId = "character-1"),
                readableEntries = listOf(
                    SettingLibraryAgentEntry(
                        id = "scene",
                        title = "现场",
                        groupId = "",
                        groupPath = "",
                        path = "现场",
                        content = "临时现场。",
                    ),
                ),
                groups = emptyList(),
            )
        }
        val tool = characterSettingLibraryMutationTools(contextProvider) { mutations ->
            received = mutations
            SettingLibrarySessionMutationResult(
                applied = emptyList(),
                effectiveLibrary = SettingLibrary(characterId = "character-1"),
            )
        }.single { it.definition.name == AgentMoveSettingFileTool }

        val result = tool.handler.execute(
            buildJsonObject {
                put("path", "现场")
                put("destination", "归档/现场")
            },
        )

        assertTrue(result.success)
        val archive = received[0] as SettingLibrarySessionMutation.CreateGroup
        val moved = received[1] as SettingLibrarySessionMutation.UpdateEntry
        assertEquals(archive.groupId, moved.groupId)
        assertEquals("现场", moved.title)
        assertEquals("scene", moved.entryId)
    }

    @Test
    fun `live read provider sees changes made after tool creation`() = runBlocking {
        var content = "修改前"
        val provider: suspend () -> SettingLibraryAgentTurnContext = {
            SettingLibraryAgentTurnContext(
                automaticLibrary = SettingLibrary(characterId = "character-1"),
                readableEntries = listOf(
                    SettingLibraryAgentEntry(
                        id = "memory",
                        title = "共同记忆",
                        groupId = "dynamic",
                        groupPath = "动态设定",
                        path = "动态设定/共同记忆",
                        content = content,
                    ),
                ),
                groups = listOf(
                    SettingLibraryAgentGroup(
                        id = "dynamic",
                        name = "动态设定",
                        parentId = "",
                        path = "动态设定",
                    ),
                ),
            )
        }
        val readTool = characterSettingLibraryReadTool(provider)
        val globTool = characterSettingLibraryGlobTool(
            contextProvider = provider,
            virtualFileSearch = FakeAgentVirtualFileSearch(
                globHandler = { corpus, _ ->
                    AgentVirtualGlobResult(paths = corpus.map { it.path }, omitted = 0)
                },
            ),
        )
        val arguments = buildJsonObject {
            put(
                SettingLibraryPathsArgument,
                buildJsonArray { add(JsonPrimitive("动态设定/共同记忆")) },
            )
        }

        assertTrue(readTool.handler.execute(arguments).content.contains("修改前"))
        content = "修改后"
        assertTrue(readTool.handler.execute(arguments).content.contains("修改后"))
        val globArguments = buildJsonObject { put("pattern", "**") }
        assertTrue(globTool.handler.execute(globArguments).content.contains("动态设定/共同记忆"))
        assertFalse(globTool.handler.execute(globArguments).content.contains("\"id\":\"dynamic\""))
    }

    private fun agentEntry(
        id: String,
        title: String,
        groupPath: String,
        content: String,
        selectionHint: String = "",
        readStrategy: SettingLibraryAgentReadStrategy = SettingLibraryAgentReadStrategy.Normal,
        promotedToRequiredThisTurn: Boolean = false,
        resolvedReferences: List<SettingLibraryResolvedReference> = emptyList(),
    ) = SettingLibraryAgentEntry(
        id = id,
        title = title,
        groupPath = groupPath,
        path = listOf(groupPath, title).filter(String::isNotBlank).joinToString("/"),
        content = content,
        selectionHint = selectionHint,
        readStrategy = readStrategy,
        promotedToRequiredThisTurn = promotedToRequiredThisTurn,
        resolvedReferences = resolvedReferences,
    )

    private fun emptyTurnContext(
        entries: List<SettingLibraryAgentEntry> = emptyList(),
        groups: List<SettingLibraryAgentGroup> = emptyList(),
    ) = SettingLibraryAgentTurnContext(
        automaticLibrary = SettingLibrary(characterId = "character-1"),
        readableEntries = entries,
        groups = groups,
    )
}
