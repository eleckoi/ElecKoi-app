package com.eleckoi.android.sdk.author

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorApiEventAccessTest {
    @Test
    fun `events read alone does not expose data events`() {
        assertFalse(
            AuthorApiEventAccess.canReceive(
                "messages.changed",
                setOf(AuthorApiPermission.EventsRead),
            ),
        )
    }

    @Test
    fun `event requires both subscription and matching data permission`() {
        val permissions = setOf(
            AuthorApiPermission.EventsRead,
            AuthorApiPermission.ChatRead,
        )

        assertTrue(AuthorApiEventAccess.canReceive("agent.output.delta", permissions))
        assertFalse(AuthorApiEventAccess.canReceive("messages.changed", permissions))
    }

    @Test
    fun `matching data permission without events read is denied`() {
        assertFalse(
            AuthorApiEventAccess.canReceive(
                "agent.output.delta",
                setOf(AuthorApiPermission.ChatRead),
            ),
        )
    }

    @Test
    fun `messages changed requires message read permission`() {
        assertTrue(
            AuthorApiEventAccess.canReceive(
                "messages.changed",
                setOf(AuthorApiPermission.EventsRead, AuthorApiPermission.MessagesRead),
            ),
        )
        assertFalse(
            AuthorApiEventAccess.canReceive(
                "messages.changed",
                setOf(AuthorApiPermission.EventsRead, AuthorApiPermission.ChatRead),
            ),
        )
    }

    @Test
    fun `unknown events fail closed`() {
        assertFalse(
            AuthorApiEventAccess.canReceive(
                "future.secret.changed",
                AuthorApiPermission.entries.toSet(),
            ),
        )
    }
}
