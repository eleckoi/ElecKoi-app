package com.eleckoi.android.engine.agent.search

import android.content.Context
import com.eleckoi.android.engine.agent.api.AgentVirtualFile
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.api.AgentVirtualGlobRequest
import com.eleckoi.android.engine.agent.api.AgentVirtualGlobResult
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepLine
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepRequest
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepResult
import com.eleckoi.android.engine.workspace.runtime.AndroidDnsConfigWriter
import com.eleckoi.android.engine.workspace.runtime.RuntimeInstallationInspector
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import com.eleckoi.android.engine.workspace.runtime.process.ProotRuntimeGuestCommandExecutor
import com.eleckoi.android.engine.workspace.runtime.process.RuntimeGuestCommand
import com.eleckoi.android.engine.workspace.runtime.process.RuntimeGuestProcessSpecFactory
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class RipgrepAgentVirtualFileSearch(
    context: Context,
    private val runtimePaths: RuntimePaths,
) : AgentVirtualFileSearch {
    private val appContext = context.applicationContext
    private val dnsConfigWriter = AndroidDnsConfigWriter(appContext, runtimePaths.hostResolverConfig)
    private val executor = ProotRuntimeGuestCommandExecutor(
        RuntimeGuestProcessSpecFactory(
            nativeLibraryDirectory = runtimePaths.nativeLibraryRoot,
            hostTempDirectory = runtimePaths.hostTemp,
        ),
    )

    override suspend fun glob(
        files: List<AgentVirtualFile>,
        request: AgentVirtualGlobRequest,
    ): AgentVirtualGlobResult = withSnapshot(files, maxFiles = null, retainFullOutput = true) { guestDirectory, command ->
        require(request.pattern.isNotBlank()) { "Glob pattern 不能为空" }
        val pattern = request.pattern.normalizedPattern()
        val arguments = buildList {
            add("rg")
            add("--files")
            add("--sort")
            add("path")
            add("--hidden")
            add("--no-ignore")
            add(if (request.ignoreCase) "--iglob" else "--glob")
            add(pattern)
        }
        val result = command(arguments, guestDirectory)
        if (result.exitCode !in setOf(0, 1)) {
            error(result.stderr.ifBlank { result.stdout }.ifBlank { "ripgrep Glob 执行失败" })
        }
        val allPaths = result.stdout
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { path -> path.normalizedResultPath() }
            .distinct()
            .toList()
        AgentVirtualGlobResult(
            paths = allPaths,
            omitted = 0,
        )
    }

    override suspend fun grep(
        files: List<AgentVirtualFile>,
        request: AgentVirtualGrepRequest,
    ): AgentVirtualGrepResult = withSnapshot(files) { guestDirectory, command ->
        require(request.pattern.isNotBlank()) { "Grep pattern 不能为空" }
        require(request.limit in 1..MaxResultLimit) { "Grep limit 必须在 1 到 $MaxResultLimit 之间" }
        val arguments = buildList {
            add("rg")
            add("--json")
            add("--sort")
            add("path")
            add("--hidden")
            add("--no-ignore")
            if (request.ignoreCase) add("--ignore-case")
            if (request.multiline) {
                add("--multiline")
                add("--multiline-dotall")
            }
            request.fileGlob?.trim()?.takeIf(String::isNotBlank)?.let { pattern ->
                add("--glob")
                add(pattern.normalizedPattern())
            }
            add("--")
            add(request.pattern)
            add(".")
        }
        val result = command(arguments, guestDirectory)
        if (result.exitCode !in setOf(0, 1)) {
            error(result.stderr.ifBlank { result.stdout }.ifBlank { "ripgrep Grep 执行失败" })
        }

        val allLines = result.stdout
            .lineSequence()
            .mapNotNull(::parseMatchLine)
            .toList()
        val allPaths = allLines.map(AgentVirtualGrepLine::path).distinct()
        val counts = linkedMapOf<String, Int>()
        allLines.forEach { line ->
            counts[line.path] = (counts[line.path] ?: 0) + line.matchCount
        }
        AgentVirtualGrepResult(
            paths = allPaths.take(request.limit),
            counts = counts.entries
                .take(request.limit)
                .associateTo(linkedMapOf()) { it.key to it.value },
            lines = allLines.take(request.limit),
            omittedPaths = (allPaths.size - request.limit).coerceAtLeast(0),
            omittedLines = (allLines.size - request.limit).coerceAtLeast(0),
        )
    }

    private suspend fun <T> withSnapshot(
        files: List<AgentVirtualFile>,
        maxFiles: Int? = MaxCorpusFiles,
        retainFullOutput: Boolean = false,
        block: suspend (
            guestDirectory: String,
            command: suspend (List<String>, String) -> com.eleckoi.android.engine.workspace.runtime.process.RuntimeGuestCommandResult,
        ) -> T,
    ): T = withContext(Dispatchers.IO) {
        maxFiles?.let { require(files.size <= it) { "虚拟搜索文件超过 $it 个" } }
        val activeRuntime = requireNotNull(RuntimeInstallationInspector.activePaths(runtimePaths)) {
            "本地 Agent 运行时尚未安装完成"
        }
        val snapshotName = "eleckoi-rg-${UUID.randomUUID().toString().replace("-", "")}"
        val hostDirectory = File(activeRuntime.rootfs, "tmp/$snapshotName").canonicalFile
        val guestDirectory = "/tmp/$snapshotName"
        require(hostDirectory.toPath().startsWith(File(activeRuntime.rootfs, "tmp").canonicalFile.toPath())) {
            "虚拟搜索目录越界"
        }
        require(hostDirectory.mkdirs()) { "无法创建虚拟搜索目录" }
        try {
            var totalBytes = 0L
            files.forEach { virtualFile ->
                val relative = virtualFile.path.normalizedVirtualFilePath()
                val target = File(hostDirectory, relative).canonicalFile
                require(target.toPath().startsWith(hostDirectory.toPath()) && target != hostDirectory) {
                    "虚拟文件路径越界"
                }
                val bytes = virtualFile.content.toByteArray(Charsets.UTF_8)
                require(bytes.size <= MaxFileBytes) { "虚拟文件 $relative 超过搜索大小上限" }
                totalBytes += bytes.size
                require(totalBytes <= MaxCorpusBytes) { "虚拟搜索内容超过总大小上限" }
                val parent = requireNotNull(target.parentFile) { "虚拟文件缺少父目录" }
                require(parent.isDirectory || parent.mkdirs()) {
                    "无法创建虚拟文件目录"
                }
                target.writeBytes(bytes)
            }
            val resolverConfig = dnsConfigWriter.refresh()
            block(guestDirectory) { arguments, workingDirectory ->
                executor.execute(
                    RuntimeGuestCommand(
                        commandId = "agent-rg-${UUID.randomUUID().toString().take(8)}",
                        rootfs = activeRuntime.rootfs,
                        tools = activeRuntime.tools,
                        hostResolverConfig = resolverConfig,
                        arguments = arguments,
                        guestWorkingDirectory = workingDirectory,
                        timeoutMillis = SearchTimeoutMillis,
                        retainFullOutput = retainFullOutput,
                    ),
                )
            }
        } finally {
            hostDirectory.deleteRecursively()
        }
    }

    private fun parseMatchLine(value: String): AgentVirtualGrepLine? {
        val event = runCatching {
            ElecKoiJson.parseToJsonElement(value) as? JsonObject
        }.getOrNull() ?: return null
        if (event.string("type") != "match") return null
        val data = event["data"] as? JsonObject ?: return null
        val path = (data["path"] as? JsonObject)
            ?.string("text")
            ?.normalizedResultPath()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val text = (data["lines"] as? JsonObject)
            ?.string("text")
            .orEmpty()
            .trimEnd('\r', '\n')
        val line = (data["line_number"] as? JsonPrimitive)
            ?.contentOrNull
            ?.toIntOrNull()
            ?: 1
        val matchCount = (data["submatches"] as? JsonArray)?.size?.coerceAtLeast(1) ?: 1
        return AgentVirtualGrepLine(
            path = path,
            line = line,
            text = text.take(MaxResultLineCharacters),
            matchCount = matchCount,
        )
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun String.normalizedPattern(): String =
        replace('\\', '/').trim().trimStart('/')

    private fun String.normalizedResultPath(): String =
        replace('\\', '/').removePrefix("./").trimStart('/')

    private fun String.normalizedVirtualFilePath(): String {
        val normalized = normalizedResultPath()
        require(normalized.isNotBlank()) { "虚拟文件路径不能为空" }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) { "虚拟文件路径无效" }
        return segments.joinToString("/")
    }

    private companion object {
        const val MaxCorpusFiles = 2_000
        const val MaxFileBytes = 512 * 1024
        const val MaxCorpusBytes = 8L * 1024L * 1024L
        const val MaxResultLimit = 1_000
        const val MaxResultLineCharacters = 2_000
        const val SearchTimeoutMillis = 30_000L
    }
}
