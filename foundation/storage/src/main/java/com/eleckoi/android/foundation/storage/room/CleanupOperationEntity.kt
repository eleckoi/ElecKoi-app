package com.eleckoi.android.foundation.storage.room

import androidx.room.Entity
import androidx.room.Index

/** A tombstone intentionally has no owner FK because it must survive partial owner deletion. */
@Entity(
    tableName = "cleanup_operations",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["kind", "targetId"], unique = true),
        Index(value = ["state", "updatedAtEpochMs"]),
    ],
)
data class CleanupOperationEntity(
    val id: String,
    val kind: String,
    val targetId: String,
    val state: String,
    val attemptCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastError: String,
)
