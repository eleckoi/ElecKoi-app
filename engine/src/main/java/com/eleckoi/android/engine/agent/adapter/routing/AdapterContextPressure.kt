package com.eleckoi.android.engine.agent.adapter

/** One client-visible `contextPressure` value emitted by DSH's projection registry. */
internal data class AdapterContextPressure(
    val sessionId: String,
    val sequence: Long,
    val pressureTokens: Long?,
    val projectedTokens: Long?,
    val contextWindow: Long?,
    val systemTokens: Long?,
    val toolsTokens: Long?,
    val messageTokens: Long?,
)
