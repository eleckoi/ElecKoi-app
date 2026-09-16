package com.eleckoi.android.feature.chat.ui.loading

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatWaitingReplyTest {
    @Test
    fun `duration uses the same second and minute formatting as PC`() {
        assertEquals("0秒", formatDeepDivingDuration(-1L))
        assertEquals("14秒", formatDeepDivingDuration(14_999L))
        assertEquals("15秒", formatDeepDivingDuration(15_000L))
        assertEquals("1分05秒", formatDeepDivingDuration(65_999L))
    }
}
