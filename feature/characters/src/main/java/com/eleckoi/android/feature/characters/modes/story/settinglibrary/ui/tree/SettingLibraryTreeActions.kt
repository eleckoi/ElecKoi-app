package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshFolderGlyph
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import androidx.compose.runtime.getValue

private val BottomToolbarOuterPadding = 14.dp
private val BottomToolbarInnerPadding = 8.dp
private val BottomToolbarItemSpacing = 2.dp
private val BottomToolbarHeight = 70.dp
@Composable
internal fun SettingTreeBottomPanel(
    hasSelection: Boolean,
    canEdit: Boolean,
    canCopy: Boolean,
    canDelete: Boolean,
    hasClipboard: Boolean,
    createMenuOpen: Boolean,
    appearance: AppearanceTheme,
    createStaticLabel: String = "设定",
    modifier: Modifier = Modifier,
    onCreate: () -> Unit,
    onDismissCreateMenu: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateStatic: () -> Unit,
    onEdit: () -> Unit,
    onCopyOrPaste: () -> Unit,
    onCutOrCancel: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCreateReference: (() -> Unit)? = null,
) {
    var moreMenuOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BottomToolbarOuterPadding, vertical = 8.dp),
    ) {
        val createMenuX = 0.dp
        val moreMenuX = maxWidth - SettingLibraryTreeMoreMenuWidth

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomToolbarHeight)
                // Lifted, not outlined. A hairline around a bar that already floats over the list
                // was drawing its edge twice; the shadow alone says the same thing and says it the
                // way the rest of the app says it.
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = appearance.mobileText.copy(alpha = 0.30f),
                    spotColor = appearance.mobileText.copy(alpha = 0.30f),
                )
                .clip(RoundedCornerShape(18.dp))
                .background(appearance.mobileSurface)
                .padding(horizontal = BottomToolbarInnerPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(BottomToolbarItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingTreeToolbarAction(
                text = "新建",
                icon = SettingLibraryIcons.Plus,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                enabled = true,
                onClick = onCreate,
            )
            SettingTreeToolbarAction(
                text = if (hasClipboard) "粘贴" else "编辑",
                icon = if (hasClipboard) SettingLibraryIcons.Paste else AppIconPaths.EditSquare,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                enabled = hasClipboard || canEdit,
                onClick = {
                    if (hasClipboard) onCopyOrPaste() else onEdit()
                },
            )
            SettingTreeToolbarAction(
                text = if (hasClipboard) "取消" else "移动",
                icon = if (hasClipboard) SettingLibraryIcons.Cancel else SettingLibraryIcons.Move,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                enabled = hasClipboard || canDelete,
                onClick = onCutOrCancel,
            )
            SettingTreeToolbarAction(
                text = "删除",
                icon = SettingLibraryIcons.Trash,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                enabled = canDelete,
                danger = true,
                onClick = onDelete,
            )
            SettingTreeToolbarAction(
                text = "更多",
                icon = SettingLibraryIcons.More,
                appearance = appearance,
                modifier = Modifier.weight(1f),
                enabled = hasSelection && !hasClipboard,
                onClick = { moreMenuOpen = true },
            )
        }
        if (createMenuOpen) {
            SettingLibraryTreeCreateMenuPopup(
                appearance = appearance,
                xOffset = createMenuX,
                createStaticLabel = createStaticLabel,
                onCreateFolder = onCreateFolder,
                onCreateStatic = onCreateStatic,
                onCreateReference = onCreateReference,
                onDismiss = onDismissCreateMenu,
            )
        }
        if (moreMenuOpen) {
            SettingLibraryTreeMoreMenuPopup(
                appearance = appearance,
                xOffset = moreMenuX,
                canCopy = canCopy,
                onCopy = onCopyOrPaste,
                onRename = onRename,
                onDismiss = { moreMenuOpen = false },
            )
        }
    }
}

@Composable
internal fun EmptySettingRootGuide(
    appearance: AppearanceTheme,
    onCreateRoot: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DshFolderGlyph(
                expanded = false,
                tint = appearance.mobileMuted.copy(alpha = 0.62f),
                iconSize = 38.dp,
            )
            Text(
                "还没有文件夹",
                color = appearance.mobileText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "从零创建第一个文件夹",
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appearance.mobileBlue)
                    .noRippleClickable(onClick = onCreateRoot)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StrokeSvgIcon(AppIconPaths.Plus, appearance.mobileSurface, iconSize = 16.dp, strokeWidth = 1.8f)
                Text(
                    "新建文件夹",
                    color = appearance.mobileSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
