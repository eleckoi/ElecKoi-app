package com.eleckoi.android.foundation.storage.room

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(
    tableName = "variable_configs",
    primaryKeys = ["characterId"],
    foreignKeys = [ForeignKey(
        entity = CharacterEntity::class,
        parentColumns = ["id"], childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    ), ForeignKey(
        entity = VariableConfigVersionEntity::class,
        parentColumns = ["characterId", "versionId"], childColumns = ["characterId", "activeVersionId"],
        deferred = true,
    )],
    indices = [Index(value = ["characterId", "activeVersionId"])],
)
data class VariableConfigEntity(
    val characterId: String,
    val activeVersionId: String,
    val updatedAt: String,
    /** Monotonic business revision used for optimistic writes without hashing the config body. */
    val revision: Long,
)

@Entity(
    tableName = "variable_config_versions",
    primaryKeys = ["characterId", "versionId"],
    foreignKeys = [ForeignKey(
        entity = VariableConfigEntity::class,
        parentColumns = ["characterId"], childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class VariableConfigVersionEntity(
    val characterId: String,
    val versionId: String,
    val sortIndex: Int,
    val name: String,
    val expandedObjectIdsJson: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "variable_config_version_contents",
    primaryKeys = ["characterId", "versionId", "kind"],
    foreignKeys = [ForeignKey(
        entity = VariableConfigVersionEntity::class,
        parentColumns = ["characterId", "versionId"],
        childColumns = ["characterId", "versionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["characterId", "versionId"])],
)
data class VariableConfigVersionContentEntity(
    val characterId: String,
    val versionId: String,
    val kind: String,
    val content: String,
)

@Entity(
    tableName = "variable_config_objects",
    primaryKeys = ["characterId", "versionId", "objectId"],
    foreignKeys = [ForeignKey(
        entity = VariableConfigVersionEntity::class,
        parentColumns = ["characterId", "versionId"],
        childColumns = ["characterId", "versionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["characterId", "versionId"])],
)
data class VariableConfigObjectEntity(
    val characterId: String,
    val versionId: String,
    val objectId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "variable_config_variables",
    primaryKeys = ["characterId", "versionId", "variableId"],
    foreignKeys = [ForeignKey(
        entity = VariableConfigVersionEntity::class,
        parentColumns = ["characterId", "versionId"],
        childColumns = ["characterId", "versionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["characterId", "versionId"])],
)
data class VariableConfigVariableEntity(
    val characterId: String,
    val versionId: String,
    val variableId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

data class VariableConfigRecord(
    @Embedded val config: VariableConfigEntity,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val versions: List<VariableConfigVersionEntity>,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val contents: List<VariableConfigVersionContentEntity>,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val objects: List<VariableConfigObjectEntity>,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val variables: List<VariableConfigVariableEntity>,
)
