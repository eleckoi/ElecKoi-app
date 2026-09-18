package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProtocolCapabilityValidatorTest {
    @Test
    fun `Responses is probed directly with reasoning and tool replay`() = runBlocking {
        val server = ProbeServer(
            firstResponse = """{
                "output":[
                  {"type":"reasoning","id":"reasoning_probe","summary":[]},
                  {"type":"function_call","call_id":"call_probe","name":"eleckoi_capability_probe","arguments":"{\"value\":\"ok\"}"}
                ]
            }""".trimIndent(),
            secondResponse = """{
                "output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"ok"}]}]
            }""".trimIndent(),
        )
        val job = async(Dispatchers.IO) { server.serveTwo() }

        ModelProtocolCapabilityValidator().verify(
            config(server, ModelApiFormat.Responses),
        )
        val requests = job.await()

        assertEquals("POST /v1/responses HTTP/1.1", requests[0].line)
        assertEquals("Bearer secret", requests[0].headers["authorization"])
        val first = JSONObject(requests[0].body)
        assertFalse(first.optBoolean("stream", false))
        assertEquals("eleckoi_capability_probe", first.getJSONArray("tools").getJSONObject(0).getString("name"))
        val secondInput = JSONObject(requests[1].body).getJSONArray("input")
        assertTrue((0 until secondInput.length()).any { secondInput.getJSONObject(it).optString("type") == "reasoning" })
        assertTrue((0 until secondInput.length()).any { secondInput.getJSONObject(it).optString("type") == "function_call_output" })
        server.close()
    }

    @Test
    fun `Chat Completions is probed directly without forced tool choice`() = runBlocking {
        val server = ProbeServer(
            firstResponse = """{
                "choices":[{"message":{"role":"assistant","reasoning_content":"check","tool_calls":[{
                  "id":"call_probe","type":"function","function":{"name":"eleckoi_capability_probe","arguments":"{\"value\":\"ok\"}"}
                }]}}]
            }""".trimIndent(),
            secondResponse = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""",
        )
        val job = async(Dispatchers.IO) { server.serveTwo() }

        ModelProtocolCapabilityValidator().verify(
            config(server, ModelApiFormat.ChatCompletions),
        )
        val requests = job.await()

        assertEquals("POST /v1/chat/completions HTTP/1.1", requests[0].line)
        val first = JSONObject(requests[0].body)
        assertFalse(first.has("tool_choice"))
        assertFalse(first.has("parallel_tool_calls"))
        val secondMessages = JSONObject(requests[1].body).getJSONArray("messages")
        assertTrue((0 until secondMessages.length()).any { secondMessages.getJSONObject(it).optString("role") == "tool" })
        assertEquals("check", secondMessages.getJSONObject(1).getString("reasoning_content"))
        server.close()
    }

    @Test
    fun `Anthropic Messages is probed directly with native tool blocks`() = runBlocking {
        val server = ProbeServer(
            firstResponse = """{
                "content":[{"type":"tool_use","id":"toolu_probe","name":"eleckoi_capability_probe","input":{"value":"ok"}}]
            }""".trimIndent(),
            secondResponse = """{"content":[{"type":"text","text":"ok"}]}""",
        )
        val job = async(Dispatchers.IO) { server.serveTwo() }

        ModelProtocolCapabilityValidator().verify(
            config(server, ModelApiFormat.AnthropicMessages),
        )
        val requests = job.await()

        assertEquals("POST /v1/messages HTTP/1.1", requests[0].line)
        assertEquals("secret", requests[0].headers["x-api-key"])
        assertEquals("2023-06-01", requests[0].headers["anthropic-version"])
        val first = JSONObject(requests[0].body)
        assertEquals("tool", first.getJSONObject("tool_choice").getString("type"))
        val secondMessages = JSONObject(requests[1].body).getJSONArray("messages")
        assertEquals("tool_result", secondMessages.getJSONObject(2).getJSONArray("content").getJSONObject(0).getString("type"))
        server.close()
    }

    @Test
    fun `Google Gemini is probed directly with generateContent`() = runBlocking {
        val server = ProbeServer(
            firstResponse = """{
                "candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"eleckoi_capability_probe","args":{"value":"ok"}}}]}}]
            }""".trimIndent(),
            secondResponse = """{
                "candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]}}]
            }""".trimIndent(),
        )
        val job = async(Dispatchers.IO) { server.serveTwo() }

        ModelProtocolCapabilityValidator().verify(
            config(server, ModelApiFormat.GoogleGemini, model = "models/gemini-2.5-flash"),
        )
        val requests = job.await()

        assertEquals(
            "POST /v1beta/models/gemini-2.5-flash:generateContent HTTP/1.1",
            requests[0].line,
        )
        assertEquals("secret", requests[0].headers["x-goog-api-key"])
        assertFalse(requests[0].headers.containsKey("authorization"))
        val secondContents = JSONObject(requests[1].body).getJSONArray("contents")
        assertTrue(
            secondContents.getJSONObject(2)
                .getJSONArray("parts")
                .getJSONObject(0)
                .has("functionResponse"),
        )
        server.close()
    }

    private fun config(
        server: ProbeServer,
        format: ModelApiFormat,
        model: String = "probe-model",
    ) = ModelConfig(
        provider = "custom",
        apiKey = "secret",
        baseUrl = "http://127.0.0.1:${server.port}/v1",
        model = model,
        apiFormat = format,
        customHeaders = mapOf("X-Relay" to "probe"),
    )

    private inner class ProbeServer(
        private val firstResponse: String,
        private val secondResponse: String,
    ) {
        private val server = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        val port: Int get() = server.localPort

        fun serveTwo(): List<CapturedRequest> = listOf(firstResponse, secondResponse).map { response ->
            server.accept().use { socket ->
                val request = readRequest(socket.getInputStream())
                val bytes = response.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write(
                        buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: application/json\r\n")
                            append("Content-Length: ${bytes.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(Charsets.ISO_8859_1),
                    )
                    write(bytes)
                    flush()
                }
                request
            }
        }

        fun close() = server.close()
    }

    private data class CapturedRequest(
        val line: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private fun readRequest(input: InputStream): CapturedRequest {
        val line = readLine(input)
        val headers = linkedMapOf<String, String>()
        while (true) {
            val header = readLine(input)
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            headers[header.substring(0, colon).lowercase()] = header.substring(colon + 1).trim()
        }
        val body = ByteArray(headers["content-length"]?.toIntOrNull() ?: 0)
        var offset = 0
        while (offset < body.size) {
            val count = input.read(body, offset, body.size - offset)
            if (count < 0) break
            offset += count
        }
        return CapturedRequest(line, headers, body.toString(Charsets.UTF_8))
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
}
