package com.eleckoi.android.engine.generation.reasoning

import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.effectiveApiFormat

/**
 * Capabilities that ElecKoi can identify without guessing from a custom endpoint or model name.
 * The wire profile selects a matching pi-ai catalog route after Android has projected chat context.
 */
data class DshModelCapabilityProfile(
    val wireProfile: String,
    val reasoningEfforts: List<DshReasoningEffortOption>,
)

object DshModelCapabilities {
    const val DefaultWireProfile = "default"
    const val KimiK3WireProfile = "kimi-k3"

    private val default = DshModelCapabilityProfile(
        wireProfile = DefaultWireProfile,
        reasoningEfforts = emptyList(),
    )
    private val kimiK3 = DshModelCapabilityProfile(
        wireProfile = KimiK3WireProfile,
        reasoningEfforts = listOf(
            DshReasoningEffortOption("low", "低"),
            DshReasoningEffortOption("high", "高"),
            DshReasoningEffortOption("max", "最高"),
        ),
    )

    fun active(config: ModelConfig): DshModelCapabilityProfile {
        val selected = config.model.trim()
        val option = config.modelOptions.firstOrNull { it.id == selected }
            ?: ModelOption(id = selected)
        return forModel(config, option)
    }

    fun forModel(config: ModelConfig, option: ModelOption): DshModelCapabilityProfile {
        if (option.id.isBlank()) return default
        val selectedConfig = config.copy(model = option.id)
        return if (
            selectedConfig.effectiveApiFormat() == ModelApiFormat.ChatCompletions &&
            option.id.trim().equals(KimiK3ModelId, ignoreCase = true)
        ) {
            kimiK3
        } else {
            default
        }
    }

    private const val KimiK3ModelId = "kimi-k3"
}
