package com.eleckoi.android.feature.characters.transfer.format

import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.characters.transfer.model.PortableAsset
import com.eleckoi.android.feature.characters.transfer.model.PortableCharacter
import com.eleckoi.android.feature.characters.transfer.model.PortableCharacterPackage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal object CharacterCardJsonCodec {
    const val PortableKeyword = "eleckoi-card"
    const val PortableFormat = "eleckoi.character-card"
    private const val PortableVersion = 1
    private const val MaxExpandedBytes = 96 * 1024 * 1024
    private const val MaxAttachmentBytes = 48L * 1024 * 1024

    fun encodeJson(value: PortableCharacterPackage): String = packageJson(value).toString(2)

    fun encodePortable(value: PortableCharacterPackage): String {
        val json = encodeJson(value).toByteArray(StandardCharsets.UTF_8)
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(json) }
            output.toByteArray()
        }
        return Base64.getEncoder().encodeToString(compressed)
    }

    fun decodePortable(encoded: String): PortableCharacterPackage {
        val compressed = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { error("ElecKoi 角色卡数据损坏") }
        val json = GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MaxExpandedBytes) { "角色卡解压后过大" }
                output.write(buffer, 0, count)
            }
            output.toString(StandardCharsets.UTF_8.name())
        }
        return decodeJson(json)
    }

    fun decodeJson(json: String): PortableCharacterPackage = packageFromJson(
        runCatching { JSONObject(json) }.getOrElse { error("ElecKoi 角色卡 JSON 已损坏") },
    )

    private fun packageJson(value: PortableCharacterPackage): JSONObject = JSONObject()
        .put("format", PortableFormat)
        .put("version", PortableVersion)
        .put("character", characterJson(value.character))
        .put("assets", JSONArray(value.assets.map(::assetJson)))
        .put("setting_library", value.settingLibraryJson)
        .put("variable_config", value.variableConfigJson)
        .put("regex_rules", JSONArray(value.regexRules.sortedBy(RegexRule::order).map(::regexRuleJson)))

    private fun packageFromJson(root: JSONObject): PortableCharacterPackage {
        require(root.optString("format") == PortableFormat) { "这不是 ElecKoi 角色卡" }
        require(root.optInt("version") == PortableVersion) { "暂不支持这个角色卡版本" }
        val assets = root.optJSONArray("assets").objects().map(::assetFromJson)
        require(assets.sumOf { it.bytes.size.toLong() } <= MaxAttachmentBytes) {
            "角色图片总大小不能超过 48 MB"
        }
        return PortableCharacterPackage(
            character = characterFromJson(root.getJSONObject("character")),
            assets = assets,
            settingLibraryJson = root.optString("setting_library"),
            variableConfigJson = root.optString("variable_config"),
            regexRules = root.optJSONArray("regex_rules").objects()
                .mapIndexed(::regexRuleFromJson)
                .sortedBy(RegexRule::order),
        )
    }

    private fun characterJson(value: PortableCharacter): JSONObject = JSONObject()
        .put("name", value.name)
        .put("group", value.group)
        .put("frontend_beauty_enabled", value.frontendBeautyEnabled)
        .put("profile_age", value.profileAge)
        .put("profile_sex", value.profileSex)
        .put("profile_height", value.profileHeight)
        .put("profile_birthday", value.profileBirthday)
        .put("profile_like", value.profileLike)
        .put("image_prompt", value.imagePrompt)
        .put("opening", value.opening)
        .put("show_opening", value.showOpening)

    private fun characterFromJson(value: JSONObject): PortableCharacter = PortableCharacter(
        name = value.optString("name").take(120).ifBlank { "导入角色" },
        group = value.optString("group").take(40),
        frontendBeautyEnabled = value.optBoolean("frontend_beauty_enabled"),
        profileAge = value.optString("profile_age"),
        profileSex = value.optString("profile_sex"),
        profileHeight = value.optString("profile_height"),
        profileBirthday = value.optString("profile_birthday"),
        profileLike = value.optString("profile_like"),
        imagePrompt = value.optString("image_prompt"),
        opening = value.optString("opening"),
        showOpening = value.optBoolean("show_opening"),
    )

    private fun assetJson(value: PortableAsset): JSONObject = JSONObject()
        .put("key", value.key)
        .put("media_type", value.mediaType)
        .put("data", Base64.getEncoder().encodeToString(value.bytes))

    private fun assetFromJson(value: JSONObject): PortableAsset = PortableAsset(
        key = value.optString("key").take(80),
        mediaType = value.optString("media_type").take(80),
        bytes = decodeBytes(value.optString("data")),
    )

    private fun regexRuleJson(value: RegexRule): JSONObject = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("pattern", value.pattern)
        .put("replacement", value.replacement)
        .put("targets", JSONArray(value.targets.map(RegexRuleTarget::name)))
        .put("enabled", value.enabled)
        .put("display_only", value.displayOnly)
        .put("prompt_only", value.promptOnly)
        .put("run_on_edit", value.runOnEdit)
        .put("order", value.order)

    private fun regexRuleFromJson(index: Int, value: JSONObject): RegexRule {
        val rule = RegexRule(
            id = value.optString("id").trim().ifBlank { "regex-import-$index" },
            name = value.optString("name").trim().take(60),
            pattern = value.optString("pattern").take(4_000),
            replacement = value.optString("replacement"),
            targets = value.optJSONArray("targets").enumSet<RegexRuleTarget>(),
            enabled = value.optBoolean("enabled", true),
            displayOnly = value.optBoolean("display_only"),
            promptOnly = value.optBoolean("prompt_only"),
            runOnEdit = value.optBoolean("run_on_edit"),
            order = value.optInt("order", index).coerceAtLeast(0),
        )
        val validationMessage = RegexRuleProcessor.validationMessage(rule)
        require(validationMessage == null) {
            "角色卡正则“${rule.name.ifBlank { rule.id }}”无效：$validationMessage"
        }
        return rule
    }

    private fun decodeBytes(value: String): ByteArray = runCatching {
        Base64.getDecoder().decode(value)
    }.getOrElse { error("角色卡附件损坏") }

    private fun JSONArray?.objects(): List<JSONObject> = buildList {
        val array = this@objects ?: return@buildList
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(::add)
        }
    }

    private inline fun <reified T : Enum<T>> JSONArray?.enumSet(): Set<T> = buildSet {
        val array = this@enumSet ?: return@buildSet
        for (index in 0 until array.length()) {
            runCatching { enumValueOf<T>(array.optString(index)) }.getOrNull()?.let(::add)
        }
    }
}
