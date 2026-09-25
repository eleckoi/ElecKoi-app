package com.eleckoi.android.app.service.backup

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * Stages the large file part of an import on the app-data filesystem, then swaps each owned root
 * into place without keeping a second copy of the imported bytes.
 */
internal class BackupFileRootSwap(
    private val appRoot: File,
    private val includedRoots: List<String>,
) {
    val stagingRoot = File(appRoot, ".backup-import-${java.util.UUID.randomUUID()}")
    private val rollbackRoot = File(appRoot, ".backup-rollback-${java.util.UUID.randomUUID()}")
    private val committed = mutableListOf<CommittedRoot>()
    private var finished = false

    init {
        require(stagingRoot.mkdir()) { "无法创建备份导入暂存区" }
        require(rollbackRoot.mkdir()) { "无法创建备份导入回滚区" }
    }

    fun commit(presentEntryNames: Collection<String>) {
        check(!finished) { "备份文件导入已经结束" }
        val roots = includedRoots.filter { relative ->
            val entryRoot = "files/$relative"
            presentEntryNames.any { it == entryRoot || it.startsWith("$entryRoot/") }
        }
        require(roots.none { left -> roots.any { right -> left != right && left.startsWith("$right/") } }) {
            "备份文件根目录不能互相嵌套"
        }
        try {
            roots.forEach { relative -> commitRoot(relative) }
        } catch (error: Throwable) {
            rollback()
            throw error
        }
    }

    fun finish() {
        finished = true
        deleteBackupTreeNoFollow(rollbackRoot)
        deleteBackupTreeNoFollow(stagingRoot)
    }

    fun rollback() {
        committed.asReversed().forEach { root ->
            if (Files.exists(root.installed.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                deleteBackupTreeNoFollow(root.installed)
            }
            if (root.previous != null && Files.exists(root.previous.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                root.installed.parentFile?.mkdirs()
                move(root.previous, root.installed)
            }
        }
        committed.clear()
        finished = true
        deleteBackupTreeNoFollow(rollbackRoot)
        deleteBackupTreeNoFollow(stagingRoot)
    }

    private fun commitRoot(relative: String) {
        val source = ownedChild(stagingRoot, relative)
        require(source.exists()) { "备份缺少文件根目录：$relative" }
        val target = ownedChild(appRoot, relative)
        val previous = if (target.exists()) {
            val backup = ownedChild(rollbackRoot, relative)
            backup.parentFile?.mkdirs()
            move(target, backup)
            backup
        } else {
            null
        }
        try {
            target.parentFile?.mkdirs()
            move(source, target)
            committed += CommittedRoot(target, previous)
        } catch (error: Throwable) {
            if (previous != null && previous.exists()) move(previous, target)
            throw error
        }
    }

    private fun ownedChild(root: File, relative: String): File {
        val canonicalRoot = root.canonicalFile
        require(relative.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "备份文件路径不安全"
        }
        var child = canonicalRoot
        relative.split('/').forEach { segment ->
            child = File(child, segment)
            require(!Files.isSymbolicLink(child.toPath())) { "备份文件路径不能经过符号链接" }
        }
        return child
    }

    private fun move(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private data class CommittedRoot(val installed: File, val previous: File?)
}

private fun deleteBackupTreeNoFollow(target: File) {
    val path = target.toPath()
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        requireNotNull(target.listFiles()) { "无法清理备份目录" }.forEach(::deleteBackupTreeNoFollow)
    }
    Files.delete(path)
}
