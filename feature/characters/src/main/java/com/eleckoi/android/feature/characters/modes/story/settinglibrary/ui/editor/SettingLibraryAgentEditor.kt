package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.ui.shared.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.dropShadow
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.MaxSettingLibrarySelectionHintCharacters
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentCatalogItem
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.renderSettingLibraryAgentCatalogPreviewTree
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.settingLibraryAgentCatalogPreview
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.foundation.design.components.DshFolderGlyph
import com.eleckoi.android.foundation.design.components.DshTreeDisclosureGlyph
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.foundation.design.components.dshTreeRowEntrance
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun AgentToolTriggerSettings(
    currentEntry: SettingLibraryEntry,
    entries: List<SettingLibraryEntry>,
    groups: List<SettingLibraryGroup>,
    showSelectionHint: Boolean,
    appearance: AppearanceTheme,
    scrollState: androidx.compose.foundation.ScrollState,
    imeBottomPx: Int,
    onEntryChange: ((SettingLibraryEntry) -> SettingLibraryEntry) -> Unit,
) {
    var directoryPreviewOpen by remember { mutableStateOf(false) }
    val catalog = remember(entries, groups) {
        settingLibraryAgentCatalogPreview(entries = entries, groups = groups)
    }
    val directoryTree = remember(catalog) {
        renderSettingLibraryAgentCatalogPreviewTree(catalog)
    }
    if (showSelectionHint) {
        PlainInput(
            label = "注释（AI 读目录时靠它判断）",
            value = currentEntry.agentSelectionHint,
            appearance = appearance,
            scrollState = scrollState,
            imeBottomPx = imeBottomPx,
            minHeight = 96,
            placeholder = "写给 AI 看的一句话",
            immersiveTitle = "条目注释",
            groupedStyle = true,
            onChange = { value ->
                onEntryChange {
                    it.copy(agentSelectionHint = value.take(MaxSettingLibrarySelectionHintCharacters))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = StoryEditorCardSpacing)) {
        AgentCatalogTree(
            items = catalog,
            currentEntryId = currentEntry.id,
            appearance = appearance,
            onOpenFullscreen = { directoryPreviewOpen = true },
        )
    }
    if (directoryPreviewOpen) {
        SettingLibraryDirectoryPreviewDialog(
            directoryTree = directoryTree,
            enabledCount = catalog.size,
            appearance = appearance,
            onDismiss = { directoryPreviewOpen = false },
        )
    }
}

/**
 * The catalogue the agent reads, drawn as a tree.
 *
 * It was a block of monospace with `├──` glyphs standing in for structure and every entry's note
 * quoted inline. Two characters of box-drawing is not enough indent to read as depth, and seven
 * quoted notes are seven paragraphs you did not come here for. Depth is now real indentation with
 * a rule down each group; a note becomes a tag saying one exists, and the full text stays in the
 * fullscreen view where you go when you actually want to read them.
 */
@Composable
private fun AgentCatalogTree(
    items: List<SettingLibraryAgentCatalogItem>,
    currentEntryId: String,
    appearance: AppearanceTheme,
    onOpenFullscreen: () -> Unit,
) {
    val editor = appearance.storyEditorPalette()
    val rows = remember(items) { agentCatalogRows(items) }
    var expandedFolderPaths by remember(items) { mutableStateOf(emptySet<String>()) }
    val visibleRows = rows.filter { row -> row.ancestorFolderPaths.all { it in expandedFolderPaths } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dropShadow(RoundedCornerShape(18.dp), appearance.mobileText.copy(alpha = 0.07f), blur = 3.dp, offsetY = 1.dp)
            .dropShadow(RoundedCornerShape(18.dp), appearance.mobileText.copy(alpha = 0.28f), blur = 12.dp, offsetY = 4.dp, spread = (-8).dp)
            .clip(RoundedCornerShape(18.dp))
            .background(editor.cardFace),
    ) {
        EditorFieldLabel(
            text = "AI 看到的目录",
            appearance = appearance,
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .noRippleClickable(onClick = onOpenFullscreen)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${items.size} 条已启用", modifier = Modifier.weight(1f), color = editor.meta, fontSize = 12.sp)
            Text("全屏浏览", color = appearance.mobileBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(editor.divider))
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
            if (rows.isEmpty()) {
                Text(
                    "还没有可供 Agent 读取的条目",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = editor.meta,
                    fontSize = 13.sp,
                )
            }
            visibleRows.forEach { row ->
                val current = row.id.isNotEmpty() && row.id == currentEntryId
                Row(
                    modifier = Modifier
                        .dshTreeRowEntrance(enabled = row.depth > 0)
                        .fillMaxWidth()
                        .height(34.dp)
                        .padding(start = 16.dp, end = 14.dp)
                        // One rule per level of depth, drawn where that level's folder icon sits,
                        // so a child is visibly hanging off its parent instead of merely starting
                        // a few pixels further in.
                        .drawBehind {
                            repeat(row.depth) { level ->
                                val x = (level * 20 + 8).dp.toPx()
                                drawRect(
                                    color = editor.divider,
                                    topLeft = Offset(x, 0f),
                                    size = androidx.compose.ui.geometry.Size(1.5.dp.toPx(), size.height),
                                )
                            }
                        }
                        .padding(start = (row.depth * 20).dp)
                        .then(
                            if (row.folder) {
                                Modifier.noRippleClickable {
                                    expandedFolderPaths = if (row.path in expandedFolderPaths) {
                                        expandedFolderPaths - row.path
                                    } else {
                                        expandedFolderPaths + row.path
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.folder) {
                        val folderExpanded = row.path in expandedFolderPaths
                        DshTreeDisclosureGlyph(
                            expanded = folderExpanded,
                            tint = editor.meta,
                            iconSize = 14.dp,
                        )
                    } else {
                        Box(modifier = Modifier.width(14.dp))
                    }
                    if (row.folder) {
                        DshFolderGlyph(
                            expanded = row.path in expandedFolderPaths,
                            tint = if (current) appearance.mobileBlue else editor.label,
                            iconSize = 16.dp,
                        )
                    } else {
                        SettingLibraryPromptGlyph(
                            tint = if (current) appearance.mobileBlue else editor.meta,
                            iconSize = 15.dp,
                        )
                    }
                    Text(
                        row.name,
                        modifier = Modifier.weight(1f).padding(start = 7.dp, end = 8.dp),
                        color = when {
                            current -> appearance.mobileBlue
                            row.folder -> editor.label
                            else -> editor.bodyText
                        },
                        fontSize = if (row.folder) 13.sp else 13.5.sp,
                        fontWeight = if (row.folder || current) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.required) {
                        Text(
                            "必读",
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(appearance.mobileBlue.copy(alpha = 0.11f))
                                .padding(horizontal = 7.dp, vertical = 1.5.dp),
                            color = appearance.mobileBlue,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    } else if (row.keywordPriority) {
                        Text(
                            "关键词",
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF9B6BD6).copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 1.5.dp),
                            color = Color(0xFF9B6BD6),
                            fontSize = 10.5.sp,
                        )
                    } else if (row.variableCondition) {
                        Text(
                            "变量条件",
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF3A9B84).copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 1.5.dp),
                            color = Color(0xFF3A9B84),
                            fontSize = 10.5.sp,
                        )
                    } else if (row.onDemand) {
                        Text(
                            "按需",
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(appearance.mobileText.copy(alpha = 0.045f))
                                .padding(horizontal = 7.dp, vertical = 1.5.dp),
                            color = editor.meta,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
        }
    }
}

private data class AgentCatalogRow(
    val name: String,
    val path: String,
    val depth: Int,
    val folder: Boolean,
    val ancestorFolderPaths: List<String> = emptyList(),
    val required: Boolean = false,
    val keywordPriority: Boolean = false,
    val variableCondition: Boolean = false,
    val onDemand: Boolean = false,
    val id: String = "",
)

/** Flattens `世界/樱川高中` logical breadcrumbs into group headers followed by their entries. */
private fun agentCatalogRows(items: List<SettingLibraryAgentCatalogItem>): List<AgentCatalogRow> {
    val rows = mutableListOf<AgentCatalogRow>()
    var openFolders = emptyList<String>()
    items.forEach { item ->
        val segments = item.path.split('/')
        val folders = segments.dropLast(1)
        folders.forEachIndexed { depth, name ->
            if (openFolders.getOrNull(depth) != name) {
                val folderPath = folders.take(depth + 1).joinToString("/")
                rows += AgentCatalogRow(
                    name = name,
                    path = folderPath,
                    depth = depth,
                    folder = true,
                    ancestorFolderPaths = folders.take(depth).mapIndexed { parentDepth, _ ->
                        folders.take(parentDepth + 1).joinToString("/")
                    },
                )
            }
        }
        openFolders = folders
        rows += AgentCatalogRow(
            name = segments.last(),
            path = item.path,
            depth = folders.size,
            folder = false,
            ancestorFolderPaths = folders.mapIndexed { depth, _ ->
                folders.take(depth + 1).joinToString("/")
            },
            required = item.readStrategy == SettingLibraryAgentReadStrategy.Required,
            keywordPriority = item.readStrategy == SettingLibraryAgentReadStrategy.Keyword,
            variableCondition = item.readStrategy == SettingLibraryAgentReadStrategy.VariableCondition,
            onDemand = item.readStrategy == SettingLibraryAgentReadStrategy.Normal,
            id = item.id,
        )
    }
    return rows
}

@Composable
private fun SettingLibraryDirectoryPreviewDialog(
    directoryTree: String,
    enabledCount: Int,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(color = appearance.storyEditorPalette().pageBg) {
            PinnedStatusScaffold(
                appearance = appearance,
                imeAware = false,
                backgroundColor = appearance.storyEditorPalette().pageBg,
            ) {
                EntryEditorTopBar(
                    title = "AI 所见目录",
                    appearance = appearance,
                    onBack = onDismiss,
                    menuVisible = false,
                    menuExpanded = false,
                    onMenuExpandedChange = {},
                    onDelete = {},
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(appearance.storyEditorPalette().pageBg)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text(
                        "$enabledCount 条已启用",
                        color = appearance.mobileBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 14.dp, bottom = 10.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(appearance.mobileSurface)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(
                            directoryTree,
                            color = appearance.mobileText,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
