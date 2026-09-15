package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.engine.agent.adapter.request.AgentHistoryProjection
import com.eleckoi.android.engine.agent.adapter.request.AgentTurnRequestContext
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
import java.util.concurrent.atomic.AtomicInteger
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

class LoopbackResponsesAdapterServerTest {
    @Test
    fun `prepares Claude as native pi-ai protocol after DSH context projection`() = runBlocking {
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "b".repeat(32) },
        )
        val endpoint = server.start()
        val owner = server.registerSessionRoute(
            routeKey = "claude-session",
            routeModelConfig = ModelConfig(
                provider = "anthropic",
                apiKey = "secret-key",
                model = "claude-sonnet-test",
                apiFormat = ModelApiFormat.AnthropicMessages,
                modelOptions = listOf(
                    ModelOption(
                        id = "claude-sonnet-test",
                        contextWindowTokens = 200_000,
                        reasoningEffort = "high",
                    ),
                ),
            ),
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn(
            routeKey = "claude-session",
            ownerToken = owner,
            userMessage = "现在的问题",
            turnContext = AgentTurnRequestContext(
                userMessage = "现在的问题",
                history = emptyList(),
                injections = listOf(
                    AgentContextInjection(
                        id = "before-user",
                        anchor = AgentContextAnchor.BeforeHistory,
                        role = AgentContextRole.User,
                        activation = AgentContextActivation.Immediate,
                        content = "前置用户上下文",
                    ),
                ),
                historyProjection = AgentHistoryProjection.Native,
            ),
        )
        val payload = """
            {
              "provider":"eleckoi-bridge",
              "model":"route",
              "sessionId":"claude-session",
              "messages":[{
                "id":"current",
                "role":"user",
                "content":[{"type":"text","text":"现在的问题"}],
                "source":{"kind":"user"}
              }]
            }
        """.trimIndent()

        val (status, body) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/provider/prepare",
            payload,
        )
        val response = ElecKoiJson.parseToJsonElement(body).jsonObject
        val projected = response["request"]!!.jsonObject
        val messages = (projected["messages"] as JsonArray).map { it.jsonObject }

        assertEquals(200, status)
        assertEquals("anthropic-messages", response.string("api"))
        assertFalse("reasoningEffort" in response)
        assertEquals("default", response.string("wireProfile"))
        assertEquals("claude-sonnet-test", response.string("model"))
        assertEquals(32, response.string("requestToken")!!.length)
        assertEquals(listOf("user", "user"), messages.map { it.string("role") })
        assertEquals("前置用户上下文", messages.first().messageText())

        server.endSessionTurn("claude-session", owner)
        server.stop()
    }

    @Test
    fun `prepares Kimi K3 with its real pi-ai capability profile`() = runBlocking {
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "t".repeat(32) },
        )
        val endpoint = server.start()
        val option = ModelOption(id = "kimi-k3", reasoningEffort = "max")
        val owner = server.registerSessionRoute(
            routeKey = "kimi-chat",
            routeModelConfig = ModelConfig(
                provider = "moonshot",
                apiKey = "secret-key",
                model = option.id,
                modelOptions = listOf(option),
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("kimi-chat", owner, "hello")

        val (status, body) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/provider/prepare",
            """{"provider":"eleckoi-bridge","model":"route","sessionId":"kimi-chat","messages":[{"id":"u","role":"user","content":[{"type":"text","text":"hello"}],"source":{"kind":"user"}}]}""",
        )
        val response = ElecKoiJson.parseToJsonElement(body).jsonObject

        assertEquals(200, status)
        assertEquals("openai-completions", response.string("api"))
        assertEquals("kimi-k3", response.string("wireProfile"))
        assertEquals("max", response.string("reasoningEffort"))

        server.endSessionTurn("kimi-chat", owner)
        server.stop()
    }

    @Test
    fun `unsupported Kimi off effort is omitted instead of becoming none`() = runBlocking {
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "o".repeat(32) },
        )
        val endpoint = server.start()
        val option = ModelOption(id = "kimi-k3", reasoningEffort = "off")
        val owner = server.registerSessionRoute(
            routeKey = "kimi-off",
            routeModelConfig = ModelConfig(
                provider = "moonshot",
                apiKey = "secret-key",
                model = option.id,
                modelOptions = listOf(option),
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("kimi-off", owner, "hello")

        val (status, body) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/provider/prepare",
            """{"provider":"eleckoi-bridge","model":"route","sessionId":"kimi-off","messages":[{"id":"u","role":"user","content":[{"type":"text","text":"hello"}],"source":{"kind":"user"}}]}""",
        )
        val response = ElecKoiJson.parseToJsonElement(body).jsonObject

        assertEquals(200, status)
        assertEquals("kimi-k3", response.string("wireProfile"))
        assertFalse("reasoningEffort" in response)

        server.endSessionTurn("kimi-off", owner)
        server.stop()
    }

    @Test
    fun `proxies prepared Claude request and response without Responses translation`() = runBlocking {
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
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
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
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("native-claude", owner, "hello")
        val preparePayload = """
            {"provider":"eleckoi-bridge","model":"route","sessionId":"native-claude","messages":[{"id":"u","role":"user","content":[{"type":"text","text":"hello"}],"source":{"kind":"user"}}]}
        """.trimIndent()
        val (prepareStatus, prepareBody) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/provider/prepare",
            preparePayload,
        )
        assertEquals(200, prepareStatus)
        val prepared = ElecKoiJson.parseToJsonElement(prepareBody).jsonObject
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
              "tools":[
                {"name":"weather","description":"lookup","input_schema":{"type":"object","properties":{"city":{"type":"string"}}}},
                {"name":"eleckoi_internal_route_${prepared.string("requestToken")}","description":"internal","input_schema":{"type":"object","properties":{}}}
              ]
            }
        """.trimIndent()
        val wireUrl = endpoint.baseUrl.removeSuffix("/v1") + "/provider-wire/anthropic/v1/messages"
        val bytes = wirePayload.toByteArray()
        val connection = URI(wireUrl).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("anthropic-version", "2023-06-01")
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
    fun `proxies prepared Responses tool round trip in native wire shape`() = runBlocking {
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
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
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
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("native-responses", owner, "hello")
        val (_, prepareBody) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/provider/prepare",
            """{"provider":"eleckoi-bridge","model":"route","sessionId":"native-responses","messages":[{"id":"u","role":"user","content":[{"type":"text","text":"hello"}],"source":{"kind":"user"}}]}""",
        )
        val prepared = ElecKoiJson.parseToJsonElement(prepareBody).jsonObject
        assertEquals("openai-responses", prepared.string("api"))
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
              "tools":[
                {"type":"function","name":"weather","description":"lookup","parameters":{"type":"object","properties":{"city":{"type":"string"}}}},
                {"type":"function","name":"eleckoi_internal_route_${prepared.string("requestToken")}","description":"internal","parameters":{"type":"object","properties":{}}}
              ]
            }
        """.trimIndent()

        val (status, downstream) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/provider-wire/responses/v1/responses",
            wirePayload,
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
    fun `proxies prepared Chat Completions tool round trip in native wire shape`() = runBlocking {
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
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
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
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("native-chat", owner, "hello")
        val (_, prepareBody) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/provider/prepare",
            """{"provider":"eleckoi-bridge","model":"route","sessionId":"native-chat","messages":[{"id":"u","role":"user","content":[{"type":"text","text":"hello"}],"source":{"kind":"user"}}]}""",
        )
        val prepared = ElecKoiJson.parseToJsonElement(prepareBody).jsonObject
        assertEquals("openai-completions", prepared.string("api"))
        assertEquals(
            0.0,
            (prepared["request"]!!.jsonObject["temperature"] as JsonPrimitive).content.toDouble(),
            0.0,
        )
        assertEquals(
            0.0,
            (prepared["request"]!!.jsonObject["topP"] as JsonPrimitive).content.toDouble(),
            0.0,
        )
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
              "tools":[
                {"type":"function","function":{"name":"weather","description":"lookup","parameters":{"type":"object","properties":{"city":{"type":"string"}}}}},
                {"type":"function","function":{"name":"eleckoi_internal_route_${prepared.string("requestToken")}","description":"internal","parameters":{"type":"object","properties":{}}}}
              ]
            }
        """.trimIndent()

        val (status, downstream) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/provider-wire/chat/v1/chat/completions",
            wirePayload,
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
        val tokenIndex = AtomicInteger()
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = {
                when (tokenIndex.getAndIncrement()) {
                    0 -> "a".repeat(32)
                    else -> "b".repeat(32)
                }
            },
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
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        val ownerB = server.registerSessionRoute(
            routeKey = "sampling-session-b",
            routeModelConfig = routeConfig("model-b", "secret-b", upstreamB.localPort, 0.87),
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("sampling-session-a", ownerA, "hello a")
        server.beginSessionTurn("sampling-session-b", ownerB, "hello b")

        suspend fun prepare(sessionId: String, message: String): JsonObject {
            val (_, responseBody) = postJson(
                endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/provider/prepare",
                """{"provider":"eleckoi-bridge","model":"route","sessionId":"$sessionId","messages":[{"id":"u","role":"user","content":[{"type":"text","text":"$message"}],"source":{"kind":"user"}}]}""",
            )
            return ElecKoiJson.parseToJsonElement(responseBody).jsonObject
        }
        fun wirePayload(token: String, message: String, topP: Double): String =
            """{"model":"eleckoi-wire","stream":true,"top_p":$topP,"messages":[{"role":"user","content":"$message"}],"tools":[{"type":"function","function":{"name":"eleckoi_internal_route_$token","description":"internal","parameters":{"type":"object","properties":{}}}}]}"""

        try {
            val preparedA = prepare("sampling-session-a", "hello a")
            val preparedB = prepare("sampling-session-b", "hello b")
            assertEquals(0.21, (preparedA["request"]!!.jsonObject["topP"] as JsonPrimitive).content.toDouble(), 0.0)
            assertEquals(0.87, (preparedB["request"]!!.jsonObject["topP"] as JsonPrimitive).content.toDouble(), 0.0)
            val wireUrl = endpoint.baseUrl.removeSuffix("/v1") + "/provider-wire/chat/v1/chat/completions"
            val responseA = async(Dispatchers.IO) {
                postJson(wireUrl, wirePayload(preparedA.string("requestToken")!!, "hello a", 0.21))
            }
            val responseB = async(Dispatchers.IO) {
                postJson(wireUrl, wirePayload(preparedB.string("requestToken")!!, "hello b", 0.87))
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
    fun `proxies prepared Gemini request through its native model path`() = runBlocking {
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
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
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
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("native-gemini", owner, "hello")
        val preparePayload = """
            {"provider":"eleckoi-bridge","model":"route","sessionId":"native-gemini","messages":[{"id":"u","role":"user","content":[{"type":"text","text":"hello"}],"source":{"kind":"user"}}]}
        """.trimIndent()
        val (prepareStatus, prepareBody) = postJson(
            endpoint.baseUrl.removeSuffix("/v1") + "/host-tools/provider/prepare",
            preparePayload,
        )
        assertEquals(200, prepareStatus)
        val prepared = ElecKoiJson.parseToJsonElement(prepareBody).jsonObject
        assertEquals("google-generative-ai", prepared.string("api"))
        val wirePayload = """
            {
              "contents":[
                {"role":"user","parts":[{"text":"weather?"}]},
                {"role":"model","parts":[{"functionCall":{"name":"weather","args":{"city":"Taipei"}}}]},
                {"role":"user","parts":[{"functionResponse":{"name":"weather","response":{"result":"sunny"}}}]},
                {"role":"user","parts":[{"text":"answer now"}]}
              ],
              "generationConfig":{"maxOutputTokens":512},
              "tools":[{"functionDeclarations":[
                {"name":"weather","description":"lookup","parameters":{"type":"OBJECT","properties":{"city":{"type":"STRING"}}}},
                {"name":"eleckoi_internal_route_${prepared.string("requestToken")}","description":"internal","parameters":{"type":"OBJECT","properties":{}}}
              ]}]
            }
        """.trimIndent()
        val wireUrl = endpoint.baseUrl.removeSuffix("/v1") +
            "/provider-wire/google/models/eleckoi-wire:streamGenerateContent?alt=sse"
        val (_, downstream) = postJson(wireUrl, wirePayload)
        val upstreamRequest = withTimeout(2_000) { captured.await() }

        assertEquals(
            "POST /v1beta/models/gemini-test:streamGenerateContent?alt=sse HTTP/1.1",
            upstreamRequest.requestLine,
        )
        assertEquals("google-secret", upstreamRequest.headers["x-goog-api-key"])
        assertTrue("model" !in ElecKoiJson.parseToJsonElement(upstreamRequest.body).jsonObject)
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
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "route"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "z".repeat(32) },
        )
        val endpoint = server.start()
        server.beginTurn("hello")

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
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "secret-key", model = "test-model"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "p".repeat(32) },
        )
        val endpoint = server.start()
        server.registerSessionRoute(
            routeKey = "pressure-session",
            routeModelConfig = ModelConfig(apiKey = "secret-key", model = "test-model"),
            routeEnabledToolGroupIds = emptySet(),
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
                "contextWindow":1000000
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
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "secret-key", model = "test-model"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "h".repeat(32) },
        )
        val endpoint = server.start()
        val ownerToken = server.registerSessionRoute(
            routeKey = "tool-session",
            routeModelConfig = ModelConfig(apiKey = "secret-key", model = "test-model"),
            routeEnabledToolGroupIds = emptySet(),
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

    @Test
    fun `model override sends native Responses request without Chat conversion`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val captured = CompletableDeferred<CapturedRequest>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                captured.complete(readRequest(socket.getInputStream()))
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/event-stream\r\n")
                    append("Connection: close\r\n\r\n")
                    append("event: response.created\n")
                    append("data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_native\"}}\n\n")
                    append("event: response.completed\n")
                    append("data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_native\",\"output\":[]}}\n\n")
                    append("data: [DONE]\n\n")
                    append("event: ping\n")
                    append("data: {\"type\":\"ping\",\"cost\":\"0.0001\"}\n\n")
                }
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "test",
                apiKey = "native-secret",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1/chat/completions",
                model = "native-model",
                modelOptions = listOf(
                    ModelOption(
                        id = "native-model",
                        maxOutputTokens = 1_234,
                        apiFormatOverride = ModelApiFormat.Responses,
                    ),
                ),
                apiFormat = ModelApiFormat.ChatCompletions,
                customHeaders = mapOf("X-Relay" to "native"),
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "n".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        server.beginTurn()
        val payload = """
            {
              "model":"eleckoi-chat",
              "instructions":"keep native",
              "input":[{"type":"message","role":"user","content":"test"}],
              "tools":[],
              "text":{"format":{"type":"text"}},
              "stream":true
            }
        """.trimIndent().toByteArray()
        val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }

        assertEquals(200, connection.responseCode)
        val responseBody = connection.inputStream.bufferedReader().readText()
        assertTrue(responseBody.contains("response.created"))
        assertTrue(responseBody.contains("response.completed"))
        assertFalse(responseBody.contains("[DONE]"))
        assertFalse(responseBody.contains("event: ping"))
        val upstreamRequest = withTimeout(2_000) { captured.await() }
        assertEquals("POST /v1/responses HTTP/1.1", upstreamRequest.requestLine)
        assertEquals("Bearer native-secret", upstreamRequest.headers["authorization"])
        assertEquals("native", upstreamRequest.headers["x-relay"])
        val upstreamJson = ElecKoiJson.parseToJsonElement(upstreamRequest.body).jsonObject
        assertEquals("native-model", upstreamJson.string("model"))
        assertEquals("1234", (upstreamJson["max_output_tokens"] as JsonPrimitive).content)
        assertFalse("reasoning" in upstreamJson)
        assertTrue("input" in upstreamJson)
        assertTrue("text" in upstreamJson)
        assertFalse("messages" in upstreamJson)

        connection.disconnect()
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `incomplete upstream stream publishes an immediate turn failure`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                readRequest(socket.getInputStream())
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/event-stream\r\n")
                    append("Connection: close\r\n\r\n")
                    append("data: {\"id\":\"chatcmpl_broken\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"部分正文\"},\"finish_reason\":null}]}\n\n")
                }
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "test",
                apiKey = "secret-key",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "test-model",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "b".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        server.beginTurn()
        val failure = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) { server.defaultTurnFailures.first() }
        }

        val payload =
            """{"model":"test-model","input":[{"type":"message","role":"user","content":"test"}],"tools":[],"stream":true}"""
                .toByteArray()
        val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }
        assertEquals(200, connection.responseCode)
        connection.inputStream.bufferedReader().readText()

        assertEquals("Chat Completions 流在 [DONE] 前关闭", failure.await())

        connection.disconnect()
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `terminal finish reason allows compatible stream to close without done sentinel`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                readRequest(socket.getInputStream())
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/event-stream\r\n")
                    append("Connection: close\r\n\r\n")
                    append("data: {\"id\":\"chatcmpl_clean_eof\",\"model\":\"minimax-m3\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"pong\"},\"finish_reason\":\"stop\"}]}\n\n")
                    append("data: {\"choices\":[],\"cost\":\"0\"}\n\n")
                }
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "test",
                apiKey = "secret-key",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "minimax-m3",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "e".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        server.beginTurn()
        val payload =
            """{"model":"minimax-m3","input":[{"type":"message","role":"user","content":"test"}],"tools":[],"stream":true}"""
                .toByteArray()
        val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }

        assertEquals(200, connection.responseCode)
        val responseBody = connection.inputStream.bufferedReader().readText()
        assertTrue(responseBody.contains("event: response.completed"))
        assertFalse(responseBody.contains("event: response.failed"))
        assertTrue(responseBody.contains("pong"))

        connection.disconnect()
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `downstream Harness disconnect publishes a direction-specific turn failure`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val upstreamAccepted = CompletableDeferred<Unit>()
        val releaseUpstreamResponse = CompletableDeferred<Unit>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                readRequest(socket.getInputStream())
                upstreamAccepted.complete(Unit)
                releaseUpstreamResponse.await()
                val largeDelta = "x".repeat(2 * 1024 * 1024)
                runCatching {
                    socket.getOutputStream().apply {
                        write(
                            buildString {
                                append("HTTP/1.1 200 OK\r\n")
                                append("Content-Type: text/event-stream\r\n")
                                append("Connection: close\r\n\r\n")
                                append(
                                    "data: {\"id\":\"chatcmpl_disconnect\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"",
                                )
                                append(largeDelta)
                                append("\"},\"finish_reason\":\"stop\"}]}\n\n")
                                append("data: [DONE]\n\n")
                            }.toByteArray(Charsets.UTF_8),
                        )
                        flush()
                    }
                }
            }
        }
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "test",
                apiKey = "secret-key",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "test-model",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "d".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        server.beginTurn()
        val turnFailure = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) { server.defaultTurnFailures.first() }
        }
        val endpointUri = URI(endpoint.baseUrl)
        try {
            Socket(endpointUri.host, endpointUri.port).use { client ->
                client.setSoLinger(true, 0)
                val payload =
                    """{"model":"test-model","input":[{"type":"message","role":"user","content":"test"}],"tools":[],"stream":true}"""
                        .toByteArray()
                client.getOutputStream().apply {
                    write(
                        buildString {
                            append("POST ${endpointUri.path}/responses HTTP/1.1\r\n")
                            append("Host: ${endpointUri.host}:${endpointUri.port}\r\n")
                            append("Content-Type: application/json\r\n")
                            append("Content-Length: ${payload.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(Charsets.US_ASCII),
                    )
                    write(payload)
                    flush()
                }
                withTimeout(2_000) { upstreamAccepted.await() }
            }
            releaseUpstreamResponse.complete(Unit)
            withTimeout(2_000) { upstreamJob.await() }
            assertEquals(
                "Agent Harness 提前关闭本地响应连接，上游生成未完成",
                turnFailure.await(),
            )
        } finally {
            releaseUpstreamResponse.complete(Unit)
            server.stop()
            upstream.close()
        }
    }

    @Test
    fun `rejects provider requests outside an app initiated DSH turn`() = runBlocking {
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "test",
                apiKey = "secret-key",
                baseUrl = "https://api.example.com/v1",
                model = "test-model",
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "c".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        val payload = """{"model":"test-model","input":[],"tools":[],"stream":true}""".toByteArray()
        val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }

        assertEquals(429, connection.responseCode)

        connection.disconnect()
        server.stop()
    }

    @Test
    fun `serves authenticated path and streams converted upstream response`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val captured = CompletableDeferred<CapturedRequest>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                val request = readRequest(socket.getInputStream())
                captured.complete(request)
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/event-stream\r\n")
                    append("Connection: close\r\n\r\n")
                    append("data: {\"id\":\"chatcmpl_test\",\"model\":\"deepseek\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"完成\"},\"finish_reason\":null}]}\n\n")
                    append("data: {\"id\":\"chatcmpl_test\",\"model\":\"deepseek\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
                    append("data: [DONE]\n\n")
                }
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "deepseek",
                apiKey = "secret-key",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1/responses",
                model = "deepseek-test",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            scope = scope,
            tokenFactory = { "a".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        server.beginTurn()
        assertTrue(endpoint.baseUrl.startsWith("http://127.0.0.1:"))

        val payload =
            """{"model":"deepseek-test","instructions":"help","input":[{"type":"message","role":"user","content":[{"type":"input_text","text":"test"}]}],"tools":[],"stream":true}"""
        val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        val bytes = payload.toByteArray()
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        assertEquals(200, connection.responseCode)
        val response = connection.inputStream.bufferedReader().readText()
        assertTrue(response.contains("response.created"))
        assertTrue(response.contains("response.output_text.delta"))
        assertTrue(response.contains("response.output_item.done"))
        assertTrue(response.contains("response.completed"))
        assertFalse(response.contains("secret-key"))

        val upstreamRequest = withTimeout(2_000) { captured.await() }
        assertEquals("POST /v1/chat/completions HTTP/1.1", upstreamRequest.requestLine)
        assertEquals("Bearer secret-key", upstreamRequest.headers["authorization"])
        val upstreamJson = ElecKoiJson.parseToJsonElement(upstreamRequest.body).jsonObject
        assertEquals("deepseek-test", upstreamJson.string("model"))
        assertEquals(true, (upstreamJson["stream"] as JsonPrimitive).content.toBoolean())

        connection.disconnect()
        server.endTurn()
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `bound default session route accepts only its DeepSeek prompt cache key`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val captured = CompletableDeferred<CapturedRequest>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                captured.complete(readRequest(socket.getInputStream()))
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/event-stream\r\n")
                    append("Connection: close\r\n\r\n")
                    append("data: {\"id\":\"chatcmpl_deepseek_route\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"pong\"},\"finish_reason\":\"stop\"}]}\n\n")
                    append("data: [DONE]\n\n")
                }
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "deepseek",
                apiKey = "deepseek-secret",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "deepseek-test",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "k".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        server.bindDefaultSessionRoute("eleckoi-deepseek-session")
        server.beginTurn("Reply pong")

        fun request(promptCacheKey: String): Pair<Int, String> {
            val payload =
                """{"model":"deepseek-test","prompt_cache_key":"$promptCacheKey","input":[{"type":"message","role":"user","content":"Reply pong"}],"tools":[],"stream":true}"""
                    .toByteArray()
            val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val body = (if (code >= 400) connection.errorStream else connection.inputStream)
                .bufferedReader()
                .readText()
            connection.disconnect()
            return code to body
        }

        val rejected = request("another-session")
        assertEquals(429, rejected.first)
        assertTrue(rejected.second.contains("No active Agent session route"))

        val accepted = request("eleckoi-deepseek-session")
        assertEquals(200, accepted.first)
        assertTrue(accepted.second.contains("response.output_text.delta"))
        val upstreamRequest = withTimeout(2_000) { captured.await() }
        assertEquals("Bearer deepseek-secret", upstreamRequest.headers["authorization"])
        assertEquals(
            "deepseek-test",
            ElecKoiJson.parseToJsonElement(upstreamRequest.body).jsonObject.string("model"),
        )

        server.endTurn()
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `child route reuses parent authority without projecting parent dialogue`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val captured = CompletableDeferred<CapturedRequest>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                captured.complete(readRequest(socket.getInputStream()))
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/event-stream\r\n")
                    append("Connection: close\r\n\r\n")
                    append("data: {\"id\":\"chatcmpl_child\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n")
                    append("data: [DONE]\n\n")
                }
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "unused"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "s".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        val owner = server.registerSessionRoute(
            routeKey = "parent-session",
            routeModelConfig = ModelConfig(
                apiKey = "parent-secret",
                baseUrl = "https://parent.invalid/v1",
                model = "parent-model",
            ),
            routeSubagentModelConfig = ModelConfig(
                apiKey = "child-secret",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "child-model",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            routeEnabledToolGroupIds = emptySet(),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn(
            routeKey = "parent-session",
            ownerToken = owner,
            userMessage = "PARENT_CURRENT",
            turnContext = AgentTurnRequestContext(
                userMessage = "PARENT_CURRENT",
                history = listOf(
                    AgentHistoryItem(
                        """{"type":"message","role":"assistant","content":[{"type":"output_text","text":"PARENT_SECRET"}]}""",
                    ),
                ),
                injections = emptyList(),
                historyProjection = AgentHistoryProjection.ReplacePreviousTurns,
            ),
        )
        assertTrue(server.registerChildSessionRoute("parent-session", "child-session"))

        val payload =
            """{"model":"eleckoi-route","prompt_cache_key":"child-session","input":[{"type":"message","role":"user","content":"CHILD_STANDALONE"}],"tools":[],"stream":true}"""
                .toByteArray()
        val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }
        assertEquals(200, connection.responseCode)
        connection.inputStream.bufferedReader().readText()

        val upstreamRequest = withTimeout(2_000) { captured.await() }
        assertEquals("Bearer child-secret", upstreamRequest.headers["authorization"])
        assertEquals(
            "child-model",
            ElecKoiJson.parseToJsonElement(upstreamRequest.body).jsonObject.string("model"),
        )
        assertTrue(upstreamRequest.body.contains("CHILD_STANDALONE"))
        assertFalse(upstreamRequest.body.contains("PARENT_SECRET"))
        assertFalse(upstreamRequest.body.contains("PARENT_CURRENT"))

        connection.disconnect()
        server.unregisterSessionRoute("parent-session", owner)
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
    }

    @Test
    fun `routes simultaneous persistent app server requests by Agent session id`() = runBlocking {
        val upstreamA = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val upstreamB = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val capturedA = CompletableDeferred<CapturedRequest>()
        val capturedB = CompletableDeferred<CapturedRequest>()
        val filteredSelections = java.util.Collections.synchronizedSet(mutableSetOf<Set<String>>())
        fun serve(upstream: ServerSocket, captured: CompletableDeferred<CapturedRequest>) =
            async(Dispatchers.IO) {
                upstream.accept().use { socket ->
                    captured.complete(readRequest(socket.getInputStream()))
                    val response = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/event-stream\r\n")
                        append("Connection: close\r\n\r\n")
                        append("data: {\"id\":\"chatcmpl_route\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n")
                        append("data: [DONE]\n\n")
                    }
                    socket.getOutputStream().apply {
                        write(response.toByteArray(Charsets.UTF_8))
                        flush()
                    }
                }
            }
        val upstreamJobA = serve(upstreamA, capturedA)
        val upstreamJobB = serve(upstreamB, capturedB)
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(apiKey = "unused", model = "eleckoi-chat"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "m".repeat(32) },
            legacyResponsesProbeEnabled = true,
            toolRequestFilter = { enabledGroupIds, request ->
                filteredSelections += enabledGroupIds
                request
            },
        )
        val endpoint = server.start()
        val ownerA = server.registerSessionRoute(
            routeKey = "thread-route-a",
            routeModelConfig = ModelConfig(
                apiKey = "secret-a",
                baseUrl = "http://127.0.0.1:${upstreamA.localPort}/v1",
                model = "model-a",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            routeSystemInstructions = "session-system-a",
            routeEnabledToolGroupIds = setOf("tool:a"),
            routeCaptureProviderRequests = false,
        )
        val ownerB = server.registerSessionRoute(
            routeKey = "thread-route-b",
            routeModelConfig = ModelConfig(
                apiKey = "secret-b",
                baseUrl = "http://127.0.0.1:${upstreamB.localPort}/v1",
                model = "model-b",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            routeSystemInstructions = "session-system-b",
            routeEnabledToolGroupIds = setOf("tool:b"),
            routeCaptureProviderRequests = false,
        )
        server.beginSessionTurn("thread-route-a", ownerA, "A")
        server.beginSessionTurn("thread-route-b", ownerB, "B")

        suspend fun request(threadId: String) = async(Dispatchers.IO) {
            val payload =
                """{"model":"eleckoi-chat","prompt_cache_key":"$threadId","instructions":"shared-harness-system","input":[{"type":"message","role":"user","content":"test"}],"tools":[],"stream":true}"""
                    .toByteArray()
            val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            code
        }
        val responseA = request("thread-route-a")
        val responseB = request("thread-route-b")

        assertEquals(200, responseA.await())
        assertEquals(200, responseB.await())
        val requestA = withTimeout(2_000) { capturedA.await() }
        val requestB = withTimeout(2_000) { capturedB.await() }
        assertEquals("Bearer secret-a", requestA.headers["authorization"])
        assertEquals("Bearer secret-b", requestB.headers["authorization"])
        assertEquals("model-a", ElecKoiJson.parseToJsonElement(requestA.body).jsonObject.string("model"))
        assertEquals("model-b", ElecKoiJson.parseToJsonElement(requestB.body).jsonObject.string("model"))
        assertTrue(requestA.body.contains("shared-harness-system"))
        assertTrue(requestA.body.contains("session-system-a"))
        assertFalse(requestA.body.contains("session-system-b"))
        assertTrue(requestB.body.contains("shared-harness-system"))
        assertTrue(requestB.body.contains("session-system-b"))
        assertFalse(requestB.body.contains("session-system-a"))
        assertEquals(setOf(setOf("tool:a"), setOf("tool:b")), filteredSelections)

        server.unregisterSessionRoute("thread-route-a", ownerA)
        server.unregisterSessionRoute("thread-route-b", ownerB)
        server.stop()
        upstreamA.close()
        upstreamB.close()
        upstreamJobA.await()
        upstreamJobB.await()
        Unit
    }

    @Test
    fun `forwards custom tool input delta before upstream stream completes`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val releaseUpstreamCompletion = CompletableDeferred<Unit>()
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                readRequest(socket.getInputStream())
                val output = socket.getOutputStream()
                output.write(
                    buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/event-stream\r\n")
                        append("Connection: close\r\n\r\n")
                        append("data: {\"id\":\"chatcmpl_patch\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_patch\",\"function\":{\"name\":\"apply_patch\",\"arguments\":\"{\\\"input\\\":\\\"*** Begin Patch\\\\n\"}}]},\"finish_reason\":null}]}\n\n")
                    }.toByteArray(Charsets.UTF_8),
                )
                output.flush()
                releaseUpstreamCompletion.await()
                output.write(
                    buildString {
                        append("data: {\"id\":\"chatcmpl_patch\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"*** End Patch\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n")
                        append("data: [DONE]\n\n")
                    }.toByteArray(Charsets.UTF_8),
                )
                output.flush()
            }
        }
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "deepseek",
                apiKey = "secret-key",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "deepseek-test",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "s".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        server.beginTurn()
        val deltaSeen = CompletableDeferred<String>()
        val payload =
            """{"model":"deepseek-test","input":[{"type":"message","role":"user","content":"edit"}],"tools":[{"type":"custom","name":"apply_patch","description":"edit"}],"stream":true}"""
                .toByteArray()
        val clientJob = async(Dispatchers.IO) {
            val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            assertEquals(200, connection.responseCode)
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("data:") && line.contains("response.custom_tool_call_input.delta")) {
                        deltaSeen.complete(line)
                    }
                }
            }
            connection.disconnect()
        }

        try {
            val deltaLine = withTimeout(2_000) { deltaSeen.await() }
            assertTrue(deltaLine.contains("*** Begin Patch"))
        } finally {
            releaseUpstreamCompletion.complete(Unit)
        }
        withTimeout(2_000) { clientJob.await() }
        upstreamJob.await()
        server.endTurn()
        server.stop()
        upstream.close()
    }

    @Test
    fun `forwards undeclared tool call`() = runBlocking {
        val upstream = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val upstreamJob = async(Dispatchers.IO) {
            upstream.accept().use { socket ->
                readRequest(socket.getInputStream())
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/event-stream\r\n")
                    append("Connection: close\r\n\r\n")
                    append("data: {\"id\":\"chatcmpl_route\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"apply_patch\",\"arguments\":\"{\\\"input\\\":\\\"hidden patch body\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n")
                    append("data: [DONE]\n\n")
                }
                socket.getOutputStream().apply {
                    write(response.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
        val server = LoopbackResponsesAdapterServer(
            modelConfig = ModelConfig(
                provider = "test",
                apiKey = "secret-key",
                baseUrl = "http://127.0.0.1:${upstream.localPort}/v1",
                model = "test-model",
                apiFormat = ModelApiFormat.ChatCompletions,
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokenFactory = { "r".repeat(32) },
            legacyResponsesProbeEnabled = true,
        )
        val endpoint = server.start()
        server.beginTurn()
        val payload =
            """{"model":"test-model","input":[{"type":"message","role":"user","content":"test"}],"tools":[],"stream":true}"""
                .toByteArray()
        val connection = URI("${endpoint.baseUrl}/responses").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }

        assertEquals(200, connection.responseCode)
        val responseBody = connection.inputStream.bufferedReader().readText()
        assertTrue(responseBody.contains("\"name\":\"apply_patch\""))
        assertTrue(responseBody.contains("response.completed"))

        connection.disconnect()
        server.endTurn()
        server.stop()
        upstream.close()
        upstreamJob.await()
        Unit
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

    private fun postJson(url: String, payload: String): Pair<Int, String> {
        val bytes = payload.toByteArray()
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
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
