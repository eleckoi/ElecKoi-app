package com.eleckoi.android.engine.workspace.storage.media

import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.storage.WorkspaceCatalogStore
import com.eleckoi.android.engine.workspace.storage.WorkspacePathGuard
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * Non-thread-safe private media store. Asset files live outside the author-visible project tree and
 * are addressed only by validated opaque ids.
 */
internal class CreatorMediaAssetStore(
    private val paths: WorkspacePathGuard,
    private val catalog: WorkspaceCatalogStore,
) {
    fun import(
        workspaceId: String,
        assetId: String,
        extension: String,
        source: File,
    ): File {
        require(paths.isSafeStorageId(assetId)) { "创作媒体 asset id 无效" }
        val normalizedExtension = extension.lowercase()
        require(normalizedExtension in Extensions) { "创作媒体格式不受支持" }
        require(paths.isRegularFileNoFollow(source)) { "创作媒体源文件不存在或不安全" }
        require(source.length() in 1..MaxAssetBytes) { "创作媒体文件过大" }
        val workspace = catalog.requireWorkspace(workspaceId)
        val directory = requireNotNull(directory(workspace, create = true))
        val destination = File(directory, "$assetId.$normalizedExtension")
        require(destination.canonicalFile.parentFile == directory.canonicalFile) { "创作媒体路径越界" }
        require(!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) { "创作媒体 asset id 已存在" }
        val staging = File.createTempFile(".media-", ".tmp", directory)
        try {
            source.inputStream().use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }
            require(staging.length() == source.length()) { "创作媒体复制不完整" }
            try {
                Files.move(
                    staging.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging.toPath(), destination.toPath())
            }
            require(paths.isRegularFileNoFollow(destination)) { "创作媒体落盘失败" }
            return destination
        } catch (error: Throwable) {
            staging.delete()
            destination.delete()
            throw error
        }
    }

    fun find(workspaceId: String, assetId: String): File? {
        if (!paths.isSafeStorageId(assetId)) return null
        val workspace = catalog.requireWorkspace(workspaceId)
        val directory = directory(workspace, create = false) ?: return null
        return Extensions.asSequence()
            .map { extension -> File(directory, "$assetId.$extension") }
            .firstOrNull(paths::isRegularFileNoFollow)
    }

    fun list(workspaceId: String): List<File> {
        val workspace = catalog.requireWorkspace(workspaceId)
        val directory = directory(workspace, create = false) ?: return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { file ->
                paths.isRegularFileNoFollow(file) &&
                    file.extension.lowercase() in Extensions &&
                    paths.isSafeStorageId(file.nameWithoutExtension)
            }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy(File::getName))
    }

    fun delete(workspaceId: String, assetId: String) {
        if (!paths.isSafeStorageId(assetId)) return
        val workspace = catalog.requireWorkspace(workspaceId)
        val directory = directory(workspace, create = false) ?: return
        Extensions.forEach { extension ->
            val target = File(directory, "$assetId.$extension")
            if (paths.isRegularFileNoFollow(target)) Files.deleteIfExists(target.toPath())
        }
        if (directory.listFiles()?.isEmpty() == true) Files.deleteIfExists(directory.toPath())
    }

    private fun directory(
        workspace: CreatorWorkspace,
        create: Boolean,
    ): File? {
        val workspaceRoot = paths.workspaceDirectory(workspace)
        require(paths.isSafeWorkspaceDirectory(workspace)) { "工作区目录不存在或不安全" }
        val stateDirectory = File(workspaceRoot, WorkspacePathGuard.InternalStateDirectoryName)
        if (!Files.exists(stateDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (!create) return null
            require(stateDirectory.mkdir()) { "无法创建工作区内部状态目录" }
        }
        require(paths.isDirectChildDirectory(workspaceRoot, stateDirectory)) { "工作区内部状态目录无效" }
        val mediaDirectory = File(stateDirectory, DirectoryName)
        if (!Files.exists(mediaDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (!create) return null
            require(mediaDirectory.mkdir()) { "无法创建创作媒体目录" }
        }
        require(paths.isDirectChildDirectory(stateDirectory, mediaDirectory)) { "创作媒体目录无效" }
        return mediaDirectory
    }

    internal companion object {
        const val MaxAssetBytes = 32L * 1024L * 1024L
        private const val DirectoryName = "media_assets"
        private val Extensions = setOf("png", "jpg", "webp", "gif")
    }
}
