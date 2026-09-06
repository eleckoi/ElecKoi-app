package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RoleplayRichHeightDao {
    @Query("SELECT * FROM roleplay_rich_heights WHERE sessionId = :sessionId")
    suspend fun heightsForSession(sessionId: String): List<RoleplayRichHeightEntity>

    @Upsert
    suspend fun upsert(height: RoleplayRichHeightEntity)

    @Query(
        "DELETE FROM roleplay_rich_heights " +
            "WHERE sessionId = :sessionId AND messageId = :messageId " +
            "AND contentRevision != :contentRevision",
    )
    suspend fun deleteOtherRevisions(
        sessionId: String,
        messageId: String,
        contentRevision: String,
    )
}
