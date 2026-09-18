package com.eleckoi.android.engine.generation.config

import com.eleckoi.android.engine.generation.model.DeepSeekOfficialVisionModel
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.isOfficialDeepSeekEndpoint

/** Refresh the provider catalog without discarding user-added models or per-model choices. */
internal fun mergeFetchedModelOptions(config: ModelConfig, fetched: List<ModelOption>): List<ModelOption> {
    val previousById = config.modelOptions.associateBy { it.id }
    val refreshed = fetched.distinctBy { it.id }.map { option ->
        val previous = previousById[option.id]
        option.copy(
            isUserAdded = previous?.isUserAdded == true,
            contextWindowTokens = previous?.contextWindowTokens ?: option.contextWindowTokens,
            autoCompactTokenLimit = previous?.autoCompactTokenLimit ?: option.autoCompactTokenLimit,
            maxOutputTokens = previous?.maxOutputTokens ?: option.maxOutputTokens,
            temperature = if (previous != null) previous.temperature else option.temperature,
            topP = if (previous != null) previous.topP else option.topP,
            reasoningEffort = previous?.reasoningEffort,
            reasoningEfforts = previous?.reasoningEfforts ?: option.reasoningEfforts,
            dshReasoningEffortIds = previous?.dshReasoningEffortIds
                ?: option.dshReasoningEffortIds,
            reasoningThinkingFormat = previous?.reasoningThinkingFormat
                ?: option.reasoningThinkingFormat,
            apiFormatOverride = previous?.apiFormatOverride,
            supportsImageInput = previous?.supportsImageInput == true ||
                (config.isOfficialDeepSeekEndpoint() &&
                    option.id.equals(DeepSeekOfficialVisionModel, ignoreCase = true)),
        )
    }
    val fetchedIds = refreshed.mapTo(hashSetOf()) { it.id }
    val manualOnly = config.modelOptions.filter { it.isUserAdded && it.id !in fetchedIds }
    return manualOnly + refreshed
}
