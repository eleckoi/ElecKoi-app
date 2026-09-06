package com.eleckoi.android.app.service.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class StreamingBackupArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `version 3 archive validates and restores each entry independently`() = runBlocking {
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
            zip.write(if (name == targetName) replacement.toByteArray() else bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}
