package com.eleckoi.android.engine.workspace.storage

import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Centralizes workspace layout and no-follow path validation.
 *
 * This class is deliberately stateless and does not own a lock. Callers must
 * execute compound filesystem operations under [CreatorWorkspaceRepository]'s
 * transaction mutex.
 */
internal class WorkspacePathGuard(
    val root: File,
) {
    val workspacesRoot = File(root, WorkspacesDirectoryName)
    val characterWorkspacesRoot = File(root, CharacterWorkspacesDirectoryName)
    val stagingRoot = File(root, StagingDirectoryName)
    val catalogFile = File(root, CatalogFileName)

    fun initialize() {
        require(root.mkdirs() || isDirectoryNoFollow(root)) { "无法创建创作工作区存储" }
        require(isDirectoryNoFollow(root)) { "创作工作区存储无效" }
        require(workspacesRoot.mkdir() || isDirectoryNoFollow(workspacesRoot)) {
            "无法创建创作工作区目录"
        }
        require(isDirectoryNoFollow(workspacesRoot)) { "创作工作区目录无效" }
        require(characterWorkspacesRoot.mkdir() || isDirectoryNoFollow(characterWorkspacesRoot)) {
            "无法创建角色工作区目录"
        }
        require(isDirectoryNoFollow(characterWorkspacesRoot)) { "角色工作区目录无效" }
        require(stagingRoot.mkdir() || isDirectoryNoFollow(stagingRoot)) {
            "无法创建创作工作区临时目录"
        }
        require(isDirectChildDirectory(root, stagingRoot)) { "创作工作区临时目录无效" }
    }

    fun runtimeProjectPath(workspace: CreatorWorkspace): String {
        return workspaceStorageSegments(workspace).plus(ProjectDirectoryName).joinToString("/")
    }

    fun validateName(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "工作区名称不能为空" }
        require(normalized.length <= MaxWorkspaceNameLength) {
            "工作区名称不能超过 $MaxWorkspaceNameLength 个字符"
        }
        require(normalized.none(Char::isISOControl)) { "工作区名称包含无效字符" }
        return normalized
    }

    fun validateCharacterId(characterId: String?): String? {
        val normalized = characterId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(normalized.length <= MaxCharacterIdLength && isSafeStorageId(normalized)) {
            "角色关联编号无效"
        }
        return normalized
    }

    fun validateCheckpointLabel(label: String?): String? {
        val normalized = label?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(normalized.length <= MaxCheckpointLabelLength && normalized.none(Char::isISOControl)) {
            "快照名称无效"
        }
        return normalized
    }

    fun isSafeStorageId(value: String): Boolean {
        return value.isNotBlank() && value.length <= 64 &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    fun ensureCharacterContainer(characterId: String): File {
        val directory = characterContainerDirectory(
            validateCharacterId(characterId) ?: error("角色关联编号不能为空"),
        )
        require(directory.mkdir() || isDirectoryNoFollow(directory)) { "无法创建角色工作区容器" }
        require(isSafeCharacterContainerDirectory(directory)) { "角色工作区容器无效" }
        return directory
    }

    fun characterContainerDirectory(characterId: String): File {
        require(isSafeStorageId(characterId)) { "角色关联编号无效" }
        return File(characterWorkspacesRoot, characterId)
    }

    fun workspaceDirectory(workspace: CreatorWorkspace): File = workspaceDirectory(
        workspaceId = workspace.id,
        linkedCharacterId = workspace.linkedCharacterId,
        characterOwned = workspace.characterOwned,
    )

    fun workspaceDirectory(
        workspaceId: String,
        linkedCharacterId: String?,
        characterOwned: Boolean,
    ): File = if (linkedCharacterId != null && characterOwned) {
        File(
            characterContainerDirectory(linkedCharacterId),
            CharacterWorkspaceDirectoryName,
        )
    } else {
        File(workspacesRoot, workspaceId)
    }

    fun projectDirectory(workspace: CreatorWorkspace): File {
        return File(workspaceDirectory(workspace), ProjectDirectoryName)
    }

    /** Recreates only the one directory whose absence is unambiguous for a manifest-empty project. */
    fun ensureEmptyProjectDirectory(workspace: CreatorWorkspace): File {
        require(isSafeWorkspaceDirectory(workspace)) { "创作工作区目录无效" }
        val project = projectDirectory(workspace)
        if (isDirectoryNoFollow(project)) return project
        require(!Files.exists(project.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "工作区项目路径不是安全目录"
        }
        require(workspace.files.isEmpty() && workspace.totalBytes == 0L) {
            "工作区项目目录缺失，但清单仍记录了项目文件"
        }
        require(project.mkdir() && isDirectoryNoFollow(project)) { "无法恢复空的工作区项目目录" }
        return project
    }

    fun workspaceDirectoriesForDiscovery(): List<File> {
        val creatorDirectories = workspacesRoot.listFiles()
            .orEmpty()
            .filter { directory ->
                isSafeStorageId(directory.name) &&
                    isDirectChildDirectory(workspacesRoot, directory)
            }
        val characterDirectories = characterWorkspacesRoot.listFiles()
            .orEmpty()
            .filter(::isSafeCharacterContainerDirectory)
            .flatMap { container ->
                container.listFiles()
                    .orEmpty()
                    .filter { directory ->
                        directory.name == CharacterWorkspaceDirectoryName &&
                            isDirectChildDirectory(container, directory)
                    }
            }
        return creatorDirectories + characterDirectories
    }

    fun requireCheckpointsDirectory(
        workspace: CreatorWorkspace,
        createIfMissing: Boolean,
    ): File {
        val workspaceRoot = workspaceDirectory(workspace)
        require(isSafeWorkspaceDirectory(workspace)) { "创作工作区目录无效" }
        val checkpointsRoot = File(workspaceRoot, CheckpointsDirectoryName)
        if (createIfMissing && !Files.exists(checkpointsRoot.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            require(checkpointsRoot.mkdir()) { "无法创建工作区快照目录" }
        }
        require(isDirectoryNoFollow(checkpointsRoot)) { "工作区快照目录不存在或不安全" }
        require(checkpointsRoot.canonicalFile.parentFile == workspaceRoot.canonicalFile) {
            "工作区快照路径越过工作区边界"
        }
        return checkpointsRoot
    }

    fun resolveProjectPath(projectDirectory: File, rawPath: String): File {
        val normalized = rawPath.replace('\\', '/')
        require(normalized.isNotBlank() && normalized.length <= MaxRelativePathLength) {
            "文件路径无效"
        }
        val segments = normalized.split('/')
        require(segments.all(::isSafePathSegment)) { "文件路径包含不安全片段" }
        require(isDirectoryNoFollow(projectDirectory)) { "工作区项目目录不存在或不安全" }
        val rootCanonical = projectDirectory.canonicalFile
        val lexicalTarget = segments.fold(rootCanonical) { current, segment -> File(current, segment) }
        var current: File? = lexicalTarget
        while (current != null && current != rootCanonical) {
            if (Files.exists(current.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(current.toPath())) { "工作区不能包含符号链接" }
            }
            current = current.parentFile
        }
        require(current == rootCanonical) { "文件路径越过了工作区边界" }
        val target = lexicalTarget.canonicalFile
        require(target.path.startsWith(rootCanonical.path + File.separator)) {
            "文件路径越过了工作区边界"
        }
        return target
    }

    /** Resolves a project entry without following its final component. */
    fun resolveProjectEntryNoFollow(projectDirectory: File, rawPath: String): File {
        val normalized = rawPath.replace('\\', '/')
        require(normalized.isNotBlank() && normalized.length <= MaxRelativePathLength) {
            "文件路径无效"
        }
        val segments = normalized.split('/')
        require(segments.all(::isSafePathSegment)) { "文件路径包含不安全片段" }
        require(isDirectoryNoFollow(projectDirectory)) { "工作区项目目录不存在或不安全" }
        val parent = if (segments.size == 1) {
            projectDirectory.canonicalFile
        } else {
            resolveProjectPath(projectDirectory, segments.dropLast(1).joinToString("/"))
        }
        return File(parent, segments.last())
    }

    fun internalStateFile(directory: File, name: String): File {
        require(InternalStateFileName.matches(name)) { "工作区内部状态文件名无效" }
        val root = directory.canonicalFile
        val target = File(root, name).canonicalFile
        require(target.parentFile == root && target != root) { "工作区内部状态路径越界" }
        return target
    }

    fun countMissingPathEntries(projectDirectory: File, target: File): Int {
        val rootCanonical = projectDirectory.canonicalFile
        var current: File? = target
        var missing = 0
        while (current != null && current != rootCanonical) {
            if (!Files.exists(current.toPath(), LinkOption.NOFOLLOW_LINKS)) missing += 1
            current = current.parentFile
        }
        require(current == rootCanonical) { "文件路径越过了工作区边界" }
        return missing
    }

    fun ensureNoSymbolicLinks(projectDirectory: File, target: File) {
        val rootCanonical = projectDirectory.canonicalFile
        var current: File? = target
        while (current != null && current != rootCanonical) {
            if (current.exists()) {
                require(!Files.isSymbolicLink(current.toPath())) { "工作区不能包含符号链接" }
            }
            current = current.parentFile
        }
        require(current == rootCanonical) { "文件路径越过了工作区边界" }
    }

    fun isSafeWorkspaceDirectory(workspace: CreatorWorkspace): Boolean {
        if (!isSafeStorageId(workspace.id)) return false
        val directory = runCatching { workspaceDirectory(workspace) }.getOrNull() ?: return false
        if (!isDirectoryNoFollow(directory)) return false
        return if (workspace.linkedCharacterId != null && workspace.characterOwned) {
            val container = characterContainerDirectory(workspace.linkedCharacterId)
            isSafeCharacterContainerDirectory(container) &&
                directory.name == CharacterWorkspaceDirectoryName &&
                runCatching { directory.canonicalFile.parentFile == container.canonicalFile }
                    .getOrDefault(false)
        } else {
            isDirectoryNoFollow(workspacesRoot) &&
                directory.name == workspace.id &&
                runCatching { directory.canonicalFile.parentFile == workspacesRoot.canonicalFile }
                    .getOrDefault(false)
        }
    }

    fun isSafeCharacterContainerDirectory(directory: File): Boolean {
        if (!isSafeStorageId(directory.name) || !isDirectoryNoFollow(characterWorkspacesRoot)) return false
        if (!isDirectoryNoFollow(directory)) return false
        return runCatching {
            directory.canonicalFile.parentFile == characterWorkspacesRoot.canonicalFile
        }.getOrDefault(false)
    }

    fun isDirectChildDirectory(parent: File, child: File): Boolean {
        if (!isDirectoryNoFollow(parent) || !isDirectoryNoFollow(child)) return false
        return runCatching { child.canonicalFile.parentFile == parent.canonicalFile }.getOrDefault(false)
    }

    fun isDirectoryNoFollow(directory: File): Boolean {
        val path = directory.toPath()
        return !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
    }

    fun isRegularFileNoFollow(file: File): Boolean {
        val path = file.toPath()
        return !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
    }

    fun deleteTreeNoFollow(target: File) {
        val path = target.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(path)
            return
        }
        val children = requireNotNull(target.listFiles()) { "无法读取待删除的快照目录" }
        children.forEach(::deleteTreeNoFollow)
        Files.deleteIfExists(path)
    }

    private fun workspaceStorageSegments(workspace: CreatorWorkspace): List<String> {
        return if (workspace.linkedCharacterId != null && workspace.characterOwned) {
            listOf(
                CharacterWorkspacesDirectoryName,
                workspace.linkedCharacterId,
                CharacterWorkspaceDirectoryName,
            )
        } else {
            listOf(WorkspacesDirectoryName, workspace.id)
        }
    }

    private fun isSafePathSegment(segment: String): Boolean {
        return segment.isNotBlank() &&
            segment != "." &&
            segment != ".." &&
            segment.length <= MaxPathSegmentLength &&
            ':' !in segment &&
            segment.none(Char::isISOControl)
    }

    companion object {
        const val RootDirectoryName = "creator_workspaces"
        const val WorkspacesDirectoryName = "workspaces"
        const val CharacterWorkspacesDirectoryName = "characters"
        const val StagingDirectoryName = ".staging"
        const val ProjectDirectoryName = "project"
        const val CheckpointsDirectoryName = "checkpoints"
        const val InternalStateDirectoryName = "app_state"
        const val CatalogFileName = "catalog.json"
        const val ManifestFileName = "manifest.json"
        const val CheckpointManifestFileName = "checkpoint.json"

        private const val MaxWorkspaceNameLength = 80
        private const val MaxCharacterIdLength = 128
        private const val MaxCheckpointLabelLength = 80
        private const val MaxRelativePathLength = 512
        private const val MaxPathSegmentLength = 128
        private const val CharacterWorkspaceDirectoryName = "剧情小说"
        private val InternalStateFileName = Regex("^[a-z][a-z0-9._-]{0,127}$")
    }
}
