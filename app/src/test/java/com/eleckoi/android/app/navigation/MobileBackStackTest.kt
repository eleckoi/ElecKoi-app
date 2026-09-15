package com.eleckoi.android.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileBackStackTest {
    @Test
    fun `returning from character settings reuses the existing chat destination`() {
        val backStack = mutableListOf("root", "chat", "character-settings")

        backStack.replaceTopWith("chat")

        assertEquals(listOf("root", "chat"), backStack)
    }

    @Test
    fun `repeated chat settings loops never grow the back stack`() {
        val backStack = mutableListOf("root", "chat")

        repeat(20) {
            backStack += "character-settings"
            backStack.replaceTopWith("chat")
        }

        assertEquals(listOf("root", "chat"), backStack)
    }

    @Test
    fun `starting a chat from a root child replaces that child`() {
        val backStack = mutableListOf("root", "character-settings")

        backStack.replaceTopWith("chat")

        assertEquals(listOf("root", "chat"), backStack)
    }
}
