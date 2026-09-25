package com.eleckoi.android.engine.workspace.storage

import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCheckpoint
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceCheckpointLink
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceFile
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Owns immutable checkpoint storage and restore transactions.
 *
 * It intentionally has no mutex; [CreatorWorkspaceRepository] keeps the single
 * transaction lock while invoking these operations.
 */
internal class WorkspaceCheckpointStore(
    private val paths: WorkspacePathGuard,
    private val projects: WorkspaceProjectStore,
    private val catalog: WorkspaceCatalogStore,
    private val atomicFiles: AtomicWorkspaceFileStore,
    private val now: () -> Instant,
    private val newId: () -> String,
    private val moveDirectory: (source: File, destination: File) -> Unit,
) {
    fun create(workspace: CreatorWorkspace, label: String?): CreatorWorkspaceCheckpoint {
        val normalizedLabel = paths.validateCheckpointLabel(label)
        val sourceProject = paths.projectDirectory(workspace)
        val projectState = projects.inspect(sourceProject)
        val checkpointsRoot = paths.requireCheckpointsDirectory(workspace, createIfMissing = true)
        val checkpointId = createUniqueCheckpointId(checkpointsRoot)
        val workspaceRoot = paths.workspaceDirectory(workspace)
        val staging = File(workspaceRoot, ".checkpoint-$checkpointId")
        val destination = File(checkpointsRoot, checkpointId)

        require(!Files.exists(staging.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "工作区快照临时目录已存在"
        }
        require(staging.mkdir()) { "无法创建工作区快照临时目录" }
        try {
            require(paths.isDirectChildDirectory(workspaceRoot, staging)) {
                "工作区快照临时目录无效"
            }
            val stagingProject = File(staging, WorkspacePathGuard.ProjectDirectoryName)
            projects.copyProject(
                sourceProject = sourceProject,
                destinationProject = stagingProject,
                pathsToCopy = projectState.files.map(CreatorWorkspaceFile::path),
                symbolicLinks = projectState.symbolicLinks,
                relocateInternalLinks = true,
            )
            val snapshotState = projects.inspect(stagingProject)
            val checkpoint = CreatorWorkspaceCheckpoint(
                id = checkpointId,
                workspaceId = workspace.id,
                label = normalizedLabel,
                createdAt = now().toString(),
                files = snapshotState.files.map(CreatorWorkspaceFile::path),
                totalBytes = checkpointByteSize(snapshotState),
                symbolicLinks = checkpointLinks(snapshotState.symbolicLinks),
            )
            atomicFiles.writeJson(
                File(staging, WorkspacePathGuard.CheckpointManifestFileName),
                ElecKoiPrettyJson.encodeToString(checkpoint),
            )
            destination.parentFile?.mkdirs()
            require(staging.renameTo(destination)) { "无法保存工作区快照" }

            val updated = workspace.copy(
                updatedAt = now().toString(),
                files = projectState.files.map(CreatorWorkspaceFile::path),
                totalBytes = projectState.totalBytes,
                latestCheckpointId = checkpoint.id,
            )
            try {
                catalog.commitWorkspace(updated)
            } catch (error: Throwable) {
                paths.deleteTreeNoFollow(destination)
                throw error
            }
            trim(workspace)
            return checkpoint
        } finally {
            paths.deleteTreeNoFollow(staging)
        }
    }

    fun list(workspace: CreatorWorkspace): List<CreatorWorkspaceCheckpoint> {
        val checkpointsRoot = File(
            paths.workspaceDirectory(workspace),
            WorkspacePathGuard.CheckpointsDirectoryName,
        )
        if (!Files.exists(checkpointsRoot.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return emptyList()
        }
        val safeCheckpointsRoot = paths.requireCheckpointsDirectory(
            workspace,
            createIfMissing = false,
        )
        return safeCheckpointsRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter { directory ->
                paths.isSafeStorageId(directory.name) &&
                    paths.isDirectChildDirectory(safeCheckpointsRoot, directory)
            }
            .mapNotNull { directory ->
                runCatching { loadAndValidate(workspace, directory.name) }.getOrNull()
            }
            .sortedByDescending(CreatorWorkspaceCheckpoint::createdAt)
            .toList()
    }

    /**
     * Restores one immutable checkpoint without ever mutating it in place.
     */
    fun restore(
        workspace: CreatorWorkspace,
        checkpointId: String,
    ): CreatorWorkspace {
        require(paths.isSafeStorageId(checkpointId)) { "快照编号无效" }
        val checkpoint = loadAndValidate(workspace, checkpointId)
        val checkpointsRoot = paths.requireCheckpointsDirectory(workspace, createIfMissing = false)
        val sourceProject = File(
            File(checkpointsRoot, checkpoint.id),
            WorkspacePathGuard.ProjectDirectoryName,
        )
        val transactionId = UUID.randomUUID().toString()
        val workspaceRoot = paths.workspaceDirectory(workspace)
        val stagingProject = File(workspaceRoot, ".restore-$transactionId")
        val backupProject = File(workspaceRoot, ".restore-backup-$transactionId")
        val discardedProject = File(workspaceRoot, ".restore-discarded-$transactionId")
        val liveProject = paths.projectDirectory(workspace)
        var liveMovedToBackup = false
        var stagingInstalled = false

        listOf(stagingProject, backupProject, discardedProject).forEach { temporary ->
            require(!Files.exists(temporary.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "工作区恢复临时目录已存在"
            }
        }
        try {
            val sourceLinks = projects.inspect(sourceProject).symbolicLinks
            projects.copyProject(
                sourceProject,
                stagingProject,
                checkpoint.files,
                sourceLinks,
            )
            val restoredState = projects.inspect(stagingProject)
            requireCheckpointMatchesProject(checkpoint, restoredState)
            // Validate the current tree before it becomes the rollback copy as well.
            projects.inspect(liveProject)

            moveDirectory(liveProject, backupProject)
            liveMovedToBackup = true
            moveDirectory(stagingProject, liveProject)
            stagingInstalled = true

            val restoredWorkspace = workspace.copy(
                updatedAt = now().toString(),
                files = restoredState.files.map(CreatorWorkspaceFile::path),
                totalBytes = restoredState.totalBytes,
                latestCheckpointId = checkpoint.id,
            )
            try {
                catalog.commitWorkspace(restoredWorkspace)
            } catch (commitError: Throwable) {
                rollbackRestore(
                    workspace = workspace,
                    liveProject = liveProject,
                    backupProject = backupProject,
                    discardedProject = discardedProject,
                    stagingInstalled = stagingInstalled,
                    originalError = commitError,
                )
                throw commitError
            }
            paths.deleteTreeNoFollow(backupProject)
            return restoredWorkspace
        } catch (error: Throwable) {
            if (liveMovedToBackup && backupProject.exists()) {
                rollbackRestore(
                    workspace = workspace,
                    liveProject = liveProject,
                    backupProject = backupProject,
                    discardedProject = discardedProject,
                    stagingInstalled = stagingInstalled,
                    originalError = error,
                )
            }
            throw error
        } finally {
            paths.deleteTreeNoFollow(stagingProject)
            paths.deleteTreeNoFollow(discardedProject)
        }
    }

    private fun loadAndValidate(
        workspace: CreatorWorkspace,
        checkpointId: String,
    ): CreatorWorkspaceCheckpoint {
        require(paths.isSafeStorageId(workspace.id) && paths.isSafeStorageId(checkpointId)) {
            "快照编号无效"
        }
        val checkpointsRoot = paths.requireCheckpointsDirectory(workspace, createIfMissing = false)
        val directory = File(checkpointsRoot, checkpointId)
        require(paths.isDirectChildDirectory(checkpointsRoot, directory)) {
            "工作区快照不存在或不安全"
        }
        val manifestFile = File(directory, WorkspacePathGuard.CheckpointManifestFileName)
        require(paths.isRegularFileNoFollow(manifestFile)) {
            "工作区快照清单不存在或不安全"
        }
        val checkpoint = ElecKoiJson.decodeFromString<CreatorWorkspaceCheckpoint>(
            manifestFile.readText(Charsets.UTF_8),
        )
        require(checkpoint.id == checkpointId && checkpoint.workspaceId == workspace.id) {
            "工作区快照清单不匹配"
        }
        val projectState = projects.inspect(File(directory, WorkspacePathGuard.ProjectDirectoryName))
        requireCheckpointMatchesProject(checkpoint, projectState)
        return checkpoint
    }

    private fun requireCheckpointMatchesProject(
        checkpoint: CreatorWorkspaceCheckpoint,
        projectState: WorkspaceProjectState,
    ) {
        require(checkpoint.files.distinct().sorted() == projectState.files.map(CreatorWorkspaceFile::path)) {
            "工作区快照文件清单已损坏"
        }
        require(checkpoint.totalBytes == checkpointByteSize(projectState)) {
            "工作区快照容量清单已损坏"
        }
        require(checkpoint.symbolicLinks == checkpointLinks(projectState.symbolicLinks)) {
            "工作区快照链接清单已损坏"
        }
    }

    private fun checkpointLinks(links: Map<String, String>): Map<String, CreatorWorkspaceCheckpointLink> {
        val appFilesRoot = requireNotNull(paths.root.parentFile).canonicalFile.path
            .replace('\\', '/').trimEnd('/')
        return links.mapValues { (_, rawTarget) ->
            val normalized = rawTarget.replace('\\', '/')
            if (normalized.startsWith("$appFilesRoot/")) {
                CreatorWorkspaceCheckpointLink(
                    target = normalized.removePrefix("$appFilesRoot/"),
                    relativeToAppFiles = true,
                )
            } else {
                CreatorWorkspaceCheckpointLink(target = rawTarget)
            }
        }
    }

    private fun checkpointByteSize(state: WorkspaceProjectState): Long =
        state.files.sumOf(CreatorWorkspaceFile::sizeBytes) +
            checkpointLinks(state.symbolicLinks).values.sumOf { link ->
                link.target.toByteArray(Charsets.UTF_8).size.toLong()
            }

    private fun rollbackRestore(
        workspace: CreatorWorkspace,
        liveProject: File,
        backupProject: File,
        discardedProject: File,
        stagingInstalled: Boolean,
        originalError: Throwable,
    ) {
        runCatching {
            if (stagingInstalled && liveProject.exists()) {
                moveDirectory(liveProject, discardedProject)
            }
            if (!liveProject.exists()) {
                moveDirectory(backupProject, liveProject)
            }
            catalog.commitWorkspace(workspace)
        }.onFailure(originalError::addSuppressed)
    }

    private fun trim(workspace: CreatorWorkspace) {
        val checkpointsRoot = paths.requireCheckpointsDirectory(workspace, createIfMissing = false)
        val checkpoints = checkpointsRoot
            .listFiles()
            .orEmpty()
            .filter { directory ->
                paths.isSafeStorageId(directory.name) &&
                    paths.isDirectChildDirectory(checkpointsRoot, directory)
            }
            .map { directory ->
                val createdAt = runCatching {
                    val manifestFile = File(directory, WorkspacePathGuard.CheckpointManifestFileName)
                    require(paths.isRegularFileNoFollow(manifestFile)) { "工作区快照清单无效" }
                    val manifest = ElecKoiJson.decodeFromString<CreatorWorkspaceCheckpoint>(
                        manifestFile.readText(Charsets.UTF_8),
                    )
                    manifest.createdAt
                }.getOrElse {
                    Instant.ofEpochMilli(directory.lastModified()).toString()
                }
                directory to createdAt
            }
            .sortedByDescending { (_, createdAt) -> createdAt }
        checkpoints.drop(MaxCheckpointCount).forEach { (directory, _) ->
            paths.deleteTreeNoFollow(directory)
        }
    }

    private fun createUniqueCheckpointId(checkpointsRoot: File): String {
        repeat(10) {
            val candidate = newId().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
            val candidateDirectory = File(checkpointsRoot, candidate)
            if (
                paths.isSafeStorageId(candidate) &&
                !Files.exists(candidateDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)
            ) {
                return candidate
            }
        }
        error("无法生成快照编号")
    }

    companion object {
        const val MaxCheckpointCount = 5
    }
}
