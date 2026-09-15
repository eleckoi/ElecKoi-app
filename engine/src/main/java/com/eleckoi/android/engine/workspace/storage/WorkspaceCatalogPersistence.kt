package com.eleckoi.android.engine.workspace.storage

import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.workspace.model.CreatorConversation
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCharacterRoot
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import com.eleckoi.android.foundation.storage.room.CreatorWorkspaceCharacterRootEntity
import com.eleckoi.android.foundation.storage.room.CreatorWorkspaceConversationEntity
import com.eleckoi.android.foundation.storage.room.CreatorWorkspaceEntity
import com.eleckoi.android.foundation.storage.room.CreatorWorkspaceFileEntity
import com.eleckoi.android.foundation.storage.room.CreatorWorkspaceRecord
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal interface WorkspaceCatalogPersistence {
    fun read(): CreatorWorkspaceCatalog?
    fun write(previous: CreatorWorkspaceCatalog?, catalog: CreatorWorkspaceCatalog)
}

internal class JsonWorkspaceCatalogPersistence(
    private val file: File,
    private val persist: (File, String) -> Unit,
) : WorkspaceCatalogPersistence {
    override fun read(): CreatorWorkspaceCatalog? {
        if (!file.isFile) return null
        return ElecKoiJson.decodeFromString(file.readText(Charsets.UTF_8))
    }

    override fun write(previous: CreatorWorkspaceCatalog?, catalog: CreatorWorkspaceCatalog) {
        persist(file, ElecKoiPrettyJson.encodeToString(catalog))
    }
}

internal class RoomWorkspaceCatalogPersistence(
    private val database: ElecKoiDatabase,
) : WorkspaceCatalogPersistence {
    override fun read(): CreatorWorkspaceCatalog = CreatorWorkspaceCatalog(
        workspaces = database.creatorWorkspaceDao().records().map(::fromRecord),
    )

    override fun write(previous: CreatorWorkspaceCatalog?, catalog: CreatorWorkspaceCatalog) {
        database.runInTransaction {
            val dao = database.creatorWorkspaceDao()
            val records = catalog.workspaces.map(::toRecord)
            if (previous == null) {
                dao.replaceAll(records)
            } else {
                dao.replaceAllKnown(previous.workspaces.map(::toRecord), records)
            }
        }
    }

    private fun toRecord(workspace: CreatorWorkspace) = CreatorWorkspaceRecord(
        workspace = CreatorWorkspaceEntity(
            id = workspace.id,
            schemaVersion = workspace.schemaVersion,
            name = workspace.name,
            linkedCharacterId = workspace.linkedCharacterId,
            characterOwned = workspace.characterOwned,
            primaryCharacterRootId = workspace.primaryCharacterRootId,
            previewEntryFile = workspace.previewEntryFile,
            createdAt = workspace.createdAt,
            updatedAt = workspace.updatedAt,
            totalBytes = workspace.totalBytes,
            latestCheckpointId = workspace.latestCheckpointId,
            permissionMode = workspace.permissionMode.name,
            activeConversationId = workspace.activeConversationId,
        ),
        files = workspace.files.mapIndexed { index, path ->
            CreatorWorkspaceFileEntity(
                workspaceId = workspace.id,
                path = path,
                sortIndex = index,
            )
        },
        characterRoots = workspace.characterRoots.map { root ->
            CreatorWorkspaceCharacterRootEntity(
                workspaceId = workspace.id,
                id = root.id,
                characterId = root.characterId,
                alias = root.alias,
                access = root.access.name,
            )
        },
        conversations = workspace.conversations.map { conversation ->
            CreatorWorkspaceConversationEntity(
                workspaceId = workspace.id,
                id = conversation.id,
                title = conversation.title,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
            )
        },
    )

    private fun fromRecord(record: CreatorWorkspaceRecord): CreatorWorkspace {
        val row = record.workspace
        return CreatorWorkspace(
            schemaVersion = row.schemaVersion,
            id = row.id,
            name = row.name,
            linkedCharacterId = row.linkedCharacterId,
            characterOwned = row.characterOwned,
            primaryCharacterRootId = row.primaryCharacterRootId,
            characterRoots = record.characterRoots.map { root ->
                CreatorWorkspaceCharacterRoot(
                    id = root.id,
                    characterId = root.characterId,
                    alias = root.alias,
                    access = CreatorWorkspaceRootAccess.valueOf(root.access),
                )
            },
            previewEntryFile = row.previewEntryFile,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            files = record.files.sortedBy(CreatorWorkspaceFileEntity::sortIndex).map { it.path },
            totalBytes = row.totalBytes,
            latestCheckpointId = row.latestCheckpointId,
            permissionMode = AgentPermissionMode.valueOf(row.permissionMode),
            conversations = record.conversations.map { conversation ->
                CreatorConversation(
                    id = conversation.id,
                    title = conversation.title,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                )
            },
            activeConversationId = row.activeConversationId,
        )
    }
}
