package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleVersion
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringException
import com.eleckoi.android.feature.studio.authoring.creatorArray
import com.eleckoi.android.feature.studio.authoring.creatorArraySchema
import com.eleckoi.android.feature.studio.authoring.creatorBoolean
import com.eleckoi.android.feature.studio.authoring.creatorBooleanSchema
import com.eleckoi.android.feature.studio.authoring.creatorInt
import com.eleckoi.android.feature.studio.authoring.creatorObjectSchema
import com.eleckoi.android.feature.studio.authoring.creatorRawString
import com.eleckoi.android.feature.studio.authoring.creatorString
import com.eleckoi.android.feature.studio.authoring.creatorStringSchema
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal fun regexRootSchema() = creatorObjectSchema {
    put("root_id", creatorStringSchema("已挂载角色根 id；留空使用主角色。"))
}

internal fun regexPageLimitSchema() = regexIntegerSchema("单页返回数量；最多 50。", 1, MaxRegexPageSize, 20)

internal fun regexChangeOperationSchema() = creatorObjectSchema(required = listOf("op")) {
    put("op", creatorStringSchema("修改类型。", listOf(
        "create_rule", "patch_rule", "delete_rule",
        "create_version", "patch_version", "switch_version", "delete_version",
    )))
    put("id", creatorStringSchema("规则或版本 id；创建时留空由宿主生成。"))
    put("scope", creatorStringSchema("规则作用域。", listOf("global", "prompt_preset", "character")))
    put("name", creatorStringSchema("规则或版本名称。"))
    put("pattern", creatorStringSchema("完整匹配式，可用 /pattern/gims 格式。"))
    put("replacement", creatorStringSchema("完整替换内容。"))
    put("targets", creatorArraySchema("执行目标。", creatorStringSchema("目标。", RegexRuleTarget.entries.map { it.name }), 5))
    put("enabled", creatorBooleanSchema("规则是否启用。"))
    put("display_only", creatorBooleanSchema("只作用于显示投影，不写回原始消息。"))
    put("prompt_only", creatorBooleanSchema("只作用于发送给模型的提示词投影。"))
    put("run_on_edit", creatorBooleanSchema("编辑消息后是否运行。"))
    put("order", regexIntegerSchema("同一作用域内顺序。", 0, null))
    put("global_enabled_ids", creatorArraySchema("版本启用的全局规则 id。", creatorStringSchema("规则 id。"), 1000))
    put("prompt_preset_enabled_ids", creatorArraySchema("版本记录的预设规则 id。", creatorStringSchema("规则 id。"), 1000))
    put("character_enabled_ids", creatorArraySchema("版本启用的角色规则 id。", creatorStringSchema("规则 id。"), 1000))
    put("activate", creatorBooleanSchema("创建版本后是否立即激活。"))
}

internal fun regexIntegerSchema(description: String, minimum: Int?, maximum: Int?, default: Int? = null) = buildJsonObject {
    put("type", "integer"); put("description", description)
    minimum?.let { put("minimum", it) }; maximum?.let { put("maximum", it) }; default?.let { put("default", it) }
}

internal fun applyOperations(source: RegexRuleCollection, operations: List<JsonObject>): RegexApplyResult {
    var collection = source
    val descriptions = mutableListOf<String>()
    operations.forEachIndexed { index, op ->
        when (val kind = op.creatorString("op")) {
            "create_rule" -> {
                val scope = scopeFromApi(op.creatorString("scope"))
                val id = op.creatorString("id").ifBlank { "regex-${UUID.randomUUID()}" }
                if (collection.scopedRules().any { it.rule.id == id }) invalid(index, "规则 id 已存在：$id")
                val rule = RegexRule(
                    id = id,
                    name = op.creatorString("name"),
                    pattern = op.creatorRawString("pattern"),
                    replacement = op.creatorRawString("replacement"),
                    targets = op.targets(index, setOf(RegexRuleTarget.AiOutput)),
                    enabled = op.creatorBoolean("enabled", true),
                    displayOnly = op.creatorBoolean("display_only"),
                    promptOnly = op.creatorBoolean("prompt_only"),
                    runOnEdit = op.creatorBoolean("run_on_edit"),
                    order = op.creatorInt("order", collection.rules(scope).size),
                )
                collection = collection.withRules(scope, collection.rules(scope) + rule).enableInActiveVersion(scope, id, rule.enabled)
                descriptions += "创建正则规则 $id"
            }
            "patch_rule" -> {
                val id = op.requiredId(index)
                val old = collection.scopedRules().firstOrNull { it.rule.id == id } ?: invalid(index, "找不到规则：$id")
                val targetScope = if ("scope" in op) scopeFromApi(op.creatorString("scope")) else old.scope
                val next = old.rule.copy(
                    name = op.stringPatch("name", old.rule.name),
                    pattern = op.rawStringPatch("pattern", old.rule.pattern),
                    replacement = op.rawStringPatch("replacement", old.rule.replacement),
                    targets = if ("targets" in op) op.targets(index, old.rule.targets) else old.rule.targets,
                    enabled = op.booleanPatch("enabled", old.rule.enabled),
                    displayOnly = op.booleanPatch("display_only", old.rule.displayOnly),
                    promptOnly = op.booleanPatch("prompt_only", old.rule.promptOnly),
                    runOnEdit = op.booleanPatch("run_on_edit", old.rule.runOnEdit),
                    order = op.intPatch("order", old.rule.order),
                )
                val withoutOld = collection.withRules(
                    old.scope,
                    collection.rules(old.scope).filterNot { it.id == id },
                )
                collection = withoutOld.withRules(targetScope, withoutOld.rules(targetScope) + next)
                    .moveVersionMembership(old.scope, targetScope, id)
                descriptions += "修改正则规则 $id"
            }
            "delete_rule" -> {
                val id = op.requiredId(index)
                val existing = collection.scopedRules().firstOrNull { it.rule.id == id } ?: invalid(index, "找不到规则：$id")
                collection = collection.withRules(existing.scope, collection.rules(existing.scope).filterNot { it.id == id })
                    .removeFromVersions(id)
                descriptions += "删除正则规则 $id"
            }
            "create_version" -> {
                val id = op.creatorString("id").ifBlank { "regex-version-${UUID.randomUUID()}" }
                if (collection.versions.any { it.id == id }) invalid(index, "版本 id 已存在：$id")
                val version = RegexRuleVersion(
                    id = id,
                    name = op.creatorString("name").ifBlank { "未命名版本" },
                    globalEnabledIds = op.idSetOr("global_enabled_ids") { collection.globalRules.filter { it.enabled }.map { it.id }.toSet() },
                    promptPresetEnabledIds = op.idSetOr("prompt_preset_enabled_ids") { collection.promptPresetRules.filter { it.enabled }.map { it.id }.toSet() },
                    characterEnabledIds = op.idSetOr("character_enabled_ids") { collection.characterRules.filter { it.enabled }.map { it.id }.toSet() },
                )
                collection = collection.copy(
                    versions = collection.versions + version,
                    activeVersionId = if (op.creatorBoolean("activate", true)) id else collection.activeVersionId,
                )
                descriptions += "创建正则版本 $id"
            }
            "patch_version" -> {
                val id = op.requiredId(index)
                val old = collection.versions.firstOrNull { it.id == id } ?: invalid(index, "找不到版本：$id")
                val next = old.copy(
                    name = op.stringPatch("name", old.name),
                    globalEnabledIds = op.idSetPatch("global_enabled_ids", old.globalEnabledIds),
                    promptPresetEnabledIds = op.idSetPatch("prompt_preset_enabled_ids", old.promptPresetEnabledIds),
                    characterEnabledIds = op.idSetPatch("character_enabled_ids", old.characterEnabledIds),
                )
                collection = collection.copy(versions = collection.versions.map { if (it.id == id) next else it })
                descriptions += "修改正则版本 $id"
            }
            "switch_version" -> {
                val id = op.requiredId(index)
                if (collection.versions.none { it.id == id }) invalid(index, "找不到版本：$id")
                collection = collection.copy(activeVersionId = id)
                descriptions += "切换正则版本 $id"
            }
            "delete_version" -> {
                val id = op.requiredId(index)
                if (collection.versions.none { it.id == id }) invalid(index, "找不到版本：$id")
                val remaining = collection.versions.filterNot { it.id == id }
                collection = collection.copy(
                    versions = remaining,
                    activeVersionId = if (collection.activeVersionId == id) remaining.firstOrNull()?.id.orEmpty() else collection.activeVersionId,
                )
                descriptions += "删除正则版本 $id"
            }
            else -> invalid(index, "不支持的操作：$kind")
        }
    }
    collection = collection.copy(
        globalRules = collection.globalRules.sortedBy(RegexRule::order),
        promptPresetRules = collection.promptPresetRules.sortedBy(RegexRule::order),
        characterRules = collection.characterRules.sortedBy(RegexRule::order),
    )
    return RegexApplyResult(collection, descriptions)
}

internal fun validateCollection(collection: RegexRuleCollection) {
    val scoped = collection.scopedRules()
    val ids = scoped.map { it.rule.id }
    if (ids.any(String::isBlank) || ids.distinct().size != ids.size) {
        throw CreatorAuthoringException("VALIDATION_FAILED", "正则规则 id 不能为空且不可重复")
    }
    scoped.forEach { item ->
        if (item.rule.name.isBlank()) throw CreatorAuthoringException("VALIDATION_FAILED", "正则规则名称不能为空：${item.rule.id}")
        if (item.rule.pattern.isBlank()) throw CreatorAuthoringException("VALIDATION_FAILED", "正则匹配式不能为空：${item.rule.id}")
        RegexRuleProcessor.validationMessage(item.rule)?.let { message ->
            throw CreatorAuthoringException("VALIDATION_FAILED", "正则 ${item.rule.id} 无法编译：$message")
        }
        if (item.rule.displayOnly && item.rule.promptOnly) {
            throw CreatorAuthoringException("VALIDATION_FAILED", "正则不能同时 display_only 和 prompt_only：${item.rule.id}")
        }
    }
    val versionIds = collection.versions.map { it.id }
    if (versionIds.any(String::isBlank) || versionIds.distinct().size != versionIds.size) {
        throw CreatorAuthoringException("VALIDATION_FAILED", "正则版本 id 不能为空且不可重复")
    }
    if (collection.activeVersionId.isNotBlank() && collection.activeVersionId !in versionIds) {
        throw CreatorAuthoringException("VALIDATION_FAILED", "活动正则版本不存在")
    }
    // Do not normalize version membership during an unrelated edit. In particular, runtime
    // deliberately ignores ids captured by an older prompt preset while another preset is
    // active; those ids can become valid again after switching presets.
}

internal fun regexAuthoringGuideJson() = buildJsonObject {
    put("pagination", "inspect/search 每次最多 50 条；继续使用 nextCursor，长匹配式/替换正文使用 nextOffset。")
    put("writeWorkflow", buildJsonArray {
        add(JsonPrimitive("先 inspect/read 确认目标和现状，再 preview_changes；预览会自动使用最新快照，编译校验通过后才 apply_changes。"))
        add(JsonPrimitive("不得为了验证工具创建测试正则、占位规则或测试版本。"))
        add(JsonPrimitive("修改时保留作者未要求变化的作用域、目标和投影开关。"))
    })
    put("scopes", buildJsonObject {
        put("global", "全局规则，对所有角色可用。")
        put("prompt_preset", "当前提示词预设携带的规则；切换预设会更换这一组。")
        put("character", "仅属于当前角色的规则。")
    })
    put("targets", buildJsonArray { RegexRuleTarget.entries.forEach { add(buildJsonObject { put("value", it.name); put("label", it.label) }) } })
    put("surfaces", buildJsonObject {
        put("stored", "原始消息持久层；核心对话历史必须保持原文。")
        put("display_only", "只改变显示投影，不写回 Room，也不进入下一轮模型历史。")
        put("prompt_only", "只改变发送给模型的提示词投影。")
        put("default", "既非 display_only 也非 prompt_only 时按目标用于正常处理；设定内容也可进入提示词投影。")
    })
    put("pattern", "支持普通 Kotlin/Java 正则或 /pattern/gims；g 控制替换全部，i/m/s 映射到对应选项。")
    put("versions", "活动版本控制全局和角色规则启用集合；提示词预设规则的运行启用状态随预设本身，不会被旧版本静默清空。")
}

internal data class ScopedRule(val scope: RegexRuleScope, val rule: RegexRule)
internal data class RegexPage<T>(val items: List<T>, val nextCursor: String)
internal data class RegexApplyResult(val collection: RegexRuleCollection, val descriptions: List<String>)

internal fun RegexRuleCollection.scopedRules(): List<ScopedRule> =
    globalRules.map { ScopedRule(RegexRuleScope.Global, it) } +
        promptPresetRules.map { ScopedRule(RegexRuleScope.PromptPreset, it) } +
        characterRules.map { ScopedRule(RegexRuleScope.Character, it) }

private fun RegexRuleCollection.rules(scope: RegexRuleScope): List<RegexRule> = when (scope) {
    RegexRuleScope.Global -> globalRules
    RegexRuleScope.PromptPreset -> promptPresetRules
    RegexRuleScope.Character -> characterRules
}

private fun RegexRuleCollection.withRules(scope: RegexRuleScope, rules: List<RegexRule>): RegexRuleCollection = when (scope) {
    RegexRuleScope.Global -> copy(globalRules = rules)
    RegexRuleScope.PromptPreset -> copy(promptPresetRules = rules)
    RegexRuleScope.Character -> copy(characterRules = rules)
}

private fun RegexRuleCollection.enableInActiveVersion(scope: RegexRuleScope, id: String, enabled: Boolean): RegexRuleCollection {
    if (!enabled || activeVersionId.isBlank() || scope == RegexRuleScope.PromptPreset) return this
    return copy(versions = versions.map { version ->
        if (version.id != activeVersionId) version else when (scope) {
            RegexRuleScope.Global -> version.copy(globalEnabledIds = version.globalEnabledIds + id)
            RegexRuleScope.PromptPreset -> version
            RegexRuleScope.Character -> version.copy(characterEnabledIds = version.characterEnabledIds + id)
        }
    })
}

private fun RegexRuleCollection.moveVersionMembership(
    oldScope: RegexRuleScope,
    newScope: RegexRuleScope,
    id: String,
): RegexRuleCollection {
    if (oldScope == newScope) return this
    return copy(versions = versions.map { version ->
        val wasEnabled = id in when (oldScope) {
            RegexRuleScope.Global -> version.globalEnabledIds
            RegexRuleScope.PromptPreset -> version.promptPresetEnabledIds
            RegexRuleScope.Character -> version.characterEnabledIds
        }
        val cleared = version.copy(
            globalEnabledIds = version.globalEnabledIds - id,
            promptPresetEnabledIds = version.promptPresetEnabledIds - id,
            characterEnabledIds = version.characterEnabledIds - id,
        )
        if (!wasEnabled) cleared else when (newScope) {
            RegexRuleScope.Global -> cleared.copy(globalEnabledIds = cleared.globalEnabledIds + id)
            RegexRuleScope.PromptPreset -> cleared.copy(promptPresetEnabledIds = cleared.promptPresetEnabledIds + id)
            RegexRuleScope.Character -> cleared.copy(characterEnabledIds = cleared.characterEnabledIds + id)
        }
    })
}

private fun RegexRuleCollection.removeFromVersions(id: String): RegexRuleCollection = copy(
    versions = versions.map { version -> version.copy(
        globalEnabledIds = version.globalEnabledIds - id,
        promptPresetEnabledIds = version.promptPresetEnabledIds - id,
        characterEnabledIds = version.characterEnabledIds - id,
    ) },
)

internal fun ScopedRule.summaryJson() = buildJsonObject {
    put("id", rule.id); put("scope", scope.toApi()); put("name", rule.name); put("enabled", rule.enabled)
    put("targets", buildJsonArray { rule.targets.forEach { add(JsonPrimitive(it.name)) } })
    put("displayOnly", rule.displayOnly); put("promptOnly", rule.promptOnly); put("runOnEdit", rule.runOnEdit)
    put("order", rule.order); put("patternLength", rule.pattern.length); put("replacementLength", rule.replacement.length)
}

internal fun ScopedRule.fullJson(field: String, content: String) = buildJsonObject {
    put("id", rule.id); put("scope", scope.toApi()); put("name", rule.name); put("contentField", field); put("content", content)
    put("targets", buildJsonArray { rule.targets.forEach { add(JsonPrimitive(it.name)) } })
    put("enabled", rule.enabled); put("displayOnly", rule.displayOnly); put("promptOnly", rule.promptOnly)
    put("runOnEdit", rule.runOnEdit); put("order", rule.order)
}

internal fun RegexRuleVersion.summaryJson() = buildJsonObject {
    put("id", id); put("name", name)
    put("globalEnabledCount", globalEnabledIds.size); put("promptPresetEnabledCount", promptPresetEnabledIds.size)
    put("characterEnabledCount", characterEnabledIds.size)
}

internal fun <T> regexPage(items: List<T>, cursor: String, limit: Int): RegexPage<T> {
    val offset = cursor.toIntOrNull()?.coerceIn(0, items.size) ?: 0
    val result = items.drop(offset).take(limit)
    val next = offset + result.size
    return RegexPage(result, if (next < items.size) next.toString() else "")
}

internal fun scopeFromApi(value: String): RegexRuleScope = when (value) {
    "global" -> RegexRuleScope.Global
    "prompt_preset" -> RegexRuleScope.PromptPreset
    "character" -> RegexRuleScope.Character
    else -> throw CreatorAuthoringException("INVALID_ARGUMENTS", "scope 无效：$value")
}

internal fun RegexRuleScope.toApi(): String = when (this) {
    RegexRuleScope.Global -> "global"
    RegexRuleScope.PromptPreset -> "prompt_preset"
    RegexRuleScope.Character -> "character"
}

private fun JsonObject.requiredId(index: Int): String = creatorString("id").ifBlank { invalid(index, "id 不能为空") }
private fun JsonObject.stringPatch(name: String, old: String): String = if (name in this) creatorString(name) else old
private fun JsonObject.rawStringPatch(name: String, old: String): String = if (name in this) creatorRawString(name) else old
private fun JsonObject.booleanPatch(name: String, old: Boolean): Boolean = if (name in this) creatorBoolean(name, old) else old
private fun JsonObject.intPatch(name: String, old: Int): Int = if (name in this) creatorInt(name, old) else old
private fun JsonObject.targets(index: Int, default: Set<RegexRuleTarget>): Set<RegexRuleTarget> {
    if ("targets" !in this) return default
    val values = creatorArray("targets").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    val targets = values.map { raw -> RegexRuleTarget.entries.firstOrNull { it.name == raw } ?: invalid(index, "target 无效：$raw") }.toSet()
    return targets.takeIf { it.isNotEmpty() } ?: invalid(index, "targets 不能为空")
}
private fun JsonObject.idSetPatch(name: String, old: Set<String>): Set<String> = if (name in this) idSet(name) else old
private fun JsonObject.idSetOr(name: String, fallback: () -> Set<String>): Set<String> = if (name in this) idSet(name) else fallback()
private fun JsonObject.idSet(name: String): Set<String> = creatorArray(name).orEmpty().mapNotNull {
    (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
}.toSet()

private fun invalid(index: Int, message: String): Nothing =
    throw CreatorAuthoringException("INVALID_ARGUMENTS", "operations[$index] $message")
