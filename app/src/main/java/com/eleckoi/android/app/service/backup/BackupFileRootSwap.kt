package com.eleckoi.android.app.service.backup

import java.io.File
import java.nio.file.Files
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
        rollbackRoot.deleteRecursively()
        stagingRoot.deleteRecursively()
    }

    fun rollback() {
        committed.asReversed().forEach { root ->
            if (root.installed.exists()) root.installed.deleteRecursively()
            if (root.previous != null && root.previous.exists()) {
                root.installed.parentFile?.mkdirs()
                move(root.previous, root.installed)
            }
        }
        committed.clear()
        finished = true
        rollbackRoot.deleteRecursively()
        stagingRoot.deleteRecursively()
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
        val child = File(canonicalRoot, relative.replace('/', File.separatorChar)).canonicalFile
        require(child != canonicalRoot && child.toPath().startsWith(canonicalRoot.toPath())) {
            "备份文件路径越界"
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
