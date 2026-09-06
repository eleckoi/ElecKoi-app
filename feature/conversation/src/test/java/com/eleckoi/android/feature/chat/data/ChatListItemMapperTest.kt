package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.foundation.storage.room.ChatListRoomRow
import com.eleckoi.android.foundation.storage.room.ChatSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListItemMapperTest {
    @Test
    fun `keeps persisted session when its imported character id is no longer live`() {
        val item = legacyRow(0).toChatListItem(character = null, snapshot = null)

        assertEquals("session-legacy-0", item.id)
        assertEquals("character-old-import-id-0", item.characterId)
        assertEquals("旧导入角色", item.characterName)
        assertEquals("content://old-avatar", item.characterAvatar)
        assertEquals("旧消息", item.summary)
    }

    @Test
    fun `repeatedly maps a long imported conversation list without dropping sessions`() {
        val rows = List(1_000, ::legacyRow)

        repeat(3) {
            val items = rows.map { row ->
                row.toChatListItem(character = null, snapshot = null)
            }

            assertEquals(1_000, items.size)
            assertEquals(1_000, items.mapTo(mutableSetOf()) { item -> item.id }.size)
        }
    }

    private fun legacyRow(index: Int): ChatListRoomRow = ChatListRoomRow(
        session = ChatSessionEntity(
            id = "session-legacy-$index",
            workspaceId = "workspace-legacy-$index",
            title = "",
            characterId = "character-old-import-id-$index",
            characterName = "旧导入角色",
            characterAvatar = "content://old-avatar",
            characterMode = "agent",
            historySummary = "旧消息",
            historyMessageCount = 2,
            historyUserMessageCount = 1,
            createdAt = "2026-08-30T00:00:00Z",
            updatedAt = "2026-08-30T00:01:00Z",
        ),
        summary = "旧消息",
        messageCount = 2,
    )
}
