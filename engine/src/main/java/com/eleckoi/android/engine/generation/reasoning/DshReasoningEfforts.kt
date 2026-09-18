package com.eleckoi.android.engine.generation.reasoning

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption

/** A DSH-native reasoning level. pi-ai owns its final provider-protocol representation. */
data class DshReasoningEffortOption(
    val id: String,
    val label: String,
)

object DshReasoningEfforts {
    val allOptions = listOf(
        DshReasoningEffortOption("off", "关闭"),
        DshReasoningEffortOption("minimal", "极低"),
        DshReasoningEffortOption("low", "低"),
        DshReasoningEffortOption("medium", "中"),
        DshReasoningEffortOption("high", "高"),
        DshReasoningEffortOption("xhigh", "极高"),
        DshReasoningEffortOption("max", "最高"),
    )

    val supportedIds: Set<String> = allOptions.mapTo(linkedSetOf()) { it.id }

    val supportedThinkingFormats: List<String> = listOf(
        "openai",
        "openrouter",
        "deepseek",
        "together",
        "baseten",
        "zai",
        "qwen",
        "chat-template",
        "qwen-chat-template",
        "string-thinking",
        "ant-ling",
    )

    fun optionsFor(ids: Collection<String>): List<DshReasoningEffortOption> {
        val normalized = ids.mapTo(hashSetOf()) { it.trim().lowercase() }
        return allOptions.filter { it.id in normalized }
    }

    /** Adds the selected level to a custom DSH profile without discarding earlier spellings. */
    fun withCustomReasoningEffort(
        profile: Map<String, String?>?,
        effort: String?,
    ): Map<String, String?>? {
        val normalized = effort?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            ?: return profile
        require(normalized in supportedIds) { "不支持的推理档位：$effort" }
        return linkedMapOf<String, String?>().apply {
            putAll(profile.orEmpty())
            put(normalized, if (normalized == "off") null else normalized)
        }
    }

    fun forModel(config: ModelConfig, option: ModelOption): List<DshReasoningEffortOption> =
        DshModelCapabilities.forModel(config, option).reasoningEfforts

    fun selected(config: ModelConfig): String? {
        val option = config.modelOptions.firstOrNull { it.id == config.model.trim() } ?: return null
        val selected = option.reasoningEffort?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            ?: return null
        return selected.takeIf { candidate -> forModel(config, option).any { it.id == candidate } }
    }

    fun label(id: String?): String = when (id?.trim()?.lowercase()) {
        null, "" -> "跟随提供方默认"
        else -> (allOptions.firstOrNull { it.id == id.trim().lowercase() }?.label ?: "跟随提供方默认")
    }
}
