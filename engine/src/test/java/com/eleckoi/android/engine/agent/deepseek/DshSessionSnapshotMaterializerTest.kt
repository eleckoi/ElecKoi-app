package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.adapter.request.AgentHistoryProjection
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DshSessionSnapshotMaterializerTest {
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
