package com.eleckoi.android.feature.characters.ui.list.group

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.themedListRowClickable
import com.eleckoi.android.foundation.design.fieldPalette
import com.eleckoi.android.foundation.design.ElecKoiDanger
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.eleckoi.android.feature.characters.ui.list.*
import com.eleckoi.android.feature.characters.ui.list.components.CharacterGroupAction

private const val CharacterGroupRowHeight = 54

private class CharacterGroupManagerState {
    var deleteMode by mutableStateOf(false)
    var groupEditor by mutableStateOf<GroupEditor?>(null)
    var pendingDeleteGroup by mutableStateOf<String?>(null)
    var pickerGroup by mutableStateOf<String?>(null)
    var sortGroup by mutableStateOf<String?>(null)

    fun openAddGroup() {
        groupEditor = GroupEditor("add", "", "")
    }

    fun openRenameGroup(group: String) {
        groupEditor = GroupEditor("rename", group, group)
    }

    fun closeGroupEditor() {
        groupEditor = null
    }

    fun toggleDeleteMode() {
        deleteMode = !deleteMode
    }

    fun requestDeleteGroup(group: String) {
        pendingDeleteGroup = group
    }

    fun closePendingDeleteGroup() {
        pendingDeleteGroup = null
    }

    fun openPicker(group: String) {
        pickerGroup = group
    }

    fun closePicker() {
        pickerGroup = null
    }

    fun openSort(group: String) {
        sortGroup = group
    }

    fun closeSort() {
        sortGroup = null
    }
}

@Composable
private fun rememberCharacterGroupManagerState(): CharacterGroupManagerState {
    return remember { CharacterGroupManagerState() }
}

@Composable
internal fun CharacterGroupManagerPage(
    groups: List<String>,
    characters: CharactersPayload,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onSaveCharacters: (CharactersPayload) -> Unit,
) {
    val orderedGroups = groups
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val managerState = rememberCharacterGroupManagerState()
    with(managerState) {
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index - 1
        val toIndex = to.index - 1
        if (fromIndex !in orderedGroups.indices || toIndex !in orderedGroups.indices || fromIndex == toIndex) return@rememberReorderableLazyListState
        val next = orderedGroups.toMutableList()
        val moved = next.removeAt(fromIndex)
        next.add(toIndex, moved)
        onSaveCharacters(characters.copy(groups = next))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.mobileSurface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).themedListRowClickable(appearance = appearance, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.Back, appearance.mobileText, iconSize = 25.dp, strokeWidth = 1.8f)
            }
            Text("角色分组管理", modifier = Modifier.weight(1f), color = appearance.mobileText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Box(modifier = Modifier.size(44.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterGroupAction("添加分组", AppIconPaths.Plus, appearance, Modifier.weight(1f)) {
                openAddGroup()
            }
            CharacterGroupAction(if (deleteMode) "完成删除" else "删除分组", AppIconPaths.Trash, appearance, Modifier.weight(1f).padding(start = 10.dp)) {
                toggleDeleteMode()
            }
        }
        DividerLine(appearance)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "all") {
                CharacterAllGroupRow(characters.items.size, appearance) { openSort(ALL_CHARACTERS) }
            }
            items(orderedGroups, key = { it }) { group ->
                val count = characters.items.count { characterGroup(it) == group }
                ReorderableItem(reorderableState, key = group) { isDragging ->
                    CharacterGroupManageRow(
                        group = group,
                        count = count,
                        appearance = appearance,
                        dragging = isDragging,
                        deleteMode = deleteMode,
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        ),
                        onRename = { openRenameGroup(group) },
                        onSort = { openSort(group) },
                        onPick = { openPicker(group) },
                        onDelete = { requestDeleteGroup(group) },
                    )
                }
            }
        }
    }

    groupEditor?.let { editor ->
        GroupEditorDialog(
            editor = editor,
            groups = groups,
            characters = characters,
            appearance = appearance,
            onDismiss = ::closeGroupEditor,
            onSave = { next ->
                onSaveCharacters(next)
                closeGroupEditor()
            },
            onChange = { groupEditor = it },
        )
    }

    pendingDeleteGroup?.let { group ->
        ConfirmDialog(
            title = "删除分组？",
            message = "会删除「$group」这个分组，分组里的角色会回到全部角色。",
            appearance = appearance,
            onDismiss = ::closePendingDeleteGroup,
            onConfirm = {
                onSaveCharacters(
                    characters.copy(
                        groups = groups.filterNot { it == group },
                        items = characters.items.map {
                            if (characterGroup(it) == group) it.copy(group = "", groupViewOrder = 0) else it
                        },
                    ),
                )
                closePendingDeleteGroup()
            },
        )
    }

    pickerGroup?.let { group ->
        CharacterPickerPanel(
            group = group,
            groups = groups,
            characters = characters,
            appearance = appearance,
            onDismiss = ::closePicker,
            onConfirm = { ids ->
                val existingOrders = characters.items.filter { characterGroup(it) == group && it.groupViewOrder > 0 }.associate { it.id to it.groupViewOrder }
                var nextOrder = (existingOrders.values.maxOrNull() ?: 0) + 1
                onSaveCharacters(
                    characters.copy(
                        items = characters.items.map { character ->
                            when {
                                character.id in ids -> {
                                    val order = existingOrders[character.id] ?: nextOrder++
                                    character.copy(group = group, groupViewOrder = order)
                                }
                                characterGroup(character) == group -> character.copy(group = "", groupViewOrder = 0)
                                else -> character
                            }
                        },
                    ),
                )
                closePicker()
            },
        )
    }

    sortGroup?.let { group ->
        CharacterInternalSortPanel(
            group = group,
            characters = characters,
            appearance = appearance,
            onDismiss = ::closeSort,
            onSaveCharacters = onSaveCharacters,
        )
    }
    }
}

@Composable
private fun CharacterAllGroupRow(count: Int, appearance: AppearanceTheme, onSort: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(CharacterGroupRowHeight.dp).background(appearance.mobileBg).themedListRowClickable(appearance = appearance, onClick = onSort).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            StrokeSvgIcon(AppIconPaths.History, appearance.mobileMuted, iconSize = 20.dp, strokeWidth = 1.7f)
        }
        Text("全部角色 ($count)", modifier = Modifier.weight(1f).padding(start = 12.dp), color = appearance.mobileText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("排序", color = appearance.mobileMuted, fontSize = 12.sp)
    }
}

@Composable
private fun CharacterGroupManageRow(
    group: String,
    count: Int,
    appearance: AppearanceTheme,
    dragging: Boolean,
    deleteMode: Boolean,
    dragHandleModifier: Modifier,
    onRename: () -> Unit,
    onSort: () -> Unit,
    onPick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CharacterGroupRowHeight.dp)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                alpha = if (dragging) 0.82f else 1f
                scaleX = if (dragging) 1.015f else 1f
                scaleY = if (dragging) 1.015f else 1f
            }
            .background(if (dragging) appearance.mobileBlue.copy(alpha = 0.08f) else appearance.mobileBg)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp).then(dragHandleModifier), contentAlignment = Alignment.Center) {
            StrokeSvgIcon(emptyList(), appearance.mobileMuted, iconSize = 22.dp, strokeWidth = 1.6f, circles = CharacterDragHandleDots)
        }
        Text(
            "$group ($count)",
            modifier = Modifier.weight(1f).noRippleClickable(onClick = onRename).padding(start = 12.dp, end = 8.dp),
            color = appearance.mobileText,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconCircleButton(AppIconPaths.Eye, appearance.mobileMuted, appearance, onSort)
        IconCircleButton(AppIconPaths.Plus, appearance.mobileText, appearance, onPick)
        if (deleteMode) {
            IconCircleButton(AppIconPaths.Trash, ElecKoiDanger, appearance, onDelete)
        }
    }
    DividerLine(appearance)
}

@Composable
private fun IconCircleButton(
    icon: List<String>,
    contentColor: Color,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.padding(start = 6.dp).size(34.dp).clip(RoundedCornerShape(999.dp)).themedListRowClickable(appearance = appearance, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StrokeSvgIcon(icon, contentColor, iconSize = 18.dp, strokeWidth = 1.75f)
    }
}

private data class GroupEditor(val mode: String, val original: String, val value: String)

@Composable
private fun GroupEditorDialog(
    editor: GroupEditor,
    groups: List<String>,
    characters: CharactersPayload,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onSave: (CharactersPayload) -> Unit,
    onChange: (GroupEditor) -> Unit,
) {
    val field = appearance.fieldPalette()
    val name = editor.value.trim()
    val duplicate = name.isNotBlank() && name != editor.original && groups.contains(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.mode == "rename") "重命名该组" else "添加分组", color = appearance.mobileText) },
        text = {
            Column {
                AppInsetTextField(
                    value = editor.value,
                    onValueChange = { onChange(editor.copy(value = it)) },
                    appearance = appearance,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(color = field.text, fontSize = 16.sp),
                )
                if (duplicate) Text("分组名已存在", color = appearance.mobileBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !duplicate,
                onClick = {
                    val next = if (editor.mode == "rename") {
                        val nextGroups = groups.map { if (it == editor.original) name else it }
                        val nextItems = characters.items.map { if (characterGroup(it) == editor.original) it.copy(group = name) else it }
                        characters.copy(groups = nextGroups, items = nextItems)
                    } else {
                        characters.copy(groups = groups + name)
                    }
                    onSave(next)
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = appearance.mobileSurface,
    )
}
