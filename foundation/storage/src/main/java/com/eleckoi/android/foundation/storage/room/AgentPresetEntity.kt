package com.eleckoi.android.foundation.storage.room

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(tableName = "agent_preset_state")
data class AgentPresetStateEntity(
    @androidx.room.PrimaryKey
    val singletonId: Int = 0,
    val activePresetId: String,
)

@Entity(tableName = "agent_preset_library_groups")
data class AgentPresetLibraryGroupEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val name: String,
    val sortIndex: Int,
)

@Entity(tableName = "agent_presets")
data class AgentPresetEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val name: String,
    val modelFamily: String,
    @ColumnInfo(defaultValue = "'[]'")
    val modelTagsJson: String,
    @ColumnInfo(defaultValue = "'agent-preset-group-default'")
    val libraryGroupId: String,
    @ColumnInfo(defaultValue = "''")
    val activeVersionId: String,
    @ColumnInfo(defaultValue = "''")
    val authorName: String,
    @ColumnInfo(defaultValue = "''")
    val authorAvatarPath: String,
    val sortIndex: Int,
    val expandedGroupIdsJson: String,
)

/** Independently writable preset documents kept out of the frequently renamed metadata row. */
@Entity(
    tableName = "agent_preset_contents",
    primaryKeys = ["presetId", "kind"],
    foreignKeys = [ForeignKey(
        entity = AgentPresetEntity::class,
        parentColumns = ["id"],
        childColumns = ["presetId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("presetId")],
)
data class AgentPresetContentEntity(
    val presetId: String,
    val kind: String,
    val content: String,
)

@Entity(
    tableName = "agent_preset_entries",
    primaryKeys = ["presetId", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = AgentPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class AgentPresetEntryEntity(
    val presetId: String,
    val entryId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "agent_preset_groups",
    primaryKeys = ["presetId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = AgentPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class AgentPresetGroupEntity(
    val presetId: String,
    val groupId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "agent_preset_versions",
    primaryKeys = ["presetId", "versionId"],
    foreignKeys = [
        ForeignKey(
            entity = AgentPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class AgentPresetVersionEntity(
    val presetId: String,
    val versionId: String,
    val versionNumber: Int,
    @ColumnInfo(defaultValue = "''")
    val name: String,
    val createdAtEpochMs: Long,
    val expandedGroupIdsJson: String,
)

@Entity(
    tableName = "agent_preset_version_contents",
    primaryKeys = ["presetId", "versionId", "kind"],
    foreignKeys = [ForeignKey(
        entity = AgentPresetVersionEntity::class,
        parentColumns = ["presetId", "versionId"],
        childColumns = ["presetId", "versionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["presetId", "versionId"])],
)
data class AgentPresetVersionContentEntity(
    val presetId: String,
    val versionId: String,
    val kind: String,
    val content: String,
)

@Entity(
    tableName = "agent_preset_version_entries",
    primaryKeys = ["presetId", "versionId", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = AgentPresetVersionEntity::class,
            parentColumns = ["presetId", "versionId"],
            childColumns = ["presetId", "versionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId", "versionId"])],
)
data class AgentPresetVersionEntryEntity(
    val presetId: String,
    val versionId: String,
    val entryId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "agent_preset_version_groups",
    primaryKeys = ["presetId", "versionId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = AgentPresetVersionEntity::class,
            parentColumns = ["presetId", "versionId"],
            childColumns = ["presetId", "versionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId", "versionId"])],
)
data class AgentPresetVersionGroupEntity(
    val presetId: String,
    val versionId: String,
    val groupId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

data class AgentPresetVersionRecord(
    @Embedded
    val version: AgentPresetVersionEntity,
    @Relation(parentColumn = "versionId", entityColumn = "versionId")
    val contents: List<AgentPresetVersionContentEntity>,
    @Relation(
        parentColumn = "versionId",
        entityColumn = "versionId",
    )
    val entries: List<AgentPresetVersionEntryEntity>,
    @Relation(
        parentColumn = "versionId",
        entityColumn = "versionId",
    )
    val groups: List<AgentPresetVersionGroupEntity>,
)

/** Heavy child rows are only materialized when one preset is opened or selected for a turn. */
data class AgentPresetRecord(
    @Embedded
    val preset: AgentPresetEntity,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val contents: List<AgentPresetContentEntity>,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val entries: List<AgentPresetEntryEntity>,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val groups: List<AgentPresetGroupEntity>,
)

/** Lightweight row used by the preset manager; no prompt payload is read for the list screen. */
data class AgentPresetSummaryRecord(
    val id: String,
    val name: String,
    val modelFamily: String,
    val modelTagsJson: String,
    val libraryGroupId: String,
    val activeVersionId: String,
    val authorName: String,
    val authorAvatarPath: String,
    val usageInstructions: String,
    val timelineJson: String,
    val activeVersionNumber: Int,
    val sortIndex: Int,
    val entryCount: Int,
)

data class AgentPresetVersionSummaryRecord(
    val id: String,
    val number: Int,
    val name: String,
    val createdAtEpochMs: Long,
    val entryCount: Int,
)
