package com.eleckoi.android.app.service.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assume.assumeNoException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupFileRootSwapTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `rollback restores the previous owned root`() {
        val appRoot = temporaryFolder.newFolder("rollback-app")
        File(appRoot, "data/user/old.txt").apply {
            parentFile?.mkdirs()
            writeText("old")
        }
        val swap = BackupFileRootSwap(appRoot, listOf("data/user"))
        File(swap.stagingRoot, "data/user/new.txt").apply {
            parentFile?.mkdirs()
            writeText("new")
        }

        swap.commit(listOf("files/data/user", "files/data/user/new.txt"))
        assertEquals("new", File(appRoot, "data/user/new.txt").readText())
        assertFalse(File(appRoot, "data/user/old.txt").exists())

        swap.rollback()
        assertEquals("old", File(appRoot, "data/user/old.txt").readText())
        assertFalse(File(appRoot, "data/user/new.txt").exists())
    }

    @Test
    fun `finish keeps the imported root and removes temporary roots`() {
        val appRoot = temporaryFolder.newFolder("finish-app")
        val swap = BackupFileRootSwap(appRoot, listOf("creator_workspaces/workspaces"))
        File(swap.stagingRoot, "creator_workspaces/workspaces/empty-project").mkdirs()

        swap.commit(listOf("files/creator_workspaces/workspaces"))
        swap.finish()

        assertTrue(File(appRoot, "creator_workspaces/workspaces/empty-project").isDirectory)
        assertFalse(swap.stagingRoot.exists())
    }

    @Test
    fun `finishing an import never deletes a link target in the old workspace`() {
        val appRoot = temporaryFolder.newFolder("backup-swap")
        val oldProject = File(appRoot, "creator_workspaces/workspaces/old/project")
        assertTrue(oldProject.mkdirs())
        val outside = File(appRoot, "outside.txt").apply { writeText("must-survive") }
        runCatching {
            Files.createSymbolicLink(File(oldProject, "outside-link").toPath(), outside.toPath())
        }.exceptionOrNull()?.let(::assumeNoException)

        val rootName = "creator_workspaces/workspaces"
        val swap = BackupFileRootSwap(appRoot, listOf(rootName))
        val newProject = File(swap.stagingRoot, "$rootName/new/project")
        assertTrue(newProject.mkdirs())
        File(newProject, "story.txt").writeText("restored")

        swap.commit(listOf("files/$rootName/new/project/story.txt"))
        swap.finish()

        assertEquals("must-survive", outside.readText())
        assertEquals("restored", File(appRoot, "$rootName/new/project/story.txt").readText())
    }
}
