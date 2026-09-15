package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.feature.chat.model.ChatListItem

@Composable
internal fun MessagesRootPage(
    chats: List<ChatListItem>,
    pinnedChatIds: List<String>,
    hiddenChatIds: List<String>,
    activeChatSessionIds: Map<String, String>,
    useCoverArtwork: Boolean,
    appearance: AppearanceTheme,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenChat: (String) -> Unit,
    onTogglePinnedChat: (String) -> Unit,
    onHideChat: (String) -> Unit,
) {
    val swipeState = rememberMobileSwipeState()
    val pinned = pinnedChatIds.toSet()
    val conversationChats = orderMessageChats(
        chats = chats,
        pinnedChatIds = pinnedChatIds,
        hiddenChatIds = hiddenChatIds,
        activeChatSessionIds = activeChatSessionIds,
    )
    val pinnedItems = conversationChats.filter { it.id in pinned }
    val regularItems = conversationChats.filterNot { it.id in pinned }
    MobileRootSurface(
        appearance = appearance,
        header = {
            MobileRootActionHeader(
                title = "消息",
                appearance = appearance,
                onOpenSidebar = onOpenSidebar,
                onSearch = {
                    swipeState.close()
                    onSearch()
                },
                onAdd = onAdd,
            )
        },
    ) {
        if (conversationChats.isEmpty()) {
            MobileRootEmptyState(
                title = "还没有消息",
                message = "点击右上角的加号，开始新的对话",
                appearance = appearance,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 0.dp),
            ) {
                if (pinnedItems.isNotEmpty()) {
                    item("pinned-section") {
                        MessageListSectionHeader(
                            label = "置顶",
                            showPin = true,
                            appearance = appearance,
                        )
                    }
                    items(pinnedItems, key = { "pinned-${it.id}" }) { chat ->
                        MessageChatRow(
                            chat = chat,
                            isPinned = true,
                            appearance = appearance,
                            rowContainerColor = mobileRootContentColor(appearance),
                            useCoverArtwork = useCoverArtwork,
                            swipeState = swipeState,
                            onOpenChat = onOpenChat,
                            onTogglePinnedChat = onTogglePinnedChat,
                            onHideChat = onHideChat,
                        )
                    }
                    if (regularItems.isNotEmpty()) {
                        item("regular-section") {
                            MessageListSectionHeader(
                                label = "最近消息",
                                appearance = appearance,
                            )
                        }
                    }
                }
                items(regularItems, key = { it.id }) { chat ->
                    MessageChatRow(
                        chat = chat,
                        isPinned = false,
                        appearance = appearance,
                        rowContainerColor = mobileRootContentColor(appearance),
                        useCoverArtwork = useCoverArtwork,
                        swipeState = swipeState,
                        onOpenChat = onOpenChat,
                        onTogglePinnedChat = onTogglePinnedChat,
                        onHideChat = onHideChat,
                    )
                }
            }
        }
    }
}

internal fun orderMessageChats(
    chats: List<ChatListItem>,
    pinnedChatIds: List<String>,
    hiddenChatIds: List<String> = emptyList(),
    activeChatSessionIds: Map<String, String> = emptyMap(),
): List<ChatListItem> {
    val pinned = pinnedChatIds.toSet()
    val pinnedOrder = pinnedChatIds.withIndex().associate { it.value to it.index }
    return chats
        .sortedWith(
            compareByDescending<ChatListItem> { it.id in pinned }
                .thenBy { pinnedOrder[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.updatedAt },
        )
        .collapseByCharacter(activeChatSessionIds)
        .filterNot { it.id in hiddenChatIds }
}

private fun List<ChatListItem>.collapseByCharacter(
    activeChatSessionIds: Map<String, String>,
): List<ChatListItem> {
    val byId = associateBy(ChatListItem::id)
    val byCharacter = linkedMapOf<String, ChatListItem>()
    forEach { chat ->
        val key = chat.characterId.ifBlank { chat.characterName.ifBlank { chat.id } }
        if (key !in byCharacter) {
            val characterActive = activeChatSessionIds[key]
                ?.let(byId::get)
                ?.takeIf { candidate -> candidate.characterId == chat.characterId }
            byCharacter[key] = characterActive ?: chat
        }
    }
    return byCharacter.values.toList()
}

internal fun messageRootEntryTitle(chat: ChatListItem): String =
    chat.characterName.ifBlank { chat.title.ifBlank { "新对话" } }

internal fun filterMessageRootSearch(
    chats: List<ChatListItem>,
    keyword: String,
): List<ChatListItem> {
    val key = keyword.trim().lowercase()
    if (key.isBlank()) return chats
    return chats.filter { chat ->
        listOf(chat.title, chat.characterName, chat.summary)
            .joinToString(" ")
            .lowercase()
            .contains(key)
    }
}

@Composable
private fun MessageChatRow(
    chat: ChatListItem,
    isPinned: Boolean,
    appearance: AppearanceTheme,
    rowContainerColor: androidx.compose.ui.graphics.Color,
    useCoverArtwork: Boolean,
    swipeState: MobileSwipeState,
    onOpenChat: (String) -> Unit,
    onTogglePinnedChat: (String) -> Unit,
    onHideChat: (String) -> Unit,
) {
    var hideConfirmationOpen by remember(chat.id) { mutableStateOf(false) }
    MobileSwipeRow(
        key = chat.id,
        state = swipeState,
        actions = listOf(
            MobileSwipeAction(
                label = if (isPinned) "取消置顶" else "置顶",
                containerColor = appearance.mobileSearchBg,
                contentColor = appearance.mobileMuted,
                onClick = { onTogglePinnedChat(chat.id) },
                icon = { tint ->
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(19.dp),
                    )
                },
            ),
            MobileSwipeAction(
                label = "删除",
                containerColor = ElecKoiDanger,
                contentColor = appearance.mobileAccentFg,
                onClick = { hideConfirmationOpen = true },
                icon = { tint ->
                    StrokeSvgIcon(
                        paths = AppIconPaths.Trash,
                        color = tint,
                        iconSize = 19.dp,
                        strokeWidth = 1.8f,
                    )
                },
            ),
        ),
        rowHeight = mobileConversationRowHeight(useCoverArtwork),
        rowContainerColor = rowContainerColor,
        onClick = { onOpenChat(chat.id) },
    ) { rowClick ->
        MobileConversationRow(
            title = messageRootEntryTitle(chat),
            subtitle = chat.summary.ifBlank { "新对话" },
            avatarName = messageRootEntryTitle(chat),
            avatarPath = chat.characterAvatar,
            coverPath = chat.characterCover,
            useCoverArtwork = useCoverArtwork,
            sideText = formatShortDate(chat.updatedAt),
            appearance = appearance,
            pinned = isPinned,
            onClick = rowClick,
        )
    }
    if (hideConfirmationOpen) {
        ConfirmDialog(
            title = "删除消息入口？",
            message = "只会从消息列表隐藏这个入口；角色和聊天记录都会保留。",
            appearance = appearance,
            confirmText = "删除入口",
            destructive = true,
            onDismiss = { hideConfirmationOpen = false },
            onConfirm = {
                hideConfirmationOpen = false
                onHideChat(chat.id)
            },
        )
    }
}

@Composable
private fun MessageListSectionHeader(
    label: String,
    appearance: AppearanceTheme,
    showPin: Boolean = false,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 17.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPin) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                tint = appearance.mobileBlue,
                modifier = Modifier
                    .padding(end = 7.dp)
                    .size(16.dp),
            )
        }
        Text(
            text = label,
            color = appearance.mobileMuted,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
