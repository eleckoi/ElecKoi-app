package com.eleckoi.android.app.service.cleanup

import com.eleckoi.android.foundation.storage.room.CleanupOperationDao
import com.eleckoi.android.foundation.storage.room.CleanupOperationEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentCleanupQueueTest {
    @Test
    fun `failed work remains durable and resume removes it after success`() = runBlocking {
        val dao = FakeCleanupOperationDao()
        var clock = 10L
        var ids = 0
        val queue = PersistentCleanupQueue(dao, { clock++ }, { "op-${ids++}" })

        val failure = runCatching {
            queue.run("character", "card-a") { error("disk busy") }
        }.exceptionOrNull()

        assertEquals("disk busy", failure?.message)
        assertEquals("failed", dao.rows.single().state)
        assertEquals(1, dao.rows.single().attemptCount)
        assertEquals("disk busy", dao.rows.single().lastError)

        val resumed = mutableListOf<String>()
        queue.resume { operation -> resumed += operation.targetId }

        assertEquals(listOf("card-a"), resumed)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `resume continues after one target fails`() = runBlocking {
        val dao = FakeCleanupOperationDao().apply {
            insert(queuedOperation("a", "workspace-a"))
            insert(queuedOperation("b", "workspace-b"))
        }
        val queue = PersistentCleanupQueue(dao)
        val attempted = mutableListOf<String>()

        queue.resume { operation ->
            attempted += operation.targetId
            if (operation.targetId == "workspace-a") error("still locked")
        }

        assertEquals(listOf("workspace-a", "workspace-b"), attempted)
        assertEquals(listOf("workspace-a"), dao.rows.map { it.targetId })
        assertEquals("failed", dao.rows.single().state)
    }

    private fun queuedOperation(id: String, targetId: String) = CleanupOperationEntity(
        id = id,
        kind = "creator_workspace",
        targetId = targetId,
        state = "queued",
        attemptCount = 0,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
        lastError = "",
    )
}

private class FakeCleanupOperationDao : CleanupOperationDao {
    val rows = mutableListOf<CleanupOperationEntity>()

    override fun pending(): List<CleanupOperationEntity> = rows.sortedWith(
        compareBy(CleanupOperationEntity::createdAtEpochMs, CleanupOperationEntity::id),
    )

    override fun operation(kind: String, targetId: String): CleanupOperationEntity? =
        rows.firstOrNull { it.kind == kind && it.targetId == targetId }

    override fun insert(operation: CleanupOperationEntity): Long {
        if (operation(operation.kind, operation.targetId) != null) return -1
        rows += operation
        return rows.size.toLong()
    }

    override fun markRunning(id: String, updatedAtEpochMs: Long) = update(id) {
        it.copy(
            state = "running",
            attemptCount = it.attemptCount + 1,
            updatedAtEpochMs = updatedAtEpochMs,
            lastError = "",
        )
    }

    override fun markFailed(id: String, updatedAtEpochMs: Long, error: String) = update(id) {
        it.copy(state = "failed", updatedAtEpochMs = updatedAtEpochMs, lastError = error)
    }

    override fun delete(id: String) {
        rows.removeAll { it.id == id }
    }

    private fun update(id: String, transform: (CleanupOperationEntity) -> CleanupOperationEntity) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = transform(rows[index])
    }
}
