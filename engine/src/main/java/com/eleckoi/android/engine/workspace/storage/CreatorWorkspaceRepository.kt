package com.eleckoi.android.engine.workspace.storage

import android.content.Context
import com.eleckoi.android.engine.agent.api.AgentPermissionMode
import com.eleckoi.android.engine.workspace.model.CreatorConversation
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineItem
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCheckpoint
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceFile
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCharacterRoot
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.engine.workspace.model.creatorCharacterRootId
import com.eleckoi.android.engine.workspace.model.withNormalizedCharacterRoots
import com.eleckoi.android.engine.workspace.storage.conversation.WorkspaceConversationStore
import com.eleckoi.android.engine.workspace.storage.media.CreatorMediaAssetStore
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/**
 * Transaction owner for editable creator workspaces.
 *
 * Layout and security checks, catalog persistence, project-tree I/O and
 * checkpoints are delegated to focused stores. Those stores intentionally do
 * not own locks: every compound operation remains serialized by [mutex].
 */
class CreatorWorkspaceRepository constructor(
    root: File,
    private val now: () -> Instant,
    private val newId: () -> String,
    moveDirectory: (source: File, destination: File) -> Unit = ::moveWorkspaceDirectoryAtomically,
    database: ElecKoiDatabase? = null,
) {
    constructor(context: Context, database: ElecKoiDatabase) : this(
        root = File(context.filesDir, WorkspacePathGuard.RootDirectoryName),
        now = Instant::now,
        newId = { UUID.randomUUID().toString() },
        database = database,
    )

    private val paths = WorkspacePathGuard(root)
    private val atomicFiles = AtomicWorkspaceFileStore()
    private val projects = WorkspaceProjectStore(paths, atomicFiles)
    private val catalog = WorkspaceCatalogStore(
        paths = paths,
        atomicFiles = atomicFiles,
        persistence = database?.let(::RoomWorkspaceCatalogPersistence)
            ?: JsonWorkspaceCatalogPersistence(paths.catalogFile, atomicFiles::writeJson),
    )
    private val checkpoints = WorkspaceCheckpointStore(
        paths = paths,
        projects = projects,
        catalog = catalog,
        atomicFiles = atomicFiles,
        now = now,
        newId = newId,
        moveDirectory = moveDirectory,
    )
    private val conversations = WorkspaceConversationStore(
        catalog = catalog,
        paths = paths,
        now = now,
        newId = newId,
        schemaVersion = CurrentWorkspaceSchemaVersion,
    )
    private val mediaAssets = CreatorMediaAssetStore(paths, catalog)
    private val mutex = Mutex()
    private val characterWorkspaceMutex = Mutex()

    init {
        paths.initialize()
    }

    suspend fun list(): List<CreatorWorkspace> = transaction {
        catalog.catalog().workspaces.sortedByDescending(CreatorWorkspace::updatedAt)
    }

    /** Reloads externally restored workspace files and verifies them against their manifests. */
    suspend fun reloadAfterBackupRestore(): List<CreatorWorkspace> = withContext(Dispatchers.IO) {
        mutex.withLock {
            catalog.invalidate()
            val restored = catalog.catalog().workspaces
            restored.forEach { workspace ->
                paths.ensureEmptyProjectDirectory(workspace)
                val state = projects.inspect(workspace)
                require(state.files.map(CreatorWorkspaceFile::path) == workspace.files) {
                    "工作区备份缺少项目文件：${workspace.name}"
                }
                require(state.totalBytes == workspace.totalBytes) {
                    "工作区备份项目大小不一致：${workspace.name}"
                }
            }
            restored
        }
    }

    suspend fun create(
        name: String,
        linkedCharacterId: String? = null,
        characterOwned: Boolean = false,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalizedName = paths.validateName(name)
            val normalizedCharacterId = paths.validateCharacterId(linkedCharacterId)
            require(!characterOwned || normalizedCharacterId != null) {
                "角色工作区必须关联角色"
            }
            val workspaceId = createUniqueWorkspaceId()
            val createdAt = now().toString()
            val stagingDirectory = File(paths.stagingRoot, workspaceId)
            val destinationDirectory = paths.workspaceDirectory(
                workspaceId = workspaceId,
                linkedCharacterId = normalizedCharacterId,
                characterOwned = characterOwned,
            )

            require(!Files.exists(destinationDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "工作区编号冲突，请重试"
            }
            require(!Files.exists(stagingDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "工作区临时编号冲突，请重试"
            }
            require(stagingDirectory.mkdir()) { "无法创建工作区临时目录" }
            try {
                require(paths.isDirectChildDirectory(paths.stagingRoot, stagingDirectory)) {
                    "工作区临时目录无效"
                }
                val stagingProject = File(stagingDirectory, WorkspacePathGuard.ProjectDirectoryName)
                require(stagingProject.mkdir()) { "无法创建工作区项目目录" }
                val projectState = projects.inspect(stagingProject)
                val conversation = CreatorConversation(
                    id = workspaceId,
                    title = DefaultConversationTitle,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                )
                val workspace = CreatorWorkspace(
                    id = workspaceId,
                    name = normalizedName,
                    linkedCharacterId = normalizedCharacterId,
                    characterOwned = characterOwned,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    files = projectState.files.map(CreatorWorkspaceFile::path),
                    totalBytes = projectState.totalBytes,
                    conversations = listOf(conversation),
                    activeConversationId = conversation.id,
                ).withNormalizedCharacterRoots()
                atomicFiles.writeJson(
                    File(stagingDirectory, WorkspacePathGuard.ManifestFileName),
                    ElecKoiPrettyJson.encodeToString(workspace),
                )
                if (normalizedCharacterId != null && characterOwned) {
                    val container = paths.ensureCharacterContainer(normalizedCharacterId)
                    require(destinationDirectory.parentFile?.canonicalFile == container.canonicalFile) {
                        "角色工作区路径无效"
                    }
                }
                require(stagingDirectory.renameTo(destinationDirectory)) { "无法创建创作工作区" }
                try {
                    catalog.commitCatalog(
                        catalog.catalog().copy(workspaces = catalog.catalog().workspaces + workspace),
                    )
                } catch (error: Throwable) {
                    paths.deleteTreeNoFollow(destinationDirectory)
                    throw error
                }
                workspace
            } finally {
                paths.deleteTreeNoFollow(stagingDirectory)
            }
        }
    }

    /** Returns the one persistent physical runtime workspace owned by a character. */
    suspend fun ensureCharacterWorkspace(
        characterId: String,
        name: String,
    ): CreatorWorkspace = characterWorkspaceMutex.withLock {
        val normalizedCharacterId = paths.validateCharacterId(characterId)
            ?: error("角色关联编号不能为空")
        list().firstOrNull { workspace ->
            workspace.linkedCharacterId == normalizedCharacterId &&
                workspace.characterOwned
        }?.let { return@withLock it }
        create(
            name = name,
            linkedCharacterId = normalizedCharacterId,
            characterOwned = true,
        )
    }

    suspend fun ensureCharacterContainer(characterId: String) = transaction {
        paths.ensureCharacterContainer(characterId)
    }

    suspend fun deleteCharacterContainer(characterId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalizedCharacterId = paths.validateCharacterId(characterId)
                ?: error("角色关联编号不能为空")
            require(
                catalog.catalog().workspaces.none {
                    it.linkedCharacterId == normalizedCharacterId && it.characterOwned
                },
            ) { "角色仍有关联的工作区" }
            val directory = paths.characterContainerDirectory(normalizedCharacterId)
            if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return@withLock
            require(paths.isSafeCharacterContainerDirectory(directory)) { "角色工作区容器无效" }
            paths.deleteTreeNoFollow(directory)
        }
    }

    suspend fun deleteCharacterContainersExcept(
        retainedCharacterIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val retained = retainedCharacterIds.mapNotNull(paths::validateCharacterId).toSet()
            paths.characterWorkspacesRoot.listFiles()
                .orEmpty()
                .filter(paths::isSafeCharacterContainerDirectory)
                .filterNot { it.name in retained }
                .filter { container ->
                    catalog.catalog().workspaces.none {
                        it.linkedCharacterId == container.name && it.characterOwned
                    }
                }
                .forEach(paths::deleteTreeNoFollow)
        }
    }

    fun runtimeProjectPath(workspace: CreatorWorkspace): String = paths.runtimeProjectPath(workspace)

    suspend fun get(workspaceId: String): CreatorWorkspace? = transaction { catalog.find(workspaceId) }

    suspend fun rename(workspaceId: String, name: String): CreatorWorkspace = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workspace = catalog.requireWorkspace(workspaceId)
            val updated = workspace.copy(
                schemaVersion = CurrentWorkspaceSchemaVersion,
                name = paths.validateName(name),
                updatedAt = now().toString(),
            )
            catalog.commitWorkspace(updated)
            updated
        }
    }

    suspend fun attachCharacterRoot(
        workspaceId: String,
        characterId: String,
        alias: String,
        access: CreatorWorkspaceRootAccess = CreatorWorkspaceRootAccess.ReadOnly,
        makePrimary: Boolean = false,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workspace = catalog.requireWorkspace(workspaceId).withNormalizedCharacterRoots()
            require(!workspace.characterOwned) { "角色工作区不能挂载创作角色" }
            val normalizedCharacterId = paths.validateCharacterId(characterId)
                ?: error("角色编号不能为空")
            val rootId = creatorCharacterRootId(normalizedCharacterId)
            val existing = workspace.characterRoots.firstOrNull { it.characterId == normalizedCharacterId }
            val requestedAccess = if (makePrimary) CreatorWorkspaceRootAccess.ReadWrite else access
            val root = (existing ?: CreatorWorkspaceCharacterRoot(
                id = rootId,
                characterId = normalizedCharacterId,
            )).copy(
                id = rootId,
                alias = alias.trim().take(80),
                access = requestedAccess,
            )
            val roots = workspace.characterRoots.filterNot { it.characterId == normalizedCharacterId } + root
            val primaryId = when {
                makePrimary -> root.id
                workspace.primaryCharacterRootId != null -> workspace.primaryCharacterRootId
                else -> null
            }
            commitCharacterRoots(workspace, roots, primaryId)
        }
    }

    suspend fun detachCharacterRoot(workspaceId: String, rootId: String): CreatorWorkspace =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val workspace = catalog.requireWorkspace(workspaceId).withNormalizedCharacterRoots()
                val roots = workspace.characterRoots.filterNot { it.id == rootId }
                require(roots.size != workspace.characterRoots.size) { "创作角色根不存在" }
                val primaryId = workspace.primaryCharacterRootId
                    ?.takeIf { it != rootId }
                commitCharacterRoots(workspace, roots, primaryId)
            }
        }

    suspend fun setPrimaryCharacterRoot(workspaceId: String, rootId: String): CreatorWorkspace =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val workspace = catalog.requireWorkspace(workspaceId).withNormalizedCharacterRoots()
                require(workspace.characterRoots.any { it.id == rootId }) { "创作角色根不存在" }
                val roots = workspace.characterRoots.map { root ->
                    when {
                        root.id == rootId -> root.copy(access = CreatorWorkspaceRootAccess.ReadWrite)
                        root.id == workspace.primaryCharacterRootId -> root.copy(
                            access = CreatorWorkspaceRootAccess.ReadOnly,
                        )
                        else -> root
                    }
                }
                commitCharacterRoots(workspace, roots, rootId)
            }
        }

    suspend fun setCharacterRootAccess(
        workspaceId: String,
        rootId: String,
        access: CreatorWorkspaceRootAccess,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workspace = catalog.requireWorkspace(workspaceId).withNormalizedCharacterRoots()
            require(workspace.characterRoots.any { it.id == rootId }) { "创作角色根不存在" }
            require(
                rootId != workspace.primaryCharacterRootId || access == CreatorWorkspaceRootAccess.ReadWrite,
            ) { "主角色必须保持可写" }
            val roots = workspace.characterRoots.map { root ->
                if (root.id == rootId) root.copy(access = access) else root
            }
            commitCharacterRoots(workspace, roots, workspace.primaryCharacterRootId)
        }
    }

    suspend fun detachCharacterRootsFor(characterIds: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (characterIds.isEmpty()) return@withLock
            catalog.catalog().workspaces
                .filterNot { it.characterOwned }
                .forEach { rawWorkspace ->
                    val workspace = rawWorkspace.withNormalizedCharacterRoots()
                    val roots = workspace.characterRoots.filterNot { it.characterId in characterIds }
                    if (roots.size != workspace.characterRoots.size) {
                        val primaryId = workspace.primaryCharacterRootId
                            ?.takeIf { id -> roots.any { it.id == id } }
                        commitCharacterRoots(workspace, roots, primaryId)
                    }
                }
        }
    }

    suspend fun delete(workspaceId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(paths.isSafeStorageId(workspaceId)) { "工作区编号无效" }
            val discarded = File(paths.stagingRoot, "delete-$workspaceId")
            val workspace = catalog.find(workspaceId)
            if (workspace == null) {
                paths.deleteTreeNoFollow(discarded)
                return@withLock
            }
            val source = paths.workspaceDirectory(workspace)
            require(paths.isSafeWorkspaceDirectory(workspace)) { "工作区目录不存在或不安全" }
            paths.deleteTreeNoFollow(discarded)
            require(source.renameTo(discarded)) { "无法暂存待删除的工作区" }
            val originalCatalog = catalog.catalog()
            try {
                catalog.commitCatalog(
                    catalog.catalog().copy(
                        workspaces = catalog.catalog().workspaces.filterNot { it.id == workspace.id },
                    ),
                )
                paths.deleteTreeNoFollow(discarded)
            } catch (error: Throwable) {
                // Keep the owner discoverable when physical cleanup fails, so deletion is retryable.
                runCatching {
                    if (!source.exists()) require(discarded.renameTo(source)) { "无法恢复待删除的工作区目录" }
                    catalog.commitCatalog(originalCatalog)
                }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }

    suspend fun ensureConversation(workspaceId: String): CreatorWorkspace =
        transaction { conversations.ensure(workspaceId, DefaultConversationTitle) }

    suspend fun createConversation(
        workspaceId: String,
        title: String = DefaultConversationTitle,
    ): CreatorWorkspace = transaction { conversations.create(workspaceId, title) }

    suspend fun selectConversation(
        workspaceId: String,
        conversationId: String,
    ): CreatorWorkspace = transaction { conversations.select(workspaceId, conversationId) }

    suspend fun renameConversation(
        workspaceId: String,
        conversationId: String,
        title: String,
    ): CreatorWorkspace = transaction { conversations.rename(workspaceId, conversationId, title) }

    suspend fun saveWorkspacePermissionMode(
        workspaceId: String,
        permissionMode: AgentPermissionMode,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workspace = catalog.requireWorkspace(workspaceId)
            val timestamp = now().toString()
            val updated = workspace.copy(
                schemaVersion = CurrentWorkspaceSchemaVersion,
                updatedAt = timestamp,
                permissionMode = permissionMode,
            )
            catalog.commitWorkspace(updated)
            updated
        }
    }

    suspend fun saveConversationTimeline(
        workspaceId: String,
        conversationId: String,
        timeline: List<CreatorConversationTimelineItem>,
    ): CreatorWorkspace = transaction {
        conversations.saveTimeline(workspaceId, conversationId, timeline)
    }

    suspend fun deleteConversation(
        workspaceId: String,
        conversationId: String,
    ): CreatorWorkspace = transaction { conversations.delete(workspaceId, conversationId) }

    suspend fun readText(workspaceId: String, path: String): String = transaction {
        projects.readText(catalog.requireWorkspace(workspaceId), path)
    }

    suspend fun writeText(
        workspaceId: String,
        path: String,
        content: String,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workspace = catalog.requireWorkspace(workspaceId)
            val state = projects.writeText(workspace, path, content) ?: return@withLock workspace
            commitProjectState(workspace, state)
        }
    }

    /** Creates an author-visible project folder without manufacturing a placeholder file. */
    suspend fun ensureDirectory(
        workspaceId: String,
        path: String,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workspace = catalog.requireWorkspace(workspaceId)
            val state = projects.ensureDirectory(workspace, path) ?: return@withLock workspace
            commitProjectState(workspace.copy(schemaVersion = CurrentWorkspaceSchemaVersion), state)
        }
    }

    suspend fun deletePath(
        workspaceId: String,
        path: String,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workspace = catalog.requireWorkspace(workspaceId)
            val state = projects.deletePath(workspace, path) ?: return@withLock workspace
            commitProjectState(
                workspace.copy(schemaVersion = CurrentWorkspaceSchemaVersion),
                state,
            )
        }
    }

    suspend fun listFiles(workspaceId: String): List<CreatorWorkspaceFile> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workspace = catalog.requireWorkspace(workspaceId)
            val projectState = projects.inspect(workspace)
            val filePaths = projectState.files.map(CreatorWorkspaceFile::path)
            if (workspace.files != filePaths || workspace.totalBytes != projectState.totalBytes) {
                catalog.commitWorkspace(
                    workspace.copy(
                        schemaVersion = CurrentWorkspaceSchemaVersion,
                        updatedAt = now().toString(),
                        files = filePaths,
                        totalBytes = projectState.totalBytes,
                    ),
                )
            }
            projectState.files
        }
    }

    suspend fun readInternalState(workspaceId: String, name: String): String? = transaction {
        projects.readInternalState(catalog.requireWorkspace(workspaceId), name)
    }

    suspend fun writeInternalState(
        workspaceId: String,
        name: String,
        content: String,
    ) = transaction {
        projects.writeInternalState(catalog.requireWorkspace(workspaceId), name, content)
    }

    suspend fun deleteInternalState(workspaceId: String, name: String) = transaction {
        projects.deleteInternalState(catalog.requireWorkspace(workspaceId), name)
    }

    /**
     * Imports an image into the workspace's private media-asset area. These files are intentionally
     * outside `/workspace`: the model addresses them by opaque asset id and cannot overwrite them
     * through ordinary text-file tools.
     */
    suspend fun importCreatorMediaAsset(
        workspaceId: String,
        assetId: String,
        extension: String,
        source: File,
    ): File = transaction { mediaAssets.import(workspaceId, assetId, extension, source) }

    suspend fun creatorMediaAssetFile(workspaceId: String, assetId: String): File? =
        transaction { mediaAssets.find(workspaceId, assetId) }

    suspend fun listCreatorMediaAssetFiles(workspaceId: String): List<File> =
        transaction { mediaAssets.list(workspaceId) }

    suspend fun deleteCreatorMediaAsset(workspaceId: String, assetId: String) =
        transaction { mediaAssets.delete(workspaceId, assetId) }

    fun projectDirectoryOrNull(workspaceId: String): File? {
        if (!paths.isSafeStorageId(workspaceId)) return null
        val workspace = runCatching { catalog.requireWorkspace(workspaceId) }.getOrNull() ?: return null
        if (!paths.isSafeWorkspaceDirectory(workspace)) return null
        return paths.projectDirectory(workspace).takeIf(paths::isDirectoryNoFollow)
    }

    suspend fun checkpoint(
        workspaceId: String,
        label: String? = null,
    ): CreatorWorkspaceCheckpoint = transaction {
        checkpoints.create(catalog.requireWorkspace(workspaceId), label)
    }

    suspend fun listCheckpoints(
        workspaceId: String,
    ): List<CreatorWorkspaceCheckpoint> = transaction {
        checkpoints.list(catalog.requireWorkspace(workspaceId))
    }

    suspend fun restoreCheckpoint(
        workspaceId: String,
        checkpointId: String,
    ): CreatorWorkspace = transaction {
        checkpoints.restore(catalog.requireWorkspace(workspaceId), checkpointId)
    }

    private suspend fun <T> transaction(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block() }
    }

    private fun commitProjectState(
        workspace: CreatorWorkspace,
        state: WorkspaceProjectState,
    ): CreatorWorkspace {
        val updated = workspace.copy(
            updatedAt = now().toString(),
            files = state.files.map(CreatorWorkspaceFile::path),
            totalBytes = state.totalBytes,
        )
        catalog.commitWorkspace(updated)
        return updated
    }

    private fun commitCharacterRoots(
        workspace: CreatorWorkspace,
        roots: List<CreatorWorkspaceCharacterRoot>,
        primaryRootId: String?,
    ): CreatorWorkspace {
        val updated = workspace.copy(
            schemaVersion = CurrentWorkspaceSchemaVersion,
            characterRoots = roots,
            primaryCharacterRootId = primaryRootId,
            linkedCharacterId = roots.firstOrNull { it.id == primaryRootId }?.characterId,
            updatedAt = now().toString(),
        ).withNormalizedCharacterRoots()
        catalog.commitWorkspace(updated)
        return updated
    }

    private fun createUniqueWorkspaceId(): String {
        repeat(10) {
            val candidate = newId().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
            if (
                paths.isSafeStorageId(candidate) &&
                catalog.find(candidate) == null &&
                !File(paths.workspacesRoot, candidate).exists()
            ) {
                return candidate
            }
        }
        error("无法生成工作区编号")
    }

    internal companion object {
        const val MaxFileCount = WorkspaceProjectStore.MaxFileCount
        const val MaxSingleFileBytes = WorkspaceProjectStore.MaxSingleFileBytes
        const val MaxTotalBytes = WorkspaceProjectStore.MaxTotalBytes
        const val MaxDirectoryDepth = WorkspaceProjectStore.MaxDirectoryDepth
        const val MaxFilesystemEntries = WorkspaceProjectStore.MaxFilesystemEntries
        const val MaxCheckpointCount = WorkspaceCheckpointStore.MaxCheckpointCount
        const val MaxCreatorMediaAssetBytes = CreatorMediaAssetStore.MaxAssetBytes

        private const val CurrentWorkspaceSchemaVersion = 6
        private const val DefaultConversationTitle = "新对话"
    }
}
