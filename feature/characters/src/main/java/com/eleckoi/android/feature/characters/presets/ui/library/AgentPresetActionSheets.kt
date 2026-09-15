package com.eleckoi.android.feature.characters.presets.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.presets.model.AgentPresetLibraryGroup
import com.eleckoi.android.feature.characters.presets.model.AgentPresetSummary
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshFolderGlyph
import com.eleckoi.android.foundation.design.components.DshGeneralGlyph
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetGroupActionSheet(
    group: AgentPresetLibraryGroup,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = appearance.mobileSurface,
        contentColor = appearance.mobileText,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .background(appearance.mobileLine),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                group.name,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = appearance.mobileText,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "分组管理",
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                color = appearance.mobileMuted,
                fontSize = 12.sp,
            )
            PresetSheetAction(
                label = "重命名分组",
                appearance = appearance,
                onClick = onRename,
                icon = {
                    StrokeSvgIcon(
                        AppIconPaths.EditSquare,
                        appearance.mobileText,
                        iconSize = 19.dp,
                        strokeWidth = 1.7f,
                    )
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                thickness = 0.5.dp,
                color = appearance.mobileLine,
            )
            PresetSheetAction(
                label = "删除分组",
                appearance = appearance,
                danger = true,
                onClick = onDelete,
                icon = {
                    StrokeSvgIcon(
                        AppIconPaths.Trash,
                        ElecKoiDanger,
                        iconSize = 19.dp,
                        strokeWidth = 1.7f,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetActionSheet(
    preset: AgentPresetSummary,
    groups: List<AgentPresetLibraryGroup>,
    active: Boolean,
    canDelete: Boolean,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onSetActive: () -> Unit,
    onEditProfile: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choosingGroup by remember(preset.id) { mutableStateOf(false) }
    var selectedGroupId by remember(preset.id, preset.libraryGroupId) {
        mutableStateOf(preset.libraryGroupId)
    }
    BackHandler(enabled = choosingGroup) { choosingGroup = false }
    val moveTargets = remember(groups) { groups.filter { it.id.isNotBlank() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appearance.mobileSurface,
        contentColor = appearance.mobileText,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .background(appearance.mobileLine),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            if (choosingGroup) {
                PresetMoveGroupPicker(
                    presetName = preset.name,
                    groups = moveTargets,
                    currentGroupId = preset.libraryGroupId,
                    selectedGroupId = selectedGroupId,
                    appearance = appearance,
                    onSelect = { selectedGroupId = it },
                    onBack = { choosingGroup = false },
                    onConfirm = { onMove(selectedGroupId) },
                )
            } else {
                Text(
                    preset.name,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!active) {
                    PresetSheetAction(
                        label = "设为全局生效",
                        appearance = appearance,
                        onClick = onSetActive,
                        icon = {
                            DshGeneralGlyph(
                                tint = appearance.mobileText,
                                modifier = Modifier.size(19.dp),
                                iconSize = 19.dp,
                            )
                        },
                    )
                }
                PresetSheetAction(
                    label = "编辑简介",
                    appearance = appearance,
                    onClick = onEditProfile,
                    icon = {
                        StrokeSvgIcon(
                            AppIconPaths.User,
                            appearance.mobileText,
                            iconSize = 19.dp,
                            strokeWidth = 1.7f,
                        )
                    },
                )
                PresetSheetAction(
                    label = "重命名预设",
                    appearance = appearance,
                    onClick = onRename,
                    icon = {
                        StrokeSvgIcon(
                            AppIconPaths.EditSquare,
                            appearance.mobileText,
                            iconSize = 19.dp,
                            strokeWidth = 1.7f,
                        )
                    },
                )
                PresetSheetAction(
                    label = "复制预设",
                    appearance = appearance,
                    onClick = onDuplicate,
                    icon = {
                        StrokeSvgIcon(
                            AppIconPaths.Copy,
                            appearance.mobileText,
                            iconSize = 19.dp,
                            strokeWidth = 1.7f,
                        )
                    },
                )
                PresetSheetAction(
                    label = "移动到分组",
                    appearance = appearance,
                    onClick = { choosingGroup = true },
                    icon = {
                        StrokeSvgIcon(
                            AppIconPaths.Move,
                            appearance.mobileText,
                            iconSize = 19.dp,
                            strokeWidth = 1.7f,
                        )
                    },
                )
                if (canDelete) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = appearance.mobileLine,
                    )
                    PresetSheetAction(
                        label = "删除预设",
                        appearance = appearance,
                        danger = true,
                        onClick = onDelete,
                        icon = {
                            StrokeSvgIcon(
                                AppIconPaths.Trash,
                                ElecKoiDanger,
                                iconSize = 19.dp,
                                strokeWidth = 1.7f,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PresetMoveGroupPicker(
    presetName: String,
    groups: List<AgentPresetLibraryGroup>,
    currentGroupId: String,
    selectedGroupId: String,
    appearance: AppearanceTheme,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Text(
        "移动到分组",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        color = appearance.mobileText,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        presetName,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        color = appearance.mobileMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 8.dp),
    ) {
        items(groups, key = { "move-group:${it.id}" }) { group ->
            val selected = group.id == selectedGroupId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        if (selected) appearance.mobileBlue.copy(alpha = 0.08f) else Color.Transparent,
                    )
                    .noRippleClickable { onSelect(group.id) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    DshFolderGlyph(
                        expanded = false,
                        tint = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                        iconSize = 19.dp,
                    )
                }
                Text(
                    group.name,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    color = appearance.mobileText,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    StrokeSvgIcon(
                        AppIconPaths.Check,
                        appearance.mobileBlue,
                        iconSize = 18.dp,
                        strokeWidth = 1.8f,
                    )
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        thickness = 0.5.dp,
        color = appearance.mobileLine,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("返回", color = appearance.mobileMuted, fontWeight = FontWeight.Medium)
        }
        TextButton(
            enabled = selectedGroupId != currentGroupId,
            onClick = onConfirm,
        ) {
            Text(
                "确认移动",
                color = if (selectedGroupId != currentGroupId) {
                    appearance.mobileBlue
                } else {
                    appearance.mobileMuted.copy(alpha = 0.45f)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PresetSheetAction(
    label: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val color = if (danger) ElecKoiDanger else appearance.mobileText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            label,
            modifier = Modifier.padding(start = 12.dp),
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
