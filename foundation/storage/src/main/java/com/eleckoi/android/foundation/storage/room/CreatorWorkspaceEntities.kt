package com.eleckoi.android.foundation.storage.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(
    tableName = "creator_workspaces",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = CharacterEntity::class,
        parentColumns = ["id"],
        childColumns = ["linkedCharacterId"],
    )],
    indices = [
        Index("linkedCharacterId"),
        Index("updatedAt"),
    ],
)
data class CreatorWorkspaceEntity(
    val id: String,
    val schemaVersion: Int,
    val name: String,
    val linkedCharacterId: String?,
    val characterOwned: Boolean,
    val primaryCharacterRootId: String?,
    val previewEntryFile: String?,
    val createdAt: String,
    val updatedAt: String,
    val totalBytes: Long,
    val latestCheckpointId: String?,
    val permissionMode: String,
    val activeConversationId: String?,
)

/** File bodies stay on disk; Room keeps one path per row for bounded catalog updates. */
@Entity(
    tableName = "creator_workspace_files",
    primaryKeys = ["workspaceId", "path"],
    foreignKeys = [ForeignKey(
        entity = CreatorWorkspaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["workspaceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workspaceId")],
)
data class CreatorWorkspaceFileEntity(
    val workspaceId: String,
    val path: String,
    val sortIndex: Int,
)

@Entity(
    tableName = "creator_workspace_character_roots",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = CreatorWorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("characterId")],
)
data class CreatorWorkspaceCharacterRootEntity(
    val workspaceId: String,
    val id: String,
    val characterId: String,
    val alias: String,
    val access: String,
)

@Entity(
    tableName = "creator_workspace_conversations",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [ForeignKey(
        entity = CreatorWorkspaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["workspaceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("id"), Index(value = ["workspaceId", "updatedAt"])],
)
data class CreatorWorkspaceConversationEntity(
    val workspaceId: String,
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
)

data class CreatorWorkspaceRecord(
    @Embedded val workspace: CreatorWorkspaceEntity,
    @Relation(parentColumn = "id", entityColumn = "workspaceId")
    val files: List<CreatorWorkspaceFileEntity>,
    @Relation(parentColumn = "id", entityColumn = "workspaceId")
    val characterRoots: List<CreatorWorkspaceCharacterRootEntity>,
    @Relation(parentColumn = "id", entityColumn = "workspaceId")
    val conversations: List<CreatorWorkspaceConversationEntity>,
)
