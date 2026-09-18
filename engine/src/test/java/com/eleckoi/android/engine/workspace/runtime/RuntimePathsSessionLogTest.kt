package com.eleckoi.android.engine.workspace.runtime

import com.github.luben.zstd.ZstdOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimePathsSessionLogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `regular session log is accepted`() {
        val session = temporaryFolder.newFolder("regular-session")
        val log = File(session, "session.jsonl.zstd").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertEquals(log.absoluteFile, resolvePersistentDshSessionLog(session))
    }

    @Test
    fun `highest valid format generation wins`() {
        val session = temporaryFolder.newFolder("versioned-session")
        File(session, "session.jsonl").writeText("legacy")
        File(session, "session.v2.jsonl.zstd").writeBytes(byteArrayOf(1))
        val latest = File(session, "session.v3.jsonl").apply { writeText("latest") }
        File(session, "session.v999999999999999999999999999999.jsonl").writeText("overflow")
        File(session, "session.v4.jsonl.tmp").writeText("temporary")

        assertEquals(latest.absoluteFile, resolvePersistentDshSessionLog(session))
    }

    @Test
    fun `header reader supports raw and compressed generations`() {
        val session = temporaryFolder.newFolder("header-session")
        val raw = File(session, "session.v2.jsonl").apply {
            writeText(
                """{"type":"session","version":2,"id":"raw-id"}""" + "\n" +
                    """{"type":"event"}""" + "\n",
            )
        }
        val compressed = File(session, "session.v3.jsonl.zstd")
        ZstdOutputStream(compressed.outputStream()).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("""{"type":"session","version":3,"id":"compressed-id"}""")
            writer.appendLine("""{"type":"event"}""")
        }

        assertEquals("raw-id", readPersistentDshSessionHeaderId(raw))
        assertEquals("compressed-id", readPersistentDshSessionHeaderId(compressed))
    }

    @Test
    fun `encoded directory is located by logical header id`() {
        val sessions = temporaryFolder.newFolder("sessions")
        val project = File(sessions, "encoded-project").apply { mkdir() }
        val encoded = File(project, "s-a71f26").apply { mkdir() }
        val log = File(encoded, "session.v3.jsonl").apply {
            writeText("""{"type":"session","version":3,"id":"logical:session-1"}""" + "\n")
        }

        assertEquals(log.absoluteFile, findPersistentDshSessionLog(sessions, "logical:session-1"))
        assertNull(findPersistentDshSessionLog(sessions, "s-a71f26"))
    }

    @Test
    fun `deletion targets encoded directory by logical header id`() {
        val sessions = temporaryFolder.newFolder("delete-sessions")
        val project = File(sessions, "project-hash").apply { mkdir() }
        val target = File(project, "encoded-target").apply { mkdir() }
        File(target, "session.v3.jsonl").writeText(
            """{"type":"session","version":3,"id":"target-session"}""" + "\n",
        )
        val kept = File(project, "encoded-kept").apply { mkdir() }
        File(kept, "session.v3.jsonl").writeText(
            """{"type":"session","version":3,"id":"kept-session"}""" + "\n",
        )

        deletePersistentDshSessionDirectories(sessions, setOf("target-session"))

        assertEquals(false, target.exists())
        assertEquals(true, kept.isDirectory)
    }

    @Test
    fun `rotated symlink chain inside session is accepted with logical compressed name`() {
        val session = temporaryFolder.newFolder("linked-session")
        val finalLog = File(session, ".session.jsonl.zstd.segment").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val rotatingLog = File(session, ".session.jsonl.zstd.rotating")
        val logicalLog = File(session, "session.jsonl.zstd")
        createSymbolicLinkOrSkip(rotatingLog, finalLog)
        createSymbolicLinkOrSkip(logicalLog, rotatingLog)

        val resolved = resolvePersistentDshSessionLog(session)

        assertEquals(logicalLog.absoluteFile, resolved)
        assertEquals("session.jsonl.zstd", resolved?.name)
    }

    @Test
    fun `session log link escaping its session is rejected`() {
        val session = temporaryFolder.newFolder("escaped-session")
        val outside = temporaryFolder.newFile("outside.jsonl.zstd").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val logicalLog = File(session, "session.jsonl.zstd")
        createSymbolicLinkOrSkip(logicalLog, outside)

        assertNull(resolvePersistentDshSessionLog(session))
    }

    private fun createSymbolicLinkOrSkip(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (error: Exception) {
            assumeNoException("This filesystem cannot create symbolic links", error)
        }
    }
}
