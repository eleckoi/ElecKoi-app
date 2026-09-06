package com.eleckoi.android.foundation.storage.room

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(tableName = "story_preset_state")
data class StoryPresetStateEntity(
    @androidx.room.PrimaryKey
    val singletonId: Int = 0,
    val activePresetId: String,
)

@Entity(tableName = "story_preset_library_groups")
data class StoryPresetLibraryGroupEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val name: String,
    val sortIndex: Int,
)

@Entity(tableName = "story_presets")
data class StoryPresetEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val name: String,
    val modelFamily: String,
    @ColumnInfo(defaultValue = "'[]'")
    val modelTagsJson: String,
    @ColumnInfo(defaultValue = "'story-preset-group-default'")
    val libraryGroupId: String,
    @ColumnInfo(defaultValue = "''")
    val activeVersionId: String,
    @ColumnInfo(defaultValue = "''")
    val authorName: String,
    @ColumnInfo(defaultValue = "''")
    val authorAvatarPath: String,
    @ColumnInfo(defaultValue = "'[]'")
    val authorTagsJson: String,
    @ColumnInfo(defaultValue = "''")
    val description: String,
    val sortIndex: Int,
    val expandedGroupIdsJson: String,
)

/** Independently writable preset documents kept out of the frequently renamed metadata row. */
@Entity(
    tableName = "story_preset_contents",
    primaryKeys = ["presetId", "kind"],
    foreignKeys = [ForeignKey(
        entity = StoryPresetEntity::class,
        parentColumns = ["id"],
        childColumns = ["presetId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("presetId")],
)
data class StoryPresetContentEntity(
    val presetId: String,
    val kind: String,
    val content: String,
)

@Entity(
    tableName = "story_preset_entries",
    primaryKeys = ["presetId", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = StoryPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class StoryPresetEntryEntity(
    val presetId: String,
    val entryId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "story_preset_groups",
    primaryKeys = ["presetId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = StoryPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class StoryPresetGroupEntity(
    val presetId: String,
    val groupId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

/** Runtime-owned fixed entries are rows too, rather than large columns on the preset metadata row. */
@Entity(
    tableName = "story_preset_runtime_entries",
    primaryKeys = ["presetId", "slot"],
    foreignKeys = [
        ForeignKey(
            entity = StoryPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class StoryPresetRuntimeEntryEntity(
    val presetId: String,
    val slot: String,
    val contentOverride: String,
    val enabled: Boolean,
)

@Entity(
    tableName = "story_preset_versions",
    primaryKeys = ["presetId", "versionId"],
    foreignKeys = [
        ForeignKey(
            entity = StoryPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class StoryPresetVersionEntity(
    val presetId: String,
    val versionId: String,
    val versionNumber: Int,
    @ColumnInfo(defaultValue = "''")
    val name: String,
    val createdAtEpochMs: Long,
    val expandedGroupIdsJson: String,
)

@Entity(
    tableName = "story_preset_version_contents",
    primaryKeys = ["presetId", "versionId", "kind"],
    foreignKeys = [ForeignKey(
        entity = StoryPresetVersionEntity::class,
        parentColumns = ["presetId", "versionId"],
        childColumns = ["presetId", "versionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["presetId", "versionId"])],
)
data class StoryPresetVersionContentEntity(
    val presetId: String,
    val versionId: String,
    val kind: String,
    val content: String,
)

@Entity(
    tableName = "story_preset_version_entries",
    primaryKeys = ["presetId", "versionId", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = StoryPresetVersionEntity::class,
            parentColumns = ["presetId", "versionId"],
            childColumns = ["presetId", "versionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId", "versionId"])],
)
data class StoryPresetVersionEntryEntity(
    val presetId: String,
    val versionId: String,
    val entryId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "story_preset_version_groups",
    primaryKeys = ["presetId", "versionId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = StoryPresetVersionEntity::class,
            parentColumns = ["presetId", "versionId"],
            childColumns = ["presetId", "versionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId", "versionId"])],
)
data class StoryPresetVersionGroupEntity(
    val presetId: String,
    val versionId: String,
    val groupId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "story_preset_version_runtime_entries",
    primaryKeys = ["presetId", "versionId", "slot"],
    foreignKeys = [
        ForeignKey(
            entity = StoryPresetVersionEntity::class,
            parentColumns = ["presetId", "versionId"],
            childColumns = ["presetId", "versionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId", "versionId"])],
)
data class StoryPresetVersionRuntimeEntryEntity(
    val presetId: String,
    val versionId: String,
    val slot: String,
    val contentOverride: String,
    val enabled: Boolean,
)

data class StoryPresetVersionRecord(
    @Embedded
    val version: StoryPresetVersionEntity,
    @Relation(parentColumn = "versionId", entityColumn = "versionId")
    val contents: List<StoryPresetVersionContentEntity>,
    @Relation(
        parentColumn = "versionId",
        entityColumn = "versionId",
    )
    val entries: List<StoryPresetVersionEntryEntity>,
    @Relation(
        parentColumn = "versionId",
        entityColumn = "versionId",
    )
    val groups: List<StoryPresetVersionGroupEntity>,
    @Relation(
        parentColumn = "versionId",
        entityColumn = "versionId",
    )
    val runtimeEntries: List<StoryPresetVersionRuntimeEntryEntity>,
)

/** Heavy child rows are only materialized when one preset is opened or selected for a turn. */
data class StoryPresetRecord(
    @Embedded
    val preset: StoryPresetEntity,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val contents: List<StoryPresetContentEntity>,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val entries: List<StoryPresetEntryEntity>,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val groups: List<StoryPresetGroupEntity>,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val runtimeEntries: List<StoryPresetRuntimeEntryEntity>,
)

/** Lightweight row used by the preset manager; no prompt payload is read for the list screen. */
data class StoryPresetSummaryRecord(
    val id: String,
    val name: String,
    val modelFamily: String,
    val modelTagsJson: String,
    val libraryGroupId: String,
    val activeVersionId: String,
    val authorName: String,
    val authorAvatarPath: String,
    val authorTagsJson: String,
    val description: String,
    val timelineJson: String,
    val activeVersionNumber: Int,
    val sortIndex: Int,
    val entryCount: Int,
)

data class StoryPresetVersionSummaryRecord(
    val id: String,
    val number: Int,
    val name: String,
    val createdAtEpochMs: Long,
    val entryCount: Int,
)
