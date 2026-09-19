package com.eleckoi.android.feature.chat.ui.composer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.conversation.R
import com.eleckoi.android.feature.chat.ui.composer.ChatPhosphorIcon
import com.eleckoi.android.feature.chat.ui.composer.ChatPhosphorIconPaths
import com.eleckoi.android.feature.chat.ui.composer.chatComposerPalette
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun ComposerChip(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = chatComposerPalette(appearance.isDark)
    Box(
        modifier = modifier
            .height(36.dp)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
internal fun ComposerCircleButton(
    path: String,
    contentDescription: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    bare: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = chatComposerPalette(appearance.isDark)
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    bare -> Color.Transparent
                    filled -> palette.content
                    else -> palette.secondaryContainer
                },
            )
            .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ChatPhosphorIcon(
            path = path,
            color = when {
                !enabled -> palette.placeholder.copy(alpha = 0.4f)
                filled && !bare -> palette.container
                else -> palette.content
            },
            size = if (bare) 17.dp else 16.dp,
        )
    }
}

@Composable
internal fun ComposerPrimaryActionButton(
    isSending: Boolean,
    stopEnabled: Boolean,
    hasText: Boolean,
    submitEnabled: Boolean,
    voiceInputEnabled: Boolean,
    appearance: AppearanceTheme,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onVoiceInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = chatComposerPalette(appearance.isDark)
    val action = composerPrimaryAction(isSending = isSending, hasContent = hasText)
    val enabled = when (action) {
        ComposerPrimaryAction.Stop -> stopEnabled
        ComposerPrimaryAction.Send -> hasText && submitEnabled
        ComposerPrimaryAction.Voice -> voiceInputEnabled
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .then(
                if (enabled) {
                    Modifier.noRippleClickable(
                        onClick = when (action) {
                            ComposerPrimaryAction.Stop -> onStop
                            ComposerPrimaryAction.Send -> onSubmit
                            ComposerPrimaryAction.Voice -> onVoiceInput
                        },
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = when (action) {
                    ComposerPrimaryAction.Stop -> "停止生成"
                    ComposerPrimaryAction.Send -> "发送"
                    ComposerPrimaryAction.Voice -> "语音输入"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (action) {
            ComposerPrimaryAction.Voice -> Icon(
                painter = painterResource(R.drawable.ic_chat_composer_voice),
                contentDescription = null,
                tint = palette.content,
                modifier = Modifier
                    .size(26.dp)
                    .alpha(if (enabled) 1f else 0.4f),
            )
            ComposerPrimaryAction.Send,
            ComposerPrimaryAction.Stop -> Box(
                modifier = Modifier
                    .size(30.dp)
                    .alpha(if (enabled) 1f else 0.4f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.sendContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (action == ComposerPrimaryAction.Send) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chat_composer_send),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    ChatPhosphorIcon(
                        path = ChatPhosphorIconPaths.Stop,
                        color = Color.White,
                        size = 12.dp,
                    )
                }
            }
        }
    }
}

internal enum class ComposerPrimaryAction { Voice, Send, Stop }

internal fun composerPrimaryAction(
    isSending: Boolean,
    hasContent: Boolean,
): ComposerPrimaryAction = when {
    isSending -> ComposerPrimaryAction.Stop
    hasContent -> ComposerPrimaryAction.Send
    else -> ComposerPrimaryAction.Voice
}
