package com.eleckoi.android.app.service.cleanup

import com.eleckoi.android.foundation.storage.PersistentCleanupRunner
import com.eleckoi.android.foundation.storage.room.CleanupOperationDao
import com.eleckoi.android.foundation.storage.room.CleanupOperationEntity
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Durable cross-store deletion boundary. Completed operations leave no ledger row behind. */
internal class PersistentCleanupQueue(
    private val dao: CleanupOperationDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : PersistentCleanupRunner {
    constructor(database: ElecKoiDatabase) : this(database.cleanupOperationDao())

    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun <T> run(kind: String, targetId: String, action: suspend () -> T): T {
        require(kind.isNotBlank() && targetId.isNotBlank()) { "清理任务缺少目标" }
        val key = "$kind:$targetId"
        val lock = locks.getOrPut(key, ::Mutex)
        try {
            return lock.withLock { withContext(Dispatchers.IO) {
                val timestamp = now()
                dao.insert(
                    CleanupOperationEntity(
                        id = newId(),
                        kind = kind,
                        targetId = targetId,
                        state = "queued",
                        attemptCount = 0,
                        createdAtEpochMs = timestamp,
                        updatedAtEpochMs = timestamp,
                        lastError = "",
                    ),
                )
                val operation = requireNotNull(dao.operation(kind, targetId))
                dao.markRunning(operation.id, now())
                try {
                    val result = action()
                    dao.delete(operation.id)
                    result
                } catch (error: Throwable) {
                    dao.markFailed(
                        operation.id,
                        now(),
                        error.message.orEmpty().take(MaxStoredErrorLength),
                    )
                    throw error
                }
            } }
        } finally {
            locks.remove(key, lock)
        }
    }

    suspend fun resume(handler: suspend (CleanupOperationEntity) -> Unit) {
        val pending = withContext(Dispatchers.IO) { dao.pending() }
        pending.forEach { operation ->
            try {
                run(operation.kind, operation.targetId) { handler(operation) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The failed row retains its error and the next startup continues with it.
            }
        }
    }

    private companion object {
        const val MaxStoredErrorLength = 2_000
    }
}
