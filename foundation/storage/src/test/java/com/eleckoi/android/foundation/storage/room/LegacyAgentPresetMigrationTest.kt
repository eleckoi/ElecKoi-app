package com.eleckoi.android.foundation.storage.room

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyAgentPresetMigrationTest {
    @Test
    fun `legacy plan is moved into current tool configuration without truncation`() {
        val steps = LegacyAgentPresetMigration.roleplayPlanSteps(
            """{"kind":"roleplay_plan","content":" 读取设定 \n\n输出正文 "}""",
        )
        val configuration = JSONObject(
            LegacyAgentPresetMigration.toolConfigurationWithRoleplayPlan(steps),
        )

        assertEquals(listOf("读取设定", "输出正文"), steps)
        assertEquals(
            listOf("读取设定", "输出正文"),
            configuration.getJSONObject("roleplayPlan").getJSONArray("steps").let { array ->
                List(array.length()) { index -> array.getString(index) }
            },
        )
        assertTrue(configuration.getJSONArray("includedGroupIds").toString().contains("builtin:variables"))
        assertTrue(configuration.has("toolModelConfigIds"))
    }

    @Test
    fun `obsolete preset rows are identified by stable id or old kind`() {
        assertTrue(LegacyAgentPresetMigration.isObsoleteEntry("fixed-roleplay-plan", "{}"))
        assertTrue(LegacyAgentPresetMigration.isObsoleteEntry("built-in-dsh-harness-identity", "{}"))
        assertTrue(
            LegacyAgentPresetMigration.isObsoleteEntry(
                "renamed-plan",
                """{"kind":"roleplay_plan","content":"输出正文"}""",
            ),
        )
        assertFalse(
            LegacyAgentPresetMigration.isObsoleteEntry(
                "author-instructions",
                """{"kind":"normal","content":"保留"}""",
            ),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `damaged legacy plan aborts migration instead of guessing`() {
        LegacyAgentPresetMigration.roleplayPlanSteps("""{"kind":"roleplay_plan","content":""}""")
    }
}
