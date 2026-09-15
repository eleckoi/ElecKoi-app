package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.engine.creator.capability.CreatorCapability
import com.eleckoi.android.engine.creator.capability.CreatorCapabilityEffect
import com.eleckoi.android.engine.creator.capability.CreatorOperationDefinition
import com.eleckoi.android.engine.creator.capability.CreatorToolsetDefinition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningMessage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isPinnedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryOpeningEntry
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringContext
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringException
import com.eleckoi.android.feature.studio.authoring.creatorArray
import com.eleckoi.android.feature.studio.authoring.creatorArraySchema
import com.eleckoi.android.feature.studio.authoring.creatorBoolean
import com.eleckoi.android.feature.studio.authoring.creatorBooleanSchema
import com.eleckoi.android.feature.studio.authoring.creatorInt
import com.eleckoi.android.feature.studio.authoring.creatorObjectSchema
import com.eleckoi.android.feature.studio.authoring.creatorString
import com.eleckoi.android.feature.studio.authoring.creatorStringSchema
import com.eleckoi.android.feature.studio.api.CreatorSettingLibraryMetadata
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private const val MaxPreviewOperations = 100

internal data class SettingLibraryPendingChange(
    val id: String,
    val rootId: String,
    val baseRevision: String,
    val nextLibrary: SettingLibrary,
    val summary: JsonObject,
)

internal class SettingLibraryCreatorChangeStore {
    private val pending = ConcurrentHashMap<String, SettingLibraryPendingChange>()

    fun put(change: SettingLibraryPendingChange) {
        pending[change.id] = change
    }

    fun get(id: String): SettingLibraryPendingChange? = pending[id]

    fun remove(id: String): SettingLibraryPendingChange? = pending.remove(id)
}

internal object SettingLibraryCreatorCapabilities {
    val toolset = CreatorToolsetDefinition(
        id = "creator.setting_library",
        title = "角色设定库",
        description = "查看并修改角色设定库，包括开场白、角色扮演计划、Agent 读取策略、提示词常驻位置、分组与普通条目。",
    )

    fun capabilities(): List<CreatorCapability<CreatorAuthoringContext, CreatorOperationDefinition>> = listOf(
        capability(
            id = "setting_library.get_authoring_guide",
            title = "读取设定库技术说明",
            description = "返回触发方式、Agent 读取策略、动态模式、关键词规则和提示词常驻位置的运行语义；修改这些字段前应先读取。",
            schema = rootSchema(),
        ) { context, arguments ->
            context.resolveRootId(arguments.creatorString("root_id"))
            authoringGuideJson()
        },
        capability(
            id = "setting_library.inspect",
            title = "查看设定库目录",
            description = "分页查看指定角色根的统计、分组和条目摘要，不返回全部正文。",
            schema = creatorObjectSchema {
                put("root_id", creatorStringSchema("已挂载角色根 id；留空使用主角色。"))
                put("entry_cursor", creatorStringSchema("上一页 entriesNextCursor；首页留空。"))
                put("group_cursor", creatorStringSchema("上一页 groupsNextCursor；首页留空。"))
                put("limit", pageLimitSchema(20))
            },
        ) { context, arguments ->
            val rootId = context.resolveRootId(arguments.creatorString("root_id"))
            val limit = arguments.creatorInt("limit", 20).coerceIn(1, 50)
            val metadata = context.service.creatorSettingLibraryMetadata(context.workspaceId, rootId)
            val entries = context.service.searchCreatorSettingEntries(
                workspaceId = context.workspaceId,
                rootId = rootId,
                cursor = arguments.creatorString("entry_cursor"),
                limit = limit,
            )
            val groups = context.service.searchCreatorSettingGroups(
                workspaceId = context.workspaceId,
                rootId = rootId,
                cursor = arguments.creatorString("group_cursor"),
                limit = limit,
            )
            buildJsonObject {
                put("rootId", rootId)
                put("characterId", metadata.characterId)
                put("name", metadata.name)
                put("revision", metadata.revision())
                put("entryCount", metadata.entryCount)
                put("groupCount", metadata.groupCount)
                put("promptPositions", buildJsonArray {
                    metadata.promptPositions.forEach { add(it.summaryJson()) }
                })
                put("groups", buildJsonArray { groups.items.forEach { add(it.summaryJson()) } })
                put("groupsNextCursor", groups.nextCursor)
                put("entries", buildJsonArray { entries.items.forEach { add(it.summaryJson()) } })
                put("entriesNextCursor", entries.nextCursor)
            }
        },
        capability(
            id = "setting_library.search",
            title = "搜索设定库",
            description = "在一个已挂载角色根中分页搜索标题、正文、选择提示和关键词；搜索其他角色时切换 root_id。",
            schema = creatorObjectSchema(required = listOf("query")) {
                put("query", creatorStringSchema("不区分大小写的搜索文本。"))
                put("root_id", creatorStringSchema("要搜索的角色根；留空使用主角色。"))
                put("cursor", creatorStringSchema("上一页 nextCursor；首页留空。"))
                put("limit", pageLimitSchema(20))
            },
        ) { context, arguments ->
            val query = arguments.creatorString("query")
                .takeIf(String::isNotBlank)
                ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "query 不能为空")
            val rootId = context.resolveRootId(arguments.creatorString("root_id"))
            val page = context.service.searchCreatorSettingEntries(
                workspaceId = context.workspaceId,
                rootId = rootId,
                query = query,
                cursor = arguments.creatorString("cursor"),
                limit = arguments.creatorInt("limit", 20).coerceIn(1, 50),
            )
            buildJsonObject {
                put("rootId", rootId)
                put("query", query)
                put("matches", buildJsonArray { page.items.forEach { add(it.summaryJson()) } })
                put("nextCursor", page.nextCursor)
                put("hasMore", page.nextCursor.isNotBlank())
            }
        },
        capability(
            id = "setting_library.read_entry",
            title = "读取完整设定条目",
            description = "按正文字符区间读取一个条目及其完整配置；超长正文必须继续翻页。",
            schema = creatorObjectSchema(required = listOf("entry_id")) {
                put("root_id", creatorStringSchema("角色根 id；留空使用主角色。"))
                put("entry_id", creatorStringSchema("inspect 或 search 返回的条目 id。"))
                put("opening_message_id", creatorStringSchema("开场白条目的消息 id；留空读取默认开场。"))
                put("content_field", creatorStringSchema(
                    "分页读取的长文本字段；普通条目只支持 content。",
                    listOf("content", "initial_variable_state_json"),
                ))
                put("content_offset", buildJsonObject {
                    put("type", "integer")
                    put("minimum", 0)
                })
                put("max_chars", buildJsonObject {
                    put("type", "integer")
                    put("minimum", 500)
                    put("maximum", 12000)
                    put("default", 6000)
                })
            },
        ) { context, arguments ->
            val rootId = context.resolveRootId(arguments.creatorString("root_id"))
            val entryId = arguments.creatorString("entry_id")
            val entry = context.service.creatorSettingEntry(context.workspaceId, rootId, entryId)
                ?: throw CreatorAuthoringException("ENTRY_NOT_FOUND", "找不到设定条目：$entryId")
            val metadata = context.service.creatorSettingLibraryMetadata(context.workspaceId, rootId)
            val openingEntry = entry.takeIf { it.isOpeningEntry() }?.let(::settingLibraryOpeningEntry)
            val openingMessage = openingEntry?.let { normalized ->
                val requested = arguments.creatorString("opening_message_id")
                    .ifBlank { normalized.defaultOpeningMessageId }
                normalized.openingMessages.firstOrNull { it.id == requested }
                    ?: throw CreatorAuthoringException("OPENING_MESSAGE_NOT_FOUND", "找不到开场白消息：$requested")
            }
            val contentField = arguments.creatorString("content_field").ifBlank { "content" }
            if (openingMessage == null && contentField != "content") {
                throw CreatorAuthoringException("INVALID_ARGUMENTS", "普通设定条目只支持读取 content")
            }
            val sourceContent = when (contentField) {
                "content" -> openingMessage?.content ?: entry.content
                "initial_variable_state_json" -> openingMessage?.initialVariableStateJson
                    ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "只有开场白消息包含初始变量状态")
                else -> throw CreatorAuthoringException("INVALID_ARGUMENTS", "content_field 无效：$contentField")
            }
            val offset = arguments.creatorInt("content_offset", 0).coerceIn(0, sourceContent.length)
            val maxChars = arguments.creatorInt("max_chars", 6000).coerceIn(500, 12000)
            val end = (offset + maxChars).coerceAtMost(sourceContent.length)
            buildJsonObject {
                put("rootId", rootId)
                put("revision", metadata.revision())
                put("entry", entry.fullJson(
                    contentChunk = sourceContent.substring(offset, end),
                    contentField = contentField,
                    selectedOpeningMessageId = openingMessage?.id.orEmpty(),
                ))
                put("contentOffset", offset)
                put("contentEnd", end)
                put("contentLength", sourceContent.length)
                put("nextOffset", if (end < sourceContent.length) end else -1)
                put("hasMore", end < sourceContent.length)
            }
        },
        capability(
            id = "setting_library.preview_changes",
            title = "预览设定库修改",
            description = "在内存中校验并预览一组创建、修改、移动或删除操作，不保存。",
            effect = CreatorCapabilityEffect.Preview,
            schema = creatorObjectSchema(required = listOf("operations")) {
                put("root_id", creatorStringSchema("可写角色根 id；留空使用主角色。"))
                put("base_revision", creatorStringSchema("可选的读取版本提示；预览始终自动基于最新设定库，不会因该值过期而失败。"))
                put("operations", creatorArraySchema("有序设定库修改操作。", changeOperationSchema(), 100))
            },
        ) { context, arguments ->
            val rootId = context.resolveRootId(arguments.creatorString("root_id"))
            context.requireWritableRoot(rootId)
            val metadata = context.service.creatorSettingLibraryMetadata(context.workspaceId, rootId)
            val currentRevision = metadata.revision()
            // Full materialization is reserved for a bounded mutation preview. Directory/search/read
            // operations above are row-paged and never take this path.
            val library = context.service.loadCreatorSettingLibrary(context.workspaceId, rootId)
            val operations = arguments.creatorArray("operations")
                ?.mapIndexed { index, element ->
                    element as? JsonObject
                        ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "operations[$index] 必须是 object")
                }
                .orEmpty()
            if (operations.isEmpty()) {
                throw CreatorAuthoringException("INVALID_ARGUMENTS", "operations 不能为空")
            }
            if (operations.size > MaxPreviewOperations) {
                throw CreatorAuthoringException(
                    "INVALID_ARGUMENTS",
                    "单次最多预览 $MaxPreviewOperations 个修改操作，请拆分后分批提交",
                )
            }
            val result = applyOperations(library, operations)
            validateLibrary(result.library)
            val changeSetId = "setting-change-${UUID.randomUUID()}"
            val summary = buildJsonObject {
                put("operationCount", operations.size)
                put("createdEntries", result.createdEntries)
                put("updatedEntries", result.updatedEntries)
                put("deletedEntries", result.deletedEntries)
                put("createdGroups", result.createdGroups)
                put("updatedGroups", result.updatedGroups)
                put("deletedGroups", result.deletedGroups)
                put("createdPromptPositions", result.createdPromptPositions)
                put("updatedPromptPositions", result.updatedPromptPositions)
                put("deletedPromptPositions", result.deletedPromptPositions)
            }
            context.settingLibraryChanges.put(
                SettingLibraryPendingChange(
                    id = changeSetId,
                    rootId = rootId,
                    baseRevision = currentRevision,
                    nextLibrary = result.library,
                    summary = summary,
                ),
            )
            buildJsonObject {
                put("valid", true)
                put("changeSetId", changeSetId)
                put("rootId", rootId)
                put("baseRevision", currentRevision)
                put("summary", summary)
                put("changes", buildJsonArray { result.descriptions.forEach { add(JsonPrimitive(it)) } })
                put("requiresWritePermission", context.currentPermissionMode() == com.eleckoi.android.engine.agent.api.AgentPermissionMode.AskForApproval)
            }
        },
        capability(
            id = "setting_library.apply_changes",
            title = "提交设定库修改",
            description = "提交一个已预览变更集；要求 Workspace Write，并在提交前重新检查 revision。",
            effect = CreatorCapabilityEffect.Write,
            schema = creatorObjectSchema(required = listOf("change_set_id")) {
                put("change_set_id", creatorStringSchema("preview_changes 返回的 changeSetId。"))
            },
        ) { context, arguments ->
            context.requireWritePermission()
            val changeSetId = arguments.creatorString("change_set_id")
            val change = context.settingLibraryChanges.get(changeSetId)
                ?: throw CreatorAuthoringException("CHANGE_SET_NOT_FOUND", "变更集不存在或当前会话已经重建")
            context.requireWritableRoot(change.rootId)
            val currentRevision = context.service.creatorSettingLibraryMetadata(
                context.workspaceId,
                change.rootId,
            ).revision()
            if (currentRevision != change.baseRevision) {
                context.settingLibraryChanges.remove(changeSetId)
                throw CreatorAuthoringException("REVISION_CONFLICT", "设定库已经变化，旧变更集没有提交")
            }
            context.service.saveCreatorSettingLibrary(
                workspaceId = context.workspaceId,
                rootId = change.rootId,
                library = change.nextLibrary,
            )
            val savedRevision = context.service.creatorSettingLibraryMetadata(
                context.workspaceId,
                change.rootId,
            ).revision()
            context.settingLibraryChanges.remove(changeSetId)
            buildJsonObject {
                put("status", "applied")
                put("changeSetId", changeSetId)
                put("rootId", change.rootId)
                put("revision", savedRevision)
                put("summary", change.summary)
            }
        },
    )

    private fun capability(
        id: String,
        title: String,
        description: String,
        effect: CreatorCapabilityEffect = CreatorCapabilityEffect.Read,
        schema: JsonObject,
        handler: suspend (CreatorAuthoringContext, JsonObject) -> JsonElement,
    ) = CreatorCapability(
        definition = CreatorOperationDefinition(
            capabilityId = id,
            toolsetId = toolset.id,
            title = title,
            description = description,
            effect = effect,
            inputSchema = schema,
        ),
        handler = handler,
    )

    private fun CreatorSettingLibraryMetadata.revision(): String =
        updatedAt.ifBlank { "uninitialized:$characterId:$entryCount:$groupCount" }
}
