package com.eleckoi.android.foundation.design.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomTabTest {
    @Test
    fun `mobile bar tabs stay fixed and ordered`() {
        assertEquals(
            listOf(
                BottomTab.Messages,
                BottomTab.Characters,
                BottomTab.Presets,
                BottomTab.Models,
            ),
            BottomTab.MobileBarTabs,
        )
    }

    @Test
    fun `all bottom pages map to root tabs`() {
        assertEquals(RootTab.Messages, BottomTab.Messages.rootTab())
        assertEquals(RootTab.Characters, BottomTab.Characters.rootTab())
        assertEquals(RootTab.Presets, BottomTab.Presets.rootTab())
        assertEquals(RootTab.Models, BottomTab.Models.rootTab())
    }
}
