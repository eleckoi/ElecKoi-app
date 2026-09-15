package com.eleckoi.android.feature.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveChatSessionSelectionTest {
    @Test
    fun `bulk deletion clears all character pointers and is idempotent`() {
        val deleted = (0 until 5_000).map { "session-$it" }
        val selection = ActiveChatSessionSelection(
            lastSessionId = deleted.last(),
            sessionIdsByContext = deleted.associateBy { "character-$it:story" } + ("retained:agent" to "keep"),
        )
        val result = selection.forgetAll(deleted)
        assertEquals("", result.lastSessionId)
        assertEquals(mapOf("retained:agent" to "keep"), result.sessionIdsByContext)
        assertEquals(result, result.forgetAll(deleted))
    }

    @Test
    fun `each character remembers its latest selected chat`() {
        val selection = ActiveChatSessionSelection()
            .remember("character-a", "session-a-first")
            .remember("character-a", "session-a-latest")
            .remember("character-b", "session-b")

        assertEquals("session-a-latest", selection.sessionIdFor("character-a"))
        assertEquals("session-b", selection.sessionIdFor("character-b"))
        assertEquals("session-b", selection.lastSessionId)
    }

    @Test
    fun `forget removes a deleted session without disturbing other chat contexts`() {
        val selection = ActiveChatSessionSelection()
            .remember("character-a", "session-a")
            .remember("character-b", "session-b")
            .forget("session-b")

        assertEquals("session-a", selection.sessionIdFor("character-a"))
        assertEquals("", selection.sessionIdFor("character-b"))
        assertEquals("", selection.lastSessionId)
    }

    @Test
    fun `character lookup uses only the exact normalized key`() {
        val selection = ActiveChatSessionSelection(
            sessionIdsByContext = mapOf(
                "character-a" to "session-story",
                "character-a:agent" to "session-agent",
                "character-a:story" to "session-story",
            ),
        )

        assertEquals("session-story", selection.sessionIdFor("character-a"))
        assertEquals("session-agent", selection.sessionIdFor(" character-a:agent "))
        assertEquals("", selection.sessionIdFor("character-a:missing"))
    }
}
