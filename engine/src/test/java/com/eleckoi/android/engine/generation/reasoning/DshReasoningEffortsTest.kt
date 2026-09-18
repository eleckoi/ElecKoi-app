package com.eleckoi.android.engine.generation.reasoning

import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshReasoningEffortsTest {
    @Test
    fun `dedicated DeepSeek Chat entry uses the native DSH route`() {
        val option = ModelOption("future-model")
        val config = ModelConfig(
            provider = "deepseek",
            baseUrl = "https://relay.example/v1",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        assertEquals(
            listOf("off", "low", "high", "max"),
            DshReasoningEfforts.forModel(config, option).map { it.id },
        )
        assertEquals(
            DshModelCapabilities.DeepSeekOfficialWireProfile,
            DshModelCapabilities.active(config).wireProfile,
        )
        assertEquals(DshProviderApi.OpenAiCompletions, config.effectiveDshApi())
    }

    @Test
    fun `dedicated DeepSeek Responses entry uses pi-ai with the same four levels`() {
        val option = ModelOption("future-model")
        val config = ModelConfig(
            provider = "deepseek",
            baseUrl = "https://relay.example/v1",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.Responses,
        )

        assertEquals(
            listOf("off", "low", "high", "max"),
            DshReasoningEfforts.forModel(config, option).map { it.id },
        )
        assertEquals(
            DshModelCapabilities.PiAiWireProfile,
            DshModelCapabilities.active(config).wireProfile,
        )
        assertEquals(DshProviderApi.OpenAiResponses, config.effectiveDshApi())
        assertEquals(
            DshModelCapabilities.DeepSeekResponsesProviderRoute,
            DshPiAiProviderCatalog.providerRoute(config),
        )
    }

    @Test
    fun `dedicated DeepSeek Anthropic entry uses pi-ai with the same four levels`() {
        val option = ModelOption("future-model")
        val config = ModelConfig(
            provider = "deepseek",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.AnthropicMessages,
        )

        assertEquals(
            listOf("off", "low", "high", "max"),
            DshReasoningEfforts.forModel(config, option).map { it.id },
        )
        assertEquals(
            DshModelCapabilities.PiAiWireProfile,
            DshModelCapabilities.active(config).wireProfile,
        )
        assertEquals(DshProviderApi.AnthropicMessages, config.effectiveDshApi())
        assertEquals(
            DshModelCapabilities.DeepSeekAnthropicProviderRoute,
            DshPiAiProviderCatalog.providerRoute(config),
        )
    }

    @Test
    fun `per-model DeepSeek protocol override selects the matching DSH adapter`() {
        val chat = ModelOption("chat-model")
        val responses = ModelOption(
            "responses-model",
            apiFormatOverride = ModelApiFormat.Responses,
        )
        val config = ModelConfig(
            provider = "deepseek",
            model = chat.id,
            modelOptions = listOf(chat, responses),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        assertEquals(
            DshModelCapabilities.DeepSeekOfficialWireProfile,
            DshModelCapabilities.forModel(config, chat).wireProfile,
        )
        assertEquals(
            DshModelCapabilities.PiAiWireProfile,
            DshModelCapabilities.forModel(config, responses).wireProfile,
        )
    }

    @Test
    fun `custom routes never infer DeepSeek capability from model or URL`() {
        val option = ModelOption("deepseek-flash")
        val config = ModelConfig(
            provider = "custom",
            baseUrl = "https://api.deepseek.com",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        assertEquals(emptyList<String>(), DshReasoningEfforts.forModel(config, option).map { it.id })
        assertEquals(
            DshModelCapabilities.PiAiWireProfile,
            DshModelCapabilities.active(config).wireProfile,
        )
    }

    @Test
    fun `official product entries project the bundled pi-ai catalog without sending overrides`() {
        val kimi = ModelOption(
            "kimi-k3",
            reasoningEffort = "max",
            dshReasoningEffortIds = listOf("low", "high", "max"),
        )
        val config = ModelConfig(
            provider = "moonshot",
            model = kimi.id,
            modelOptions = listOf(kimi),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        assertEquals(
            listOf("low", "high", "max"),
            DshReasoningEfforts.forModel(config, kimi).map { it.id },
        )
        assertEquals("max", DshReasoningEfforts.selected(config))
        assertNull(kimi.reasoningEfforts)
    }

    @Test
    fun `explicit DSH reasoningEfforts determines the exact selectable levels`() {
        val option = ModelOption(
            id = "gateway-model",
            reasoningEfforts = linkedMapOf(
                "off" to null,
                "low" to "lite",
                "max" to "ultra",
            ),
            reasoningThinkingFormat = "deepseek",
        )
        val config = ModelConfig(
            provider = "custom",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        assertEquals(
            listOf("off", "low", "max"),
            DshReasoningEfforts.forModel(config, option).map { it.id },
        )
    }

    @Test
    fun `reasoningEfforts false exposes no selector levels`() {
        val option = ModelOption("plain", reasoningEfforts = emptyMap())
        val config = ModelConfig(model = option.id, modelOptions = listOf(option))

        assertEquals(emptyList<String>(), DshReasoningEfforts.forModel(config, option).map { it.id })
    }

    @Test
    fun `selected effort is validated against the exact model capability`() {
        val declared = linkedMapOf("off" to null, "high" to "high", "max" to "ultra")
        val option = ModelOption("custom", reasoningEfforts = declared, reasoningEffort = "MAX")
        val config = ModelConfig(model = option.id, modelOptions = listOf(option))
        val unsupported = config.copy(
            modelOptions = listOf(option.copy(reasoningEffort = "low")),
        )
        val deepSeek = ModelConfig(
            provider = "deepseek",
            model = "unlisted",
            modelOptions = listOf(ModelOption("unlisted", reasoningEffort = "off")),
        )

        assertEquals("max", DshReasoningEfforts.selected(config))
        assertNull(DshReasoningEfforts.selected(unsupported))
        assertEquals("off", DshReasoningEfforts.selected(deepSeek))
        assertEquals("跟随提供方默认", DshReasoningEfforts.label(null))
    }

    @Test
    fun `custom route uses the PC style seven-level list and accumulates the selected profile`() {
        val option = ModelOption("gateway-model")
        val config = ModelConfig(
            provider = "custom",
            baseUrl = "https://gateway.example/v1",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.Responses,
        )

        assertTrue(DshModelCapabilities.usesCustomReasoningList(config, option))
        val high = DshReasoningEfforts.withCustomReasoningEffort(null, "high")
        val withOff = DshReasoningEfforts.withCustomReasoningEffort(high, "off")
        assertEquals(linkedMapOf("high" to "high", "off" to null), withOff)
        assertEquals(withOff, DshReasoningEfforts.withCustomReasoningEffort(withOff, null))
    }

    @Test
    fun `official DeepSeek always keeps the DSH four-level catalog`() {
        val option = ModelOption("future-model")
        val config = ModelConfig(
            provider = "deepseek",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.Responses,
        )

        assertEquals(false, DshModelCapabilities.usesCustomReasoningList(config, option))
    }

    @Test
    fun `pi-ai catalog routes require the pinned protocol and endpoint`() {
        assertEquals(
            "eleckoi-custom",
            DshModelCapabilities.piAiCatalogProvider(ModelConfig(provider = "zhipu")),
        )
        assertEquals(
            "zai",
            DshModelCapabilities.piAiCatalogProvider(
                ModelConfig(
                    provider = "custom",
                    baseUrl = "https://api.z.ai/api/coding/paas/v4",
                    apiFormat = ModelApiFormat.ChatCompletions,
                ),
            ),
        )
        assertEquals(
            "moonshotai-cn",
            DshModelCapabilities.piAiCatalogProvider(
                ModelConfig(provider = "moonshot", apiFormat = ModelApiFormat.ChatCompletions),
            ),
        )
        assertEquals(
            "eleckoi-custom",
            DshModelCapabilities.piAiCatalogProvider(
                ModelConfig(
                    provider = "openai",
                    baseUrl = "https://gateway.example/v1",
                    apiFormat = ModelApiFormat.Responses,
                ),
            ),
        )
        assertEquals(
            DshModelCapabilities.DeepSeekResponsesProviderRoute,
            DshModelCapabilities.piAiCatalogProvider(ModelConfig(provider = "deepseek")),
        )
    }
}
