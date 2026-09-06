package com.eleckoi.android.feature.chat.data.session

import com.eleckoi.android.feature.chat.data.GenerationAttemptRepository
import com.eleckoi.android.feature.chat.data.recoverImageAttachment
import com.eleckoi.android.feature.chat.data.toChatMessage
import com.eleckoi.android.feature.chat.data.toLedgerMessage
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.foundation.storage.nowIso
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase

/** Applies one image attempt to one persisted assistant attachment atomically. */
internal class ChatSessionImageCoordinator(
    private val database: ElecKoiDatabase,
    private val room: ChatSessionRoomStorage,
    private val generationAttempts: GenerationAttemptRepository,
) {
    fun installAttempt(
        sessionId: String,
        messageId: String,
        replacement: ChatImageAttachment,
    ): Boolean {
        if (!generationAttempts.isLatest(replacement.generationAttemptId)) return false
        return patchAttachment(
            sessionId = sessionId,
            messageId = messageId,
            attachmentId = replacement.id,
            attemptId = replacement.generationAttemptId,
            requireProjectedAttempt = false,
        ) { replacement }
    }

    fun settleAttempt(
        sessionId: String,
        messageId: String,
        completed: ChatImageAttachment,
    ): Boolean {
        if (!generationAttempts.isLatest(completed.generationAttemptId)) return false
        return patchAttachment(
            sessionId = sessionId,
            messageId = messageId,
            attachmentId = completed.id,
            attemptId = completed.generationAttemptId,
            requireProjectedAttempt = true,
        ) { completed }
    }

    fun recoverProjection(
        sessionId: String,
        image: ChatImageAttachment,
        interruptedReason: String,
    ): ChatImageAttachment = recoverImageAttachment(
        image = image,
        latest = generationAttempts.latestImage(sessionId, image.id),
        interruptedReason = interruptedReason,
    )

    private fun patchAttachment(
        sessionId: String,
        messageId: String,
        attachmentId: String,
        attemptId: String,
        requireProjectedAttempt: Boolean,
        transform: (ChatImageAttachment) -> ChatImageAttachment,
    ): Boolean {
        var changed = false
        database.runInTransaction {
            if (!generationAttempts.isLatest(attemptId)) return@runInTransaction
            val entity = room.requireSession(sessionId)
            room.ensureLedger(entity)
            val messages = room.ledger.allMessages(sessionId).map { it.toChatMessage() }
            val messageIndex = messages.indexOfFirst { it.id == messageId }
            if (messageIndex < 0) return@runInTransaction
            val message = messages[messageIndex]
            if (message.role != MessageRole.Assistant) return@runInTransaction
            val imageIndex = message.imageAttachments.indexOfFirst { it.id == attachmentId }
            if (imageIndex < 0) return@runInTransaction
            val current = message.imageAttachments[imageIndex]
            if (requireProjectedAttempt && current.generationAttemptId != attemptId) {
                return@runInTransaction
            }
            val next = transform(current)
            if (next.id != attachmentId || next.generationAttemptId != attemptId) {
                return@runInTransaction
            }
            val updated = message.copy(
                imageAttachments = message.imageAttachments.toMutableList().apply {
                    this[imageIndex] = next
                },
            )
            val user = messages.subList(0, messageIndex)
                .lastOrNull { it.role == MessageRole.User }
                ?: return@runInTransaction
            val updatedAt = nowIso()
            room.dao.touchSession(sessionId, updatedAt)
            room.ledger.upsertResponseInTransaction(
                conversationId = sessionId,
                updatedAt = updatedAt,
                turnSourceMessageId = user.id,
                response = updated.toLedgerMessage(
                    entity.session.characterId,
                    entity.session.characterName,
                    entity.session.characterAvatar,
                ),
            )
            changed = true
        }
        return changed
    }
}
