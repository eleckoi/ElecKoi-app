package com.eleckoi.android.feature.chat.ui.layout

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.foundation.design.AppearanceTheme

@Immutable
internal data class ChatBubblePalette(
    val container: Color,
    val content: Color,
)

internal fun resolveChatBubblePalette(
    appearance: AppearanceTheme,
    layoutMode: ChatLayoutMode,
    user: Boolean,
): ChatBubblePalette {
    if (layoutMode == ChatLayoutMode.Agent || layoutMode == ChatLayoutMode.Social) {
        return ChatBubblePalette(
            container = when {
                appearance.isDark -> Color(0xFF333333)
                else -> Color(0xFFEDF3FE)
            },
            content = if (appearance.isDark) Color(0xFFF8F8F8) else Color(0xFF0F0F0F),
        )
    }
    return ChatBubblePalette(
        container = if (user) appearance.mobileChatUserBg else appearance.mobileChatMessageBg,
        content = if (user) appearance.mobileChatUserFg else appearance.mobileChatMessageFg,
    )
}
