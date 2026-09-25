package com.eleckoi.android.engine.workspace.storage

import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceFile
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceLimits
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

/**
 * Performs project-tree I/O and quota validation without owning transactions.
 */
internal class WorkspaceProjectStore(
    private val paths: WorkspacePathGuard,
    private val atomicFiles: AtomicWorkspaceFileStore,
) {
    fun readText(workspace: CreatorWorkspace, path: String): String {
        val projectDirectory = paths.projectDirectory(workspace)
        val target = paths.resolveProjectPath(projectDirectory, path)
        require(target.isFile) { "文件不存在：$path" }
        require(!Files.isSymbolicLink(target.toPath())) { "不允许读取符号链接" }
        require(target.length() <= MaxSingleFileBytes) {
            "单个文件不能超过 ${MaxSingleFileBytes / Megabyte} MB"
        }
        val bytes = target.readBytes()
        require(bytes.none { it == 0.toByte() }) { "该文件不是可编辑文本" }
        return bytes.toString(Charsets.UTF_8)
    }

    fun writeText(
        workspace: CreatorWorkspace,
        path: String,
        content: String,
    ): WorkspaceProjectState? {
        val projectDirectory = paths.projectDirectory(workspace)
        val target = paths.resolveProjectPath(projectDirectory, path)
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= MaxSingleFileBytes) {
            "单个文件不能超过 ${MaxSingleFileBytes / Megabyte} MB"
        }
        require(!target.exists() || target.isFile) { "目标路径不是文件：$path" }
        paths.ensureNoSymbolicLinks(projectDirectory, target)

        if (
            target.isFile &&
            target.length() == bytes.size.toLong() &&
            target.readBytes().contentEquals(bytes)
        ) {
            return null
        }

        val before = inspect(projectDirectory)
        val existingSize = target.takeIf(File::isFile)?.length() ?: 0L
        if (!target.exists()) {
            require(before.files.size < MaxFileCount) { "一个工作区最多包含 $MaxFileCount 个文件" }
        }
        val newEntries = paths.countMissingPathEntries(projectDirectory, target)
        require(before.entryCount + newEntries <= MaxFilesystemEntries) {
            "一个工作区最多包含 $MaxFilesystemEntries 个文件系统条目"
        }
        require(before.totalBytes - existingSize + bytes.size <= MaxTotalBytes) {
            "一个工作区最多占用 ${MaxTotalBytes / Megabyte} MB"
        }

        atomicFiles.writeBytes(target, bytes)
        return inspect(projectDirectory)
    }

    fun ensureDirectory(workspace: CreatorWorkspace, path: String): WorkspaceProjectState? {
        val projectDirectory = paths.projectDirectory(workspace)
        val target = paths.resolveProjectPath(projectDirectory, path)
        require(!target.exists() || paths.isDirectoryNoFollow(target)) { "目标路径不是文件夹：$path" }
        paths.ensureNoSymbolicLinks(projectDirectory, target)
        if (paths.isDirectoryNoFollow(target)) return null
        val before = inspect(projectDirectory)
        val newEntries = paths.countMissingPathEntries(projectDirectory, target)
        require(before.entryCount + newEntries <= MaxFilesystemEntries) {
            "一个工作区最多包含 $MaxFilesystemEntries 个文件系统条目"
        }
        require(target.mkdirs() || paths.isDirectoryNoFollow(target)) { "无法创建工作区文件夹：$path" }
        return inspect(projectDirectory)
    }

    fun deletePath(
        workspace: CreatorWorkspace,
        path: String,
    ): WorkspaceProjectState? {
        val projectDirectory = paths.projectDirectory(workspace)
        val target = paths.resolveProjectEntryNoFollow(projectDirectory, path)
        if (!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        paths.deleteTreeNoFollow(target)
        return inspect(projectDirectory)
    }

    fun inspect(workspace: CreatorWorkspace): WorkspaceProjectState {
        return inspect(paths.projectDirectory(workspace))
    }

    fun inspect(projectDirectory: File): WorkspaceProjectState {
        require(paths.isDirectoryNoFollow(projectDirectory)) { "工作区项目目录不存在或不安全" }
        val rootCanonical = projectDirectory.canonicalFile
        val files = mutableListOf<CreatorWorkspaceFile>()
        val symbolicLinks = sortedMapOf<String, String>()
        var totalBytes = 0L
        var entryCount = 0

        fun visit(directory: File, depth: Int) {
            require(depth <= MaxDirectoryDepth) {
                "一个工作区的目录层级不能超过 $MaxDirectoryDepth"
            }
            require(paths.isDirectoryNoFollow(directory)) { "工作区不能包含符号链接或特殊目录" }
            val children = requireNotNull(directory.listFiles()) { "无法读取工作区目录" }
            children.sortedBy { child -> child.name }.forEach { child ->
                entryCount += 1
                require(entryCount <= MaxFilesystemEntries) {
                    "一个工作区最多包含 $MaxFilesystemEntries 个文件系统条目"
                }
                val attributes = Files.readAttributes(
                    child.toPath(),
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
                require(attributes.isSymbolicLink ||
                    child.canonicalPath.startsWith(rootCanonical.path + File.separator)) {
                    "工作区包含不安全路径"
                }
                when {
                    attributes.isSymbolicLink -> {
                        val target = Files.readSymbolicLink(child.toPath()).toString()
                        require(attributes.size() <= MaxSingleFileBytes) { "工作区符号链接过大" }
                        symbolicLinks[child.relativeTo(rootCanonical).invariantSeparatorsPath] = target
                        require(Long.MAX_VALUE - totalBytes >= attributes.size()) { "工作区容量计算溢出" }
                        totalBytes += attributes.size()
                        require(totalBytes <= MaxTotalBytes) {
                            "一个工作区最多占用 ${MaxTotalBytes / Megabyte} MB"
                        }
                    }
                    attributes.isDirectory -> visit(child, depth + 1)
                    attributes.isRegularFile -> {
                        require(files.size < MaxFileCount) { "一个工作区最多包含 $MaxFileCount 个文件" }
                        require(attributes.size() <= MaxSingleFileBytes) {
                            "单个文件不能超过 ${MaxSingleFileBytes / Megabyte} MB"
                        }
                        require(Long.MAX_VALUE - totalBytes >= attributes.size()) { "工作区容量计算溢出" }
                        totalBytes += attributes.size()
                        require(totalBytes <= MaxTotalBytes) {
                            "一个工作区最多占用 ${MaxTotalBytes / Megabyte} MB"
                        }
                        files += CreatorWorkspaceFile(
                            path = child.relativeTo(rootCanonical).invariantSeparatorsPath,
                            sizeBytes = attributes.size(),
                            lastModifiedAt = attributes.lastModifiedTime().toInstant().toString(),
                        )
                    }
                    else -> throw IllegalArgumentException("工作区包含不支持的文件类型")
                }
            }
        }

        visit(rootCanonical, depth = 0)
        return WorkspaceProjectState(
            files = files.sortedBy(CreatorWorkspaceFile::path),
            totalBytes = totalBytes,
            entryCount = entryCount,
            symbolicLinks = symbolicLinks,
        )
    }

    fun readInternalState(workspace: CreatorWorkspace, name: String): String? {
        val workspaceRoot = paths.workspaceDirectory(workspace)
        require(paths.isSafeWorkspaceDirectory(workspace)) { "工作区目录不存在或不安全" }
        val directory = File(workspaceRoot, WorkspacePathGuard.InternalStateDirectoryName)
        if (!paths.isDirectoryNoFollow(directory)) return null
        require(paths.isDirectChildDirectory(workspaceRoot, directory)) { "工作区内部状态目录无效" }
        val target = paths.internalStateFile(directory, name)
        if (!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        require(target.isFile && !Files.isSymbolicLink(target.toPath())) { "工作区内部状态文件无效" }
        require(target.length() <= MaxSingleFileBytes) { "工作区内部状态文件过大" }
        return target.readText(Charsets.UTF_8)
    }

    fun writeInternalState(
        workspace: CreatorWorkspace,
        name: String,
        content: String,
    ) {
        val workspaceRoot = paths.workspaceDirectory(workspace)
        require(paths.isSafeWorkspaceDirectory(workspace)) { "工作区目录不存在或不安全" }
        val directory = File(workspaceRoot, WorkspacePathGuard.InternalStateDirectoryName)
        require(directory.mkdir() || paths.isDirectoryNoFollow(directory)) {
            "无法创建工作区内部状态目录"
        }
        require(paths.isDirectChildDirectory(workspaceRoot, directory)) { "工作区内部状态目录无效" }
        val target = paths.internalStateFile(directory, name)
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= MaxSingleFileBytes) { "工作区内部状态文件过大" }
        require(!target.exists() || target.isFile) { "工作区内部状态目标不是文件" }
        require(!Files.isSymbolicLink(target.toPath())) { "不允许写入符号链接" }
        if (
            target.isFile &&
            target.length() == bytes.size.toLong() &&
            target.readBytes().contentEquals(bytes)
        ) {
            return
        }
        atomicFiles.writeBytes(target, bytes)
    }

    fun deleteInternalState(workspace: CreatorWorkspace, name: String) {
        val workspaceRoot = paths.workspaceDirectory(workspace)
        require(paths.isSafeWorkspaceDirectory(workspace)) { "工作区目录不存在或不安全" }
        val directory = File(workspaceRoot, WorkspacePathGuard.InternalStateDirectoryName)
        if (!paths.isDirectoryNoFollow(directory)) return
        require(paths.isDirectChildDirectory(workspaceRoot, directory)) { "工作区内部状态目录无效" }
        val target = paths.internalStateFile(directory, name)
        if (!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        require(target.isFile && !Files.isSymbolicLink(target.toPath())) { "工作区内部状态文件无效" }
        Files.delete(target.toPath())
        if (directory.listFiles()?.isEmpty() == true) {
            Files.delete(directory.toPath())
        }
    }

    fun copyProject(
        sourceProject: File,
        destinationProject: File,
        pathsToCopy: List<String>,
        symbolicLinks: Map<String, String> = emptyMap(),
        relocateInternalLinks: Boolean = false,
    ) {
        require(!Files.exists(destinationProject.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "恢复临时目录已存在"
        }
        val parent = requireNotNull(destinationProject.parentFile) { "恢复临时目录缺少父目录" }
        require(destinationProject.mkdir() && paths.isDirectChildDirectory(parent, destinationProject)) {
            "无法创建恢复临时目录"
        }
        pathsToCopy.distinct().forEach { path ->
            val source = paths.resolveProjectPath(sourceProject, path)
            require(source.isFile && !Files.isSymbolicLink(source.toPath())) {
                "工作区快照文件不存在：$path"
            }
            val destination = paths.resolveProjectPath(destinationProject, path)
            destination.parentFile?.mkdirs()
            Files.copy(source.toPath(), destination.toPath(), LinkOption.NOFOLLOW_LINKS)
        }
        symbolicLinks.toSortedMap().forEach { (path, linkTarget) ->
            val source = paths.resolveProjectEntryNoFollow(sourceProject, path)
            require(Files.isSymbolicLink(source.toPath()) &&
                Files.readSymbolicLink(source.toPath()).toString() == linkTarget) {
                "工作区快照链接不存在或已变化：$path"
            }
            val destination = paths.resolveProjectEntryNoFollow(destinationProject, path)
            require(!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "工作区快照路径重复：$path"
            }
            require(destination.parentFile?.mkdirs() == true ||
                paths.isDirectoryNoFollow(requireNotNull(destination.parentFile))) {
                "无法创建工作区快照目录：$path"
            }
            val target = if (relocateInternalLinks) {
                relocatedLinkTarget(sourceProject, destinationProject, destination, linkTarget)
            } else {
                Files.readSymbolicLink(source.toPath())
            }
            Files.createSymbolicLink(destination.toPath(), target)
        }
    }

    private fun relocatedLinkTarget(
        sourceProject: File,
        destinationProject: File,
        destinationLink: File,
        rawTarget: String,
    ): java.nio.file.Path {
        val original = Paths.get(rawTarget)
        if (!original.isAbsolute) return original
        val sourceRoot = sourceProject.toPath().toAbsolutePath().normalize()
        val normalized = original.normalize()
        if (!normalized.startsWith(sourceRoot)) return original
        val targetInSnapshot = destinationProject.toPath().toAbsolutePath().normalize()
            .resolve(sourceRoot.relativize(normalized))
        val relative = requireNotNull(destinationLink.toPath().parent).relativize(targetInSnapshot)
        return if (relative.toString().isEmpty()) Paths.get(".") else relative
    }

    companion object {
        const val MaxFileCount = CreatorWorkspaceLimits.MaxFileCount
        const val MaxSingleFileBytes = CreatorWorkspaceLimits.MaxSingleFileBytes
        const val MaxTotalBytes = CreatorWorkspaceLimits.MaxTotalBytes
        const val MaxDirectoryDepth = CreatorWorkspaceLimits.MaxDirectoryDepth
        const val MaxFilesystemEntries = CreatorWorkspaceLimits.MaxFilesystemEntries
        private const val Megabyte = 1024L * 1024L
    }
}

internal data class WorkspaceProjectState(
    val files: List<CreatorWorkspaceFile>,
    val totalBytes: Long,
    val entryCount: Int,
    val symbolicLinks: Map<String, String> = emptyMap(),
)
