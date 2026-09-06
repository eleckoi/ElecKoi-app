package com.eleckoi.android.foundation.storage.room

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "global_tool_config",
    primaryKeys = ["singletonId"],
)
data class GlobalToolConfigEntity(
    val singletonId: Int = 1,
    val payloadJson: String,
    val updatedAt: String,
)

@Entity(
    tableName = "character_tool_configs",
    primaryKeys = ["characterId"],
    foreignKeys = [ForeignKey(
        entity = CharacterEntity::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CharacterToolConfigEntity(
    val characterId: String,
    val payloadJson: String,
    val updatedAt: String,
)
