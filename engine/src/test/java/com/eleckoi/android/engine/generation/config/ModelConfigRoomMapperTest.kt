package com.eleckoi.android.engine.generation.config

import com.eleckoi.android.foundation.storage.room.ModelConfigEntity
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.ImageGenerationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.KeyGenerator

class ModelConfigRoomMapperTest {
    private val key = KeyGenerator.getInstance("AES").run {
        init(256)
        generateKey()
    }
    private val codec = AesGcmModelSecretCodec(keyProvider = { key })

    @Test
    fun `manual origin survives persistence while provider entries stay protected`() {
        val manual = ModelOption("manual", isUserAdded = true)
        val provider = ModelOption("provider")
        val config = ModelConfig(id = "origin-test", model = "manual", modelOptions = listOf(manual, provider))

        val restored = config.toEntity(codec).toModelConfig(codec)

        assertTrue(restored.modelOptions.first().isUserAdded)
        assertFalse(restored.modelOptions.last().isUserAdded)
        val deleted = restored.copy(model = "provider", modelOptions = listOf(provider))
            .toEntity(codec).toModelConfig(codec)
        assertEquals(listOf(provider), deleted.modelOptions)
    }

    @Test
    fun `damaged ciphertext preserves metadata and requests key reentry`() {
        val entity = entity(apiKey = "egsec:v2:aes-gcm:not-valid-base64!")

        val config = entity.toModelConfig(codec)

        assertEquals("config-7", config.id)
        assertEquals("DeepSeek 主配置", config.name)
        assertEquals("https://example.invalid/v1", config.baseUrl)
        assertEquals("deepseek-chat", config.model)
        assertEquals("", config.apiKey)
        assertTrue(config.apiKeyNeedsReentry)
    }

    @Test
    fun `unavailable state survives unrelated saves and a new key recovers it`() {
        val unavailable = entity(apiKey = ModelSecretUnavailableMarker).toModelConfig(codec)
        assertTrue(unavailable.apiKeyNeedsReentry)

        val savedWithoutKey = unavailable.copy(name = "仍保留的配置").toEntity(codec)
        assertEquals(ModelSecretUnavailableMarker, savedWithoutKey.apiKey)
        assertTrue(savedWithoutKey.toModelConfig(codec).apiKeyNeedsReentry)

        val recoveredEntity = unavailable.copy(apiKey = "sk-new-secret").toEntity(codec)
        val recovered = recoveredEntity.toModelConfig(codec)
        assertEquals("sk-new-secret", recovered.apiKey)
        assertFalse(recovered.apiKeyNeedsReentry)
    }

    @Test
    fun `blank key without a previous decryption failure remains ordinary missing input`() {
        val config = entity(apiKey = "").toModelConfig(codec)

        assertEquals("", config.apiKey)
        assertFalse(config.apiKeyNeedsReentry)
        assertEquals("", config.toEntity(codec).apiKey)
    }

    @Test
    fun `per model token limits survive room json mapping`() {
        val source = ModelConfig(
            id = "config-limits",
            model = "deepseek-v4-flash",
            modelOptions = listOf(
                ModelOption(
                    id = "deepseek-v4-flash",
                    contextWindowTokens = 1_000_000,
                    autoCompactTokenLimit = 258_000,
                    maxOutputTokens = 128_000,
                    temperature = 0.6,
                    topP = 0.85,
                    apiFormatOverride = ModelApiFormat.Responses,
                ),
            ),
        )

        val restored = source.toEntity(codec).toModelConfig(codec)

        assertEquals(1_000_000, restored.modelOptions.single().contextWindowTokens)
        assertEquals(258_000, restored.modelOptions.single().autoCompactTokenLimit)
        assertEquals(128_000, restored.modelOptions.single().maxOutputTokens)
        assertEquals(0.6, restored.modelOptions.single().temperature)
        assertEquals(0.85, restored.modelOptions.single().topP)
        assertEquals(ModelApiFormat.Responses, restored.modelOptions.single().apiFormatOverride)
    }

    @Test
    fun `blank sampling values stay null and are not written to room json`() {
        val source = ModelConfig(
            id = "config-provider-defaults",
            model = "custom-model",
            modelOptions = listOf(ModelOption(id = "custom-model")),
        )

        val entity = source.toEntity(codec)
        val restored = entity.toModelConfig(codec).modelOptions.single()

        assertFalse(entity.modelOptionsJson.contains("temperature"))
        assertFalse(entity.modelOptionsJson.contains("topP"))
        assertEquals(null, restored.temperature)
        assertEquals(null, restored.topP)
    }

    @Test
    fun `selected api format survives room mapping and unknown values fail fast`() {
        ModelApiFormat.entries.forEach { format ->
            val restored = ModelConfig(id = "config-format", apiFormat = format)
                .toEntity(codec)
                .toModelConfig(codec)

            assertEquals(format, restored.apiFormat)
        }

        assertThrows(IllegalArgumentException::class.java) {
            entity(apiKey = "").copy(apiFormat = "future_unknown_format").toModelConfig(codec)
        }
    }

    @Test
    fun `DSH reasoning effort survives room json mapping`() {
        val source = ModelConfig(
            id = "config-reasoning",
            model = "deepseek-v4-flash",
            modelOptions = listOf(
                ModelOption(
                    id = "deepseek-v4-flash",
                    reasoningEffort = "max",
                ),
            ),
        )

        val restored = source.toEntity(codec).toModelConfig(codec)
        val option = restored.modelOptions.single()

        assertEquals("max", option.reasoningEffort)
    }

    @Test
    fun `image generation enabled switch survives room mapping`() {
        val restored = ModelConfig(
            id = "novelai",
            provider = "novelai_image",
            enabled = true,
            imageSettings = ImageGenerationSettings(
                width = 1024,
                height = 1536,
                steps = 32,
                scale = 6.0,
                sampler = "k_dpmpp_2m",
                automaticImageCount = true,
                fixedImageCount = 4,
                automaticImageMin = 2,
                automaticImageMax = 9,
                promptCompilerInstruction = "Return strict NovelAI JSON tags.",
                promptPrefix = "anime screencap",
                negativePrompt = "photorealistic",
            ),
        ).toEntity(codec).toModelConfig(codec)

        assertTrue(restored.enabled)
        assertEquals("novelai_image", restored.provider)
        assertEquals(1024, restored.imageSettings.width)
        assertEquals(1536, restored.imageSettings.height)
        assertEquals(32, restored.imageSettings.steps)
        assertEquals(6.0, restored.imageSettings.scale, 0.0)
        assertEquals("k_dpmpp_2m", restored.imageSettings.sampler)
        assertTrue(restored.imageSettings.automaticImageCount)
        assertEquals(4, restored.imageSettings.fixedImageCount)
        assertEquals(2, restored.imageSettings.automaticImageMin)
        assertEquals(9, restored.imageSettings.automaticImageMax)
        assertEquals(
            "Return strict NovelAI JSON tags.",
            restored.imageSettings.promptCompilerInstruction,
        )
        assertEquals("anime screencap", restored.imageSettings.promptPrefix)
        assertEquals("photorealistic", restored.imageSettings.negativePrompt)
    }

    @Test
    fun `long image action instruction survives room mapping without truncation`() {
        val instruction = "BEGIN\n" + "compiler rule\n".repeat(1_100) + "END"

        val restored = ModelConfig(
            id = "long-image-action",
            imageSettings = ImageGenerationSettings(
                promptCompilerInstruction = instruction,
            ),
        ).toEntity(codec).toModelConfig(codec)

        assertTrue(instruction.length > 12_000)
        assertEquals(instruction, restored.imageSettings.promptCompilerInstruction)
    }

    private fun entity(apiKey: String) = ModelConfigEntity(
        id = "config-7",
        name = "DeepSeek 主配置",
        provider = "deepseek",
        apiKey = apiKey,
        baseUrl = "https://example.invalid/v1",
        proxyUrl = "",
        model = "deepseek-chat",
        modelOptionsJson = "[]",
    )
}
