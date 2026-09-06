package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AgentToolConfigDao {
    @Query("SELECT * FROM global_tool_config WHERE singletonId = 1 LIMIT 1")
    fun global(): GlobalToolConfigEntity?

    @Query("SELECT * FROM character_tool_configs ORDER BY characterId")
    fun characters(): List<CharacterToolConfigEntity>

    @Upsert
    fun upsertGlobal(config: GlobalToolConfigEntity)

    @Upsert
    fun upsertCharacters(configs: List<CharacterToolConfigEntity>)

    @Query("DELETE FROM character_tool_configs WHERE characterId IN (:characterIds)")
    fun deleteCharacters(characterIds: List<String>)

}
