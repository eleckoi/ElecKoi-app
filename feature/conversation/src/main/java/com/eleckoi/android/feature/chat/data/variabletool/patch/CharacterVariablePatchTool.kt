package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentApplyVariablePatchTool
import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.protocol.VariablePatchProtocol
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeCheckResult
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

internal fun characterVariablePatchTool(
    config: VariableConfig,
    turnState: CharacterVariableTurnState,
    validateState: suspend (schemaCode: String, stateJson: String) -> VariableRuntimeCheckResult,
    settingChangesForPatch: (suspend (String, String) -> SettingLibraryEjsChanges)? = null,
): AgentDynamicTool = AgentDynamicTool(
    definition = AgentToolDefinition(
        name = AgentApplyVariablePatchTool,
        description = "修改剧情运行时状态。状态只保存变量的当前值，不保存变量定义；" +
            "不要把数值变量包装成 {value,min,max,description,update_rule,type,default}，" +
            "这些范围、说明和更新规则属于作者配置或 Zod，而不是变量值；" +
            "例如新建数值变量‘好感度’应使用 insert /好感度 value=0，而不是插入说明对象。" +
            "只有用户明确要求复合数据，或作者 Zod 明确规定对象结构时，value 才使用 object。" +
            "支持 replace、delta、insert、remove 四种明确操作；" +
            "replace 只修改已有路径，insert 可创建任意顶层变量或新字段，并自动补齐缺失的对象父路径；" +
            "数组索引仅用于 insert/remove 等数组修改，不代表数组元素是独立变量；" +
            "delta 只增减已有数字，remove 只删除已有路径；" +
            "无需先读取才能通过协议，但应先读取涉及作者更新规则的变量；" +
            "作者 Zod 是最终结构规则，校验失败会返回具体错误且不改变状态，请按错误修正后重试。",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put(VariableOperationsArgument, buildJsonObject {
                    put("type", "array")
                    put(
                        "description",
                        "按顺序执行，path 使用 JSON Pointer。insert 可使用尚不存在的顶层或多级对象路径。",
                    )
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("op", buildJsonObject {
                                put("type", "string")
                                put("enum", buildJsonArray {
                                    listOf("replace", "delta", "insert", "remove").forEach {
                                        add(JsonPrimitive(it))
                                    }
                                })
                                put(
                                    "description",
                                    "修改已有值用 replace；创建路径或数组项用 insert；数字增减用 delta；删除用 remove。",
                                )
                            })
                            put("path", buildJsonObject {
                                put("type", "string")
                                put("pattern", "^/")
                            })
                            put("value", buildJsonObject {
                                put(
                                    "description",
                                    "replace、delta、insert 必填；remove 不填写。这里填写运行时当前值本身。" +
                                        "数值直接写 0，文本直接写字符串，布尔值直接写 true/false；" +
                                        "不要用 value/min/max/description/update_rule 等字段包装简单值。",
                                )
                            })
                        })
                        put(
                            "required",
                            buildJsonArray {
                                add(JsonPrimitive("op"))
                                add(JsonPrimitive("path"))
                            },
                        )
                        put("additionalProperties", false)
                    })
                    put("minItems", 1)
                    put("maxItems", MaxPatchOperations)
                })
            })
            put("required", buildJsonArray { add(JsonPrimitive(VariableOperationsArgument)) })
            put("additionalProperties", false)
        },
    ),
    handler = { arguments ->
        val operations = arguments[VariableOperationsArgument] as? JsonArray
            ?: return@AgentDynamicTool AgentDynamicToolResult(
                """{"status":"invalid_arguments","message":"operations 必须是非空数组。"}""",
                success = false,
            )
        if (operations.isEmpty()) {
            return@AgentDynamicTool AgentDynamicToolResult(
                """{"status":"invalid_arguments","message":"operations 不能为空。"}""",
                success = false,
            )
        }
        val operationPaths = operations.mapNotNull { operation ->
            (operation as? JsonObject)?.variableString("path")
        }
        if (operationPaths.size != operations.size) {
            return@AgentDynamicTool AgentDynamicToolResult(
                """{"status":"invalid_arguments","message":"每项操作都必须包含 path。"}""",
                success = false,
            )
        }
        val nextState = try {
            VariablePatchProtocol.applyPatch(
                turnState.stateJson,
                operations.toString(),
            )
        } catch (error: ElecKoiDataException) {
            return@AgentDynamicTool AgentDynamicToolResult(
                JSONObject()
                    .put("status", "patch_error")
                    .put("message", error.message ?: "变量补丁无效")
                    .put("paths", JSONArray(operationPaths.distinct()))
                    .put("state_unchanged", true)
                    .toString(),
                success = false,
            )
        }

        var validatedState = nextState
        if (config.schemaCode.isNotBlank()) {
            val validation = validateState(config.schemaCode, nextState)
            if (!validation.ok) {
                val validationDetail = validation.detail
                    .ifBlank { validation.message }
                    .ifBlank { "作者 Zod 拒绝了本次变量状态，但没有提供更多细节。" }
                return@AgentDynamicTool AgentDynamicToolResult(
                    JSONObject()
                        .put("status", "validation_error")
                        .put("message", validation.message.ifBlank { "变量状态不符合 Zod 规则" })
                        .put("detail", validationDetail.take(MaxValidationDetailCharacters))
                        .put("schema_source", "author_zod")
                        .put("paths", JSONArray(operationPaths.distinct()))
                        .put("state_unchanged", true)
                        .toString(2),
                    success = false,
                )
            }
            validatedState = validation.normalizedStateJson.ifBlank { nextState }
            val normalizationConflicts = runCatching {
                variableNormalizationConflicts(
                    patchedStateJson = nextState,
                    normalizedStateJson = validatedState,
                    operations = operations,
                )
            }.getOrElse { error ->
                return@AgentDynamicTool AgentDynamicToolResult(
                    JSONObject()
                        .put("status", "validation_error")
                        .put("message", "作者 Zod 返回了无法读取的归一化状态")
                        .put("detail", error.message.orEmpty().take(MaxValidationDetailCharacters))
                        .put("schema_source", "author_zod")
                        .put("paths", JSONArray(operationPaths.distinct()))
                        .put("state_unchanged", true)
                        .toString(2),
                    success = false,
                )
            }
            if (normalizationConflicts.isNotEmpty()) {
                return@AgentDynamicTool AgentDynamicToolResult(
                    JSONObject()
                        .put("status", "normalization_conflict")
                        .put("message", "作者 Zod 校验通过，但归一化结果移除了或恢复了本次修改路径。")
                        .put(
                            "detail",
                            "这些路径未按补丁结果保留：${normalizationConflicts.joinToString()}。" +
                                "请改用作者 Zod 允许的字段或结构后重试。",
                        )
                        .put("schema_source", "author_zod")
                        .put("paths", JSONArray(normalizationConflicts))
                        .put("state_unchanged", true)
                        .toString(2),
                    success = false,
                )
            }
        }

        val previousState = turnState.stateJson
        turnState.replaceState(validatedState)
        val settingChangesResult = runCatching {
            settingChangesForPatch?.invoke(previousState, turnState.stateJson)
        }
        val settingChanges = settingChangesResult.getOrNull()
        AgentDynamicToolResult(
            JSONObject()
                .put("status", "ok")
                .put("applied_operations", operations.size)
                .put("paths", JSONArray(operationPaths.distinct()))
                .put("message", if (settingChanges?.isEmpty == false) {
                    "变量补丁已暂存。设定条件已变化：newly_available_paths 请重新浏览目录，若属于 required_entries 则必须读取；stale_read_paths 需重新读取；unavailable_read_paths 已失效。其余设定无需重读。"
                } else {
                    "变量补丁已通过校验并暂存，将随本回合成功完成后提交。"
                })
                .apply {
                    if (settingChanges?.isEmpty == false) {
                        put("setting_library_changes", JSONObject()
                            .put("newly_available_paths", JSONArray(settingChanges.newlyAvailablePaths))
                            .put("stale_read_paths", JSONArray(settingChanges.staleReadPaths))
                            .put("unavailable_read_paths", JSONArray(settingChanges.unavailableReadPaths)))
                    }
                    settingChangesResult.exceptionOrNull()?.let { error ->
                        put("setting_library_refresh_error", error.message ?: "EJS 设定重新计算失败")
                    }
                }
                .toString(),
        )
    },
)

private fun variableNormalizationConflicts(
    patchedStateJson: String,
    normalizedStateJson: String,
    operations: JsonArray,
): List<String> {
    val patched = JSONObject(patchedStateJson)
    val normalized = JSONObject(normalizedStateJson)
    val affectedPaths = operations
        .mapNotNull { operation -> (operation as? JsonObject)?.variableString("path") }
        .map { path -> if (path.endsWith("/-")) path.variableJsonPointerParent() else path }
        .distinct()
    return affectedPaths.flatMap { path ->
        if (!patched.containsVariablePointer(path)) {
            if (normalized.containsVariablePointer(path)) listOf(path) else emptyList()
        } else {
            patched.persistedVariableLeafPaths(path).filterNot(normalized::containsVariablePointer)
        }
    }.distinct()
}

private fun JSONObject.persistedVariableLeafPaths(pointer: String): List<String> {
    fun collect(value: Any?, path: String): List<String> = when (value) {
        is JSONObject -> {
            val keys = value.keys().asSequence().toList()
            if (keys.isEmpty()) listOf(path) else keys.flatMap { key ->
                collect(value.opt(key), "$path/${key.toVariableJsonPointerToken()}")
            }
        }
        is JSONArray -> {
            if (value.length() == 0) listOf(path) else buildList {
                repeat(value.length()) { index -> addAll(collect(value.opt(index), "$path/$index")) }
            }
        }
        else -> listOf(path)
    }
    return collect(variableValueAtPointerOrNull(pointer), pointer)
}

private const val VariableOperationsArgument = "operations"
private const val MaxPatchOperations = 200
private const val MaxValidationDetailCharacters = 8_000
