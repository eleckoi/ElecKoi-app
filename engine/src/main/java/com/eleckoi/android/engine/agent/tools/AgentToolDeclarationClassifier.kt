package com.eleckoi.android.engine.agent.tools

import com.eleckoi.android.engine.agent.api.AgentApplyVariablePatchTool
import com.eleckoi.android.engine.agent.api.AgentApplySettingPatchTool
import com.eleckoi.android.engine.agent.api.AgentSettingFileMutationTools
import com.eleckoi.android.engine.agent.api.AgentGlobSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGlobVariablesTool
import com.eleckoi.android.engine.agent.api.AgentGrepSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGrepVariablesTool
import com.eleckoi.android.engine.agent.api.AgentReadSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentReadVariablesTool
import com.eleckoi.android.engine.agent.api.AgentRemoteDshTaskTool
import com.eleckoi.android.engine.agent.api.AgentToolContextBlock
import com.eleckoi.android.engine.agent.api.AgentUpdateRoleplayPlanTool
import com.eleckoi.android.engine.agent.api.AgentWebSearchTool
import com.eleckoi.android.engine.agent.api.AgentNativeWebSearchBridgeTool
import com.eleckoi.android.engine.agent.api.AgentCreatorMetaTools
import com.eleckoi.android.engine.agent.api.AgentListCreatorToolsetsTool
import com.eleckoi.android.engine.agent.api.AgentDescribeCreatorToolsetTool
import com.eleckoi.android.engine.agent.api.AgentCallCreatorCapabilityTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Maps wire-level Harness declarations to the stable user-facing capability groups. */
internal fun classifyAgentToolDeclaration(element: JsonElement): AgentToolGroupSnapshot? {
    val declaration = element as? JsonObject ?: return null
    val type = declaration.string("type")
    if (type == "namespace") {
        val namespace = declaration.string("name")?.takeIf(String::isNotBlank) ?: return null
        val members = (declaration["tools"] as? JsonArray)
            .orEmpty()
            .mapNotNull { child ->
                val tool = child as? JsonObject ?: return@mapNotNull null
                val name = tool.string("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                AgentToolRequestPolicy.member(name, tool.string("description").orEmpty())
            }
        return namespaceGroup(
            namespace = namespace,
            description = declaration.string("description").orEmpty(),
            members = members,
        )
    }

    val name = declaration.string("name")?.takeIf(String::isNotBlank)
        ?: return when (type) {
            "web_search" -> builtInToolGroupWithNames(
                id = AgentToolRequestPolicy.BuiltInWeb,
                name = "联网搜索",
                description = "搜索公开互联网，获取最新事实和可引用来源",
                members = listOf("web_search"),
            )
            else -> null
        }
    val member = AgentToolRequestPolicy.member(name, declaration.string("description").orEmpty())
    return when (name) {
        AgentWebSearchTool,
        AgentNativeWebSearchBridgeTool,
        -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInWeb,
            "联网搜索",
            "搜索公开互联网，获取最新事实和可引用来源",
            listOf(member),
        )
        AgentReadSettingFilesTool,
        AgentGlobSettingFilesTool,
        AgentGrepSettingFilesTool,
        -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInSettingLibrary,
            "角色设定库",
            "读取角色基础设定，并把 AI 修改作为当前对话差异保存",
            listOf(member),
        )
        AgentRemoteDshTaskTool -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInRemoteDsh,
            "远端 DSH",
            "把需要操作电脑的任务交给电脑上的 DSH，并把执行结果带回当前角色对话",
            listOf(member),
        )
        in AgentSettingFileMutationTools -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInSettingLibrary,
            "角色设定库",
            "读取角色基础设定，并把 AI 修改作为当前对话差异保存",
            listOf(member),
        )
        AgentApplySettingPatchTool -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInSettingLibrary,
            "角色设定库",
            "读取角色基础设定，并把 AI 修改作为当前对话差异保存",
            listOf(member),
        )
        AgentGlobVariablesTool,
        AgentGrepVariablesTool,
        AgentReadVariablesTool,
        AgentApplyVariablePatchTool,
        -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInVariables,
            "剧情变量",
            "按路径模式查找或按内容搜索剧情变量，并通过校验后的补丁更新状态",
            listOf(member),
        )
        in AgentCreatorMetaTools -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInCreator,
            "创作能力",
            "按需发现并调用角色创作、设定库与图片生成能力",
            listOf(member),
        )
        in WorkspaceTools -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInWorkspace,
            "本地工作区",
            "执行命令并修改角色或创作工作区中的文件",
            listOf(member),
        )
        in WorkflowTools -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInWorkflow,
            "任务与交互",
            "维护任务计划，并在确有必要时向用户提问",
            listOf(member),
        )
        in RoleplayWorkflowTools -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
            "角色扮演计划",
            "维护角色扮演专用任务计划",
            listOf(member),
        )
        in McpResourceTools -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInMcpResources,
            "MCP 资源读取",
            "列出并读取 MCP 服务器提供的资源和资源模板",
            listOf(member),
        )
        in PluginDiscoveryTools -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInPluginDiscovery,
            "插件发现",
            "发现并请求安装当前尚未启用的插件",
            listOf(member),
        )
        in CollaborationTools -> builtInToolGroup(
            AgentToolRequestPolicy.BuiltInCollaboration,
            "多代理协作",
            "创建和管理并行子任务；普通角色通常不需要",
            listOf(member),
        )
        // Never surfaced as a switch; see AgentToolRequestPolicy.HiddenGroupIds.
        else -> builtInToolGroup(
            id = AgentToolRequestPolicy.BuiltInOther,
            name = "未识别的工具",
            description = "当前 Harness 提供、但本应用还没有归类的工具",
            members = listOf(member),
        )
    }
}

private fun namespaceGroup(
    namespace: String,
    description: String,
    members: List<AgentToolMember>,
): AgentToolGroupSnapshot {
    if (namespace == "collaboration") {
        return builtInToolGroup(
            id = AgentToolRequestPolicy.BuiltInCollaboration,
            name = "多代理协作",
            description = "创建和管理并行子任务；普通角色通常不需要",
            members = members,
        )
    }
    if (namespace == "web") {
        return builtInToolGroup(
            id = AgentToolRequestPolicy.BuiltInWeb,
            name = "联网搜索",
            description = "搜索并读取互联网内容",
            members = members,
        )
    }
    if (namespace.startsWith(McpNamespacePrefix)) {
        val serverId = namespace.removePrefix(McpNamespacePrefix)
        return AgentToolGroupSnapshot(
            id = AgentToolRequestPolicy.mcpGroupId(serverId),
            name = if (serverId == "exa") "联网（Exa）" else serverId,
            description = description.normalizedDescription().ifBlank {
                if (serverId == "exa") "搜索网页并抓取完整页面内容" else "来自 $serverId MCP 服务器"
            },
            source = AgentToolGroupSource.Mcp,
            sourceId = serverId,
            members = members.distinctBy(AgentToolMember::name),
        )
    }
    return AgentToolGroupSnapshot(
        id = "extension:$namespace",
        name = namespace,
        description = description.normalizedDescription().ifBlank { "由扩展提供的一组工具" },
        source = AgentToolGroupSource.Extension,
        sourceId = namespace,
        members = members.distinctBy(AgentToolMember::name),
    )
}

internal fun builtInToolGroupWithNames(
    id: String,
    name: String,
    description: String,
    members: List<String>,
): AgentToolGroupSnapshot = builtInToolGroup(
    id = id,
    name = name,
    description = description,
    members = members.map { AgentToolRequestPolicy.member(it) },
)

internal fun builtInToolGroup(
    id: String,
    name: String,
    description: String,
    members: List<AgentToolMember>,
): AgentToolGroupSnapshot = AgentToolGroupSnapshot(
    id = id,
    name = name,
    description = description,
    source = AgentToolGroupSource.BuiltIn,
    members = members.distinctBy(AgentToolMember::name),
)

internal fun List<AgentToolGroupSnapshot>.mergeAgentToolGroups(): AgentToolGroupSnapshot {
    val first = first()
    return first.copy(
        description = firstNotNullOfOrNull { it.description.takeIf(String::isNotBlank) }.orEmpty(),
        members = flatMap(AgentToolGroupSnapshot::members).distinctBy(AgentToolMember::name),
    )
}

internal fun JsonElement.isEssentialAgentTool(): Boolean {
    val declaration = this as? JsonObject ?: return false
    if (declaration.string("type") == "namespace") return false
    val name = declaration.string("name").orEmpty()
    return name == CapabilityProbeTool ||
        name.startsWith("eleckoi_internal_")
}

private fun JsonObject.string(name: String): String? =
    (get(name) as? JsonPrimitive)?.contentOrNull

private const val McpNamespacePrefix = "mcp__"
private const val CapabilityProbeTool = "eleckoi_capability_probe"
private val WorkspaceTools = setOf(
    "shell_command",
    "bash",
    "read",
    "read_image",
    "edit",
    "write",
    "exec_command",
    "write_stdin",
    "apply_patch",
    "request_permissions",
)
private val WorkflowTools = setOf(
    "update_plan",
    "todo_write",
    "request_user_input",
    "get_goal",
    "create_goal",
    "update_goal",
    "get_context_remaining",
    "new_context_window",
)
private val RoleplayWorkflowTools = setOf(AgentUpdateRoleplayPlanTool)
private val McpResourceTools = setOf(
    "list_mcp_resources",
    "list_mcp_resource_templates",
    "read_mcp_resource",
)
private val PluginDiscoveryTools = setOf(
    "request_plugin_install",
    "list_available_plugins_to_install",
)
private val CollaborationTools = setOf(
    "subagent",
    "spawn_agent",
    "send_input",
    "resume_agent",
    "wait_agent",
    "close_agent",
    "send_message",
    "followup_task",
    "interrupt_agent",
    "list_agents",
)
