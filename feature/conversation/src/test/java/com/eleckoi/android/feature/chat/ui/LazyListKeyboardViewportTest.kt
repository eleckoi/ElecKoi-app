package com.eleckoi.android.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyListKeyboardViewportTest {
    @Test
    fun `bottom owner follows only a shrinking viewport`() {
        assertEquals(420, keyboardViewportScrollDelta(960, 540, true, false, false))
        assertEquals(0, keyboardViewportScrollDelta(540, 960, true, false, false))
    }

    @Test
    fun `keyboard compensation never fights an active drag`() {
        assertEquals(0, keyboardViewportScrollDelta(960, 540, true, false, true))
        assertFalse(keyboardViewportOwnsLiveTail(false, true))
        assertTrue(keyboardViewportOwnsLiveTail(false, false))
    }
}
