package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_configs ORDER BY rowid ASC")
    fun configs(): List<ModelConfigEntity>

    @Query("SELECT * FROM model_configs ORDER BY rowid ASC")
    fun configsFlow(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM model_configs WHERE id = :configId LIMIT 1")
    fun config(configId: String): ModelConfigEntity?

    @Query("SELECT * FROM model_config_meta WHERE id = 'default' LIMIT 1")
    fun meta(): ModelConfigMetaEntity?

    @Query("SELECT * FROM model_config_meta WHERE id = 'default' LIMIT 1")
    fun metaFlow(): Flow<ModelConfigMetaEntity?>

    @Upsert
    fun upsertConfig(config: ModelConfigEntity)

    @Upsert
    fun upsertConfigs(configs: List<ModelConfigEntity>)

    @Query(
        "UPDATE model_configs SET enabled = 0 " +
            "WHERE provider = :providerId AND id != :exceptConfigId AND enabled != 0",
    )
    fun disableOtherProviderConfigs(providerId: String, exceptConfigId: String)

    @Upsert
    fun upsertMeta(meta: ModelConfigMetaEntity)

    @Query("DELETE FROM model_configs WHERE id = :configId")
    fun deleteConfig(configId: String)

    @Query("DELETE FROM model_configs")
    fun deleteAllConfigs()
}
