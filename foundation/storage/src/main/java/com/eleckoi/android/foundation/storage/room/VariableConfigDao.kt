package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VariableConfigDao {
    @Transaction
    @Query("SELECT * FROM variable_configs WHERE characterId = :characterId LIMIT 1")
    fun config(characterId: String): VariableConfigRecord?

    @Transaction
    @Query("SELECT * FROM variable_configs WHERE characterId = :characterId LIMIT 1")
    fun configFlow(characterId: String): Flow<VariableConfigRecord?>

    @Transaction
    fun upsert(record: VariableConfigRecord) {
        val owner = record.config.characterId
        require(record.versions.isNotEmpty()) { "变量配置至少需要一个版本" }
        require(record.versions.all { it.characterId == owner }) { "变量版本不属于此角色" }
        val ids = record.versions.map { it.versionId }.toSet()
        require(ids.size == record.versions.size && ids.none(String::isBlank)) { "变量版本编号无效或重复" }
        require(record.config.activeVersionId in ids) { "当前变量版本不存在" }
        val previous = config(owner)
        if (previous?.config != record.config) upsertMetadata(record.config)
        val oldVersions = previous?.versions.orEmpty().associateBy { it.versionId }
        val changed = record.versions.filter { oldVersions[it.versionId] != it }
        if (changed.isNotEmpty()) upsertVersions(changed)
        (oldVersions.keys - ids).forEach { deleteVersion(owner, it) }

        require(record.contents.all { it.characterId == owner && it.versionId in ids }) {
            "变量内容不属于此配置"
        }
        require(record.objects.all { it.characterId == owner && it.versionId in ids }) {
            "变量对象不属于此配置"
        }
        require(record.variables.all { it.characterId == owner && it.versionId in ids }) {
            "变量条目不属于此配置"
        }
        syncContents(owner, previous?.contents.orEmpty().filter { it.versionId in ids }, record.contents)
        syncObjects(owner, previous?.objects.orEmpty().filter { it.versionId in ids }, record.objects)
        syncVariables(owner, previous?.variables.orEmpty().filter { it.versionId in ids }, record.variables)
    }

    @Upsert
    fun upsertMetadata(config: VariableConfigEntity)

    @Upsert
    fun upsertVersions(versions: List<VariableConfigVersionEntity>)

    @Upsert
    fun upsertContents(contents: List<VariableConfigVersionContentEntity>)

    @Upsert
    fun upsertObjects(objects: List<VariableConfigObjectEntity>)

    @Upsert
    fun upsertVariables(variables: List<VariableConfigVariableEntity>)

    @Query("DELETE FROM variable_config_version_contents WHERE characterId = :characterId AND versionId = :versionId AND kind IN (:kinds)")
    fun deleteContents(characterId: String, versionId: String, kinds: List<String>)

    @Query("DELETE FROM variable_config_objects WHERE characterId = :characterId AND versionId = :versionId AND objectId IN (:objectIds)")
    fun deleteObjects(characterId: String, versionId: String, objectIds: List<String>)

    @Query("DELETE FROM variable_config_variables WHERE characterId = :characterId AND versionId = :versionId AND variableId IN (:variableIds)")
    fun deleteVariables(characterId: String, versionId: String, variableIds: List<String>)

    @Query("DELETE FROM variable_config_versions WHERE characterId = :characterId AND versionId = :versionId")
    fun deleteVersion(characterId: String, versionId: String)

    @Query("DELETE FROM variable_configs WHERE characterId IN (:characterIds)")
    fun deleteForCharacters(characterIds: List<String>)

    @Query("DELETE FROM variable_configs WHERE characterId NOT IN (:characterIds)")
    fun deleteExceptCharacters(characterIds: List<String>)

    @Query("DELETE FROM variable_configs")
    fun deleteAll()

    private fun syncContents(
        owner: String,
        old: List<VariableConfigVersionContentEntity>,
        next: List<VariableConfigVersionContentEntity>,
    ) {
        val oldByKey = old.associateBy { it.versionId to it.kind }
        val nextKeys = next.mapTo(mutableSetOf()) { it.versionId to it.kind }
        (oldByKey.keys - nextKeys).groupBy { it.first }.forEach { (versionId, keys) ->
            keys.map { it.second }.chunked(DeleteBatchSize).forEach {
                deleteContents(owner, versionId, it)
            }
        }
        next.filter { it != oldByKey[it.versionId to it.kind] }
            .chunked(DeleteBatchSize).forEach(::upsertContents)
    }

    private fun syncObjects(
        owner: String,
        old: List<VariableConfigObjectEntity>,
        next: List<VariableConfigObjectEntity>,
    ) {
        val oldByKey = old.associateBy { it.versionId to it.objectId }
        val nextKeys = next.mapTo(mutableSetOf()) { it.versionId to it.objectId }
        (oldByKey.keys - nextKeys).groupBy { it.first }.forEach { (versionId, keys) ->
            keys.map { it.second }.chunked(DeleteBatchSize).forEach {
                deleteObjects(owner, versionId, it)
            }
        }
        next.filter { it != oldByKey[it.versionId to it.objectId] }
            .chunked(DeleteBatchSize).forEach(::upsertObjects)
    }

    private fun syncVariables(
        owner: String,
        old: List<VariableConfigVariableEntity>,
        next: List<VariableConfigVariableEntity>,
    ) {
        val oldByKey = old.associateBy { it.versionId to it.variableId }
        val nextKeys = next.mapTo(mutableSetOf()) { it.versionId to it.variableId }
        (oldByKey.keys - nextKeys).groupBy { it.first }.forEach { (versionId, keys) ->
            keys.map { it.second }.chunked(DeleteBatchSize).forEach {
                deleteVariables(owner, versionId, it)
            }
        }
        next.filter { it != oldByKey[it.versionId to it.variableId] }
            .chunked(DeleteBatchSize).forEach(::upsertVariables)
    }

    private companion object {
        const val DeleteBatchSize = 900
    }
}
