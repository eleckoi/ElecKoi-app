package com.eleckoi.android.engine.generation.reasoning

import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DshReasoningEffortsTest {
    @Test
    fun `Kimi K3 Chat exposes only its declared efforts`() {
        val option = ModelOption("kimi-k3")
        val config = ModelConfig(
            provider = "moonshot",
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.ChatCompletions,
        )

        assertEquals(
            listOf("low", "high", "max"),
            DshReasoningEfforts.forModel(config, option).map { it.id },
        )
        assertEquals(DshModelCapabilities.KimiK3WireProfile, DshModelCapabilities.active(config).wireProfile)
    }

    @Test
    fun `unknown models and unsupported protocols do not guess reasoning levels`() {
        ModelApiFormat.entries.forEach { format ->
            val option = ModelOption("custom-model")
            val config = ModelConfig(model = option.id, modelOptions = listOf(option), apiFormat = format)
            assertEquals(emptyList<String>(), DshReasoningEfforts.forModel(config, option).map { it.id })
            assertEquals(
                DshModelCapabilities.DefaultWireProfile,
                DshModelCapabilities.active(config).wireProfile,
            )
        }

        val kimiResponses = ModelConfig(model = "kimi-k3", apiFormat = ModelApiFormat.Responses)
        assertEquals(emptyList<String>(), DshReasoningEfforts.forModel(kimiResponses, ModelOption("kimi-k3")).map { it.id })
    }

    @Test
    fun `selected effort is validated against the exact model capability`() {
        val option = ModelOption("kimi-k3", reasoningEffort = "MAX")
        val kimi = ModelConfig(
            model = option.id,
            modelOptions = listOf(option),
            apiFormat = ModelApiFormat.ChatCompletions,
        )
        val unsupportedOff = kimi.copy(modelOptions = listOf(option.copy(reasoningEffort = "off")))
        val unknown = kimi.copy(model = "custom", modelOptions = listOf(ModelOption("custom", reasoningEffort = "high")))

        assertEquals("max", DshReasoningEfforts.selected(kimi))
        assertNull(DshReasoningEfforts.selected(unsupportedOff))
        assertNull(DshReasoningEfforts.selected(unknown))
        assertEquals("跟随模型默认", DshReasoningEfforts.label(null))
    }
}
