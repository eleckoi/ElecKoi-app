package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshFolderGlyph
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.themedListRowClickable
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrarySource
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry

@Composable
internal fun EntryPicker(
    source: SettingLibrarySource,
    version: SettingLibraryVersion?,
    checkedEntryIds: Set<String>,
    takenTitles: Set<String>,
    targetGroups: List<SettingLibraryGroup>,
    destinationGroupId: String,
    appearance: AppearanceTheme,
    onSelectVersion: (SettingLibraryVersion) -> Unit,
    onToggleEntries: (Set<String>, Boolean) -> Unit,
    onOpenDestinationPicker: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (version == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("这张角色卡还没有设定库", color = appearance.mobileMuted, fontSize = 15.sp)
        }
        return
    }
    val nodes = remember(version) {
        settingTreeNodes(
            groups = version.groups,
            entries = version.entries.filterNot { it.isFixedEntry() },
            expandedGroupIds = version.groups.mapTo(mutableSetOf()) { it.id },
            search = "",
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (source.versions.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    source.versions.forEach { item ->
                        VersionChip(
                            version = item,
                            selected = item.id == version.id,
                            appearance = appearance,
                            onClick = { onSelectVersion(item) },
                        )
                    }
                }
            }
            Text(
                if (checkedEntryIds.isEmpty()) "勾选要并入的设定" else "已选 ${checkedEntryIds.size} 条",
                modifier = Modifier.padding(start = 34.dp, top = 10.dp, bottom = 8.dp),
                color = appearance.mobileMuted,
                fontSize = 12.sp,
            )
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            ) {
                items(nodes, key = { it.id }) { node ->
                    PickerNodeRow(
                        node = node,
                        version = version,
                        checkedEntryIds = checkedEntryIds,
                        takenTitles = takenTitles,
                        appearance = appearance,
                        onToggleEntries = onToggleEntries,
                    )
                }
                item(key = "picker_bottom_space") { Spacer(modifier = Modifier.height(150.dp)) }
            }
        }

        MergeActionBar(
            targetGroups = targetGroups,
            destinationGroupId = destinationGroupId,
            selectedCount = checkedEntryIds.size,
            appearance = appearance,
            modifier = Modifier.align(Alignment.BottomCenter),
            onOpenDestinationPicker = onOpenDestinationPicker,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun VersionChip(
    version: SettingLibraryVersion,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val count = version.entries.count { !it.isFixedEntry() }
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) appearance.mobileBlue.copy(alpha = 0.10f) else appearance.mobileSurface,
            )
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            version.name.trim().ifBlank { "待命名" },
            color = if (selected) appearance.mobileBlue else appearance.mobileMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
        Text(
            count.toString(),
            modifier = Modifier.padding(start = 7.dp),
            color = if (selected) appearance.mobileBlue.copy(alpha = 0.7f) else appearance.mobileSoft,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PickerNodeRow(
    node: SettingTreeNode,
    version: SettingLibraryVersion,
    checkedEntryIds: Set<String>,
    takenTitles: Set<String>,
    appearance: AppearanceTheme,
    onToggleEntries: (Set<String>, Boolean) -> Unit,
) {
    val coveredIds = remember(node, version) {
        when (node) {
            is SettingTreeNode.Folder -> importableEntryIdsUnder(node.group.id, version.entries, version.groups)
            is SettingTreeNode.File -> setOf(node.entry.id)
        }
    }
    val selectedCount = coveredIds.count { it in checkedEntryIds }
    val state = when {
        coveredIds.isEmpty() -> CheckState.Empty
        selectedCount == 0 -> CheckState.Empty
        selectedCount == coveredIds.size -> CheckState.Checked
        else -> CheckState.Partial
    }
    val duplicate = node is SettingTreeNode.File && node.entry.title.trim() in takenTitles

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .themedListRowClickable(appearance = appearance, enabled = coveredIds.isNotEmpty()) {
                onToggleEntries(coveredIds, state != CheckState.Checked)
            }
            .padding(start = (10 + node.depth * 18).dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeSvgIcon(
            paths = when (state) {
                CheckState.Empty -> SettingLibraryIcons.CheckboxEmpty
                CheckState.Partial -> SettingLibraryIcons.CheckboxPartial
                CheckState.Checked -> SettingLibraryIcons.CheckboxChecked
            },
            color = if (state == CheckState.Empty) appearance.mobileSoft else appearance.mobileBlue,
            iconSize = 21.dp,
            strokeWidth = 1.85f,
        )
        Spacer(modifier = Modifier.width(11.dp))
        when (node) {
            is SettingTreeNode.Folder -> DshFolderGlyph(
                expanded = true,
                tint = appearance.mobileMuted.copy(alpha = 0.78f),
                iconSize = 20.dp,
            )
            is SettingTreeNode.File -> if (node.isEjsReference) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = appearance.mobileBlue.copy(alpha = 0.62f),
                    modifier = Modifier.size(19.dp),
                )
            } else {
                SettingLibraryPromptGlyph(
                    tint = appearance.mobileBlue.copy(alpha = 0.62f),
                    iconSize = 19.dp,
                )
            }
        }
        Text(
            node.title,
            modifier = Modifier.weight(1f).padding(start = 9.dp, end = 8.dp),
            color = appearance.mobileText,
            fontSize = 14.sp,
            fontWeight = if (node is SettingTreeNode.Folder) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            // The rename happens silently on import; saying so here is what keeps it from being a
            // surprise, and one chip says it without adding a second line to the row.
            duplicate -> Text(
                "重名",
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(appearance.mobileBlue.copy(alpha = 0.10f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                color = appearance.mobileBlue,
                fontSize = 11.sp,
            )
            node is SettingTreeNode.Folder -> Text(
                "$selectedCount/${coveredIds.size}",
                color = appearance.mobileSoft,
                fontSize = 12.sp,
            )
        }
    }
}

private enum class CheckState { Empty, Partial, Checked }

internal fun importableEntryIdsUnder(
    groupId: String,
    entries: List<SettingLibraryEntry>,
    groups: List<SettingLibraryGroup>,
): Set<String> {
    val ids = descendantGroupIds(groupId, groups) + groupId
    return entries.filter { it.groupId in ids && !it.isFixedEntry() }.map { it.id }.toSet()
}

@Composable
private fun MergeActionBar(
    targetGroups: List<SettingLibraryGroup>,
    destinationGroupId: String,
    selectedCount: Int,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onOpenDestinationPicker: () -> Unit,
    onConfirm: () -> Unit,
) {
    val destinationName = targetGroups.firstOrNull { it.id == destinationGroupId }
        ?.name?.trim()?.ifBlank { "未命名文件夹" }
        ?: "设定库根目录"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .shadow(
                elevation = 16.dp,
                shape = ManagerCardShape,
                ambientColor = appearance.mobileText.copy(alpha = 0.30f),
                spotColor = appearance.mobileText.copy(alpha = 0.30f),
            )
            .clip(ManagerCardShape)
            .background(appearance.mobileSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .themedListRowClickable(appearance = appearance, onClick = onOpenDestinationPicker)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshFolderGlyph(
                expanded = false,
                tint = appearance.mobileMuted.copy(alpha = 0.78f),
                iconSize = 19.dp,
            )
            Text(
                "并入到 $destinationName",
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                color = appearance.mobileText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileSoft, iconSize = 17.dp, strokeWidth = 1.7f)
        }
        val enabled = selectedCount > 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (enabled) appearance.mobileBlue else appearance.mobileMuted.copy(alpha = 0.18f))
                .noRippleClickable(enabled = enabled, onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (enabled) "并入 $selectedCount 条" else "还没有勾选设定",
                color = if (enabled) Color.White else appearance.mobileMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
