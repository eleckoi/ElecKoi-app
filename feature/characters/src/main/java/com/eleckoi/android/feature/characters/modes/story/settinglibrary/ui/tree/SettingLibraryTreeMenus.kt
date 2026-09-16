package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshProjectAddGlyph
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.themedListRowClickable

internal val SettingLibraryTreeBottomActionMenuOffset = 66.dp
internal val SettingLibraryTreeCreateMenuWidth = 120.dp
internal val SettingLibraryTreeMoreMenuWidth = 96.dp

/**
 * Pressing shrinks the whole action to 0.93 and springs back rather than washing a colour behind
 * it. These five sit on a raised bar, and a thing that is raised answers a finger by moving.
 */
@Composable
internal fun SettingTreeToolbarAction(
    text: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 1500f),
        label = "setting_tree_toolbar_press",
    )
    Column(
        modifier = modifier
            .height(58.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(13.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(top = 7.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (enabled) appearance.mobileText else appearance.mobileMuted.copy(alpha = 0.48f)
        val iconColor = if (danger) ElecKoiDanger.copy(alpha = if (enabled) 1f else 0.42f) else color
        StrokeSvgIcon(icon, iconColor, iconSize = 23.dp, strokeWidth = 1.75f)
        Text(
            text,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun SettingLibraryTreeCreateMenuPopup(
    appearance: AppearanceTheme,
    xOffset: Dp,
    createStaticLabel: String,
    onCreateFolder: () -> Unit,
    onCreateStatic: () -> Unit,
    onCreateCache: (() -> Unit)?,
    onCreateReference: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.BottomStart,
        offset = with(density) {
            IntOffset(xOffset.roundToPx(), -SettingLibraryTreeBottomActionMenuOffset.roundToPx())
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        SettingLibraryTreeCreateMenu(
            appearance = appearance,
            createStaticLabel = createStaticLabel,
            onCreateFolder = onCreateFolder,
            onCreateStatic = onCreateStatic,
            onCreateCache = onCreateCache,
            onCreateReference = onCreateReference,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun SettingLibraryTreeMoreMenuPopup(
    appearance: AppearanceTheme,
    xOffset: Dp,
    canCopy: Boolean,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.BottomStart,
        offset = with(density) {
            IntOffset(xOffset.roundToPx(), -SettingLibraryTreeBottomActionMenuOffset.roundToPx())
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(SettingLibraryTreeMoreMenuWidth)
                .zIndex(4f)
                .settingLibraryTreeMenuSurface(appearance)
                .padding(vertical = 3.dp),
        ) {
            if (canCopy) {
                SettingLibraryTreeMoreButton("复制", SettingLibraryIcons.Copy, appearance) {
                    onDismiss()
                    onCopy()
                }
            }
            SettingLibraryTreeMoreButton("重命名", SettingLibraryIcons.Rename, appearance) {
                onDismiss()
                onRename()
            }
        }
    }
}

@Composable
private fun SettingLibraryTreeCreateMenu(
    appearance: AppearanceTheme,
    createStaticLabel: String,
    onCreateFolder: () -> Unit,
    onCreateStatic: () -> Unit,
    onCreateCache: (() -> Unit)?,
    onCreateReference: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(SettingLibraryTreeCreateMenuWidth)
            .zIndex(4f)
            .settingLibraryTreeMenuSurface(appearance)
            .padding(vertical = 3.dp),
    ) {
        SettingLibraryTreeCreateFolderButton("文件夹", appearance) {
            onDismiss()
            onCreateFolder()
        }
        SettingLibraryTreeCreatePromptButton(createStaticLabel, appearance) {
            onDismiss()
            onCreateStatic()
        }
        if (onCreateCache != null) {
            SettingLibraryTreeCreateButton("缓存设定", SettingLibraryIcons.Cache, appearance) {
                onDismiss()
                onCreateCache()
            }
        }
        if (onCreateReference != null) {
            SettingLibraryTreeCreateButton("EJS引用设定", Icons.Rounded.Link, appearance) {
                onDismiss()
                onCreateReference()
            }
        }
    }
}

/** The popups hang off the raised bar, so they are lifted the same way it is. */
@Composable
private fun Modifier.settingLibraryTreeMenuSurface(appearance: AppearanceTheme): Modifier = this
    .shadow(
        elevation = 14.dp,
        shape = RoundedCornerShape(12.dp),
        ambientColor = appearance.mobileText.copy(alpha = 0.28f),
        spotColor = appearance.mobileText.copy(alpha = 0.28f),
    )
    .clip(RoundedCornerShape(12.dp))
    .background(appearance.mobileSurface)

@Composable
private fun SettingLibraryTreeMoreButton(
    text: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .themedListRowClickable(appearance = appearance, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeSvgIcon(icon, appearance.mobileText, iconSize = 17.dp, strokeWidth = 1.75f)
        Text(
            text,
            color = appearance.mobileText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 7.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingLibraryTreeCreateButton(
    text: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .themedListRowClickable(appearance = appearance, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(icon, appearance.mobileText, iconSize = 17.dp, strokeWidth = 1.7f)
        }
        Text(
            text,
            color = appearance.mobileText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingLibraryTreeCreateFolderButton(
    text: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .themedListRowClickable(appearance = appearance, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            DshProjectAddGlyph(tint = appearance.mobileText, iconSize = 17.dp)
        }
        Text(
            text,
            color = appearance.mobileText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingLibraryTreeCreatePromptButton(
    text: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .themedListRowClickable(appearance = appearance, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            SettingLibraryPromptGlyph(
                tint = appearance.mobileText,
                iconSize = 17.dp,
            )
        }
        Text(
            text,
            color = appearance.mobileText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingLibraryTreeCreateButton(
    text: String,
    icon: ImageVector,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .themedListRowClickable(appearance = appearance, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = appearance.mobileText, modifier = Modifier.size(17.dp))
        }
        Text(
            text,
            color = appearance.mobileText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
    }
}
