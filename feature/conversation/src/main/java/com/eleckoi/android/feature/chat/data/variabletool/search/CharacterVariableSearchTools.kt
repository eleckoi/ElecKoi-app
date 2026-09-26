package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.agent.api.AgentGlobVariablesTool
import com.eleckoi.android.engine.agent.api.AgentGrepVariablesTool
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.engine.agent.api.AgentVirtualFile
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.api.AgentVirtualGlobRequest
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepLine
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepRequest
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

internal fun characterVariableGlobTool(
    catalogProvider: () -> List<CharacterVariableCatalogEntry>,
    virtualFileSearch: AgentVirtualFileSearch,
): AgentDynamicTool = AgentDynamicTool(
    definition = AgentToolDefinition(
        name = AgentGlobVariablesTool,
        description = "使用 Glob 路径模式查找虚拟变量路径。" +
            "每轮最终回复前至少调用一次，省略 pattern 和 path，以 ** 一次列出全部变量路径及当前 required_variables。" +
            "不要按变量组逐层进入并重复调用 Glob；结果已有完整路径时直接读取目标变量。" +
            "仅在明确需要筛选某个已知变量组时传 path；它会递归搜索该组及其子组。" +
            "同一轮内变量结构未变化时不要重复相同搜索。" +
            "例如 **、角色/**、**/*好感*。" +
            "数组是一个变量，只返回数组本身的路径，不把 0、1、2 等索引当成变量。" +
            "返回真实 JSON Pointer 路径，不返回完整值与规则。" +
            "required_variables 始终列出当前配置中的必读变量，不受 pattern 或 path 影响；本回合必须逐项读取。",
        parameters = variableSearchParameters(includeOutputMode = false),
    ),
    handler = { arguments ->
        val catalog = catalogProvider()
        val pattern = arguments.variableString(VariablePatternArgument).orEmpty().trim().ifBlank { "**" }
        val scope = validatedVariableScope(catalog, arguments.variableString(VariablePathArgument))
            ?: return@AgentDynamicTool variableInvalidPath()
        val scoped = variableEntriesInScope(catalog, scope)
        val byVirtualPath = scoped.associateBy(ScopedVariableEntry::virtualPath)
        val orderByVirtualPath = scoped.mapIndexed { index, entry -> entry.virtualPath to index }.toMap()
        val result = runCatching {
            virtualFileSearch.glob(
                files = byVirtualPath.keys.map { path -> AgentVirtualFile(path, "") },
                request = AgentVirtualGlobRequest(
                    pattern = pattern,
                    ignoreCase = false,
                ),
            )
        }.getOrElse { error ->
            return@AgentDynamicTool variableSearchFailure("glob_error", error)
        }
        AgentDynamicToolResult(
            JSONObject()
                .put("status", if (result.paths.isEmpty()) "no_matches" else "ok")
                .put("pattern", pattern)
                .put("path", scope)
                .put("required_variables", catalog.requiredVariablesJson())
                .put(
                    "paths",
                    JSONArray(
                        result.paths
                            .mapNotNull(byVirtualPath::get)
                            .sortedBy { selected -> orderByVirtualPath[selected.virtualPath] ?: Int.MAX_VALUE }
                            .map { selected -> selected.entry.path },
                    ),
                )
                .put("truncated", result.omitted > 0)
                .put("omitted", result.omitted)
                .toString(),
        )
    },
)

internal fun characterVariableGrepTool(
    config: VariableConfig,
    catalogProvider: () -> List<CharacterVariableCatalogEntry>,
    turnState: CharacterVariableTurnState,
    virtualFileSearch: AgentVirtualFileSearch,
): AgentDynamicTool = AgentDynamicTool(
    definition = AgentToolDefinition(
        name = AgentGrepVariablesTool,
        description = "使用 ripgrep 正则搜索变量路径、类型、默认值、当前值、说明和作者更新规则。" +
            "数组按一个完整变量搜索，不拆成索引变量。" +
            "默认只返回匹配变量；找到路径后用读取工具取得完整信息。" +
            "required_variables 始终列出当前配置中的必读变量，本回合必须逐项读取。",
        parameters = variableSearchParameters(includeOutputMode = true),
    ),
    handler = { arguments ->
        val catalog = catalogProvider()
        val pattern = arguments.variableString(VariablePatternArgument).orEmpty()
        if (pattern.isBlank()) {
            return@AgentDynamicTool variableInvalidArguments("pattern 不能为空。")
        }
        val scope = validatedVariableScope(catalog, arguments.variableString(VariablePathArgument))
            ?: return@AgentDynamicTool variableInvalidPath()
        val scoped = variableEntriesInScope(catalog, scope)
        val byVirtualPath = scoped.associateBy(ScopedVariableEntry::virtualPath)
        val orderByVirtualPath = scoped.mapIndexed { index, entry -> entry.virtualPath to index }.toMap()
        val initialState = JSONObject(config.initialStateJson.ifBlank { "{}" })
        val currentState = JSONObject(turnState.stateJson)
        val outputMode = arguments.variableString(VariableOutputModeArgument)
            ?: VariableFilesWithMatchesMode
        if (outputMode !in VariableGrepOutputModes) {
            return@AgentDynamicTool variableInvalidArguments("output_mode 不受支持。")
        }
        val result = runCatching {
            virtualFileSearch.grep(
                files = scoped.map { selected ->
                    AgentVirtualFile(
                        path = selected.virtualPath,
                        content = selected.entry.searchableVariableText(initialState, currentState),
                    )
                },
                request = AgentVirtualGrepRequest(
                    pattern = pattern,
                    fileGlob = arguments.variableString(VariableGlobArgument),
                    ignoreCase = arguments.variableBoolean(VariableIgnoreCaseArgument),
                    multiline = arguments.variableBoolean(VariableMultilineArgument),
                    limit = arguments.variableResultLimit(),
                ),
            )
        }.getOrElse { error ->
            return@AgentDynamicTool variableSearchFailure("grep_error", error)
        }
        val matches = when (outputMode) {
            VariableFilesWithMatchesMode -> JSONArray(
                result.paths
                    .mapNotNull(byVirtualPath::get)
                    .sortedBy { selected -> orderByVirtualPath[selected.virtualPath] ?: Int.MAX_VALUE }
                    .map { selected -> selected.entry.searchCandidateJson() },
            )
            VariableCountMode -> JSONArray(
                result.counts.entries
                    .sortedBy { (virtualPath, _) -> orderByVirtualPath[virtualPath] ?: Int.MAX_VALUE }
                    .mapNotNull { (virtualPath, count) ->
                        byVirtualPath[virtualPath]?.let { selected ->
                            selected.entry.searchCandidateJson().put("count", count)
                        }
                    },
            )
            else -> JSONArray(
                result.lines
                    .sortedWith(
                        compareBy<AgentVirtualGrepLine> { line ->
                            orderByVirtualPath[line.path] ?: Int.MAX_VALUE
                        }.thenBy { it.line },
                    )
                    .mapNotNull { line ->
                        byVirtualPath[line.path]?.let { selected ->
                            selected.entry.searchCandidateJson()
                                .put("line", line.line)
                                .put("text", line.text)
                                .put("match_count", line.matchCount)
                        }
                    },
            )
        }
        AgentDynamicToolResult(
            JSONObject()
                .put("status", if (result.paths.isEmpty()) "no_matches" else "ok")
                .put("pattern", pattern)
                .put("path", scope)
                .put("output_mode", outputMode)
                .put("required_variables", catalog.requiredVariablesJson())
                .put("matches", matches)
                .put(
                    "omitted",
                    if (outputMode == VariableContentMode) result.omittedLines else result.omittedPaths,
                )
                .toString(),
        )
    },
)

private fun variableSearchParameters(includeOutputMode: Boolean): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put(VariablePatternArgument, buildJsonObject {
            put("type", "string")
            put(
                "description",
                if (includeOutputMode) "ripgrep 正则表达式。" else "变量路径 Glob 模式；省略时使用 ** 列出当前 path 范围内全部变量路径。",
            )
            put("minLength", 1)
            put("maxLength", MaxVariablePatternCharacters)
        })
        put(VariablePathArgument, buildJsonObject {
            put("type", "string")
            put("description", "可选的精确变量组 JSON Pointer；留空表示所有变量，指定后递归搜索该组下的变量。")
        })
        if (includeOutputMode) {
            put(VariableGlobArgument, buildJsonObject {
                put("type", "string")
                put("description", "可选的变量路径 Glob 过滤器。")
            })
            put(VariableOutputModeArgument, buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    VariableGrepOutputModes.forEach { mode -> add(JsonPrimitive(mode)) }
                })
                put("description", "默认 files_with_matches；需要匹配行或计数时再改变。")
            })
            put(VariableIgnoreCaseArgument, buildJsonObject {
                put("type", "boolean")
                put("description", "是否忽略大小写；默认 false。")
            })
            put(VariableMultilineArgument, buildJsonObject {
                put("type", "boolean")
                put("description", "是否允许正则跨行匹配；默认 false。")
            })
            put(VariableLimitArgument, buildJsonObject {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", MaxVariableSearchResults)
                put("description", "最多返回多少项；默认 $DefaultVariableSearchResults。")
            })
        }
    })
    if (includeOutputMode) {
        put("required", buildJsonArray { add(JsonPrimitive(VariablePatternArgument)) })
    }
    put("additionalProperties", false)
}

private fun validatedVariableScope(
    catalog: List<CharacterVariableCatalogEntry>,
    rawPath: String?,
): String? {
    val scope = normalizeVariableGroupPrefix(rawPath.orEmpty()) ?: return null
    return scope.takeIf { it.isBlank() || it in variableGroupPaths(catalog) }
}

private fun variableEntriesInScope(
    catalog: List<CharacterVariableCatalogEntry>,
    scope: String,
): List<ScopedVariableEntry> {
    val prefix = scope.takeIf(String::isNotBlank)?.let { "$it/" } ?: "/"
    val pathsWithDescendants = variableGroupPaths(catalog)
    return catalog.mapNotNull { entry ->
        if (!entry.path.startsWith(prefix)) return@mapNotNull null
        val relativePath = entry.path.removePrefix(prefix)
        ScopedVariableEntry(
            entry = entry,
            // An object and its descendants cannot share the same real filesystem path.
            // Give container search documents an internal suffix, then translate matches
            // back to their authoritative JSON Pointer before returning tool results.
            virtualPath = if (entry.objectContainer || entry.path in pathsWithDescendants) {
                "$relativePath$VariableObjectSearchSuffix"
            } else {
                relativePath
            },
        )
    }
}

private fun CharacterVariableCatalogEntry.searchableVariableText(
    initialState: JSONObject,
    currentState: JSONObject,
): String = buildString {
    append("# ")
    appendLine(path)
    append("type: ")
    appendLine(type)
    append("title: ")
    appendLine(title)
    append("read_mode: ")
    appendLine(readMode.storageValue)
    append("default: ")
    appendLine(initialState.variableValueAtPointerOrNull(path).searchableJsonValue())
    append("current: ")
    appendLine(currentState.variableValueAtPointerOrNull(path).searchableJsonValue())
    append("description: ")
    appendLine(description)
    append("update_rule: ")
    append(updateRule)
}

private fun CharacterVariableCatalogEntry.searchCandidateJson(): JSONObject = JSONObject()
    .put("path", path)
    .put("read_mode", readMode.storageValue)

private fun List<CharacterVariableCatalogEntry>.requiredVariablesJson(): JSONArray = JSONArray(
    filter(CharacterVariableCatalogEntry::isRequired).map { entry ->
        entry.searchCandidateJson().put("title", entry.title)
    },
)

private fun Any.searchableJsonValue(): String = when (this) {
    JSONObject.NULL -> "null"
    is String -> JSONObject.quote(this)
    else -> toString()
}

private fun JsonObject.variableResultLimit(): Int =
    (get(VariableLimitArgument) as? JsonPrimitive)
        ?.contentOrNull
        ?.toIntOrNull()
        ?.coerceIn(1, MaxVariableSearchResults)
        ?: DefaultVariableSearchResults

private fun variableInvalidArguments(message: String): AgentDynamicToolResult =
    AgentDynamicToolResult(
        JSONObject()
            .put("status", "invalid_arguments")
            .put("message", message)
            .toString(),
        success = false,
    )

private fun variableInvalidPath(): AgentDynamicToolResult =
    AgentDynamicToolResult(
        JSONObject()
            .put("status", "invalid_path")
            .put("message", "path 必须是当前变量配置中真实存在的 JSON Pointer 变量组。")
            .toString(),
        success = false,
    )

private fun variableSearchFailure(status: String, error: Throwable): AgentDynamicToolResult =
    AgentDynamicToolResult(
        JSONObject()
            .put("status", status)
            .put("message", error.message.orEmpty().take(MaxVariableSearchErrorCharacters))
            .toString(),
        success = false,
    )

private fun variableGroupPaths(catalog: List<CharacterVariableCatalogEntry>): Set<String> = buildSet {
    catalog.forEach { entry ->
        val segments = entry.path.removePrefix("/").split('/').filter(String::isNotBlank)
        segments.dropLast(1).indices.forEach { index ->
            add("/" + segments.take(index + 1).joinToString("/"))
        }
    }
}

private data class ScopedVariableEntry(
    val entry: CharacterVariableCatalogEntry,
    val virtualPath: String,
)

private const val VariableObjectSearchSuffix = ".__eleckoi_object__"
private const val VariablePatternArgument = "pattern"
private const val VariablePathArgument = "path"
private const val VariableGlobArgument = "glob"
private const val VariableOutputModeArgument = "output_mode"
private const val VariableIgnoreCaseArgument = "ignore_case"
private const val VariableMultilineArgument = "multiline"
private const val VariableLimitArgument = "limit"
private const val VariableFilesWithMatchesMode = "files_with_matches"
private const val VariableContentMode = "content"
private const val VariableCountMode = "count"
private val VariableGrepOutputModes = listOf(
    VariableFilesWithMatchesMode,
    VariableContentMode,
    VariableCountMode,
)
private const val DefaultVariableSearchResults = 100
private const val MaxVariableSearchResults = 1_000
private const val MaxVariablePatternCharacters = 500
private const val MaxVariableSearchErrorCharacters = 2_000
