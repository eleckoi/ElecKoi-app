package com.eleckoi.android.feature.chat.ui.variables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.dshTreeRowEntrance
import com.eleckoi.android.foundation.design.components.noRippleClickable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Composable
internal fun VariableTreeRow(
    row: VariableTreeDisplayRow,
    appearance: AppearanceTheme,
    onOpen: () -> Unit,
) {
    val railColor = appearance.mobileMuted.copy(alpha = 0.82f)
    val interaction = if (row.container) {
        Modifier
            .semantics {
                contentDescription = if (row.opensFocusedSubtree) {
                    "打开变量 ${row.name}"
                } else if (row.expanded) {
                    "折叠变量 ${row.name}"
                } else {
                    "展开变量 ${row.name}"
                }
                stateDescription = when {
                    row.opensFocusedSubtree -> "打开下一级"
                    row.expanded -> "已展开"
                    else -> "已折叠"
                }
                role = Role.Button
            }
            .noRippleClickable(onClick = onOpen)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .dshTreeRowEntrance(enabled = row.depth > 0)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                val baseX = VariableViewerHorizontalPadding.toPx() + 14.dp.toPx()
                val centerY = size.height / 2f
                row.guides.forEachIndexed { level, guide ->
                    val x = baseX + level * TreeIndent.toPx()
                    val immediateGuide = level == row.guides.lastIndex
                    if (!guide.above && !guide.below && !immediateGuide) {
                        return@forEachIndexed
                    }
                    drawLine(railColor, Offset(x, 0f), Offset(x, centerY), stroke)
                    if (guide.below) {
                        drawLine(railColor, Offset(x, centerY), Offset(x, size.height), stroke)
                    }
                    if (immediateGuide) {
                        val rowStartX = (VariableViewerHorizontalPadding + TreeIndent * row.depth).toPx()
                        val branchEndX = if (row.container) {
                            val iconLeftX = rowStartX + ((TreeNodeSlot - TreeChevronSize) / 2).toPx()
                            val silhouetteInset = if (row.expanded && !row.opensFocusedSubtree) {
                                TreeChevronDownConnectionInset
                            } else {
                                TreeChevronRightConnectionInset
                            }
                            iconLeftX + silhouetteInset.toPx()
                        } else {
                            rowStartX + TreeNodeSlot.toPx() - TreeScalarBranchGap.toPx()
                        }
                        drawLine(
                            railColor,
                            Offset(x, centerY),
                            Offset(branchEndX, centerY),
                            stroke,
                        )
                    }
                }
                if (row.hasInlineChildren) {
                    val chevronCenterX = (
                        VariableViewerHorizontalPadding +
                            TreeIndent * row.depth +
                            TreeNodeSlot / 2
                        ).toPx()
                    drawLine(
                        railColor,
                        Offset(chevronCenterX, centerY + TreeParentRailStartOffset.toPx()),
                        Offset(chevronCenterX, size.height),
                        stroke,
                    )
                }
            }
            .then(interaction)
            .padding(
                start = VariableViewerHorizontalPadding + TreeIndent * row.depth,
                end = VariableViewerHorizontalPadding,
                top = 7.dp,
                bottom = 7.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(TreeNodeSlot),
            contentAlignment = Alignment.Center,
        ) {
            if (row.container) {
                VariableViewerChevron(
                    expanded = row.expanded && !row.opensFocusedSubtree,
                    color = appearance.mobileMuted,
                    iconSize = TreeChevronSize,
                )
            }
        }
        if (row.container) {
            Text(
                text = row.name,
                color = appearance.mobileText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            VariableScalarColumns(
                name = row.name,
                value = row.value,
                changed = row.changed,
                depth = row.depth,
                appearance = appearance,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VariableScalarColumns(
    name: String,
    value: JsonElement,
    changed: Boolean,
    depth: Int,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val depthOffset = TreeIndent * depth
        val globalColumnsWidth = maxWidth + depthOffset
        val keyColumnWidth = globalColumnsWidth * KeyColumnFraction - depthOffset
        val stackColumns = globalColumnsWidth < StackedValueBreakpoint ||
            keyColumnWidth < MinimumKeyColumnWidth ||
            LocalDensity.current.fontScale >= 1.3f
        if (stackColumns) {
            Column(modifier = Modifier.fillMaxWidth()) {
                VariableKey(name, appearance)
                VariableValue(
                    value = value,
                    changed = changed,
                    appearance = appearance,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                VariableKey(
                    name = name,
                    appearance = appearance,
                    modifier = Modifier.width(keyColumnWidth),
                )
                Spacer(modifier = Modifier.width(KeyValueGap))
                VariableValue(
                    value = value,
                    changed = changed,
                    appearance = appearance,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VariableKey(
    name: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        color = appearance.mobileText,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun VariableValue(
    value: JsonElement,
    changed: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    val displayValue = value.variableDisplayValue().orEmpty()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        if (changed) {
            Canvas(
                modifier = Modifier
                    .padding(top = 7.dp, end = 7.dp)
                    .size(6.dp),
            ) {
                drawCircle(color = appearance.mobileBlue)
            }
        }
        Text(
            text = displayValue,
            color = if (value === JsonNull) appearance.mobileMuted else appearance.mobileText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.then(
                if (changed) {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(appearance.mobileBlue.copy(alpha = 0.10f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                } else {
                    Modifier
                },
            ),
        )
    }
}

private const val KeyColumnFraction = 0.42f
private val TreeIndent = 16.dp
private val TreeNodeSlot = 28.dp
private val TreeChevronSize = 14.dp
private val TreeChevronDownConnectionInset = 4.7.dp
private val TreeChevronRightConnectionInset = 8.5.dp
private val TreeParentRailStartOffset = 8.dp
private val TreeScalarBranchGap = 4.dp
private val KeyValueGap = 12.dp
private val MinimumKeyColumnWidth = 80.dp
private val StackedValueBreakpoint = 220.dp
