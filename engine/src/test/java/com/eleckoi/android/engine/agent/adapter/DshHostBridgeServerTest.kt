package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelOption
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshHostBridgeServerTest {
    @Test
    fun `proxies DSH serialized Claude request without protocol translation`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val captured = CompletableDeferred<CapturedRequest>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                captured.complete(readRequest(socket.getInputStream()))
                val response = (
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/event-stream\r\n" +
                        "Connection: close\r\n\r\n" +
                        "event: message_start\r\n" +
                        "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"claude-sonnet-test\",\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":1,\"output_tokens\":0}}}\r\n\r\n" +
                        "event: message_stop\r\n" +
                        "data: {\"type\":\"message_stop\"}\r\n\r\n"
                    )
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = DshHostBridgeServer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "c".repeat(32) },
        )
        val endpoint = server.start()
        val owner = server.registerSessionRoute(
            routeKey = "native-claude",
            routeModelConfig = ModelConfig(
                provider = "anthropic",
                apiKey = "secret-key",
                baseUrl = "http://127.0.0.1:${upstream.localPort}",
                model = "claude-sonnet-test",
                apiFormat = ModelApiFormat.AnthropicMessages,
            ),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("native-claude", owner, "hello")
        val wirePayload = """
            {
              "model":"eleckoi-wire",
              "max_tokens":512,
              "stream":true,
              "messages":[
                {"role":"user","content":"weather?"},
                {"role":"assistant","content":[{"type":"tool_use","id":"call-weather","name":"weather","input":{"city":"Taipei"}}]},
                {"role":"user","content":[{"type":"tool_result","tool_use_id":"call-weather","content":"sunny"}]},
                {"role":"user","content":"answer now"}
              ],
              "tools":[{"name":"weather","description":"lookup","input_schema":{"type":"object","properties":{"city":{"type":"string"}}}}]
            }
        """.trimIndent()
        val wireUrl = endpoint.baseUrl.removeSuffix("/v1") + "/provider-wire/anthropic/v1/messages"
        val bytes = wirePayload.toByteArray()
        val connection = URI(wireUrl).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("anthropic-version", "2023-06-01")
        connection.setRequestProperty("x-deepseek-harness-session-id", "native-claude")
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }

        assertEquals(200, connection.responseCode)
        val downstream = connection.inputStream.bufferedReader().readText()
        val upstreamRequest = withTimeout(2_000) { captured.await() }
        assertEquals("POST /v1/messages HTTP/1.1", upstreamRequest.requestLine)
        assertEquals("secret-key", upstreamRequest.headers["x-api-key"])
        assertTrue(upstreamRequest.body.contains("\"model\":\"claude-sonnet-test\""))
        assertFalse(upstreamRequest.body.contains("eleckoi_internal_route_"))
        assertTrue(upstreamRequest.body.contains("\"name\":\"weather\""))
        assertTrue(upstreamRequest.body.contains("\"type\":\"tool_use\""))
        assertTrue(upstreamRequest.body.contains("\"type\":\"tool_result\""))
        assertFalse(upstreamRequest.body.contains("\"temperature\""))
        assertFalse(upstreamRequest.body.contains("\"top_p\""))
        assertTrue(downstream.contains("event: message_start"))
        assertFalse(downstream.contains("response.completed"))

        connection.disconnect()
        server.endSessionTurn("native-claude", owner)
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `proxies DSH serialized Responses tool round trip in native wire shape`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val captured = CompletableDeferred<CapturedRequest>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                captured.complete(readRequest(socket.getInputStream()))
                val response = (
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/event-stream\r\n" +
                        "Connection: close\r\n\r\n" +
                        "event: response.completed\r\n" +
                        "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"response_1\",\"status\":\"completed\",\"output\":[]}}\r\n\r\n"
                    )
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = DshHostBridgeServer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "r".repeat(32) },
        )
        val endpoint = server.start()
        val owner = server.registerSessionRoute(
            routeKey = "native-responses",
            routeModelConfig = ModelConfig(
                provider = "custom",
                apiKey = "responses-secret",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "gpt-test",
                apiFormat = ModelApiFormat.Responses,
            ),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("native-responses", owner, "hello")
        val wirePayload = """
            {
              "model":"eleckoi-wire",
              "stream":true,
              "input":[
                {"type":"message","role":"user","content":[{"type":"input_text","text":"weather?"}]},
                {"type":"function_call","id":"item-weather","call_id":"call-weather","name":"weather","arguments":"{\"city\":\"Taipei\"}"},
                {"type":"function_call_output","call_id":"call-weather","output":"sunny"},
                {"type":"message","role":"user","content":[{"type":"input_text","text":"answer now"}]}
              ],
              "tools":[{"type":"function","name":"weather","description":"lookup","parameters":{"type":"object","properties":{"city":{"type":"string"}}}}]
            }
        """.trimIndent()

        val (status, downstream) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/provider-wire/responses/v1/responses",
            wirePayload,
            mapOf("x-deepseek-harness-session-id" to "native-responses"),
        )
        val upstreamRequest = withTimeout(2_000) { captured.await() }

        assertEquals(200, status)
        assertEquals("POST /v1/responses HTTP/1.1", upstreamRequest.requestLine)
        assertEquals("Bearer responses-secret", upstreamRequest.headers["authorization"])
        assertTrue(upstreamRequest.body.contains("\"model\":\"gpt-test\""))
        assertFalse(upstreamRequest.body.contains("eleckoi_internal_route_"))
        assertTrue(upstreamRequest.body.contains("\"name\":\"weather\""))
        assertTrue(upstreamRequest.body.contains("\"type\":\"function_call\""))
        assertTrue(upstreamRequest.body.contains("\"type\":\"function_call_output\""))
        assertTrue(downstream.contains("response.completed"))

        server.endSessionTurn("native-responses", owner)
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `proxies DSH serialized Chat Completions tool round trip in native wire shape`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val captured = CompletableDeferred<CapturedRequest>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                captured.complete(readRequest(socket.getInputStream()))
                val response = (
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/event-stream\r\n" +
                        "Connection: close\r\n\r\n" +
                        "data: {\"id\":\"chatcmpl_1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":null}]}\r\n\r\n" +
                        "data: [DONE]\r\n\r\n"
                    )
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = DshHostBridgeServer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "h".repeat(32) },
        )
        val endpoint = server.start()
        val owner = server.registerSessionRoute(
            routeKey = "native-chat",
            routeModelConfig = ModelConfig(
                provider = "custom",
                apiKey = "chat-secret",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "chat-test",
                modelOptions = listOf(
                    ModelOption(id = "chat-test", temperature = 0.0, topP = 0.0),
                ),
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("native-chat", owner, "hello")
        val wirePayload = """
            {
              "model":"eleckoi-wire",
              "stream":true,
              "temperature":0,
              "top_p":0,
              "messages":[
                {"role":"user","content":"weather?"},
                {"role":"assistant","content":null,"tool_calls":[{"id":"call-weather","type":"function","function":{"name":"weather","arguments":"{\"city\":\"Taipei\"}"}}]},
                {"role":"tool","tool_call_id":"call-weather","content":"sunny"},
                {"role":"user","content":"answer now"}
              ],
              "tools":[{"type":"function","function":{"name":"weather","description":"lookup","parameters":{"type":"object","properties":{"city":{"type":"string"}}}}}]
            }
        """.trimIndent()

        val (status, downstream) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/provider-wire/chat/v1/chat/completions",
            wirePayload,
            mapOf("x-deepseek-harness-session-id" to "native-chat"),
        )
        val upstreamRequest = withTimeout(2_000) { captured.await() }

        assertEquals(200, status)
        assertEquals("POST /v1/chat/completions HTTP/1.1", upstreamRequest.requestLine)
        assertEquals("Bearer chat-secret", upstreamRequest.headers["authorization"])
        assertTrue(upstreamRequest.body.contains("\"model\":\"chat-test\""))
        assertTrue(upstreamRequest.body.contains("\"temperature\":0"))
        assertTrue(upstreamRequest.body.contains("\"top_p\":0"))
        assertFalse(upstreamRequest.body.contains("eleckoi_internal_route_"))
        assertTrue(upstreamRequest.body.contains("\"name\":\"weather\""))
        assertTrue(upstreamRequest.body.contains("\"tool_calls\""))
        assertTrue(upstreamRequest.body.contains("\"role\":\"tool\""))
        assertTrue(downstream.contains("chatcmpl_1"))

        server.endSessionTurn("native-chat", owner)
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `keeps Top P bound to its owning session when chats request concurrently`() = runBlocking {
        val upstreamA = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val upstreamB = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val capturedA = CompletableDeferred<CapturedRequest>()
        val capturedB = CompletableDeferred<CapturedRequest>()
        suspend fun serveOnce(server: ServerSocket, captured: CompletableDeferred<CapturedRequest>) {
            server.accept().use { socket ->
                captured.complete(readRequest(socket.getInputStream()))
                val response = (
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/event-stream\r\n" +
                        "Connection: close\r\n\r\n" +
                        "data: {\"id\":\"chatcmpl_sampling\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":null}]}\r\n\r\n" +
                        "data: [DONE]\r\n\r\n"
                    )
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val upstreamJobA = async(Dispatchers.IO) { serveOnce(upstreamA, capturedA) }
        val upstreamJobB = async(Dispatchers.IO) { serveOnce(upstreamB, capturedB) }
        val server = DshHostBridgeServer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        val endpoint = server.start()
        fun routeConfig(name: String, secret: String, port: Int, topP: Double) = ModelConfig(
            provider = "custom",
            apiKey = secret,
            baseUrl = "http://127.0.0.1:$port/v1",
            model = name,
            modelOptions = listOf(ModelOption(id = name, topP = topP)),
            apiFormat = ModelApiFormat.ChatCompletions,
        )
        val ownerA = server.registerSessionRoute(
            routeKey = "sampling-session-a",
            routeModelConfig = routeConfig("model-a", "secret-a", upstreamA.localPort, 0.21),
            routeCaptureProviderRequests = false,
        )
        val ownerB = server.registerSessionRoute(
            routeKey = "sampling-session-b",
            routeModelConfig = routeConfig("model-b", "secret-b", upstreamB.localPort, 0.87),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("sampling-session-a", ownerA, "hello a")
        server.beginSessionTurn("sampling-session-b", ownerB, "hello b")

        fun wirePayload(message: String, topP: Double): String =
            """{"model":"eleckoi-wire","stream":true,"top_p":$topP,"messages":[{"role":"user","content":"$message"}]}"""

        try {
            val wireUrl = endpoint.baseUrl.removeSuffix("/v1") + "/provider-wire/chat/v1/chat/completions"
            val responseA = async(Dispatchers.IO) {
                postJson(
                    wireUrl,
                    wirePayload("hello a", 0.21),
                    mapOf("x-deepseek-harness-session-id" to "sampling-session-a"),
                )
            }
            val responseB = async(Dispatchers.IO) {
                postJson(
                    wireUrl,
                    wirePayload("hello b", 0.87),
                    mapOf("x-deepseek-harness-session-id" to "sampling-session-b"),
                )
            }

            assertEquals(200, responseA.await().first)
            assertEquals(200, responseB.await().first)
            val requestA = withTimeout(2_000) { capturedA.await() }
            val requestB = withTimeout(2_000) { capturedB.await() }
            val jsonA = ElecKoiJson.parseToJsonElement(requestA.body).jsonObject
            val jsonB = ElecKoiJson.parseToJsonElement(requestB.body).jsonObject

            assertEquals("Bearer secret-a", requestA.headers["authorization"])
            assertEquals("Bearer secret-b", requestB.headers["authorization"])
            assertEquals("model-a", jsonA.string("model"))
            assertEquals("model-b", jsonB.string("model"))
            assertEquals(0.21, (jsonA["top_p"] as JsonPrimitive).content.toDouble(), 0.0)
            assertEquals(0.87, (jsonB["top_p"] as JsonPrimitive).content.toDouble(), 0.0)
        } finally {
            server.endSessionTurn("sampling-session-a", ownerA)
            server.endSessionTurn("sampling-session-b", ownerB)
            server.stop()
            upstreamA.close()
            upstreamB.close()
            upstreamJobA.await()
            upstreamJobB.await()
        }
    }

    @Test
    fun `proxies DSH serialized Gemini request through its native model path`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val captured = CompletableDeferred<CapturedRequest>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                captured.complete(readRequest(socket.getInputStream()))
                val response = (
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/event-stream\r\n" +
                        "Connection: close\r\n\r\n" +
                        "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"ok\"}]},\"finishReason\":\"STOP\"}]}\r\n\r\n"
                    )
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = DshHostBridgeServer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "g".repeat(32) },
        )
        val endpoint = server.start()
        val owner = server.registerSessionRoute(
            routeKey = "native-gemini",
            routeModelConfig = ModelConfig(
                provider = "google",
                apiKey = "google-secret",
                baseUrl = "http://127.0.0.1:${upstream.localPort}",
                model = "gemini-test",
                apiFormat = ModelApiFormat.GoogleGemini,
            ),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("native-gemini", owner, "hello")
        val wirePayload = """
            {
              "contents":[
                {"role":"user","parts":[{"text":"weather?"}]},
                {"role":"model","parts":[{"functionCall":{"name":"weather","args":{"city":"Taipei"}}}]},
                {"role":"user","parts":[{"functionResponse":{"name":"weather","response":{"result":"sunny"}}}]},
                {"role":"user","parts":[{"text":"answer now"}]}
              ],
              "generationConfig":{"maxOutputTokens":512},
              "store":true,
              "tools":[{"functionDeclarations":[
                {"name":"weather","description":"lookup","parameters":{"type":"OBJECT","properties":{"city":{"type":"STRING"}}}}
              ]}]
            }
        """.trimIndent()
        val wireUrl = endpoint.baseUrl.removeSuffix("/v1") +
            "/provider-wire/google/models/eleckoi-wire:streamGenerateContent?alt=sse"
        val (_, downstream) = postJson(
            wireUrl,
            wirePayload,
            mapOf("x-deepseek-harness-session-id" to "native-gemini"),
        )
        val upstreamRequest = withTimeout(2_000) { captured.await() }

        assertEquals(
            "POST /v1beta/models/gemini-test:streamGenerateContent?alt=sse HTTP/1.1",
            upstreamRequest.requestLine,
        )
        assertEquals("google-secret", upstreamRequest.headers["x-goog-api-key"])
        val upstreamBody = ElecKoiJson.parseToJsonElement(upstreamRequest.body).jsonObject
        assertTrue("model" !in upstreamBody)
        assertTrue("store" !in upstreamBody)
        assertFalse(upstreamRequest.body.contains("eleckoi_internal_route_"))
        assertTrue(upstreamRequest.body.contains("\"name\":\"weather\""))
        assertTrue(upstreamRequest.body.contains("\"functionCall\""))
        assertTrue(upstreamRequest.body.contains("\"functionResponse\""))
        assertTrue(downstream.contains("\"candidates\""))

        server.endSessionTurn("native-gemini", owner)
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `production bridge does not expose the legacy Responses endpoint`() = runBlocking {
        val server = DshHostBridgeServer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "z".repeat(32) },
        )
        val endpoint = server.start()

        val (status, _) = postJson(
            "${endpoint.baseUrl}/responses",
            """{"model":"route","input":[],"stream":true}""",
        )

        assertEquals(404, status)
        server.stop()
    }

    @Test
    fun `forwards native DSH context pressure without estimating it in Android`() = runBlocking {
        val received = CompletableDeferred<AdapterContextPressure>()
        val server = DshHostBridgeServer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "p".repeat(32) },
        )
        val endpoint = server.start()
        server.registerSessionRoute(
            routeKey = "pressure-session",
            routeModelConfig = ModelConfig(apiKey = "secret-key", model = "test-model"),
            routeCaptureProviderRequests = false,
            onContextPressure = { sample -> received.complete(sample) },
        )
        val payload = """
            {
              "sessionId":"pressure-session",
              "seq":17,
              "value":{
                "pressureTokens":26900,
                "projectedTokens":5100,
                "contextWindow":1000000,
                "systemTokens":21,
                "toolsTokens":551,
                "messageTokens":650
              }
            }
        """.trimIndent().toByteArray()
        val url = endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/context-pressure"
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }

        assertEquals(200, connection.responseCode)
        val sample = withTimeout(2_000) { received.await() }
        assertEquals("pressure-session", sample.sessionId)
        assertEquals(17L, sample.sequence)
        assertEquals(26_900L, sample.pressureTokens)
        assertEquals(5_100L, sample.projectedTokens)
        assertEquals(1_000_000L, sample.contextWindow)
        assertEquals(21L, sample.systemTokens)
        assertEquals(551L, sample.toolsTokens)
        assertEquals(650L, sample.messageTokens)

        connection.disconnect()
        server.stop()
    }

    @Test
    fun `dispatches session scoped Android tool calls`() = runBlocking {
        var receivedArgument = ""
        val tool = AgentDynamicTool(
            definition = AgentToolDefinition(
                name = "eleckoi_test_tool",
                description = "Android host test tool",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("value", buildJsonObject { put("type", "string") })
                    })
                },
            ),
            handler = { arguments ->
                receivedArgument = arguments.string("value").orEmpty()
                AgentDynamicToolResult("host:$receivedArgument")
            },
        )
        val server = DshHostBridgeServer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "h".repeat(32) },
        )
        val endpoint = server.start()
        val ownerToken = server.registerSessionRoute(
            routeKey = "tool-session",
            routeModelConfig = ModelConfig(apiKey = "secret-key", model = "test-model"),
            routeDynamicTools = listOf(tool),
            routeCaptureProviderRequests = false,
        )
        val hostToolBaseUrl = endpoint.baseUrl.removeSuffix("/v1") + "/host-tools"

        fun post(path: String, payload: String): Pair<Int, String> {
            val bytes = payload.toByteArray()
            val connection = URI("$hostToolBaseUrl/$path").toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader()
                .readText()
            connection.disconnect()
            return status to body
        }

        val (callStatus, callBody) = post(
            "call",
            """{"sessionId":"tool-session","name":"eleckoi_test_tool","arguments":{"value":"phone"}}""",
        )
        assertEquals(200, callStatus)
        assertEquals("phone", receivedArgument)
        val result = ElecKoiJson.parseToJsonElement(callBody).jsonObject
        assertEquals("host:phone", result.string("content"))
        assertEquals("true", (result["success"] as JsonPrimitive).content)

        assertTrue(server.registerChildSessionRoute("tool-session", "child-session"))
        assertTrue(server.registerChildSessionRoute("child-session", "grandchild-session"))
        val childStatus = post(
            "call",
            """{"sessionId":"child-session","name":"eleckoi_test_tool","arguments":{"value":"child"}}""",
        ).first
        assertEquals(200, childStatus)
        assertEquals("child", receivedArgument)
        val grandchildStatus = post(
            "call",
            """{"sessionId":"grandchild-session","name":"eleckoi_test_tool","arguments":{"value":"grandchild"}}""",
        ).first
        assertEquals(200, grandchildStatus)
        assertEquals("grandchild", receivedArgument)

        val wrongSessionStatus = post(
            "call",
            """{"sessionId":"other-session","name":"eleckoi_test_tool","arguments":{"value":"leak"}}""",
        ).first
        assertEquals(404, wrongSessionStatus)
        assertEquals("grandchild", receivedArgument)

        server.unregisterChildSessionRoute("child-session")
        assertEquals(
            404,
            post(
                "call",
                """{"sessionId":"child-session","name":"eleckoi_test_tool","arguments":{"value":"leak"}}""",
            ).first,
        )

        server.unregisterSessionRoute("tool-session", ownerToken)
        assertEquals(
            404,
            post(
                "call",
                """{"sessionId":"grandchild-session","name":"eleckoi_test_tool","arguments":{"value":"stale"}}""",
            ).first,
        )
        server.stop()
    }

    private fun readRequest(input: InputStream): CapturedRequest {
        val requestLine = readLine(input)
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            headers[line.substring(0, colon).lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers.getValue("content-length").toInt()
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) offset += input.read(body, offset, length - offset)
        return CapturedRequest(requestLine, headers, body.toString(Charsets.UTF_8))
    }

    private fun postJson(
        url: String,
        payload: String,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val bytes = payload.toByteArray()
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        headers.forEach(connection::setRequestProperty)
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().readText()
        connection.disconnect()
        return status to body
    }

    private fun readLine(input: InputStream): String {
        val output = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0 || value == '\n'.code) break
            if (value != '\r'.code) output.write(value)
        }
        return output.toString(Charsets.ISO_8859_1)
    }

    private data class CapturedRequest(
        val requestLine: String,
        val headers: Map<String, String>,
        val body: String,
    )
    private fun JsonObject.string(name: String): String? = (get(name) as? JsonPrimitive)?.content
    private fun JsonObject.messageText(): String =
        (get("content") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.string("text") }
            ?.joinToString("\n")
            .orEmpty()
}
