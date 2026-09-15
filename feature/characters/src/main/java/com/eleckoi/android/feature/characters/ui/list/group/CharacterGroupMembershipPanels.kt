package com.eleckoi.android.feature.characters.ui.list.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.foundation.design.components.MobileEmptyState
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppSearchField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.SvgCircle
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.themedListRowClickable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.eleckoi.android.feature.characters.ui.list.*

internal val CharacterDragHandleDots = listOf(
    SvgCircle(9f, 5f, 1.35f, fill = true),
    SvgCircle(9f, 12f, 1.35f, fill = true),
    SvgCircle(9f, 19f, 1.35f, fill = true),
    SvgCircle(15f, 5f, 1.35f, fill = true),
    SvgCircle(15f, 12f, 1.35f, fill = true),
    SvgCircle(15f, 19f, 1.35f, fill = true),
)

@Composable
internal fun CharacterPickerPanel(
    group: String,
    groups: List<String>,
    characters: CharactersPayload,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val selectedIds = remember(group, characters.items) {
        mutableStateListOf<String>().apply {
            addAll(characters.items.filter { characterGroup(it) == group }.map { it.id })
        }
    }
    var keyword by remember { mutableStateOf("") }
    val groupNames = groups.associateWith { it }
    val visible = filterCharacters(characters.items, keyword)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(appearance.mobileSurface)
                .noRippleClickable {}
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("添加角色", color = appearance.mobileText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(group, color = appearance.mobileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Text("取消", color = appearance.mobileMuted, fontSize = 14.sp, modifier = Modifier.noRippleClickable(onClick = onDismiss).padding(8.dp))
                Text("完成", color = appearance.mobileBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.noRippleClickable { onConfirm(selectedIds.toSet()) }.padding(8.dp))
            }
            AppSearchField(keyword, "搜索角色名称", appearance) { keyword = it }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                if (visible.isEmpty()) {
                    item { MobileEmptyState("没有匹配的角色", appearance) }
                } else {
                    items(visible, key = { "pick-${it.id}" }) { character ->
                        val checked = character.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .themedListRowClickable(appearance = appearance) {
                                    if (checked) selectedIds.remove(character.id) else selectedIds.add(character.id)
                                }
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value ->
                                    if (value) {
                                        if (character.id !in selectedIds) selectedIds.add(character.id)
                                    } else {
                                        selectedIds.remove(character.id)
                                    }
                                },
                            )
                            val currentGroup = groupNames[characterGroup(character)]
                            val title = if (currentGroup == null || currentGroup == group) {
                                characterName(character)
                            } else {
                                "${characterName(character)}（$currentGroup）"
                            }
                            Text(
                                title,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                color = appearance.mobileText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CharacterInternalSortPanel(
    group: String,
    characters: CharactersPayload,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onSaveCharacters: (CharactersPayload) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val groupCharacters = remember(characters.items, group) {
        if (group == ALL_CHARACTERS) sortedAllCharacters(characters.items) else sortedCharactersForGroup(characters.items, group)
    }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = (from.key as? String)?.removePrefix("sort-character-") ?: return@rememberReorderableLazyListState
        val toId = (to.key as? String)?.removePrefix("sort-character-") ?: return@rememberReorderableLazyListState
        val reordered = reorderCharacterDisplay(characters.items, group, fromId, toId)
            ?: return@rememberReorderableLazyListState
        onSaveCharacters(characters.copy(items = reordered))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(appearance.mobileSurface)
                .noRippleClickable {}
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("内部排序", color = appearance.mobileText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(group, color = appearance.mobileMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Text("完成", color = appearance.mobileBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.noRippleClickable(onClick = onDismiss).padding(8.dp))
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 8.dp)) {
                item(key = "sort-header-$group") {
                    CharacterSortHeader(group, groupCharacters.size, appearance)
                }
                if (groupCharacters.isEmpty()) {
                    item { MobileEmptyState("这个分组还没有角色", appearance) }
                } else {
                    items(groupCharacters, key = { "sort-character-${it.id}" }) { character ->
                        ReorderableItem(reorderableState, key = "sort-character-${character.id}") { isDragging ->
                            CharacterSortRow(
                                character = character,
                                appearance = appearance,
                                dragging = isDragging,
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterSortHeader(title: String, count: Int, appearance: AppearanceTheme) {
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).background(appearance.mobileBg).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$title ($count)", color = appearance.mobileMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CharacterSortRow(
    character: CharacterSlot,
    appearance: AppearanceTheme,
    dragging: Boolean,
    dragHandleModifier: Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                alpha = if (dragging) 0.82f else 1f
                scaleX = if (dragging) 1.012f else 1f
                scaleY = if (dragging) 1.012f else 1f
            }
            .background(if (dragging) appearance.mobileBlue.copy(alpha = 0.08f) else appearance.mobileSurface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(34.dp).then(dragHandleModifier), contentAlignment = Alignment.Center) {
            StrokeSvgIcon(emptyList(), appearance.mobileMuted, iconSize = 21.dp, strokeWidth = 1.6f, circles = CharacterDragHandleDots)
        }
        Text(
            characterName(character),
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            color = appearance.mobileText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    DividerLine(appearance)
}

@Composable
internal fun DividerLine(appearance: AppearanceTheme) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(appearance.mobileMuted.copy(alpha = 0.08f)))
}


@Composable
private fun ManagerSectionTitle(text: String, appearance: AppearanceTheme, action: (() -> Unit)? = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), color = appearance.mobileMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (action != null) {
            Box(modifier = Modifier.size(34.dp).noRippleClickable(onClick = action), contentAlignment = Alignment.Center) {
                StrokeSvgIcon(AppIconPaths.Plus, appearance.mobileText, iconSize = 22.dp)
            }
        }
    }
}
