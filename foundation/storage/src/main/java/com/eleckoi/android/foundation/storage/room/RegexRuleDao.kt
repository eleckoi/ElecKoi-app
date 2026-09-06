package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface RegexRuleDao {
    @Query("SELECT * FROM global_regex_rules ORDER BY sortIndex, id")
    fun globalRules(): List<GlobalRegexRuleEntity>

    @Query("SELECT * FROM character_regex_rules WHERE characterId = :characterId ORDER BY sortIndex, id")
    fun characterRules(characterId: String): List<CharacterRegexRuleEntity>

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
