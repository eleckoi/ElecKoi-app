package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryHistoryCompactionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLibraryUiSupportTest {
    @Test
    fun `fixed insertion order does not expose its internal sentinel`() {
        assertEquals("固定", settingLibraryHistoryCompactionEntry().positionOrderPreviewLabel())
    }

    @Test
    fun `normal insertion order displays its number`() {
        assertEquals("7", SettingLibraryEntry(order = 7).positionOrderPreviewLabel())
    }

    @Test
    fun `custom insertion displays its own name instead of the fixed anchor`() {
        val entry = SettingLibraryEntry(
            position = SettingLibraryPosition.InsertPoint2,
            promptPositionId = "hidden-tools",
        )

        assertEquals(
            "隐藏工具时间线",
            entry.insertionPositionLabel(
                listOf(
                    SettingLibraryPromptPosition(
                        id = "hidden-tools",
                        name = "隐藏工具时间线",
                        anchor = SettingLibraryPosition.InsertPoint2,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `fixed insertion displays the fixed slot`() {
        val entry = SettingLibraryEntry(position = SettingLibraryPosition.InsertPoint3)

        assertEquals("设定插入点 3", entry.insertionPositionLabel(emptyList()))
    }

    @Test
    fun `agent tool entry does not require an insertion position`() {
        val entry = SettingLibraryEntry(
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            position = null,
        )

        assertTrue(entry.hasRequiredActivationFields())
    }

    @Test
    fun `agent tool entry ignores matching persistent insertion order`() {
        val agentEntry = entry(
            id = "agent",
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            enabled = false,
        )
        val persistentEntry = entry(
            id = "persistent",
            triggerMode = SettingLibraryTriggerMode.Always,
            enabled = true,
        )

        assertFalse(agentEntry.hasOrderConflictIn(listOf(agentEntry, persistentEntry)))
    }

    @Test
    fun `persistent entry conflicts only with another enabled persistent entry`() {
        val target = entry(
            id = "target",
            triggerMode = SettingLibraryTriggerMode.Always,
            enabled = false,
        )
        val enabledPersistent = entry(
            id = "enabled-persistent",
            triggerMode = SettingLibraryTriggerMode.Always,
            enabled = true,
        )
        val disabledPersistent = entry(
            id = "disabled-persistent",
            triggerMode = SettingLibraryTriggerMode.Always,
            enabled = false,
        )
        val enabledAgent = entry(
            id = "enabled-agent",
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            enabled = true,
        )

        assertTrue(target.hasOrderConflictIn(listOf(target, enabledPersistent)))
        assertFalse(target.hasOrderConflictIn(listOf(target, disabledPersistent)))
        assertFalse(target.hasOrderConflictIn(listOf(target, enabledAgent)))
    }

    private fun entry(
        id: String,
        triggerMode: SettingLibraryTriggerMode,
        enabled: Boolean,
    ) = SettingLibraryEntry(
        id = id,
        triggerMode = triggerMode,
        enabled = enabled,
        position = SettingLibraryPosition.InsertPoint1,
        order = 7,
    )
}
