package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.engine.creator.capability.CreatorCapability
import com.eleckoi.android.engine.creator.capability.CreatorCapabilityEffect
import com.eleckoi.android.engine.creator.capability.CreatorOperationDefinition
import com.eleckoi.android.engine.creator.capability.CreatorToolsetDefinition
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringContext
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringException
import com.eleckoi.android.feature.studio.authoring.creatorArray
import com.eleckoi.android.feature.studio.authoring.creatorArraySchema
import com.eleckoi.android.feature.studio.authoring.creatorInt
import com.eleckoi.android.feature.studio.authoring.creatorObjectSchema
import com.eleckoi.android.feature.studio.authoring.creatorString
import com.eleckoi.android.feature.studio.authoring.creatorStringSchema
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MaxVariablePreviewOperations = 100
internal const val MaxVariablePageSize = 50

internal data class VariablePendingChange(
    val id: String,
    val rootId: String,
    val baseRevision: String,
    val nextConfig: VariableConfig,
    val summary: JsonObject,
)

internal class VariableCreatorChangeStore {
    private val pending = ConcurrentHashMap<String, VariablePendingChange>()

    fun put(change: VariablePendingChange) {
        pending[change.id] = change
    }

    fun get(id: String): VariablePendingChange? = pending[id]

    fun remove(id: String): VariablePendingChange? = pending.remove(id)
}

internal object VariableCreatorCapabilities {
    val toolset = CreatorToolsetDefinition(
        id = "creator.variables",
        title = "角色变量配置",
        description = "分页查看、搜索并完整修改角色变量对象、变量项、初始化状态、校验代码和配置版本。",
    )

    fun capabilities(): List<CreatorCapability<CreatorAuthoringContext, CreatorOperationDefinition>> = listOf(
        capability(
            id = "variables.get_authoring_guide",
            title = "读取变量配置技术说明",
            description = "返回变量对象、变量项、初始状态、读取方式、校验代码与版本的写入语义。",
            schema = variableRootSchema(),
        ) { context, arguments ->
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            authoringGuideJson(context.creatorRootDisplayName(rootId))
        },
        capability(
            id = "variables.inspect",
            title = "查看变量配置目录",
            description = "分页返回变量配置元数据、对象、变量项和版本摘要，不返回大段初始状态或校验代码。",
            schema = creatorObjectSchema {
                put("root_id", creatorStringSchema("已挂载角色根 id；留空使用主角色。"))
                put("object_cursor", creatorStringSchema("上一页 objectsNextCursor；首页留空。"))
                put("variable_cursor", creatorStringSchema("上一页 variablesNextCursor；首页留空。"))
                put("version_cursor", creatorStringSchema("上一页 versionsNextCursor；首页留空。"))
                put("limit", variablePageLimitSchema())
            },
        ) { context, arguments ->
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            val versioned = context.service.loadVersionedCreatorVariableConfig(context.workspaceId, rootId)
            val config = versioned.config
            val limit = arguments.creatorInt("limit", 20).coerceIn(1, MaxVariablePageSize)
            val objects = page(config.objects, arguments.creatorString("object_cursor"), limit)
            val variables = page(config.variables, arguments.creatorString("variable_cursor"), limit)
            val versions = page(config.versions, arguments.creatorString("version_cursor"), limit)
            buildJsonObject {
                put("rootId", rootId)
                put("targetName", context.creatorRootDisplayName(rootId))
                put("name", config.name)
                put("revision", versioned.revision.toString())
                put("activeVersionId", config.activeVersionId)
                put("initialStateLength", config.initialStateJson.length)
                put("schemaCodeLength", config.schemaCode.length)
                put("objectCount", config.objects.size)
                put("variableCount", config.variables.size)
                put("versionCount", config.versions.size)
                put("objects", buildJsonArray { objects.items.forEach { add(it.summaryJson()) } })
                put("objectsNextCursor", objects.nextCursor)
                put("variables", buildJsonArray { variables.items.forEach { add(it.summaryJson()) } })
                put("variablesNextCursor", variables.nextCursor)
                put("versions", buildJsonArray { versions.items.forEach { add(it.summaryJson()) } })
                put("versionsNextCursor", versions.nextCursor)
            }
        },
        capability(
            id = "variables.search",
            title = "搜索变量配置",
            description = "分页搜索当前活动版本中的对象名、变量名、说明、更新规则和默认值。",
            schema = creatorObjectSchema(required = listOf("query")) {
                put("root_id", creatorStringSchema("角色根 id；留空使用主角色。"))
                put("query", creatorStringSchema("不区分大小写的搜索文本。"))
                put("cursor", creatorStringSchema("上一页 nextCursor；首页留空。"))
                put("limit", variablePageLimitSchema())
            },
        ) { context, arguments ->
            val query = arguments.creatorString("query").takeIf(String::isNotBlank)
                ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "query 不能为空")
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            val versioned = context.service.loadVersionedCreatorVariableConfig(context.workspaceId, rootId)
            val config = versioned.config
            val matches = buildList<VariableSearchResult> {
                config.objects.filter { item ->
                    listOf(item.name, item.description, item.updateRule).any { it.contains(query, ignoreCase = true) }
                }.forEach { add(VariableSearchResult.Object(it)) }
                config.variables.filter { item ->
                    listOf(item.title, item.description, item.updateRule, item.defaultValue).any {
                        it.contains(query, ignoreCase = true)
                    }
                }.forEach { add(VariableSearchResult.Variable(it)) }
            }
            val result = page(matches, arguments.creatorString("cursor"), arguments.creatorInt("limit", 20).coerceIn(1, MaxVariablePageSize))
            buildJsonObject {
                put("rootId", rootId)
                put("targetName", context.creatorRootDisplayName(rootId))
                put("query", query)
                put("matches", buildJsonArray { result.items.forEach { add(it.summaryJson()) } })
                put("nextCursor", result.nextCursor)
                put("hasMore", result.nextCursor.isNotBlank())
            }
        },
        capability(
            id = "variables.read",
            title = "读取完整变量配置项",
            description = "读取对象、变量项、版本元数据，或按字符区间读取初始化状态与校验代码。",
            schema = creatorObjectSchema(required = listOf("kind")) {
                put("root_id", creatorStringSchema("角色根 id；留空使用主角色。"))
                put("kind", creatorStringSchema("读取对象类型。", listOf("object", "variable", "version", "initial_state_json", "schema_code")))
                put("id", creatorStringSchema("object/variable/version 的稳定 id。"))
                put("content_field", creatorStringSchema(
                    "读取 version 时选择长文本；留空只读版本目录。",
                    listOf("", "initial_state_json", "schema_code"),
                ))
                put("object_cursor", creatorStringSchema("读取 version 对象目录的上一页 cursor。"))
                put("variable_cursor", creatorStringSchema("读取 version 变量目录的上一页 cursor。"))
                put("limit", variablePageLimitSchema())
                put("offset", variableIntegerSchema("长文本起始字符。", 0, null))
                put("max_chars", variableIntegerSchema("单次返回字符数。", 500, 12000, 6000))
            },
        ) { context, arguments ->
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            val versioned = context.service.loadVersionedCreatorVariableConfig(context.workspaceId, rootId)
            val config = versioned.config
            val kind = arguments.creatorString("kind")
            val payload = when (kind) {
                "object" -> config.objects.firstOrNull { it.id == arguments.creatorString("id") }?.fullJson()
                    ?: throw CreatorAuthoringException("OBJECT_NOT_FOUND", "找不到变量对象")
                "variable" -> config.variables.firstOrNull { it.id == arguments.creatorString("id") }?.fullJson()
                    ?: throw CreatorAuthoringException("VARIABLE_NOT_FOUND", "找不到变量项")
                "version" -> {
                    val version = config.versions.firstOrNull { it.id == arguments.creatorString("id") }
                        ?: throw CreatorAuthoringException("VERSION_NOT_FOUND", "找不到变量配置版本")
                    val limit = arguments.creatorInt("limit", 20).coerceIn(1, MaxVariablePageSize)
                    val objects = page(version.objects, arguments.creatorString("object_cursor"), limit)
                    val variables = page(version.variables, arguments.creatorString("variable_cursor"), limit)
                    val field = arguments.creatorString("content_field")
                    val source = when (field) {
                        "" -> ""
                        "initial_state_json" -> version.initialStateJson
                        "schema_code" -> version.schemaCode
                        else -> throw CreatorAuthoringException("INVALID_ARGUMENTS", "content_field 无效：$field")
                    }
                    val offset = arguments.creatorInt("offset", 0).coerceIn(0, source.length)
                    val end = (offset + arguments.creatorInt("max_chars", 6000).coerceIn(500, 12000)).coerceAtMost(source.length)
                    buildJsonObject {
                        put("id", version.id)
                        put("name", version.name)
                        put("createdAt", version.createdAt)
                        put("updatedAt", version.updatedAt)
                        put("active", version.id == config.activeVersionId)
                        put("objects", buildJsonArray { objects.items.forEach { add(it.summaryJson()) } })
                        put("objectsNextCursor", objects.nextCursor)
                        put("variables", buildJsonArray { variables.items.forEach { add(it.summaryJson()) } })
                        put("variablesNextCursor", variables.nextCursor)
                        put("expandedObjectIds", buildJsonArray { version.expandedObjectIds.forEach { add(JsonPrimitive(it)) } })
                        put("contentField", field)
                        put("content", source.substring(offset, end))
                        put("contentOffset", offset)
                        put("contentEnd", end)
                        put("contentLength", source.length)
                        put("nextOffset", if (end < source.length) end else -1)
                        put("hasMore", end < source.length)
                    }
                }
                "initial_state_json", "schema_code" -> {
                    val source = if (kind == "initial_state_json") config.initialStateJson else config.schemaCode
                    val offset = arguments.creatorInt("offset", 0).coerceIn(0, source.length)
                    val end = (offset + arguments.creatorInt("max_chars", 6000).coerceIn(500, 12000)).coerceAtMost(source.length)
                    buildJsonObject {
                        put("field", kind)
                        put("content", source.substring(offset, end))
                        put("offset", offset)
                        put("end", end)
                        put("length", source.length)
                        put("nextOffset", if (end < source.length) end else -1)
                        put("hasMore", end < source.length)
                    }
                }
                else -> throw CreatorAuthoringException("INVALID_ARGUMENTS", "kind 无效：$kind")
            }
            buildJsonObject {
                put("rootId", rootId)
                put("targetName", context.creatorRootDisplayName(rootId))
                put("revision", versioned.revision.toString())
                put("result", payload)
            }
        },
        capability(
            id = "variables.preview_changes",
            title = "预览变量配置修改",
            description = "在内存中校验并预览配置、对象、变量项和版本修改，不保存。",
            effect = CreatorCapabilityEffect.Preview,
            schema = creatorObjectSchema(required = listOf("operations")) {
                put("root_id", creatorStringSchema("可写角色根 id；留空使用主角色。"))
                put("base_revision", creatorStringSchema("可选的读取版本提示；预览始终自动基于最新配置，不会因该值过期而失败。"))
                put(
                    "operations",
                    creatorArraySchema(
                        "有序变量配置修改操作。",
                        variableChangeOperationSchema(),
                        MaxVariablePreviewOperations,
                    ),
                )
            },
        ) { context, arguments ->
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            context.requireCreatorWritableRoot(rootId)
            val versioned = context.service.loadVersionedCreatorVariableConfig(context.workspaceId, rootId)
            val current = versioned.config
            val currentRevision = versioned.revision.toString()
            val operations = arguments.creatorArray("operations")?.mapIndexed { index, element ->
                element as? JsonObject
                    ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "operations[$index] 必须是 object")
            }.orEmpty()
            if (operations.isEmpty()) throw CreatorAuthoringException("INVALID_ARGUMENTS", "operations 不能为空")
            if (operations.size > MaxVariablePreviewOperations) {
                throw CreatorAuthoringException("INVALID_ARGUMENTS", "单次最多预览 $MaxVariablePreviewOperations 个操作")
            }
            val result = applyOperations(current, operations)
            val initialStateExplicitlyChanged = operations.any { operation ->
                operation.creatorString("op") == "set_config" && "initial_state_json" in operation
            }
            val stateShapeChanged = current.stateShape() != result.config.stateShape()
            val nextConfig = result.config.withResolvedInitialState(
                rebuildFromTree = stateShapeChanged && !initialStateExplicitlyChanged,
            )
            validateConfig(nextConfig)
            if (stateShapeChanged || initialStateExplicitlyChanged) {
                validateInitialStateShape(nextConfig)
            }
            if (nextConfig.schemaCode.isNotBlank()) {
                val schemaCheck = context.service.validateCreatorVariableSchema(nextConfig.schemaCode)
                if (!schemaCheck.ok) {
                    throw CreatorAuthoringException(
                        "SCHEMA_VALIDATION_FAILED",
                        listOf(schemaCheck.message, schemaCheck.detail).filter(String::isNotBlank).joinToString("\n"),
                    )
                }
                val stateCheck = context.service.validateCreatorVariableState(
                    schemaCode = nextConfig.schemaCode,
                    stateJson = nextConfig.initialStateJson,
                )
                if (!stateCheck.ok) {
                    throw CreatorAuthoringException(
                        "STATE_VALIDATION_FAILED",
                        listOf(stateCheck.message, stateCheck.detail).filter(String::isNotBlank).joinToString("\n"),
                    )
                }
            }
            val changeSetId = "variable-change-${UUID.randomUUID()}"
            val summary = buildJsonObject {
                put("operationCount", operations.size)
                put("objectCount", nextConfig.objects.size)
                put("variableCount", nextConfig.variables.size)
                put("versionCount", nextConfig.versions.size)
                put("schemaValidated", nextConfig.schemaCode.isNotBlank())
                put("initialStateValidated", nextConfig.schemaCode.isNotBlank())
            }
            context.variableChanges.put(VariablePendingChange(changeSetId, rootId, currentRevision, nextConfig, summary))
            buildJsonObject {
                put("valid", true)
                put("changeSetId", changeSetId)
                put("rootId", rootId)
                put("targetName", context.creatorRootDisplayName(rootId))
                put("baseRevision", currentRevision)
                put("summary", summary)
                put("changes", buildJsonArray { result.descriptions.forEach { add(JsonPrimitive(it)) } })
                put("requiresWritePermission", context.currentPermissionMode() == com.eleckoi.android.engine.agent.api.AgentPermissionMode.AskForApproval)
            }
        },
        capability(
            id = "variables.apply_changes",
            title = "提交变量配置修改",
            description = "提交已预览的变量配置变更集；要求 Workspace Write，并重新检查 revision。",
            effect = CreatorCapabilityEffect.Write,
            schema = creatorObjectSchema(required = listOf("change_set_id")) {
                put("change_set_id", creatorStringSchema("preview_changes 返回的 changeSetId。"))
            },
        ) { context, arguments ->
            context.requireWritePermission()
            val changeSetId = arguments.creatorString("change_set_id")
            val change = context.variableChanges.get(changeSetId)
                ?: throw CreatorAuthoringException("CHANGE_SET_NOT_FOUND", "变更集不存在或当前会话已经重建")
            context.requireCreatorWritableRoot(change.rootId)
            val expectedRevision = change.baseRevision.toLongOrNull()
                ?: throw CreatorAuthoringException("INVALID_CHANGE_SET", "变量变更集 revision 无效")
            val saved = context.service.saveCreatorVariableConfigIfRevision(
                context.workspaceId,
                change.rootId,
                change.nextConfig,
                expectedRevision,
            )
            if (saved == null) {
                context.variableChanges.remove(changeSetId)
                throw CreatorAuthoringException(
                    "REVISION_CONFLICT",
                    "变量配置已经变化，旧变更集没有提交。请重新预览后提交。",
                )
            }
            context.variableChanges.remove(changeSetId)
            buildJsonObject {
                put("status", "applied")
                put("changeSetId", changeSetId)
                put("rootId", change.rootId)
                put("targetName", context.creatorRootDisplayName(change.rootId))
                put("revision", saved.revision.toString())
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
        definition = CreatorOperationDefinition(id, toolset.id, title, description, effect, schema),
        handler = handler,
    )

}
