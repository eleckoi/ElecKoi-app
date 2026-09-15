package com.eleckoi.android.feature.characters.presets.ui.editor

import com.eleckoi.android.feature.characters.modes.story.ui.shared.*
import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun AgentPresetRoleplayPlanEditor(
    plan: AgentPresetRoleplayPlan,
    appearance: AppearanceTheme,
    onChange: (AgentPresetRoleplayPlan) -> Unit,
) {
    val editorPalette = appearance.storyEditorPalette()
    var items by remember(plan.steps) {
        mutableStateOf(plan.normalized().steps)
    }

    fun updateItems(next: List<String>) {
        val editableItems = next.ifEmpty { listOf("") }
        items = editableItems
        onChange(AgentPresetRoleplayPlan(editableItems).normalized())
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = StoryEditorCardSpacing)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(editorPalette.cardFace),
        ) {
            Text(
                "固定任务项",
                color = appearance.mobileText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 10.dp),
            )
            items.forEachIndexed { index, item ->
                val terminalItem = index == items.lastIndex
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp)
                            .height(1.dp)
                            .background(editorPalette.divider),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(appearance.mobileBlue.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (index + 1).toString(),
                                color = appearance.mobileBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                        AppInsetTextField(
                            value = item,
                            onValueChange = { value ->
                                val next = items.toMutableList()
                                next[index] = value.replace('\n', ' ').take(2_000)
                                updateItems(next)
                            },
                            appearance = appearance,
                            singleLine = false,
                            placeholder = "填写一项任务",
                            textStyle = TextStyle(
                                color = editorPalette.bodyText,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .padding(start = 10.dp, end = 2.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                        )
                    }
                    if (!terminalItem) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 38.dp, top = 2.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlanItemAction(
                                icon = Icons.Rounded.KeyboardArrowUp,
                                description = "上移",
                                enabled = index > 0,
                                appearance = appearance,
                                onClick = {
                                    val next = items.toMutableList()
                                    val moved = next.removeAt(index)
                                    next.add(index - 1, moved)
                                    updateItems(next)
                                },
                            )
                            PlanItemAction(
                                icon = Icons.Rounded.KeyboardArrowDown,
                                description = "下移",
                                enabled = index < items.lastIndex - 1,
                                appearance = appearance,
                                onClick = {
                                    val next = items.toMutableList()
                                    val moved = next.removeAt(index)
                                    next.add(index + 1, moved)
                                    updateItems(next)
                                },
                            )
                            PlanItemAction(
                                icon = Icons.Rounded.DeleteOutline,
                                description = "删除",
                                enabled = true,
                                appearance = appearance,
                                onClick = {
                                    val next = items.toMutableList().also { it.removeAt(index) }
                                    updateItems(next)
                                },
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(editorPalette.divider),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .noRippleClickable {
                        if (items.size >= 20) return@noRippleClickable
                        val next = items.toMutableList().apply { add(lastIndex, "新任务") }
                        updateItems(next)
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = appearance.mobileBlue,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "添加任务项",
                    color = appearance.mobileBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PlanItemAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .noRippleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = appearance.mobileMuted.copy(alpha = if (enabled) 0.86f else 0.28f),
            modifier = Modifier.size(18.dp),
        )
    }
}
