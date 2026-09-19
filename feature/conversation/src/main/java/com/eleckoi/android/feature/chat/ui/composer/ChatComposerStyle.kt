package com.eleckoi.android.feature.chat.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.components.dropShadow

internal data class ChatComposerPalette(
    val container: Color,
    val border: Color,
    val content: Color,
    val placeholder: Color,
    val secondaryContainer: Color,
    val sendContainer: Color,
)

internal fun chatComposerPalette(isDark: Boolean): ChatComposerPalette = if (isDark) {
    ChatComposerPalette(
        container = Color(0xFF242424),
        border = Color(0xFF333333),
        content = Color(0xFFF8F8F8),
        placeholder = Color(0xFF7D7F85),
        secondaryContainer = Color(0xFF3C3C3D),
        sendContainer = Color(0xFF507BF2),
    )
} else {
    ChatComposerPalette(
        container = Color.White,
        border = Color(0xFFF0F0F0),
        content = Color(0xFF0F0F0F),
        placeholder = Color(0xFFB9BABB),
        secondaryContainer = Color(0xFFF5F5F5),
        sendContainer = Color(0xFF426EFE),
    )
}

internal fun Modifier.chatComposerSurface(palette: ChatComposerPalette): Modifier {
    val shape = RoundedCornerShape(ChatComposerCornerRadius)
    return this
        .dropShadow(
            shape = shape,
            color = Color.Black.copy(alpha = 0.0352f),
            blur = 17.6.dp,
            offsetY = 10.dp,
            spread = 6.dp,
        )
        .dropShadow(
            shape = shape,
            color = Color.Black.copy(alpha = 0.0176f),
            blur = 8.8.dp,
            offsetX = 4.dp,
            offsetY = 4.dp,
            spread = (-6).dp,
        )
        .dropShadow(
            shape = shape,
            color = Color.Black.copy(alpha = 0.0176f),
            blur = 7.04.dp,
            offsetX = (-4).dp,
            offsetY = 4.dp,
        )
        .background(palette.container, shape)
        .border(1.dp, palette.border, shape)
        .clip(shape)
}

/** Flat menu surface shared by the DeepSeek-style chat composers. */
@Composable
fun ChatComposerMenuSurface(
    appearance: com.eleckoi.android.foundation.design.AppearanceTheme,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = chatComposerPalette(appearance.isDark)
    val shape = RoundedCornerShape(ChatComposerMenuCornerRadius)
    Box(
        modifier = modifier
            .dropShadow(
                shape = shape,
                color = Color.Black.copy(alpha = 0.08f),
                blur = 16.dp,
                offsetY = 4.dp,
            )
            .background(palette.container, shape)
            .border(1.dp, palette.border, shape)
            .clip(shape),
        content = content,
    )
}

internal val ChatComposerCornerRadius = 28.dp
private val ChatComposerMenuCornerRadius = 16.dp
