package com.eleckoi.android.feature.characters.presets.data.storage

import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan
import com.eleckoi.android.feature.characters.presets.model.AgentPresetToolConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentPresetMetadataCodecTest {
    @Test
    fun `current tool configuration round trips without losing local model selections`() {
        val configuration = AgentPresetToolConfiguration(
            includedGroupIds = listOf("builtin:variables", "builtin:auto-illustration"),
            enabledGroupIds = setOf("builtin:auto-illustration"),
            subagentModelConfigId = "child-config",
            subagentModel = "child-model",
            toolModelConfigIds = mapOf("builtin:auto-illustration" to "image-config"),
        )
        val plan = AgentPresetRoleplayPlan(listOf("读取设定", "输出正文"))
        val encoded = AgentPresetMetadataCodec.encodeToolConfiguration(configuration, plan)

        assertEquals(configuration, AgentPresetMetadataCodec.decodeToolConfiguration(encoded))
        assertEquals(plan, AgentPresetMetadataCodec.decodeRoleplayPlan(encoded))
    }

    @Test
    fun `missing tool configuration is rejected instead of silently using defaults`() {
        assertThrows(IllegalStateException::class.java) {
            AgentPresetMetadataCodec.decodeToolConfiguration("")
        }
    }

    @Test
    fun `old tool configuration version is rejected at runtime`() {
        assertThrows(IllegalStateException::class.java) {
            AgentPresetMetadataCodec.decodeToolConfiguration(
                """{"version":3,"includedGroupIds":[],"enabledGroupIds":[],"subagentModelSelection":{"configId":"","model":""},"roleplayPlan":{"steps":["输出正文"]},"toolModelConfigIds":{}}""",
            )
        }
    }

    @Test
    fun `unknown current database fields are rejected`() {
        assertThrows(IllegalStateException::class.java) {
            AgentPresetMetadataCodec.decodeToolConfiguration(
                """{"version":4,"includedGroupIds":[],"enabledGroupIds":[],"subagentModelSelection":{"configId":"","model":""},"roleplayPlan":{"steps":["输出正文"]},"toolModelConfigIds":{},"tool_policy":{}}""",
            )
        }
    }

    @Test
    fun `invalid roleplay plan is rejected instead of replaced`() {
        assertThrows(IllegalStateException::class.java) {
            AgentPresetMetadataCodec.decodeRoleplayPlan(
                """{"version":4,"includedGroupIds":[],"enabledGroupIds":[],"subagentModelSelection":{"configId":"","model":""},"roleplayPlan":{"steps":[]},"toolModelConfigIds":{}}""",
            )
        }
    }
}
