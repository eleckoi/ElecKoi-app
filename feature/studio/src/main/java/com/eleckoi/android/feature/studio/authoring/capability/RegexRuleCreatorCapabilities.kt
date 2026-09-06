package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.engine.creator.capability.CreatorCapability
import com.eleckoi.android.engine.creator.capability.CreatorCapabilityEffect
import com.eleckoi.android.engine.creator.capability.CreatorOperationDefinition
import com.eleckoi.android.engine.creator.capability.CreatorToolsetDefinition
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
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

private const val MaxRegexPreviewOperations = 100
internal const val MaxRegexPageSize = 50

internal data class RegexRulePendingChange(
    val id: String,
    val rootId: String,
    val baseRevision: String,
    val nextCollection: RegexRuleCollection,
    val summary: JsonObject,
)

internal class RegexRuleCreatorChangeStore {
    private val pending = ConcurrentHashMap<String, RegexRulePendingChange>()

    fun put(change: RegexRulePendingChange) {
        pending[change.id] = change
    }

    fun get(id: String): RegexRulePendingChange? = pending[id]

    fun remove(id: String): RegexRulePendingChange? = pending.remove(id)
}

internal object RegexRuleCreatorCapabilities {
    val toolset = CreatorToolsetDefinition(
        id = "creator.regex_rules",
        title = "角色正则规则",
        description = "分页查看、搜索并完整修改全局、提示词预设和角色正则规则及其版本。",
    )

    fun capabilities(): List<CreatorCapability<CreatorAuthoringContext, CreatorOperationDefinition>> = listOf(
        capability(
            id = "regex_rules.get_authoring_guide",
            title = "读取正则规则技术说明",
            description = "返回作用域、执行目标、显示/提示词投影和版本启用语义。",
            schema = regexRootSchema(),
        ) { context, arguments ->
            context.resolveCreatorRootId(arguments.creatorString("root_id"))
            regexAuthoringGuideJson()
        },
        capability(
            id = "regex_rules.inspect",
            title = "查看正则规则目录",
            description = "分页返回三个作用域的规则摘要和版本摘要，不返回完整匹配式或替换正文。",
            schema = creatorObjectSchema {
                put("root_id", creatorStringSchema("已挂载角色根 id；留空使用主角色。"))
                put("rule_cursor", creatorStringSchema("上一页 rulesNextCursor；首页留空。"))
                put("version_cursor", creatorStringSchema("上一页 versionsNextCursor；首页留空。"))
                put("limit", regexPageLimitSchema())
            },
        ) { context, arguments ->
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            val versioned = context.service.loadVersionedCreatorRegexRules(context.workspaceId, rootId)
            val collection = versioned.collection
            val limit = arguments.creatorInt("limit", 20).coerceIn(1, MaxRegexPageSize)
            val rules = regexPage(collection.scopedRules(), arguments.creatorString("rule_cursor"), limit)
            val versions = regexPage(collection.versions, arguments.creatorString("version_cursor"), limit)
            buildJsonObject {
                put("rootId", rootId)
                put("revision", versioned.revision.toString())
                put("activeVersionId", collection.activeVersionId)
                put("globalRuleCount", collection.globalRules.size)
                put("promptPresetRuleCount", collection.promptPresetRules.size)
                put("characterRuleCount", collection.characterRules.size)
                put("versionCount", collection.versions.size)
                put("rules", buildJsonArray { rules.items.forEach { add(it.summaryJson()) } })
                put("rulesNextCursor", rules.nextCursor)
                put("versions", buildJsonArray { versions.items.forEach { add(it.summaryJson()) } })
                put("versionsNextCursor", versions.nextCursor)
            }
        },
        capability(
            id = "regex_rules.search",
            title = "搜索正则规则",
            description = "分页搜索规则名称、匹配式与替换内容，可限制作用域。",
            schema = creatorObjectSchema(required = listOf("query")) {
                put("root_id", creatorStringSchema("角色根 id；留空使用主角色。"))
                put("query", creatorStringSchema("不区分大小写的搜索文本。"))
                put("scope", creatorStringSchema("可选作用域。", listOf("", "global", "prompt_preset", "character")))
                put("cursor", creatorStringSchema("上一页 nextCursor；首页留空。"))
                put("limit", regexPageLimitSchema())
            },
        ) { context, arguments ->
            val query = arguments.creatorString("query").takeIf(String::isNotBlank)
                ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "query 不能为空")
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            val versioned = context.service.loadVersionedCreatorRegexRules(context.workspaceId, rootId)
            val collection = versioned.collection
            val requestedScope = arguments.creatorString("scope").takeIf(String::isNotBlank)?.let(::scopeFromApi)
            val matches = collection.scopedRules().filter { item ->
                (requestedScope == null || item.scope == requestedScope) &&
                    listOf(item.rule.name, item.rule.pattern, item.rule.replacement).any { it.contains(query, ignoreCase = true) }
            }
            val result = regexPage(
                matches,
                arguments.creatorString("cursor"),
                arguments.creatorInt("limit", 20).coerceIn(1, MaxRegexPageSize),
            )
            buildJsonObject {
                put("rootId", rootId)
                put("query", query)
                put("matches", buildJsonArray { result.items.forEach { add(it.summaryJson()) } })
                put("nextCursor", result.nextCursor)
                put("hasMore", result.nextCursor.isNotBlank())
            }
        },
        capability(
            id = "regex_rules.read_rule",
            title = "读取完整正则规则",
            description = "读取规则全部开关和目标，并按字符区间读取匹配式或替换内容。",
            schema = creatorObjectSchema(required = listOf("rule_id")) {
                put("root_id", creatorStringSchema("角色根 id；留空使用主角色。"))
                put("rule_id", creatorStringSchema("inspect/search 返回的规则 id。"))
                put("content_field", creatorStringSchema("分页读取字段。", listOf("pattern", "replacement")))
                put("offset", regexIntegerSchema("长文本起始字符。", 0, null))
                put("max_chars", regexIntegerSchema("单次返回字符数。", 500, 12000, 6000))
            },
        ) { context, arguments ->
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            val versioned = context.service.loadVersionedCreatorRegexRules(context.workspaceId, rootId)
            val collection = versioned.collection
            val item = collection.scopedRules().firstOrNull { it.rule.id == arguments.creatorString("rule_id") }
                ?: throw CreatorAuthoringException("RULE_NOT_FOUND", "找不到正则规则")
            val field = arguments.creatorString("content_field").ifBlank { "pattern" }
            val source = when (field) {
                "pattern" -> item.rule.pattern
                "replacement" -> item.rule.replacement
                else -> throw CreatorAuthoringException("INVALID_ARGUMENTS", "content_field 无效：$field")
            }
            val offset = arguments.creatorInt("offset", 0).coerceIn(0, source.length)
            val end = (offset + arguments.creatorInt("max_chars", 6000).coerceIn(500, 12000)).coerceAtMost(source.length)
            buildJsonObject {
                put("rootId", rootId)
                put("revision", versioned.revision.toString())
                put("rule", item.fullJson(field, source.substring(offset, end)))
                put("contentOffset", offset)
                put("contentEnd", end)
                put("contentLength", source.length)
                put("nextOffset", if (end < source.length) end else -1)
                put("hasMore", end < source.length)
            }
        },
        capability(
            id = "regex_rules.read_version",
            title = "读取完整正则版本",
            description = "按作用域分页读取一个版本启用的规则 id，避免一次返回超大集合。",
            schema = creatorObjectSchema(required = listOf("version_id")) {
                put("root_id", creatorStringSchema("角色根 id；留空使用主角色。"))
                put("version_id", creatorStringSchema("inspect 返回的版本 id。"))
                put("scope", creatorStringSchema("要读取的启用集合。", listOf("global", "prompt_preset", "character")))
                put("cursor", creatorStringSchema("上一页 nextCursor；首页留空。"))
                put("limit", regexPageLimitSchema())
            },
        ) { context, arguments ->
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            val versioned = context.service.loadVersionedCreatorRegexRules(context.workspaceId, rootId)
            val collection = versioned.collection
            val version = collection.versions.firstOrNull { it.id == arguments.creatorString("version_id") }
                ?: throw CreatorAuthoringException("VERSION_NOT_FOUND", "找不到正则版本")
            val scope = scopeFromApi(arguments.creatorString("scope").ifBlank { "global" })
            val enabledIds = when (scope) {
                RegexRuleScope.Global -> version.globalEnabledIds
                RegexRuleScope.PromptPreset -> version.promptPresetEnabledIds
                RegexRuleScope.Character -> version.characterEnabledIds
            }.sorted()
            val result = regexPage(
                enabledIds,
                arguments.creatorString("cursor"),
                arguments.creatorInt("limit", 20).coerceIn(1, MaxRegexPageSize),
            )
            buildJsonObject {
                put("rootId", rootId)
                put("revision", versioned.revision.toString())
                put("versionId", version.id)
                put("name", version.name)
                put("active", version.id == collection.activeVersionId)
                put("scope", scope.toApi())
                put("enabledRuleIds", buildJsonArray { result.items.forEach { add(JsonPrimitive(it)) } })
                put("nextCursor", result.nextCursor)
                put("hasMore", result.nextCursor.isNotBlank())
            }
        },
        capability(
            id = "regex_rules.preview_changes",
            title = "预览正则规则修改",
            description = "在内存中编译校验并预览规则、作用域和版本修改，不保存。",
            effect = CreatorCapabilityEffect.Preview,
            schema = creatorObjectSchema(required = listOf("operations")) {
                put("root_id", creatorStringSchema("可写角色根 id；留空使用主角色。"))
                put("base_revision", creatorStringSchema("可选的读取版本提示；预览始终自动基于最新规则，不会因该值过期而失败。"))
                put(
                    "operations",
                    creatorArraySchema(
                        "有序正则修改操作。",
                        regexChangeOperationSchema(),
                        MaxRegexPreviewOperations,
                    ),
                )
            },
        ) { context, arguments ->
            val rootId = context.resolveCreatorRootId(arguments.creatorString("root_id"))
            context.requireCreatorWritableRoot(rootId)
            val versioned = context.service.loadVersionedCreatorRegexRules(context.workspaceId, rootId)
            val current = versioned.collection
            val currentRevision = versioned.revision.toString()
            val operations = arguments.creatorArray("operations")?.mapIndexed { index, element ->
                element as? JsonObject
                    ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "operations[$index] 必须是 object")
            }.orEmpty()
            if (operations.isEmpty()) throw CreatorAuthoringException("INVALID_ARGUMENTS", "operations 不能为空")
            if (operations.size > MaxRegexPreviewOperations) {
                throw CreatorAuthoringException("INVALID_ARGUMENTS", "单次最多预览 $MaxRegexPreviewOperations 个操作")
            }
            val result = applyOperations(current, operations)
            validateCollection(result.collection)
            val changeSetId = "regex-change-${UUID.randomUUID()}"
            val summary = buildJsonObject {
                put("operationCount", operations.size)
                put("globalRuleCount", result.collection.globalRules.size)
                put("promptPresetRuleCount", result.collection.promptPresetRules.size)
                put("characterRuleCount", result.collection.characterRules.size)
                put("versionCount", result.collection.versions.size)
            }
            context.regexRuleChanges.put(RegexRulePendingChange(changeSetId, rootId, currentRevision, result.collection, summary))
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
            id = "regex_rules.apply_changes",
            title = "提交正则规则修改",
            description = "提交已预览的正则变更集；要求 Workspace Write，并重新检查 revision。",
            effect = CreatorCapabilityEffect.Write,
            schema = creatorObjectSchema(required = listOf("change_set_id")) {
                put("change_set_id", creatorStringSchema("preview_changes 返回的 changeSetId。"))
            },
        ) { context, arguments ->
            context.requireWritePermission()
            val changeSetId = arguments.creatorString("change_set_id")
            val change = context.regexRuleChanges.get(changeSetId)
                ?: throw CreatorAuthoringException("CHANGE_SET_NOT_FOUND", "变更集不存在或当前会话已经重建")
            context.requireCreatorWritableRoot(change.rootId)
            val expectedRevision = change.baseRevision.toLongOrNull()
                ?: throw CreatorAuthoringException("INVALID_CHANGE_SET", "正则变更集 revision 无效")
            val saved = context.service.saveCreatorRegexRulesIfRevision(
                context.workspaceId,
                change.rootId,
                change.nextCollection,
                expectedRevision,
            )
            if (saved == null) {
                context.regexRuleChanges.remove(changeSetId)
                throw CreatorAuthoringException("REVISION_CONFLICT", "正则规则已经变化，旧变更集没有提交")
            }
            context.regexRuleChanges.remove(changeSetId)
            buildJsonObject {
                put("status", "applied")
                put("changeSetId", changeSetId)
                put("rootId", change.rootId)
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
