package com.eleckoi.android.engine.agent.deepseek.protocol

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekHarnessJsonRpcClientTest {
    @Test
    fun `performs official handshake prompt and shutdown contract`() = runBlocking {
        val transport = RespondingTransport()
        val client = DeepSeekHarnessJsonRpcClient(transport, this)

        client.start("/workspace", "eleckoi", "test-model", 4096)
        assertEquals(
            DeepSeekPermissionPreset.ApproveForMe,
            client.setPermission(
                sessionId = "session-1",
                cwd = "/workspace/characters/one/剧情小说/project",
                preset = DeepSeekPermissionPreset.ApproveForMe,
            ),
        )
        val inspection = client.inspectSession("session-1")
        val modelInfo = client.resolveModel("eleckoi", "test-model")
        val messageId = client.prompt(
            sessionId = "session-1",
            text = "hello",
            mode = DeepSeekPromptMode.Steer,
            cwd = "/workspace/characters/one/剧情小说/project",
        )
        assertTrue(client.cancel("session-1"))
        assertTrue(client.resolveApproval(7L, DeepSeekApprovalOutcome.AllowedOnce))
        client.shutdown()

        assertEquals("message-1", messageId)
        assertTrue(transport.stopped)
        assertEquals(
            listOf(
                "initialize",
                "session/set_permission",
                "session/inspect",
                "model/resolve",
                "session/prompt",
                "session/cancel",
                "session/resolve_approval",
                "shutdown",
            ),
            transport.requests.map { it.method },
        )
        val initialize = transport.requests.first().params
        assertEquals("/workspace", initialize["cwd"]?.jsonPrimitive?.content)
        assertEquals("eleckoi", initialize["provider"]?.jsonPrimitive?.content)
        assertEquals("test-model", initialize["model"]?.jsonPrimitive?.content)
        assertEquals("4096", initialize["maxTokens"]?.jsonPrimitive?.content)
        assertEquals("approve-for-me", transport.requests[1].params["preset"]?.jsonPrimitive?.content)
        assertTrue("agentPreset" !in transport.requests[1].params)
        assertEquals("session-1", inspection.header["id"]?.jsonPrimitive?.content)
        assertEquals(0, inspection.inheritedEventCount)
        assertEquals("turn/start", inspection.events.single()["type"]?.jsonPrimitive?.content)
        assertEquals(listOf("off", "high"), modelInfo.reasoningEffortIds)
        assertEquals(128_000, modelInfo.contextWindowTokens)
        assertEquals(true, modelInfo.supportsImageInput)
        assertEquals("steer", transport.requests[4].params["mode"]?.jsonPrimitive?.content)
        assertEquals(
            "/workspace/characters/one/剧情小说/project",
            transport.requests[4].params["cwd"]?.jsonPrimitive?.content,
        )
        assertTrue("content" !in transport.requests[4].params)
        assertEquals(
            "hello",
            transport.requests[4].params["contentBlocks"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content,
        )
    }

    private data class Request(val method: String, val params: JsonObject)

    private class RespondingTransport : DeepSeekHarnessTransport {
        private val lines = Channel<String>(Channel.UNLIMITED)
        val requests = mutableListOf<Request>()
        var stopped = false
        override val incomingLines: Flow<String> = lines.receiveAsFlow()

        override suspend fun start() = Unit

        override suspend fun sendLine(line: String) {
            val request = Json.parseToJsonElement(line).jsonObject
            val id = requireNotNull(request["id"])
            val method = requireNotNull(request["method"]?.jsonPrimitive?.content)
            val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())
            requests += Request(method, params)
            val result = when (method) {
                "initialize" -> buildJsonObject {
                    put("serverInfo", buildJsonObject {
                        put("name", "deepseek-harness-sdk-runtime")
                        put("version", "test")
                    })
                }
                "session/prompt" -> buildJsonObject { put("messageId", "message-1") }
                "session/cancel" -> buildJsonObject { put("accepted", true) }
                "session/inspect" -> buildJsonObject {
                    put("header", buildJsonObject {
                        put("type", "session")
                        put("version", 3)
                        put("id", "session-1")
                        put("createdAt", 1)
                    })
                    put("inheritedEventCount", 0)
                    put("events", kotlinx.serialization.json.buildJsonArray {
                        add(buildJsonObject {
                            put("type", "turn/start")
                            put("seq", 0)
                            put("data", buildJsonObject { })
                        })
                    })
                }
                "model/resolve" -> buildJsonObject {
                    put("provider", "eleckoi")
                    put("id", "test-model")
                    put("name", "Test model")
                    put("inputModalities", kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("text"))
                        add(kotlinx.serialization.json.JsonPrimitive("image"))
                    })
                    put("context", buildJsonObject { put("contextWindow", 128_000) })
                    put("reasoning", buildJsonObject {
                        put("efforts", kotlinx.serialization.json.buildJsonArray {
                            add(buildJsonObject { put("id", "off"); put("name", "Off") })
                            add(buildJsonObject { put("id", "high"); put("name", "High") })
                        })
                    })
                }
                "session/set_permission" -> buildJsonObject { put("preset", "approve-for-me") }
                "session/resolve_approval" -> buildJsonObject { put("accepted", true) }
                "shutdown" -> buildJsonObject { }
                else -> error("Unexpected method: $method")
            }
            lines.send(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("result", result)
                }.toString(),
            )
        }

        override suspend fun stop() {
            stopped = true
            lines.close()
        }
    }
}
