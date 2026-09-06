package com.eleckoi.android.engine.workspace.storage

import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.withNormalizedCharacterRoots
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Non-thread-safe manifest/catalog store. The repository owns the transaction lock.
 */
internal class WorkspaceCatalogStore(
    private val paths: WorkspacePathGuard,
    private val atomicFiles: AtomicWorkspaceFileStore,
    private val persistence: WorkspaceCatalogPersistence = JsonWorkspaceCatalogPersistence(
        paths.catalogFile,
        atomicFiles::writeJson,
    ),
) {
    constructor(
        paths: WorkspacePathGuard,
        atomicFiles: AtomicWorkspaceFileStore,
        persistCatalog: (File, String) -> Unit,
    ) : this(
        paths,
        atomicFiles,
        JsonWorkspaceCatalogPersistence(paths.catalogFile, persistCatalog),
    )

    private var cachedCatalog: CreatorWorkspaceCatalog? = null

    fun catalog(): CreatorWorkspaceCatalog {
        return cachedCatalog ?: loadCatalog().also { cachedCatalog = it }
    }

    fun find(workspaceId: String): CreatorWorkspace? {
        if (workspaceId.isBlank()) return null
        return catalog().workspaces.firstOrNull { it.id == workspaceId }
    }

    fun requireWorkspace(workspaceId: String): CreatorWorkspace {
        val workspace = find(workspaceId) ?: error("创作工作区不存在")
        require(paths.isSafeWorkspaceDirectory(workspace)) { "创作工作区目录无效" }
        paths.ensureEmptyProjectDirectory(workspace)
        return workspace
    }

    fun invalidate() {
        cachedCatalog = null
    }

    fun commitWorkspace(workspace: CreatorWorkspace) {
        atomicFiles.writeJson(
            File(paths.workspaceDirectory(workspace), WorkspacePathGuard.ManifestFileName),
            ElecKoiPrettyJson.encodeToString(workspace.withoutEmbeddedRuntimeData()),
        )
        val current = catalog()
        commitCatalog(
            current.copy(
                workspaces = current.workspaces.map { existing ->
                    if (existing.id == workspace.id) workspace else existing
                },
            ),
        )
    }

    fun commitCatalog(next: CreatorWorkspaceCatalog) {
        paths.root.mkdirs()
        persistence.write(
            previous = cachedCatalog?.withoutEmbeddedRuntimeData(),
            catalog = next.withoutEmbeddedRuntimeData(),
        )
        cachedCatalog = next
    }

    private fun loadCatalog(): CreatorWorkspaceCatalog {
        val decoded = runCatching {
            persistence.read()
        }.getOrNull()
        // A manifest is the recoverable file-side half of a workspace write. Production writes
        // the reconstructed index back to Room; the JSON persistence exists only in JVM tests.
        val manifestsById = paths.workspaceDirectoriesForDiscovery()
            .asSequence()
            .mapNotNull { directory ->
                runCatching {
                    val workspace = ElecKoiJson.decodeFromString<CreatorWorkspace>(
                        File(directory, WorkspacePathGuard.ManifestFileName).readText(Charsets.UTF_8),
                    )
                    workspace.withNormalizedCharacterRoots().takeIf {
                        paths.isSafeStorageId(it.id) &&
                            paths.isSafeWorkspaceDirectory(it) &&
                            paths.workspaceDirectory(it).canonicalFile == directory.canonicalFile
                    }
                }.getOrNull()
            }
            .associateBy(CreatorWorkspace::id)
        val fromCatalog = decoded?.workspaces.orEmpty().mapNotNull { indexed ->
            manifestsById[indexed.id]
                ?: indexed.takeIf {
                    paths.isSafeStorageId(it.id) && paths.isSafeWorkspaceDirectory(it)
                }?.withNormalizedCharacterRoots()
        }
        val knownIds = fromCatalog.mapTo(mutableSetOf(), CreatorWorkspace::id)
        val recovered = manifestsById.values.filter { it.id !in knownIds }
        val loaded = CreatorWorkspaceCatalog(
            workspaces = (fromCatalog + recovered).distinctBy(CreatorWorkspace::id),
        )
        val persistedLoaded = loaded.withoutEmbeddedRuntimeData()
        val persistedDecoded = decoded?.withoutEmbeddedRuntimeData()
        if (persistedDecoded?.workspaces != persistedLoaded.workspaces) {
            try {
                persistence.write(persistedDecoded, persistedLoaded)
            } catch (error: Exception) {
                throw ElecKoiDataException(
                    "工作区目录恢复后无法写回索引",
                    error,
                )
            }
        }
        return loaded
    }

    private fun CreatorWorkspaceCatalog.withoutEmbeddedRuntimeData(): CreatorWorkspaceCatalog = copy(
        workspaces = workspaces.map { it.withoutEmbeddedRuntimeData() },
    )

    private fun CreatorWorkspace.withoutEmbeddedRuntimeData(): CreatorWorkspace = copy(
        conversations = conversations.map { conversation ->
            conversation.copy(timeline = emptyList())
        },
    )
}

@Serializable
internal data class CreatorWorkspaceCatalog(
    val schemaVersion: Int = 1,
    val workspaces: List<CreatorWorkspace> = emptyList(),
)
