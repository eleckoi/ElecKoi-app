package com.eleckoi.android.app.workspace

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionPluginContractTest {
    @Test
    fun `session bridge projects one durable definition into exact request positions`() {
        val cordis = source("cordis.yml")
        val manifest = source("manifest.json")
        val bridge = source("agent-session-bridge.mjs")
        val projection = source("request-projection.mjs")
        val requestContext = source("request-context.mjs")

        assertTrue(cordis.contains("name: ./agent-session-bridge.mjs"))
        assertTrue(manifest.contains("\"request-projection.mjs\""))
        assertTrue(manifest.contains("\"request-context.mjs\""))
        assertTrue(cordis.contains("ELECKOI_REQUEST_CONTEXT_ROOT"))
        assertTrue(bridge.contains("agentCtx.on('agent/request'"))
        assertTrue(bridge.contains("'agent/pre-step'"))
        assertTrue(bridge.contains("agentCtx.systemPrompt.section"))
        assertTrue(bridge.contains("text: '{{eleckoi_session_instructions}}'"))
        assertTrue(bridge.contains("eleckoi_session_instructions: requestInstructions(snapshot)"))
        assertFalse(bridge.contains("text: () => requestInstructions(read())"))
        assertFalse(bridge.contains("agentCtx.systemPrompt.context"))
        assertTrue(bridge.contains("projectProductHistory"))
        assertFalse(bridge.contains("ReplacePreviousTurns"))
        assertFalse(bridge.contains("isModelHistoryMessage"))
        assertTrue(bridge.contains("from './request-projection.mjs'"))
        assertTrue(projection.contains("export const projectionPlugin = 'eleckoi-request-projection'"))
        assertTrue(projection.contains("export function requestProjectionPlan(snapshot)"))
        assertTrue(projection.contains("export function projectRequestMessages(messages"))
        assertTrue(projection.contains("export function projectProductHistory(messages, history)"))
        assertTrue(bridge.contains("ensureProjectionEnvelope(agent.session, plan)"))
        assertTrue(bridge.contains("const productMessages = projectProductHistory(options.messages, snapshot.history)"))
        assertTrue(bridge.contains("const messages = projectRequestMessages(productMessages, plan)"))
        assertTrue(
            bridge.indexOf("const productMessages = projectProductHistory") <
                bridge.indexOf("const messages = projectRequestMessages(productMessages"),
        )
        assertTrue(projection.contains("messages.filter(message => !isInternalProjectionMessage(message))"))
        assertTrue(projection.contains("surfaceOp: { op: 'replace', startSeq: current.seq, endSeq: current.seq }"))
        assertTrue(projection.contains("sourceEventSeqs: [current.seq]"))
        assertTrue(projection.contains("...beforeHistory"))
        assertTrue(projection.contains("...beforeLatestUser"))
        assertTrue(projection.contains("dialogue[latestUserIndex]"))
        assertTrue(projection.contains("...afterLatestUser"))
        assertTrue(projection.contains("...dialogue.slice(latestUserIndex + 1)"))
        assertTrue(projection.contains("...afterToolFlow"))
        assertTrue(bridge.contains("export const inject = ['agents', 'attachments', 'agentPresets']"))
        assertTrue(bridge.contains("await rootCtx.agentPresets.mount(agentCtx, snapshot.mountedPresetId)"))
        assertTrue(bridge.contains("agentPreset: snapshot.mountedPresetId"))
        assertFalse(bridge.contains("registerAdapter"))
        assertFalse(bridge.contains("ctx.llm.stream"))
        assertTrue(bridge.contains("agentCtx.on('llm/stream'"))
        assertTrue(bridge.contains("recordRequestContextSnapshot("))
        assertTrue(bridge.contains("options.messages,"))
        assertTrue(requestContext.contains("session.snapshotEvents().findLast"))
        assertTrue(requestContext.contains("type: 'definition'"))
        assertTrue(requestContext.contains("type: 'request'"))
    }

    @Test
    fun `session bridge never posts or wraps the official serialized model request`() {
        val bridge = source("agent-session-bridge.mjs")

        assertFalse(bridge.contains("/provider/authorize"))
        assertFalse(bridge.contains("/provider/prepare"))
        assertFalse(bridge.contains("serializableRequest"))
        assertFalse(bridge.contains("eleckoi_internal_route_"))
        assertFalse(bridge.contains("host-tools/provider"))
    }

    @Test
    fun `bridge contains no model name or endpoint based reasoning guesses`() {
        val cordis = source("cordis.yml")
        val bridge = source("agent-session-bridge.mjs")

        assertFalse(cordis.contains("kimi-k3"))
        assertFalse(cordis.contains("eleckoi-wire-chat-thinking"))
        assertFalse(bridge.contains("includes('deepseek')"))
        assertFalse(bridge.contains("api.deepseek.com"))
    }

    @Test
    fun `session bridge preserves request scoped Top P for DSH serialization`() {
        val bridge = source("agent-session-bridge.mjs")

        assertTrue(bridge.contains("topP: assembled.topP"))
    }

    @Test
    fun `compaction hook reroutes an immutable copy without retaining projection fields`() {
        val bridge = source("agent-session-bridge.mjs")

        assertTrue(bridge.contains("options?.purpose === 'compaction'"))
        assertTrue(bridge.contains("options.messages.filter(message => !isInternalProjectionMessage(message))"))
        assertTrue(bridge.contains("const { tools: _tools, reasoningEffort: _reasoningEffort, ...rest } = options"))
        assertTrue(bridge.contains("return reroute({"))
        assertFalse(bridge.contains("delete options.tools"))
        assertFalse(bridge.contains("delete options.reasoningEffort"))
        assertFalse(bridge.contains("ctx.llm.stream"))
        assertTrue(bridge.contains("return agentCtx.llm.stream(options)"))
    }

    @Test
    fun `conversation seed emits DSH message envelopes without unresolved SEA package imports`() {
        val bridge = source("agent-session-bridge.mjs")

        assertFalse(bridge.contains("from '@deepseek-ai/dsh-llm'"))
        assertTrue(bridge.contains("import { randomUUID } from 'node:crypto'"))
        assertTrue(bridge.contains("source: { kind: 'model'"))
        assertTrue(bridge.contains("source: { kind: 'tool', callId: input.callId }"))
        assertTrue(bridge.contains("id: randomUUID()"))
        assertFalse(bridge.contains("createSystemMessage"))
        assertFalse(bridge.contains("append('system/message'"))
        assertTrue(bridge.contains("append('user/message', createUserMessage({"))
        assertTrue(bridge.contains("message: createAssistantMessage({"))
        assertTrue(bridge.contains("message: createToolResultMessage({"))
        assertTrue(bridge.contains("const call = append('tool/call'"))
        assertTrue(bridge.contains("append('tool/result'"))
        assertTrue(bridge.contains("source: { kind: 'user' }"))
        assertFalse(bridge.contains("message: {\n        ...message,"))
    }

    private fun source(name: String): String {
        val relative = "app/src/main/assets/dsh-plugins/agent-session-bridge/$name"
        val file = sequenceOf(
            File(relative),
            File("../$relative"),
            File("src/main/assets/dsh-plugins/agent-session-bridge/$name"),
        ).firstOrNull(File::isFile)
        checkNotNull(file) { "Missing Agent session bridge asset: $relative" }
        return file.readText()
    }
}
