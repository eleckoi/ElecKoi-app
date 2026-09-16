package com.eleckoi.android.engine.generation.provider

import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiCompatibleClientGeminiModelsTest {
    @Test
    fun `Gemini model discovery uses native endpoint authentication and response shape`() {
        val requestPath = AtomicReference<String>()
        val apiKey = AtomicReference<String>()
        val authorization = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requestPath.set(exchange.requestURI.toString())
                apiKey.set(exchange.requestHeaders.getFirst("x-goog-api-key"))
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                val body = """
                    {
                      "models": [
                        {
                          "name": "models/gemini-test",
                          "inputTokenLimit": 1048576,
                          "outputTokenLimit": 8192,
                          "supportedGenerationMethods": ["generateContent"]
                        },
                        {
                          "name": "models/embedding-test",
                          "supportedGenerationMethods": ["embedContent"]
                        }
                      ]
                    }
                """.trimIndent().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        try {
            val models = OpenAiCompatibleClient().fetchModels(
                ModelConfig(
                    provider = "custom",
                    baseUrl = "http://127.0.0.1:${server.address.port}/v1beta/openai",
                    apiKey = "gemini-key",
                    apiFormat = ModelApiFormat.GoogleGemini,
                ),
            )

            assertEquals("/v1beta/models?pageSize=1000", requestPath.get())
            assertEquals("gemini-key", apiKey.get())
            assertNull(authorization.get())
            assertEquals(1, models.size)
            assertEquals("models/gemini-test", models.single().id)
            assertEquals(1_048_576, models.single().contextWindowTokens)
            assertEquals(8_192, models.single().maxOutputTokens)
        } finally {
            server.stop(0)
        }
    }
}
