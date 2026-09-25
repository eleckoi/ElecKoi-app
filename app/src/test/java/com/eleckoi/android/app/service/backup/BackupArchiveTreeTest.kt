package com.eleckoi.android.app.service.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assume.assumeNoException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTreeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `empty creator project directories survive backup tree restoration`() {
        val source = temporaryFolder.newFolder("source")
        val workspace = File(source, "creator_workspaces/workspaces/workspace-1")
        File(workspace, "manifest.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }
        File(workspace, "project/empty-folder").mkdirs()

        val tree = collectBackupArchiveTree(
            root = source,
            includedRoots = listOf("creator_workspaces/workspaces"),
        )

        assertTrue("files/creator_workspaces/workspaces/workspace-1/project" in tree.directories)
        assertTrue("files/creator_workspaces/workspaces/workspace-1/project/empty-folder" in tree.directories)
        assertEquals(
            listOf("files/creator_workspaces/workspaces/workspace-1/manifest.json"),
            tree.files.map(BackupArchiveFile::entryName),
        )

        val restored = temporaryFolder.newFolder("restored")
        restoreBackupDirectories(restored, tree.directories)
        assertTrue(File(restored, "creator_workspaces/workspaces/workspace-1/project").isDirectory)
        assertTrue(File(restored, "creator_workspaces/workspaces/workspace-1/project/empty-folder").isDirectory)
    }

    @Test
    fun `backup directory restoration rejects traversal`() {
        val root = temporaryFolder.newFolder("safe-root")

        assertThrows(IllegalArgumentException::class.java) {
            restoreBackupDirectories(root, listOf("files/../escape"))
        }
    }

    @Test
    fun `creator project links are archived as links without reading their targets`() {
        val source = temporaryFolder.newFolder("linked-source")
        val project = File(source, "creator_workspaces/workspaces/workspace-1/project")
        assertTrue(project.mkdirs())
        val outside = File(source, "outside.txt").apply { writeText("private-content") }
        val link = File(project, "reference.txt")
        createLinkOrSkip(link, outside)

        val tree = collectBackupArchiveTree(source, listOf("creator_workspaces/workspaces"))

        assertTrue(tree.files.none { it.entryName.endsWith("reference.txt") })
        assertEquals(
            listOf("files/creator_workspaces/workspaces/workspace-1/project/reference.txt"),
            tree.symbolicLinks.map(BackupArchiveSymbolicLink::entryName),
        )
        assertEquals(outside.absolutePath, tree.symbolicLinks.single().target)

        val restored = temporaryFolder.newFolder("linked-restored")
        restoreBackupDirectories(restored, tree.directories)
        restoreBackupSymbolicLink(restored, tree.symbolicLinks.single().entryName, "../target.txt")
        val restoredLink = File(restored, "creator_workspaces/workspaces/workspace-1/project/reference.txt")
        assertTrue(Files.isSymbolicLink(restoredLink.toPath()))
        assertEquals("../target.txt", Files.readSymbolicLink(restoredLink.toPath()).toString().replace('\\', '/'))
    }

    @Test
    fun `backup still rejects links outside creator projects`() {
        val source = temporaryFolder.newFolder("unsafe-link-source")
        val workspace = File(source, "creator_workspaces/workspaces/workspace-1")
        assertTrue(workspace.mkdirs())
        val outside = File(source, "outside.txt").apply { writeText("secret") }
        createLinkOrSkip(File(workspace, "manifest.json"), outside)

        assertThrows(IllegalArgumentException::class.java) {
            collectBackupArchiveTree(source, listOf("creator_workspaces/workspaces"))
        }
    }

    private fun createLinkOrSkip(link: File, target: File) {
        runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }
            .exceptionOrNull()?.let(::assumeNoException)
    }
}
