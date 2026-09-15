package com.eleckoi.android.feature.characters.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.MobileRootEmptyState
import com.eleckoi.android.feature.characters.ui.list.components.CharacterWaterfallCard
import com.eleckoi.android.feature.characters.ui.list.components.CharacterWaterfallGroupChips
import com.eleckoi.android.feature.characters.ui.list.components.CharacterWaterfallSelectionBar

internal enum class CharacterBatchAction {
    Export,
    Delete,
}

private class CharacterWaterfallState {
    val selectedIds = mutableStateListOf<String>()
    var pendingDeleteCharacters by mutableStateOf<List<String>?>(null)

    fun toggleCharacterSelection(characterId: String) {
        if (selectedIds.contains(characterId)) {
            selectedIds.remove(characterId)
        } else {
            selectedIds.add(characterId)
        }
    }

    fun clearPendingDelete() {
        pendingDeleteCharacters = null
    }

    fun confirmDeleteHandled() {
        selectedIds.clear()
        pendingDeleteCharacters = null
    }
}

@Composable
private fun rememberCharacterWaterfallState(): CharacterWaterfallState {
    return remember { CharacterWaterfallState() }
}

@Composable
internal fun CharacterWaterfall(
    user: UserProfile,
    characters: CharactersPayload,
    groups: List<String>,
    selectedGroup: String,
    batchAction: CharacterBatchAction?,
    appearance: AppearanceTheme,
    onSelectGroup: (String) -> Unit,
    onBatchActionChange: (CharacterBatchAction?) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onExportCharacters: (List<String>) -> Unit,
    onDeleteCharacters: (List<String>) -> Unit,
) {
    val state = rememberCharacterWaterfallState()
    val visible = characters.items.let { filtered ->
        if (selectedGroup == ALL_CHARACTERS) {
            sortedAllCharacters(filtered)
        } else {
            sortedCharactersForGroup(filtered, selectedGroup)
        }
    }

    fun exitBatchMode() {
        onBatchActionChange(null)
        state.selectedIds.clear()
    }

    BackHandler(enabled = batchAction != null) {
        exitBatchMode()
    }

    LaunchedEffect(batchAction) {
        state.selectedIds.clear()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
    ) {
        if (batchAction != null) {
            val currentAction = checkNotNull(batchAction)
            val allVisibleSelected = visible.isNotEmpty() &&
                visible.all { character -> character.id in state.selectedIds }
            CharacterWaterfallSelectionBar(
                selectedCount = state.selectedIds.size,
                allSelected = allVisibleSelected,
                action = currentAction,
                appearance = appearance,
                onToggleSelectAll = {
                    val visibleIds = visible.map { character -> character.id }.toSet()
                    if (allVisibleSelected) {
                        state.selectedIds.removeAll(visibleIds)
                    } else {
                        state.selectedIds.addAll(visibleIds - state.selectedIds.toSet())
                    }
                },
                onConfirm = {
                    val ids = state.selectedIds.toList()
                    if (ids.isEmpty()) return@CharacterWaterfallSelectionBar
                    when (currentAction) {
                        CharacterBatchAction.Export -> {
                            onExportCharacters(ids)
                            exitBatchMode()
                        }
                        CharacterBatchAction.Delete -> state.pendingDeleteCharacters = ids
                    }
                },
                onCancel = ::exitBatchMode,
            )
        }

        if (visible.isEmpty()) {
            val libraryEmpty = characters.items.isEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                MobileRootEmptyState(
                    title = if (libraryEmpty) "还没有角色" else "这个分组还没有角色",
                    message = if (libraryEmpty) {
                        "打开右上角菜单，新建或导入角色"
                    } else {
                        "可以在分组管理中把已有角色添加到这里"
                    },
                    appearance = appearance,
                    modifier = Modifier.fillMaxSize(),
                )
                CharacterWaterfallGroupChips(
                    groups = groups,
                    characters = characters,
                    selectedGroup = selectedGroup,
                    appearance = appearance,
                    onSelectGroup = onSelectGroup,
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 0.dp, end = 0.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalItemSpacing = 4.dp,
            ) {
                item(key = "character-groups", span = StaggeredGridItemSpan.FullLine) {
                    CharacterWaterfallGroupChips(
                        groups = groups,
                        characters = characters,
                        selectedGroup = selectedGroup,
                        appearance = appearance,
                        onSelectGroup = onSelectGroup,
                    )
                }
                itemsIndexed(visible, key = { _, character -> character.id }) { index, character ->
                    CharacterWaterfallCard(
                        user = user,
                        character = character,
                        artworkAspectRatio = waterfallArtworkAspectRatio(index),
                        selected = state.selectedIds.contains(character.id),
                        selectable = batchAction != null,
                        appearance = appearance,
                    ) {
                        if (batchAction != null) {
                            state.toggleCharacterSelection(character.id)
                        } else {
                            onOpenCharacter(character.id)
                        }
                    }
                }
            }
        }
    }

    state.pendingDeleteCharacters?.let { ids ->
        ConfirmDialog(
            title = "删除角色？",
            message = "将删除 ${ids.size} 个角色和对应聊天记录。",
            appearance = appearance,
            onDismiss = state::clearPendingDelete,
            onConfirm = {
                onDeleteCharacters(ids)
                state.confirmDeleteHandled()
                onBatchActionChange(null)
            },
        )
    }
}

internal fun waterfallArtworkAspectRatio(index: Int): Float = when (index.mod(4)) {
    0 -> 0.76f
    1 -> 0.68f
    2 -> 0.84f
    else -> 0.72f
}
