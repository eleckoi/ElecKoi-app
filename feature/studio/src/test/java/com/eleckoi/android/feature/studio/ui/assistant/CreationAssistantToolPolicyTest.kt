package com.eleckoi.android.feature.studio.ui.assistant

import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSource
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationAssistantToolPolicyTest {
    @Test
    fun `creator assistant excludes only role conversation specific groups`() {
        assertEquals(
            setOf(
                AgentToolRequestPolicy.BuiltInVariables,
                AgentToolRequestPolicy.BuiltInSettingLibrary,
                AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
                AgentToolRequestPolicy.BuiltInAutoIllustration,
            ),
            CreationAssistantToolPolicy.excludedGroupIds,
        )

        assertEquals(
            setOf(AgentToolRequestPolicy.BuiltInCreator),
            CreationAssistantToolPolicy.defaultEnabledGroupIds,
        )
        assertTrue(
            CreationAssistantToolPolicy.defaultGroups
                .single { it.id == AgentToolRequestPolicy.BuiltInCreator }
                .enabled,
        )
        assertTrue(
            CreationAssistantToolPolicy.defaultGroups
                .filterNot { it.id == AgentToolRequestPolicy.BuiltInCreator }
                .none { it.enabled },
        )
    }

    @Test
    fun `creator assistant keeps dynamically discovered non role tools`() {
        val extension = AgentToolGroupSnapshot(
            id = "extension:example",
            name = "Example",
            description = "",
            source = AgentToolGroupSource.Extension,
        )
        val variables = extension.copy(id = AgentToolRequestPolicy.BuiltInVariables)

        assertEquals(
            listOf(extension),
            CreationAssistantToolPolicy.selectable(listOf(extension, variables)),
        )
    }
}
