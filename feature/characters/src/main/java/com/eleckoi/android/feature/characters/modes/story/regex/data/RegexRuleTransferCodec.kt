package com.eleckoi.android.feature.characters.modes.story.regex.data

import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportDocument
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import org.json.JSONArray
import org.json.JSONObject

internal data class DecodedRegexImportDocuments(
    val rules: List<ScopedRegexRule>,
    val importedFileCount: Int,
    val failedFileNames: List<String>,
)

internal fun decodeRegexImportDocuments(
    documents: List<RegexRuleImportDocument>,
    fallbackScope: RegexRuleScope,
): DecodedRegexImportDocuments {
    val failedFileNames = mutableListOf<String>()
    val rules = mutableListOf<ScopedRegexRule>()
    var importedFileCount = 0
    documents.forEach { document ->
        runCatching { RegexRuleImportCodec.decodeScopedRules(document.json, fallbackScope) }
            .onSuccess { decoded ->
                if (decoded.isNotEmpty()) {
                    rules += decoded
                    importedFileCount += 1
                }
            }
            .onFailure { failedFileNames += document.displayName }
    }
    return DecodedRegexImportDocuments(rules, importedFileCount, failedFileNames)
}

internal fun RegexRuleCollection.includeImportedRulesInActiveVersion(
    importedRules: List<ScopedRegexRule>,
): RegexRuleCollection {
    if (activeVersionId.isBlank() || importedRules.isEmpty()) return this
    val activeVersion = versions.firstOrNull { it.id == activeVersionId } ?: return this
    val enabledByScope = importedRules
        .filter { it.rule.enabled }
        .groupBy(ScopedRegexRule::scope) { it.rule.id }
    return copy(
        versions = versions.map { version ->
            if (version.id != activeVersion.id) return@map version
            version.copy(
                globalEnabledIds = version.globalEnabledIds + enabledByScope[RegexRuleScope.Global].orEmpty(),
                promptPresetEnabledIds = version.promptPresetEnabledIds +
                    enabledByScope[RegexRuleScope.PromptPreset].orEmpty(),
                characterEnabledIds = version.characterEnabledIds +
                    enabledByScope[RegexRuleScope.Character].orEmpty(),
            )
        },
    )
}

object RegexRuleImportCodec {
    fun decode(json: String): List<RegexRule> {
        return decodeScoped(json, RegexRuleScope.Global).map(ScopedRegexRule::rule)
    }

    fun decodeScoped(json: String, fallbackScope: RegexRuleScope): List<ScopedRegexRule> {
        return decodeScopedRules(json, fallbackScope)
            .ifEmpty { throw ElecKoiDataException("文件里没有有效规则") }
    }

    internal fun decodeScopedRules(
        json: String,
        fallbackScope: RegexRuleScope,
    ): List<ScopedRegexRule> {
        val trimmed = json.trim()
        val root = runCatching { JSONObject(trimmed) }.getOrNull()
        val values = when {
            root != null -> root.optJSONArray("rules")
                ?: root.optJSONArray("regex_scripts")
                ?: root.optJSONArray("regex")
                ?: root.takeIf { it.has("pattern") || it.has("findRegex") }?.let { JSONArray().put(it) }
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull()
            else -> null
        } ?: throw ElecKoiDataException("文件里没有正则规则")
        return (0 until values.length()).mapNotNull { index ->
            values.optJSONObject(index)?.let { item ->
                val pattern = item.strictString("pattern").ifEmpty { item.strictString("findRegex") }
                if (pattern.isBlank()) return@let null
                ScopedRegexRule(
                    scope = importedScope(item.strictString("scope"), fallbackScope),
                    rule = RegexRule(
                        name = item.strictString("name")
                            .ifEmpty { item.strictString("scriptName") }
                            .trim()
                            .take(60),
                        pattern = pattern.take(4_000),
                        replacement = item.strictString("replacement")
                            .ifEmpty { item.strictString("replaceString") },
                        targets = importedTargets(item),
                        enabled = !item.strictBoolean("disabled") && item.strictBoolean("enabled", true),
                        displayOnly = item.strictBoolean(
                            "display_only",
                            item.strictBoolean("markdownOnly"),
                        ),
                        promptOnly = item.strictBoolean("prompt_only", item.strictBoolean("promptOnly")),
                        runOnEdit = item.strictBoolean("run_on_edit", item.strictBoolean("runOnEdit")),
                        order = index,
                    ),
                )
            }
        }
    }
}

data class ScopedRegexRule(val scope: RegexRuleScope, val rule: RegexRule)

private fun importedTargets(item: JSONObject): Set<RegexRuleTarget> {
    val named = item.optJSONArray("targets")?.let { values ->
        (0 until values.length()).mapNotNull { index ->
            (values.opt(index) as? String)?.let { name ->
                runCatching { RegexRuleTarget.valueOf(name) }.getOrNull()
            }
        }.toSet()
    }.orEmpty()
    return named.ifEmpty { placementTargets(item.optJSONArray("placement")) }
}

private fun placementTargets(placement: JSONArray?): Set<RegexRuleTarget> {
    val values = placement?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.opt(index).strictIntegerOrNull() }.toSet()
    }.orEmpty()
    return buildSet {
        if (1 in values) add(RegexRuleTarget.UserInput)
        if (2 in values || values.isEmpty()) add(RegexRuleTarget.AiOutput)
        if (3 in values) add(RegexRuleTarget.SlashCommand)
        if (5 in values) add(RegexRuleTarget.SettingContent)
        if (6 in values) add(RegexRuleTarget.Reasoning)
    }
}

private fun importedScope(value: String, fallback: RegexRuleScope): RegexRuleScope = when (value) {
    "Global" -> RegexRuleScope.Global
    "AgentPreset", "PromptPreset" -> RegexRuleScope.PromptPreset
    "Character" -> RegexRuleScope.Character
    else -> fallback
}

internal fun RegexRuleScope.transferName(): String = when (this) {
    RegexRuleScope.Global -> "Global"
    RegexRuleScope.PromptPreset -> "AgentPreset"
    RegexRuleScope.Character -> "Character"
}

internal fun encodeRegexExport(rules: List<ScopedRegexRule>): String = JSONObject()
    .put("format", "eleckoi.regex-rules-export")
    .put("version", 2)
    .put(
        "rules",
        JSONArray(rules.map { (scope, rule) ->
            RegexRuleJsonCodec.ruleToJson(rule)
                .put("displayOnly", rule.displayOnly)
                .put("promptOnly", rule.promptOnly)
                .put("runOnEdit", rule.runOnEdit)
                .put("scope", scope.transferName())
        }),
    )
    .toString(2)

private fun JSONObject.strictString(key: String): String = opt(key) as? String ?: ""

private fun JSONObject.strictBoolean(key: String, fallback: Boolean = false): Boolean =
    (opt(key) as? Boolean) ?: fallback

private fun Any?.strictIntegerOrNull(): Int? {
    val number = this as? Number ?: return null
    val doubleValue = number.toDouble()
    if (!doubleValue.isFinite() || doubleValue % 1.0 != 0.0) return null
    if (doubleValue < Int.MIN_VALUE || doubleValue > Int.MAX_VALUE) return null
    return doubleValue.toInt()
}
