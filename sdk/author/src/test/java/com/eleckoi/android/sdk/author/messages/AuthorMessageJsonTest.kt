package com.eleckoi.android.sdk.author.messages

import com.eleckoi.android.sdk.author.AuthorMessageSnapshot
import com.eleckoi.android.sdk.author.AuthorAgentProcessSnapshot
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorMessageJsonTest {
    @Test
    fun exposesPcMessageContract() {
        val json = AuthorMessageSnapshot(
            id = "m1",
            conversationId = "conversation-1",
            role = "assistant",
            content = "完成",
            reasoningContent = "检查变量",
            provider = "",
            model = "",
            createdAt = "",
            pending = false,
            variableStateJson = "{\"favor\":2}",
            turnId = "turn-1",
            process = listOf(
                AuthorAgentProcessSnapshot(
                    id = "call-1",
                    kind = "tool",
                    status = "complete",
                    toolName = "eleckoi_apply_variable_patch",
                    arguments = "",
                    summary = "更新剧情变量",
                    detail = "完成",
                    startedAtMillis = 1L,
                    completedAtMillis = 2L,
                ),
            ),
        ).toAuthorMessageJson()

        assertEquals("conversation-1", json["conversationId"]?.jsonPrimitive?.content)
        assertEquals("complete", json["status"]?.jsonPrimitive?.content)
        assertEquals("2", json["variableState"]?.jsonObject?.get("favor")?.jsonPrimitive?.content)
        val process = json["process"]?.jsonArray?.single()?.jsonObject
        assertEquals("eleckoi_apply_variable_patch", process?.get("toolName")?.jsonPrimitive?.content)
        assertTrue("reasoningContent" !in json)
        assertTrue("toolCalls" !in json)
    }
}
