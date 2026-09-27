package com.eleckoi.android.feature.settings.ui.personalization.chat

import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.preferences.UiPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDisplayDraftStateTest {
    @Test
    fun `callbacks captured before recomposition update the latest draft`() {
        val initial = ChatLayoutEditorState(ChatLayoutDraft(UiPreferences()), true)
        val original = initial.draft
        val changeAvatar: ChatLayoutDraft.() -> ChatLayoutDraft = {
            copy(avatarSize = original.avatarSize + 4f)
        }
        val changeName: ChatLayoutDraft.() -> ChatLayoutDraft = {
            copy(nameFontSize = original.nameFontSize + 2f)
        }
        val changeLineHeight: ChatLayoutDraft.() -> ChatLayoutDraft = {
            copy(lineHeight = original.lineHeight + 0.1f)
        }

        val edited = initial.updated(changeAvatar).updated(changeName).updated(changeLineHeight)

        assertEquals(original.avatarSize + 4f, edited.draft.avatarSize)
        assertEquals(original.nameFontSize + 2f, edited.draft.nameFontSize)
        assertEquals(original.lineHeight + 0.1f, edited.draft.lineHeight)
    }

    @Test
    fun `multiple controls remain in one draft until explicitly saved`() {
        val stored = ChatLayoutDraft(UiPreferences())
        val initial = ChatLayoutEditorState(stored, generationStatsEnabled = true)

        val edited = initial
            .edited(initial.draft.copy(fontSize = 18f))
            .edited(initial.draft.copy(fontSize = 18f, letterSpacing = 1.5f))
            .copy(generationStatsEnabled = false)

        assertTrue(edited.hasUnsavedChanges)
        assertEquals(18f, edited.draft.fontSize)
        assertEquals(1.5f, edited.draft.letterSpacing)
        assertEquals(stored, edited.baselines.getValue(ChatLayoutMode.Roleplay))
        assertTrue(edited.storedGenerationStatsEnabled)
        assertFalse(edited.generationStatsEnabled)

        // A partial storage emission must not replace either pending control value.
        val partial = stored.copy(fontSize = 18f)
        assertEquals(edited, edited.storedChanged(partial, true))
    }

    @Test
    fun `switching layouts keeps unsaved edits and loads the other profile`() {
        val roleplay = ChatLayoutDraft(UiPreferences())
        val agent = ChatLayoutDraft(UiPreferences(
            chatLayoutMode = ChatLayoutMode.Agent,
            chatMessageFontSize = 16f,
        ))
        val changed = ChatLayoutEditorState(roleplay, true)
            .edited(roleplay.copy(fontSize = 19f))
            .switched(ChatLayoutMode.Agent, agent)

        assertEquals(16f, changed.draft.fontSize)
        assertEquals(19f, changed.switched(ChatLayoutMode.Roleplay, roleplay).draft.fontSize)
        assertEquals(ChatLayoutMode.Roleplay, changed.storedMode)
        assertTrue(changed.hasUnsavedChanges)
    }

    @Test
    fun `reset is staged and reverting all values is clean`() {
        val stored = ChatLayoutDraft(UiPreferences())
        val initial = ChatLayoutEditorState(stored, true)
        val edited = initial.edited(stored.copy(fontSize = 19f))

        assertTrue(edited.hasUnsavedChanges)
        assertFalse(edited.resetCurrentLayout().hasUnsavedChanges)
    }
}
