package com.eleckoi.android.foundation.storage.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Relation

@Entity(
    tableName = "chat_sessions",
    indices = [
        Index("characterId"),
        Index("updatedAt"),
    ],
    primaryKeys = ["id"],
)
data class ChatSessionEntity(
    val id: String,
    val workspaceId: String,
    val title: String,
    val characterId: String,
    val characterName: String,
    val characterAvatar: String,
    val permissionMode: String = "AskForApproval",
    /** Counts and preview text derived from the normalized Room ledger. */
    val historySummary: String,
    val historyMessageCount: Int,
    val historyUserMessageCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "chat_session_character_snapshots",
    primaryKeys = ["sessionId"],
    foreignKeys = [ForeignKey(
        entity = ChatSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ChatSessionCharacterSnapshotEntity(
    val sessionId: String,
    val personaJson: String,
)

@Entity(
    tableName = "chat_session_variable_states",
    primaryKeys = ["sessionId", "kind"],
    foreignKeys = [ForeignKey(
        entity = ChatSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ChatSessionVariableStateEntity(
    val sessionId: String,
    /** Either `initial` (immutable baseline) or `current` (updated as the story advances). */
    val kind: String,
    val stateJson: String,
)

/** Full session aggregate. Large snapshots remain independently writable child rows. */
data class ChatSessionRecord(
    @Embedded val session: ChatSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val characterSnapshot: ChatSessionCharacterSnapshotEntity?,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val variableStates: List<ChatSessionVariableStateEntity>,
)

data class ChatListRoomRow(
    @Embedded val session: ChatSessionEntity,
    val summary: String,
    val messageCount: Int,
)
