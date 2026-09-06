package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CleanupOperationDao {
    @Query("SELECT * FROM cleanup_operations ORDER BY createdAtEpochMs, id")
    fun pending(): List<CleanupOperationEntity>

    @Query("SELECT * FROM cleanup_operations WHERE kind = :kind AND targetId = :targetId LIMIT 1")
    fun operation(kind: String, targetId: String): CleanupOperationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(operation: CleanupOperationEntity): Long

    @Query("""
        UPDATE cleanup_operations
        SET state = 'running', attemptCount = attemptCount + 1,
            updatedAtEpochMs = :updatedAtEpochMs, lastError = ''
        WHERE id = :id
    """)
    fun markRunning(id: String, updatedAtEpochMs: Long)

    @Query("""
        UPDATE cleanup_operations
        SET state = 'failed', updatedAtEpochMs = :updatedAtEpochMs, lastError = :error
        WHERE id = :id
    """)
    fun markFailed(id: String, updatedAtEpochMs: Long, error: String)

    @Query("DELETE FROM cleanup_operations WHERE id = :id")
    fun delete(id: String)
}
