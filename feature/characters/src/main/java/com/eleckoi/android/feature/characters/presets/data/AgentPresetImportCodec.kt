package com.eleckoi.android.feature.characters.presets.data

import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetImportDocument
import com.eleckoi.android.feature.characters.presets.model.AgentPresetImportSource
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelFamily
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetProfile
import com.eleckoi.android.feature.characters.presets.model.AgentPresetToolConfiguration
import com.eleckoi.android.feature.characters.presets.model.AgentPresetRoleplayPlan
import com.eleckoi.android.feature.characters.presets.model.AgentPresetTimelineItem
import com.eleckoi.android.feature.characters.presets.model.toTag
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryJsonCodec
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleJsonCodec
import com.eleckoi.android.feature.characters.modes.story.regex.data.hasUnsupportedRegexDepth
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class AgentPresetTransferAvatar(
    val mediaType: String,
    val bytes: ByteArray,
)

internal data class AgentPresetTransferVersion(
    val id: String,
    val number: Int,
    val name: String,
    val createdAtEpochMs: Long,
    val preset: AgentPreset,
)

internal data class AgentPresetImportConversion(
    val preset: AgentPreset,
    val skippedUnsupportedEntries: Int = 0,
    val skippedDepthRegexCount: Int = 0,
    val authorAvatar: AgentPresetTransferAvatar? = null,
    val versions: List<AgentPresetTransferVersion> = emptyList(),
)

/** Converts supported preset files into an unpersisted [AgentPreset]. */
internal object AgentPresetImportCodec {
    private const val ElecKoiFormat = "eleckoi.agent-preset"
    private const val ElecKoiFormatVersion = 1
    private const val SillyTavernGlobalPromptOrderId = "100001"
    private const val SillyTavernPromptGroupId = "tavern-preset-prompts"
    private const val MaxAuthorAvatarBytes = 8 * 1024 * 1024
    private const val MaxVersions = 100
    private val AuthorAvatarMediaTypes = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
    private val PortableToolGroupIds = setOf(
        AgentToolRequestPolicy.BuiltInMcpResources,
        AgentToolRequestPolicy.BuiltInWorkflow,
        AgentToolRequestPolicy.BuiltInCreator,
        AgentToolRequestPolicy.BuiltInVariables,
        AgentToolRequestPolicy.BuiltInCollaboration,
        AgentToolRequestPolicy.BuiltInPluginDiscovery,
        AgentToolRequestPolicy.BuiltInWorkspace,
        AgentToolRequestPolicy.BuiltInWeb,
        AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
        AgentToolRequestPolicy.BuiltInAutoIllustration,
        AgentToolRequestPolicy.BuiltInSettingLibrary,
    )

    fun decode(
        document: AgentPresetImportDocument,
        source: AgentPresetImportSource,
    ): AgentPresetImportConversion = when (source) {
        AgentPresetImportSource.ElecKoi -> decodeElecKoi(document)
        AgentPresetImportSource.SillyTavern -> decodeSillyTavern(document)
    }

    fun encodeElecKoi(
        preset: AgentPreset,
        authorAvatar: AgentPresetTransferAvatar? = null,
        versions: List<AgentPresetTransferVersion> = emptyList(),
    ): String {
        val value = JSONObject()
            .put("name", preset.name)
            .put("model_family", preset.modelFamily.storageValue)
            .put("model_tags", JSONArray(preset.modelTags.map(::modelTagToJson)))
            .put("profile", profileToJson(preset.profile, authorAvatar))
            .put("active_version_id", preset.activeVersionId)
            .put("active_version_number", preset.activeVersionNumber)
        putTransferContent(value, preset)
        value.put(
            "versions",
            JSONArray(versions.map(::versionToJson)),
        )
        return JSONObject()
            .put("format", ElecKoiFormat)
            .put("version", ElecKoiFormatVersion)
            .put("preset", value)
            .toString(2)
    }

    private fun decodeElecKoi(document: AgentPresetImportDocument): AgentPresetImportConversion {
        val json = document.bytes?.let { bytes ->
            if (AgentPresetPngFormat.isPng(bytes)) {
                AgentPresetPngFormat.decode(bytes).json
            } else {
                bytes.toString(StandardCharsets.UTF_8)
            }
        } ?: document.json
        val root = parseRoot(json, "ElecKoi 预设")
        require(
            root.cleanString("format") == ElecKoiFormat &&
                root.optInt("version", -1) == ElecKoiFormatVersion,
        ) { "这不是当前版本的 ElecKoi 预设文件" }
        val value = root.optJSONObject("preset") ?: error("ElecKoi 预设缺少 preset 数据")
        val family = AgentPresetModelFamily.fromStorage(value.cleanString("model_family"))
        val tags = value.optJSONArray("model_tags")
            ?.objects()
            ?.mapNotNull(::modelTagFromJson)
            .orEmpty()
            .ifEmpty { listOf(family.toTag()) }
        val profileValue = value.optJSONObject("profile")
        val profile = profileValue?.let(::profileFromJson) ?: AgentPresetProfile()
        val avatar = profileValue?.optJSONObject("author_avatar")?.let(::avatarFromJson)
        val current = AgentPreset(
            id = "",
            name = value.cleanString("name").ifBlank { document.suggestedName() },
            modelFamily = family,
            modelTags = tags,
            activeVersionId = value.cleanString("active_version_id").ifBlank { "import:v1" },
            activeVersionNumber = value.optInt("active_version_number", 1).coerceAtLeast(1),
            profile = profile,
        ).let { base -> contentFromJson(value, profileValue, base) }
        val versions = value.optJSONArray("versions")
            ?.objects()
            ?.take(MaxVersions)
            ?.mapIndexed { index, version ->
                val content = contentFromJson(version, version, current)
                AgentPresetTransferVersion(
                    id = version.cleanString("id").ifBlank { "import:v${index + 1}" },
                    number = version.optInt("number", index + 1).coerceAtLeast(1),
                    name = version.cleanString("name").take(60).ifBlank { current.name },
                    createdAtEpochMs = version.optLong("created_at_epoch_ms", System.currentTimeMillis())
                        .coerceAtLeast(0L),
                    preset = content,
                )
            }
            .orEmpty()
        return AgentPresetImportConversion(
            preset = current,
            authorAvatar = avatar,
            versions = versions,
        )
    }

    private fun decodeSillyTavern(document: AgentPresetImportDocument): AgentPresetImportConversion {
        val root = document.parseRoot("酒馆预设")
        val definitions = root.optJSONArray("prompts")
            ?.objects()
            ?.mapNotNull { prompt ->
                prompt.cleanString("identifier").takeIf(String::isNotBlank)?.let { it to prompt }
            }
            ?.toMap()
            .orEmpty()
        require(definitions.isNotEmpty()) { "酒馆预设没有 prompts 条目" }

        val promptOrder = root.optJSONArray("prompt_order")
            ?.objects()
            ?.firstOrNull { it.opt("character_id")?.toString() == SillyTavernGlobalPromptOrderId }
            ?.optJSONArray("order")
            ?: error("找不到酒馆预设外层条目顺序（100001）")

        val usedTitles = mutableSetOf<String>()
        var skipped = 0
        val entries = buildList {
            for (orderIndex in 0 until promptOrder.length()) {
                val orderEntry = promptOrder.optJSONObject(orderIndex)
                val identifier = orderEntry?.cleanString("identifier").orEmpty()
                val prompt = definitions[identifier]
                if (prompt == null || prompt.optBoolean("marker", false)) {
                    skipped += 1
                    continue
                }
                val requestedTitle = prompt.cleanString("name")
                    .ifBlank { "提示词 ${orderIndex + 1}" }
                add(
                    SettingLibraryEntry(
                        id = "tavern-preset-entry-${orderIndex + 1}",
                        title = uniqueTitle(requestedTitle, usedTitles),
                        groupId = SillyTavernPromptGroupId,
                        content = prompt.optString("content", ""),
                        agentSelectionHint = "酒馆预设常驻条目，Agent 必读",
                        agentReadStrategy = SettingLibraryAgentReadStrategy.Required,
                        triggerMode = SettingLibraryTriggerMode.AgentTool,
                        enabled = orderEntry?.optBoolean("enabled", true) ?: true,
                        insertRole = prompt.cleanString("role").toInsertRole(),
                        order = orderIndex + 1,
                        viewOrder = orderIndex + 1,
                        groupViewOrder = 1,
                        treeViewOrder = orderIndex + 1,
                    ),
                )
            }
        }
        require(entries.isNotEmpty()) { "酒馆预设外层列表没有可导入的提示词" }
        val group = SettingLibraryGroup(
            id = SillyTavernPromptGroupId,
            name = "酒馆提示词",
            order = 1,
            treeViewOrder = 1,
        )
        val tavernRegexScripts = root.optJSONObject("extensions")?.optJSONArray("regex_scripts")
            ?: root.optJSONArray("regex_scripts")
        var skippedDepthRegexCount = 0
        val supportedRegexScripts = tavernRegexScripts?.let { scripts ->
            JSONArray().also { supported ->
                for (index in 0 until scripts.length()) {
                    scripts.optJSONObject(index)?.let { rule ->
                        if (rule.hasUnsupportedRegexDepth()) {
                            skippedDepthRegexCount += 1
                        } else {
                            supported.put(rule)
                        }
                    }
                }
            }
        }
        val regexRules = RegexRuleJsonCodec.decodeRules(supportedRegexScripts)
        return AgentPresetImportConversion(
            preset = AgentPreset(
                id = "",
                name = root.cleanString("name").ifBlank { document.suggestedName() },
                modelTags = listOf(AgentPresetModelFamily.General.toTag()),
                entries = entries,
                groups = listOf(group),
                regexRules = regexRules,
                expandedGroupIds = listOf(group.id),
            ),
            skippedUnsupportedEntries = skipped,
            skippedDepthRegexCount = skippedDepthRegexCount,
        )
    }

    private fun String.toInsertRole(): SettingLibraryInsertRole = when (lowercase()) {
        "user" -> SettingLibraryInsertRole.User
        "assistant" -> SettingLibraryInsertRole.Assistant
        else -> SettingLibraryInsertRole.System
    }

    private fun uniqueTitle(requested: String, used: MutableSet<String>): String {
        val base = requested.trim().take(120).ifBlank { "未命名提示词" }
        if (used.add(base.lowercase())) return base
        return generateSequence(2) { it + 1 }
            .map { suffix -> "$base ($suffix)".take(120) }
            .first { candidate -> used.add(candidate.lowercase()) }
    }

    private fun AgentPresetImportDocument.parseRoot(label: String): JSONObject = parseRoot(json, label)

    private fun parseRoot(json: String, label: String): JSONObject = runCatching {
        JSONObject(json)
    }.getOrElse { error ->
        throw IllegalArgumentException("$label JSON 已损坏", error)
    }

    private fun AgentPresetImportDocument.suggestedName(): String = fileName
        .replace(Regex("(?i)\\.(json|png)$"), "")
        .trim()
        .ifBlank { "导入预设" }

    private fun JSONObject.cleanString(key: String): String = optString(key, "")
        .takeUnless { it == "null" }
        .orEmpty()
        .trim()

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun modelTagToJson(tag: AgentPresetModelTag): JSONObject = JSONObject()
        .put("id", tag.id)
        .put("label", tag.label)
        .put("provider_id", tag.providerId)

    private fun modelTagFromJson(value: JSONObject): AgentPresetModelTag? {
        val id = value.cleanString("id")
        val label = value.cleanString("label")
        if (id.isBlank() || label.isBlank()) return null
        return AgentPresetModelTag(id, label, value.cleanString("provider_id"))
    }

    private fun profileToJson(
        profile: AgentPresetProfile,
        authorAvatar: AgentPresetTransferAvatar?,
    ): JSONObject = JSONObject()
        .put("author_name", profile.authorName)
        .put("usage_instructions", profile.usageInstructions)
        .put("timeline", timelineToJson(profile.timeline))
        .apply {
            authorAvatar?.let { avatar ->
                put(
                    "author_avatar",
                    JSONObject()
                        .put("media_type", avatar.mediaType)
                        .put("data", Base64.getEncoder().encodeToString(avatar.bytes)),
                )
            }
        }

    private fun profileFromJson(value: JSONObject): AgentPresetProfile = AgentPresetProfile(
        authorName = value.cleanString("author_name"),
        usageInstructions = value.cleanString("usage_instructions"),
        timeline = value.optJSONArray("timeline")
            ?.objects()
            ?.mapIndexedNotNull { index, item ->
                item.cleanString("title").takeIf(String::isNotBlank)?.let { title ->
                    AgentPresetTimelineItem(
                        id = item.cleanString("id").ifBlank { "timeline-${index + 1}" },
                        title = title,
                        dateLabel = item.cleanString("date_label"),
                        note = item.cleanString("note"),
                    )
                }
            }
            .orEmpty(),
    )

    private fun roleplayPlanToJson(plan: AgentPresetRoleplayPlan): JSONObject = JSONObject()
        .put("steps", JSONArray(plan.normalized().steps))

    private fun roleplayPlanFromJson(value: JSONObject?): AgentPresetRoleplayPlan {
        val steps = value?.optJSONArray("steps")?.strings().orEmpty()
        return AgentPresetRoleplayPlan(steps).normalized()
    }

    private fun versionToJson(version: AgentPresetTransferVersion): JSONObject = JSONObject()
        .put("id", version.id)
        .put("number", version.number)
        .put("name", version.name)
        .put("created_at_epoch_ms", version.createdAtEpochMs)
        .put("usage_instructions", version.preset.profile.usageInstructions)
        .put("timeline", timelineToJson(version.preset.profile.timeline))
        .also { putTransferContent(it, version.preset) }

    private fun putTransferContent(target: JSONObject, preset: AgentPreset) {
        target
            .put("entries", JSONArray(preset.entries.map(SettingLibraryJsonCodec::entryToJson)))
            .put("groups", JSONArray(preset.groups.map(SettingLibraryJsonCodec::groupToJson)))
            .put("regex_rules", JSONArray(preset.regexRules.map(RegexRuleJsonCodec::ruleToJson)))
            .put(
                "prompt_positions",
                JSONArray(preset.promptPositions.map(SettingLibraryJsonCodec::promptPositionToJson)),
            )
            .put("tool_configuration", toolConfigurationToJson(preset.toolConfiguration))
            .put("roleplay_plan", roleplayPlanToJson(preset.roleplayPlan))
            .put("expanded_group_ids", JSONArray(preset.expandedGroupIds))
    }

    private fun contentFromJson(
        value: JSONObject,
        profile: JSONObject?,
        base: AgentPreset,
    ): AgentPreset = base.copy(
        profile = base.profile.copy(
            usageInstructions = profile?.cleanString("usage_instructions").orEmpty(),
            timeline = profile?.optJSONArray("timeline")
                ?.objects()
                ?.mapIndexedNotNull { index, item -> timelineItemFromJson(index, item) }
                .orEmpty(),
        ),
        entries = value.optJSONArray("entries")
            ?.objects()
            ?.map(SettingLibraryJsonCodec::entryFromJson)
            .orEmpty(),
        groups = value.optJSONArray("groups")
            ?.objects()
            ?.mapIndexed(SettingLibraryJsonCodec::groupFromJson)
            .orEmpty(),
        regexRules = RegexRuleJsonCodec.decodeRules(value.optJSONArray("regex_rules")),
        promptPositions = value.optJSONArray("prompt_positions")
            ?.objects()
            ?.mapIndexed(SettingLibraryJsonCodec::promptPositionFromJson)
            .orEmpty(),
        toolConfiguration = toolConfigurationFromJson(value.optJSONObject("tool_configuration")),
        roleplayPlan = roleplayPlanFromJson(value.optJSONObject("roleplay_plan")),
        expandedGroupIds = value.optJSONArray("expanded_group_ids")?.strings().orEmpty(),
    )

    private fun toolConfigurationToJson(configuration: AgentPresetToolConfiguration): JSONObject {
        val included = configuration.includedGroupIds
            .map(String::trim)
            .filter { it in PortableToolGroupIds }
            .distinct()
        val enabled = configuration.enabledGroupIds
            .map(String::trim)
            .filter { it in PortableToolGroupIds && it in included }
            .distinct()
        return JSONObject()
            .put("included_group_ids", JSONArray(included))
            .put("enabled_group_ids", JSONArray(enabled))
    }

    private fun toolConfigurationFromJson(value: JSONObject?): AgentPresetToolConfiguration {
        if (value == null) return AgentPresetToolConfiguration()
        val included = value.optJSONArray("included_group_ids")
            ?.strings()
            ?.filter { it in PortableToolGroupIds }
            ?.distinct()
            .orEmpty()
        val enabled = value.optJSONArray("enabled_group_ids")
            ?.strings()
            ?.filter { it in PortableToolGroupIds && it in included }
            ?.toSet()
            .orEmpty()
        return AgentPresetToolConfiguration(
            includedGroupIds = included,
            enabledGroupIds = enabled,
        )
    }

    private fun timelineToJson(timeline: List<AgentPresetTimelineItem>): JSONArray = JSONArray(
        timeline.map { item ->
            JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("date_label", item.dateLabel)
                .put("note", item.note)
        },
    )

    private fun timelineItemFromJson(index: Int, item: JSONObject): AgentPresetTimelineItem? =
        item.cleanString("title").takeIf(String::isNotBlank)?.let { title ->
            AgentPresetTimelineItem(
                id = item.cleanString("id").ifBlank { "timeline-${index + 1}" },
                title = title,
                dateLabel = item.cleanString("date_label"),
                note = item.cleanString("note"),
            )
        }

    private fun avatarFromJson(value: JSONObject): AgentPresetTransferAvatar {
        val mediaType = value.cleanString("media_type").lowercase()
        require(mediaType in AuthorAvatarMediaTypes) { "预设卡作者头像格式不受支持" }
        val encoded = value.cleanString("data").replace(Regex("\\s+"), "")
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { error -> throw IllegalArgumentException("预设卡作者头像已损坏", error) }
        require(bytes.isNotEmpty() && bytes.size <= MaxAuthorAvatarBytes) {
            "预设卡作者头像已损坏或超过 8 MB"
        }
        return AgentPresetTransferAvatar(mediaType, bytes)
    }

}
