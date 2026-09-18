package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentTokenUsage
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.feature.chat.model.ChatContextWindowUsage
import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics

/**
 * Folds native Agent events into one reply's durable display measurements.
 *
 * It records only boundaries DSH actually emitted. Missing cache or timing fields are deliberately
 * left absent from the resulting UI instead of being estimated from local text length.
 */
internal class ChatTurnMetricsCollector {
    private data class StepBoundary(
        val startedAtMillis: Long,
        var firstTokenAtMillis: Long? = null,
        var assistantCompletedAtMillis: Long? = null,
    )

    private val steps = mutableMapOf<Int, StepBoundary>()
    private val usageByStep = mutableMapOf<Int, AgentTokenUsage>()
    private val toolStartedAt = mutableMapOf<String, Long>()
    private var metrics = ChatGenerationMetrics()
    private var contextWindowUsage: ChatContextWindowUsage? = null
    private var projectedContextWindow: Long? = null

    fun accept(event: AgentSessionEvent): Boolean = when (event) {
        is AgentSessionEvent.StepStarted -> {
            steps[event.step] = StepBoundary(event.startedAtMillis)
            true
        }

        is AgentSessionEvent.AssistantDelta -> {
            val boundary = steps[event.step]
            if (
                boundary != null &&
                boundary.firstTokenAtMillis == null &&
                event.tokenObserved &&
                event.observedAtMillis > 0L
            ) {
                boundary.firstTokenAtMillis = event.observedAtMillis
                true
            } else {
                false
            }
        }

        is AgentSessionEvent.ReasoningTextDelta -> {
            val boundary = event.step?.let(steps::get)
            if (
                boundary != null &&
                boundary.firstTokenAtMillis == null &&
                event.delta.isNotBlank() &&
                event.observedAtMillis > 0L
            ) {
                boundary.firstTokenAtMillis = event.observedAtMillis
                true
            } else {
                false
            }
        }

        is AgentSessionEvent.WorkItemCompleted -> when {
            event.type == AgentWorkItemType.AssistantMessage && event.step != null -> {
                steps[event.step]?.assistantCompletedAtMillis = event.completedAtMillis
                true
            }

            else -> toolStartedAt.remove(event.itemId)?.let { startedAtMillis ->
                metrics = metrics.copy(
                    toolDurationMillis = metrics.toolDurationMillis +
                        (event.completedAtMillis - startedAtMillis).coerceAtLeast(0L),
                )
                true
            } ?: false
        }

        is AgentSessionEvent.WorkItemStarted -> {
            if (event.type != AgentWorkItemType.Tool || event.startedAtMillis <= 0L) {
                false
            } else {
                toolStartedAt[event.itemId] = event.startedAtMillis
                false
            }
        }

        is AgentSessionEvent.TokenUsageUpdated -> {
            val previous = usageByStep.put(event.step, event.last)
            metrics = metrics.copy(
                inputTokens = metrics.inputTokens - (previous?.inputTokens ?: 0L) +
                    event.last.inputTokens,
                cacheReadTokens = metrics.cacheReadTokens - (previous?.cacheReadTokens ?: 0L) +
                    event.last.cacheReadTokens,
                cacheWriteTokens = metrics.cacheWriteTokens - (previous?.cacheWriteTokens ?: 0L) +
                    event.last.cacheWriteTokens,
                cacheUsageReported = metrics.cacheUsageReported || event.last.cacheUsageReported,
                outputTokens = metrics.outputTokens - (previous?.outputTokens ?: 0L) +
                    event.last.outputTokens,
            )
            val promptPressure = event.last.inputTokens +
                event.last.cacheReadTokens +
                event.last.cacheWriteTokens
            contextWindowUsage = ChatContextWindowUsage(
                latestTokens = promptPressure.coerceAtLeast(0L),
                totalTokens = event.total.totalTokens.coerceAtLeast(0L),
                modelContextWindow = event.modelContextWindow
                    ?: projectedContextWindow
                    ?: contextWindowUsage?.modelContextWindow,
                systemTokens = contextWindowUsage?.systemTokens,
                toolsTokens = contextWindowUsage?.toolsTokens,
                messageTokens = contextWindowUsage?.messageTokens,
            )
            true
        }

        is AgentSessionEvent.ContextWindowUpdated -> {
            val activeTokens = event.projectedTokens ?: event.pressureTokens
            projectedContextWindow = event.modelContextWindow ?: projectedContextWindow
            val existing = contextWindowUsage
            if (activeTokens == null) {
                false
            } else {
                contextWindowUsage = ChatContextWindowUsage(
                    latestTokens = activeTokens,
                    totalTokens = existing?.totalTokens ?: 0L,
                    modelContextWindow = projectedContextWindow
                        ?: existing?.modelContextWindow,
                    systemTokens = event.systemTokens ?: existing?.systemTokens,
                    toolsTokens = event.toolsTokens ?: existing?.toolsTokens,
                    messageTokens = event.messageTokens ?: existing?.messageTokens,
                )
                true
            }
        }

        is AgentSessionEvent.StepCompleted -> finishStep(event.step)
        else -> false
    }

    fun snapshot(): ChatGenerationMetrics = metrics

    fun contextWindowUsage(): ChatContextWindowUsage? = contextWindowUsage

    private fun finishStep(step: Int): Boolean {
        val boundary = steps.remove(step)
        val usage = usageByStep.remove(step)
        val assistantCompletedAtMillis = boundary?.assistantCompletedAtMillis
        val firstTokenAtMillis = boundary?.firstTokenAtMillis
        val llmDuration = if (boundary != null && assistantCompletedAtMillis != null) {
            (assistantCompletedAtMillis - boundary.startedAtMillis).coerceAtLeast(0L)
        } else {
            0L
        }
        val firstTokenDelay = if (boundary != null && firstTokenAtMillis != null) {
            (firstTokenAtMillis - boundary.startedAtMillis).coerceAtLeast(0L)
        } else {
            null
        }
        val decodeDuration = if (assistantCompletedAtMillis != null && firstTokenAtMillis != null) {
            (assistantCompletedAtMillis - firstTokenAtMillis).coerceAtLeast(0L)
        } else {
            null
        }
        metrics = metrics.copy(
            turns = if (metrics.turns == 0) 1 else metrics.turns,
            steps = metrics.steps + 1,
            llmDurationMillis = metrics.llmDurationMillis + llmDuration,
            firstTokenDelayMillis = metrics.firstTokenDelayMillis + (firstTokenDelay ?: 0L),
            firstTokenSamples = metrics.firstTokenSamples + if (firstTokenDelay != null) 1 else 0,
            decodeDurationMillis = metrics.decodeDurationMillis + (decodeDuration ?: 0L),
            decodeOutputTokens = metrics.decodeOutputTokens + if (decodeDuration != null) {
                usage?.outputTokens ?: 0L
            } else {
                0L
            },
        )
        return true
    }
}
