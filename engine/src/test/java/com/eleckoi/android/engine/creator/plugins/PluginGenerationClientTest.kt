package com.eleckoi.android.engine.creator.plugins

import com.eleckoi.android.engine.generation.model.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class PluginGenerationClientTest {
    @Test fun `cancelling a stream does not cancel a separate generation`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.executor = executor
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        server.createContext("/chat/completions") { exchange ->
            val params = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
            if (params["stream"]?.jsonPrimitive?.boolean == true) {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { body ->
                    body.write("data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n".toByteArray()); body.flush()
                    entered.countDown(); release.await(5, java.util.concurrent.TimeUnit.SECONDS)
                }
            } else {
                val result = "{\"choices\":[{\"message\":{\"content\":\"second\"}}]}".toByteArray()
                exchange.sendResponseHeaders(200, result.size.toLong()); exchange.responseBody.use { it.write(result) }
            }
        }
        server.start()
        try {
            val config = ModelConfig(model = "test", baseUrl = "http://127.0.0.1:${server.address.port}", apiFormat = ModelApiFormat.ChatCompletions)
            val client = PluginGenerationClient()
            val first = async(Dispatchers.IO) { runCatching { client.invoke(config, Json.parseToJsonElement("""{"id":"first","stream":true,"messages":[]}""").jsonObject) { _, _ -> } } }
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            val second = client.invoke(config, Json.parseToJsonElement("""{"id":"second","messages":[]}""").jsonObject) { _, _ -> }
            assertEquals("second", second.getValue("content").jsonPrimitive.content)
            assertTrue(client.cancel("first"))
            assertNotNull(withTimeout(3000) { first.await() }.exceptionOrNull())
            assertFalse(client.cancel("second"))
        } finally { release.countDown(); server.stop(0); executor.shutdownNow() }
    }
    @Test fun `raw ordered messages reach actual http endpoint and streaming deltas are joined`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var request = ""
        server.createContext("/chat/completions") { exchange ->
            request = exchange.requestBody.bufferedReader().readText()
            val response = "data: {\"choices\":[{\"delta\":{\"content\":\"hello \",\"reasoning_content\":\"think\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"world\"}}]}\n\ndata: [DONE]\n\n"
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val params = Json.parseToJsonElement("""{"id":"raw-test","stream":true,"messages":[{"role":"system","content":"raw only"},{"role":"user","content":"extract"}]}""").jsonObject
            val deltas = mutableListOf<String>()
            val result = PluginGenerationClient().invoke(ModelConfig(model = "test", baseUrl = "http://127.0.0.1:${server.address.port}", apiFormat = ModelApiFormat.ChatCompletions), params) { text, _ -> deltas += text }
            assertEquals("hello world", result.getValue("content").jsonPrimitive.content)
            assertEquals("think", result.getValue("reasoning").jsonPrimitive.content)
            assertEquals(listOf("hello ", "world"), deltas)
            assertEquals(params.getValue("messages"), Json.parseToJsonElement(request).jsonObject.getValue("messages"))
        } finally { server.stop(0) }
    }

    @Test fun `provider http error contains status and response details`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/responses") { exchange ->
            exchange.sendResponseHeaders(422, 0); exchange.responseBody.use { it.write("bad model".toByteArray()) }
        }
        server.start()
        try {
            val params = Json.parseToJsonElement("""{"id":"error-test","messages":[{"role":"user","content":"hello"}]}""").jsonObject
            val error = runCatching { PluginGenerationClient().invoke(ModelConfig(model = "bad", baseUrl = "http://127.0.0.1:${server.address.port}"), params) { _, _ -> } }.exceptionOrNull()
            assertNotNull(error); assertTrue(error!!.message!!.contains("422")); assertTrue(error.message!!.contains("bad model"))
        } finally { server.stop(0) }
    }

    @Test fun `each provider format preserves history and role semantics`() {
        val messages = Json.parseToJsonElement("""[{"role":"system","content":"rules"},{"role":"user","content":"hi"},{"role":"assistant","content":"hello"}]""").jsonArray
        val params = buildJsonObject { put("responseFormat", "json") }
        for (format in ModelApiFormat.entries) {
            val request = PluginGenerationClient.requestBody(format, "test", messages, params, false)
            when (format) {
                ModelApiFormat.ChatCompletions -> assertEquals(messages, request["messages"])
                ModelApiFormat.Responses -> assertEquals(messages, request["input"])
                ModelApiFormat.AnthropicMessages -> { assertEquals("rules", request.getValue("system").jsonPrimitive.content); assertEquals(2, request.getValue("messages").jsonArray.size) }
                ModelApiFormat.GoogleGemini -> { assertEquals(2, request.getValue("contents").jsonArray.size); assertEquals("model", request.getValue("contents").jsonArray[1].jsonObject.getValue("role").jsonPrimitive.content) }
            }
        }
    }
}
