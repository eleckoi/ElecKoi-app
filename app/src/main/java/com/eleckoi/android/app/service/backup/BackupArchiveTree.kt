package com.eleckoi.android.app.service.backup

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths

internal data class BackupArchiveTree(
    val directories: List<String>,
    val files: List<BackupArchiveFile>,
    val symbolicLinks: List<BackupArchiveSymbolicLink>,
)

internal data class BackupArchiveFile(
    val entryName: String,
    val file: File,
)

internal data class BackupArchiveSymbolicLink(
    val entryName: String,
    val target: String,
)

/** Captures files, empty directories, and creator project links without following link targets. */
internal fun collectBackupArchiveTree(root: File, includedRoots: List<String>): BackupArchiveTree {
    val canonicalRoot = root.canonicalFile
    val directories = linkedMapOf<String, Unit>()
    val files = linkedMapOf<String, BackupArchiveFile>()
    val symbolicLinks = linkedMapOf<String, BackupArchiveSymbolicLink>()

    fun entryName(file: File): String {
        val lexical = file.toPath().toAbsolutePath().normalize()
        require(lexical != canonicalRoot.toPath() && lexical.startsWith(canonicalRoot.toPath())) {
            "备份文件路径越界"
        }
        return "files/" + canonicalRoot.toPath().relativize(lexical).toString().replace(File.separatorChar, '/')
    }

    fun visit(current: File) {
        val path = current.toPath()
        when {
            Files.isSymbolicLink(path) -> {
                val name = entryName(current)
                require(isCreatorWorkspaceProjectEntry(name)) { "备份目录包含不安全的符号链接" }
                val target = Files.readSymbolicLink(path).toString()
                require(target.isNotBlank() && target.toByteArray(Charsets.UTF_8).size <= MaxBackupLinkBytes) {
                    "备份符号链接目标无效"
                }
                symbolicLinks[name] = BackupArchiveSymbolicLink(name, target)
            }
            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> {
                directories[entryName(current)] = Unit
                requireNotNull(current.listFiles()) { "无法读取备份目录：${current.absolutePath}" }
                    .sortedBy(File::getName)
                    .forEach(::visit)
            }
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> {
                if (!current.name.endsWith(".part", ignoreCase = true)) {
                    val name = entryName(current)
                    files[name] = BackupArchiveFile(name, current)
                }
            }
            else -> error("备份目录包含不支持的文件类型：${current.absolutePath}")
        }
    }

    includedRoots.forEach { relative ->
        val candidate = File(canonicalRoot, relative.replace('/', File.separatorChar))
        var current = canonicalRoot
        relative.split('/').forEach { segment ->
            current = File(current, segment)
            require(!Files.isSymbolicLink(current.toPath())) { "备份目录路径不安全" }
        }
        if (Files.exists(candidate.toPath(), LinkOption.NOFOLLOW_LINKS)) visit(candidate)
    }
    return BackupArchiveTree(
        directories = directories.keys.sortedWith(compareBy<String> { it.count { char -> char == '/' } }.thenBy { it }),
        files = files.values.sortedBy(BackupArchiveFile::entryName),
        symbolicLinks = symbolicLinks.values.sortedBy(BackupArchiveSymbolicLink::entryName),
    )
}

internal fun isCreatorWorkspaceProjectEntry(entryName: String): Boolean {
    val parts = entryName.split('/')
    if (parts.size < 6 || parts[0] != "files" || parts[1] != "creator_workspaces") return false
    val start = when (parts[2]) {
        "workspaces" -> 4
        "characters" -> if (parts.getOrNull(4) == "剧情小说") 5 else return false
        else -> return false
    }
    val remainder = parts.drop(start)
    return (remainder.size >= 2 && remainder[0] == "project") ||
        (remainder.size >= 4 && remainder[0] == "checkpoints" && remainder[2] == "project")
}

internal fun restoreBackupSymbolicLink(root: File, entryName: String, target: String) {
    require(isCreatorWorkspaceProjectEntry(entryName)) { "备份链接路径不安全" }
    require(target.isNotBlank() && target.toByteArray(Charsets.UTF_8).size <= MaxBackupLinkBytes) {
        "备份符号链接目标无效"
    }
    val entry = resolveBackupEntryNoFollow(root, entryName)
    require(!Files.exists(entry.toPath(), LinkOption.NOFOLLOW_LINKS)) { "备份链接路径重复：$entryName" }
    Files.createSymbolicLink(entry.toPath(), Paths.get(target))
}

private fun resolveBackupEntryNoFollow(root: File, entryName: String): File {
    val relative = entryName.removePrefix("files/")
    require(entryName.startsWith("files/") && relative.split('/').none {
        it.isBlank() || it == "." || it == ".."
    }) { "备份文件路径不安全" }
    val canonicalRoot = root.canonicalFile
    val target = File(canonicalRoot, relative.replace('/', File.separatorChar))
    val parent = requireNotNull(target.parentFile).canonicalFile
    require(parent.toPath().startsWith(canonicalRoot.toPath()) &&
        Files.isDirectory(parent.toPath(), LinkOption.NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(parent.toPath())) { "备份链接路径越界" }
    return File(parent, target.name)
}

internal const val MaxBackupLinkBytes = 4096

internal fun restoreBackupDirectories(root: File, entryNames: List<String>) {
    entryNames
        .sortedWith(compareBy<String> { it.count { char -> char == '/' } }.thenBy { it })
        .forEach { entryName ->
            val directory = resolveBackupEntry(root, entryName)
            require(!Files.isSymbolicLink(directory.toPath())) { "备份目录路径不安全" }
            require(directory.mkdirs() || Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "无法恢复备份目录：$entryName"
            }
        }
}

internal fun resolveBackupEntry(root: File, entryName: String): File {
    require(entryName.startsWith("files/") && entryName.length > "files/".length) {
        "备份文件路径不安全"
    }
    val relative = entryName.removePrefix("files/")
    require(relative.split('/').none { it.isBlank() || it == "." || it == ".." }) {
        "备份文件路径不安全"
    }
    val canonicalRoot = root.canonicalFile
    val target = File(canonicalRoot, relative.replace('/', File.separatorChar)).canonicalFile
    require(target != canonicalRoot && target.toPath().startsWith(canonicalRoot.toPath())) {
        "备份文件路径越界"
    }
    return target
}
