package com.eleckoi.android.engine.workspace.runtime

import android.content.Context
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** App-private filesystem layout for the DSH-only runtime. */
class RuntimePaths(context: Context) {
    val runtimeRoot: File = File(context.noBackupFilesDir, "local_runtime")
    val deepSeekFileUploadIndex: File = File(runtimeRoot, "state/deepseek_file_uploads.json")
    val activeRuntimeManifest: File = File(runtimeRoot, "active/manifest.json")
    val downloadsRoot: File = File(runtimeRoot, "downloads")
    val installationsRoot: File = File(runtimeRoot, "installations")
    val hostTemp: File = File(runtimeRoot, "tmp")
    val hostNetworkRoot: File = File(runtimeRoot, "network")
    val hostResolverConfig: File = File(hostNetworkRoot, "resolv.conf")
    val nativeLibraryRoot: File = File(requireNotNull(context.applicationInfo.nativeLibraryDir))
    private val runtimeSessionsRoot = File(runtimeRoot, "sessions")
    private val workspaceDeepSeekHomesRoot = File(runtimeRoot, "state/workspace_dsh_homes")
    private val creatorWorkspaceStorageRoot = File(context.filesDir, "creator_workspaces")
    val persistentDeepSeekWorkspaceId: String = PersistentHarnessWorkspaceId

    init {
        listOf(
            runtimeRoot,
            workspaceDeepSeekHomesRoot,
            downloadsRoot,
            installationsRoot,
            hostTemp,
            hostNetworkRoot,
            runtimeSessionsRoot,
        ).forEach(File::mkdirs)
    }

    fun installation(version: String): File {
        require(RuntimeVersion.matches(version) && version != "." && version != ".." && !version.startsWith('.')) {
            "运行时版本号无效"
        }
        val root = installationsRoot.canonicalFile
        val target = File(root, version).canonicalFile
        require(target.toPath().startsWith(root.toPath()) && target != root) { "运行时安装路径越界" }
        return target
    }

    fun nativeHost(name: String): File {
        require(NativeHostName.matches(name)) { "原生宿主文件名无效" }
        return File(nativeLibraryRoot, name)
    }

    fun workspaceProject(workspaceId: String, relativeProjectPath: String = ""): File {
        require(WorkspaceId.matches(workspaceId)) { "工作区编号无效" }
        if (workspaceId == PersistentHarnessWorkspaceId) {
            val root = creatorWorkspaceStorageRoot.canonicalFile
            require(root.isDirectory || root.mkdirs()) { "无法创建全局创作工作区目录" }
            return root
        }
        val normalizedPath = relativeProjectPath.trim().ifBlank { "workspaces/$workspaceId/project" }
        require(isAllowedWorkspaceProjectPath(normalizedPath, workspaceId)) { "工作区项目路径无效" }
        val root = creatorWorkspaceStorageRoot.canonicalFile
        val project = File(root, normalizedPath).canonicalFile
        require(project.toPath().startsWith(root.toPath()) && project != root) { "工作区路径越界" }
        require(project.isDirectory) { "工作区不存在" }
        return project
    }

    fun persistentGuestWorkspacePath(
        workspaceId: String,
        relativeProjectPath: String = "",
    ): String {
        require(WorkspaceId.matches(workspaceId)) { "工作区编号无效" }
        require(workspaceId != PersistentHarnessWorkspaceId) { "聊天工作区编号无效" }
        val normalizedPath = relativeProjectPath.trim().ifBlank { "workspaces/$workspaceId/project" }
        require(isAllowedWorkspaceProjectPath(normalizedPath, workspaceId)) { "工作区项目路径无效" }
        workspaceProject(workspaceId, normalizedPath)
        return "/workspace/$normalizedPath"
    }

    fun workspaceDeepSeekHome(workspaceId: String): File {
        require(WorkspaceId.matches(workspaceId)) { "工作区编号无效" }
        val root = workspaceDeepSeekHomesRoot.canonicalFile
        val home = File(root, workspaceId).canonicalFile
        require(home.toPath().startsWith(root.toPath()) && home != root) { "DSH 状态路径越界" }
        require(home.isDirectory || home.mkdirs()) { "无法创建 DSH 状态目录" }
        return home
    }

    /** True when the packaged DSH JSONL backend already owns this app-level session id. */
    fun persistentDeepSeekSessionExists(sessionId: String): Boolean {
        require(DeepSeekSessionId.matches(sessionId)) { "DSH session 编号无效" }
        val sessions = File(workspaceDeepSeekHome(persistentDeepSeekWorkspaceId), "sessions").canonicalFile
        if (!sessions.isDirectory) return false
        return sessions.listFiles().orEmpty().any { projectEntry ->
            val project = projectEntry.canonicalFile
            if (!projectEntry.isDirectory || project.parentFile != sessions) return@any false
            val session = File(project, sessionId).canonicalFile
            if (!session.toPath().startsWith(project.toPath()) || session == project) return@any false
            File(session, "session.jsonl.zstd").isFile || File(session, "session.jsonl").isFile
        }
    }

    /** Deletes exact, product-declared obsolete DSH sessions without following filesystem links. */
    fun deletePersistentDeepSeekSessions(sessionIds: Set<String>) {
        if (sessionIds.isEmpty()) return
        sessionIds.forEach { sessionId ->
            require(DeepSeekSessionId.matches(sessionId)) { "DSH session 编号无效" }
        }
        val sessions = File(workspaceDeepSeekHome(persistentDeepSeekWorkspaceId), "sessions").canonicalFile
        if (!sessions.isDirectory) return
        sessions.listFiles().orEmpty().forEach { projectEntry ->
            val project = projectEntry.canonicalFile
            if (!projectEntry.isDirectory || project.parentFile != sessions) return@forEach
            sessionIds.forEach { sessionId ->
                val unresolved = File(project, sessionId)
                if (!Files.exists(unresolved.toPath()) || Files.isSymbolicLink(unresolved.toPath())) {
                    Files.deleteIfExists(unresolved.toPath())
                    return@forEach
                }
                val target = unresolved.canonicalFile
                require(target.parentFile == project) { "DSH session 清理路径越界" }
                deleteTreeWithoutFollowingLinks(target)
            }
        }
    }

    private fun deleteTreeWithoutFollowingLinks(root: File) {
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(
                file: java.nio.file.Path,
                attrs: BasicFileAttributes,
            ): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(
                directory: java.nio.file.Path,
                error: java.io.IOException?,
            ): FileVisitResult {
                error?.let { throw it }
                Files.deleteIfExists(directory)
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun prepareSessionScratch(commandId: String): RuntimeSessionScratch {
        require(CommandId.matches(commandId)) { "运行任务编号无效" }
        val root = runtimeSessionsRoot.canonicalFile
        val session = File(root, commandId).canonicalFile
        require(session.toPath().startsWith(root.toPath()) && session != root) { "运行会话路径越界" }
        if (session.exists()) require(session.deleteRecursively()) { "无法清理旧运行会话" }
        val home = File(session, "home")
        val guestTemp = File(session, "guest-tmp")
        val prootTemp = File(session, "proot-tmp")
        listOf(home, guestTemp, prootTemp).forEach { directory ->
            require(directory.mkdirs()) { "无法创建运行会话临时目录" }
        }
        return RuntimeSessionScratch(session, home, guestTemp, prootTemp)
    }

    fun cleanupSessionScratch(commandId: String) {
        if (!CommandId.matches(commandId)) return
        val root = runtimeSessionsRoot.canonicalFile
        val session = File(root, commandId).canonicalFile
        if (session.toPath().startsWith(root.toPath()) && session != root) session.deleteRecursively()
    }

    fun cleanupAllSessionScratch() {
        val root = runtimeSessionsRoot.canonicalFile
        root.listFiles().orEmpty().forEach { child ->
            val target = child.canonicalFile
            if (target.toPath().startsWith(root.toPath()) && target != root) target.deleteRecursively()
        }
    }

    private fun isAllowedWorkspaceProjectPath(path: String, workspaceId: String): Boolean {
        if ('\\' in path || path.startsWith('/') || path.endsWith('/')) return false
        val segments = path.split('/')
        return when {
            segments.size == 3 && segments[0] == "workspaces" ->
                segments[1] == workspaceId && segments[2] == "project"
            segments.size == 4 && segments[0] == "characters" ->
                CharacterId.matches(segments[1]) &&
                    segments[2] == CharacterWorkspaceDirectory &&
                    segments[3] == "project"
            else -> false
        }
    }

    companion object {
        const val PersistentHarnessWorkspaceId = "persistent-dsh-runtime"
        private val WorkspaceId = Regex("^[A-Za-z0-9_-]{1,80}$")
        private val CharacterId = Regex("^[A-Za-z0-9_-]{1,128}$")
        private const val CharacterWorkspaceDirectory = "剧情小说"
        private val RuntimeVersion = Regex("^[A-Za-z0-9._-]{1,120}$")
        private val NativeHostName = Regex("^lib[A-Za-z0-9_-]+\\.so$")
        private val CommandId = Regex("^[A-Za-z0-9_-]{1,100}$")
        private val DeepSeekSessionId = Regex("^[A-Za-z0-9._:-]{1,160}$")
    }
}

data class RuntimeSessionScratch(
    val root: File,
    val home: File,
    val guestTemp: File,
    val prootTemp: File,
)
