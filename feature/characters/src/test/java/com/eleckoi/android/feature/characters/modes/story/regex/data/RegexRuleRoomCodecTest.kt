package com.eleckoi.android.feature.characters.modes.story.regex.data

import com.eleckoi.android.feature.characters.modes.story.regex.model.*
import org.junit.Assert.*
import org.junit.Test

class RegexRuleRoomCodecTest {
    @Test fun `rich replacements and all processing flags survive repeated storage projection`() {
        val source = RegexRule(id = "rule", name = "显示", pattern = "(?s)原文(.*)",
            replacement = "<style>.card { color: red }</style><script>const text = '$1';</script>".repeat(1000),
            targets = RegexRuleTarget.entries.toSet(), enabled = false, displayOnly = true,
            promptOnly = false, runOnEdit = true, order = 17)
        var current = source
        repeat(100) { current = current.toRoomFields().toRule(current.id) }
        assertEquals(source, current)
    }

    @Test fun `enablement versions preserve selection without copying rule content or preset activation`() {
        val selection = RegexRuleVersion(id = "v", name = "组合",
            globalEnabledIds = setOf("global-a", "global-b"), characterEnabledIds = setOf("card-a-rule"))
        assertEquals(selection, selection.toRoomVersion(2).toVersion())
        assertTrue(selection.toRoomVersion(2).toVersion().promptPresetEnabledIds.isEmpty())
    }
}
