package com.eleckoi.android.foundation.storage.room

import androidx.room.Embedded
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Relation

@Entity(
    tableName = "setting_libraries",
    primaryKeys = ["characterId"],
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SettingLibraryEntity(
    val characterId: String,
    val name: String,
    val activeVersionId: String,
    val listAllExpanded: Boolean,
    val expandedGroupIdsJson: String,
    val promptPositionsJson: String,
    val updatedAt: String,
)

@DatabaseView(
    viewName = "setting_library_entries",
    value = """SELECT link.characterId, link.entryId, link.sortIndex, content.payloadJson
        FROM setting_library_entry_links AS link
        JOIN setting_entry_contents AS content ON content.characterId = link.characterId
          AND content.entryId = link.entryId AND content.revisionId = link.revisionId""",
)
data class SettingLibraryEntryEntity(
    val characterId: String,
    val entryId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "setting_library_groups",
    primaryKeys = ["characterId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = SettingLibraryEntity::class,
            parentColumns = ["characterId"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SettingLibraryGroupEntity(
    val characterId: String,
    val groupId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "setting_library_versions",
    primaryKeys = ["characterId", "versionId"],
    foreignKeys = [
        ForeignKey(
            entity = SettingLibraryEntity::class,
            parentColumns = ["characterId"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SettingLibraryVersionEntity(
    val characterId: String,
    val versionId: String,
    val sortIndex: Int,
    val name: String,
    val listAllExpanded: Boolean,
    val expandedGroupIdsJson: String,
    val promptPositionsJson: String,
    val createdAt: String,
    val updatedAt: String,
)

@DatabaseView(
    viewName = "setting_library_version_entries",
    value = """SELECT link.characterId, link.versionId, link.entryId, link.sortIndex, content.payloadJson
        FROM setting_library_version_entry_links AS link
        JOIN setting_entry_contents AS content ON content.characterId = link.characterId
          AND content.entryId = link.entryId AND content.revisionId = link.revisionId""",
)
data class SettingLibraryVersionEntryEntity(
    val characterId: String,
    val versionId: String,
    val entryId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

@Entity(
    tableName = "setting_library_version_groups",
    primaryKeys = ["characterId", "versionId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = SettingLibraryVersionEntity::class,
            parentColumns = ["characterId", "versionId"],
            childColumns = ["characterId", "versionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SettingLibraryVersionGroupEntity(
    val characterId: String,
    val versionId: String,
    val groupId: String,
    val sortIndex: Int,
    val payloadJson: String,
)

/**
 * Complete Room representation of one setting library.
 *
 * Entries are deliberately stored as child rows instead of one JSON array on the parent row. Android's
 * CursorWindow must be able to materialize each row independently, so this shape scales with the number
 * of imported SillyTavern world-book entries without creating one oversized cursor row.
 */
data class SettingLibraryRecord(
    @Embedded
    val library: SettingLibraryEntity,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val entries: List<SettingLibraryEntryEntity>,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val groups: List<SettingLibraryGroupEntity>,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val versions: List<SettingLibraryVersionEntity>,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val versionEntries: List<SettingLibraryVersionEntryEntity>,
    @Relation(parentColumn = "characterId", entityColumn = "characterId")
    val versionGroups: List<SettingLibraryVersionGroupEntity>,
)
