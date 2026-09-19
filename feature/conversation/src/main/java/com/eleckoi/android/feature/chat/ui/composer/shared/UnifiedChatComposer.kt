package com.eleckoi.android.feature.chat.ui.composer.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.ui.blocks.image.UserInputImageGallery
import com.eleckoi.android.feature.chat.ui.composer.ChatPhosphorIconPaths
import com.eleckoi.android.feature.chat.ui.composer.chatComposerPalette
import com.eleckoi.android.feature.chat.ui.composer.chatComposerSurface
import com.eleckoi.android.feature.chat.ui.composer.components.ComposerChip
import com.eleckoi.android.feature.chat.ui.composer.components.ComposerCircleButton
import com.eleckoi.android.feature.chat.ui.composer.components.ComposerPrimaryActionButton
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.ContextWindowUsage
import com.eleckoi.android.foundation.design.components.ContextWindowUsageControl
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.detectKnownModelProviderId
import com.eleckoi.android.foundation.design.components.focusDismissInputRegion

/** The one visual shell used by every chat-style composer in the app. */
@Composable
fun UnifiedChatComposerSurface(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    menuContent: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = chatComposerPalette(appearance.isDark)
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusDismissInputRegion()
                    .chatComposerSurface(palette),
            ) {
                content()
            }
            menuContent?.invoke(this)
        }
    }
}

/** Applies the shared composer's screen placement while keeping placement owned by its caller. */
fun Modifier.unifiedChatComposerPlacement(): Modifier =
    fillMaxWidth().padding(horizontal = 12.dp)

// The shared input and action row. Screen-specific menu commands never alter these measurements.
@Composable
fun ColumnScope.UnifiedChatComposerBody(
    input: String,
    inputImages: List<ChatUserImageAttachment> = emptyList(),
    onInputChange: (String) -> Unit,
    inputEnabled: Boolean,
    isSending: Boolean,
    stopEnabled: Boolean,
    submitEnabled: Boolean,
    modelLabel: String,
    modelSelectorEnabled: Boolean,
    moreToolsOpen: Boolean,
    appearance: AppearanceTheme,
    contextWindowUsage: ContextWindowUsage?,
    onSubmit: () -> Unit,
    onRemoveImage: (String) -> Unit = {},
    onStop: () -> Unit,
    onVoiceInput: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onToggleMore: () -> Unit,
    onDismissMore: () -> Unit,
) {
    val hasContent = input.trim().isNotEmpty() || inputImages.isNotEmpty()
    val palette = chatComposerPalette(appearance.isDark)
    val composerInputTextStyle = TextStyle(
        color = palette.content,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    )
    if (inputImages.isNotEmpty()) {
        Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            UserInputImageGallery(
                images = inputImages,
                appearance = appearance,
                compact = true,
                onRemove = onRemoveImage,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
    BasicTextField(
        value = input,
        onValueChange = { value ->
            if (moreToolsOpen) onDismissMore()
            onInputChange(value)
        },
        enabled = inputEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 26.dp)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (inputImages.isEmpty()) 12.dp else 0.dp,
                bottom = 12.dp,
            )
            .pointerInput(moreToolsOpen, onDismissMore) {
                if (!moreToolsOpen) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.changedToDownIgnoreConsumed() }) {
                            onDismissMore()
                        }
                    }
                }
            },
        minLines = 1,
        maxLines = 5,
        cursorBrush = SolidColor(palette.sendContainer),
        textStyle = composerInputTextStyle,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = "发消息或按住说话",
                        style = composerInputTextStyle.copy(color = palette.placeholder),
                    )
                }
                innerTextField()
            }
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposerCircleButton(
            path = ChatPhosphorIconPaths.MenuBars,
            contentDescription = if (moreToolsOpen) "收起更多工具" else "更多工具",
            appearance = appearance,
            bare = true,
            enabled = inputEnabled,
            onClick = onToggleMore,
        )
        ComposerChip(
            appearance = appearance,
            onClick = if (modelSelectorEnabled) onOpenModelPicker else null,
            modifier = Modifier.widthIn(min = 104.dp, max = 128.dp),
        ) {
            ComposerModelLabel(
                modelLabel = modelLabel,
                appearance = appearance,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        ContextWindowUsageControl(
            usage = contextWindowUsage,
            appearance = appearance,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        ComposerPrimaryActionButton(
            isSending = isSending,
            stopEnabled = stopEnabled,
            hasText = hasContent,
            submitEnabled = submitEnabled,
            voiceInputEnabled = inputEnabled,
            appearance = appearance,
            onSubmit = onSubmit,
            onStop = onStop,
            onVoiceInput = onVoiceInput,
        )
    }
}

@Composable
private fun ComposerModelLabel(
    modelLabel: String,
    appearance: AppearanceTheme,
) {
    val palette = chatComposerPalette(appearance.isDark)
    val iconProviderId = remember(modelLabel) { detectKnownModelProviderId(modelLabel) }
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconProviderId != null) {
            ModelProviderIcon(
                providerId = iconProviderId,
                initials = iconProviderId.take(1).uppercase(),
                appearance = appearance,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = modelLabel.composerShortName(),
            color = palette.content,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun String.composerShortName(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return "选择模型"
    return if (trimmed.length <= ComposerModelNameLimit) {
        trimmed
    } else {
        trimmed.take(ComposerModelNameLimit) + "…"
    }
}

private const val ComposerModelNameLimit = 12
