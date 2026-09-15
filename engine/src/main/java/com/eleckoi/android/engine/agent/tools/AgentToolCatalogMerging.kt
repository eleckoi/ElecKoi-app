package com.eleckoi.android.engine.agent.tools

/**
 * A non-empty built-in declaration is the product's current protocol and must not be expanded by
 * stale observations. Empty declarations are runtime-owned and use the latest Harness observation.
 */
internal fun resolveBuiltInMembers(
    fallback: AgentToolGroupSnapshot,
    observed: AgentToolGroupSnapshot?,
): List<AgentToolMember> = if (fallback.members.isNotEmpty()) {
    fallback.members
} else {
    observed?.members.orEmpty()
}

internal fun mergeObservedToolGroups(
    previous: List<AgentToolGroupSnapshot>,
    incoming: List<AgentToolGroupSnapshot>,
): List<AgentToolGroupSnapshot> {
    val merged = previous.associateBy(AgentToolGroupSnapshot::id).toMutableMap()
    incoming.forEach { current ->
        val older = merged[current.id]
        merged[current.id] = if (current.source == AgentToolGroupSource.BuiltIn) {
            current
        } else {
            current.copy(
                members = (current.members + older?.members.orEmpty())
                    .distinctBy(AgentToolMember::name),
            )
        }
    }
    return merged.values.sortedBy(AgentToolGroupSnapshot::id)
}
