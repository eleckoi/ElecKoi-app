package com.eleckoi.android.engine.agent.adapter.request

import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshRequestContextProjectorTest {
    @Test
    fun `keeps same-role insertions separate until pi-ai serializes the target protocol`() {
        val request = dshRequest(message("current", "user", "现在的问题", "user"))
        val context = context(
            injections = listOf(
                injection("before", AgentContextAnchor.BeforeHistory, AgentContextRole.User, "前置用户上下文"),
            ),
        )

        val messages = DshRequestContextProjector.project(request, context, 1).messages()

        assertEquals(listOf("user", "user"), messages.map { it.string("role") })
        assertEquals(listOf("前置用户上下文", "现在的问题"), messages.map { it.text() })
    }

    @Test
    fun `keeps hidden timeline before the setting placed immediately before tool flow`() {
        val request = dshRequest(message("current", "user", "现在的问题", "user"))
        val context = context(
            injections = listOf(
                injection(
                    id = "built-in-hidden-tool-timeline",
                    anchor = AgentContextAnchor.BeforeToolFlow,
                    role = AgentContextRole.User,
                    content = "隐藏工具时间线",
                    order = 1,
                ),
                injection(
                    id = "000-test-setting",
                    anchor = AgentContextAnchor.BeforeToolFlow,
                    role = AgentContextRole.Assistant,
                    content = "测试设定",
                    order = 2,
                ),
            ),
        )

        val messages = DshRequestContextProjector.project(request, context, 1).messages()

        assertEquals(
            listOf("现在的问题", "隐藏工具时间线", "测试设定"),
            messages.map { it.text() },
        )
        assertEquals(listOf("user", "user", "assistant"), messages.map { it.string("role") })
    }

    @Test
    fun `keeps history latest-input and tool-flow boundaries in product order`() {
        val request = dshRequest(
            message("old-user", "user", "旧问题", "user"),
            message("old-assistant", "assistant", "旧回答", "model"),
            message("current", "user", "最新问题", "user"),
        )
        val context = context(
            injections = listOf(
                injection("after-history", AgentContextAnchor.AfterHistory, AgentContextRole.User, "历史之后", order = 1),
                injection("before-latest", AgentContextAnchor.BeforeLatestUserInput, AgentContextRole.User, "最新输入之前", order = 2),
                injection("after-latest", AgentContextAnchor.AfterLatestUserInput, AgentContextRole.User, "最新输入之后", order = 3),
                injection("before-tools", AgentContextAnchor.BeforeToolFlow, AgentContextRole.User, "工具流程之前", order = 4),
            ),
            userMessage = "最新问题",
        )

        val messages = DshRequestContextProjector.project(request, context, 1).messages()

        assertEquals(
            listOf("旧问题", "旧回答", "历史之后", "最新输入之前", "最新问题", "最新输入之后", "工具流程之前"),
            messages.map { it.text() },
        )
    }

    @Test
    fun `projects tool call result and after-tool-flow without pretending the result is dialogue`() {
        val request = dshRequest(
            message("current", "user", "查一下天气", "user"),
            toolCall("call-1", "weather", "{\"city\":\"台北\"}"),
            toolResult("call-1", "晴天"),
        )
        val context = context(
            injections = listOf(
                injection(
                    id = "after-weather",
                    anchor = AgentContextAnchor.AfterToolFlow,
                    role = AgentContextRole.System,
                    content = "结合工具结果回答",
                    activation = AgentContextActivation.AfterToolCall("weather"),
                ),
            ),
        )

        val messages = DshRequestContextProjector.project(request, context, 2).messages()

        assertEquals(listOf("user", "assistant", "user", "system"), messages.map { it.string("role") })
        assertEquals("tool-call", messages[1].blockTypes().single())
        assertEquals("tool-result", messages[2].blockTypes().single())
        assertEquals("结合工具结果回答", messages.last().text())
    }

    @Test
    fun `replaces previous native turns with product history in DSH message shape`() {
        val request = dshRequest(
            message("old-user", "user", "native old", "user"),
            message("old-assistant", "assistant", "native answer", "model"),
            message("current", "user", "current", "user"),
        )
        val context = AgentTurnRequestContext(
            userMessage = "current",
            history = listOf(
                legacyHistory("user", "product old"),
                legacyHistory("assistant", "product answer"),
            ),
            injections = emptyList(),
            historyProjection = AgentHistoryProjection.ReplacePreviousTurns,
        )

        val messages = DshRequestContextProjector.project(request, context, 1).messages()

        assertEquals(listOf("product old", "product answer", "current"), messages.map { it.text() })
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.string("role") })
        assertTrue(messages.take(2).all { it.stringObject("source")?.string("plugin") == "eleckoi-product-history" })
    }

    @Test
    fun `tool argument activation reads DSH tool-call blocks`() {
        val request = dshRequest(
            message("current", "user", "create", "user"),
            toolCall("call-2", "generate_image", "{\"tags\":[\"night\",\"rain\"]}"),
            toolResult("call-2", "ok"),
        )
        val context = context(
            injections = listOf(
                injection(
                    id = "night-rule",
                    anchor = AgentContextAnchor.AfterToolFlow,
                    role = AgentContextRole.Assistant,
                    content = "night matched",
                    activation = AgentContextActivation.AfterToolCallArgumentContains(
                        toolName = "generate_image",
                        argumentName = "tags",
                        value = "night",
                    ),
                ),
            ),
        )

        val messages = DshRequestContextProjector.project(request, context, 2).messages()

        assertEquals("assistant", messages.last().string("role"))
        assertEquals("night matched", messages.last().text())
    }

    @Test
    fun `projects a Room data image once then preserves DSH durable attachment history`() {
        val history = listOf(legacyImageHistory("old picture", "data:image/png;base64,AQID"))
        val freshRequest = dshRequest(message("current", "user", "current", "user"))
        val context = AgentTurnRequestContext(
            userMessage = "current",
            history = history,
            injections = emptyList(),
            historyProjection = AgentHistoryProjection.ReplacePreviousTurns,
        )

        val freshHistory = DshRequestContextProjector.project(freshRequest, context, 1).messages().first()
        val freshBlocks = freshHistory["content"] as JsonArray
        assertEquals("eleckoi-data-image", freshBlocks[1].jsonObject.string("type"))
        assertEquals("data:image/png;base64,AQID", freshBlocks[1].jsonObject.string("dataUrl"))

        val durable = nativeImageMessage("old picture")
        val continuedRequest = dshRequest(
            durable,
            message("current", "user", "current", "user"),
        )
        val continuedHistory = DshRequestContextProjector.project(continuedRequest, context, 2).messages().first()

        assertEquals(durable, continuedHistory)
        assertEquals("image", (continuedHistory["content"] as JsonArray)[1].jsonObject.string("type"))
    }

    @Test
    fun `long history projection is deterministic and does not mutate its source`() {
        val history = (0 until 1_000).map { index ->
            legacyHistory(if (index % 2 == 0) "user" else "assistant", "history-$index")
        }
        val request = dshRequest(message("current", "user", "current", "user"))
        val context = AgentTurnRequestContext(
            userMessage = "current",
            history = history,
            injections = emptyList(),
            historyProjection = AgentHistoryProjection.SeedProductHistory,
        )

        val first = DshRequestContextProjector.project(request, context, 1)
        val second = DshRequestContextProjector.project(request, context, 1)

        assertEquals(first, second)
        assertEquals(1, request.messages().size)
        assertEquals(1_001, first.messages().size)
        assertFalse(first.toString().contains("input_text"))
    }

    private fun context(
        injections: List<AgentContextInjection>,
        userMessage: String = "查一下天气".takeIf { injections.any { it.id == "after-weather" } }
            ?: "create".takeIf { injections.any { it.id == "night-rule" } }
            ?: "现在的问题",
    ) = AgentTurnRequestContext(
        userMessage = userMessage,
        history = emptyList(),
        injections = injections,
        historyProjection = AgentHistoryProjection.Native,
    )

    private fun injection(
        id: String,
        anchor: AgentContextAnchor,
        role: AgentContextRole,
        content: String,
        activation: AgentContextActivation = AgentContextActivation.Immediate,
        order: Int = 1,
    ) = AgentContextInjection(id, anchor, role, activation, content, order)

    private fun dshRequest(vararg messages: JsonObject) = buildJsonObject {
        put("provider", "eleckoi-bridge")
        put("model", "route")
        put("sessionId", "session-1")
        put("messages", JsonArray(messages.toList()))
    }

    private fun message(id: String, role: String, text: String, sourceKind: String) = buildJsonObject {
        put("id", id)
        put("role", role)
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        })
        put("source", buildJsonObject {
            put("kind", sourceKind)
            if (sourceKind == "model") {
                put("provider", "eleckoi-bridge")
                put("model", "route")
            }
        })
    }

    private fun toolCall(id: String, name: String, arguments: String) = buildJsonObject {
        put("id", "message-$id")
        put("role", "assistant")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "tool-call")
                put("id", id)
                put("name", name)
                put("arguments", arguments)
            })
        })
        put("source", buildJsonObject {
            put("kind", "model")
            put("provider", "eleckoi-bridge")
            put("model", "route")
        })
    }

    private fun toolResult(id: String, text: String) = buildJsonObject {
        put("id", "result-$id")
        put("role", "user")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "tool-result")
                put("toolCallId", id)
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                })
            })
        })
        put("source", buildJsonObject {
            put("kind", "tool")
            put("callId", id)
        })
    }

    private fun legacyHistory(role: String, text: String) = AgentHistoryItem(
        buildJsonObject {
            put("type", "message")
            put("role", role)
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", if (role == "assistant") "output_text" else "input_text")
                    put("text", text)
                })
            })
        }.toString(),
    )

    private fun legacyImageHistory(text: String, dataUrl: String) = AgentHistoryItem(
        buildJsonObject {
            put("type", "message")
            put("role", "user")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "input_text")
                    put("text", text)
                })
                add(buildJsonObject {
                    put("type", "input_image")
                    put("image_url", dataUrl)
                })
            })
        }.toString(),
    )

    private fun nativeImageMessage(text: String) = buildJsonObject {
        put("id", "native-image")
        put("role", "user")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
            add(buildJsonObject {
                put("type", "image")
                put("attachment", buildJsonObject {
                    put("attachmentId", "image-1")
                    put("mediaType", "image/png")
                    put("bytes", 3)
                    put("width", 1)
                    put("height", 1)
                })
            })
        })
        put("source", buildJsonObject { put("kind", "user") })
    }

    private fun JsonObject.messages(): List<JsonObject> =
        (get("messages") as JsonArray).map { it as JsonObject }

    private fun JsonObject.blockTypes(): List<String> =
        (get("content") as JsonArray).mapNotNull { (it as? JsonObject)?.string("type") }

    private fun JsonObject.text(): String =
        (get("content") as JsonArray).mapNotNull { (it as? JsonObject)?.string("text") }.joinToString("\n")

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.stringObject(name: String): JsonObject? = get(name) as? JsonObject
}
