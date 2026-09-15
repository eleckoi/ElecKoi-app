package com.eleckoi.android.feature.characters.presets.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.presets.model.AgentPresetLibraryGroup
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshSettingsGlyph
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.dropShadow
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun PresetGroupRail(
    groups: List<AgentPresetLibraryGroup>,
    presetCounts: Map<String, Int>,
    allPresetCount: Int,
    selectedGroupId: String,
    managementEnabled: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onSelect: (AgentPresetLibraryGroup) -> Unit,
    onManage: (AgentPresetLibraryGroup) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item(key = "group-rail:$AllPresetGroupId") {
            PresetGroupRailItem(
                group = AgentPresetLibraryGroup(
                    id = AllPresetGroupId,
                    name = "全部预设",
                    sortIndex = Int.MIN_VALUE,
                ),
                count = allPresetCount,
                selected = selectedGroupId == AllPresetGroupId,
                showManage = false,
                appearance = appearance,
                onClick = {
                    onSelect(
                        AgentPresetLibraryGroup(
                            id = AllPresetGroupId,
                            name = "全部预设",
                            sortIndex = Int.MIN_VALUE,
                        ),
                    )
                },
                onManage = {},
            )
        }
        items(groups, key = { "group-rail:${it.id}" }) { group ->
            PresetGroupRailItem(
                group = group,
                count = presetCounts[group.id] ?: 0,
                selected = group.id == selectedGroupId,
                showManage = managementEnabled && group.id == selectedGroupId && group.id.isNotBlank(),
                appearance = appearance,
                onClick = { onSelect(group) },
                onManage = { onManage(group) },
            )
        }
        if (managementEnabled) {
            item(key = "create-group") {
                val lineColor = appearance.mobileMuted.copy(alpha = 0.34f)
                val corner = 10.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = lineColor,
                                cornerRadius = CornerRadius(corner.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                                    ),
                                ),
                            )
                        }
                        .noRippleClickable(onClick = onCreate)
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    StrokeSvgIcon(
                        AppIconPaths.Plus,
                        appearance.mobileSoft,
                        iconSize = 15.dp,
                        strokeWidth = 1.7f,
                    )
                    Text(
                        "新建分组",
                        modifier = Modifier.padding(start = 6.dp),
                        color = appearance.mobileSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetGroupRailItem(
    group: AgentPresetLibraryGroup,
    count: Int,
    selected: Boolean,
    showManage: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onManage: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .then(
                if (selected) {
                    Modifier.dropShadow(
                        shape = shape,
                        color = appearance.mobileText.copy(alpha = 0.045f),
                        blur = 9.dp,
                        offsetY = 3.dp,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(if (selected) appearance.mobileSurface else Color.Transparent)
            .noRippleClickable(onClick = onClick)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 9.dp)) {
            Text(
                group.name,
                color = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                count.toString(),
                modifier = Modifier.padding(top = 3.dp),
                color = if (selected) appearance.mobileBlue.copy(alpha = 0.78f) else appearance.mobileSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (showManage) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics {
                        contentDescription = "管理分组 ${group.name}"
                        role = Role.Button
                    }
                    .noRippleClickable(onClick = onManage),
                contentAlignment = Alignment.Center,
            ) {
                DshSettingsGlyph(
                    tint = appearance.mobileText.copy(alpha = 0.68f),
                    iconSize = 15.dp,
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}
