package com.eleckoi.android.engine.agent.tools

import com.eleckoi.android.engine.agent.api.AgentToolContextBlock
import com.eleckoi.android.engine.agent.api.AgentToolContextBlockIds

/** Pure projection from ordered capability switches to Harness prompt-context blocks. */
internal fun buildAgentToolContextSnapshot(
    allGroups: List<AgentToolGroupSnapshot>,
    contextOrder: List<String>,
): AgentToolContextSnapshot {
    val knownIds = allGroups.map(AgentToolGroupSnapshot::id)
    val orderedIds = (contextOrder.filter { it in knownIds } + knownIds).distinct()
    val orderIndex = orderedIds.withIndex().associate { (index, id) -> id to index }
    val orderedGroups = allGroups.sortedWith(
        compareBy<AgentToolGroupSnapshot> { orderIndex[it.id] ?: Int.MAX_VALUE }
            .thenBy { it.name.lowercase() },
    )
    val orderById = orderedGroups.withIndex().associate { (index, group) ->
        group.id to (index + 1) * ToolContextOrderStride
    }
    val enabledGroups = orderedGroups.filter(AgentToolGroupSnapshot::enabled)
    val workspaceOrder = orderById[AgentToolRequestPolicy.BuiltInWorkspace]
        ?: ToolContextFallbackOrder
    val collaborationOrder = orderById[AgentToolRequestPolicy.BuiltInCollaboration]
        ?: ToolContextFallbackOrder
    val extensionGroups = enabledGroups.filter { group ->
        group.source != AgentToolGroupSource.BuiltIn || group.id in ExtensionContextGroups
    }
    val extensionOrder = extensionGroups.minOfOrNull { group ->
        orderById[group.id] ?: ToolContextFallbackOrder
    } ?: ToolContextFallbackOrder
    val workspaceEnabled = enabledGroups.any { it.id == AgentToolRequestPolicy.BuiltInWorkspace }
    val collaborationEnabled = enabledGroups.any {
        it.id == AgentToolRequestPolicy.BuiltInCollaboration
    }
    return AgentToolContextSnapshot(
        groups = orderedGroups,
        blocks = listOf(
            AgentToolContextBlock(
                id = AgentToolContextBlockIds.Permissions,
                enabled = workspaceEnabled,
                order = workspaceOrder + 10,
            ),
            AgentToolContextBlock(
                id = AgentToolContextBlockIds.Skills,
                enabled = workspaceEnabled,
                order = workspaceOrder + 20,
            ),
            AgentToolContextBlock(
                id = AgentToolContextBlockIds.Extensions,
                enabled = extensionGroups.isNotEmpty(),
                order = extensionOrder + 10,
            ),
            AgentToolContextBlock(
                id = AgentToolContextBlockIds.Collaboration,
                enabled = collaborationEnabled,
                order = collaborationOrder + 10,
            ),
            AgentToolContextBlock(
                id = AgentToolContextBlockIds.Environment,
                enabled = workspaceEnabled,
                order = workspaceOrder + 30,
            ),
        ),
    )
}

private const val ToolContextOrderStride = 1_000
private const val ToolContextFallbackOrder = 1_000_000
private val ExtensionContextGroups = setOf(
    AgentToolRequestPolicy.BuiltInMcpResources,
    AgentToolRequestPolicy.BuiltInPluginDiscovery,
    AgentToolRequestPolicy.BuiltInWeb,
    AgentToolRequestPolicy.BuiltInOther,
)
