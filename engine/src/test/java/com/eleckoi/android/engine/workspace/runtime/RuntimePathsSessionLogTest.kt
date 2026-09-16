package com.eleckoi.android.engine.workspace.runtime

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
