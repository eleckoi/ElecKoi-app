package com.eleckoi.android.engine.immersive.project

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal object FrontendHtmlProjectFiles {
    private const val EntryFile = "index.html"

    fun create(html: String, destination: File): ImportedFrontendFiles {
        val bytes = validatedBytes(html)
        destination.mkdirs()
        File(destination, EntryFile).writeBytes(bytes)
        return ImportedFrontendFiles(entryFile = EntryFile, files = listOf(EntryFile))
    }

    fun readEntry(projectDirectory: File, entryFile: String): String {
        val target = resolveEntry(projectDirectory, entryFile)
        require(target.isFile) { "前端入口文件不存在" }
        require(target.length() <= FrontendProjectImporter.MaxExpandedBytes) { "前端入口文件过大" }
        return target.readText(Charsets.UTF_8)
    }

    fun replaceEntry(projectDirectory: File, entryFile: String, html: String) {
        val target = resolveEntry(projectDirectory, entryFile)
        val replacement = File(
            target.parentFile,
            ".${target.name}.${UUID.randomUUID()}.replacement",
        )
        try {
            replacement.writeBytes(validatedBytes(html))
            runCatching {
                Files.move(
                    replacement.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.recoverCatching { error ->
                if (error !is AtomicMoveNotSupportedException) throw error
                Files.move(
                    replacement.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrThrow()
        } finally {
            replacement.delete()
        }
    }

    private fun resolveEntry(projectDirectory: File, entryFile: String): File {
        require(projectDirectory.isDirectory) { "前端项目文件不存在" }
        val root = projectDirectory.canonicalFile
        val normalizedEntry = entryFile.replace('\\', '/').trimStart('/')
        require(normalizedEntry.endsWith(".html", ignoreCase = true) ||
            normalizedEntry.endsWith(".htm", ignoreCase = true)) {
            "前端入口不是 HTML 文件"
        }
        val target = File(root, normalizedEntry).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "前端入口路径无效" }
        return target
    }

    private fun validatedBytes(html: String): ByteArray {
        require(html.isNotBlank()) { "HTML 代码不能为空" }
        val bytes = html.toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= FrontendProjectImporter.MaxExpandedBytes) {
            "HTML 代码不能超过 ${FrontendProjectImporter.MaxExpandedBytes / (1024L * 1024L)} MB"
        }
        return bytes
    }
}
