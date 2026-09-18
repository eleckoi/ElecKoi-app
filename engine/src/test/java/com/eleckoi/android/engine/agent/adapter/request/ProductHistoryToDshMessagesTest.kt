package com.eleckoi.android.engine.agent.adapter.request

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductHistoryToDshMessagesTest {
    @Test
    fun `keeps dialogue reasoning and paired tool records but drops stale system entries`() {
        val converted = ProductHistoryToDshMessages.convert(
            listOf(
                item("""{"type":"message","role":"system","content":"old system"}"""),
                item("""{"type":"message","role":"user","content":[{"type":"input_text","text":"question"}]}"""),
                item("""{"type":"reasoning","summary":[{"type":"summary_text","text":"thought"}]}"""),
                item("""{"type":"function_call","call_id":"call-1","name":"lookup","arguments":"{\"q\":1}"}"""),
                item("""{"type":"function_call_output","call_id":"call-1","output":"answer"}"""),
                item("""{"type":"message","role":"assistant","content":[{"type":"output_text","text":"done"}]}"""),
            ),
        )

        assertEquals(listOf("user", "assistant", "assistant", "user", "assistant"), converted.map(::role))
        assertEquals("reasoning", blockType(converted[1]))
        assertEquals("tool-call", blockType(converted[2]))
        assertEquals("tool-result", blockType(converted[3]))
        assertEquals("text", blockType(converted[4]))
        assertTrue(converted.none { it.toString().contains("old system") })
    }

    @Test
    fun `normalizes historical data images for DSH attachment admission`() {
        val converted = ProductHistoryToDshMessages.convert(
            listOf(item("""{"type":"message","role":"user","content":[{"type":"input_image","image_url":"data:image/png;base64,AAAA"}]}""")),
        )

        val block = converted.single().getValue("content").jsonArray.single().jsonObject
        assertEquals("eleckoi-data-image", block.getValue("type").jsonPrimitive.content)
        assertEquals("data:image/png;base64,AAAA", block.getValue("dataUrl").jsonPrimitive.content)
    }

    private fun item(value: String): JsonObject = ElecKoiJson.parseToJsonElement(value).jsonObject

    private fun role(message: JsonObject): String = message.getValue("role").jsonPrimitive.content

    private fun blockType(message: JsonObject): String = message.getValue("content")
        .jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content
}
