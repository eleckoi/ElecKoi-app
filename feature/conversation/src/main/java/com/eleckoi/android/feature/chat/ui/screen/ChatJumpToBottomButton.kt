package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.dropShadow
import com.eleckoi.android.foundation.design.components.noRippleClickable

internal val ChatJumpToBottomButtonSize = 34.dp
val ChatJumpToBottomButtonGap = 8.dp
private val ChatJumpToBottomChevron = listOf(
    "M5.25 9.828L10.4736 15.0468C11.1096 15.6816 11.4276 15.9996 11.8128 16.0608" +
        "C11.9364 16.0812 12.0636 16.0812 12.1872 16.0608" +
        "C12.5724 15.9996 12.8904 15.6816 13.5264 15.0468L18.75 9.828",
)

internal data class ChatJumpToBottomColors(
    val container: Color,
    val border: Color,
    val content: Color,
)

/** The fixed light/dark tokens used by DeepSeek's GoToBottomButton. */
internal fun chatJumpToBottomColors(appearance: AppearanceTheme): ChatJumpToBottomColors =
    if (appearance.isDark) {
        ChatJumpToBottomColors(
            container = Color(0xFF292929),
            border = Color(0xFF3C3C3D),
            content = Color(0xFFF8F8F8),
        )
    } else {
        ChatJumpToBottomColors(
            container = Color.White,
            border = Color(0xFFEDEDED),
            content = Color(0xFF0F0F0F),
        )
    }

/** Shared jump control for every normally ordered conversation list. */
@Composable
fun ChatJumpToBottomButton(
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val colors = chatJumpToBottomColors(appearance)
    Box(
        modifier = Modifier
            .size(ChatJumpToBottomButtonSize)
            .dropShadow(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.10f),
                blur = 8.dp,
                offsetY = 2.dp,
            )
            .clip(CircleShape)
            .background(colors.container)
            .border(1.dp, colors.border, CircleShape)
            .noRippleClickable(onClick = onClick)
            .semantics { contentDescription = "回到最新消息" },
        contentAlignment = Alignment.Center,
    ) {
        StrokeSvgIcon(
            paths = ChatJumpToBottomChevron,
            color = colors.content,
            iconSize = 20.dp,
            strokeWidth = 1.8f,
        )
    }
}
