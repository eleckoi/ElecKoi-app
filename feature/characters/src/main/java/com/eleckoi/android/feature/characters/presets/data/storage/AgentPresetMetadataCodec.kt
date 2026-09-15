package com.eleckoi.android.feature.characters.presets.data.storage

import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelFamily
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetProfile
import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan
import com.eleckoi.android.feature.characters.presets.model.AgentPresetTimelineItem
import com.eleckoi.android.feature.characters.presets.model.AgentPresetToolConfiguration
import com.eleckoi.android.feature.characters.presets.model.toTag
import org.json.JSONArray
import org.json.JSONObject

internal object AgentPresetMetadataCodec {
    private const val ToolConfigurationVersion = 4

    fun encodeModelTags(tags: List<AgentPresetModelTag>): String = JSONArray().apply {
        tags.forEach { tag ->
            put(
                JSONObject()
                    .put("id", tag.id)
                    .put("label", tag.label)
                    .put("providerId", tag.providerId),
            )
        }
    }.toString()

    fun decodeModelTags(json: String, fallbackFamily: String): List<AgentPresetModelTag> {
        val tags = runCatching { JSONArray(json) }.getOrDefault(JSONArray()).let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    val id = value.optString("id").trim()
                    val label = value.optString("label").trim()
                    if (id.isNotBlank() && label.isNotBlank()) {
                        add(AgentPresetModelTag(id, label, value.optString("providerId").trim()))
                    }
                }
            }
        }
        return tags.ifEmpty { listOf(AgentPresetModelFamily.fromStorage(fallbackFamily).toTag()) }
    }

    fun encodeTimeline(items: List<AgentPresetTimelineItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("dateLabel", item.dateLabel)
                    .put("note", item.note),
            )
        }
    }.toString()

    fun encodeToolConfiguration(
        config: AgentPresetToolConfiguration,
        roleplayPlan: AgentPresetRoleplayPlan,
    ): String = config.normalized().let { value ->
        JSONObject()
            .put("version", ToolConfigurationVersion)
            .put("includedGroupIds", JSONArray(value.includedGroupIds))
            .put("enabledGroupIds", JSONArray(value.enabledGroupIds.toList()))
            .put(
                "subagentModelSelection",
                JSONObject()
                    .put("configId", value.subagentModelConfigId)
                    .put("model", value.subagentModel),
            )
            .put("roleplayPlan", roleplayPlan.normalized().toJson())
            .put("toolModelConfigIds", JSONObject(value.toolModelConfigIds))
            .toString()
    }

    fun decodeToolConfiguration(json: String): AgentPresetToolConfiguration {
        val stored = decodeStoredToolConfiguration(json)
        return AgentPresetToolConfiguration(
            includedGroupIds = stored.includedGroupIds,
            enabledGroupIds = stored.enabledGroupIds.toSet(),
            subagentModelConfigId = stored.subagentModelConfigId,
            subagentModel = stored.subagentModel,
            toolModelConfigIds = stored.toolModelConfigIds,
        ).normalized()
    }

    fun decodeRoleplayPlan(json: String): AgentPresetRoleplayPlan =
        AgentPresetRoleplayPlan(decodeStoredToolConfiguration(json).roleplayPlanSteps).normalized()

    fun decodeProfile(
        authorName: String,
        authorAvatarPath: String,
        usageInstructions: String,
        timelineJson: String,
    ): AgentPresetProfile = AgentPresetProfile(
        authorName = authorName,
        authorAvatarPath = authorAvatarPath,
        usageInstructions = usageInstructions,
        timeline = decodeTimeline(timelineJson),
    )

    private fun decodeTimeline(json: String): List<AgentPresetTimelineItem> =
        runCatching { JSONArray(json) }.getOrDefault(JSONArray()).let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    val title = value.optString("title").trim()
                    if (title.isNotBlank()) {
                        add(
                            AgentPresetTimelineItem(
                                id = value.optString("id").ifBlank { "timeline-$index" },
                                title = title,
                                dateLabel = value.optString("dateLabel").trim(),
                                note = value.optString("note").trim(),
                            ),
                        )
                    }
                }
            }
        }

    private fun decodeStoredToolConfiguration(json: String): StoredToolConfiguration {
        if (json.isBlank()) throw IllegalStateException("预设缺少工具配置。")
        return try {
            val value = JSONObject(json)
            value.requireExactKeys(
                label = "预设工具配置",
                expected = setOf(
                    "version",
                    "includedGroupIds",
                    "enabledGroupIds",
                    "subagentModelSelection",
                    "roleplayPlan",
                    "toolModelConfigIds",
                ),
            )
            check(value.getInt("version") == ToolConfigurationVersion) {
                "预设工具配置版本无效。"
            }
            val subagentSelection = value.getJSONObject("subagentModelSelection").also {
                it.requireExactKeys("子 Agent 模型配置", setOf("configId", "model"))
            }
            val roleplayPlan = value.getJSONObject("roleplayPlan").also {
                it.requireExactKeys("角色扮演计划", setOf("steps"))
            }
            val roleplayPlanSteps = roleplayPlan.getJSONArray("steps").strictStringList("角色扮演计划")
            check(roleplayPlanSteps.isNotEmpty() && roleplayPlanSteps.size <= 20) {
                "角色扮演计划必须包含 1 至 20 项。"
            }
            check(roleplayPlanSteps.none { it.isBlank() || it.length > 2_000 }) {
                "角色扮演计划包含空项或过长内容。"
            }
            StoredToolConfiguration(
                includedGroupIds = value.getJSONArray("includedGroupIds")
                    .strictStringList("预设包含的工具组"),
                enabledGroupIds = value.getJSONArray("enabledGroupIds")
                    .strictStringList("预设启用的工具组"),
                subagentModelConfigId = subagentSelection.strictString("configId", "子 Agent 配置 ID"),
                subagentModel = subagentSelection.strictString("model", "子 Agent 模型"),
                roleplayPlanSteps = roleplayPlanSteps,
                toolModelConfigIds = value.getJSONObject("toolModelConfigIds")
                    .strictStringMap("工具模型配置"),
            )
        } catch (error: Throwable) {
            if (error is IllegalStateException && error.message == "预设缺少工具配置。") throw error
            throw IllegalStateException("预设工具配置已损坏。", error)
        }
    }

    private fun JSONArray?.stringList(): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }

    private fun JSONArray.strictStringList(label: String): List<String> = buildList<String> {
        for (index in 0 until length()) {
            val item: Any? = this@strictStringList.get(index)
            check(item is String) { "$label 的第 ${index + 1} 项必须是文本。" }
            add(item)
        }
    }

    private fun JSONObject.strictString(key: String, label: String): String {
        val item = get(key)
        check(item is String) { "$label 必须是文本。" }
        return item
    }

    private fun JSONObject.strictStringMap(label: String): Map<String, String> = buildMap<String, String> {
        keys().forEach { key ->
            val item: Any? = this@strictStringMap.get(key)
            check(item is String) { "$label 的 $key 必须是文本。" }
            put(key, item)
        }
    }

    private fun JSONObject.requireExactKeys(label: String, expected: Set<String>) {
        val actual = keys().asSequence().toSet()
        check(actual == expected) { "$label 字段不完整或包含未知字段。" }
    }

    private fun AgentPresetRoleplayPlan.toJson(): JSONObject = JSONObject()
        .put("steps", JSONArray(steps))

    private data class StoredToolConfiguration(
        val includedGroupIds: List<String>,
        val enabledGroupIds: List<String>,
        val subagentModelConfigId: String,
        val subagentModel: String,
        val roleplayPlanSteps: List<String>,
        val toolModelConfigIds: Map<String, String>,
    )
}
