package com.eleckoi.android.engine.agent.eleckoi.conversation

import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentContentPartEntity
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationAttachmentCleanupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `shared input and output files survive until the last conversation is deleted`() {
        val input = temporary.newFile("input.png")
        val root = temporary.newFolder("generated")
        val session = File(root, "role").apply { mkdirs() }
        val output = File(session, "output.png").apply { writeText("generated") }
        val parts = mutableListOf(
            part("role", "input_images", input.path), part("role", "images", output.path),
            part("creator", "input_images", input.path), part("copy", "images", output.path),
        )
        val cleanup = ConversationAttachmentCleanup(
            { it() }, { parts.asSequence() }, { File(it).delete() }, ReplyImageGenerator(root),
        )
        cleanup.deleteConversations(listOf("role")) { parts.removeAll { it.conversationId == "role" } }
        assertTrue(input.exists())
        assertTrue(output.exists())
        repeat(2) {
            cleanup.deleteConversations(listOf("creator", "copy")) { parts.clear() }
        }
        assertFalse(input.exists())
        assertFalse(output.exists())
        assertFalse(session.exists())
    }

    @Test fun `long conversations process chunked attachments without loading message bodies`() {
        val root = temporary.newFolder("long")
        val reads = { sequence {
            repeat(5_000) { index ->
                val complete = part("deleted", "input_images", File(root, "$index.png").path)
                    .copy(ownerId = "turn-$index")
                complete.payloadJson.chunked(7).forEachIndexed { chunk, payload ->
                    yield(complete.copy(chunkIndex = chunk, payloadJson = payload))
                }
            }
        } }
        repeat(2) {
            val plan = planAttachmentDeletion(setOf("deleted"), reads)
            assertEquals(5_000, plan.inputPaths.size)
            assertTrue(plan.generatedPaths.isEmpty())
        }
    }

    @Test fun `corrupt metadata and failed file deletion preserve ownership records for retry`() {
        val image = temporary.newFile("retry.png")
        var row = part("deleted", "input_images", image.path)
        var removed = false
        var failDelete = true
        val cleanup = ConversationAttachmentCleanup({ it() }, { sequenceOf(row) }, {
            if (failDelete) throw IOException("locked image")
            File(it).delete()
        }, null)
        assertThrows(IOException::class.java) {
            cleanup.deleteConversations(listOf("deleted")) { removed = true }
        }
        assertFalse(removed)
        assertTrue(image.exists())
        val complete = row
        row = row.copy(payloadJson = "{")
        failDelete = false
        assertThrows(IllegalArgumentException::class.java) {
            cleanup.deleteConversations(listOf("deleted")) { removed = true }
        }
        assertFalse(removed)
        assertTrue(image.exists())
        row = complete
        cleanup.deleteConversations(listOf("deleted")) { removed = true }
        assertTrue(removed)
        assertFalse(image.exists())
    }

    @Test fun `missing chunks cannot silently lose file references`() {
        val row = part("deleted", "input_images", temporary.newFile().path).copy(chunkIndex = 1)
        assertThrows(IllegalStateException::class.java) {
            planAttachmentDeletion(setOf("deleted")) { sequenceOf(row) }
        }
    }

    @Test fun `regeneration deletes discarded attachments but retains previous turns and other branches`() {
        val retained = temporary.newFile("retained.png")
        val discarded = temporary.newFile("discarded.png")
        val shared = temporary.newFile("branch.png")
        val previous = part("chat", "input_images", retained.path).copy(ownerId = "previous")
        val future = part("chat", "input_images", discarded.path).copy(ownerId = "future")
        val branch = part("chat", "input_images", shared.path).copy(ownerId = "branch")
        val parts = mutableListOf(previous, future, branch)
        val cleanup = ConversationAttachmentCleanup({ it() }, { parts.asSequence() }, { File(it).delete() }, null)
        repeat(2) {
            cleanup.discardMessages("chat") { parts.remove(future) }
        }
        assertFalse(discarded.exists())
        assertTrue(retained.exists())
        assertTrue(shared.exists())
    }

    @Test fun `suffix deletion removes user uploads and generated replies but retains earlier files`() {
        val retained = temporary.newFile("retained-input.png")
        val removedInput = temporary.newFile("removed-input.png")
        val generatedRoot = temporary.newFolder("suffix-generated")
        val removedOutput = File(generatedRoot, "removed-output.png").apply { writeText("generated") }
        val parts = mutableListOf(
            part("chat", "input_images", retained.path).copy(ownerId = "retained-turn"),
            part("chat", "input_images", removedInput.path).copy(ownerId = "removed-turn"),
            part("chat", "images", removedOutput.path).copy(ownerType = "response", ownerId = "removed-response"),
        )
        val cleanup = ConversationAttachmentCleanup(
            { it() },
            { parts.asSequence() },
            { File(it).delete() },
            ReplyImageGenerator(generatedRoot),
        )

        cleanup.discardMessages("chat") {
            parts.removeAll { it.ownerId.startsWith("removed-") }
        }

        assertTrue(retained.exists())
        assertFalse(removedInput.exists())
        assertFalse(removedOutput.exists())
    }

    private fun part(conversation: String, kind: String, path: String) = AgentContentPartEntity(
        conversationId = conversation, ownerType = "turn", ownerId = "$conversation-$kind",
        partIndex = 0, kind = kind, text = "",
        payloadJson = buildJsonArray { add(buildJsonObject { put("local_path", path) }) }.toString(),
    )
}
