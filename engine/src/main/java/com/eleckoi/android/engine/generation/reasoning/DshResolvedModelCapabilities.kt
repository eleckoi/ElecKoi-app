package com.eleckoi.android.engine.generation.reasoning

/** Exact model metadata returned by DSH's registered adapter. */
data class DshResolvedModelCapabilities(
    val reasoningEffortIds: List<String>,
    val contextWindowTokens: Int?,
    val maxOutputTokens: Int?,
    val supportsImageInput: Boolean?,
)
