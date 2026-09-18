package com.eleckoi.android.engine.generation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigContextWindowTest {
    @Test
    fun `uses the selected model context window for the Harness`() {
        val config = ModelConfig(
            model = "deepseek-v4-pro",
            modelOptions = listOf(
                ModelOption(
                    id = "deepseek-v4-pro",
                    contextWindowTokens = 1_000_000,
                ),
            ),
        )

        assertEquals(1_000_000, config.configuredContextWindowTokens())
    }

    @Test
    fun `uses the selected models absolute automatic compaction threshold`() {
        val config = ModelConfig(
            model = "deepseek-v4-pro",
            modelOptions = listOf(
                ModelOption(
                    id = "deepseek-v4-pro",
                    contextWindowTokens = 1_000_000,
                    autoCompactTokenLimit = 2_000,
                ),
            ),
        )

        assertEquals(2_000, config.configuredAutoCompactTokenLimit())
    }

    @Test
    fun `falls back to the declared agent window when metadata is absent`() {
        assertEquals(
            ModelOption.AgentFallbackContextWindowTokens,
            ModelConfig(model = "unlisted").configuredContextWindowTokens(),
        )
    }

    @Test
    fun `dedicated DeepSeek route defaults missing model metadata to one million tokens`() {
        assertEquals(
            DeepSeekOfficialContextWindowTokens,
            ModelConfig(
                provider = "deepseek",
                model = "deepseek-v4-pro",
            ).configuredContextWindowTokens(),
        )
        assertEquals(
            ModelOption.AgentFallbackContextWindowTokens,
            ModelConfig(
                provider = "custom",
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-v4-flash",
            ).configuredContextWindowTokens(),
        )
    }

    @Test
    fun `dedicated DeepSeek relay still uses the official DSH adapter default`() {
        assertEquals(
            DeepSeekOfficialContextWindowTokens,
            ModelConfig(
                provider = "deepseek",
                baseUrl = "https://relay.example/v1",
                model = "deepseek-v4-pro",
            ).configuredContextWindowTokens(),
        )
    }

    @Test
    fun `explicit official DeepSeek model capacity overrides the provider default`() {
        assertEquals(
            128_000,
            ModelConfig(
                provider = "deepseek",
                model = "deepseek-v4-pro",
                modelOptions = listOf(
                    ModelOption(id = "deepseek-v4-pro", contextWindowTokens = 128_000),
                ),
            ).configuredContextWindowTokens(),
        )
    }

    @Test
    fun `new custom configurations prefer Responses while dedicated providers use Chat`() {
        assertEquals(ModelApiFormat.Responses, defaultApiFormatForProvider("custom"))
        assertEquals(ModelApiFormat.ChatCompletions, defaultApiFormatForProvider("deepseek"))
        assertEquals(ModelApiFormat.ChatCompletions, defaultApiFormatForProvider("zhipu"))
        assertEquals(ZhipuDefaultBaseUrl, defaultBaseUrlForProvider("zhipu"))
        assertEquals(ZaiDefaultBaseUrl, defaultBaseUrlForProvider("zai"))
        assertEquals(MoonshotDefaultBaseUrl, defaultBaseUrlForProvider("moonshot"))
    }

    @Test
    fun `dedicated DeepSeek keeps the selected connection and model wire formats`() {
        val connectionResponses = ModelConfig(
            provider = "deepseek",
            apiFormat = ModelApiFormat.Responses,
            model = "deepseek-flash",
        )
        val modelOverride = connectionResponses.copy(
            modelOptions = listOf(
                ModelOption(
                    id = "deepseek-flash",
                    apiFormatOverride = ModelApiFormat.ChatCompletions,
                ),
            ),
        )

        assertEquals(ModelApiFormat.Responses, connectionResponses.effectiveApiFormat())
        assertEquals(ModelApiFormat.ChatCompletions, modelOverride.effectiveApiFormat())
    }

    @Test
    fun `new model sampling follows provider defaults while explicit one and zero remain values`() {
        val defaults = ModelOption("model")
        val inherited = ModelConfig(model = "model", modelOptions = listOf(defaults))
        val explicitOne = inherited.copy(
            modelOptions = listOf(defaults.copy(temperature = 1.0, topP = 1.0)),
        )
        val explicitZero = inherited.copy(
            modelOptions = listOf(defaults.copy(temperature = 0.0, topP = 0.0)),
        )

        assertEquals(null, inherited.configuredTemperature())
        assertEquals(null, inherited.configuredTopP())
        assertEquals(1.0, explicitOne.configuredTemperature())
        assertEquals(1.0, explicitOne.configuredTopP())
        assertEquals(0.0, explicitZero.configuredTemperature())
        assertEquals(0.0, explicitZero.configuredTopP())
    }

    @Test
    fun `official DeepSeek vision model declares image input without a manual switch`() {
        assertTrue(
            ModelConfig(
                provider = "deepseek",
                model = DeepSeekOfficialVisionModel,
            ).supportsImageInput(),
        )
        assertFalse(
            ModelConfig(
                provider = "deepseek",
                model = "deepseek-v4-flash",
            ).supportsImageInput(),
        )
        assertTrue(
            ModelConfig(
                provider = "deepseek",
                baseUrl = "https://relay.example/v1",
                model = DeepSeekOfficialVisionModel,
            ).supportsImageInput(),
        )
        assertTrue(
            ModelConfig(
                provider = "deepseek",
                model = "deepseek-flash",
            ).supportsImageInput(),
        )
    }

    @Test
    fun `image input follows the conversation selected model instead of the connection default`() {
        val config = ModelConfig(
            provider = "deepseek",
            model = "deepseek-v4-flash",
            modelOptions = listOf(
                ModelOption(id = "deepseek-v4-flash"),
                ModelOption(id = "custom-vision", supportsImageInput = true),
            ),
        )

        assertFalse(config.supportsImageInput())
        assertTrue(config.supportsImageInput("custom-vision"))
        assertTrue(config.supportsImageInput(DeepSeekOfficialVisionModel))
    }
}
