package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.agent.api.AgentSessionOptions
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DshAgentPresetMaterializerTest {
    @Test
    fun `materializes prompt and tools inside the official preset composition`() {
        val root = Files.createTempDirectory("dsh-agent-preset").toFile()
        try {
            val materializer = DshAgentPresetMaterializer(root)
            val id = materializer.materialize(
                options = options(tool("story_lookup", "Lookup story facts")).copy(
                    historyCompactionInstructions = "角色摘要模板",
                ),
                systemInstructions = "System line\nDeveloper line",
                contextWindow = 1_000_000,
                autoCompactTokenLimit = 800_000,
            )

            val composition = File(root, ".agent-presets/$id/agent.cordis.yml").readText()
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-persona'"))
            assertTrue(composition.contains("prefix: \"System line\\nDeveloper line\""))
            assertTrue(composition.contains("includeRuntimeContext: false"))
            assertFalse(composition.contains("dsh-agent-spine-demo"))
            assertTrue(composition.contains("name: /deepseek-home/eleckoi/eleckoi-host-tools.mjs"))
            assertTrue(composition.contains("tools: [{\"name\":\"story_lookup\""))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-tool-subagent'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-tool-subagent-control'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-tool-subagent-control/list-agents'"))
            assertTrue(composition.contains("toolName: subagent_fork"))
            assertTrue(composition.contains("backgroundMode: continuable"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-command-compact'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-compaction-tool-result-pruner'"))
            assertTrue(composition.contains("只返回非空的纯文本摘要正文"))
            assertTrue(composition.contains("角色摘要模板"))
            assertFalse(composition.contains("ELECKOI_HOST_TOOL_CATALOG"))
            assertFalse(composition.contains("DSH_SYSTEM_PROMPT"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `workflow switch mounts the complete official DSH workflow plane`() {
        val root = Files.createTempDirectory("dsh-agent-preset-workflow").toFile()
        try {
            val materializer = DshAgentPresetMaterializer(root)
            val id = materializer.materialize(
                options = AgentSessionOptions(
                    workspaceId = "workspace",
                    conversationId = "conversation",
                    enabledToolGroupIds = setOf(AgentToolRequestPolicy.BuiltInWorkflow),
                ),
                systemInstructions = "Prompt",
                contextWindow = 100_000,
                autoCompactTokenLimit = null,
            )

            val composition = File(root, ".agent-presets/$id/agent.cordis.yml").readText()
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-workflow-worker-thread'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-tool-workflow'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-tool-todo'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-tool-jobs'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-skill-filesystem'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-tool-skill'"))
            assertTrue(composition.contains("name: '@deepseek-ai/dsh-tool-goal'"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `keeps conflicting dynamic tool schemas in separate preset scopes`() {
        val root = Files.createTempDirectory("dsh-agent-preset-isolation").toFile()
        try {
            val materializer = DshAgentPresetMaterializer(root)
            val first = materializer.materialize(
                options = options(tool("lookup", "First schema")),
                systemInstructions = "First prompt",
                contextWindow = 100_000,
                autoCompactTokenLimit = null,
            )
            val second = materializer.materialize(
                options = options(tool("lookup", "Second schema")),
                systemInstructions = "Second prompt",
                contextWindow = 100_000,
                autoCompactTokenLimit = null,
            )

            assertNotEquals(first, second)
            val firstComposition = File(root, ".agent-presets/$first/agent.cordis.yml").readText()
            val secondComposition = File(root, ".agent-presets/$second/agent.cordis.yml").readText()
            assertTrue(firstComposition.contains("First schema"))
            assertFalse(firstComposition.contains("Second schema"))
            assertTrue(secondComposition.contains("Second schema"))
            assertFalse(secondComposition.contains("First schema"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun options(tool: AgentDynamicTool) = AgentSessionOptions(
        workspaceId = "workspace",
        conversationId = "conversation",
        enabledToolGroupIds = setOf(AgentToolRequestPolicy.BuiltInCollaboration),
        dynamicTools = listOf(tool),
    )

    private fun tool(name: String, description: String) = AgentDynamicTool(
        definition = AgentToolDefinition(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
            },
        ),
        handler = { _ -> AgentDynamicToolResult("ok") },
    )
}
