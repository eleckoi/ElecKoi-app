package com.eleckoi.android.app.service.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupFileRootSwapTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
}
