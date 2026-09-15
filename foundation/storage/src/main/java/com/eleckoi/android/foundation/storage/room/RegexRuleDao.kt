package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface RegexRuleDao {
    @Transaction
    fun globalRules(): List<GlobalRegexRuleEntity> = globalRuleMetadata().map { metadata ->
        GlobalRegexRuleEntity(
            id = metadata.id,
            rule = readRegexRuleFields(metadata) { start, length ->
                globalRuleTextChunk(metadata.id, start, length)
            },
        )
    }

    @Transaction
    fun characterRules(characterId: String): List<CharacterRegexRuleEntity> =
        characterRuleMetadata(characterId).map { row ->
            CharacterRegexRuleEntity(
                characterId = row.characterId,
                id = row.metadata.id,
                rule = readRegexRuleFields(row.metadata) { start, length ->
                    characterRuleTextChunk(row.characterId, row.metadata.id, start, length)
                },
            )
        }

    @Query(
        """
        SELECT id, enabled, displayOnly, promptOnly, runOnEdit, sortIndex,
            length(name) AS nameLength,
            length(pattern) AS patternLength,
            length(replacement) AS replacementLength,
            length(targetsJson) AS targetsJsonLength
        FROM global_regex_rules
        ORDER BY sortIndex, id
        """,
    )
    fun globalRuleMetadata(): List<RegexRuleReadMetadata>

    @Query(
        """
        SELECT characterId, id, enabled, displayOnly, promptOnly, runOnEdit, sortIndex,
            length(name) AS nameLength,
            length(pattern) AS patternLength,
            length(replacement) AS replacementLength,
            length(targetsJson) AS targetsJsonLength
        FROM character_regex_rules
        WHERE characterId = :characterId
        ORDER BY sortIndex, id
        """,
    )
    fun characterRuleMetadata(characterId: String): List<CharacterRegexRuleReadMetadata>

    @Query(
        """
        SELECT
            substr(name, :start, :length) AS nameChunk,
            substr(pattern, :start, :length) AS patternChunk,
            substr(replacement, :start, :length) AS replacementChunk,
            substr(targetsJson, :start, :length) AS targetsJsonChunk
        FROM global_regex_rules
        WHERE id = :id
        """,
    )
    fun globalRuleTextChunk(id: String, start: Int, length: Int): RegexRuleTextChunk

    @Query(
        """
        SELECT
            substr(name, :start, :length) AS nameChunk,
            substr(pattern, :start, :length) AS patternChunk,
            substr(replacement, :start, :length) AS replacementChunk,
            substr(targetsJson, :start, :length) AS targetsJsonChunk
        FROM character_regex_rules
        WHERE characterId = :characterId AND id = :id
        """,
    )
    fun characterRuleTextChunk(
        characterId: String,
        id: String,
        start: Int,
        length: Int,
    ): RegexRuleTextChunk

    @Query("SELECT * FROM regex_enablement_versions ORDER BY sortIndex, id")
    fun versions(): List<RegexEnablementVersionEntity>

    @Query("SELECT * FROM regex_state WHERE singletonId = 0")
    fun state(): RegexStateEntity?

    @Transaction
    fun saveShared(
        rules: List<GlobalRegexRuleEntity>,
        selections: List<RegexEnablementVersionEntity>,
        activeVersionId: String,
    ) {
        require(rules.map { it.id }.toSet().size == rules.size) { "全局正则编号重复" }
        require(selections.map { it.id }.toSet().size == selections.size) { "正则版本编号重复" }
        require(activeVersionId.isBlank() || selections.any { it.id == activeVersionId }) { "正则版本不存在" }
        val oldRules = globalRules().associateBy { it.id }
        val newIds = rules.map { it.id }.toSet()
        (oldRules.keys - newIds).forEach(::deleteGlobalRule)
        rules.filter { oldRules[it.id] != it }.takeIf { it.isNotEmpty() }?.let(::upsertGlobalRules)
        val oldVersions = versions().associateBy { it.id }
        val versionIds = selections.map { it.id }.toSet()
        (oldVersions.keys - versionIds).forEach(::deleteVersion)
        selections.filter { oldVersions[it.id] != it }.takeIf { it.isNotEmpty() }?.let(::upsertVersions)
        val state = RegexStateEntity(
            activeVersionId = activeVersionId.takeIf(String::isNotBlank),
            revision = (state()?.revision ?: 0L) + 1L,
        )
        if (state() != state) upsertState(state)
    }

    @Transaction
    fun saveCharacter(characterId: String, rules: List<CharacterRegexRuleEntity>) {
        require(rules.all { it.characterId == characterId }) { "角色正则归属不一致" }
        require(rules.map { it.id }.toSet().size == rules.size) { "角色正则编号重复" }
        val old = characterRules(characterId).associateBy { it.id }
        val ids = rules.map { it.id }.toSet()
        (old.keys - ids).forEach { deleteCharacterRule(characterId, it) }
        rules.filter { old[it.id] != it }.takeIf { it.isNotEmpty() }?.let(::upsertCharacterRules)
    }

    @Upsert fun upsertGlobalRules(rules: List<GlobalRegexRuleEntity>)
    @Upsert fun upsertCharacterRules(rules: List<CharacterRegexRuleEntity>)
    @Upsert fun upsertVersions(versions: List<RegexEnablementVersionEntity>)
    @Upsert fun upsertState(state: RegexStateEntity)

    @Query("UPDATE regex_state SET revision = revision + 1 WHERE singletonId = 0")
    fun bumpRevision(): Int

    @Query("DELETE FROM global_regex_rules WHERE id = :id")
    fun deleteGlobalRule(id: String)

    @Query("DELETE FROM character_regex_rules WHERE characterId = :characterId AND id = :id")
    fun deleteCharacterRule(characterId: String, id: String)

    @Query("DELETE FROM regex_enablement_versions WHERE id = :id")
    fun deleteVersion(id: String)

    @Query("DELETE FROM character_regex_rules WHERE characterId IN (:characterIds)")
    fun deleteForCharacters(characterIds: List<String>): Int

    @Query("SELECT DISTINCT characterId FROM character_regex_rules")
    fun characterOwners(): List<String>
}

internal const val RegexRuleCursorChunkCharacters = 32 * 1024

internal fun readRegexRuleFields(
    metadata: RegexRuleReadMetadata,
    readChunk: (start: Int, length: Int) -> RegexRuleTextChunk,
): RegexRuleFields {
    val name = StringBuilder()
    val pattern = StringBuilder()
    val replacement = StringBuilder()
    val targetsJson = StringBuilder()
    val longestField = maxOf(
        metadata.nameLength,
        metadata.patternLength,
        metadata.replacementLength,
        metadata.targetsJsonLength,
    )
    var start = 1 // SQLite substr uses one-based character positions.
    while (start <= longestField) {
        val chunk = readChunk(start, RegexRuleCursorChunkCharacters)
        name.append(chunk.nameChunk)
        pattern.append(chunk.patternChunk)
        replacement.append(chunk.replacementChunk)
        targetsJson.append(chunk.targetsJsonChunk)
        start += RegexRuleCursorChunkCharacters
    }
    return RegexRuleFields(
        name = name.toString(),
        pattern = pattern.toString(),
        replacement = replacement.toString(),
        targetsJson = targetsJson.toString(),
        enabled = metadata.enabled,
        displayOnly = metadata.displayOnly,
        promptOnly = metadata.promptOnly,
        runOnEdit = metadata.runOnEdit,
        sortIndex = metadata.sortIndex,
    )
}
