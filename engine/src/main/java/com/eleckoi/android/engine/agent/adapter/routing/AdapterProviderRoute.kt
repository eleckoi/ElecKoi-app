package com.eleckoi.android.engine.agent.adapter

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.adapter.request.ResponsesCompactionRequestProjector
import com.eleckoi.android.engine.agent.adapter.request.AgentTurnRequestContext
import com.eleckoi.android.engine.agent.adapter.request.DshCompactionRequestProjector
import com.eleckoi.android.engine.agent.adapter.request.DshRequestContextProjector
import com.eleckoi.android.engine.agent.diagnostics.AgentRequestDiagnostics
import com.eleckoi.android.engine.generation.model.ModelConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class AdapterProviderRoute(
    val ownerToken: String,
    val modelConfig: ModelConfig,
    /** Optional provider configuration used only by spawned descendant sessions. */
    val subagentModelConfig: ModelConfig? = null,
    /** Provider-system instructions owned by this native Harness session route. */
    val systemInstructions: String = "",
    /** Preset-owned directive used only for DSH's auxiliary compaction request. */
    private val historyCompactionInstructions: String? = null,
    /** Immutable preset selection applied to requests on this session route. */
    val enabledToolGroupIds: Set<String> = emptySet(),
    /** Android-owned tools available only to this native Harness session. */
    val dynamicTools: List<AgentDynamicTool> = emptyList(),
    private val requestCaptureWorkspaceId: String = "",
    private val requestCaptureConversationId: String = "",
    private val captureProviderRequests: Boolean,
    private val onTurnFailure: (String) -> Unit = {},
    private val onContextPressure: (AdapterContextPressure) -> Unit = {},
) {
    private val requestGateOpen = AtomicBoolean(false)
    private val remainingTurnRequests = AtomicInteger(0)
    val turnRequestSequence = AtomicInteger(0)
    private val totalSessionRequests = AtomicInteger(0)
    val activeRequestCaptureId = AtomicReference<String?>(null)
    private val activeTurnContext = AtomicReference<AgentTurnRequestContext?>(null)
    private val latestContextPressureSequence = AtomicLong(-1L)

    fun beginTurn(
        userMessage: String,
        turnContext: AgentTurnRequestContext? = null,
    ): String {
        requestGateOpen.set(true)
        remainingTurnRequests.set(MaxRequestsPerTurn)
        turnRequestSequence.set(0)
        activeTurnContext.set(turnContext)
        if (!captureProviderRequests && !AgentRequestDiagnostics.captureEnabled.value) {
            activeRequestCaptureId.set(null)
            return ""
        }
        return AgentRequestDiagnostics.beginTurn(
            workspaceId = requestCaptureWorkspaceId,
            conversationId = requestCaptureConversationId,
            userMessage = userMessage,
        ).also {
            activeRequestCaptureId.set(it)
        }
    }

    fun bindActiveTurn(captureId: String, runtimeTurnId: String) {
        if (activeRequestCaptureId.get() == captureId) {
            AgentRequestDiagnostics.bindRuntimeTurn(captureId, runtimeTurnId)
        }
    }

    fun endTurn() {
        requestGateOpen.set(false)
        remainingTurnRequests.set(0)
        activeTurnContext.set(null)
        activeRequestCaptureId.getAndSet(null)?.let(AgentRequestDiagnostics::endTurn)
    }

    fun failTurn(message: String) {
        if (!requestGateOpen.getAndSet(false)) return
        remainingTurnRequests.set(0)
        onTurnFailure(message)
    }

    fun consumeRequestBudget(): Boolean {
        if (!requestGateOpen.get()) return false
        while (true) {
            val remaining = remainingTurnRequests.get()
            if (remaining <= 0) return false
            if (remainingTurnRequests.compareAndSet(remaining, remaining - 1)) break
        }
        val total = totalSessionRequests.incrementAndGet()
        if (total > MaxRequestsPerSession) {
            endTurn()
            return false
        }
        return true
    }

    /** The isolated settings probe has no product history/insertion projection. */
    fun projectLegacyProbeRequest(
        request: kotlinx.serialization.json.JsonObject,
        isCompactionRequest: Boolean,
    ) = if (isCompactionRequest) {
        ResponsesCompactionRequestProjector.project(request, historyCompactionInstructions)
    } else {
        request
    }

    fun projectDshRequest(
        request: kotlinx.serialization.json.JsonObject,
        requestIndex: Int,
        isCompactionRequest: Boolean,
    ) = if (isCompactionRequest) {
        DshCompactionRequestProjector.project(request, historyCompactionInstructions)
    } else {
        activeTurnContext.get()?.let { context ->
            DshRequestContextProjector.project(request, context, requestIndex)
        } ?: request
    }

    fun publishContextPressure(sample: AdapterContextPressure) {
        while (true) {
            val previous = latestContextPressureSequence.get()
            if (sample.sequence <= previous) return
            if (latestContextPressureSequence.compareAndSet(previous, sample.sequence)) break
        }
        onContextPressure(sample)
    }

    private companion object {
        const val MaxRequestsPerTurn = 32
        const val MaxRequestsPerSession = 256
    }
}
