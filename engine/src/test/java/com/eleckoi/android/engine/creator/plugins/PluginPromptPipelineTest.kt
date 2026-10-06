package com.eleckoi.android.engine.creator.plugins

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PluginPromptPipelineTest {
    @Test fun `once injection freezes per turn and does not consume another chat prompt`() {
        val owner = "test-once"
        val entries = Json.parseToJsonElement("""[{"id":"a","conversationId":"a","once":true,"depth":2,"role":"user","content":"memory"},{"id":"b","conversationId":"b","content":"other"}]""").jsonArray
        PluginPromptPipeline.set(owner, entries)
        try {
            val first = PluginPromptPipeline.snapshot("a")
            assertEquals("memory", first.single().content); assertEquals(2, first.single().historyDepth)
            assertTrue(PluginPromptPipeline.snapshot("a").isEmpty())
            assertEquals("other", PluginPromptPipeline.snapshot("b").single().content)
            assertEquals("memory", first.single().content)
        } finally { PluginPromptPipeline.remove(owner) }
    }
}
