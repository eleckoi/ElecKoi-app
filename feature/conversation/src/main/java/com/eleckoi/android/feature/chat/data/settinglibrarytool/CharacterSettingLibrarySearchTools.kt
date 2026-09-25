package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.agent.api.AgentGlobSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGrepSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentReadSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.engine.agent.api.AgentVirtualFile
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.api.AgentVirtualGlobRequest
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepLine
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepRequest
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal fun buildCharacterSettingLibraryGlobTool(
    entries: List<SettingLibraryAgentEntry>,
    virtualFileSearch: AgentVirtualFileSearch,
): AgentDynamicTool? {
    val available = availableSettingLibraryEntries(entries)
    if (available.isEmpty()) return null
    return createCharacterSettingLibraryGlobTool({ available }, virtualFileSearch, RequiredSettingLibraryCache(emptyList()))
}

internal fun buildCharacterSettingLibraryGlobTool(
    contextProvider: suspend () -> SettingLibraryAgentTurnContext,
    virtualFileSearch: AgentVirtualFileSearch,
    requiredCache: RequiredSettingLibraryCache,
): AgentDynamicTool = createCharacterSettingLibraryGlobTool(
    entriesProvider = { contextProvider().readableEntries },
    virtualFileSearch = virtualFileSearch,
    requiredCache = requiredCache,
)

private fun createCharacterSettingLibraryGlobTool(
    entriesProvider: suspend () -> List<SettingLibraryAgentEntry>,
    virtualFileSearch: AgentVirtualFileSearch,
    requiredCache: RequiredSettingLibraryCache,
): AgentDynamicTool = AgentDynamicTool(
    definition = AgentToolDefinition(
        name = AgentGlobSettingFilesTool,
        description = "使用 Glob 路径模式浏览当前对话的虚拟设定文件。" +
            "路径没有 .md 后缀，例如 **、人物/**、**/*关系*。" +
            "返回真实完整路径、标题和作者注释；不会读取正文。" +
            RequiredEntriesReadInstruction,
        parameters = searchParameters(includeOutputMode = false),
    ),
    handler = { arguments ->
        val available = availableSettingLibraryEntries(entriesProvider())
        val pattern = arguments.settingString(PatternArgument)?.trim().orEmpty().ifBlank { DefaultGlobPattern }
        val scope = validatedSettingScope(available, arguments.settingStringAllowBlank(PathArgument))
            ?: return@AgentDynamicTool invalidPath()
        val scopedFiles = settingFilesInScope(available, scope)
        val byVirtualPath = scopedFiles.associateBy(ScopedSettingEntry::virtualPath)
        val orderByVirtualPath = scopedFiles
            .mapIndexed { index, file -> file.virtualPath to index }
            .toMap()
        val result = runCatching {
            virtualFileSearch.glob(
                files = byVirtualPath.keys.map { path -> AgentVirtualFile(path, "") },
                request = AgentVirtualGlobRequest(
                    pattern = pattern,
                    limit = DefaultSearchResults,
                ),
            )
        }.getOrElse { error ->
            return@AgentDynamicTool searchFailure("glob_error", error)
        }
        AgentDynamicToolResult(
            content = buildJsonObject {
                put("status", if (result.paths.isEmpty()) "no_matches" else "ok")
                put("pattern", pattern)
                put("path", scope)
                putRequiredEntries(available, requiredCache)
                put("entries", buildJsonArray {
                    result.paths
                        .mapNotNull(byVirtualPath::get)
                        .sortedBy { scoped ->
                            orderByVirtualPath[scoped.virtualPath] ?: Int.MAX_VALUE
                        }
                        .forEach { scoped -> add(scoped.entry.candidateJson(requiredCache)) }
                })
                put("truncated", result.omitted > 0)
                put("omitted", result.omitted)
            }.toString(),
        )
    },
)

internal fun buildCharacterSettingLibraryGrepTool(
    entries: List<SettingLibraryAgentEntry>,
    virtualFileSearch: AgentVirtualFileSearch,
): AgentDynamicTool? {
    val available = availableSettingLibraryEntries(entries)
    if (available.isEmpty()) return null
    return createCharacterSettingLibraryGrepTool({ available }, virtualFileSearch, RequiredSettingLibraryCache(emptyList()))
}

internal fun buildCharacterSettingLibraryGrepTool(
    contextProvider: suspend () -> SettingLibraryAgentTurnContext,
    virtualFileSearch: AgentVirtualFileSearch,
    requiredCache: RequiredSettingLibraryCache,
): AgentDynamicTool = createCharacterSettingLibraryGrepTool(
    entriesProvider = { contextProvider().readableEntries },
    virtualFileSearch = virtualFileSearch,
    requiredCache = requiredCache,
)

private fun createCharacterSettingLibraryGrepTool(
    entriesProvider: suspend () -> List<SettingLibraryAgentEntry>,
    virtualFileSearch: AgentVirtualFileSearch,
    requiredCache: RequiredSettingLibraryCache,
): AgentDynamicTool = AgentDynamicTool(
    definition = AgentToolDefinition(
        name = AgentGrepSettingFilesTool,
        description = "使用 ripgrep 正则搜索当前对话的虚拟设定文件标题、作者注释和正文。" +
            "默认返回匹配文件的完整路径；需要定位文本时再选择 content 或 count。" +
            RequiredEntriesReadInstruction,
        parameters = searchParameters(includeOutputMode = true),
    ),
    handler = { arguments ->
        val available = availableSettingLibraryEntries(entriesProvider())
        val pattern = arguments.settingString(PatternArgument).orEmpty()
        if (pattern.isBlank()) return@AgentDynamicTool invalidArguments("pattern 不能为空。")
        val scope = validatedSettingScope(available, arguments.settingStringAllowBlank(PathArgument))
            ?: return@AgentDynamicTool invalidPath()
        val scopedFiles = settingFilesInScope(available, scope)
        val byVirtualPath = scopedFiles.associateBy(ScopedSettingEntry::virtualPath)
        val orderByVirtualPath = scopedFiles
            .mapIndexed { index, file -> file.virtualPath to index }
            .toMap()
        val outputMode = arguments.settingString(OutputModeArgument) ?: FilesWithMatchesMode
        if (outputMode !in GrepOutputModes) {
            return@AgentDynamicTool invalidArguments("output_mode 不受支持。")
        }
        val result = runCatching {
            virtualFileSearch.grep(
                files = scopedFiles.map { scoped ->
                    AgentVirtualFile(
                        path = scoped.virtualPath,
                        content = scoped.entry.searchableText(),
                    )
                },
                request = AgentVirtualGrepRequest(
                    pattern = pattern,
                    fileGlob = arguments.settingString(GlobArgument),
                    ignoreCase = arguments.settingBoolean(IgnoreCaseArgument),
                    multiline = arguments.settingBoolean(MultilineArgument),
                    limit = arguments.resultLimit(),
                ),
            )
        }.getOrElse { error ->
            return@AgentDynamicTool searchFailure("grep_error", error)
        }
        AgentDynamicToolResult(
            content = buildJsonObject {
                put("status", if (result.paths.isEmpty()) "no_matches" else "ok")
                put("pattern", pattern)
                put("path", scope)
                put("output_mode", outputMode)
                putRequiredEntries(available, requiredCache)
                put("matches", buildJsonArray {
                    when (outputMode) {
                        FilesWithMatchesMode -> result.paths
                            .sortedBy { path -> orderByVirtualPath[path] ?: Int.MAX_VALUE }
                            .forEach { path ->
                                byVirtualPath[path]?.entry?.let { entry -> add(entry.candidateJson(requiredCache)) }
                            }
                        CountMode -> result.counts.entries
                            .sortedBy { (path, _) -> orderByVirtualPath[path] ?: Int.MAX_VALUE }
                            .forEach { (path, count) ->
                                byVirtualPath[path]?.entry?.let { entry ->
                                    add(buildJsonObject {
                                        put("path", entry.path.normalizedSettingPath())
                                        put("count", count)
                                    })
                                }
                            }
                        ContentMode -> result.lines
                            .sortedWith(
                                compareBy<AgentVirtualGrepLine> { line ->
                                    orderByVirtualPath[line.path] ?: Int.MAX_VALUE
                                }.thenBy(AgentVirtualGrepLine::line),
                            )
                            .forEach { line ->
                                byVirtualPath[line.path]?.entry?.let { entry ->
                                    add(line.matchJson(entry))
                                }
                            }
                    }
                })
                put("omitted", if (outputMode == ContentMode) result.omittedLines else result.omittedPaths)
            }.toString(),
        )
    },
)

private fun searchParameters(includeOutputMode: Boolean): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put(PatternArgument, buildJsonObject {
            put("type", "string")
            put(
                "description",
                if (includeOutputMode) {
                    "ripgrep 正则表达式。"
                } else {
                    "可选的虚拟文件路径 Glob 模式；省略时列出全部设定。"
                },
            )
            put("minLength", 1)
            put("maxLength", MaxPatternCharacters)
        })
        put(PathArgument, buildJsonObject {
            put("type", "string")
            put("description", "可选的精确虚拟目录路径；留空表示整个设定库。")
        })
        if (includeOutputMode) {
            put(GlobArgument, buildJsonObject {
                put("type", "string")
                put("description", "可选的虚拟文件路径 Glob 过滤器，例如 人物/**。")
            })
            put(OutputModeArgument, buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray { GrepOutputModes.forEach { add(JsonPrimitive(it)) } })
                put("description", "默认 files_with_matches；需要匹配行或计数时选择 content 或 count。")
            })
            put(IgnoreCaseArgument, buildJsonObject {
                put("type", "boolean")
                put("description", "是否忽略大小写；默认 false。")
            })
            put(MultilineArgument, buildJsonObject {
                put("type", "boolean")
                put("description", "是否允许正则跨行匹配；默认 false。")
            })
            put(LimitArgument, buildJsonObject {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", MaxSearchResults)
                put("description", "最多返回多少项；默认 $DefaultSearchResults。")
            })
        }
    })
    if (includeOutputMode) {
        put("required", buildJsonArray { add(JsonPrimitive(PatternArgument)) })
    }
    put("additionalProperties", false)
}

private fun validatedSettingScope(
    entries: List<SettingLibraryAgentEntry>,
    rawPath: String?,
): String? {
    val scope = normalizeSettingLibraryPath(rawPath.orEmpty(), allowRoot = true) ?: return null
    if (scope.isBlank()) return scope
    val prefix = "$scope/"
    return scope.takeIf { entries.any { entry -> entry.path.normalizedSettingPath().startsWith(prefix) } }
}

private fun settingFilesInScope(
    entries: List<SettingLibraryAgentEntry>,
    scope: String,
): List<ScopedSettingEntry> {
    val prefix = scope.takeIf(String::isNotBlank)?.let { "$it/" }.orEmpty()
    return entries.mapNotNull { entry ->
        val path = entry.path.normalizedSettingPath()
        if (!path.startsWith(prefix)) return@mapNotNull null
        ScopedSettingEntry(
            entry = entry,
            virtualPath = path.removePrefix(prefix),
        )
    }
}

private fun SettingLibraryAgentEntry.searchableText(): String = buildString {
    append("title: ")
    appendLine(title)
    append("group: ")
    appendLine(groupPath.normalizedGroupPath())
    selectionHint.normalizedSelectionHint().takeIf(String::isNotBlank)?.let { hint ->
        append("selection_hint: ")
        appendLine(hint)
    }
    appendLine()
    append(content)
}

private fun SettingLibraryAgentEntry.candidateJson(requiredCache: RequiredSettingLibraryCache): JsonObject = buildJsonObject {
    val cached = requiredCache.referenceFor(this@candidateJson)
    put("path", path.normalizedSettingPath())
    put("title", title)
    put("group_path", groupPath.normalizedGroupPath())
    put("selection_hint", selectionHint.normalizedSelectionHint())
    put("read_strategy", readStrategy.storageValue)
    put("content_delivery", if (cached != null) "cached_reference" else "tool_result")
    cached?.let { put("cached_reference", it.reference) }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putRequiredEntries(
    entries: List<SettingLibraryAgentEntry>,
    requiredCache: RequiredSettingLibraryCache,
) {
    val required = entries.filter(SettingLibraryAgentEntry::isRequiredThisTurn)
    put("required_entries", buildJsonArray {
        required.forEach { entry -> add(entry.candidateJson(requiredCache)) }
    })
}

private fun SettingLibraryAgentEntry.isRequiredThisTurn(): Boolean =
    readStrategy == SettingLibraryAgentReadStrategy.Required || promotedToRequiredThisTurn

private val RequiredEntriesReadInstruction =
    "搜索结果的 required_entries 是本回合必读清单（固定必读及关键词、EJS/变量触发项），" +
        "与 entries/matches 是否命中无关。" +
        "若非空，回复用户前必须调用 $AgentReadSettingFilesTool，" +
        "把其中所有 path 一次传入 paths；仅搜索不算读取。" +
        "即使固定必读项标记为 cached_reference、正文已在前置设定区，也必须读取，" +
        "用工具回执的编号和标题核对前置正文。"

private fun AgentVirtualGrepLine.matchJson(entry: SettingLibraryAgentEntry): JsonObject =
    buildJsonObject {
        put("path", entry.path.normalizedSettingPath())
        put("line", line)
        put("text", text)
        put("match_count", matchCount)
    }

private fun JsonObject.resultLimit(): Int =
    (get(LimitArgument) as? JsonPrimitive)
        ?.contentOrNull
        ?.toIntOrNull()
        ?.coerceIn(1, MaxSearchResults)
        ?: DefaultSearchResults

private fun searchFailure(status: String, error: Throwable): AgentDynamicToolResult = AgentDynamicToolResult(
    content = buildJsonObject {
        put("status", status)
        put("message", error.message.orEmpty().take(MaxSearchErrorCharacters))
    }.toString(),
    success = false,
)

private data class ScopedSettingEntry(
    val entry: SettingLibraryAgentEntry,
    val virtualPath: String,
)

private const val PatternArgument = "pattern"
private const val DefaultGlobPattern = "**"
private const val PathArgument = "path"
private const val GlobArgument = "glob"
private const val OutputModeArgument = "output_mode"
private const val IgnoreCaseArgument = "ignore_case"
private const val MultilineArgument = "multiline"
private const val LimitArgument = "limit"
private const val FilesWithMatchesMode = "files_with_matches"
private const val ContentMode = "content"
private const val CountMode = "count"
private val GrepOutputModes = listOf(FilesWithMatchesMode, ContentMode, CountMode)
private const val DefaultSearchResults = 100
private const val MaxSearchResults = 1_000
private const val MaxPatternCharacters = 500
private const val MaxSearchErrorCharacters = 2_000
