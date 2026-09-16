package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPositionSide
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.DshTrashGlyph
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import java.time.Instant
import java.util.UUID
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


@Composable
internal fun SettingLibraryPositionPickerPage(
    currentEntryId: String,
    entries: List<SettingLibraryEntry>,
    promptPositions: List<SettingLibraryPromptPosition>,
    allowCustomPromptPositions: Boolean,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onEntryChange: ((SettingLibraryEntry) -> SettingLibraryEntry) -> Unit,
    onEntriesChange: (List<SettingLibraryEntry>) -> Unit,
    onPromptPositionsChange: (List<SettingLibraryPromptPosition>) -> Unit,
) {
    BackHandler(onBack = onBack)
    val entry = entries.firstOrNull { it.id == currentEntryId } ?: return
    var workingPositions by remember { mutableStateOf(promptPositions.normalizedPositions()) }
    var managedPositionId by remember(currentEntryId, allowCustomPromptPositions) {
        mutableStateOf(
            if (allowCustomPromptPositions) {
                entry.promptPositionId.ifBlank { promptPositions.firstOrNull()?.id.orEmpty() }
            } else {
                ""
            },
        )
    }
    var nameDialog by remember { mutableStateOf<PositionNameDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val rows = remember(workingPositions, allowCustomPromptPositions) {
        placementGuideRows(workingPositions, includeCustomPositions = allowCustomPromptPositions)
    }
    val settingPositionsSelectable = !allowCustomPromptPositions
    val managedPositionCanDelete = managedPositionId.isNotBlank()

    LaunchedEffect(promptPositions) {
        if (draggingId == null) workingPositions = promptPositions.normalizedPositions()
    }
    LaunchedEffect(promptPositions, entry.promptPositionId, allowCustomPromptPositions) {
        if (!allowCustomPromptPositions) return@LaunchedEffect
        if (managedPositionId !in promptPositions.map { it.id }) {
            managedPositionId = entry.promptPositionId.takeIf { id -> promptPositions.any { it.id == id } }
                ?: promptPositions.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(managedPositionCanDelete) {
        if (!managedPositionCanDelete) confirmDelete = false
    }

    val latestRows by rememberUpdatedState(rows)
    val latestWorkingPositions by rememberUpdatedState(workingPositions)
    val latestOnPromptPositionsChange by rememberUpdatedState(onPromptPositionsChange)
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val currentRows = latestRows
        val currentPositions = latestWorkingPositions
        val fromIndex = currentRows.indexOfFirst { it.key == from.key }
        val toIndex = currentRows.indexOfFirst { it.key == to.key }
        val moving = (currentRows.getOrNull(fromIndex) as? PlacementGuideRow.Custom)?.position
            ?: return@rememberReorderableLazyListState
        val target = currentRows.getOrNull(toIndex) ?: return@rememberReorderableLazyListState
        val next = movePromptPosition(currentPositions, moving.id, target, fromIndex < toIndex)
        if (next != currentPositions) {
            workingPositions = next
            latestOnPromptPositionsChange(next)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    PinnedStatusScaffold(appearance, imeAware = false, backgroundColor = appearance.mobileBg) {
        PlacementPageTopBar(appearance, onBack)
        if (allowCustomPromptPositions) {
            PlacementPromptToolbar(
                positions = workingPositions,
                selectedId = managedPositionId,
                sortMode = sortMode,
                appearance = appearance,
                onSelect = { managedPositionId = it },
                onCreate = { nameDialog = PositionNameDialog.Create },
                onRename = {
                    workingPositions.firstOrNull { it.id == managedPositionId }?.let {
                        nameDialog = PositionNameDialog.Rename(it)
                    }
                },
                canDelete = managedPositionCanDelete,
                onDelete = { if (managedPositionCanDelete) confirmDelete = true },
                onToggleSort = { sortMode = !sortMode },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(appearance.mobileLine))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 30.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                ReorderableItem(reorderableState, key = row.key) { isDragging ->
                    val topConnected = index > 0
                    val bottomConnected = index < rows.lastIndex
                    when (row) {
                        PlacementGuideRow.Instructions -> InstructionsPlacementRow(
                            selected = entry.position == SettingLibraryPosition.Instructions &&
                                entry.promptPositionId.isBlank(),
                            selectable = true,
                            topConnected = topConnected,
                            bottomConnected = bottomConnected,
                            appearance = appearance,
                            onClick = {
                                if (entry.position != SettingLibraryPosition.Instructions || entry.promptPositionId.isNotBlank()) {
                                    onEntriesChange(
                                        movePositionEntry(
                                            entries = entries,
                                            entryId = entry.id,
                                            targetPosition = SettingLibraryPosition.Instructions,
                                        ),
                                    )
                                }
                            },
                        )

                        is PlacementGuideRow.Slot -> PlacementAnchorRow(
                            position = row.slot.position,
                            label = row.slot.label,
                            selected = settingPositionsSelectable &&
                                entry.position == row.slot.position &&
                                entry.promptPositionId.isBlank(),
                            selectable = settingPositionsSelectable,
                            topConnected = topConnected,
                            bottomConnected = bottomConnected,
                            appearance = appearance,
                            onClick = {
                                if (
                                    entry.position != row.slot.position ||
                                    entry.promptPositionId.isNotBlank()
                                ) {
                                    onEntriesChange(
                                        movePositionEntry(
                                            entries = entries,
                                            entryId = entry.id,
                                            targetPosition = row.slot.position,
                                        ),
                                    )
                                }
                            },
                        )

                        is PlacementGuideRow.FixedNode -> FixedContextNode(
                            node = row.node,
                            topConnected = topConnected,
                            bottomConnected = bottomConnected,
                            appearance = appearance,
                        )

                        is PlacementGuideRow.Custom -> CustomPositionRow(
                            position = row.position,
                            selected = entry.promptPositionId == row.position.id,
                            sortMode = sortMode,
                            isDragging = isDragging,
                            topConnected = topConnected,
                            bottomConnected = bottomConnected,
                            appearance = appearance,
                            dragModifier = if (sortMode) {
                                Modifier.draggableHandle(
                                    onDragStarted = {
                                        draggingId = row.position.id
                                        managedPositionId = row.position.id
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        draggingId = null
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                )
                            } else Modifier,
                            onClick = {
                                managedPositionId = row.position.id
                                if (entry.promptPositionId != row.position.id) {
                                    onEntriesChange(
                                        movePositionEntry(
                                            entries = entries,
                                            entryId = entry.id,
                                            targetPosition = row.position.anchor,
                                            targetPromptPositionId = row.position.id,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (allowCustomPromptPositions) nameDialog?.let { dialog ->
        PromptPositionNameDialog(
            dialog = dialog,
            existingNames = workingPositions.map { it.name.trim() }.toSet(),
            appearance = appearance,
            onDismiss = { nameDialog = null },
            onConfirm = { name ->
                when (dialog) {
                    PositionNameDialog.Create -> {
                        val now = Instant.now().toString()
                        val anchor = workingPositions.firstOrNull { it.id == entry.promptPositionId }?.anchor
                            ?: entry.position?.takeUnless { it == SettingLibraryPosition.Instructions }
                            ?: SettingLibraryPosition.InsertPoint1
                        val side = workingPositions.firstOrNull { it.id == entry.promptPositionId }?.side
                            ?: SettingLibraryPromptPositionSide.BeforeSettingPosition
                        val created = SettingLibraryPromptPosition(
                            id = "prompt-position-${UUID.randomUUID()}",
                            name = name,
                            anchor = anchor,
                            side = side,
                            order = workingPositions.count { it.anchor == anchor && it.side == side } + 1,
                            createdAt = now,
                            updatedAt = now,
                        )
                        managedPositionId = created.id
                        val next = (workingPositions + created).normalizedPositions()
                        workingPositions = next
                        onPromptPositionsChange(next)
                        onEntriesChange(
                            movePositionEntry(
                                entries = entries,
                                entryId = entry.id,
                                targetPosition = created.anchor,
                                targetPromptPositionId = created.id,
                            ),
                        )
                    }

                    is PositionNameDialog.Rename -> {
                        val now = Instant.now().toString()
                        val next = workingPositions.map { position ->
                            if (position.id == dialog.position.id) position.copy(name = name, updatedAt = now)
                            else position
                        }.normalizedPositions()
                        workingPositions = next
                        onPromptPositionsChange(next)
                    }
                }
                nameDialog = null
            },
        )
    }

    if (allowCustomPromptPositions && confirmDelete && managedPositionCanDelete) {
        val selected = workingPositions.firstOrNull { it.id == managedPositionId }
        if (selected == null) confirmDelete = false
        else ConfirmDialog(
            title = "删除提示词位置",
            message = "确定删除“${selected.name.ifBlank { "未命名提示词位置" }}”吗？",
            appearance = appearance,
            confirmText = "删除",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                val next = workingPositions.filterNot { it.id == selected.id }.normalizedPositions()
                workingPositions = next
                managedPositionId = next.firstOrNull()?.id.orEmpty()
                onPromptPositionsChange(next)
                if (entry.promptPositionId == selected.id) {
                    onEntryChange { it.copy(position = null, promptPositionId = "", enabled = false) }
                }
                confirmDelete = false
            },
        )
    }
}
