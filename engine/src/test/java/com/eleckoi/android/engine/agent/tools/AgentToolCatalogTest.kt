package com.eleckoi.android.engine.agent.tools

import com.eleckoi.android.engine.agent.api.AgentUpdateRoleplayPlanTool
import com.eleckoi.android.engine.agent.api.AgentGlobSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentApplyVariablePatchTool
import com.eleckoi.android.engine.agent.api.AgentReadVariablesTool
import com.eleckoi.android.engine.agent.api.AgentReadSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGrepSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGrepVariablesTool
import com.eleckoi.android.engine.agent.api.AgentWebSearchTool
import com.eleckoi.android.engine.agent.api.AgentNativeWebSearchBridgeTool
import com.eleckoi.android.engine.agent.api.AgentRemoteDshTaskTool
import com.eleckoi.android.engine.agent.api.AgentSettingLibraryTools
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolCatalogTest {
    @Test
    fun `roleplay plan is isolated in its own built in group`() {
        val group = AgentToolRequestPolicy.builtInGroups()
            .single { it.id == AgentToolRequestPolicy.BuiltInRoleplayWorkflow }

        assertEquals(listOf(AgentUpdateRoleplayPlanTool), group.members.map { it.name })
    }

    @Test
    fun `setting library advertises independent search read and mutation tools`() {
        val group = AgentToolRequestPolicy.builtInGroups()
            .single { it.id == AgentToolRequestPolicy.BuiltInSettingLibrary }

        assertEquals(AgentSettingLibraryTools.toList(), group.members.map { it.name })
        assertEquals(
            listOf(
                "查找设定文件",
                "搜索设定内容",
                "读取设定正文",
                "管理对话设定文件",
            ),
            group.members.map { it.displayName },
        )
    }

    @Test
    fun `web search is visible before a provider tool has been observed`() {
        val group = AgentToolRequestPolicy.builtInGroups()
            .single { it.id == AgentToolRequestPolicy.BuiltInWeb }

        assertEquals("联网搜索", group.name)
        assertTrue(AgentWebSearchTool in group.members.map { it.name })
    }

    @Test
    fun `native web search bridge follows the preset tool switch`() {
        val request = json(
            """
            {
              "tools":[
                {"type":"function","name":"$AgentNativeWebSearchBridgeTool","parameters":{"type":"object"}}
              ]
            }
            """.trimIndent(),
        )

        val enabled = AgentToolRequestPolicy.filter(request) { true }
        val disabled = AgentToolRequestPolicy.filter(request) { false }

        assertEquals(
            AgentToolRequestPolicy.BuiltInWeb,
            enabled.observedGroups.single().id,
        )
        assertEquals(1, (enabled.request["tools"] as JsonArray).size)
        assertTrue((disabled.request["tools"] as JsonArray).isEmpty())
    }

    @Test
    fun `remote dsh is exposed as a selectable preset tool group`() {
        val group = AgentToolRequestPolicy.builtInGroups()
            .single { it.id == AgentToolRequestPolicy.BuiltInRemoteDsh }

        assertEquals("远端 DSH", group.name)
        assertEquals(listOf(AgentRemoteDshTaskTool), group.members.map { it.name })
    }

    @Test
    fun `filters disabled groups including setting library tool`() {
        val request = json(
            """
            {
              "input":[{"type":"message","role":"user","content":"hi"}],
              "tools":[
                {"type":"function","name":"shell_command","description":"run","parameters":{"type":"object"}},
                {
                  "type":"namespace",
                  "name":"collaboration",
                  "description":"agents",
                  "tools":[
                    {"type":"function","name":"spawn_agent","description":"spawn","parameters":{"type":"object"}},
                    {"type":"function","name":"wait_agent","description":"wait","parameters":{"type":"object"}}
                  ]
                },
                {
                  "type":"namespace",
                  "name":"mcp__exa",
                  "description":"search",
                  "tools":[
                    {"type":"function","name":"web_search_exa","description":"search","parameters":{"type":"object"}},
                    {"type":"function","name":"web_fetch_exa","description":"fetch","parameters":{"type":"object"}}
                  ]
                },
                {"type":"function","name":"$AgentReadSettingFilesTool","parameters":{"type":"object"}}
              ],
              "tool_choice":"auto"
            }
            """.trimIndent(),
        )

        val result = AgentToolRequestPolicy.filter(request) { groupId ->
            groupId !in setOf(
                AgentToolRequestPolicy.BuiltInWorkspace,
                AgentToolRequestPolicy.mcpGroupId("exa"),
                AgentToolRequestPolicy.BuiltInSettingLibrary,
            )
        }

        val names = (result.request["tools"] as JsonArray).map { declaration ->
            declaration.jsonObject.string("name")
        }
        assertEquals(listOf("collaboration"), names)
        assertTrue(result.observedGroups.any { it.id == AgentToolRequestPolicy.BuiltInWorkspace })
        assertTrue(result.observedGroups.any { it.id == AgentToolRequestPolicy.BuiltInSettingLibrary })
        val exa = result.observedGroups.single { it.id == AgentToolRequestPolicy.mcpGroupId("exa") }
        assertEquals(listOf("web_search_exa", "web_fetch_exa"), exa.members.map { it.name })
    }

    @Test
    fun `classifies DeepSeek native tools under the same user switches`() {
        val request = json(
            """
            {
              "tools":[
                {"type":"function","name":"bash","parameters":{"type":"object"}},
                {"type":"function","name":"read","parameters":{"type":"object"}},
                {"type":"function","name":"edit","parameters":{"type":"object"}},
                {"type":"function","name":"write","parameters":{"type":"object"}},
                {"type":"function","name":"todo_write","parameters":{"type":"object"}},
                {"type":"function","name":"subagent","parameters":{"type":"object"}}
              ]
            }
            """.trimIndent(),
        )

        val enabled = AgentToolRequestPolicy.filter(request) { true }
        assertEquals(
            mapOf(
                AgentToolRequestPolicy.BuiltInWorkspace to listOf("bash", "read", "edit", "write"),
                AgentToolRequestPolicy.BuiltInWorkflow to listOf("todo_write"),
                AgentToolRequestPolicy.BuiltInCollaboration to listOf("subagent"),
            ),
            enabled.observedGroups.associate { group -> group.id to group.members.map { it.name } },
        )

        val disabled = AgentToolRequestPolicy.filter(request) { false }
        assertTrue((disabled.request["tools"] as JsonArray).isEmpty())
    }

    @Test
    fun `preserves only transport-internal probes when every visible group is disabled`() {
        val request = json(
            """
            {
              "tools":[
                {"type":"function","name":"$AgentReadSettingFilesTool","parameters":{"type":"object"}},
                {"type":"function","name":"eleckoi_capability_probe","parameters":{"type":"object"}}
              ]
            }
            """.trimIndent(),
        )

        val result = AgentToolRequestPolicy.filter(request) { false }
        val names = (result.request["tools"] as JsonArray).map { declaration ->
            declaration.jsonObject.string("name")
        }

        assertEquals(listOf("eleckoi_capability_probe"), names)
    }

    @Test
    fun `removes forced tool choice when its declaration is filtered`() {
        val request = json(
            """
            {
              "input":[{"type":"message","role":"user","content":"hi"}],
              "tools":[{"type":"function","name":"view_image","parameters":{"type":"object"}}],
              "tool_choice":{"type":"function","name":"view_image"}
            }
            """.trimIndent(),
        )

        val result = AgentToolRequestPolicy.filter(request) { false }

        assertTrue((result.request["tools"] as JsonArray).isEmpty())
        assertFalse("tool_choice" in result.request)
    }

    @Test
    fun `classifies the character setting tool in the setting library group`() {
        val request = json(
            """
            {
              "tools":[
                {
                  "type":"function",
                  "name":"$AgentReadSettingFilesTool",
                  "description":"setting tree",
                  "parameters":{"type":"object"}
                },
                {
                  "type":"function",
                  "name":"$AgentGlobSettingFilesTool",
                  "description":"list setting tree",
                  "parameters":{"type":"object"}
                },
                {
                  "type":"function",
                  "name":"$AgentGrepSettingFilesTool",
                  "description":"search settings",
                  "parameters":{"type":"object"}
                }
              ]
            }
            """.trimIndent(),
        )

        val result = AgentToolRequestPolicy.filter(request) { true }
        val group = result.observedGroups.single()

        assertEquals(AgentToolRequestPolicy.BuiltInSettingLibrary, group.id)
        assertEquals(
            listOf("读取设定正文", "查找设定文件", "搜索设定内容"),
            group.members.map { it.displayName },
        )
        assertEquals(
            listOf(
                "读取 Glob 或 Grep 返回的虚拟设定文件正文，不会修改设定。",
                "使用 Glob 路径模式查找虚拟设定文件，不读取正文。",
                "使用 ripgrep 正则搜索虚拟设定文件的标题、作者注释和正文。",
            ),
            group.members.map { it.description },
        )
    }

    @Test
    fun `classifies the bundled web search tool as the web group`() {
        val request = json(
            """
            {
              "tools":[
                {
                  "type":"function",
                  "name":"$AgentWebSearchTool",
                  "description":"搜索公开互联网",
                  "parameters":{"type":"object"}
                }
              ]
            }
            """.trimIndent(),
        )

        val result = AgentToolRequestPolicy.filter(request) { true }
        val group = result.observedGroups.single()

        assertEquals(AgentToolRequestPolicy.BuiltInWeb, group.id)
        assertEquals("联网搜索", group.name)
        assertEquals(listOf("联网搜索"), group.members.map { it.displayName })
    }

    @Test
    fun `classifies character variable tools in their own group`() {
        val request = json(
            """
            {
              "tools":[
                {
                  "type":"function",
                  "name":"$AgentReadVariablesTool",
                  "description":"read variables",
                  "parameters":{"type":"object"}
                },
                {
                  "type":"function",
                  "name":"$AgentApplyVariablePatchTool",
                  "description":"patch variables",
                  "parameters":{"type":"object"}
                },
                {
                  "type":"function",
                  "name":"$AgentGrepVariablesTool",
                  "description":"search variables",
                  "parameters":{"type":"object"}
                }
              ]
            }
            """.trimIndent(),
        )

        val result = AgentToolRequestPolicy.filter(request) { true }
        val group = result.observedGroups.single()

        assertEquals(AgentToolRequestPolicy.BuiltInVariables, group.id)
        assertEquals(
            listOf("读取变量", "修改变量", "搜索变量内容"),
            group.members.map { it.displayName },
        )
    }

    @Test
    fun `merges repeated declarations without changing first seen member order`() {
        val request = json(
            """
            {
              "tools":[
                {"type":"function","name":"shell_command","parameters":{"type":"object"}},
                {"type":"function","name":"edit","parameters":{"type":"object"}},
                {"type":"function","name":"shell_command","parameters":{"type":"object"}}
              ]
            }
            """.trimIndent(),
        )

        val result = AgentToolRequestPolicy.filter(request) { true }
        val workspace = result.observedGroups.single()

        assertEquals(AgentToolRequestPolicy.BuiltInWorkspace, workspace.id)
        assertEquals(listOf("shell_command", "edit"), workspace.members.map { it.name })
        assertEquals(3, (result.request["tools"] as JsonArray).size)
    }

    private fun json(value: String): JsonObject =
        ElecKoiJson.parseToJsonElement(value).jsonObject

    private fun JsonObject.string(name: String): String =
        (get(name) as JsonPrimitive).contentOrNull.orEmpty()
}
