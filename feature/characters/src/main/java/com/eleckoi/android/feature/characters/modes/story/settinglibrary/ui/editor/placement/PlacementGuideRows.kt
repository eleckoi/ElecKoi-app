package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable

private val PlacementRailWidth = 36.dp
private val PlacementRailCenter = 18.dp
private val PlacementRowHeight = 42.dp

@Composable
internal fun InstructionsPlacementRow(
    selected: Boolean,
    selectable: Boolean = true,
    topConnected: Boolean,
    bottomConnected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .placementGuideLine(appearance, topConnected, bottomConnected)
            .semantics {
                contentDescription = if (selectable) {
                    "选择${SettingLibraryPosition.Instructions.label}"
                } else {
                    "固定位置${SettingLibraryPosition.Instructions.label}"
                }
                this.selected = selected
            }
            .noRippleClickable(enabled = selectable, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlacementRailMarker(selected = selected, fixed = !selectable, visible = true, appearance)
        Row(
            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                .background(appearance.mobileSurface)
                .border(
                    if (selected) 1.dp else 0.8.dp,
                    if (selected) appearance.mobileBlue.copy(alpha = 0.5f) else appearance.mobileLine,
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledSvgIcon(
                paths = AppIconPaths.PromptMarkerThumbTack,
                color = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                iconSize = 15.dp,
                viewportSize = 512f,
            )
            Text(
                SettingLibraryPosition.Instructions.label,
                color = appearance.mobileText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


@Composable
internal fun PlacementAnchorRow(
    position: SettingLibraryPosition,
    label: String = position.label,
    selected: Boolean,
    selectable: Boolean = true,
    topConnected: Boolean,
    bottomConnected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(if (selectable) PlacementRowHeight else 48.dp)
            .placementGuideLine(appearance, topConnected, bottomConnected)
            .semantics {
                contentDescription = if (selectable) "选择$label" else "固定位置$label"
                this.selected = selected
            }
            .noRippleClickable(enabled = selectable, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlacementRailMarker(selected, fixed = !selectable, visible = true, appearance)
        if (selectable) {
            Text(
                label,
                color = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.padding(start = 5.dp),
            )
        } else {
            Row(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                    .background(appearance.mobileSurface)
                    .border(0.8.dp, appearance.mobileLine, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledSvgIcon(
                    paths = AppIconPaths.PromptMarkerThumbTack,
                    color = appearance.mobileMuted,
                    iconSize = 15.dp,
                    viewportSize = 512f,
                )
                Text(label, color = appearance.mobileText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun FixedContextNode(
    node: FixedPlacementNode,
    topConnected: Boolean,
    bottomConnected: Boolean,
    appearance: AppearanceTheme,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .placementGuideLine(appearance, topConnected, bottomConnected)
            .semantics { contentDescription = "固定位置${node.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlacementRailMarker(selected = false, fixed = true, visible = true, appearance)
        Row(
            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                .background(appearance.mobileSurface)
                .border(0.8.dp, appearance.mobileLine, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (node == FixedPlacementNode.Cache) {
                StrokeSvgIcon(
                    paths = SettingLibraryIcons.Cache,
                    color = appearance.mobileMuted,
                    iconSize = 17.dp,
                    strokeWidth = 1.7f,
                )
            } else {
                FilledSvgIcon(
                    paths = AppIconPaths.PromptMarkerThumbTack,
                    color = appearance.mobileMuted,
                    iconSize = 15.dp,
                    viewportSize = 512f,
                )
            }
            Text(node.label, color = appearance.mobileText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun CustomPositionRow(
    position: SettingLibraryPromptPosition,
    selected: Boolean,
    sortMode: Boolean,
    isDragging: Boolean,
    topConnected: Boolean,
    bottomConnected: Boolean,
    appearance: AppearanceTheme,
    dragModifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .placementGuideLine(appearance, topConnected, bottomConnected, visible = !isDragging)
            .semantics {
                contentDescription = "选择${position.name.ifBlank { "未命名提示词位置" }}"
                this.selected = selected
            }
            .noRippleClickable(enabled = !sortMode, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlacementRailMarker(selected, fixed = false, visible = !isDragging, appearance)
        Row(
            Modifier.weight(1f).height(42.dp).clip(shape)
                .background(appearance.mobileSurface)
                .border(
                    if (selected) 1.dp else 0.8.dp,
                    if (selected) appearance.mobileBlue.copy(alpha = 0.5f) else appearance.mobileLine,
                    shape,
                )
                .then(dragModifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledSvgIcon(
                AppIconPaths.PromptMarkerThumbTack,
                if (selected) appearance.mobileBlue else appearance.mobileMuted,
                iconSize = 15.dp,
                viewportSize = 512f,
            )
            Text(
                position.name.ifBlank { "未命名提示词位置" },
                color = appearance.mobileText,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (sortMode) StrokeSvgIcon(
                paths = AppIconPaths.Sort,
                color = appearance.mobileMuted,
                iconSize = 16.dp,
                strokeWidth = 1.7f,
            )
        }
    }
}

@Composable
private fun PlacementRailMarker(
    selected: Boolean,
    fixed: Boolean,
    visible: Boolean,
    appearance: AppearanceTheme,
) {
    Box(Modifier.width(PlacementRailWidth), contentAlignment = Alignment.Center) {
        if (visible) Box(
            Modifier.size(if (selected) 20.dp else 16.dp).clip(CircleShape)
                .background(
                    when {
                        selected -> appearance.mobileBlue
                        fixed -> appearance.mobileSoft
                        else -> appearance.mobileBg
                    },
                )
                .border(
                    if (selected || fixed) 0.dp else 1.4.dp,
                    if (selected) appearance.mobileBlue else appearance.mobileSoft,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) StrokeSvgIcon(
                paths = AppIconPaths.Check,
                color = appearance.mobileAccentFg,
                iconSize = 12.dp,
                strokeWidth = 2.7f,
            )
        }
    }
}

private fun Modifier.placementGuideLine(
    appearance: AppearanceTheme,
    topConnected: Boolean,
    bottomConnected: Boolean,
    visible: Boolean = true,
): Modifier = drawBehind {
    if (!visible) return@drawBehind
    val x = PlacementRailCenter.toPx()
    val centerY = size.height / 2f
    drawLine(
        appearance.mobileLine,
        androidx.compose.ui.geometry.Offset(x, if (topConnected) 0f else centerY),
        androidx.compose.ui.geometry.Offset(x, if (bottomConnected) size.height else centerY),
        1.dp.toPx(),
    )
}
