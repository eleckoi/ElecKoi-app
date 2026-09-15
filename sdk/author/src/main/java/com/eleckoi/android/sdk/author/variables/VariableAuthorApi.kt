package com.eleckoi.android.sdk.author.variables

import com.eleckoi.android.sdk.author.AuthorApiCatalog
import com.eleckoi.android.sdk.author.AuthorApiRoute
import com.eleckoi.android.sdk.author.AuthorApiCallException
import com.eleckoi.android.sdk.author.AuthorApiErrorCode
import com.eleckoi.android.sdk.author.requireChatGateway
import com.eleckoi.android.sdk.author.toAuthorJson
import com.eleckoi.android.engine.story.variables.protocol.VariablePatchProtocol
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.sdk.author.AuthorMessageSnapshot
import com.eleckoi.android.sdk.author.AuthorChatSessionSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object VariableAuthorApi {
    val routes = listOf(
        AuthorApiRoute(AuthorApiCatalog.require("variables.getState")) { environment, params ->
            val runtime = environment.runtime
            val chatSession = runtime.chatGateway?.snapshot()?.draft?.session
                ?: runtime.chatSession
            val requestedMessageId = (params["messageId"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content
                .orEmpty()
            val selection = resolveVariableState(
                scopedMessage = runtime.currentMessage.takeIf { runtime.chatGateway == null },
                requestedMessageId = requestedMessageId,
                chatSession = chatSession,
                runtimeStateJson = runtime.variableStateJson,
                initialStateJson = runtime.variableConfig?.initialStateJson,
            )
            selection.rawState.toStateObject()
        },
        AuthorApiRoute(AuthorApiCatalog.require("variables.getConfig")) { environment, _ ->
            val config = environment.runtime.variableConfig
            config?.let { currentConfig ->
                buildJsonObject {
                    put("characterId", currentConfig.characterId)
                    put("schemaCode", currentConfig.schemaCode)
                    put("initialStateJson", currentConfig.initialStateJson)
                    put("objects", buildJsonArray {
                        currentConfig.objects.forEach { item ->
                            add(buildJsonObject {
                                put("id", item.id)
                                put("name", item.name)
                                put("parentId", item.parentId)
                                put("enabled", item.enabled)
                                put("description", item.description)
                                put("updateRule", item.updateRule)
                                put("order", item.order)
                                put("treeViewOrder", item.treeViewOrder)
                            })
                        }
                    })
                    put("variables", buildJsonArray {
                        currentConfig.variables.forEach { item ->
                            add(buildJsonObject {
                                put("id", item.id)
                                put("title", item.title)
                                put("objectId", item.objectId)
                                put("enabled", item.enabled)
                                put("type", item.type)
                                put("defaultValue", item.defaultValue)
                                put("description", item.description)
                                put("updateRule", item.updateRule)
                                put("readMode", item.readMode.storageValue)
                                put("order", item.order)
                                put("treeViewOrder", item.treeViewOrder)
                            })
                        }
                    })
                }
            } ?: kotlinx.serialization.json.JsonNull
        },
        AuthorApiRoute(AuthorApiCatalog.require("variables.setState")) { environment, params ->
            val state = params["state"] as? JsonObject ?: throw AuthorApiCallException(
                AuthorApiErrorCode.InvalidParams,
                "variables.setState 需要 JSON object 类型的 state",
            )
            environment.requireChatGateway().replaceVariableState(state.toString()).toAuthorJson()
            state
        },
        AuthorApiRoute(AuthorApiCatalog.require("variables.merge")) { environment, params ->
            val state = params["state"] as? JsonObject ?: throw AuthorApiCallException(
                AuthorApiErrorCode.InvalidParams,
                "variables.merge 需要 JSON object 类型的 state",
            )
            val session = environment.runtime.chatGateway?.snapshot()?.draft?.session
            val currentRaw = session?.variableStateJson?.takeIf { it.isNotBlank() }
                ?: session?.messages
                ?.asReversed()
                ?.firstOrNull { it.variableStateJson.isNotBlank() }
                ?.variableStateJson
                ?: environment.runtime.variableStateJson
                ?: environment.runtime.variableConfig?.initialStateJson
                ?: "{}"
            val current = runCatching { ElecKoiJson.parseToJsonElement(currentRaw) as? JsonObject }
                .getOrNull() ?: buildJsonObject {}
            val merged = mergeJsonObjects(current, state)
            environment.requireChatGateway().replaceVariableState(merged.toString()).toAuthorJson()
            merged
        },
        AuthorApiRoute(AuthorApiCatalog.require("variables.applyPatch")) { environment, params ->
            val patch = params["patch"] as? JsonArray ?: throw AuthorApiCallException(
                AuthorApiErrorCode.InvalidParams,
                "variables.applyPatch 需要 JSON array 类型的 patch",
            )
            val session = environment.runtime.chatGateway?.snapshot()?.draft?.session
            val currentRaw = session?.variableStateJson?.takeIf { it.isNotBlank() }
                ?: session?.messages
                ?.asReversed()
                ?.firstOrNull { it.variableStateJson.isNotBlank() }
                ?.variableStateJson
                ?: environment.runtime.variableStateJson
                ?: environment.runtime.variableConfig?.initialStateJson
                ?: "{}"
            val nextState = try {
                VariablePatchProtocol.applyPatch(currentRaw, patch.toString())
            } catch (error: ElecKoiDataException) {
                throw AuthorApiCallException(
                    AuthorApiErrorCode.InvalidParams,
                    error.message ?: "variables.applyPatch 的操作清单无效",
                )
            }
            environment.requireChatGateway().replaceVariableState(nextState).toAuthorJson()
            nextState.toStateObject()
        },
        AuthorApiRoute(AuthorApiCatalog.require("variables.reset")) { environment, _ ->
            val gateway = environment.requireChatGateway()
            gateway.resetVariableState().toAuthorJson()
            gateway.snapshot().draft?.session?.variableStateJson.toStateObject()
        },
    )
}

private fun String?.toStateObject(): JsonObject {
    if (isNullOrBlank()) return buildJsonObject {}
    return runCatching { ElecKoiJson.parseToJsonElement(this) as? JsonObject }
        .getOrNull()
        ?: throw AuthorApiCallException(
            AuthorApiErrorCode.InvalidParams,
            "变量状态不是有效的 JSON object",
        )
}

internal data class VariableStateSelection(
    val rawState: String?,
    val source: String,
    val messageId: String = "",
    val available: Boolean,
)

/**
 * Resolves a variable state without ever letting a historical message fall through to the
 * session's newest state. A missing historical snapshot is reported as unavailable because its
 * value cannot be inferred safely.
 */
internal fun resolveVariableState(
    scopedMessage: AuthorMessageSnapshot?,
    requestedMessageId: String,
    chatSession: AuthorChatSessionSnapshot?,
    runtimeStateJson: String?,
    initialStateJson: String?,
): VariableStateSelection {
    if (
        scopedMessage != null &&
        requestedMessageId.isNotBlank() &&
        requestedMessageId != scopedMessage.id
    ) {
        throw AuthorApiCallException(
            AuthorApiErrorCode.PermissionDenied,
            "消息内前端只能读取所属消息的变量快照",
        )
    }
    val requestedMessage = when {
        scopedMessage != null -> scopedMessage
        requestedMessageId.isNotBlank() -> chatSession?.messages?.firstOrNull {
            it.id == requestedMessageId
        }
        else -> null
    }
    if (requestedMessageId.isNotBlank() && requestedMessage == null) {
        throw AuthorApiCallException(
            AuthorApiErrorCode.InvalidParams,
            "没有找到消息：$requestedMessageId",
        )
    }

    if (requestedMessage != null) {
        val messageState = requestedMessage.variableStateJson.takeIf(String::isNotBlank)
        return VariableStateSelection(
            rawState = messageState,
            source = if (messageState != null) "message" else "unavailable",
            messageId = requestedMessage.id,
            available = messageState != null,
        )
    }

    val sessionState = chatSession?.variableStateJson?.takeIf(String::isNotBlank)
    val latestMessageState = chatSession?.messages
        ?.asReversed()
        ?.firstOrNull { it.variableStateJson.isNotBlank() }
        ?.variableStateJson
    val runtimeState = runtimeStateJson?.takeIf(String::isNotBlank)
    val initialState = initialStateJson?.takeIf(String::isNotBlank)
    return when {
        sessionState != null -> VariableStateSelection(sessionState, "session", available = true)
        latestMessageState != null -> VariableStateSelection(latestMessageState, "latest_message", available = true)
        runtimeState != null -> VariableStateSelection(runtimeState, "runtime", available = true)
        else -> VariableStateSelection(initialState, "initial", available = initialState != null)
    }
}

private fun mergeJsonObjects(current: JsonObject, patch: JsonObject): JsonObject {
    return JsonObject(
        current.toMutableMap().apply {
            patch.forEach { (key, value) ->
                val existing = this[key]
                this[key] = if (existing is JsonObject && value is JsonObject) {
                    mergeJsonObjects(existing, value)
                } else {
                    value
                }
            }
        },
    )
}
