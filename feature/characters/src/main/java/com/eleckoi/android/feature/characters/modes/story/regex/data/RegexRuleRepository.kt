package com.eleckoi.android.feature.characters.modes.story.regex.data

import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportDocument
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportResult
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleVersion
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.stringOrEmpty
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.GlobalRegexRuleEntity
import com.eleckoi.android.foundation.storage.room.CharacterRegexRuleEntity
import com.eleckoi.android.foundation.storage.room.RegexStateEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class RegexRuleSurface { Stored, Display, Prompt }

data class VersionedRegexRuleCollection(
    val collection: RegexRuleCollection,
    val revision: Long,
)

class RegexRuleRepository(
    private val database: ElecKoiDatabase,
    private val characters: CharacterRepository,
) {
    private val dao = database.regexRuleDao()
    private val storyPresetDao = database.storyPresetDao()
    private val mutableRevision = MutableStateFlow(0L)

    /** Changes only after commit so active chat projections reload one complete persisted revision. */
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun load(characterId: String): RegexRuleCollection = loadVersioned(characterId).collection

    fun loadVersioned(characterId: String): VersionedRegexRuleCollection {
        return database.runInTransaction<VersionedRegexRuleCollection> {
            requireCharacter(characterId)
            val state = dao.state()
            val collection = RegexRuleCollection(
                globalRules = dao.globalRules().map { it.rule.toRule(it.id) },
                promptPresetRules = RegexRuleJsonCodec.decodeRules(storyPresetDao.activePresetRegexRulesJson()),
                characterRules = dao.characterRules(characterId).map { it.rule.toRule(it.id) },
                versions = dao.versions().map { it.toVersion() },
                activeVersionId = state?.activeVersionId.orEmpty(),
            ).normalized()
            VersionedRegexRuleCollection(collection, state?.revision ?: 0L)
        }
    }

    fun save(characterId: String, collection: RegexRuleCollection): RegexRuleCollection =
        requireNotNull(saveInternal(characterId, collection, expectedRevision = null)).collection

    /** Atomically rejects a delayed Agent change set when its preview revision is stale. */
    fun saveIfRevision(
        characterId: String,
        collection: RegexRuleCollection,
        expectedRevision: Long,
    ): VersionedRegexRuleCollection? = saveInternal(characterId, collection, expectedRevision)

    private fun saveInternal(
        characterId: String,
        collection: RegexRuleCollection,
        expectedRevision: Long?,
    ): VersionedRegexRuleCollection? {
        requireCharacter(characterId)
        val normalized = collection.normalized()
        val promptPresetRulesJson = RegexRuleJsonCodec.encodeRules(normalized.promptPresetRules)
        val result = database.runInTransaction<RegexSaveResult?> {
            val currentState = dao.state()
            val currentRevision = currentState?.revision ?: 0L
            if (expectedRevision != null && currentRevision != expectedRevision) {
                return@runInTransaction null
            }
            val current = RegexRuleCollection(
                globalRules = dao.globalRules().map { it.rule.toRule(it.id) },
                promptPresetRules = RegexRuleJsonCodec.decodeRules(
                    storyPresetDao.activePresetRegexRulesJson(),
                ),
                characterRules = dao.characterRules(characterId).map { it.rule.toRule(it.id) },
                versions = dao.versions().map { it.toVersion() },
                activeVersionId = currentState?.activeVersionId.orEmpty(),
            ).normalized()
            if (current == normalized) {
                return@runInTransaction RegexSaveResult(
                    value = VersionedRegexRuleCollection(current, currentRevision),
                    changed = false,
                )
            }
            saveShared(normalized)
            if (current.promptPresetRules != normalized.promptPresetRules) {
                storyPresetDao.updateActivePresetRegexRules(promptPresetRulesJson)
                storyPresetDao.updateActivePresetVersionRegexRules(promptPresetRulesJson)
            }
            dao.saveCharacter(characterId, normalized.characterRules.map {
                CharacterRegexRuleEntity(characterId, it.id, it.toRoomFields())
            })
            RegexSaveResult(
                value = VersionedRegexRuleCollection(
                    normalized,
                    dao.state()?.revision ?: currentRevision,
                ),
                changed = true,
            )
        }
        if (result == null) return null
        if (result.changed) mutableRevision.update { current -> current + 1L }
        return result.value
    }

    fun deleteForCharacters(characterIds: List<String>) {
        val ids = characterIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return
        var deleted = 0
        database.runInTransaction {
            deleted = ids.chunked(900).sumOf(dao::deleteForCharacters)
            if (deleted > 0) bumpPersistedRevision()
        }
        if (deleted > 0) mutableRevision.update { current -> current + 1L }
    }

    fun deleteExceptCharacters(characterIds: Collection<String>) {
        var deleted = 0
        database.runInTransaction {
            val retained = characterIds.toSet()
            deleted = dao.characterOwners().filterNot { it in retained }
                .chunked(900).sumOf(dao::deleteForCharacters)
            if (deleted > 0) bumpPersistedRevision()
        }
        if (deleted > 0) mutableRevision.update { it + 1L }
    }

    fun importRules(
        characterId: String,
        scope: RegexRuleScope,
        documents: List<RegexRuleImportDocument>,
    ): RegexRuleImportResult {
        if (documents.isEmpty()) throw ElecKoiDataException("没有选择正则文件")
        val current = load(characterId)
        val decoded = decodeRegexImportDocuments(documents, scope)
        if (decoded.importedByFile.isEmpty()) {
            if (decoded.skippedDepthRuleCount > 0) {
                throw ElecKoiDataException("深度正则不受支持，未导入任何规则")
            }
            throw ElecKoiDataException("所选文件中没有可导入的正则规则")
        }
        val importedRules = decoded.importedByFile.flatten()
        val grouped = importedRules.groupBy(ScopedRegexRule::scope)
        val merged = RegexRuleScope.entries.fold(current) { collection, targetScope ->
            val existing = collection.rulesForScope(targetScope)
            val imported = grouped[targetScope].orEmpty().mapIndexed { index, item ->
                item.rule.copy(id = "regex-${newId(10)}", order = existing.size + index)
            }
            collection.withScopeRules(targetScope, existing + imported)
        }
        return RegexRuleImportResult(
            collection = save(characterId, merged),
            importedFileCount = decoded.importedByFile.size,
            importedRuleCount = importedRules.size,
            skippedDepthRuleCount = decoded.skippedDepthRuleCount,
            failedFileNames = decoded.failedFileNames,
        )
    }

    fun exportRules(characterId: String, ruleIds: Set<String>): String {
        val collection = load(characterId)
        val selected = RegexRuleScope.entries.flatMap { scope ->
            collection.rulesForScope(scope)
                .filter { it.id in ruleIds }
                .map { rule -> RegexRuleJsonCodec.ruleToJson(rule).put("scope", scope.name) }
        }
        if (selected.isEmpty()) throw ElecKoiDataException("请先选择要导出的规则")
        return JSONObject()
            .put("format", ExportFormat)
            .put("version", 2)
            .put("rules", JSONArray(selected))
            .toString(2)
    }

    /** Lossless snapshot for the app-level backup; unlike card export it includes every scope. */
    fun exportBackupJson(characterId: String): String {
        val collection = load(characterId)
        return JSONObject()
            .put("format", "eleckoi.regex-backup")
            .put("version", 1)
            .put("character_id", characterId)
            .put("global_rules", JSONArray(collection.globalRules.map(RegexRuleJsonCodec::ruleToJson)))
            .put("prompt_preset_rules", JSONArray(collection.promptPresetRules.map(RegexRuleJsonCodec::ruleToJson)))
            .put("character_rules", JSONArray(collection.characterRules.map(RegexRuleJsonCodec::ruleToJson)))
            .put(
                "versions",
                JSONArray(collection.versions.map(::versionJson)),
            )
            .put("active_version_id", collection.activeVersionId)
            .toString(2)
    }

    /** Restores one character's complete regex state from a backup section. */
    fun restoreBackupJson(characterId: String, json: String): RegexRuleCollection {
        requireCharacter(characterId)
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw ElecKoiDataException("正则备份已损坏", it) }
        require(root.optString("format") == "eleckoi.regex-backup") {
            "正则备份格式不正确"
        }
        require(root.optInt("version", -1) == 1) { "不支持的正则备份版本" }
        val versions = root.optJSONArray("versions")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.toVersion(index)
            }
        }.orEmpty()
        return save(
            characterId,
            RegexRuleCollection(
                globalRules = RegexRuleJsonCodec.decodeRules(root.optJSONArray("global_rules")),
                promptPresetRules = RegexRuleJsonCodec.decodeRules(root.optJSONArray("prompt_preset_rules")),
                characterRules = RegexRuleJsonCodec.decodeRules(root.optJSONArray("character_rules")),
                versions = versions,
                activeVersionId = root.stringOrEmpty("active_version_id"),
            ),
        )
    }

    fun rulesFor(characterId: String, target: RegexRuleTarget, surface: RegexRuleSurface): List<RegexRule> {
        return rulesFor(load(characterId), target, surface)
    }

    fun notifyActivePresetChanged() {
        database.runInTransaction { bumpPersistedRevision() }
        mutableRevision.update { current -> current + 1L }
    }

    fun rulesFor(
        config: RegexRuleCollection,
        target: RegexRuleTarget,
        surface: RegexRuleSurface,
    ): List<RegexRule> {
        return RegexRuleScope.entries.flatMap { scope ->
            config.rulesForScope(scope).filter { rule ->
                rule.enabled && target in rule.targets && rule.appliesTo(surface, target) &&
                    config.versionAllows(scope, rule.id)
            }
        }
    }

    private fun RegexRuleCollection.versionAllows(scope: RegexRuleScope, ruleId: String): Boolean {
        // Preset rules carry their own enabled state with the preset. A regex-version captured for
        // another preset must never silently disable every rule after the active preset changes.
        if (scope == RegexRuleScope.PromptPreset) return true
        val version = versions.firstOrNull { it.id == activeVersionId } ?: return true
        return ruleId in when (scope) {
            RegexRuleScope.Global -> version.globalEnabledIds
            RegexRuleScope.PromptPreset -> error("handled above")
            RegexRuleScope.Character -> version.characterEnabledIds
        }
    }

    private fun RegexRuleCollection.rulesForScope(scope: RegexRuleScope): List<RegexRule> = when (scope) {
        RegexRuleScope.Global -> globalRules
        RegexRuleScope.PromptPreset -> promptPresetRules
        RegexRuleScope.Character -> characterRules
    }

    private fun RegexRuleCollection.withScopeRules(scope: RegexRuleScope, rules: List<RegexRule>): RegexRuleCollection = when (scope) {
        RegexRuleScope.Global -> copy(globalRules = rules)
        RegexRuleScope.PromptPreset -> copy(promptPresetRules = rules)
        RegexRuleScope.Character -> copy(characterRules = rules)
    }

    private fun RegexRuleCollection.normalized(): RegexRuleCollection = copy(
        globalRules = globalRules.normalizedRegexRules(),
        promptPresetRules = promptPresetRules.normalizedRegexRules(),
        characterRules = characterRules.normalizedRegexRules(),
        versions = versions.map(::normalizeVersion),
        activeVersionId = activeVersionId.takeIf { id -> versions.any { it.id == id } }.orEmpty(),
    )

    private fun normalizeVersion(version: RegexRuleVersion): RegexRuleVersion = version.copy(
        id = version.id.ifBlank { "regex-version-${newId(10)}" },
        name = version.name.trim().take(60).ifBlank { "未命名版本" },
    )

    private fun versionJson(version: RegexRuleVersion): JSONObject = JSONObject()
        .put("id", version.id).put("name", version.name)
        .put("global_enabled_ids", JSONArray(version.globalEnabledIds))
        .put("prompt_preset_enabled_ids", JSONArray(version.promptPresetEnabledIds))
        .put("character_enabled_ids", JSONArray(version.characterEnabledIds))

    private fun JSONArray?.rules(): List<RegexRule> = RegexRuleJsonCodec.decodeRules(this)

    private fun JSONArray?.versions(): List<RegexRuleVersion> = this?.let { values ->
        (0 until values.length()).mapNotNull { index -> values.optJSONObject(index)?.toVersion(index) }
    }.orEmpty()

    private fun JSONObject.toVersion(index: Int): RegexRuleVersion = RegexRuleVersion(
        id = stringOrEmpty("id").ifBlank { "regex-version-${newId(10)}-$index" },
        name = stringOrEmpty("name"),
        globalEnabledIds = optJSONArray("global_enabled_ids").stringSet(),
        promptPresetEnabledIds = optJSONArray("prompt_preset_enabled_ids").stringSet(),
        characterEnabledIds = optJSONArray("character_enabled_ids").stringSet(),
    )

    private fun JSONArray?.stringSet(): Set<String> = this?.let { values ->
        (0 until values.length()).map { values.optString(it).trim() }.filter(String::isNotBlank).toSet()
    }.orEmpty()

    private fun requireCharacter(characterId: String) {
        if (characters.characterById(characterId) == null) throw ElecKoiDataException("角色不存在")
    }

    /** Global data must also survive a backup with no character cards. */
    fun exportSharedBackupJson(): String {
        return database.runInTransaction<String> { JSONObject()
            .put("format", "eleckoi.shared-regex-backup")
            .put("version", 1)
            .put("global_rules", JSONArray(dao.globalRules().map { RegexRuleJsonCodec.ruleToJson(it.rule.toRule(it.id)) }))
            .put("versions", JSONArray(dao.versions().map { versionJson(it.toVersion()) }))
            .put("active_version_id", dao.state()?.activeVersionId.orEmpty())
            .toString(2)
        }
    }

    fun restoreSharedBackupJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "eleckoi.shared-regex-backup" && root.optInt("version") == 1) {
            "全局正则备份格式不正确"
        }
        saveShared(RegexRuleCollection(
            globalRules = root.optJSONArray("global_rules").rules(),
            versions = root.optJSONArray("versions").versions(),
            activeVersionId = root.stringOrEmpty("active_version_id"),
        ).normalized())
        mutableRevision.update { it + 1L }
    }

    private fun saveShared(collection: RegexRuleCollection) {
        dao.saveShared(
            collection.globalRules.map { GlobalRegexRuleEntity(it.id, it.toRoomFields()) },
            collection.versions.mapIndexed { index, version -> version.toRoomVersion(index) },
            collection.activeVersionId,
        )
    }

    private fun bumpPersistedRevision() {
        if (dao.bumpRevision() == 0) dao.upsertState(RegexStateEntity(revision = 1L, activeVersionId = null))
    }

    private companion object {
        const val ExportFormat = "eleckoi.regex-rules-export"
    }
}

internal data class DecodedRegexImportDocuments(
    val importedByFile: List<List<ScopedRegexRule>>,
    val failedFileNames: List<String>,
    val skippedDepthRuleCount: Int,
)

private data class RegexSaveResult(
    val value: VersionedRegexRuleCollection,
    val changed: Boolean,
)

internal fun decodeRegexImportDocuments(
    documents: List<RegexRuleImportDocument>,
    fallbackScope: RegexRuleScope,
): DecodedRegexImportDocuments {
    val failedFileNames = mutableListOf<String>()
    val importedByFile = mutableListOf<List<ScopedRegexRule>>()
    var skippedDepthRuleCount = 0
    documents.forEach { document ->
        runCatching { RegexRuleImportCodec.decodeScopedWithReport(document.json, fallbackScope) }
            .onSuccess { decoded ->
                skippedDepthRuleCount += decoded.skippedDepthRuleCount
                if (decoded.rules.isNotEmpty()) importedByFile += decoded.rules
            }
            .onFailure { failedFileNames += document.displayName }
    }
    return DecodedRegexImportDocuments(importedByFile, failedFileNames, skippedDepthRuleCount)
}

internal fun List<RegexRule>.normalizedRegexRules(): List<RegexRule> = mapIndexed { index, rule ->
    rule.copy(
        id = rule.id.ifBlank { "regex-${newId(10)}" },
        name = rule.name.trim().take(60),
        pattern = rule.pattern.take(4_000),
        replacement = rule.replacement,
        targets = rule.targets.ifEmpty { setOf(RegexRuleTarget.AiOutput) },
        order = index,
    )
}

internal fun RegexRule.appliesTo(
    surface: RegexRuleSurface,
    target: RegexRuleTarget,
): Boolean = when (surface) {
    RegexRuleSurface.Stored -> !displayOnly && !promptOnly
    RegexRuleSurface.Display -> displayOnly || (!displayOnly && !promptOnly)
    RegexRuleSurface.Prompt -> promptOnly ||
        (target == RegexRuleTarget.SettingContent && !displayOnly && !promptOnly)
}

object RegexRuleImportCodec {
    fun decode(json: String): List<RegexRule> {
        return decodeScoped(json, RegexRuleScope.Global).map(ScopedRegexRule::rule)
    }

    fun decodeScoped(json: String, fallbackScope: RegexRuleScope): List<ScopedRegexRule> {
        val decoded = decodeScopedWithReport(json, fallbackScope)
        if (decoded.rules.isEmpty() && decoded.skippedDepthRuleCount > 0) {
            throw ElecKoiDataException("深度正则不受支持")
        }
        return decoded.rules.ifEmpty { throw ElecKoiDataException("文件里没有有效规则") }
    }

    internal fun decodeScopedWithReport(
        json: String,
        fallbackScope: RegexRuleScope,
    ): DecodedScopedRegexRules {
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
        var skippedDepthRuleCount = 0
        val rules = (0 until values.length()).mapNotNull { index ->
            values.optJSONObject(index)?.let { item ->
                if (item.hasUnsupportedRegexDepth()) {
                    skippedDepthRuleCount += 1
                    null
                } else {
                    ScopedRegexRule(
                        scope = runCatching {
                            RegexRuleScope.valueOf(item.stringOrEmpty("scope"))
                        }.getOrDefault(fallbackScope),
                        rule = RegexRule(
                            name = item.stringOrEmpty("name").ifBlank { item.stringOrEmpty("scriptName") },
                            pattern = item.stringOrEmpty("pattern").ifBlank { item.stringOrEmpty("findRegex") },
                            replacement = item.stringOrEmpty("replacement")
                                .ifBlank { item.stringOrEmpty("replaceString") },
                            targets = importedTargets(item),
                            enabled = !item.optBoolean("disabled", false) && item.optBoolean("enabled", true),
                            displayOnly = item.optBoolean("display_only", item.optBoolean("markdownOnly", false)),
                            promptOnly = item.optBoolean("prompt_only", item.optBoolean("promptOnly", false)),
                            runOnEdit = item.optBoolean("run_on_edit", item.optBoolean("runOnEdit", false)),
                            order = index,
                        ),
                    )
                }
            }
        }.filter { it.rule.pattern.isNotBlank() }
        return DecodedScopedRegexRules(rules, skippedDepthRuleCount)
    }
}

internal data class DecodedScopedRegexRules(
    val rules: List<ScopedRegexRule>,
    val skippedDepthRuleCount: Int,
)

data class ScopedRegexRule(val scope: RegexRuleScope, val rule: RegexRule)

private fun importedTargets(item: JSONObject): Set<RegexRuleTarget> {
    val named = item.optJSONArray("targets")?.let { values ->
        (0 until values.length()).mapNotNull { index ->
            runCatching { RegexRuleTarget.valueOf(values.optString(index)) }.getOrNull()
        }.toSet()
    }.orEmpty()
    return named.ifEmpty { placementTargets(item.optJSONArray("placement")) }
}

private fun placementTargets(placement: JSONArray?): Set<RegexRuleTarget> {
    val values = placement?.let { array -> (0 until array.length()).map { array.optInt(it, -1) }.toSet() }.orEmpty()
    return buildSet {
        if (1 in values) add(RegexRuleTarget.UserInput)
        if (2 in values || values.isEmpty()) add(RegexRuleTarget.AiOutput)
        if (3 in values) add(RegexRuleTarget.SlashCommand)
        if (5 in values) add(RegexRuleTarget.SettingContent)
        if (6 in values) add(RegexRuleTarget.Reasoning)
    }
}
