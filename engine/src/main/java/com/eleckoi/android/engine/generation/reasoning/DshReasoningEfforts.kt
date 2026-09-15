package com.eleckoi.android.engine.generation.reasoning

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption

/** A DSH-native reasoning level. pi-ai owns its final provider-protocol representation. */
data class DshReasoningEffortOption(
    val id: String,
    val label: String,
)

object DshReasoningEfforts {
    private val labels = listOf(
        DshReasoningEffortOption("off", "关闭"),
        DshReasoningEffortOption("minimal", "最低"),
        DshReasoningEffortOption("low", "低"),
        DshReasoningEffortOption("medium", "中"),
        DshReasoningEffortOption("high", "高"),
        DshReasoningEffortOption("xhigh", "极高"),
        DshReasoningEffortOption("max", "最高"),
    )

    fun forModel(config: ModelConfig, option: ModelOption): List<DshReasoningEffortOption> =
        DshModelCapabilities.forModel(config, option).reasoningEfforts

    fun selected(config: ModelConfig): String? {
        val option = config.modelOptions.firstOrNull { it.id == config.model.trim() } ?: return null
        val selected = option.reasoningEffort?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            ?: return null
        return selected.takeIf { candidate -> forModel(config, option).any { it.id == candidate } }
    }

    fun label(id: String?): String = when (id?.trim()?.lowercase()) {
        null, "" -> "跟随模型默认"
        else -> (labels.firstOrNull { it.id == id.trim().lowercase() }?.label ?: "跟随模型默认")
    }
}
