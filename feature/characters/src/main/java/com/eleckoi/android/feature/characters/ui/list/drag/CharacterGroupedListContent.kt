package com.eleckoi.android.feature.characters.ui.list

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.MobileEmptyState
import com.eleckoi.android.feature.characters.model.CharacterSlot
import kotlin.math.roundToInt

internal data class CharacterDragState(
    val characterId: String,
    val sourceKey: String,
    val pointerY: Float,
    val grabOffsetY: Float,
)

internal data class CharacterGroupDragState(
    val group: String,
    val sourceKey: String,
    val pointerY: Float,
    val grabOffsetY: Float,
)

/** Renders the grouped list and drag overlays; gesture/state mutation stays in the parent. */
@Composable
internal fun CharacterGroupedListContent(
    listState: LazyListState,
    allCharacters: List<CharacterSlot>,
    groupCharacters: Map<String, List<CharacterSlot>>,
    localCharacters: List<CharacterSlot>,
    localExpandedGroupNames: Set<String>,
    keyword: String,
    listAllExpanded: Boolean,
    reorderEnabled: Boolean,
    disableAllHeaderClickUntil: Long,
    draggingCharacter: CharacterDragState?,
    draggingGroup: CharacterGroupDragState?,
    appearance: AppearanceTheme,
    containerColor: Color,
    useCoverArtwork: Boolean,
    onToggleGroup: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
) {
    val trimmedKeyword = keyword.trim()
    val groupSortingActive = draggingGroup != null
    val effectiveListAllExpanded = listAllExpanded && !groupSortingActive
    val allHeaderClickEnabled = !groupSortingActive && SystemClock.uptimeMillis() >= disableAllHeaderClickUntil

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (allCharacters.isEmpty() && groupCharacters.isEmpty()) {
            if (trimmedKeyword.isBlank()) {
                stickyHeader(key = "group-$ALL_CHARACTERS") {
                    CharacterGroupHeader(
                        title = ALL_CHARACTERS,
                        count = 0,
                        appearance = appearance,
                        containerColor = containerColor,
                        collapsed = false,
                        clickEnabled = false,
                        onClick = {},
                    )
                }
            }
            item { MobileEmptyState(if (keyword.isBlank()) "还没有角色" else "没有匹配的角色", appearance) }
        } else {
            stickyHeader(key = "group-$ALL_CHARACTERS") {
                CharacterGroupHeader(
                    title = ALL_CHARACTERS,
                    count = allCharacters.size,
                    appearance = appearance,
                    containerColor = containerColor,
                    collapsed = trimmedKeyword.isBlank() && !effectiveListAllExpanded,
                    clickEnabled = allHeaderClickEnabled,
                    onClick = { onToggleGroup(ALL_CHARACTERS) },
                )
            }
            if (trimmedKeyword.isNotBlank() || effectiveListAllExpanded) {
                items(allCharacters, key = { "$AllCharacterDragPrefix${it.id}" }) { character ->
                    CharacterGroupedListCharacterRow(
                        character = character,
                        appearance = appearance,
                        useCoverArtwork = useCoverArtwork,
                        reorderEnabled = reorderEnabled,
                        dragState = draggingCharacter?.takeIf {
                            it.characterId == character.id && it.sourceKey == "$AllCharacterDragPrefix${character.id}"
                        },
                        onOpenCharacter = onOpenCharacter,
                    )
                }
            }
            groupCharacters.forEach { (group, groupItems) ->
                val groupExpanded = !groupSortingActive && (trimmedKeyword.isNotBlank() || group in localExpandedGroupNames)
                val groupKey = "$CharacterGroupHeaderTargetPrefix$group"
                val groupDragState = draggingGroup?.takeIf { it.group == group && it.sourceKey == groupKey }
                stickyHeader(key = groupKey) {
                    CharacterGroupHeader(
                        title = group,
                        count = groupItems.size,
                        appearance = appearance,
                        containerColor = containerColor,
                        collapsed = !groupExpanded,
                        modifier = Modifier.graphicsLayer { if (groupDragState != null) alpha = 0f },
                        onClick = { onToggleGroup(group) },
                    )
                }
                if (groupExpanded) {
                    if (groupItems.isEmpty()) {
                        item("$CharacterGroupEmptyTargetPrefix$group") {
                            Box(modifier = Modifier.fillMaxWidth().height(28.dp))
                        }
                    }
                    items(groupItems, key = { "$GroupCharacterDragPrefix${it.id}" }) { character ->
                        CharacterGroupedListCharacterRow(
                            character = character,
                            appearance = appearance,
                            useCoverArtwork = useCoverArtwork,
                            reorderEnabled = reorderEnabled,
                            dragState = draggingCharacter?.takeIf {
                                it.characterId == character.id && it.sourceKey == "$GroupCharacterDragPrefix${character.id}"
                            },
                            onOpenCharacter = onOpenCharacter,
                        )
                    }
                }
            }
        }
    }
    draggingCharacter?.let { dragState ->
        localCharacters.firstOrNull { it.id == dragState.characterId }?.let { character ->
            CharacterListRow(
                character = character,
                appearance = appearance,
                useCoverArtwork = useCoverArtwork,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(3f)
                    .offset { IntOffset(0, (dragState.pointerY - dragState.grabOffsetY).roundToInt()) }
                    .graphicsLayer {
                        alpha = 0.9f
                        scaleX = 1.01f
                        scaleY = 1.01f
                    },
            ) {}
        }
    }
    draggingGroup?.let { dragState ->
        val groupItems = groupCharacters[dragState.group].orEmpty()
        CharacterGroupHeader(
            title = dragState.group,
            count = groupItems.size,
            appearance = appearance,
            containerColor = containerColor,
            collapsed = true,
            modifier = Modifier
                .zIndex(3f)
                .offset { IntOffset(0, (dragState.pointerY - dragState.grabOffsetY).roundToInt()) }
                .graphicsLayer {
                    alpha = 0.92f
                    scaleX = 1.01f
                    scaleY = 1.01f
                },
            onClick = {},
        )
    }
}

@Composable
private fun CharacterGroupedListCharacterRow(
    character: CharacterSlot,
    appearance: AppearanceTheme,
    useCoverArtwork: Boolean,
    reorderEnabled: Boolean,
    dragState: CharacterDragState?,
    onOpenCharacter: (String) -> Unit,
) {
    if (reorderEnabled) {
        CharacterListRow(
            character = character,
            appearance = appearance,
            useCoverArtwork = useCoverArtwork,
            modifier = Modifier.graphicsLayer { if (dragState != null) alpha = 0f },
        ) { onOpenCharacter(character.id) }
    } else {
        CharacterListRow(character, appearance, useCoverArtwork) { onOpenCharacter(character.id) }
    }
}
