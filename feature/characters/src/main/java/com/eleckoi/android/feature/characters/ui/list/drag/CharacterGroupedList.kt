package com.eleckoi.android.feature.characters.ui.list

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import kotlinx.coroutines.delay

@Composable
internal fun CharacterGroupedList(
    characters: List<CharacterSlot>,
    groups: List<String>,
    payload: CharactersPayload?,
    keyword: String,
    listAllExpanded: Boolean,
    expandedGroupNames: Set<String>,
    appearance: AppearanceTheme,
    useCoverArtwork: Boolean,
    onToggleGroup: (String, Set<String>?) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onSaveCharacters: (CharactersPayload) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val latestPayload by rememberUpdatedState(payload)
    val trimmedKeyword = keyword.trim()
    var localCharacters by remember { mutableStateOf(characters) }
    var localGroups by remember { mutableStateOf(groups) }
    var localExpandedGroupNames by remember { mutableStateOf(expandedGroupNames) }
    var dragActive by remember { mutableStateOf(false) }
    var draggingCharacter by remember { mutableStateOf<CharacterDragState?>(null) }
    var pendingGroupDrop by remember { mutableStateOf<PendingCharacterGroupDrop?>(null) }
    var draggingGroup by remember { mutableStateOf<CharacterGroupDragState?>(null) }
    var dragExpandedGroupNamesSnapshot by remember { mutableStateOf<Set<String>?>(null) }
    var suppressHeaderClickUntil by remember { mutableStateOf(0L) }
    var disableAllHeaderClickUntil by remember { mutableStateOf(0L) }
    var suppressNextHeaderClickAfterDrag by remember { mutableStateOf(false) }

    LaunchedEffect(characters) {
        if (!dragActive) localCharacters = characters
    }

    LaunchedEffect(groups) {
        if (draggingGroup == null) localGroups = groups
    }

    LaunchedEffect(expandedGroupNames) {
        if (!dragActive && draggingGroup == null && draggingCharacter == null) {
            localExpandedGroupNames = expandedGroupNames
        }
    }


    LaunchedEffect(disableAllHeaderClickUntil) {
        val remaining = disableAllHeaderClickUntil - SystemClock.uptimeMillis()
        if (remaining > 0L) {
            delay(remaining)
            disableAllHeaderClickUntil = 0L
        }
    }

    fun commitLocalCharacters(nextCharacters: List<CharacterSlot> = localCharacters) {
        val currentPayload = latestPayload ?: return
        val preservedExpandedGroups = dragExpandedGroupNamesSnapshot ?: localExpandedGroupNames
        onSaveCharacters(
            currentPayload.copy(
                items = nextCharacters,
                groups = localGroups,
                expandedGroupNames = preservedExpandedGroups.toList(),
            ),
        )
        dragActive = false
    }

    val allCharacters = sortedAllCharacters(filterCharacters(localCharacters, keyword))
    val groupCharacters = localGroups.associateWith { group ->
        sortedCharactersForGroup(filterCharacters(localCharacters, keyword), group)
    }.filter { (group, groupItems) ->
        trimmedKeyword.isBlank() || groupItems.isNotEmpty() || group.lowercase().contains(trimmedKeyword.lowercase())
    }
    val reorderEnabled = trimmedKeyword.isBlank() && payload != null

    fun commitPendingGroupDrop(): Boolean {
        val drop = pendingGroupDrop
        pendingGroupDrop = null
        if (drop == null) return false
        val nextCharacters = moveCharacterToGroup(
            characters = localCharacters,
            groups = localGroups,
            characterId = drop.characterId,
            targetGroup = drop.group,
            targetCharacterId = drop.targetCharacterId,
        ) ?: return false
        localCharacters = nextCharacters
        commitLocalCharacters(nextCharacters)
        return true
    }

    fun visibleItemKeyAt(pointerY: Float): String? {
        return listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> pointerY >= item.offset && pointerY <= item.offset + item.size }
            ?.key as? String
    }

    fun characterDragTargetKey(key: String): Boolean {
        return key.startsWith(AllCharacterDragPrefix) ||
            key.startsWith(GroupCharacterDragPrefix) ||
            key.startsWith(CharacterGroupHeaderTargetPrefix) ||
            key.startsWith(CharacterGroupEmptyTargetPrefix)
    }

    fun visibleKeyAt(pointerY: Float, allowNearest: Boolean = true): String? {
        val targets = listState.layoutInfo.visibleItemsInfo.filter { item ->
            (item.key as? String)?.let(::characterDragTargetKey) == true
        }
        if (targets.isEmpty()) return null
        return targets.firstOrNull { item -> pointerY >= item.offset && pointerY <= item.offset + item.size }
            ?.key as? String
            ?: if (allowNearest) {
                targets.minBy { item ->
                val topDistance = kotlin.math.abs(pointerY - item.offset)
                val bottomDistance = kotlin.math.abs(pointerY - (item.offset + item.size))
                minOf(topDistance, bottomDistance)
                }.key as? String
            } else {
                null
            }
    }

    fun visibleItemTop(key: String): Float {
        return listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.key == key }
            ?.offset
            ?.toFloat()
            ?: 0f
    }

    fun handleCharacterDragMove(sourceKey: String, pointerY: Float) {
        if (!reorderEnabled) return
        val fromId = characterIdFromDragKey(sourceKey) ?: return
        val edge = with(density) { 72.dp.toPx() }
        val viewportStart = listState.layoutInfo.viewportStartOffset
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val inAutoScrollEdge = pointerY < viewportStart + edge || pointerY > viewportEnd - edge
        val toKey = visibleKeyAt(pointerY, allowNearest = !inAutoScrollEdge) ?: return
        val allMove = sourceKey.startsWith(AllCharacterDragPrefix) && toKey.startsWith(AllCharacterDragPrefix)
        val groupMove = sourceKey.startsWith(GroupCharacterDragPrefix) && toKey.startsWith(GroupCharacterDragPrefix)
        val groupTarget = groupTargetFromCharacterKey(toKey, localCharacters)
        if (allMove) {
            pendingGroupDrop = null
            val toId = toKey.removePrefix(AllCharacterDragPrefix)
            localCharacters = reorderAllCharacters(localCharacters, fromId, toId) ?: return
        } else {
            if (!groupMove) {
                if (groupTarget != null) {
                    pendingGroupDrop = PendingCharacterGroupDrop(fromId, groupTarget.first, groupTarget.second)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } else {
                    pendingGroupDrop = null
                }
                return
            }
            val toId = toKey.removePrefix(GroupCharacterDragPrefix)
            val fromCharacter = localCharacters.firstOrNull { it.id == fromId } ?: return
            val toCharacter = localCharacters.firstOrNull { it.id == toId } ?: return
            val group = characterGroup(fromCharacter)
            if (group.isBlank() || group != characterGroup(toCharacter)) {
                groupTarget?.let { (targetGroup, targetCharacterId) ->
                    pendingGroupDrop = PendingCharacterGroupDrop(fromId, targetGroup, targetCharacterId)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                return
            }
            pendingGroupDrop = null
            localCharacters = reorderCharactersInGroup(
                characters = localCharacters,
                group = group,
                fromId = fromId,
                toId = toId,
            ) ?: return
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun updateGroups(nextGroups: List<String>) {
        val currentPayload = latestPayload ?: return
        val normalized = nextGroups.map { it.trim() }.filter { it.isNotBlank() && it != ALL_CHARACTERS }.distinct()
        localGroups = normalized
        onSaveCharacters(
            currentPayload.copy(
                groups = normalized,
                expandedGroupNames = localExpandedGroupNames.filter { it in normalized }.toList(),
            ),
        )
    }

    fun toggleGroupFromHeader(group: String) {
        val now = SystemClock.uptimeMillis()
        val sortingGroups = draggingGroup != null
        val syntheticHeaderClick = suppressNextHeaderClickAfterDrag
        if (sortingGroups || now < suppressHeaderClickUntil || syntheticHeaderClick) {
            suppressNextHeaderClickAfterDrag = false
            return
        }
        var nextExpandedGroupNames: Set<String>? = null
        if (group != ALL_CHARACTERS) {
            nextExpandedGroupNames = if (group in localExpandedGroupNames) {
                localExpandedGroupNames - group
            } else {
                localExpandedGroupNames + group
            }
            localExpandedGroupNames = nextExpandedGroupNames
        }
        onToggleGroup(group, nextExpandedGroupNames)
    }

    fun dragItemHeight(sourceKey: String): Float {
        return listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.key == sourceKey }
            ?.size
            ?.toFloat()
            ?: with(density) {
                when {
                    sourceKey.startsWith(CharacterGroupHeaderTargetPrefix) -> 46.dp.toPx()
                    sourceKey.startsWith(CharacterGroupEmptyTargetPrefix) -> 28.dp.toPx()
                    else -> if (useCoverArtwork) 86.dp.toPx() else 62.dp.toPx()
                }
            }
    }

    fun edgeScrollDelta(sourceKey: String, pointerY: Float, grabOffsetY: Float): Float {
        val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
        val edge = with(density) { 112.dp.toPx() }
        val itemTop = pointerY - grabOffsetY
        val itemBottom = itemTop + dragItemHeight(sourceKey)
        val topOverflow = viewportStart + edge - itemTop
        val bottomOverflow = itemBottom - (viewportEnd - edge)
        return when {
            topOverflow > 0f -> {
                val factor = (topOverflow / edge).coerceIn(0f, 1f)
                -(18f + 24f * factor)
            }
            bottomOverflow > 0f -> {
                val factor = (bottomOverflow / edge).coerceIn(0f, 1f)
                18f + 24f * factor
            }
            else -> 0f
        }
    }

    fun isPointerOnAllHeader(pointerY: Float): Boolean {
        return listState.layoutInfo.visibleItemsInfo.any { item ->
            item.key == "group-$ALL_CHARACTERS" && pointerY >= item.offset && pointerY <= item.offset + item.size
        }
    }

    fun handleGroupDragMove(group: String, pointerY: Float) {
        if (!reorderEnabled) return
        val edge = with(density) { 72.dp.toPx() }
        val viewportStart = listState.layoutInfo.viewportStartOffset
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val inAutoScrollEdge = pointerY < viewportStart + edge || pointerY > viewportEnd - edge
        val targetKey = visibleKeyAt(pointerY, allowNearest = !inAutoScrollEdge) ?: return
        val targetGroup = groupTargetFromCharacterKey(targetKey, localCharacters)?.first ?: return
        if (targetGroup == group) return
        val displayGroups = localGroups.toMutableList()
        val fromIndex = displayGroups.indexOf(group)
        val toIndex = displayGroups.indexOf(targetGroup)
        if (fromIndex !in displayGroups.indices || toIndex !in displayGroups.indices || fromIndex == toIndex) return
        val moved = displayGroups.removeAt(fromIndex)
        displayGroups.add(toIndex, moved)
        updateGroups(displayGroups)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun Modifier.characterListDragContainerModifier(): Modifier {
        if (!reorderEnabled) return this
        return pointerInput(reorderEnabled) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val itemKey = visibleItemKeyAt(offset.y) ?: return@detectDragGesturesAfterLongPress
                    val itemTop = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == itemKey }
                        ?.offset
                        ?: 0
                    val pointerY = offset.y
                    pendingGroupDrop = null
                    val characterId = characterIdFromDragKey(itemKey)
                    val group = itemKey.takeIf { it.startsWith(CharacterGroupHeaderTargetPrefix) }
                        ?.removePrefix(CharacterGroupHeaderTargetPrefix)
                    when {
                        characterId != null -> {
                            dragExpandedGroupNamesSnapshot = localExpandedGroupNames
                            draggingCharacter = CharacterDragState(
                                characterId = characterId,
                                sourceKey = itemKey,
                                pointerY = pointerY,
                                grabOffsetY = offset.y - itemTop,
                            )
                            draggingGroup = null
                            dragActive = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        group != null && group in localGroups -> {
                            dragExpandedGroupNamesSnapshot = localExpandedGroupNames
                            suppressNextHeaderClickAfterDrag = true
                            draggingCharacter = null
                            draggingGroup = CharacterGroupDragState(
                                group = group,
                                sourceKey = itemKey,
                                pointerY = pointerY,
                                grabOffsetY = offset.y - itemTop,
                            )
                            dragActive = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val characterDrag = draggingCharacter
                    val groupDrag = draggingGroup
                    if (characterDrag != null) {
                        val nextPointerY = characterDrag.pointerY + dragAmount.y
                        val nextDrag = characterDrag.copy(pointerY = nextPointerY)
                        draggingCharacter = nextDrag
                        handleCharacterDragMove(nextDrag.sourceKey, nextPointerY)
                    } else if (groupDrag != null) {
                        val nextPointerY = groupDrag.pointerY + dragAmount.y
                        val nextDrag = groupDrag.copy(pointerY = nextPointerY)
                        draggingGroup = nextDrag
                        handleGroupDragMove(nextDrag.group, nextPointerY)
                    }
                },
                onDragEnd = {
                    val endedCharacterDrag = draggingCharacter
                    val endedGroupDrag = draggingGroup
                    if (draggingCharacter != null && !commitPendingGroupDrop()) {
                        commitLocalCharacters()
                    }
                    if (endedCharacterDrag != null || endedGroupDrag != null) {
                        val now = SystemClock.uptimeMillis()
                        suppressNextHeaderClickAfterDrag = true
                        suppressHeaderClickUntil = now + 500L
                    }
                    if (endedGroupDrag != null) {
                        val now = SystemClock.uptimeMillis()
                        if (isPointerOnAllHeader(endedGroupDrag.pointerY)) {
                            disableAllHeaderClickUntil = now + 1200L
                        }
                    }
                    draggingCharacter = null
                    draggingGroup = null
                    dragExpandedGroupNamesSnapshot = null
                    dragActive = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onDragCancel = {
                    val canceledCharacterDrag = draggingCharacter
                    val canceledGroupDrag = draggingGroup
                    pendingGroupDrop = null
                    if (canceledCharacterDrag != null || canceledGroupDrag != null) {
                        val now = SystemClock.uptimeMillis()
                        suppressNextHeaderClickAfterDrag = true
                        suppressHeaderClickUntil = now + 500L
                    }
                    if (canceledGroupDrag != null) {
                        val now = SystemClock.uptimeMillis()
                        if (isPointerOnAllHeader(canceledGroupDrag.pointerY)) {
                            disableAllHeaderClickUntil = now + 1200L
                        }
                    }
                    draggingCharacter = null
                    draggingGroup = null
                    dragExpandedGroupNamesSnapshot = null
                    dragActive = false
                },
            )
        }
    }

    LaunchedEffect(draggingCharacter?.sourceKey, draggingGroup?.sourceKey, reorderEnabled) {
        while (reorderEnabled && (draggingCharacter != null || draggingGroup != null)) {
            val characterDrag = draggingCharacter
            val groupDrag = draggingGroup
            val delta = when {
                characterDrag != null -> edgeScrollDelta(characterDrag.sourceKey, characterDrag.pointerY, characterDrag.grabOffsetY)
                groupDrag != null -> edgeScrollDelta(groupDrag.sourceKey, groupDrag.pointerY, groupDrag.grabOffsetY)
                else -> break
            }
            if (delta != 0f) {
                listState.scrollBy(delta)
                if (characterDrag != null) {
                    handleCharacterDragMove(characterDrag.sourceKey, characterDrag.pointerY)
                } else if (groupDrag != null) {
                    handleGroupDragMove(groupDrag.group, groupDrag.pointerY)
                }
            }
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .characterListDragContainerModifier(),
    ) {
        CharacterGroupedListContent(
            listState = listState,
            allCharacters = allCharacters,
            groupCharacters = groupCharacters,
            localCharacters = localCharacters,
            localExpandedGroupNames = localExpandedGroupNames,
            keyword = keyword,
            listAllExpanded = listAllExpanded,
            reorderEnabled = reorderEnabled,
            disableAllHeaderClickUntil = disableAllHeaderClickUntil,
            draggingCharacter = draggingCharacter,
            draggingGroup = draggingGroup,
            appearance = appearance,
            useCoverArtwork = useCoverArtwork,
            onToggleGroup = ::toggleGroupFromHeader,
            onOpenCharacter = onOpenCharacter,
        )
    }
}
