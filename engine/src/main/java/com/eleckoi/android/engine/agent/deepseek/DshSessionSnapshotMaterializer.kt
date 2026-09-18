package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.agent.adapter.request.AgentHistoryProjection
import com.eleckoi.android.engine.agent.adapter.request.AgentTurnRequestContext
import com.eleckoi.android.engine.agent.adapter.request.ProductHistoryToDshMessages
import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.configuredMaxOutputTokens
import com.eleckoi.android.engine.generation.model.configuredTemperature
import com.eleckoi.android.engine.generation.model.configuredTopP
import com.eleckoi.android.engine.generation.reasoning.DshModelCapabilities
import com.eleckoi.android.engine.generation.reasoning.DshPiAiProviderCatalog
import com.eleckoi.android.engine.generation.reasoning.DshReasoningEfforts
import com.eleckoi.android.engine.generation.reasoning.usesDshDeepSeekOfficialRoute
import com.eleckoi.android.engine.workspace.runtime.RuntimePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Materializes the immutable/session-scoped input consumed by the DSH Agent-plane extension. */
internal class DshSessionSnapshotMaterializer private constructor(
    private val snapshotRoot: () -> File,
) {
    constructor(runtimePaths: RuntimePaths) : this({
        File(
            runtimePaths.workspaceDeepSeekHome(runtimePaths.persistentDeepSeekWorkspaceId),
            SnapshotDirectory,
        )
    })

    internal constructor(deepSeekHome: File) : this({ File(deepSeekHome, SnapshotDirectory) })

    private val root: File
        get() = snapshotRoot()

    fun write(
        sessionId: String,
        turnToken: String,
        mountedPresetId: String,
        model: ModelConfig,
        modelProvider: String,
        subagentModel: ModelConfig,
        subagentProvider: String,
        turnContext: AgentTurnRequestContext,
    ) {
        require(PresetId.matches(mountedPresetId)) { "DSH Agent Preset 编号无效" }
        val target = snapshotFile(sessionId)
        val content = buildJsonObject {
            put("schemaVersion", 3)
            put("sessionId", sessionId)
            put("turnToken", turnToken)
            put("mountedPresetId", mountedPresetId)
            put("model", modelSelection(model, modelProvider))
            put("subagentModel", modelSelection(subagentModel, subagentProvider))
            put("historyProjection", turnContext.historyProjection.name)
            put("userMessage", turnContext.userMessage)
            put("history", JsonArray(ProductHistoryToDshMessages.convert(turnContext.parsedProductHistory)))
            put("injections", buildJsonArray {
                turnContext.injections.forEach { injection -> add(injectionJson(injection)) }
            })
        }.toString().toByteArray(Charsets.UTF_8)
        require(content.size <= MaxSnapshotBytes) { "DSH 会话快照过大" }
        writeAtomically(target, content)
    }

    fun emptyContext(
        history: List<AgentHistoryItem>,
        projection: AgentHistoryProjection,
    ) = AgentTurnRequestContext(
        userMessage = "",
        history = history,
        injections = emptyList(),
        historyProjection = projection,
    )

    private fun modelSelection(config: ModelConfig, provider: String): JsonObject = buildJsonObject {
        val official = config.usesDshDeepSeekOfficialRoute()
        require(provider.isNotBlank()) { "DSH Provider 路由不能为空" }
        put("provider", provider)
        put("model", if (official) config.model.trim() else DshPiAiProviderCatalog.runtimeModelId(config))
        DshReasoningEfforts.selected(config)?.let { put("reasoningEffort", it) }
        config.configuredTemperature()?.let { put("temperature", it) }
        config.configuredTopP()?.let { put("topP", it) }
        config.configuredMaxOutputTokens()?.let { put("maxTokens", it) }
    }

    private fun injectionJson(injection: AgentContextInjection): JsonObject = buildJsonObject {
        put("id", injection.id)
        put("anchor", injection.anchor.wireValue)
        put("role", injection.role.wireValue)
        put("content", injection.content)
        put("order", injection.order)
        put("traceTitle", injection.traceTitle)
        put("traceSource", injection.traceSource)
        put("activation", buildJsonObject {
            when (val activation = injection.activation) {
                AgentContextActivation.FirstModelRequest -> put("kind", "first")
                AgentContextActivation.Immediate -> put("kind", "immediate")
                is AgentContextActivation.AfterToolCall -> {
                    put("kind", "afterTool")
                    put("toolName", activation.toolName)
                }
                is AgentContextActivation.AfterToolCallArgumentContains -> {
                    put("kind", "afterToolArgument")
                    put("toolName", activation.toolName)
                    put("argumentName", activation.argumentName)
                    put("value", activation.value)
                }
            }
        })
    }

    private fun snapshotFile(sessionId: String): File {
        require(SessionId.matches(sessionId)) { "DSH session 编号无效" }
        require(root.isDirectory || root.mkdirs()) { "无法创建 DSH 会话快照目录" }
        require(!Files.isSymbolicLink(root.toPath())) { "DSH 会话快照目录不安全" }
        return File(root, "$sessionId.json").canonicalFile.also { target ->
            require(target.parentFile == root.canonicalFile) { "DSH 会话快照路径越界" }
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        if (Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) && target.readBytes().contentEquals(bytes)) return
        require(!Files.isSymbolicLink(target.toPath())) { "DSH 会话快照文件不安全" }
        val temporary = File(requireNotNull(target.parentFile), ".${target.name}.tmp")
        temporary.writeBytes(bytes)
        runCatching {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val SnapshotDirectory = "eleckoi/session-snapshots"
        const val MaxSnapshotBytes = 8 * 1024 * 1024
        val SessionId = Regex("^[A-Za-z0-9._:-]{1,160}$")
        val PresetId = Regex("^[a-z0-9][a-z0-9-]*$")
    }
}
