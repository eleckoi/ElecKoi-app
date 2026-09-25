package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.ui.shared.*

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.dropShadow
import com.eleckoi.android.foundation.design.components.innerShadow
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.ElecKoiDanger

@Composable
internal fun AgentReadStrategySettings(
    selected: SettingLibraryAgentReadStrategy,
    appearance: AppearanceTheme,
    onSelect: (SettingLibraryAgentReadStrategy) -> Unit,
) {
    val options = SettingLibraryAgentReadStrategy.entries
    val activeIndex = options.indexOf(selected).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = StoryEditorCardSpacing)
            .clip(RoundedCornerShape(18.dp))
            .background(appearance.mobileSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        EditorFieldLabel("读取策略", appearance)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(appearance.storyEditorPalette().track)
                .innerShadow(
                    shape = RoundedCornerShape(14.dp),
                    color = appearance.mobileText.copy(alpha = 0.075f),
                    blur = 2.5.dp,
                    offsetY = 1.5.dp,
                ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val thumbWidth = maxWidth / options.size
                val thumbOffset by animateDpAsState(
                    targetValue = thumbWidth * activeIndex,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                    label = "agentReadStrategyThumb",
                )
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .width(thumbWidth)
                        .fillMaxHeight()
                        .dropShadow(RoundedCornerShape(11.dp), appearance.mobileText.copy(alpha = 0.07f), blur = 1.dp, offsetY = 1.dp)
                        .dropShadow(RoundedCornerShape(11.dp), appearance.mobileText.copy(alpha = 0.22f), blur = 8.dp, offsetY = 3.dp, spread = (-2).dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(appearance.mobileSurface),
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    options.forEach { option ->
                        val active = option == selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .noRippleClickable { onSelect(option) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when (option) {
                                    SettingLibraryAgentReadStrategy.Required -> "必读"
                                    SettingLibraryAgentReadStrategy.Keyword -> "关键词"
                                    SettingLibraryAgentReadStrategy.Normal -> "按需"
                                    SettingLibraryAgentReadStrategy.VariableCondition -> "变量条件"
                                },
                                color = if (active) appearance.mobileText else appearance.mobileMuted,
                                fontSize = 13.5.sp,
                                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (selected == SettingLibraryAgentReadStrategy.Required) {
            Text(
                "固定必读：正文自动进入缓存区，读取时返回带标题的编号。关键词、变量等命中后也会成为本轮必读，读取时仍返回正文。",
                color = appearance.mobileMuted,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}


@Composable
internal fun EntryInsertSettingsGroup(
    entry: SettingLibraryEntry,
    promptPositions: List<SettingLibraryPromptPosition>,
    insertRole: SettingLibraryInsertRole,
    appearance: AppearanceTheme,
    onOpenPosition: () -> Unit,
    onRoleChange: (SettingLibraryInsertRole) -> Unit,
) {
    var roleExpanded by remember { mutableStateOf(false) }
    val position = entry.position
    val systemInstructions = position == SettingLibraryPosition.Instructions && entry.promptPositionId.isBlank()
    Column(modifier = Modifier.fillMaxWidth().padding(top = StoryEditorCardSpacing)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(appearance.mobileSurface),
        ) {
            EditorFieldLabel(
                text = "上下文位置",
                appearance = appearance,
                modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp),
            )
            EntryInsertSettingRow(
                title = "插入位置",
                value = entry.insertionPositionLabel(promptPositions),
                appearance = appearance,
                onClick = onOpenPosition,
                leadingIcon = {
                    FilledSvgIcon(
                        paths = AppIconPaths.PromptMarkerThumbTack,
                        color = appearance.mobileText,
                        iconSize = 21.dp,
                        viewportSize = 512f,
                    )
                },
            )
            if (!systemInstructions) {
                Box(modifier = Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(appearance.mobileLine))
                Box(modifier = Modifier.fillMaxWidth()) {
                    EntryInsertSettingRow(
                        title = "消息身份",
                        value = insertRole.label,
                        appearance = appearance,
                        onClick = { roleExpanded = true },
                        leadingIcon = {
                            StrokeSvgIcon(
                                paths = when (insertRole) {
                                    SettingLibraryInsertRole.System, SettingLibraryInsertRole.User ->
                                        AppIconPaths.User
                                    SettingLibraryInsertRole.Assistant -> AppIconPaths.Bot
                                },
                                color = appearance.mobileText,
                                iconSize = 23.dp,
                                strokeWidth = 1.7f,
                            )
                        },
                    )
                    DropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false },
                        modifier = Modifier.background(appearance.mobileSurface),
                    ) {
                        listOf(SettingLibraryInsertRole.User, SettingLibraryInsertRole.Assistant).forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StrokeSvgIcon(
                                            if (option == SettingLibraryInsertRole.User) {
                                                AppIconPaths.User
                                            } else {
                                                AppIconPaths.Bot
                                            },
                                            appearance.mobileText,
                                            iconSize = 22.dp,
                                            strokeWidth = 1.7f,
                                        )
                                        Text(
                                            option.label,
                                            color = appearance.mobileText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    roleExpanded = false
                                    onRoleChange(option)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EntryPositionOrderGroup(
    entry: SettingLibraryEntry,
    entries: List<SettingLibraryEntry>,
    appearance: AppearanceTheme,
    scrollState: androidx.compose.foundation.ScrollState,
    imeBottomPx: Int,
    previewExpanded: Boolean,
    onTogglePreview: () -> Unit,
    onOrderConflict: (Int) -> Unit,
    onEntryChange: ((SettingLibraryEntry) -> SettingLibraryEntry) -> Unit,
) {
    entry.position ?: return
    NumberSection(
        label = "位置内部排序",
        value = entry.order,
        minValue = 1,
        expanded = previewExpanded,
        appearance = appearance,
        modifier = Modifier.padding(top = StoryEditorCardSpacing),
        scrollState = scrollState,
        imeBottomPx = imeBottomPx,
        onValueChange = { value ->
            onEntryChange { current ->
                val candidate = current.copy(order = value.coerceAtLeast(1))
                val nextEntries = entries.map { existing ->
                    if (existing.id == candidate.id) candidate else existing
                }
                if (candidate.hasOrderConflictIn(nextEntries)) {
                    onOrderConflict(candidate.order)
                    candidate.copy(enabled = false)
                } else {
                    candidate
                }
            }
        },
        onTogglePreview = onTogglePreview,
    ) {
        PositionOrderPreview(
            currentEntry = entry,
            entries = entries,
            appearance = appearance,
        )
    }
}

@Composable
private fun PositionOrderPreview(
    currentEntry: SettingLibraryEntry,
    entries: List<SettingLibraryEntry>,
    appearance: AppearanceTheme,
) {
    val position = currentEntry.position ?: return
    val scope = positionOrderScope(entries, position, currentEntry.promptPositionId)
        .filter { entry -> entry.triggerMode == currentEntry.triggerMode }
    Column(modifier = Modifier.fillMaxWidth()) {
        scope.forEach { item ->
            val current = item.id == currentEntry.id
            val duplicate = item.hasOrderConflictIn(scope)
            val accent = when {
                duplicate -> ElecKoiDanger
                current -> appearance.mobileBlue
                else -> appearance.mobileMuted
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (current) appearance.mobilePinnedBg else appearance.mobileSurface)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.positionOrderPreviewLabel(),
                    modifier = Modifier.width(48.dp),
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    item.title.trim().ifBlank { "待命名设定" },
                    modifier = Modifier.weight(1f),
                    color = appearance.mobileText,
                    fontSize = 14.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
                if (current || duplicate) {
                    Text(
                        if (duplicate) "重复" else "当前",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryInsertSettingRow(
    title: String,
    value: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon()
        Text(
            title,
            color = appearance.mobileText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        Text(value, color = appearance.mobileMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileSoft, modifier = Modifier.padding(start = 8.dp), iconSize = 17.dp, strokeWidth = 1.7f)
    }
}
