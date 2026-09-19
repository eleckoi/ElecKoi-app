package com.eleckoi.android.feature.studio.ui.assistant.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.feature.chat.ui.composer.shared.UnifiedChatComposerBody
import com.eleckoi.android.feature.chat.ui.composer.shared.UnifiedChatComposerSurface
import com.eleckoi.android.feature.chat.ui.composer.shared.unifiedChatComposerPlacement
import com.eleckoi.android.feature.chat.ui.composer.ChatComposerMenuSurface
import com.eleckoi.android.feature.conversation.composer.AgentPermissionModeControl
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.studio.ui.assistant.CreationContextWindowUsage
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AboveAnchorPopupPositionProvider
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.ContextWindowUsage
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon

@Composable
internal fun CreationComposer(
    value: String,
    inputImages: List<ChatUserImageAttachment>,
    isPreparingImages: Boolean,
    inputEnabled: Boolean,
    sendEnabled: Boolean,
    modelLabel: String,
    contextWindowUsage: CreationContextWindowUsage?,
    permissionMode: AgentPermissionMode,
    isRunning: Boolean,
    canRegenerate: Boolean,
    appearance: AppearanceTheme,
    onChange: (String) -> Unit,
    onAddImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onPermissionModeChange: (AgentPermissionMode) -> Unit,
    onModelSelector: () -> Unit,
    onRoleSelector: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenCommand: () -> Unit,
    onRegenerate: () -> Unit,
    onVoiceInput: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(inputEnabled) {
        if (!inputEnabled) menuOpen = false
    }

    UnifiedChatComposerSurface(
        appearance = appearance,
        modifier = Modifier
            .navigationBarsPadding()
            .unifiedChatComposerPlacement()
            .padding(bottom = 10.dp),
        menuContent = {
            CreationComposerMenu(
                expanded = menuOpen,
                permissionMode = permissionMode,
                permissionEnabled = inputEnabled && !isRunning,
                imageEnabled = inputEnabled && !isRunning && !isPreparingImages,
                canRegenerate = canRegenerate && !isRunning,
                appearance = appearance,
                onDismiss = { menuOpen = false },
                onRoleSelector = onRoleSelector,
                onAddImage = onAddImage,
                onOpenTools = onOpenTools,
                onOpenCommand = onOpenCommand,
                onPermissionModeChange = onPermissionModeChange,
                onRegenerate = onRegenerate,
            )
        },
    ) {
        UnifiedChatComposerBody(
            input = value,
            inputImages = inputImages,
            onInputChange = onChange,
            onRemoveImage = onRemoveImage,
            inputEnabled = inputEnabled,
            isSending = isRunning,
            stopEnabled = isRunning,
            submitEnabled = sendEnabled,
            modelLabel = modelLabel,
            modelSelectorEnabled = !isRunning,
            moreToolsOpen = menuOpen,
            appearance = appearance,
            contextWindowUsage = contextWindowUsage?.let { usage ->
                ContextWindowUsage(
                    latestTokens = usage.latestTokens,
                    totalTokens = usage.totalTokens,
                    modelContextWindow = usage.modelContextWindow,
                    systemTokens = usage.systemTokens,
                    toolsTokens = usage.toolsTokens,
                    messageTokens = usage.messageTokens,
                )
            },
            onSubmit = onSend,
            onStop = onStop,
            onVoiceInput = onVoiceInput,
            onOpenModelPicker = onModelSelector,
            onToggleMore = { menuOpen = !menuOpen },
            onDismissMore = { menuOpen = false },
        )
    }
}
@Composable
private fun CreationComposerMenu(
    expanded: Boolean,
    permissionMode: AgentPermissionMode,
    permissionEnabled: Boolean,
    imageEnabled: Boolean,
    canRegenerate: Boolean,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onRoleSelector: () -> Unit,
    onAddImage: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenCommand: () -> Unit,
    onPermissionModeChange: (AgentPermissionMode) -> Unit,
    onRegenerate: () -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        AboveAnchorPopupPositionProvider(
            windowMarginPx = with(density) { 4.dp.roundToPx() },
            anchorGapPx = 0,
            anchorInsetPx = 0,
        )
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ChatComposerMenuSurface(
            appearance = appearance,
            modifier = Modifier.width(154.dp),
        ) {
            Column {
                CreationMenuAction(
                    label = "选择角色",
                    paths = AppIconPaths.User,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    onClick = onRoleSelector,
                )
                CreationMenuAction(
                    label = "图片",
                    paths = AppIconPaths.Image,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    enabled = imageEnabled,
                    onClick = onAddImage,
                )
                CreationMenuAction(
                    label = "工具",
                    paths = AppIconPaths.Plug,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    onClick = onOpenTools,
                )
                CreationMenuAction(
                    label = "命令",
                    paths = AppIconPaths.Command,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    onClick = onOpenCommand,
                )
                HorizontalDivider(color = appearance.mobileLine)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AgentPermissionModeControl(
                        permissionMode = permissionMode,
                        appearance = appearance,
                        enabled = permissionEnabled,
                        onPermissionModeChange = onPermissionModeChange,
                        modifier = Modifier.fillMaxWidth(),
                        showLabel = true,
                        contentColor = appearance.mobileMuted,
                        fillWidth = true,
                        menuRowLayout = true,
                        onSelectionComplete = onDismiss,
                    )
                }
                HorizontalDivider(color = appearance.mobileLine)
                CreationMenuAction(
                    label = "重新生成",
                    paths = AppIconPaths.Refresh,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    enabled = canRegenerate,
                    onClick = onRegenerate,
                )
            }
        }
    }
}

@Composable
private fun CreationMenuAction(
    label: String,
    paths: List<String>,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) appearance.mobileMuted else appearance.mobileMuted.copy(alpha = 0.36f)
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = color,
                fontSize = 13.5.sp,
                maxLines = 1,
            )
        },
        leadingIcon = {
            StrokeSvgIcon(
                paths = paths,
                color = color,
                iconSize = 17.dp,
                strokeWidth = 1.85f,
            )
        },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(40.dp),
        onClick = {
            onDismiss()
            onClick()
        },
    )
}
