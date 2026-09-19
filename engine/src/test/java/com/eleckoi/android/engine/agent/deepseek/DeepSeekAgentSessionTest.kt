package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.api.AgentHarnessId
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.api.AgentPrompt
import com.eleckoi.android.engine.agent.api.AgentSessionOptions
import com.eleckoi.android.engine.agent.api.AgentSessionEvent
import com.eleckoi.android.engine.agent.api.AgentSessionState
import com.eleckoi.android.engine.agent.api.AgentThreadStart
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.AgentWorkStatus
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekHarnessJsonRpcClient
import com.eleckoi.android.engine.agent.deepseek.protocol.DeepSeekHarnessTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekAgentSessionTest {
    @Test
    fun `subagent finish emits its final result and terminal boundary`() = runBlocking {
        val session = DeepSeekAgentSessionFactory(
            DeepSeekSessionBackendFactory { _, scope ->
                PreparedDeepSeekBackend(
                    model = "deepseek-test",
                    maxTokens = null,
                    client = DeepSeekHarnessJsonRpcClient(SubagentFinishingTransport(), scope),
                    release = {},
                    bindSessionRoute = {},
                    beginTurnWindow = { _, _, _ -> "" },
                    bindTurnWindow = { _, _ -> },
                    endTurnWindow = {},
                    subagentModel = "deepseek-child",
                )
            },
        ).create(
            AgentSessionOptions(
                workspaceId = "workspace-subagent-finish",
                conversationId = "conversation-subagent-finish",
            ),
        )

        session.start()
        val observedEvents = mutableListOf<AgentSessionEvent>()
        val childFinished = CompletableDeferred<Unit>()
        val subscription = launch(start = CoroutineStart.UNDISPATCHED) {
            session.events
                .filterIsInstance<AgentSessionEvent.DelegatedSessionEvent>()
                .collect { delegated ->
                    observedEvents += delegated.event
                    if (delegated.event is AgentSessionEvent.TurnCompleted) {
                        childFinished.complete(Unit)
                    }
                }
            }
        session.send(AgentPrompt("委派任务"))
        withTimeout(5_000L) { childFinished.await() }
        subscription.cancelAndJoin()

        val finalResult = observedEvents.filterIsInstance<AgentSessionEvent.WorkItemCompleted>().single {
            it.type == AgentWorkItemType.AssistantMessage
        }
        assertEquals("子 Agent 最终返回", finalResult.summary)
        assertEquals(AgentWorkStatus.Completed, finalResult.status)
        val terminal = observedEvents.last() as AgentSessionEvent.TurnCompleted
        assertEquals(AgentWorkStatus.Completed, terminal.status)
        session.shutdown()
    }

    @Test
    fun `turn history overrides stale session snapshot for long conversations`() = runBlocking {
        val initialHistory = listOf(AgentHistoryItem("{\"id\":\"old\"}"))
        val refreshedHistory = (1..120).map { index ->
            AgentHistoryItem("{\"id\":\"history-$index\"}")
        }
        var projectedHistory = emptyList<AgentHistoryItem>()
        val session = DeepSeekAgentSessionFactory(
            DeepSeekSessionBackendFactory { _, scope ->
                PreparedDeepSeekBackend(
                    model = "deepseek-test",
                    maxTokens = null,
                    client = DeepSeekHarnessJsonRpcClient(TurnStartingTransport(), scope),
                    release = {},
                    bindSessionRoute = {},
                    beginTurnWindow = { _, history, _ ->
                        projectedHistory = history
                        "capture-1"
                    },
                    bindTurnWindow = { _, _ -> },
                    endTurnWindow = {},
                )
            },
        ).create(
            AgentSessionOptions(
                workspaceId = "workspace-history",
                conversationId = "conversation-history",
                initialHistoryItems = initialHistory,
            ),
        )

        session.start()
        session.send(
            prompt = AgentPrompt("继续"),
            authoritativeHistoryItems = refreshedHistory,
        )

        assertEquals(refreshedHistory, projectedHistory)
        session.shutdown()
    }

    @Test
    fun `session identity is stable per workspace and conversation and isolated across chats`() = runBlocking {
        val backend = DeepSeekAgentSessionFactory(
            DeepSeekSessionBackendFactory { _, scope ->
                PreparedDeepSeekBackend(
                    model = "route",
                    maxTokens = null,
                    client = DeepSeekHarnessJsonRpcClient(RespondingTransport(), scope),
                    release = {},
                    bindSessionRoute = {},
                    beginTurnWindow = { _, _, _ -> "" },
                    bindTurnWindow = { _, _ -> },
                    endTurnWindow = {},
                )
            },
        )

        suspend fun id(workspace: String, conversation: String): String {
            val session = backend.create(
                AgentSessionOptions(
                    harness = AgentHarnessId.DeepSeek,
                    workspaceId = workspace,
                    conversationId = conversation,
                ),
            )
            session.start()
            return (session.state.value as AgentSessionState.Ready).threadId.also {
                session.shutdown()
            }
        }

        assertEquals(id("workspace-a", "chat-a"), id("workspace-a", "chat-a"))
        assertNotEquals(id("workspace-a", "chat-a"), id("workspace-a", "chat-b"))
        assertNotEquals(id("workspace-a", "chat-a"), id("workspace-b", "chat-a"))
    }

    @Test
    fun `binds DeepSeek session route before starting Harness transport`() = runBlocking {
        var boundSessionId: String? = null
        val transport = BindingAwareTransport { boundSessionId }
        val backendFactory = DeepSeekSessionBackendFactory { _, scope ->
            PreparedDeepSeekBackend(
                model = "deepseek-test",
                maxTokens = 4_096,
                client = DeepSeekHarnessJsonRpcClient(transport, scope),
                release = {},
                bindSessionRoute = { sessionId -> boundSessionId = sessionId },
                beginTurnWindow = { _, _, _ -> "" },
                bindTurnWindow = { _, _ -> },
                endTurnWindow = {},
            )
        }
        val session = DeepSeekAgentSessionFactory(backendFactory).create(
            AgentSessionOptions(
                harness = AgentHarnessId.DeepSeek,
                workspaceId = "workspace-route-binding",
                conversationId = "conversation-route-binding",
            ),
        )

        session.start()

        val ready = session.state.value as AgentSessionState.Ready
        assertEquals(ready.threadId, boundSessionId)
        assertTrue(transport.routeWasBoundBeforeStart)
        session.shutdown()
    }

    @Test
    fun `deletes obsolete native branches before binding a fresh replacement`() = runBlocking {
        val lifecycle = mutableListOf<String>()
        val transport = RespondingTransport()
        val backendFactory = DeepSeekSessionBackendFactory { _, scope ->
            PreparedDeepSeekBackend(
                model = "deepseek-test",
                maxTokens = null,
                client = DeepSeekHarnessJsonRpcClient(transport, scope),
                release = {},
                discardSessionFiles = { sessionIds ->
                    lifecycle += "discard:${sessionIds.sorted().joinToString()}"
                },
                bindSessionRoute = { lifecycle += "bind:$it" },
                beginTurnWindow = { _, _, _ -> "" },
                bindTurnWindow = { _, _ -> },
                endTurnWindow = {},
            )
        }
        val session = DeepSeekAgentSessionFactory(backendFactory).create(
            AgentSessionOptions(
                workspaceId = "workspace-replace",
                conversationId = "conversation-replace",
                threadStart = AgentThreadStart.Fresh,
                discardThreadIds = setOf("eleckoi-obsolete"),
            ),
        )

        session.start()

        assertEquals("discard:eleckoi-obsolete", lifecycle.first())
        assertTrue(lifecycle[1].startsWith("bind:eleckoi-"))
        session.shutdown()
    }

    private class BindingAwareTransport(
        private val boundSessionId: () -> String?,
    ) : DeepSeekHarnessTransport {
        private val lines = Channel<String>(Channel.BUFFERED)
        override val incomingLines: Flow<String> = lines.receiveAsFlow()
        var routeWasBoundBeforeStart = false
            private set

        override suspend fun start() {
            routeWasBoundBeforeStart = !boundSessionId().isNullOrBlank()
            check(routeWasBoundBeforeStart) { "DeepSeek route was not bound before transport start" }
        }

        override suspend fun sendLine(line: String) {
            val request = ProtocolJson.parseToJsonElement(line).jsonObject
            val id = request.getValue("id").jsonPrimitive.content
            when (request.getValue("method").jsonPrimitive.content) {
                "initialize" -> lines.send(
                    """{"jsonrpc":"2.0","id":$id,"result":{"serverInfo":{"name":"deepseek-harness-sdk-runtime","version":"test"}}}""",
                )
                "session/set_permission" -> {
                    val preset = request.getValue("params").jsonObject.getValue("preset").jsonPrimitive.content
                    lines.send("""{"jsonrpc":"2.0","id":$id,"result":{"preset":"$preset"}}""")
                }
                "shutdown" -> lines.send("""{"jsonrpc":"2.0","id":$id,"result":{}}""")
            }
        }

        override suspend fun stop() {
            lines.close()
        }
    }

    private class RespondingTransport : DeepSeekHarnessTransport {
        private val lines = Channel<String>(Channel.BUFFERED)
        override val incomingLines: Flow<String> = lines.receiveAsFlow()
        override suspend fun start() = Unit
        override suspend fun sendLine(line: String) {
            val request = ProtocolJson.parseToJsonElement(line).jsonObject
            val id = request.getValue("id").jsonPrimitive.content
            when (request.getValue("method").jsonPrimitive.content) {
                "initialize" -> lines.send(
                    """{"jsonrpc":"2.0","id":$id,"result":{"serverInfo":{"name":"deepseek-harness-sdk-runtime","version":"test"}}}""",
                )
                "session/set_permission" -> {
                    val preset = request.getValue("params").jsonObject.getValue("preset").jsonPrimitive.content
                    lines.send("""{"jsonrpc":"2.0","id":$id,"result":{"preset":"$preset"}}""")
                }
                "shutdown" -> lines.send("""{"jsonrpc":"2.0","id":$id,"result":{}}""")
            }
        }
        override suspend fun stop() {
            lines.close()
        }
    }

    private class TurnStartingTransport : DeepSeekHarnessTransport {
        private val lines = Channel<String>(Channel.UNLIMITED)
        override val incomingLines: Flow<String> = lines.receiveAsFlow()

        override suspend fun start() = Unit

        override suspend fun sendLine(line: String) {
            val request = ProtocolJson.parseToJsonElement(line).jsonObject
            val id = request.getValue("id")
            val method = request.getValue("method").jsonPrimitive.content
            val params = request["params"]?.jsonObject
            val result = when (method) {
                "initialize" -> buildJsonObject {
                    put("serverInfo", buildJsonObject {
                        put("name", "deepseek-harness-sdk-runtime")
                        put("version", "test")
                    })
                }
                "session/set_permission" -> buildJsonObject { put("preset", "ask-for-approval") }
                "session/prompt" -> buildJsonObject { put("messageId", "message-1") }
                "shutdown" -> buildJsonObject { }
                else -> error("Unexpected method: $method")
            }
            lines.send(buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", result)
            }.toString())
            if (method == "session/prompt") {
                val sessionId = requireNotNull(params?.get("sessionId")?.jsonPrimitive?.content)
                lines.send(buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("method", "session.event")
                    put("params", buildJsonObject {
                        put("sessionId", sessionId)
                        put("event", buildJsonObject {
                            put("type", "turn/start")
                            put("time", 1L)
                            put("data", buildJsonObject { put("turn", 1) })
                        })
                    })
                }.toString())
            }
        }

        override suspend fun stop() {
            lines.close()
        }
    }

    private companion object {
        val ProtocolJson = Json { ignoreUnknownKeys = false }
    }

    private class SubagentFinishingTransport : DeepSeekHarnessTransport {
        private val lines = Channel<String>(Channel.UNLIMITED)
        override val incomingLines: Flow<String> = lines.receiveAsFlow()

        override suspend fun start() = Unit

        override suspend fun sendLine(line: String) {
            val request = ProtocolJson.parseToJsonElement(line).jsonObject
            val id = request.getValue("id")
            val method = request.getValue("method").jsonPrimitive.content
            val params = request["params"]?.jsonObject
            val result = when (method) {
                "initialize" -> buildJsonObject {
                    put("serverInfo", buildJsonObject {
                        put("name", "deepseek-harness-sdk-runtime")
                        put("version", "test")
                    })
                }
                "session/set_permission" -> buildJsonObject { put("preset", "ask-for-approval") }
                "session/prompt" -> buildJsonObject { put("messageId", "message-1") }
                "shutdown" -> buildJsonObject { }
                else -> error("Unexpected method: $method")
            }
            lines.send(buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", result)
            }.toString())
            if (method != "session/prompt") return

            val parentSessionId = requireNotNull(params?.get("sessionId")?.jsonPrimitive?.content)
            val childSessionId = "child-session"
            sendSessionEvent(
                parentSessionId,
                """{"type":"turn/start","seq":0,"time":100,"data":{"turn":1}}""",
            )
            sendSessionEvent(
                parentSessionId,
                """{"type":"tool/call","seq":1,"time":101,"data":{"turn":1,"step":1,"callId":"delegate-call","name":"subagent","arguments":"{\"prompt\":\"write\"}"}}""",
            )
            sendNotification(
                method = "subagent.started",
                params = """{"parentSessionId":"$parentSessionId","childSessionId":"$childSessionId"}""",
            )
            sendSessionEvent(
                childSessionId,
                """{"type":"turn/start","seq":0,"time":102,"data":{"turn":1}}""",
            )
            sendSessionEvent(
                childSessionId,
                """{"type":"assistant/chunk","seq":1,"time":103,"data":{"turn":1,"step":1,"chunk":{"type":"reasoning-delta","index":0,"text":"正在处理"}}}""",
            )
            sendSessionEvent(
                childSessionId,
                """{"type":"tool/call","seq":2,"time":104,"data":{"turn":1,"step":1,"callId":"send-call","name":"send_message","arguments":"{\"agent_id\":\"$parentSessionId\",\"message\":\"子 Agent 最终返回\"}"}}""",
            )
            sendNotification(
                method = "subagent.finished",
                params = """{"provider":"spawn","agentId":"$childSessionId","parentSessionId":"$parentSessionId","childSessionId":"$childSessionId","status":"ok","stopReason":"completed","lastAssistantMessage":[{"type":"text","text":"子 Agent 最终返回"}]}""",
            )
        }

        private suspend fun sendSessionEvent(sessionId: String, event: String) {
            sendNotification(
                method = "session.event",
                params = """{"sessionId":"$sessionId","event":$event}""",
            )
        }

        private suspend fun sendNotification(method: String, params: String) {
            lines.send("""{"jsonrpc":"2.0","method":"$method","params":$params}""")
        }

        override suspend fun stop() {
            lines.close()
        }
    }
}
