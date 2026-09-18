package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.api.AgentSessionOptions
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekHarnessJsonRpcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class DeepSeekContextPressure(
    val sessionId: String,
    val pressureTokens: Long?,
    val projectedTokens: Long?,
    val contextWindow: Long?,
    val systemTokens: Long?,
    val toolsTokens: Long?,
    val messageTokens: Long?,
)

class PreparedDeepSeekBackend internal constructor(
    val model: String,
    val provider: String = "deepseek-official",
    val subagentModel: String = model,
    val maxTokens: Int?,
    val sessionCwd: String = "/workspace",
    internal val client: DeepSeekHarnessJsonRpcClient,
    val clientAlreadyStarted: Boolean = false,
    val turnFailures: Flow<String> = emptyFlow(),
    internal val contextPressures: Flow<DeepSeekContextPressure> = emptyFlow(),
    private val release: suspend () -> Unit,
    private val abortHost: suspend () -> Unit = {},
    private val discardSessionFiles: suspend (Set<String>) -> Unit = {},
    private val bindSessionRoute: (String) -> Unit,
    private val beginTurnWindow: (String, List<AgentHistoryItem>, List<AgentContextInjection>) -> String,
    private val bindTurnWindow: (String, String) -> Unit,
    private val endTurnWindow: () -> Unit,
) {
    private val closeMutex = Mutex()
    private var closed = false

    fun bindSession(sessionId: String) = bindSessionRoute(sessionId)
    fun beginTurn(
        userMessage: String,
        history: List<AgentHistoryItem>,
        contextInjections: List<AgentContextInjection>,
    ): String = beginTurnWindow(userMessage, history, contextInjections)
    fun bindTurn(captureId: String, turnId: String) = bindTurnWindow(captureId, turnId)
    fun endTurn() = endTurnWindow()

    suspend fun discardSessions(sessionIds: Set<String>) = discardSessionFiles(sessionIds)

    suspend fun abort() = abortHost()

    suspend fun close() = closeMutex.withLock {
        if (closed) return@withLock
        closed = true
        withContext(NonCancellable) {
            val releaseFailure = runCatching { release() }.exceptionOrNull()
            releaseFailure?.let { throw it }
        }
    }
}

fun interface DeepSeekSessionBackendFactory {
    suspend fun prepare(options: AgentSessionOptions, scope: CoroutineScope): PreparedDeepSeekBackend
}
