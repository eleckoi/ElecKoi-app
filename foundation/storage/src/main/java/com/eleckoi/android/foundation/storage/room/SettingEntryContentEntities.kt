package com.eleckoi.android.foundation.storage.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Immutable content revisions shared by current editing state and saved versions of one entry. */
@Entity(
    tableName = "setting_entry_contents",
    primaryKeys = ["characterId", "entryId", "revisionId"],
    foreignKeys = [ForeignKey(
        entity = SettingLibraryEntity::class,
        parentColumns = ["characterId"], childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SettingEntryContentEntity(
    val characterId: String,
    val entryId: String,
    val revisionId: String,
    val payloadJson: String,
)

@Entity(
    tableName = "setting_library_entry_links",
    primaryKeys = ["characterId", "entryId"],
    foreignKeys = [ForeignKey(
        entity = SettingLibraryEntity::class,
        parentColumns = ["characterId"], childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE,
    ), ForeignKey(
        entity = SettingEntryContentEntity::class,
        parentColumns = ["characterId", "entryId", "revisionId"],
        childColumns = ["characterId", "entryId", "revisionId"],
    )],
    indices = [
        Index(value = ["characterId", "entryId", "revisionId"]),
        Index(value = ["characterId", "sortIndex", "entryId"]),
    ],
)
data class SettingLibraryEntryLinkEntity(
    val characterId: String,
    val entryId: String,
    val sortIndex: Int,
    val revisionId: String,
)

@Entity(
    tableName = "setting_library_version_entry_links",
    primaryKeys = ["characterId", "versionId", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = SettingLibraryVersionEntity::class,
            parentColumns = ["characterId", "versionId"], childColumns = ["characterId", "versionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SettingEntryContentEntity::class,
            parentColumns = ["characterId", "entryId", "revisionId"],
            childColumns = ["characterId", "entryId", "revisionId"],
        ),
    ],
    indices = [
        Index(value = ["characterId", "entryId", "revisionId"]),
        Index(value = ["characterId", "versionId", "sortIndex", "entryId"]),
    ],
)
data class SettingLibraryVersionEntryLinkEntity(
    val characterId: String,
    val versionId: String,
    val entryId: String,
    val sortIndex: Int,
    val revisionId: String,
)
