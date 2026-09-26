package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.agent.api.AgentReadVariablesTool
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

internal fun characterVariableReadTool(
    config: VariableConfig,
    catalogProvider: () -> List<CharacterVariableCatalogEntry>,
    turnState: CharacterVariableTurnState,
): AgentDynamicTool = AgentDynamicTool(
    definition = AgentToolDefinition(
        name = AgentReadVariablesTool,
        description = "读取当前回合变量搜索结果中的完整路径，返回当前值、默认值、说明和完整更新规则。" +
            "每轮先用变量 Glob 获取 required_variables 并读取这些必读变量；上轮读取不能代替本轮状态。" +
            "current_present 区分‘实际存储为 null’和‘尚未写入状态’。" +
            "修改前建议先读以理解作者规则；补丁工具会独立执行路径和 Zod 校验。",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put(VariablePathsArgument, buildJsonObject {
                    put("type", "array")
                    put("description", "当前回合 Glob 或 Grep 已返回的完整变量 JSON Pointer 路径。")
                    put("items", buildJsonObject {
                        put("type", "string")
                        put("pattern", "^/")
                    })
                    put("minItems", 1)
                    put("uniqueItems", true)
                })
            })
            put("required", buildJsonArray { add(JsonPrimitive(VariablePathsArgument)) })
            put("additionalProperties", false)
        },
    ),
    handler = { arguments ->
        val byPath = catalogProvider().associateBy(CharacterVariableCatalogEntry::path)
        val paths = arguments.variableStringArray(VariablePathsArgument)
        when {
            paths.isEmpty() -> AgentDynamicToolResult(
                """{"status":"invalid_arguments","message":"至少选择一个变量路径。"}""",
                success = false,
            )
            paths.any { it !in byPath } -> {
                val missing = paths.filterNot(byPath::containsKey)
                AgentDynamicToolResult(
                    content = JSONObject()
                        .put("status", "not_found")
                        .put("message", "存在当前变量配置中没有的路径，请重新使用 Glob 或 Grep。")
                        .put("paths", JSONArray(missing))
                        .toString(),
                    success = false,
                )
            }
            else -> {
                val currentState = JSONObject(turnState.stateJson)
                val initialState = JSONObject(config.initialStateJson.ifBlank { "{}" })
                val variables = JSONArray()
                paths.distinct().forEach { path ->
                    val entry = requireNotNull(byPath[path])
                    val currentPresent = currentState.containsVariablePointer(path)
                    variables.put(
                        JSONObject()
                            .put("path", path)
                            .put("read_mode", entry.readMode.storageValue)
                            .put("type", entry.type)
                            .put("default", initialState.variableValueAtPointerOrNull(path))
                            .put("current", currentState.variableValueAtPointerOrNull(path))
                            .put("current_present", currentPresent)
                            .put(
                                "write_guidance",
                                when {
                                    entry.allowsDynamicChildren ->
                                        "这是对象容器；用 insert /当前路径/<新键> 创建新键，是否允许由作者 Zod 决定"
                                    currentPresent -> "用 replace 修改当前值"
                                    else -> "该配置变量尚未存储；用 insert 首次创建"
                                },
                            )
                            .put("description", entry.description)
                            .put("update_rule", entry.updateRule),
                    )
                }
                AgentDynamicToolResult(
                    JSONObject()
                        .put("status", "ok")
                        .put("variables", variables)
                        .toString(2),
                )
            }
        }
    },
)

private const val VariablePathsArgument = "paths"
