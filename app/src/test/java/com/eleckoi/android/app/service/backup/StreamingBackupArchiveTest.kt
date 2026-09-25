package com.eleckoi.android.app.service.backup

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class StreamingBackupArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `version 4 archive validates and restores each entry independently`() = runBlocking {
        val source = temporaryFolder.newFile("large.bin").apply {
            outputStream().use { output -> repeat(8_192) { output.write(it and 0xff) } }
        }
        val output = ByteArrayOutputStream()
        StreamingBackupArchiveWriter(output).use { writer ->
            writer.writeFile("files/data/large.bin", source)
            writer.writeText("characters.json", "{\"items\":[]}")
            writer.writeText("chats/character/session.json", "{\"sessions\":[]}", BackupEntryKindChat, "character")
            writer.finish(
                directories = listOf("files/data"),
                excluded = emptyList(),
                characterCount = 0,
                sessionCount = 1,
                creatorWorkspaceCount = 0,
                creatorConversationCount = 0,
            )
        }

        val inspection = inspectBackupArchive(
            ByteArrayInputStream(output.toByteArray()),
            maximumPayloadBytes = 1_000_000,
            maximumEntryCount = 20,
        ) as StreamingBackupArchive
        assertEquals(3, inspection.manifest.entries.size)
        assertEquals(8_192L + "{\"items\":[]}".toByteArray().size +
            "{\"sessions\":[]}".toByteArray().size, inspection.payloadBytes)

        val restored = linkedMapOf<String, ByteArray>()
        readStreamingBackupEntries(
            input = ByteArrayInputStream(output.toByteArray()),
            manifest = inspection.manifest,
        ) { entry, input ->
            restored[entry.name] = ByteArrayOutputStream().use { bytes ->
                copyBackupStream(input, bytes)
                bytes.toByteArray()
            }
        }
        assertTrue(restored.getValue("files/data/large.bin").contentEquals(source.readBytes()))
        assertEquals("{\"items\":[]}", restored.getValue("characters.json").toString(Charsets.UTF_8))
    }

    @Test
    fun `project symbolic link is stored as its target path rather than target bytes`() = runBlocking {
        val output = ByteArrayOutputStream()
        val name = "files/creator_workspaces/workspaces/workspace-1/project/reference.txt"
        StreamingBackupArchiveWriter(output).use { writer ->
            writer.writeSymbolicLink(name, encodeBackupSymbolicLinkTarget("../original.txt", "/app/files"))
            writer.writeText("characters.json", "{\"items\":[]}")
            writer.finish(emptyList(), emptyList(), 0, 0, 1, 0)
        }

        val inspection = inspectBackupArchive(
            ByteArrayInputStream(output.toByteArray()),
            maximumPayloadBytes = 1_000_000,
            maximumEntryCount = 10,
        ) as StreamingBackupArchive
        assertEquals(4, inspection.version)
        assertEquals(BackupEntryKindSymbolicLink, inspection.manifest.entries.first().kind)
        val contents = mutableMapOf<String, String>()
        readStreamingBackupEntries(
            ByteArrayInputStream(output.toByteArray()),
            inspection.manifest,
        ) { entry, input ->
            contents[entry.name] = input.readBytes().toString(Charsets.UTF_8)
        }
        assertEquals("../original.txt", decodeBackupSymbolicLinkTarget(contents.getValue(name), "/app/files"))
    }

    @Test
    fun `version 3 backups remain importable`() = runBlocking {
        val output = ByteArrayOutputStream()
        StreamingBackupArchiveWriter(output).use { writer ->
            writer.writeText("characters.json", "{\"items\":[]}")
            writer.finish(emptyList(), emptyList(), 0, 0, 0, 0)
        }
        val olderArchive = rewriteEntry(output.toByteArray(), BackupManifestEntry) { json ->
            ElecKoiPrettyJson.encodeToString(
                ElecKoiJson.decodeFromString<StreamingBackupManifest>(json).copy(version = 3),
            )
        }

        val inspection = inspectBackupArchive(
            ByteArrayInputStream(olderArchive),
            maximumPayloadBytes = 1_000_000,
            maximumEntryCount = 10,
        ) as StreamingBackupArchive
        assertEquals(3, inspection.version)
    }

    @Test
    fun `absolute app file links rebase without changing ordinary relative links`() {
        val encoded = encodeBackupSymbolicLinkTarget(
            "/old/app/files/creator_workspaces/workspaces/one/project/base.txt",
            "/old/app/files",
        )
        assertEquals(
            File(
                "/new/app/files",
                "creator_workspaces/workspaces/one/project/base.txt",
            ).absolutePath,
            decodeBackupSymbolicLinkTarget(encoded, "/new/app/files"),
        )
        assertEquals(
            "@files/literal.txt",
            decodeBackupSymbolicLinkTarget(
                encodeBackupSymbolicLinkTarget("@files/literal.txt", "/old/app/files"),
                "/new/app/files",
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            decodeBackupSymbolicLinkTarget(
                "{\"target\":\"../outside\",\"relativeToAppFiles\":true}",
                "/new/app/files",
            )
        }
    }

    @Test
    fun `creator project backup restores a linked file at the new app path`() = runBlocking {
        val sourceRoot = temporaryFolder.newFolder("link-backup-source")
        val projectPath = "creator_workspaces/workspaces/one/project"
        val original = File(sourceRoot, "$projectPath/base.txt").apply {
            parentFile?.mkdirs()
            writeText("project-data")
        }
        runCatching {
            Files.createSymbolicLink(File(original.parentFile, "alias.txt").toPath(), original.toPath())
        }.exceptionOrNull()?.let(::assumeNoException)
        val tree = collectBackupArchiveTree(sourceRoot, listOf("creator_workspaces/workspaces"))
        val output = ByteArrayOutputStream()
        StreamingBackupArchiveWriter(output).use { writer ->
            tree.files.forEach { writer.writeFile(it.entryName, it.file) }
            tree.symbolicLinks.forEach {
                writer.writeSymbolicLink(
                    it.entryName,
                    encodeBackupSymbolicLinkTarget(it.target, sourceRoot.absolutePath),
                )
            }
            writer.writeText("characters.json", "{\"items\":[]}")
            writer.finish(tree.directories, emptyList(), 0, 0, 1, 0)
        }

        val inspection = inspectBackupArchive(
            ByteArrayInputStream(output.toByteArray()),
            maximumPayloadBytes = 1_000_000,
            maximumEntryCount = 100,
        ) as StreamingBackupArchive
        val restoredRoot = temporaryFolder.newFolder("link-backup-restored")
        val swap = BackupFileRootSwap(restoredRoot, listOf("creator_workspaces/workspaces"))
        restoreBackupDirectories(swap.stagingRoot, inspection.manifest.directories)
        val links = mutableListOf<Pair<String, String>>()
        readStreamingBackupEntries(ByteArrayInputStream(output.toByteArray()), inspection.manifest) { entry, input ->
            when (entry.kind) {
                BackupEntryKindFile -> {
                    val file = resolveBackupEntry(swap.stagingRoot, entry.name)
                    file.parentFile?.mkdirs()
                    file.outputStream().use { copyBackupStream(input, it) }
                }
                BackupEntryKindSymbolicLink -> links += entry.name to input.readBytes().toString(Charsets.UTF_8)
            }
        }
        links.forEach { (name, payload) ->
            restoreBackupSymbolicLink(
                swap.stagingRoot,
                name,
                decodeBackupSymbolicLinkTarget(payload, restoredRoot.absolutePath),
            )
        }
        swap.commit(inspection.manifest.directories + inspection.manifest.entries.map { it.name })
        swap.finish()

        val restored = File(restoredRoot, "$projectPath/alias.txt")
        assertTrue(Files.isSymbolicLink(restored.toPath()))
        assertEquals("project-data", restored.readText())
        assertEquals(
            File(restoredRoot, "$projectPath/base.txt").absolutePath,
            Files.readSymbolicLink(restored.toPath()).toString(),
        )
    }

    @Test
    fun `inspection rejects an archive larger than the available restore budget`() = runBlocking {
        val output = ByteArrayOutputStream()
        StreamingBackupArchiveWriter(output).use { writer ->
            writer.writeText("characters.json", "0123456789")
            writer.finish(emptyList(), emptyList(), 0, 0, 0, 0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                inspectBackupArchive(
                    ByteArrayInputStream(output.toByteArray()),
                    maximumPayloadBytes = 9,
                    maximumEntryCount = 10,
                )
            }
        }
        Unit
    }

    @Test
    fun `manifest detects changed entry bytes`() = runBlocking {
        val output = ByteArrayOutputStream()
        StreamingBackupArchiveWriter(output).use { writer ->
            writer.writeText("characters.json", "{\"items\":[]}")
            writer.finish(emptyList(), emptyList(), 0, 0, 0, 0)
        }
        val archiveBytes = rewriteEntry(output.toByteArray(), "characters.json", "{\"items\":[1]}")

        assertThrows(Exception::class.java) {
            runBlocking {
                inspectBackupArchive(
                    ByteArrayInputStream(archiveBytes),
                    maximumPayloadBytes = 1_000_000,
                    maximumEntryCount = 10,
                )
            }
        }
        Unit
    }
}

private fun rewriteEntry(archive: ByteArray, targetName: String, replacement: String): ByteArray {
    return rewriteEntry(archive, targetName) { replacement }
}

private fun rewriteEntry(archive: ByteArray, targetName: String, replacement: (String) -> String): ByteArray {
    val entries = mutableListOf<Pair<String, ByteArray>>()
    ZipInputStream(ByteArrayInputStream(archive)).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            entries += entry.name to input.readBytes()
            input.closeEntry()
        }
    }
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(if (name == targetName) replacement(bytes.toString(Charsets.UTF_8)).toByteArray() else bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}
