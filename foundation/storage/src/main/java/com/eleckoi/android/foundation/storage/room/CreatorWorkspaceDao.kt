package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface CreatorWorkspaceDao {
    @Transaction
    @Query("SELECT * FROM creator_workspaces ORDER BY updatedAt DESC, id")
    fun records(): List<CreatorWorkspaceRecord>

    @Query("SELECT id FROM creator_workspaces")
    fun ids(): List<String>

    @Upsert
    fun upsertWorkspace(workspace: CreatorWorkspaceEntity)

    @Upsert
    fun upsertCharacterRoots(roots: List<CreatorWorkspaceCharacterRootEntity>)

    @Upsert
    fun upsertFiles(files: List<CreatorWorkspaceFileEntity>)

    @Upsert
    fun upsertConversations(conversations: List<CreatorWorkspaceConversationEntity>)

    @Query("DELETE FROM creator_workspace_character_roots WHERE workspaceId = :workspaceId AND id IN (:rootIds)")
    fun deleteCharacterRoots(workspaceId: String, rootIds: List<String>)

    @Query("DELETE FROM creator_workspace_files WHERE workspaceId = :workspaceId AND path IN (:paths)")
    fun deleteFiles(workspaceId: String, paths: List<String>)

    @Query("DELETE FROM creator_workspace_conversations WHERE workspaceId = :workspaceId AND id IN (:conversationIds)")
    fun deleteConversations(workspaceId: String, conversationIds: List<String>)

    @Query("DELETE FROM creator_workspaces WHERE id IN (:workspaceIds)")
    fun deleteWorkspaces(workspaceIds: List<String>)

    @Transaction
    fun replaceAll(records: List<CreatorWorkspaceRecord>) {
        replaceAllKnown(records(), records)
    }

    @Transaction
    fun replaceAllKnown(
        current: List<CreatorWorkspaceRecord>,
        records: List<CreatorWorkspaceRecord>,
    ) {
        val currentById = current.associateBy { it.workspace.id }
        val retained = records.mapTo(mutableSetOf()) { it.workspace.id }
        val removed = currentById.keys.filterNot(retained::contains)
        if (removed.isNotEmpty()) deleteWorkspaces(removed)
        records.forEach { record ->
            val workspaceId = record.workspace.id
            val previous = currentById[workspaceId]
            if (previous?.workspace != record.workspace) upsertWorkspace(record.workspace)

            val oldFiles = previous?.files.orEmpty().associateBy(CreatorWorkspaceFileEntity::path)
            val filePaths = record.files.mapTo(mutableSetOf()) { it.path }
            val removedFiles = oldFiles.keys.filterNot(filePaths::contains)
            if (removedFiles.isNotEmpty()) deleteFiles(workspaceId, removedFiles)
            val changedFiles = record.files.filter { it != oldFiles[it.path] }
            if (changedFiles.isNotEmpty()) upsertFiles(changedFiles)

            val oldRoots = previous?.characterRoots.orEmpty()
                .associateBy(CreatorWorkspaceCharacterRootEntity::id)
            val rootIds = record.characterRoots.mapTo(mutableSetOf()) { it.id }
            val removedRoots = oldRoots.keys.filterNot(rootIds::contains)
            if (removedRoots.isNotEmpty()) deleteCharacterRoots(workspaceId, removedRoots)
            val changedRoots = record.characterRoots.filter { it != oldRoots[it.id] }
            if (changedRoots.isNotEmpty()) upsertCharacterRoots(changedRoots)

            val oldConversations = previous?.conversations.orEmpty()
                .associateBy(CreatorWorkspaceConversationEntity::id)
            val conversationIds = record.conversations.mapTo(mutableSetOf()) { it.id }
            val removedConversations = oldConversations.keys.filterNot(conversationIds::contains)
            if (removedConversations.isNotEmpty()) deleteConversations(workspaceId, removedConversations)
            val changedConversations = record.conversations.filter { it != oldConversations[it.id] }
            if (changedConversations.isNotEmpty()) upsertConversations(changedConversations)
        }
    }
}
