package com.eleckoi.android.feature.characters.modes.story.regex.data

import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.stringOrEmpty
import org.json.JSONArray
import org.json.JSONObject

/** Shared lossless representation used by regex storage and agent-preset cards. */
internal object RegexRuleJsonCodec {
    fun encodeRules(rules: List<RegexRule>): String = JSONArray(rules.map(::ruleToJson)).toString()

    fun decodeRules(json: String?): List<RegexRule> {
        if (json.isNullOrBlank()) return emptyList()
        val values = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        return decodeRules(values)
    }

    fun decodeRules(values: JSONArray?): List<RegexRule> = values?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { value ->
                    ruleFromJson(value, index).takeIf { it.pattern.isNotBlank() }?.let(::add)
                }
            }
        }
    }.orEmpty()

    fun ruleToJson(rule: RegexRule): JSONObject = JSONObject()
        .put("id", rule.id)
        .put("name", rule.name)
        .put("pattern", rule.pattern)
        .put("replacement", rule.replacement)
        .put("targets", JSONArray(rule.targets.map(RegexRuleTarget::name)))
        .put("enabled", rule.enabled)
        .put("display_only", rule.displayOnly)
        .put("prompt_only", rule.promptOnly)
        .put("run_on_edit", rule.runOnEdit)
        .put("order", rule.order)

    fun ruleFromJson(value: JSONObject, index: Int): RegexRule = RegexRule(
        id = value.stringOrEmpty("id").ifBlank { "regex-${newId(10)}" },
        name = value.stringOrEmpty("name").ifBlank { value.stringOrEmpty("scriptName") },
        pattern = value.stringOrEmpty("pattern").ifBlank { value.stringOrEmpty("findRegex") },
        replacement = value.stringOrEmpty("replacement").ifBlank { value.stringOrEmpty("replaceString") },
        targets = targetsFrom(value),
        enabled = !value.optBoolean("disabled", false) && value.optBoolean("enabled", true),
        displayOnly = value.optBoolean("display_only", value.optBoolean("markdownOnly", false)),
        promptOnly = value.optBoolean("prompt_only", value.optBoolean("promptOnly", false)),
        runOnEdit = value.optBoolean("run_on_edit", value.optBoolean("runOnEdit", false)),
        order = value.optInt("order", index),
    )

    private fun targetsFrom(value: JSONObject): Set<RegexRuleTarget> {
        val named = value.optJSONArray("targets")?.let { values ->
            buildSet {
                for (index in 0 until values.length()) {
                    runCatching { RegexRuleTarget.valueOf(values.optString(index)) }.getOrNull()?.let(::add)
                }
            }
        }.orEmpty()
        if (named.isNotEmpty()) return named
        val placements = value.optJSONArray("placement")?.let { values ->
            buildSet {
                for (index in 0 until values.length()) add(values.optInt(index, -1))
            }
        }.orEmpty()
        return buildSet {
            if (1 in placements) add(RegexRuleTarget.UserInput)
            if (2 in placements || placements.isEmpty()) add(RegexRuleTarget.AiOutput)
            if (3 in placements) add(RegexRuleTarget.SlashCommand)
            if (5 in placements) add(RegexRuleTarget.SettingContent)
            if (6 in placements) add(RegexRuleTarget.Reasoning)
        }
    }
}

/** Depth-limited Tavern regexes cannot be made unconditional without changing their meaning. */
internal fun JSONObject.hasUnsupportedRegexDepth(): Boolean =
    listOf("minDepth", "maxDepth", "min_depth", "max_depth").any { key ->
        if (!has(key) || isNull(key)) return@any false
        val value = opt(key)
        value != null && value !== JSONObject.NULL && (value !is String || value.isNotBlank())
    }
