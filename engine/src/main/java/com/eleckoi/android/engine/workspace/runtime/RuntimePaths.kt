package com.eleckoi.android.engine.workspace.runtime

import android.content.Context
import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
        return persistentDeepSeekSessionLog(sessionId) != null
    }

    /** Returns DSH's logical event log only when its resolved file remains in the session. */
    fun persistentDeepSeekSessionLog(sessionId: String): File? {
        require(DeepSeekSessionId.matches(sessionId)) { "DSH session 编号无效" }
        val sessions = File(workspaceDeepSeekHome(persistentDeepSeekWorkspaceId), "sessions").canonicalFile
        return findPersistentDshSessionLog(sessions, sessionId)
    }

    /** Exact provider-facing context captured by the DSH request pipeline for one session. */
    fun persistentDeepSeekRequestContextLog(sessionId: String): File {
        require(DeepSeekSessionId.matches(sessionId)) { "DSH session 编号无效" }
        val root = File(
            workspaceDeepSeekHome(persistentDeepSeekWorkspaceId),
            "eleckoi/request-context",
        ).canonicalFile
        require(root.isDirectory || root.mkdirs()) { "无法创建 DSH Request 上下文目录" }
        val filename = sessionId.replace(UnsafeRequestContextFileChar, "_").take(160).ifBlank { "default" }
        val target = File(root, "$filename.jsonl").canonicalFile
        require(target.parentFile == root) { "DSH Request 上下文路径越界" }
        return target
    }

    /** Deletes exact, product-declared obsolete DSH sessions without following filesystem links. */
    fun deletePersistentDeepSeekSessions(sessionIds: Set<String>) {
        if (sessionIds.isEmpty()) return
        sessionIds.forEach { sessionId ->
            require(DeepSeekSessionId.matches(sessionId)) { "DSH session 编号无效" }
            val contextLog = persistentDeepSeekRequestContextLog(sessionId)
            if (contextLog.exists()) {
                require(!Files.isSymbolicLink(contextLog.toPath())) { "DSH Request 上下文文件不能是符号链接" }
                Files.deleteIfExists(contextLog.toPath())
            }
        }
        val sessions = File(workspaceDeepSeekHome(persistentDeepSeekWorkspaceId), "sessions").canonicalFile
        deletePersistentDshSessionDirectories(sessions, sessionIds)
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
        private val UnsafeRequestContextFileChar = Regex("[^A-Za-z0-9_-]")
    }
}

internal fun findPersistentDshSessionLog(sessionsDirectory: File, sessionId: String): File? {
    val sessions = runCatching { sessionsDirectory.canonicalFile }.getOrNull() ?: return null
    if (!sessions.isDirectory || Files.isSymbolicLink(sessions.toPath())) return null
    for (projectEntry in sessions.listFiles().orEmpty()) {
        if (!projectEntry.isDirectory || Files.isSymbolicLink(projectEntry.toPath())) continue
        val project = projectEntry.canonicalFile
        if (project.parentFile != sessions) continue
        for (sessionEntry in project.listFiles().orEmpty()) {
            if (!sessionEntry.isDirectory || Files.isSymbolicLink(sessionEntry.toPath())) continue
            val session = sessionEntry.canonicalFile
            if (session.parentFile != project) continue
            val log = resolvePersistentDshSessionLog(session) ?: continue
            if (readPersistentDshSessionHeaderId(log) == sessionId) return log
        }
    }
    return null
}

internal fun deletePersistentDshSessionDirectories(sessionsDirectory: File, sessionIds: Set<String>) {
    val sessions = runCatching { sessionsDirectory.canonicalFile }.getOrNull() ?: return
    if (!sessions.isDirectory || Files.isSymbolicLink(sessions.toPath())) return
    sessions.listFiles().orEmpty().forEach projectLoop@ { projectEntry ->
        if (!projectEntry.isDirectory || Files.isSymbolicLink(projectEntry.toPath())) return@projectLoop
        val project = projectEntry.canonicalFile
        if (project.parentFile != sessions) return@projectLoop
        project.listFiles().orEmpty().forEach sessionLoop@ { sessionEntry ->
            if (!sessionEntry.isDirectory || Files.isSymbolicLink(sessionEntry.toPath())) return@sessionLoop
            val target = sessionEntry.canonicalFile
            if (target.parentFile != project) return@sessionLoop
            val log = resolvePersistentDshSessionLog(target) ?: return@sessionLoop
            if (readPersistentDshSessionHeaderId(log) in sessionIds) deleteTree(target)
        }
    }
}

private fun deleteTree(root: File) {
    Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
        override fun visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.deleteIfExists(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(directory: java.nio.file.Path, error: java.io.IOException?): FileVisitResult {
            error?.let { throw it }
            Files.deleteIfExists(directory)
            return FileVisitResult.CONTINUE
        }
    })
}

/**
 * Selects DSH's newest persisted format generation while preserving the logical filename.
 * Symlinked logs are accepted only when their resolved file stays inside this exact session.
 */
internal fun resolvePersistentDshSessionLog(sessionDirectory: File): File? {
    val session = runCatching { sessionDirectory.toPath().toRealPath() }.getOrNull() ?: return null
    if (!Files.isDirectory(session, LinkOption.NOFOLLOW_LINKS)) return null
    return session.toFile().listFiles().orEmpty()
        .mapNotNull { candidate ->
            val generation = persistentDshSessionLogGeneration(candidate.name) ?: return@mapNotNull null
            generation to candidate
        }
        .sortedWith(compareByDescending<Pair<Long, File>> { it.first }.thenByDescending { it.second.name.endsWith(".zstd") })
        .firstNotNullOfOrNull { (_, candidate) ->
            val logicalLog = candidate.toPath()
            val resolvedLog = runCatching { logicalLog.toRealPath() }.getOrNull()
                ?: return@firstNotNullOfOrNull null
            if (Files.isRegularFile(resolvedLog, LinkOption.NOFOLLOW_LINKS) && resolvedLog.parent == session) {
                logicalLog.toFile().absoluteFile
            } else {
                null
            }
        }
}

internal fun readPersistentDshSessionHeaderId(log: File): String? = runCatching {
    val raw = log.inputStream().buffered()
    val input = if (log.name.endsWith(".zstd")) {
        ZstdInputStreamNoFinalizer(raw).setContinuous(true)
    } else {
        raw
    }
    input.bufferedReader(Charsets.UTF_8).use { reader ->
        val header = Json.parseToJsonElement(reader.readLine().orEmpty()) as? JsonObject
            ?: return@use null
        if (header["type"]?.jsonPrimitive?.contentOrNull != "session") return@use null
        header["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    }
}.getOrNull()

private fun persistentDshSessionLogGeneration(filename: String): Long? {
    if (filename == "session.jsonl" || filename == "session.jsonl.zstd") return 0L
    val match = SessionGenerationLog.matchEntire(filename) ?: return null
    return match.groupValues[1].toLongOrNull()?.takeIf { it > 0L }
}

private val SessionGenerationLog = Regex("^session\\.v([1-9][0-9]*)\\.jsonl(?:\\.zstd)?$")

data class RuntimeSessionScratch(
    val root: File,
    val home: File,
    val guestTemp: File,
    val prootTemp: File,
)
