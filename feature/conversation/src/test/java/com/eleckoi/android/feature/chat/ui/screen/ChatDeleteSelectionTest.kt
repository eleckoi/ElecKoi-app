package com.eleckoi.android.feature.chat.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDeleteSelectionTest {
    @Test
    fun selectedMessageAndEveryFollowingMessageFormOneSuffix() {
        val deleteFromIndex = 2

        assertFalse(isDeleteSuffixSelected(messageIndex = 1, deleteFromIndex = deleteFromIndex))
        assertTrue(isDeleteSuffixSelected(messageIndex = 2, deleteFromIndex = deleteFromIndex))
        assertTrue(isDeleteSuffixSelected(messageIndex = 3, deleteFromIndex = deleteFromIndex))
    }

    @Test
    fun noMessageIsSelectedBeforeADeleteStartIsChosen() {
        assertFalse(isDeleteSuffixSelected(messageIndex = 0, deleteFromIndex = -1))
    }
}
