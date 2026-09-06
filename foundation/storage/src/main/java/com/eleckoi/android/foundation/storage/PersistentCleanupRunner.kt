package com.eleckoi.android.foundation.storage

interface PersistentCleanupRunner {
    suspend fun <T> run(kind: String, targetId: String, action: suspend () -> T): T
}

object ImmediateCleanupRunner : PersistentCleanupRunner {
    override suspend fun <T> run(kind: String, targetId: String, action: suspend () -> T): T = action()
}
