package com.eleckoi.android.feature.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLayoutProfileDefaultsTest {
    @Test
    fun `each layout owns a distinct defaults profile`() {
        val social = ChatLayoutMode.Social.layoutDefaults
        val agent = ChatLayoutMode.Agent.layoutDefaults
        val roleplay = ChatLayoutMode.Roleplay.layoutDefaults

        assertNotSame(social, agent)
        assertNotSame(agent, roleplay)
        assertNotSame(social, roleplay)
    }

    @Test
    fun `profiles expose their own typography defaults`() {
        assertEquals(16f, ChatLayoutMode.Social.layoutDefaults.messageFontSize)
        assertEquals(16f, ChatLayoutMode.Agent.layoutDefaults.messageFontSize)
        assertEquals(25f / (16f * 1.4f), ChatLayoutMode.Agent.layoutDefaults.lineHeightMultiplier)
        assertEquals(14f, ChatLayoutMode.Roleplay.layoutDefaults.messageFontSize)
    }

    @Test
    fun `social defaults match compact messaging app geometry`() {
        val defaults = ChatLayoutMode.Social.layoutDefaults

        assertEquals(40f, defaults.avatarSize)
        assertEquals(ChatAvatarShape.RoundedSquare, defaults.avatarShape)
        assertEquals(6f, defaults.bubbleCornerRadius)
        assertEquals(0.12f, ChatAvatarShape.RoundedSquareCornerRatio)
        assertEquals(10f, defaults.horizontalPadding)
        assertEquals(16f, defaults.turnSpacing)
    }

    @Test
    fun `agent identity stays proportionate to its reading typography`() {
        val defaults = ChatLayoutMode.Agent.layoutDefaults

        assertEquals(40f, defaults.avatarSize)
        assertEquals(16f, defaults.nameFontSize)
        assertEquals(16f, defaults.messageFontSize)
    }

    @Test
    fun `roleplay owns its larger avatar and name defaults`() {
        val defaults = ChatLayoutMode.Roleplay.layoutDefaults

        assertEquals(55f, defaults.avatarSize)
        assertEquals(15f, defaults.nameFontSize)
    }

    @Test
    fun `only social defaults to a required bubble`() {
        assertTrue(ChatLayoutMode.Social.layoutDefaults.assistantBubbleEnabled)
        assertFalse(ChatLayoutMode.Agent.layoutDefaults.assistantBubbleEnabled)
        assertFalse(ChatLayoutMode.Roleplay.layoutDefaults.assistantBubbleEnabled)
    }

    @Test
    fun `only agent honors a stored bubble toggle`() {
        assertTrue(resolveAssistantBubbleEnabled(ChatLayoutMode.Social, false))
        assertFalse(resolveAssistantBubbleEnabled(ChatLayoutMode.Roleplay, true))
        assertTrue(resolveAssistantBubbleEnabled(ChatLayoutMode.Agent, true))
        assertFalse(resolveAssistantBubbleEnabled(ChatLayoutMode.Agent, false))
    }
}
