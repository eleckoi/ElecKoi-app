package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.importing

import com.eleckoi.android.compatibility.mvu.importer.MvuCharacterImportAdapter
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import org.json.JSONArray
import org.json.JSONObject

internal const val SillyTavernWorldBookGroupId = "tavern-world-book"
internal const val SillyTavernWorldEntryIdPrefix = "tavern-world-entry-"

/** Converts standalone SillyTavern world-info exports and embedded character books. */
internal object SillyTavernWorldBookImporter {
    fun canParse(root: JSONObject): Boolean =
        root.optString("format").isBlank() &&
            (root.optJSONArray("entries") != null ||
                root.optJSONObject("entries") != null ||
                root.optJSONObject("character_book") != null ||
                root.optJSONObject("data")?.optJSONObject("character_book") != null)

    fun parse(json: String, versionId: String): SettingLibraryVersion {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw ElecKoiDataException("酒馆世界书文件格式不正确", it) }
        return parse(root, versionId)
    }

    /**
     * Accepts both a character card's `data.character_book` object and a standalone world-book
     * object. Standalone exports commonly encode `entries` as an object keyed by numeric IDs,
     * while embedded card books use an array; both forms are normalized before conversion.
     */
    fun parse(root: JSONObject, versionId: String): SettingLibraryVersion {
        val worldBook = root.optJSONObject("data")?.optJSONObject("character_book")
            ?: root.optJSONObject("character_book")
            ?: root
        val name = root.cleanString("name")
            .ifBlank { root.optJSONObject("originalData")?.cleanString("name").orEmpty() }
            .ifBlank { worldBook.cleanString("name") }
            .ifBlank { "酒馆世界书" }
        val entries = convertEntries(worldBook)
        if (entries.isEmpty()) throw ElecKoiDataException("酒馆世界书没有可导入的条目")
        val group = SettingLibraryGroup(
            id = SillyTavernWorldBookGroupId,
            name = name,
            order = 1,
            treeViewOrder = 1,
        )
        return SettingLibraryVersion(
            id = versionId,
            name = name,
            entries = entries,
            groups = listOf(group),
            expandedGroupIds = listOf(group.id),
        )
    }

    fun convertEntries(worldBook: JSONObject?): List<SettingLibraryEntry> {
        val source = worldBook?.entriesArray() ?: return emptyList()
        val usedTitles = mutableSetOf<String>()
        val imported = buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val content = item.cleanString("content")
                val primaryKeys = item.optJSONArray("keys")?.strings()
                    ?: item.optJSONArray("key")?.strings().orEmpty()
                val rawTitle = item.cleanString("name")
                    .ifBlank { item.cleanString("comment") }
                    .ifBlank { primaryKeys.firstOrNull().orEmpty() }
                    .ifBlank { "世界书条目 ${index + 1}" }
                if (MvuCharacterImportAdapter.isInfrastructureEntry(item, rawTitle)) continue
                add(
                    TavernWorldEntry(
                        index = index,
                        item = item,
                        rawTitle = rawTitle,
                        title = uniqueTitle(rawTitle, usedTitles),
                        content = content,
                        primaryKeys = primaryKeys,
                        entryId = "$SillyTavernWorldEntryIdPrefix${newId(10)}",
                    ),
                )
            }
        }
        val byRawTitle = imported.groupBy(TavernWorldEntry::rawTitle)
        val controllerResources = imported
            .filter(TavernWorldEntry::isEjsController)
            .associateWith { controller ->
                val literalNames = LiteralGetwiTarget.findAll(controller.content)
                    .mapNotNull { match -> match.groupValues.getOrNull(2)?.takeIf(String::isNotBlank) }
                    .toList()
                val hasDynamicGetwi = GetwiCall.findAll(controller.content).count() > literalNames.size
                val candidates = if (hasDynamicGetwi) {
                    imported.filterNot(TavernWorldEntry::isEjsController)
                } else {
                    literalNames.flatMap { name -> byRawTitle[name].orEmpty().take(1) }
                }
                candidates.distinctBy(TavernWorldEntry::entryId)
            }
        val materialIds = controllerResources.values.flatten()
            .map(TavernWorldEntry::entryId)
            .toSet()
        return imported.map { entry ->
            when {
                entry.isEjsController() -> entry.toControllerEntry()
                entry.entryId in materialIds -> entry.toReferenceEntry()
                else -> entry.toNormalEntry()
            }
        }
    }

    private fun TavernWorldEntry.toControllerEntry(): SettingLibraryEntry = SettingLibraryEntry(
        id = entryId,
        title = title,
        groupId = SillyTavernWorldBookGroupId,
        content = content,
        agentSelectionHint = "酒馆 EJS 动态控制器，渲染结果为 Agent 必读",
        agentReadStrategy = SettingLibraryAgentReadStrategy.VariableCondition,
        dynamicMode = SettingLibraryDynamicMode.EjsController,
        triggerMode = SettingLibraryTriggerMode.AgentTool,
        enabled = enabledFrom(item),
        order = index + 1,
        treeViewOrder = index + 1,
    )

    private fun TavernWorldEntry.toReferenceEntry(): SettingLibraryEntry = SettingLibraryEntry(
        id = entryId,
        title = title,
        groupId = SillyTavernWorldBookGroupId,
        content = content,
        agentSelectionHint = "供 EJS 控制器通过 getwi 读取的EJS引用设定",
        agentReadStrategy = SettingLibraryAgentReadStrategy.VariableCondition,
        dynamicMode = SettingLibraryDynamicMode.EjsReference,
        triggerMode = SettingLibraryTriggerMode.AgentTool,
        // SillyTavern disables getwi-only entries to prevent ordinary scanning. Here the
        // reference type already prevents standalone delivery, so its switch means availability.
        enabled = true,
        order = index + 1,
        treeViewOrder = index + 1,
    )

    private fun TavernWorldEntry.toNormalEntry(): SettingLibraryEntry {
        val constant = item.optBoolean("constant", false)
        val secondaryKeys = item.optJSONArray("secondary_keys")?.strings()
            ?: item.optJSONArray("keysecondary")?.strings().orEmpty()
        val extensions = item.optJSONObject("extensions")
        val condition = if (secondaryKeys.isEmpty()) {
            SettingLibraryKeywordCondition.None
        } else {
            when (extensions?.optInt("selectiveLogic", 0) ?: 0) {
                2 -> SettingLibraryKeywordCondition.NotAny
                3 -> SettingLibraryKeywordCondition.All
                else -> SettingLibraryKeywordCondition.Any
            }
        }
        return SettingLibraryEntry(
            id = entryId,
            title = title,
            groupId = SillyTavernWorldBookGroupId,
            content = content,
            agentSelectionHint = if (constant) {
                "酒馆世界书常驻条目，Agent 必读"
            } else {
                "酒馆世界书关键词命中时读取"
            },
            agentReadStrategy = if (constant) {
                SettingLibraryAgentReadStrategy.Required
            } else {
                SettingLibraryAgentReadStrategy.Keyword
            },
            keywords = if (constant) emptyList() else primaryKeys,
            conditionKeywords = if (constant) emptyList() else secondaryKeys,
            keywordCondition = if (constant) SettingLibraryKeywordCondition.None else condition,
            keywordUseRegex = !constant && item.optBoolean("use_regex", false),
            keywordIgnoreCase = !item.optBoolean(
                "case_sensitive",
                extensions?.optBoolean("case_sensitive", false) ?: false,
            ),
            keywordWholeWord = item.optBoolean(
                "match_whole_words",
                extensions?.optBoolean("match_whole_words", false) ?: false,
            ),
            triggerMode = SettingLibraryTriggerMode.AgentTool,
            enabled = enabledFrom(item),
            order = index + 1,
            treeViewOrder = index + 1,
        )
    }

    private fun JSONObject.entriesArray(): JSONArray? {
        optJSONArray("entries")?.let { return it }
        val objectEntries = optJSONObject("entries") ?: return null
        val result = JSONArray()
        objectEntries.keys().asSequence()
            .sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
            .forEach { key -> objectEntries.optJSONObject(key)?.let(result::put) }
        return result
    }

    private fun enabledFrom(item: JSONObject): Boolean = if (item.has("enabled")) {
        item.optBoolean("enabled", true)
    } else {
        !item.optBoolean("disable", false)
    }

    private fun JSONObject.cleanString(key: String): String = optString(key, "")
        .takeUnless { it == "null" }
        .orEmpty()

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) {
            optString(index, "").trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun uniqueTitle(requested: String, used: MutableSet<String>): String {
        val base = requested.trim().take(120).ifBlank { "未命名设定" }
        if (used.add(base.lowercase())) return base
        return generateSequence(2) { it + 1 }
            .map { suffix -> "$base ($suffix)".take(120) }
            .first { candidate -> used.add(candidate.lowercase()) }
    }

    private val EjsTag = Regex("<%[_=\\-#]?")
    private val GetwiCall = Regex("getwi\\s*\\(", RegexOption.IGNORE_CASE)
    private val LiteralGetwiTarget = Regex(
        """getwi\s*\(\s*(?:(?:null|[\"'][^\"']*[\"'])\s*,\s*)?([\"'])([^\"']+)\1""",
        RegexOption.IGNORE_CASE,
    )

    private data class TavernWorldEntry(
        val index: Int,
        val item: JSONObject,
        val rawTitle: String,
        val title: String,
        val content: String,
        val primaryKeys: List<String>,
        val entryId: String,
    ) {
        fun isEjsController(): Boolean = EjsTag.containsMatchIn(content)
    }
}
