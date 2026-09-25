package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentDynamicTool
import com.eleckoi.android.engine.agent.api.AgentDynamicToolResult
import com.eleckoi.android.engine.agent.api.AgentReadSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentToolDefinition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun buildCharacterSettingLibraryReadTool(
    entries: List<SettingLibraryAgentEntry>,
): AgentDynamicTool? {
    val available = availableSettingLibraryEntries(entries)
    if (available.isEmpty()) return null
    return createCharacterSettingLibraryReadTool({ available }, RequiredSettingLibraryCache(emptyList()))
}

internal fun buildCharacterSettingLibraryReadTool(
    contextProvider: suspend () -> SettingLibraryAgentTurnContext,
    requiredCache: RequiredSettingLibraryCache,
): AgentDynamicTool = createCharacterSettingLibraryReadTool(
    entriesProvider = { contextProvider().readableEntries },
    requiredCache = requiredCache,
)

private fun createCharacterSettingLibraryReadTool(
    entriesProvider: suspend () -> List<SettingLibraryAgentEntry>,
    requiredCache: RequiredSettingLibraryCache,
): AgentDynamicTool = AgentDynamicTool(
    definition = AgentToolDefinition(
        name = AgentReadSettingFilesTool,
        description = "读取 Glob 或 Grep 已返回的虚拟设定文件。" +
            "路径没有 .md 后缀；不得猜测路径；不会修改设定。" +
            "只返回显式请求的文件；固定必读设定正文已在前置缓存设定区，" +
            "读取时返回可在前文定位的编号和标题；动态及按需设定返回正文。",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put(SettingLibraryPathsArgument, buildJsonObject {
                    put("type", "array")
                    put("description", "Glob 或 Grep 返回的完整虚拟设定文件路径。")
                    put("minItems", 1)
                    put("uniqueItems", true)
                    put("items", buildJsonObject {
                        put("type", "string")
                        put("minLength", 1)
                    })
                })
            })
            put("required", buildJsonArray { add(JsonPrimitive(SettingLibraryPathsArgument)) })
            put("additionalProperties", false)
        },
    ),
    handler = { arguments ->
        val available = availableSettingLibraryEntries(entriesProvider())
        val byPath = available.associateBy { entry -> entry.path.normalizedSettingPath() }
        val rawPaths = arguments.settingStringArray(SettingLibraryPathsArgument)
        if (rawPaths.isEmpty()) return@AgentDynamicTool invalidArguments("至少选择一个虚拟设定文件路径。")
        val normalizedPaths = rawPaths.map { path ->
            normalizeSettingLibraryPath(path, allowRoot = false)
        }
        if (normalizedPaths.any { it == null }) {
            return@AgentDynamicTool invalidPath("paths 必须是 Glob 或 Grep 返回的完整虚拟设定文件路径。")
        }
        val requestedPaths = normalizedPaths.filterNotNull().distinct()
        val missing = requestedPaths.filterNot(byPath::containsKey)
        if (missing.isNotEmpty()) {
            return@AgentDynamicTool AgentDynamicToolResult(
                content = buildJsonObject {
                    put("status", "not_found")
                    put("message", "存在当前虚拟设定库没有提供的文件路径，请重新使用 Glob 或 Grep。")
                    put("paths", buildJsonArray {
                        missing.forEach { path -> add(JsonPrimitive(path)) }
                    })
                }.toString(),
                success = false,
            )
        }
        AgentDynamicToolResult(
            content = buildJsonObject {
                put("status", "ok")
                put("files", buildJsonArray {
                    requestedPaths.forEach { path ->
                        val entry = requireNotNull(byPath[path])
                        val cached = requiredCache.referenceFor(entry)
                        add(buildJsonObject {
                            put("path", path)
                            put("title", entry.title)
                            put("group_path", entry.groupPath.normalizedGroupPath())
                            put("selection_hint", entry.selectionHint.normalizedSelectionHint())
                            put("read_strategy", entry.readStrategy.storageValue)
                            put("resolved_references", buildJsonArray {
                                entry.resolvedReferences.forEach { reference ->
                                    add(buildJsonObject {
                                        put("id", reference.id)
                                        put("title", reference.title)
                                        put("path", reference.path.normalizedSettingPath())
                                    })
                                }
                            })
                            put("content_delivery", if (cached != null) "cached_reference" else "tool_result")
                            cached?.let { put("cached_reference", it.reference) }
                            put("content", cached?.readReceipt ?: entry.content)
                            put("truncated", false)
                        })
                    }
                })
            }.toString(),
        )
    },
)
