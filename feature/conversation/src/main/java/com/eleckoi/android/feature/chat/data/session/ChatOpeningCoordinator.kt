package com.eleckoi.android.feature.chat.data.session

import com.eleckoi.android.feature.chat.data.ChatVariableStateCurrent
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.nowIso

/** Owns the only full-ledger rewrites allowed before a conversation has its first user turn. */
internal class ChatOpeningCoordinator(
    private val room: ChatSessionRoomStorage,
) {
    fun replaceUnstartedWith(session: ChatSession) {
        room.databaseTransaction {
            room.dao.sessionsForCharacter(session.characterId)
                .filter {
                    it.session.characterMode == session.characterMode &&
                        it.session.historyUserMessageCount == 0
                }
                .forEach { room.ledger.deleteConversationInTransaction(it.session.id) }
            room.dao.deleteUnstartedSessions(session.characterId, session.characterMode)
            room.writeInTransaction(session)
        }
    }

    fun replaceUnstartedOpening(
        characterId: String,
        characterMode: String,
        content: String,
    ) {
        room.databaseTransaction {
            room.dao.sessionsForCharacter(characterId)
                .filter {
                    it.session.characterMode == characterMode &&
                        it.session.historyUserMessageCount == 0
                }
                .forEach { entity ->
                    val current = room.sessionFromEntity(entity, includeAllMessages = true)
                    val messages = ChatOpeningMessagePolicy.replace(
                        messages = current.messages,
                        content = content,
                        createdAt = entity.session.createdAt,
                        initialVariableStateJson = entity.variableStates
                            .firstOrNull { it.kind == ChatVariableStateCurrent }
                            ?.stateJson.orEmpty(),
                        updateExistingVariableState = false,
                    )
                    room.writeInTransaction(current.copy(messages = messages))
                }
        }
    }

    fun selectOpening(
        sessionId: String,
        content: String,
        initialVariableStateJson: String,
    ): ChatSession {
        lateinit var updated: ChatSession
        room.databaseTransaction {
            val entity = room.requireSession(sessionId)
            if (entity.session.historyUserMessageCount > 0) {
                throw ElecKoiDataException("已经开始对话，不能再更换开场白")
            }
            val current = room.sessionFromEntity(entity, includeAllMessages = true)
            val messages = ChatOpeningMessagePolicy.replace(
                messages = current.messages,
                content = content,
                createdAt = entity.session.createdAt,
                initialVariableStateJson = initialVariableStateJson,
                updateExistingVariableState = true,
            )
            updated = current.copy(
                messages = messages,
                initialVariableStateJson = initialVariableStateJson,
                variableStateJson = initialVariableStateJson,
                updatedAt = nowIso(),
            )
            room.writeInTransaction(updated)
        }
        return updated
    }

    fun hasUserMessages(sessionId: String): Boolean {
        return room.requireSession(sessionId).session.historyUserMessageCount > 0
    }
}

internal object ChatOpeningMessagePolicy {
    fun replace(
        messages: List<ChatMessage>,
        content: String,
        createdAt: String,
        initialVariableStateJson: String,
        updateExistingVariableState: Boolean,
    ): List<ChatMessage> {
        val existing = messages.firstOrNull { it.id == OpeningMessageId }
        val opening = content.takeIf(String::isNotBlank)?.let { text ->
            when {
                existing == null -> ChatMessage(
                    id = OpeningMessageId,
                    role = MessageRole.Assistant,
                    content = text,
                    createdAt = createdAt,
                    variableStateJson = initialVariableStateJson,
                )
                updateExistingVariableState -> existing.copy(
                    content = text,
                    variableStateJson = initialVariableStateJson,
                )
                else -> existing.copy(content = text)
            }
        }
        return buildList(messages.size.coerceAtLeast(1)) {
            opening?.let(::add)
            messages.forEach { message ->
                if (message.id != OpeningMessageId) add(message)
            }
        }
    }
}
