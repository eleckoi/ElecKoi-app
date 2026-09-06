package com.eleckoi.android.foundation.storage.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "frontend_projects",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = CharacterEntity::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characterId")],
)
data class FrontendProjectEntity(
    val id: String,
    val characterId: String,
    val name: String,
    val entryFile: String,
    val filesJson: String,
    val importedAt: String,
)

@Entity(
    tableName = "character_frontend_settings",
    primaryKeys = ["characterId"],
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FrontendProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["selectedProjectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("selectedProjectId")],
)
data class CharacterFrontendSettingsEntity(
    val characterId: String,
    val selectedProjectId: String?,
    val messageRendererEnabled: Boolean,
)
