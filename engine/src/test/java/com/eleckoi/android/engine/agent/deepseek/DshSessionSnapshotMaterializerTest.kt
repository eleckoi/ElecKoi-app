package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.adapter.request.AgentHistoryProjection
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshSessionSnapshotMaterializerTest {
    @Test
    fun `long conversation with historical images is not blocked by the old snapshot cap`() {
        val home = Files.createTempDirectory("dsh-large-session-snapshot").toFile()
        try {
            val materializer = DshSessionSnapshotMaterializer(home)
            val model = ModelConfig(
                id = "root",
                provider = "openai",
                model = "gpt-test",
                apiFormat = ModelApiFormat.Responses,
            )
            val imageData = "A".repeat(9 * 1024 * 1024)
            val history = (1..58).map { index ->
                AgentHistoryItem(
                    if (index == 1) {
                        """{"type":"message","role":"user","content":[{"type":"input_image","image_url":"data:image/jpeg;base64,$imageData"}]}"""
                    } else {
                        """{"type":"message","role":"user","content":"Turn $index"}"""
                    },
                )
            }

            materializer.write(
                sessionId = "long-session",
                turnToken = "turn-58",
                mountedPresetId = "eleckoi-preset",
                model = model,
                modelProvider = "openai-eleckoi-route",
                subagentModel = model,
                subagentProvider = "openai-eleckoi-route",
                turnContext = materializer.emptyContext(history, AgentHistoryProjection.SeedProductHistory),
            )

            val file = File(home, "eleckoi/session-snapshots/long-session.json")
            assertTrue(file.length() > 8L * 1024L * 1024L)
            assertEquals(58, Json.parseToJsonElement(file.readText()).jsonObject.getValue("history").jsonArray.size)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `writes the mounted preset and already resolved provider routes only`() {
        val home = Files.createTempDirectory("dsh-session-snapshot").toFile()
        try {
            val materializer = DshSessionSnapshotMaterializer(home)
            val model = ModelConfig(
                id = "root",
                provider = "openai",
                model = "gpt-test",
                apiFormat = ModelApiFormat.Responses,
            )
            materializer.write(
                sessionId = "session-1",
                turnToken = "turn-1",
                mountedPresetId = "eleckoi-preset",
                model = model,
                modelProvider = "openai-eleckoi-route",
                subagentModel = model.copy(id = "child", model = "gpt-child"),
                subagentProvider = "openai-child-route",
                turnContext = materializer.emptyContext(emptyList(), AgentHistoryProjection.Native),
            )

            val snapshot = Json.parseToJsonElement(
                File(home, "eleckoi/session-snapshots/session-1.json").readText(),
            ).jsonObject
            assertEquals("eleckoi-preset", snapshot.getValue("mountedPresetId").jsonPrimitive.content)
            assertEquals(
                "openai-eleckoi-route",
                snapshot.getValue("model").jsonObject.getValue("provider").jsonPrimitive.content,
            )
            assertEquals(
                "openai-child-route",
                snapshot.getValue("subagentModel").jsonObject.getValue("provider").jsonPrimitive.content,
            )
            assertFalse("contextProjectionSignature" in snapshot)
            assertFalse("historyCompactionInstructions" in snapshot)
        } finally {
            home.deleteRecursively()
        }
    }
}
