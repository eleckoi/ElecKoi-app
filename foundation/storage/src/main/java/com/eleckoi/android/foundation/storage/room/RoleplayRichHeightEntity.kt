package com.eleckoi.android.foundation.storage.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Stable geometry for one rich-document root at one exact transcript width. */
@Entity(
    tableName = "roleplay_rich_heights",
    primaryKeys = ["sessionId", "messageId", "contentRevision", "rootIndex", "viewportWidthPx"],
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class RoleplayRichHeightEntity(
    val sessionId: String,
    val messageId: String,
    val contentRevision: String,
    val rootIndex: Int,
    val viewportWidthPx: Int,
    val heightPx: Int,
)
