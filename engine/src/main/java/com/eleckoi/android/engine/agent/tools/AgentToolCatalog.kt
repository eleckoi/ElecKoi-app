package com.eleckoi.android.engine.agent.tools

import com.eleckoi.android.engine.agent.api.AgentToolContextBlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class AgentToolGroupSource {
    BuiltIn,
    Mcp,
    Extension,
}

data class AgentToolMember(
    val name: String,
    val displayName: String,
    val description: String = "",
)

data class AgentToolGroupSnapshot(
    val id: String,
    val name: String,
    val description: String,
    val source: AgentToolGroupSource,
    val sourceId: String = "",
    val members: List<AgentToolMember> = emptyList(),
    val enabled: Boolean = true,
)

/** Ordered tool groups plus Harness prompt blocks controlled by those same groups. */
data class AgentToolContextSnapshot(
    val groups: List<AgentToolGroupSnapshot>,
    val blocks: List<AgentToolContextBlock>,
) {
    fun isEnabled(groupId: String): Boolean = groups.firstOrNull { it.id == groupId }?.enabled == true

    fun orderOf(groupId: String): Int = groups.indexOfFirst { it.id == groupId }
        .takeIf { it >= 0 }
        ?.let { (it + 1) * ToolContextOrderStride }
        ?: (groups.size + 1) * ToolContextOrderStride

    private companion object {
        const val ToolContextOrderStride = 1_000
    }
}

data class AgentToolFilteringResult(
    val request: JsonObject,
    val observedGroups: List<AgentToolGroupSnapshot>,
)

/**
 * Stable public facade for filtering Harness declarations and exposing the built-in catalog.
 * Classification, presentation, and catalog definitions live in focused sibling files.
 */
object AgentToolRequestPolicy {
    fun filter(
        request: JsonObject,
        isGroupEnabled: (String) -> Boolean,
    ): AgentToolFilteringResult {
        val tools = request["tools"] as? JsonArray
            ?: return AgentToolFilteringResult(request, emptyList())
        val declarations = tools.map { declaration ->
            ParsedDeclaration(
                element = declaration,
                group = classifyAgentToolDeclaration(declaration),
                essential = declaration.isEssentialAgentTool(),
            )
        }
        val observedGroups = declarations
            .mapNotNull(ParsedDeclaration::group)
            .groupBy(AgentToolGroupSnapshot::id)
            .map { (_, groups) -> groups.mergeAgentToolGroups() }
            .sortedWith(compareBy(AgentToolGroupSnapshot::source, AgentToolGroupSnapshot::name))
        val retained = declarations.filter { declaration ->
            declaration.essential ||
                declaration.group == null ||
                isGroupEnabled(declaration.group.id)
        }
        if (retained.size == declarations.size) {
            return AgentToolFilteringResult(request, observedGroups)
        }

        val filtered = request.toMutableMap()
        filtered["tools"] = JsonArray(retained.map(ParsedDeclaration::element))
        // An explicit forced-tool object becomes invalid if its declaration was removed.
        // Ordinary Agent turns use "auto"; preserve that portable behavior.
        if (request["tool_choice"] is JsonObject) filtered.remove("tool_choice")
        return AgentToolFilteringResult(JsonObject(filtered), observedGroups)
    }

    fun builtInGroups(): List<AgentToolGroupSnapshot> = builtInAgentToolGroups()

    fun mcpGroupId(serverId: String): String = "mcp:${serverId.trim()}"

    fun mcpServerId(groupId: String): String? =
        groupId.takeIf { it.startsWith(McpPrefix) }
            ?.removePrefix(McpPrefix)
            ?.takeIf(String::isNotBlank)

    fun member(name: String, description: String = ""): AgentToolMember =
        agentToolMember(name, description)

    private data class ParsedDeclaration(
        val element: JsonElement,
        val group: AgentToolGroupSnapshot?,
        val essential: Boolean,
    )

    const val BuiltInWorkspace = "builtin:workspace"
    const val BuiltInVisual = "builtin:visual"
    const val BuiltInWorkflow = "builtin:workflow"
    const val BuiltInRoleplayWorkflow = "builtin:roleplay-workflow"
    const val BuiltInAutoIllustration = "builtin:auto-illustration"
    const val BuiltInCollaboration = "builtin:collaboration"
    const val BuiltInMcpResources = "builtin:mcp-resources"
    const val BuiltInPluginDiscovery = "builtin:plugin-discovery"
    const val BuiltInWeb = "builtin:web"
    const val BuiltInRemoteDsh = "builtin:remote-dsh"
    const val BuiltInSettingLibrary = "builtin:setting-library"
    const val BuiltInVariables = "builtin:variables"
    const val BuiltInCreator = "builtin:creator"
    const val BuiltInOther = "builtin:other"

    /**
     * Bucket for declarations a future Harness build ships that this app has no rule for yet.
     * It stays default-off so unknown tools never reach the paid upstream, but it is never
     * offered as a switch: its contents change with the Harness version, so a name and a
     * description that describe it honestly cannot be written.
     */
    val HiddenGroupIds: Set<String> = setOf(BuiltInOther)

    private const val McpPrefix = "mcp:"
}
