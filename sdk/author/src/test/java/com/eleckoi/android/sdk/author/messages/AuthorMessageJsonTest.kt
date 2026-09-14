package com.eleckoi.android.sdk.author.messages

import com.eleckoi.android.sdk.author.AuthorMessageSnapshot
import com.eleckoi.android.sdk.author.AuthorAgentActivitySnapshot
import com.eleckoi.android.sdk.author.AuthorToolCallSnapshot
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorMessageJsonTest {
    @Test
    fun exposesReasoningAndStructuredToolState() {
        val json = AuthorMessageSnapshot(
            id = "m1",
            role = "assistant",
            content = "完成",
            reasoningContent = "检查变量",
            provider = "",
            model = "",
            createdAt = "",
            pending = false,
            variableStateJson = "",
            toolCalls = listOf(
                AuthorToolCallSnapshot(
                    callId = "call-1",
                    name = "eleckoi_apply_variable_patch",
                    arguments = "",
                    result = "",
                    state = "succeeded",
                    rollbackOnAbort = true,
                ),
            ),
            hasAgentProcess = true,
            agentActivity = AuthorAgentActivitySnapshot(
                label = "正在检查变量",
                running = true,
                thinking = true,
            ),
        ).toAuthorMessageJson()

        assertEquals("检查变量", json["reasoningContent"]?.jsonPrimitive?.content)
        assertTrue(json["hasAgentProcess"]?.jsonPrimitive?.content?.toBoolean() == true)
        assertEquals(
            "正在检查变量",
            json["agentActivity"]?.jsonObject?.get("label")?.jsonPrimitive?.content,
        )
        val call = json["toolCalls"]?.jsonArray?.single()?.jsonObject
        assertEquals("eleckoi_apply_variable_patch", call?.get("name")?.jsonPrimitive?.content)
        assertTrue(call?.get("rollbackOnAbort")?.jsonPrimitive?.content?.toBoolean() == true)
    }
}
