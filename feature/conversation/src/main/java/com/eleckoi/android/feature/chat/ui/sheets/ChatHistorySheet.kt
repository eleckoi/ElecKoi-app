package com.eleckoi.android.feature.chat.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.AvatarCircle
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.MobileBottomSheetOverlay
import com.eleckoi.android.foundation.design.fieldPalette
import com.eleckoi.android.foundation.design.selectionPalette

private class ChatHistorySheetState {
    var keyword by mutableStateOf("")
    var pendingDelete by mutableStateOf<ChatListItem?>(null)

    fun requestDelete(item: ChatListItem) {
        pendingDelete = item
    }

    fun clearPendingDelete() {
        pendingDelete = null
    }

    fun takePendingDeleteId(): String? {
        val id = pendingDelete?.id
        pendingDelete = null
        return id
    }
}

@Composable
private fun rememberChatHistorySheetState(): ChatHistorySheetState {
    return remember { ChatHistorySheetState() }
}

@Composable
fun ChatHistorySheet(
    visible: Boolean = true,
    sessions: List<ChatListItem>,
    currentSessionId: String,
    currentCharacterId: String,
    characterName: String,
    saveMode: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onLoadChat: (String) -> Unit,
    onSaveMode: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (List<String>) -> Unit,
    onImport: () -> Unit,
) {
    val sheetState = rememberChatHistorySheetState()

    with(sheetState) {
    val key = keyword.trim().lowercase()
    val related = sessions
        .filter { it.characterId == currentCharacterId }
        .filter { key.isBlank() || listOf(it.title, it.characterName, it.summary, it.updatedAt).joinToString(" ").lowercase().contains(key) }
        .sortedWith(compareByDescending<ChatListItem> { it.id == currentSessionId }.thenByDescending { it.updatedAt })
        .let { if (saveMode == "recent10") it.take(10) else it }

    MobileBottomSheetOverlay(
        visible = visible,
        appearance = appearance,
        onDismiss = onDismiss,
        sheetModifier = Modifier.height(650.dp),
        showHandle = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .noRippleClickable {},
        ) {
            SheetHeader(characterName.ifBlank { "聊天记录" }, "聊天记录", appearance, onDismiss)
            SearchField(keyword, "搜索历史对话", appearance) { keyword = it }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PolicyButton("保留全部", saveMode != "recent10", appearance, Modifier.weight(1f)) { onSaveMode("all") }
                PolicyButton("最新10条", saveMode == "recent10", appearance, Modifier.weight(1f)) { onSaveMode("recent10") }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                HistoryTransferButton("导入记录(实验)", AppIconPaths.Image, appearance, Modifier.weight(1f), onClick = onImport)
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (related.isEmpty()) {
                    item { Text("当前模式还没有本地保存的对话。", modifier = Modifier.padding(24.dp), color = appearance.mobileMuted, fontSize = 14.sp) }
                }
                items(related, key = { it.id }) { item ->
                    HistoryRow(
                        item = item,
                        active = item.id == currentSessionId,
                        appearance = appearance,
                        onClick = { onLoadChat(item.id) },
                        onExport = { onExport(listOf(item.id)) },
                        onDelete = { requestDelete(item) },
                    )
                }
            }
        }
    }

    pendingDelete?.let {
        ConfirmDialog(
            title = "删除历史对话",
            message = "这条历史记录会被删除，删除后无法恢复。",
            appearance = appearance,
            confirmText = "确认删除",
            onDismiss = ::clearPendingDelete,
            onConfirm = {
                takePendingDeleteId()?.let(onDelete)
            },
        )
    }
    }
}

@Composable
private fun PolicyButton(text: String, active: Boolean, appearance: AppearanceTheme, modifier: Modifier, onClick: () -> Unit) {
    val field = appearance.fieldPalette()
    val selection = appearance.selectionPalette()
    Box(modifier = modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (active) selection.activeContainer else field.container).noRippleClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (active) selection.indicator else field.text, fontSize = 14.sp)
    }
}

@Composable
private fun HistoryTransferButton(
    text: String,
    paths: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val field = appearance.fieldPalette()
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(field.container)
            .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeSvgIcon(paths, if (enabled) field.icon else field.placeholder.copy(alpha = 0.62f), iconSize = 18.dp)
        Text(
            text = text,
            modifier = Modifier.padding(start = 7.dp),
            color = if (enabled) field.text else field.placeholder.copy(alpha = 0.62f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryRow(
    item: ChatListItem,
    active: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val selection = appearance.selectionPalette()
    Row(modifier = Modifier.fillMaxWidth().height(64.dp).background(if (active) selection.activeContainer else Color.Transparent).padding(start = 18.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f).noRippleClickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(item.characterName.ifBlank { item.title }, 38, 14, appearance, item.characterAvatar)
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(if (active) "当前对话" else item.title, color = if (active) selection.activeText else selection.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.summary.ifBlank { "新对话" }, color = selection.mutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(timeTitle(item.updatedAt), color = selection.mutedText, fontSize = 11.sp)
        }
        Box(modifier = Modifier.size(38.dp).noRippleClickable(onClick = onExport), contentAlignment = Alignment.Center) {
            StrokeSvgIcon(AppIconPaths.Export, appearance.mobileMuted, iconSize = 19.dp)
        }
        Box(modifier = Modifier.size(38.dp).noRippleClickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            StrokeSvgIcon(AppIconPaths.X, appearance.mobileMuted, iconSize = 18.dp)
        }
    }
}

private fun timeTitle(value: String): String {
    return value.substringAfter('T', value).take(5)
}
