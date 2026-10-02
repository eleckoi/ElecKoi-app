package com.eleckoi.android.engine.creator.plugins

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PluginWorldbookProjectionTest {
    @Test fun `bound books honor constant keyword secondary scan depth and disabled entries`() {
        val book = Json.parseToJsonElement("""{"entries":[
          {"uid":1,"content":"constant","strategy":{"type":"constant"}},
          {"uid":2,"content":"disabled","enabled":false,"strategy":{"type":"constant"}},
          {"uid":3,"content":"keyword","strategy":{"type":"selective","keys":["tower"],"keys_secondary":{"logic":"and_all","keys":["Alice"]}},"position":{"role":"user","depth":2,"order":3}},
          {"uid":4,"content":"too old","strategy":{"type":"selective","keys":["old"],"scan_depth":1}},
          {"uid":5,"content":"scan injection","strategy":{"type":"selective","keys":["river"]}}
        ]}""").jsonObject
        val entries = projectPluginWorldbooks(mapOf("book" to book), listOf("old", "Alice at tower"), "river", "a")
        assertEquals(listOf("constant", "keyword", "scan injection"), entries.map { it.jsonObject.getValue("content").jsonPrimitive.content })
        assertEquals("a", entries[1].jsonObject.getValue("conversationId").jsonPrimitive.content)
        assertEquals(2, entries[1].jsonObject.getValue("depth").jsonPrimitive.int)
        PluginPromptPipeline.set("book-test", entries)
        try {
            assertEquals("user", PluginPromptPipeline.snapshot("a").first { it.content == "keyword" }.role.wireValue)
            assertTrue(PluginPromptPipeline.snapshot("b").isEmpty())
        } finally { PluginPromptPipeline.remove("book-test") }
    }
}
