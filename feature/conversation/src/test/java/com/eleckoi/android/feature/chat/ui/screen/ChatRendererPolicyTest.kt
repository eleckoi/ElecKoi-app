package com.eleckoi.android.feature.chat.ui.screen

import com.eleckoi.android.feature.preferences.ChatLayoutMode
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRendererPolicyTest {
    @Test
    fun `every ordinary chat layout uses the unified web transcript`() {
        ChatLayoutMode.entries.forEach { layout ->
            assertTrue("$layout must use the unified WebView transcript", layout.usesUnifiedWebTranscript())
        }
    }
}
