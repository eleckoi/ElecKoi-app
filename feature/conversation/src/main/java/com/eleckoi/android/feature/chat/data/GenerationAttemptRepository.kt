package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.agent.entity.GenerationAttemptEntity
import java.util.concurrent.ConcurrentHashMap

internal enum class GenerationAttemptKind(val storageValue: String) {
    Reply("reply"),
    Image("image"),
}

internal enum class GenerationAttemptState(val storageValue: String) {
    Queued("queued"),
    Running("running"),
    Succeeded("succeeded"),
    Failed("failed"),
    Cancelled("cancelled"),
    Interrupted("interrupted"),
    Superseded("superseded"),
}

class GenerationAttempt internal constructor(
    val id: String,
    internal val conversationId: String,
    internal val kind: GenerationAttemptKind,
    internal val ownerId: String,
    internal val parentAttemptId: String?,
    internal val outputMessageId: String,
    internal val attemptNumber: Int,
    internal val state: GenerationAttemptState,
    internal val errorMessage: String,
    internal val outputPath: String,
)

/** Narrow boundary used by the one-way image action and its deterministic unit tests. */
internal interface ImageGenerationAttemptStore {
    fun beginImageAttempt(
        conversationId: String,
        attachmentId: String,
        outputMessageId: String,
        parentAttemptId: String?,
    ): String

    fun markImageRunning(attemptId: String): Boolean
    fun markImageSucceeded(attemptId: String, outputPath: String): Boolean
    fun markImageFailed(attemptId: String, errorMessage: String): Boolean
    fun markImageCancelled(attemptId: String, reason: String): Boolean
}

/**
 * The process-local ownership set and the durable attempt ledger meet here.
 *
 * An image is inserted as `queued` and becomes `running` only after its worker obtains the serial
 * generation permit. Live rows are process-owned; after restart the ownership set is empty, so
 * recovery can deterministically settle every abandoned row. Regeneration creates a new id and
 * supersedes the old row before any result can be committed.
 */
class GenerationAttemptRepository(
    private val database: ElecKoiDatabase,
) : ImageGenerationAttemptStore {
    private val dao = database.generationAttemptDao()
    private val activeAttemptIds = ConcurrentHashMap.newKeySet<String>()
    private val mutationLock = Any()

    internal fun deleteForMessagesInTransaction(
        conversationId: String,
        messageIds: List<String>,
    ): Set<String> {
        check(database.inTransaction()) { "生成记录必须与聊天分支在同一事务中删除" }
        if (messageIds.isEmpty()) return emptySet()
        val removed = dao.forMessages(conversationId, messageIds)
        dao.deleteForMessages(conversationId, messageIds)
        removed.forEach { activeAttemptIds.remove(it.id) }
        return removed.mapNotNull { it.outputPath.takeIf(String::isNotBlank) }.toSet()
    }

    fun beginReply(
        conversationId: String,
        userMessageId: String,
        outputMessageId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): GenerationAttempt = begin(
        conversationId = conversationId,
        kind = GenerationAttemptKind.Reply,
        ownerId = userMessageId,
        parentAttemptId = null,
        outputMessageId = outputMessageId,
        nowMillis = nowMillis,
    )

    fun beginImage(
        conversationId: String,
        attachmentId: String,
        outputMessageId: String,
        parentAttemptId: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): GenerationAttempt = begin(
        conversationId = conversationId,
        kind = GenerationAttemptKind.Image,
        ownerId = attachmentId,
        parentAttemptId = parentAttemptId,
        outputMessageId = outputMessageId,
        nowMillis = nowMillis,
    )

    override fun beginImageAttempt(
        conversationId: String,
        attachmentId: String,
        outputMessageId: String,
        parentAttemptId: String?,
    ): String = beginImage(
        conversationId = conversationId,
        attachmentId = attachmentId,
        outputMessageId = outputMessageId,
        parentAttemptId = parentAttemptId,
    ).id

    override fun markImageRunning(attemptId: String): Boolean = start(attemptId)

    override fun markImageSucceeded(attemptId: String, outputPath: String): Boolean =
        succeed(attemptId, outputPath)

    override fun markImageFailed(attemptId: String, errorMessage: String): Boolean =
        fail(attemptId, errorMessage)

    override fun markImageCancelled(attemptId: String, reason: String): Boolean =
        cancel(attemptId, reason)

    private fun begin(
        conversationId: String,
        kind: GenerationAttemptKind,
        ownerId: String,
        parentAttemptId: String?,
        outputMessageId: String,
        nowMillis: Long,
    ): GenerationAttempt = synchronized(mutationLock) {
        require(conversationId.isNotBlank()) { "attempt conversationId 不能为空" }
        require(ownerId.isNotBlank()) { "attempt ownerId 不能为空" }
        require(outputMessageId.isNotBlank()) { "attempt outputMessageId 不能为空" }
        val attemptId = "generation-${newId(20)}"
        lateinit var inserted: GenerationAttemptEntity
        val supersededActiveIds = mutableListOf<String>()
        database.runInTransaction {
            val previous = dao.latest(conversationId, kind.storageValue, ownerId)
            previous?.let { old ->
                dao.supersede(old.id, attemptId, nowMillis)
                supersededActiveIds += old.id
                if (kind == GenerationAttemptKind.Reply) {
                    supersededActiveIds += dao.children(old.id).map(GenerationAttemptEntity::id)
                    dao.supersedeChildren(old.id, attemptId, nowMillis)
                }
            }
            val initialState = if (kind == GenerationAttemptKind.Image) {
                GenerationAttemptState.Queued
            } else {
                GenerationAttemptState.Running
            }
            inserted = GenerationAttemptEntity(
                id = attemptId,
                conversationId = conversationId,
                kind = kind.storageValue,
                ownerId = ownerId,
                parentAttemptId = parentAttemptId,
                outputMessageId = outputMessageId,
                attemptNumber = (previous?.attemptNumber ?: 0) + 1,
                state = initialState.storageValue,
                createdAtMillis = nowMillis,
                startedAtMillis = nowMillis.takeIf { initialState == GenerationAttemptState.Running },
                finishedAtMillis = null,
                errorMessage = "",
                outputPath = "",
                supersededByAttemptId = null,
            )
            dao.insert(inserted)
        }
        supersededActiveIds.forEach(activeAttemptIds::remove)
        activeAttemptIds += attemptId
        inserted.toDomain()
    }

    private fun start(
        attemptId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(mutationLock) {
        var changed = false
        database.runInTransaction {
            changed = dao.start(attemptId = attemptId, startedAtMillis = nowMillis) > 0
        }
        changed
    }

    fun succeed(
        attemptId: String,
        outputPath: String = "",
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = finish(
        attemptId = attemptId,
        state = GenerationAttemptState.Succeeded,
        errorMessage = "",
        outputPath = outputPath,
        nowMillis = nowMillis,
    )

    fun fail(
        attemptId: String,
        errorMessage: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = finish(
        attemptId = attemptId,
        state = GenerationAttemptState.Failed,
        errorMessage = errorMessage,
        outputPath = "",
        nowMillis = nowMillis,
    )

    fun cancel(
        attemptId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = finish(
        attemptId = attemptId,
        state = GenerationAttemptState.Cancelled,
        errorMessage = reason,
        outputPath = "",
        nowMillis = nowMillis,
    )

    internal fun finishInTransaction(
        attemptId: String,
        state: GenerationAttemptState,
        errorMessage: String = "",
        outputPath: String = "",
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        check(database.inTransaction())
        val changed = dao.finish(
            attemptId = attemptId,
            state = state.storageValue,
            finishedAtMillis = nowMillis,
            errorMessage = errorMessage.normalizedAttemptError(),
            outputPath = outputPath,
        ) > 0
        if (changed) activeAttemptIds.remove(attemptId)
        return changed
    }

    private fun finish(
        attemptId: String,
        state: GenerationAttemptState,
        errorMessage: String,
        outputPath: String,
        nowMillis: Long,
    ): Boolean = synchronized(mutationLock) {
        var changed = false
        database.runInTransaction {
            changed = finishInTransaction(
                attemptId = attemptId,
                state = state,
                errorMessage = errorMessage,
                outputPath = outputPath,
                nowMillis = nowMillis,
            )
        }
        activeAttemptIds.remove(attemptId)
        changed
    }

    fun byId(attemptId: String): GenerationAttempt? = dao.byId(attemptId)?.toDomain()

    fun latestImage(conversationId: String, attachmentId: String): GenerationAttempt? =
        dao.latest(conversationId, GenerationAttemptKind.Image.storageValue, attachmentId)?.toDomain()

    fun latestReplyForMessage(conversationId: String, messageId: String): GenerationAttempt? =
        dao.latestReplyForMessage(conversationId, messageId)?.toDomain()

    fun isCurrent(attemptId: String): Boolean {
        val attempt = dao.byId(attemptId) ?: return false
        val latest = dao.latest(attempt.conversationId, attempt.kind, attempt.ownerId) ?: return false
        return latest.id == attemptId && attempt.state in LiveStates
    }

    fun isLatest(attemptId: String): Boolean {
        val attempt = dao.byId(attemptId) ?: return false
        return dao.latest(attempt.conversationId, attempt.kind, attempt.ownerId)?.id == attemptId
    }

    fun isActiveInThisProcess(attemptId: String): Boolean = attemptId in activeAttemptIds

    /** Returns rows changed to `interrupted`; live rows owned by this process are untouched. */
    fun interruptOrphans(
        conversationId: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<GenerationAttempt> = synchronized(mutationLock) {
        val orphanIds = dao.liveForConversation(conversationId)
            .map(GenerationAttemptEntity::id)
            .filterNot(activeAttemptIds::contains)
        if (orphanIds.isEmpty()) return@synchronized emptyList()
        val changed = mutableListOf<GenerationAttempt>()
        database.runInTransaction {
            orphanIds.forEach { attemptId ->
                if (
                    finishInTransaction(
                        attemptId = attemptId,
                        state = GenerationAttemptState.Interrupted,
                        errorMessage = reason,
                        nowMillis = nowMillis,
                    )
                ) {
                    dao.byId(attemptId)?.toDomain()?.let(changed::add)
                }
            }
        }
        changed
    }

    private companion object {
        val LiveStates = setOf(
            GenerationAttemptState.Queued.storageValue,
            GenerationAttemptState.Running.storageValue,
        )
    }
}

private fun GenerationAttemptEntity.toDomain(): GenerationAttempt = GenerationAttempt(
    id = id,
    conversationId = conversationId,
    kind = GenerationAttemptKind.entries.firstOrNull { it.storageValue == kind }
        ?: GenerationAttemptKind.Reply,
    ownerId = ownerId,
    parentAttemptId = parentAttemptId,
    outputMessageId = outputMessageId,
    attemptNumber = attemptNumber,
    state = GenerationAttemptState.entries.firstOrNull { it.storageValue == state }
        ?: GenerationAttemptState.Failed,
    errorMessage = errorMessage,
    outputPath = outputPath,
)

private fun String.normalizedAttemptError(): String = replace(Regex("\\s+"), " ")
    .trim()
    .take(360)
