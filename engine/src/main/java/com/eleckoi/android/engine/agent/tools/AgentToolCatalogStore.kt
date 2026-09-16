package com.eleckoi.android.engine.agent.tools

import kotlinx.serialization.json.JsonObject

/**
 * Process-local discovery catalog. User choices remain owned by the consuming surface.
 */
class AgentToolCatalogStore {
    private val lock = Any()
    private var observedGroups: List<AgentToolGroupSnapshot> = emptyList()

    fun filterRequest(enabledGroupIds: Set<String>, request: JsonObject): JsonObject {
        val enabled = enabledGroupIds.map(String::trim).filter(String::isNotBlank).toSet()
        val result = AgentToolRequestPolicy.filter(request) { groupId -> groupId in enabled }
        recordObservedGroups(result.observedGroups)
        return result.request
    }

    fun groups(enabledGroupIds: Set<String>): List<AgentToolGroupSnapshot> = synchronized(lock) {
        val enabled = enabledGroupIds.map(String::trim).filter(String::isNotBlank).toSet()
        val observedById = observedGroups.associateBy(AgentToolGroupSnapshot::id)
        val builtIns = AgentToolRequestPolicy.builtInGroups().map { fallback ->
            fallback.copy(
                members = resolveBuiltInMembers(fallback, observedById[fallback.id]),
                enabled = fallback.id in enabled,
            )
        }
        val additional = observedGroups
            .filterNot { observed -> builtIns.any { it.id == observed.id } }
            .map { it.copy(enabled = it.id in enabled) }
        (builtIns + additional)
            .filterNot { it.id in AgentToolRequestPolicy.HiddenGroupIds }
            .sortedWith(
                compareBy<AgentToolGroupSnapshot> { it.source.ordinal }
                    .thenBy { it.name.lowercase() },
            )
    }

    fun toolContextSnapshot(enabledGroupIds: Set<String>): AgentToolContextSnapshot =
        buildAgentToolContextSnapshot(
            allGroups = groups(enabledGroupIds),
            contextOrder = emptyList(),
        )

    fun recordMcpServer(
        serverId: String,
        displayName: String,
        description: String,
        tools: List<AgentToolMember>,
    ) {
        val normalizedId = serverId.trim()
        if (normalizedId.isBlank()) return
        recordObservedGroups(
            listOf(
                AgentToolGroupSnapshot(
                    id = AgentToolRequestPolicy.mcpGroupId(normalizedId),
                    name = if (normalizedId == "exa") "联网（Exa）" else displayName.ifBlank { normalizedId },
                    description = description.ifBlank {
                        if (normalizedId == "exa") "搜索网页并抓取完整页面内容"
                        else "来自 ${displayName.ifBlank { normalizedId }} MCP 服务器"
                    },
                    source = AgentToolGroupSource.Mcp,
                    sourceId = normalizedId,
                    members = tools.distinctBy(AgentToolMember::name),
                ),
            ),
        )
    }

    fun forgetMcpServer(serverId: String) = synchronized(lock) {
        val groupId = AgentToolRequestPolicy.mcpGroupId(serverId)
        observedGroups = observedGroups.filterNot { it.id == groupId }
    }

    private fun recordObservedGroups(groups: List<AgentToolGroupSnapshot>) {
        if (groups.isEmpty()) return
        synchronized(lock) {
            observedGroups = mergeObservedToolGroups(observedGroups, groups)
        }
    }
}
