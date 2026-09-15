package com.eleckoi.android.feature.characters.transfer.format.sillytavern

import com.eleckoi.android.compatibility.mvu.importer.MvuCharacterImportAdapter
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleImportCodec
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningMessage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.withOpeningMessages
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.importing.SillyTavernWorldBookImporter
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.importing.SillyTavernWorldBookGroupId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.importing.SillyTavernWorldEntryIdPrefix
import com.eleckoi.android.feature.characters.transfer.format.png.PngTextChunkCodec
import com.eleckoi.android.feature.characters.transfer.model.DecodedCharacterCard
import com.eleckoi.android.feature.characters.transfer.model.PortableCharacter
import com.eleckoi.android.feature.characters.transfer.model.PortableCharacterPackage
import com.eleckoi.android.foundation.storage.newId
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Converts the small, explicit SillyTavern subset supported by ElecKoi. */
internal object SillyTavernCharacterCardFormat {
    fun decode(bytes: ByteArray): DecodedCharacterCard {
        val isPng = PngTextChunkCodec.isPng(bytes)
        val json = if (isPng) {
            val textChunks = PngTextChunkCodec.readText(bytes)
            val encoded = sequenceOf(Ccv3Keyword, CharacterKeyword)
                .mapNotNull { keyword -> textChunks[keyword]?.trim()?.takeIf(String::isNotBlank) }
                .firstOrNull()
                ?: error("图片里没有酒馆角色卡数据")
            runCatching {
                Base64.getMimeDecoder().decode(encoded).toString(StandardCharsets.UTF_8)
            }.getOrElse { throw IllegalArgumentException("酒馆角色卡数据无法解码", it) }
        } else {
            bytes.toString(StandardCharsets.UTF_8).trim().also { text ->
                require(text.startsWith("{")) { "酒馆角色卡必须是 PNG 或 JSON 文件" }
            }
        }
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("酒馆角色卡 JSON 已损坏", it) }
        val data = root.optJSONObject("data") ?: error("酒馆角色卡缺少角色数据")
        val name = data.cleanString("name").ifBlank { "未命名角色" }
        val openings = buildList {
            data.cleanString("first_mes").takeIf(String::isNotBlank)?.let(::add)
            data.optJSONArray("alternate_greetings")?.strings()?.forEach(::add)
        }.distinct()
        val worldBook = data.optJSONObject("character_book")
        val variableConversion = MvuCharacterImportAdapter.convert(
            extensions = data.optJSONObject("extensions"),
            worldBook = worldBook,
            openings = openings,
        )
        val convertedLibrary = convertSettingLibrary(
            cardName = name,
            description = data.cleanString("description"),
            openings = openings,
            openingStates = variableConversion.openingStates,
            worldBook = worldBook,
        )
        val variables = variableConversion.config
        val regexRules = convertRegexRules(data.optJSONObject("extensions"))
        val worldBookCount = convertedLibrary.entries.count { entry ->
            entry.id.startsWith(SillyTavernWorldEntryIdPrefix)
        }
        val summary = buildString {
            append("剧情小说模式")
            append(" · 世界书 ").append(worldBookCount)
            append(" · 开场白 ").append(openings.size)
            append(" · 正则 ").append(regexRules.size)
            append(" · 变量 ").append(variables?.variables?.size ?: 0)
        }
        return DecodedCharacterCard(
            packageData = PortableCharacterPackage(
                character = PortableCharacter(
                    name = name,
                    group = "",
                    frontendBeautyEnabled = false,
                    profileAge = "",
                    profileSex = "",
                    profileHeight = "",
                    profileBirthday = "",
                    profileLike = "",
                    imagePrompt = "",
                    opening = openings.firstOrNull().orEmpty(),
                    showOpening = openings.isNotEmpty(),
                ),
            ),
            sourceImage = bytes.takeIf { isPng },
            complete = false,
            summary = summary,
            settingLibrary = convertedLibrary,
            variableConfig = variables,
            regexRules = regexRules,
        )
    }

    private fun convertSettingLibrary(
        cardName: String,
        description: String,
        openings: List<String>,
        openingStates: List<String>,
        worldBook: JSONObject?,
    ): SettingLibrary {
        val openingEntry = settingLibraryOpeningEntry().withOpeningMessages(
            messages = openings.mapIndexed { index, content ->
                SettingLibraryOpeningMessage(
                    id = "tavern-opening-${index + 1}",
                    title = if (index == 0) "默认开场" else "备用开场 ${index}",
                    content = content,
                    initialVariableStateJson = openingStates.getOrNull(index).orEmpty(),
                )
            },
            defaultMessageId = "tavern-opening-1",
        )
        val groups = mutableListOf<SettingLibraryGroup>()
        val entries = mutableListOf(openingEntry)
        if (description.isNotBlank()) {
            groups += SettingLibraryGroup(
                id = DescriptionGroupId,
                name = "角色基础设定",
                order = 1,
                treeViewOrder = 1,
            )
            entries += SettingLibraryEntry(
                id = "tavern-character-description",
                title = "角色描述",
                groupId = DescriptionGroupId,
                content = description,
                agentSelectionHint = "酒馆角色卡中的角色基础描述",
                agentReadStrategy = SettingLibraryAgentReadStrategy.Required,
                triggerMode = SettingLibraryTriggerMode.AgentTool,
                order = 1,
                treeViewOrder = 1,
            )
        }

        val convertedWorldEntries = SillyTavernWorldBookImporter.convertEntries(worldBook)
        if (convertedWorldEntries.isNotEmpty()) {
            val requestedName = worldBook?.cleanString("name").orEmpty().ifBlank { "酒馆世界书" }
            val groupName = if (requestedName == "角色基础设定" && description.isNotBlank()) {
                "酒馆世界书"
            } else {
                requestedName
            }
            groups += SettingLibraryGroup(
                id = SillyTavernWorldBookGroupId,
                name = groupName,
                order = groups.size + 1,
                treeViewOrder = groups.size + 1,
            )
            entries += convertedWorldEntries
        }
        return SettingLibrary(
            characterId = "",
            name = worldBook?.cleanString("name").orEmpty().ifBlank { "$cardName 设定库" },
            entries = entries,
            groups = groups,
            expandedGroupIds = groups.map(SettingLibraryGroup::id),
        )
    }

    private fun convertRegexRules(extensions: JSONObject?): List<RegexRule> {
        val scripts = extensions?.optJSONArray("regex_scripts") ?: return emptyList()
        if (scripts.length() == 0) return emptyList()
        return runCatching {
            RegexRuleImportCodec.decode(JSONObject().put("regex_scripts", scripts).toString())
        }.getOrDefault(emptyList())
            .filter { RegexRuleProcessor.validationMessage(it) == null }
            .mapIndexed { index, rule -> rule.copy(order = index) }
    }

    private fun JSONObject.cleanString(key: String): String = optString(key, "")
        .takeUnless { it == "null" }
        .orEmpty()

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) {
            optString(index, "").trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private const val Ccv3Keyword = "ccv3"
    private const val CharacterKeyword = "chara"
    private const val DescriptionGroupId = "tavern-character-basics"
}
