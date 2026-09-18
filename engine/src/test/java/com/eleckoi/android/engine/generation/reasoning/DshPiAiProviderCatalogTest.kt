package com.eleckoi.android.engine.generation.reasoning

import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DshPiAiProviderCatalogTest {
    @Test
    fun `dedicated DeepSeek Responses route declares the four DSH levels literally`() {
        val option = ModelOption(
            id = "deepseek-flash",
            reasoningEfforts = mapOf("medium" to "medium"),
        )
        val config = ModelConfig(
            id = "deepseek-responses",
            provider = "deepseek",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.Responses,
        )

        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(listOf(config), LoopbackBaseUrl),
        ).jsonObject
        val provider = root.getValue(DshModelCapabilities.DeepSeekResponsesProviderRoute).jsonObject
        val model = provider.getValue("models").jsonArray.single().jsonObject
        val efforts = model.getValue("reasoningEfforts").jsonObject

        assertEquals("openai-responses", provider.getValue("api").jsonPrimitive.content)
        assertEquals("1000000", model.getValue("contextWindow").jsonPrimitive.content)
        assertEquals("none", efforts.getValue("off").jsonPrimitive.content)
        assertEquals("low", efforts.getValue("low").jsonPrimitive.content)
        assertEquals("high", efforts.getValue("high").jsonPrimitive.content)
        assertEquals("max", efforts.getValue("max").jsonPrimitive.content)
        assertFalse("medium" in efforts)
        assertFalse("compat" in model)
    }

    @Test
    fun `dedicated DeepSeek Anthropic route declares the same four DSH levels`() {
        val option = ModelOption(id = "deepseek-flash")
        val config = ModelConfig(
            id = "deepseek-anthropic",
            provider = "deepseek",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.AnthropicMessages,
        )

        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(listOf(config), LoopbackBaseUrl),
        ).jsonObject
        val provider = root.getValue(DshModelCapabilities.DeepSeekAnthropicProviderRoute).jsonObject
        val model = provider.getValue("models").jsonArray.single().jsonObject
        val efforts = model.getValue("reasoningEfforts").jsonObject

        assertEquals("anthropic-messages", provider.getValue("api").jsonPrimitive.content)
        assertEquals("none", efforts.getValue("off").jsonPrimitive.content)
        assertEquals("low", efforts.getValue("low").jsonPrimitive.content)
        assertEquals("high", efforts.getValue("high").jsonPrimitive.content)
        assertEquals("max", efforts.getValue("max").jsonPrimitive.content)
    }

    @Test
    fun `one DeepSeek connection registers only models using the active protocol`() {
        val responses = ModelOption("responses-model", apiFormatOverride = ModelApiFormat.Responses)
        val chat = ModelOption("chat-model", apiFormatOverride = ModelApiFormat.ChatCompletions)
        val config = ModelConfig(
            id = "mixed-deepseek",
            provider = "deepseek",
            model = responses.id,
            modelOptions = listOf(responses, chat),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(listOf(config), LoopbackBaseUrl),
        ).jsonObject
        val models = root.getValue(DshModelCapabilities.DeepSeekResponsesProviderRoute).jsonObject
            .getValue("models").jsonArray

        assertEquals(
            listOf("responses-model"),
            models.map { it.jsonObject.getValue("id").jsonPrimitive.content },
        )
    }


    @Test
    fun `known provider inherits installed catalog reasoning metadata`() {
        val config = ModelConfig(
            id = "openai-config",
            provider = "openai",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-future",
            modelOptions = listOf(ModelOption("gpt-future")),
            apiFormat = ModelApiFormat.Responses,
        )

        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(listOf(config), LoopbackBaseUrl),
        ).jsonObject
        val model = root.getValue("openai").jsonObject
            .getValue("models").jsonArray.single().jsonObject

        assertEquals("gpt-future", model.getValue("id").jsonPrimitive.content)
        assertFalse("reasoningEfforts" in model)
        assertFalse("compat" in model)
    }

    @Test
    fun `custom provider sends explicit effort spellings and thinking dialect`() {
        val option = ModelOption(
            id = "gateway-thinker",
            reasoningEfforts = linkedMapOf("off" to null, "high" to "strong", "max" to "ultra"),
            reasoningThinkingFormat = "deepseek",
        )
        val config = ModelConfig(
            id = "custom-config",
            provider = "custom",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        val route = DshPiAiProviderCatalog.providerRoute(config)
        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(listOf(config), LoopbackBaseUrl),
        ).jsonObject
        val model = root.getValue(route).jsonObject
            .getValue("models").jsonArray.single().jsonObject
        val efforts = model.getValue("reasoningEfforts").jsonObject

        assertTrue(route.startsWith("eleckoi-custom-"))
        assertEquals(JsonNull, efforts.getValue("off"))
        assertEquals("strong", efforts.getValue("high").jsonPrimitive.content)
        assertEquals("ultra", efforts.getValue("max").jsonPrimitive.content)
        assertEquals(
            "deepseek",
            model.getValue("compat").jsonObject.getValue("thinkingFormat").jsonPrimitive.content,
        )
    }

    @Test
    fun `official Google endpoint uses native pi-ai route for a custom product entry`() {
        val config = ModelConfig(
            id = "official-google",
            provider = "custom",
            baseUrl = "https://generativelanguage.googleapis.com",
            model = "models/gemini-2.5-flash",
            modelOptions = listOf(ModelOption("models/gemini-2.5-flash")),
            apiFormat = ModelApiFormat.GoogleGemini,
        )

        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(listOf(config), LoopbackBaseUrl),
        ).jsonObject
        val provider = root.getValue("google").jsonObject
        val model = provider.getValue("models").jsonArray.single().jsonObject

        assertFalse("api" in provider)
        assertEquals(
            "$LoopbackBaseUrl/provider-wire/google",
            provider.getValue("baseURL").jsonPrimitive.content,
        )
        assertEquals("gemini-2.5-flash", model.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `custom Google proxy is rejected instead of impersonating native route`() {
        val config = ModelConfig(
            id = "custom-google",
            provider = "custom",
            baseUrl = "https://gateway.example/google",
            model = "models/gemini-test",
            modelOptions = listOf(ModelOption("models/gemini-test")),
            apiFormat = ModelApiFormat.GoogleGemini,
        )

        assertThrows(IllegalArgumentException::class.java) {
            DshPiAiProviderCatalog.providersJson(listOf(config), LoopbackBaseUrl)
        }
    }

    @Test
    fun `native Google keeps the one official route across Android side proxy settings`() {
        val direct = ModelConfig(
            id = "google-direct",
            provider = "google",
            baseUrl = "https://generativelanguage.googleapis.com",
            model = "models/gemini-a",
            modelOptions = listOf(ModelOption("models/gemini-a")),
            apiFormat = ModelApiFormat.GoogleGemini,
        )
        val proxied = direct.copy(
            id = "google-proxied",
            model = "models/gemini-b",
            modelOptions = listOf(ModelOption("models/gemini-b")),
            proxyUrl = "http://127.0.0.1:8888",
        )
        val active = listOf(direct, proxied)

        assertEquals("google", DshPiAiProviderCatalog.providerRoute(direct, active))
        assertEquals("google", DshPiAiProviderCatalog.providerRoute(proxied, active))
        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(active, LoopbackBaseUrl),
        ).jsonObject
        assertEquals(setOf("google"), root.keys)
    }

    @Test
    fun `one official provider splits independent protocols into stable routes`() {
        val responses = ModelConfig(
            id = "openai-responses",
            provider = "openai",
            baseUrl = "https://api.openai.com/v1",
            model = "a",
            modelOptions = listOf(ModelOption("a")),
            apiFormat = ModelApiFormat.Responses,
        )
        val chat = ModelConfig(
            id = "openai-chat",
            provider = "openai",
            baseUrl = "https://api.openai.com/v1",
            model = "b",
            modelOptions = listOf(ModelOption("b")),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        val active = listOf(responses, chat)
        val responsesRoute = DshPiAiProviderCatalog.providerRoute(responses, active)
        val chatRoute = DshPiAiProviderCatalog.providerRoute(chat, active)
        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(active, LoopbackBaseUrl),
        ).jsonObject

        assertEquals("openai", responsesRoute)
        assertTrue(chatRoute.startsWith("eleckoi-custom-"))
        assertTrue(responsesRoute != chatRoute)
        assertEquals(
            "openai-responses",
            root.getValue(responsesRoute).jsonObject.getValue("api").jsonPrimitive.content,
        )
        assertEquals(
            "openai-completions",
            root.getValue(chatRoute).jsonObject.getValue("api").jsonPrimitive.content,
        )
    }

    @Test
    fun `saved configs sharing one connection keep the native catalog route`() {
        val first = ModelConfig(
            id = "openai-first",
            provider = "openai",
            baseUrl = "https://api.openai.com/v1",
            model = "a",
            modelOptions = listOf(ModelOption("a")),
            apiFormat = ModelApiFormat.Responses,
        )
        val second = first.copy(
            id = "openai-second",
            model = "b",
            modelOptions = listOf(ModelOption("b")),
        )
        val active = listOf(first, second)

        assertEquals("openai", DshPiAiProviderCatalog.providerRoute(first, active))
        assertEquals("openai", DshPiAiProviderCatalog.providerRoute(second, active))
        val root = Json.parseToJsonElement(
            DshPiAiProviderCatalog.providersJson(active, LoopbackBaseUrl),
        ).jsonObject
        assertEquals(setOf("openai"), root.keys)
    }

    private companion object {
        const val LoopbackBaseUrl = "http://127.0.0.1:12345/runtime01"
    }
}
