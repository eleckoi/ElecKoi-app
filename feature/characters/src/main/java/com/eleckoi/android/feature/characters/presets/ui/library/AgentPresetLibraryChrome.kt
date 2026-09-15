package com.eleckoi.android.feature.characters.presets.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StoryHeaderSearchAction
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.BubbleActionMenu
import com.eleckoi.android.foundation.design.components.MobileHeaderMenuAction
import com.eleckoi.android.foundation.design.components.MobileHeaderOverflowGlyph
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun PresetLibraryHeaderActions(
    appearance: AppearanceTheme,
    onSearch: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StoryHeaderSearchAction(appearance = appearance, onClick = onSearch)
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "更多预设操作"
                        role = Role.Button
                    }
                    .noRippleClickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                MobileHeaderOverflowGlyph(appearance = appearance)
            }
            BubbleActionMenu(
                expanded = menuOpen,
                actions = presetLibraryMenuActions(onCreate, onImport, onExport, onDelete),
                appearance = appearance,
                onDismiss = { menuOpen = false },
            )
        }
    }
}

internal fun presetLibraryMenuActions(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
): List<MobileHeaderMenuAction> = listOf(
    MobileHeaderMenuAction("新建预设", AppIconPaths.Plus, onClick = onCreate),
    MobileHeaderMenuAction(
        label = "导入预设",
        icon = AppIconPaths.Import,
        dividerBefore = true,
        onClick = onImport,
    ),
    MobileHeaderMenuAction("导出预设", AppIconPaths.Export, onClick = onExport),
    MobileHeaderMenuAction(
        label = "批量删除",
        icon = AppIconPaths.Trash,
        tint = ElecKoiDanger,
        dividerBefore = true,
        onClick = onDelete,
    ),
)
