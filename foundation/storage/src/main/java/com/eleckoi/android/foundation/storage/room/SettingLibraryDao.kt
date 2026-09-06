package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingLibraryDao {
    @Transaction
    @Query("SELECT * FROM setting_libraries WHERE characterId = :characterId LIMIT 1")
    fun library(characterId: String): SettingLibraryRecord?

    @Transaction
    @Query("SELECT * FROM setting_libraries WHERE characterId = :characterId LIMIT 1")
    fun libraryFlow(characterId: String): Flow<SettingLibraryRecord?>

    @Query("SELECT * FROM setting_libraries WHERE characterId = :characterId LIMIT 1")
    fun metadata(characterId: String): SettingLibraryEntity?

    @Query("SELECT COUNT(*) FROM setting_library_entries WHERE characterId = :characterId")
    fun entryCount(characterId: String): Int

    @Query("SELECT COUNT(*) FROM setting_library_groups WHERE characterId = :characterId")
    fun groupCount(characterId: String): Int

    /** Row-bounded creator search. Callers request one extra row to derive hasMore. */
    @Query(
        """
        SELECT * FROM setting_library_entries
        WHERE characterId = :characterId
          AND (:query = '' OR payloadJson LIKE :query ESCAPE '\')
          AND (sortIndex > :afterSortIndex OR (sortIndex = :afterSortIndex AND entryId > :afterId))
        ORDER BY sortIndex ASC, entryId ASC
        LIMIT :limit
        """,
    )
    fun entryPage(
        characterId: String,
        query: String,
        afterSortIndex: Int,
        afterId: String,
        limit: Int,
    ): List<SettingLibraryEntryEntity>

    @Query(
        """
        SELECT * FROM setting_library_groups
        WHERE characterId = :characterId
          AND (:query = '' OR payloadJson LIKE :query ESCAPE '\')
          AND (sortIndex > :afterSortIndex OR (sortIndex = :afterSortIndex AND groupId > :afterId))
        ORDER BY sortIndex ASC, groupId ASC
        LIMIT :limit
        """,
    )
    fun groupPage(
        characterId: String,
        query: String,
        afterSortIndex: Int,
        afterId: String,
        limit: Int,
    ): List<SettingLibraryGroupEntity>

    @Query(
        "SELECT * FROM setting_library_entries WHERE characterId = :characterId AND entryId = :entryId LIMIT 1",
    )
    fun entry(characterId: String, entryId: String): SettingLibraryEntryEntity?

    @Transaction
    fun upsert(library: SettingLibraryRecord) {
        val characterId = library.library.characterId
        val plan = settingLibraryWritePlan(library(characterId), library)
        plan.metadata?.let(::upsertMetadata)
        plan.deleteVersionIds.forEachDeleteBatch { deleteVersionRows(characterId, it) }
        plan.deleteEntryIds.forEachDeleteBatch { deleteEntryRows(characterId, it) }
        plan.deleteGroupIds.forEachDeleteBatch { deleteGroupRows(characterId, it) }
        plan.deleteVersionEntries.forEach { (versionId, entryIds) ->
            entryIds.forEachDeleteBatch { deleteVersionEntryRows(characterId, versionId, it) }
        }
        plan.deleteVersionGroups.forEach { (versionId, groupIds) ->
            groupIds.forEachDeleteBatch { deleteVersionGroupRows(characterId, versionId, it) }
        }
        if (plan.upsertVersions.isNotEmpty()) upsertVersionRows(plan.upsertVersions)
        if (plan.upsertEntries.isNotEmpty()) upsertEntryRows(plan.upsertEntries)
        if (plan.upsertGroups.isNotEmpty()) upsertGroupRows(plan.upsertGroups)
        if (plan.upsertVersionEntries.isNotEmpty()) upsertVersionEntryRows(plan.upsertVersionEntries)
        if (plan.upsertVersionGroups.isNotEmpty()) upsertVersionGroupRows(plan.upsertVersionGroups)
        deleteUnusedContents(characterId)
    }

    @Upsert
    fun upsertMetadata(library: SettingLibraryEntity)

    @Transaction
    fun upsertEntryRows(entries: List<SettingLibraryEntryEntity>) {
        val links = entries.map {
            val revision = persistContent(it.characterId, it.entryId, it.payloadJson)
            SettingLibraryEntryLinkEntity(it.characterId, it.entryId, it.sortIndex, revision)
        }
        if (links.isNotEmpty()) upsertEntryLinks(links)
    }

    @Upsert
    fun upsertGroupRows(groups: List<SettingLibraryGroupEntity>)

    @Upsert
    fun upsertVersionRows(versions: List<SettingLibraryVersionEntity>)

    @Transaction
    fun upsertVersionEntryRows(entries: List<SettingLibraryVersionEntryEntity>) {
        val links = entries.map {
            val revision = persistContent(it.characterId, it.entryId, it.payloadJson)
            SettingLibraryVersionEntryLinkEntity(it.characterId, it.versionId, it.entryId, it.sortIndex, revision)
        }
        if (links.isNotEmpty()) upsertVersionEntryLinks(links)
    }

    fun persistContent(characterId: String, entryId: String, payload: String): String {
        revisionForContent(characterId, entryId, payload)?.let { return it }
        val revision = UUID.randomUUID().toString()
        val content = SettingEntryContentEntity(characterId, entryId, revision, payload)
        check(insertContent(content) != -1L) { "设定内容修订 ID 发生冲突" }
        return revision
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertContent(content: SettingEntryContentEntity): Long

    @Query("""SELECT revisionId FROM setting_entry_contents
        WHERE characterId = :characterId AND entryId = :entryId AND payloadJson = :payload
        LIMIT 1""")
    fun revisionForContent(characterId: String, entryId: String, payload: String): String?

    @Upsert
    fun upsertEntryLinks(entries: List<SettingLibraryEntryLinkEntity>)

    @Upsert
    fun upsertVersionEntryLinks(entries: List<SettingLibraryVersionEntryLinkEntity>)

    @Query("""DELETE FROM setting_entry_contents WHERE characterId = :characterId
        AND NOT EXISTS (SELECT 1 FROM setting_library_entry_links AS link
            WHERE link.characterId = setting_entry_contents.characterId
              AND link.entryId = setting_entry_contents.entryId AND link.revisionId = setting_entry_contents.revisionId)
        AND NOT EXISTS (SELECT 1 FROM setting_library_version_entry_links AS link
            WHERE link.characterId = setting_entry_contents.characterId
              AND link.entryId = setting_entry_contents.entryId AND link.revisionId = setting_entry_contents.revisionId)""")
    fun deleteUnusedContents(characterId: String)

    @Upsert
    fun upsertVersionGroupRows(groups: List<SettingLibraryVersionGroupEntity>)

    @Query("DELETE FROM setting_library_entry_links WHERE characterId = :characterId AND entryId IN (:entryIds)")
    fun deleteEntryRows(characterId: String, entryIds: List<String>)

    @Query("DELETE FROM setting_library_groups WHERE characterId = :characterId AND groupId IN (:groupIds)")
    fun deleteGroupRows(characterId: String, groupIds: List<String>)

    @Query("DELETE FROM setting_library_versions WHERE characterId = :characterId AND versionId IN (:versionIds)")
    fun deleteVersionRows(characterId: String, versionIds: List<String>)

    @Query("DELETE FROM setting_library_version_entry_links WHERE characterId = :characterId AND versionId = :versionId AND entryId IN (:entryIds)")
    fun deleteVersionEntryRows(characterId: String, versionId: String, entryIds: List<String>)

    @Query("DELETE FROM setting_library_version_groups WHERE characterId = :characterId AND versionId = :versionId AND groupId IN (:groupIds)")
    fun deleteVersionGroupRows(characterId: String, versionId: String, groupIds: List<String>)

    @Query("DELETE FROM setting_libraries WHERE characterId IN (:characterIds)")
    fun deleteForCharacters(characterIds: List<String>)

    @Query("DELETE FROM setting_libraries WHERE characterId NOT IN (:characterIds)")
    fun deleteExceptCharacters(characterIds: List<String>)

    @Query("DELETE FROM setting_libraries")
    fun deleteAll()
}

private const val SETTING_LIBRARY_DELETE_BATCH_SIZE = 900

private inline fun List<String>.forEachDeleteBatch(delete: (List<String>) -> Unit) {
    chunked(SETTING_LIBRARY_DELETE_BATCH_SIZE).forEach(delete)
}
