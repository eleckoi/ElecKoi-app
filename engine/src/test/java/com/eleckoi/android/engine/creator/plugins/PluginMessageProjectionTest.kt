package com.eleckoi.android.engine.creator.plugins

import com.eleckoi.android.engine.agent.api.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PluginMessageProjectionTest {
    @Test fun `depth role and order are preserved around actual history`() {
        val history = Json.parseToJsonElement("""[{"role":"user","content":"old"},{"role":"assistant","content":"reply"},{"role":"user","content":"new"}]""").jsonArray
        fun injection(id: String, order: Int, depth: Int?) = AgentContextInjection(id, AgentContextAnchor.BeforeLatestUserInput, AgentContextRole.System, AgentContextActivation.FirstModelRequest, id, order = order, historyDepth = depth)
        val projected = projectPluginMessages(history, listOf(injection("last", 0, 0), injection("second", 2, 1), injection("first", 1, 1)))
        assertEquals(listOf("old", "reply", "first", "second", "new", "last"), projected.map { it.jsonObject.getValue("content").jsonPrimitive.content })
        assertEquals("system", projected[2].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(3, history.size)
    }
    @Test fun `removing an owner clears its macros while keeping other owners`() {
        PluginPromptPipeline.registerMacro("test-one", "first", "one")
        PluginPromptPipeline.registerMacro("test-two", "second", "two")
        try {
            PluginPromptPipeline.remove("one")
            assertEquals("{{test-one}} second", PluginPromptPipeline.expand("{{test-one}} {{test-two}}"))
        } finally { PluginPromptPipeline.remove("two") }
    }
}
