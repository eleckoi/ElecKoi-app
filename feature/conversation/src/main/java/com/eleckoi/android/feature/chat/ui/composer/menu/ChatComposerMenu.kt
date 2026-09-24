package com.eleckoi.android.feature.chat.ui.composer.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.feature.chat.ui.composer.ChatComposerMenuSurface
import com.eleckoi.android.feature.conversation.composer.AgentPermissionModeControl
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AboveAnchorPopupPositionProvider
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshIconPaths
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon

@Composable
internal fun ChatComposerMenu(
    expanded: Boolean,
    appearance: AppearanceTheme,
    permissionMode: AgentPermissionMode,
    permissionEnabled: Boolean,
    canRegenerateLatest: Boolean,
    canDeleteMessages: Boolean,
    canAttachImages: Boolean,
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onOpenHistory: () -> Unit,
    onPermissionModeChange: (AgentPermissionMode) -> Unit,
    onOpenVariableViewer: () -> Unit,
    onOpenDynamicSettings: (() -> Unit)?,
    onDeleteMessages: () -> Unit,
    onRegenerateLatest: () -> Unit,
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
                RoleplayMenuAction(
                    label = "图片",
                    paths = AppIconPaths.Image,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    enabled = canAttachImages,
                    onClick = onPickImages,
                )
                RoleplayMenuAction(
                    label = "聊天记录",
                    appearance = appearance,
                    onDismiss = onDismiss,
                    leadingContent = { color ->
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.width(17.dp).height(17.dp),
                        )
                    },
                    onClick = onOpenHistory,
                )
                HorizontalDivider(color = appearance.mobileLine)
                RoleplayMenuAction(
                    label = "变量查看器",
                    paths = DshIconPaths.Data,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    filled = true,
                    onClick = onOpenVariableViewer,
                )
                if (onOpenDynamicSettings != null) {
                    HorizontalDivider(color = appearance.mobileLine)
                    RoleplayMenuAction(
                        label = "查看动态设定",
                        paths = AppIconPaths.Eye,
                        appearance = appearance,
                        onDismiss = onDismiss,
                        onClick = onOpenDynamicSettings,
                    )
                }
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
                RoleplayMenuAction(
                    label = "删除消息",
                    paths = AppIconPaths.Trash,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    enabled = canDeleteMessages,
                    onClick = onDeleteMessages,
                )
                HorizontalDivider(color = appearance.mobileLine)
                RoleplayMenuAction(
                    label = "重新生成",
                    paths = AppIconPaths.Refresh,
                    appearance = appearance,
                    onDismiss = onDismiss,
                    enabled = canRegenerateLatest,
                    onClick = onRegenerateLatest,
                )
            }
        }
    }
}

@Composable
private fun RoleplayMenuAction(
    label: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    paths: List<String> = emptyList(),
    leadingContent: (@Composable (Color) -> Unit)? = null,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (enabled) appearance.mobileMuted else appearance.mobileMuted.copy(alpha = 0.36f)
    DropdownMenuItem(
        text = { Text(label, color = color, fontSize = 13.5.sp, maxLines = 1) },
        leadingIcon = {
            if (leadingContent != null) {
                leadingContent(color)
            } else if (filled) {
                FilledSvgIcon(
                    paths = paths,
                    color = color,
                    iconSize = 17.dp,
                    viewportSize = DshIconPaths.Viewport16,
                )
            } else {
                StrokeSvgIcon(
                    paths = paths,
                    color = color,
                    iconSize = 17.dp,
                    strokeWidth = 1.85f,
                )
            }
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
