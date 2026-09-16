package com.eleckoi.android.feature.studio.ui.assistant

import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy

/**
 * AI 创作助手拥有独立工具集。它不继承角色预设，也不暴露角色对话专用能力。
 */
internal object CreationAssistantToolPolicy {
    val excludedGroupIds: Set<String> = setOf(
        AgentToolRequestPolicy.BuiltInVariables,
        AgentToolRequestPolicy.BuiltInSettingLibrary,
        AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
        AgentToolRequestPolicy.BuiltInAutoIllustration,
    )

    val defaultGroups: List<AgentToolGroupSnapshot>
        get() = selectable(AgentToolRequestPolicy.builtInGroups()).map { group ->
            group.copy(enabled = group.id in defaultEnabledGroupIds)
        }

    val defaultEnabledGroupIds: Set<String>
        get() = setOf(AgentToolRequestPolicy.BuiltInCreator)

    fun selectable(groups: List<AgentToolGroupSnapshot>): List<AgentToolGroupSnapshot> = groups
        .filterNot { it.id in excludedGroupIds || it.id in AgentToolRequestPolicy.HiddenGroupIds }
}
