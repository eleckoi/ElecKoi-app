package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryContentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLibraryEjsChangeTrackerTest {
    @Test
    fun `threshold changes notify only newly visible or previously read changed EJS settings`() {
        val tracker = SettingLibraryEjsReadTracker()
        val hidden = context()
        val stageThree = context(ejsEntry("episode", "第三幕"))

        assertEquals(
            listOf("剧情/第三幕"),
            tracker.changesAfterVariablePatch(hidden, stageThree).newlyAvailablePaths,
        )
        tracker.record(stageThree.readableEntries)

        repeat(3) {
            assertTrue(tracker.changesAfterVariablePatch(stageThree, stageThree).isEmpty)
        }

        val changed = context(ejsEntry("episode", "第三幕更新"))
        assertEquals(
            listOf("剧情/第三幕"),
            tracker.changesAfterVariablePatch(stageThree, changed).staleReadPaths,
        )
        tracker.record(changed.readableEntries)
        assertTrue(tracker.changesAfterVariablePatch(stageThree, changed).isEmpty)
        assertEquals(
            listOf("剧情/第三幕"),
            tracker.changesAfterVariablePatch(changed, hidden).unavailableReadPaths,
        )
    }

    private fun context(vararg entries: SettingLibraryAgentEntry) = SettingLibraryAgentTurnContext(
        automaticLibrary = SettingLibrary(characterId = "character"),
        readableEntries = entries.toList(),
        groups = emptyList(),
    )

    private fun ejsEntry(id: String, body: String) = SettingLibraryAgentEntry(
        id = id,
        title = "第三幕",
        groupPath = "剧情",
        path = "剧情/第三幕",
        content = body,
        contentMode = SettingLibraryContentMode.Ejs,
    )
}
