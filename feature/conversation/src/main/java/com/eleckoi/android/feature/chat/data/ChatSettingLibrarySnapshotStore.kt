package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.storage.room.ConversationSettingChangeEntity
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentContentPartEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** A pre-turn state checkpoint, stored with the raw user turn rather than in a second chat table. */
internal class ChatSettingLibrarySnapshotStore(private val database: ElecKoiDatabase) {
    private val ledger = database.agentLedgerDao()
    private val changes = database.conversationSettingChangeDao()

    fun capture(sessionId: String, userMessageId: String) {
        requireTransaction()
        write(sessionId, userMessageId, encode(changes.changes(sessionId)))
    }

    fun snapshotForMessage(sessionId: String, messageId: String): String {
        requireTransaction()
        val userTurn = ledger.turnBySourceMessageId(sessionId, messageId)
        if (userTurn?.kind == "opening") return "[]"
        val response = if (userTurn == null) ledger.responseBySourceMessageId(sessionId, messageId) else null
        val turnId = userTurn?.id ?: response?.turnId
            ?: throw IllegalArgumentException("没有找到消息：$messageId")
        val priorResponse = response?.takeIf { it.responseIndex > 0 }?.let { selected ->
            ledger.responsesForTurns(listOf(turnId)).firstOrNull {
                it.responseIndex == selected.responseIndex - 1
            }
        }
        val ownerType = if (priorResponse == null) "turn" else "response"
        val ownerId = priorResponse?.id ?: turnId
        val chunks = ledger.contentParts(ownerType, listOf(ownerId))
            .filter { it.kind == SnapshotKind && it.partIndex == SnapshotPartIndex }
            .sortedBy(AgentContentPartEntity::chunkIndex)
        if (chunks.isEmpty()) {
            check(changes.changes(sessionId).isEmpty()) {
                "这条旧消息没有设定库回滚点，不能安全删除或重新生成"
            }
            return "[]"
        }
        check(chunks.indices.all { chunks[it].chunkIndex == it }) { "设定库回滚点不完整" }
        return chunks.joinToString(separator = "") { it.payloadJson }
            .also { ElecKoiJson.decodeFromString<List<SettingChangeSnapshot>>(it) }
    }

    fun restore(sessionId: String, snapshotJson: String) {
        requireTransaction()
        val restored = ElecKoiJson.decodeFromString<List<SettingChangeSnapshot>>(snapshotJson)
            .map { it.toEntity(sessionId) }
        changes.deleteForSession(sessionId)
        if (restored.isNotEmpty()) changes.upsertChanges(restored)
    }

    /** Regeneration rewrites the retained user turn, so restore its checkpoint afterward. */
    fun write(sessionId: String, userMessageId: String, snapshotJson: String) {
        requireTransaction()
        val turnId = requireNotNull(ledger.turnBySourceMessageId(sessionId, userMessageId)) {
            "找不到设定库回滚点所属的用户消息：$userMessageId"
        }.id
        writeParts(sessionId, "turn", turnId, SnapshotKind, snapshotJson)
    }

    fun captureResponseAfter(sessionId: String, responseMessageId: String) {
        requireTransaction()
        val responseId = requireNotNull(
            ledger.responseBySourceMessageId(sessionId, responseMessageId),
        ) { "找不到设定库回滚点所属的 AI 回复：$responseMessageId" }.id
        writeParts(
            sessionId,
            "response",
            responseId,
            SnapshotKind,
            encode(changes.changes(sessionId)),
        )
    }

    private fun writeParts(
        sessionId: String,
        ownerType: String,
        ownerId: String,
        kind: String,
        snapshotJson: String,
    ) {
        val old = ledger.contentParts(ownerType, listOf(ownerId))
            .filter { it.kind == kind && it.partIndex == SnapshotPartIndex }
        if (old.isNotEmpty()) ledger.deleteContentPartRows(old)
        ledger.upsertContentParts(snapshotJson.chunked(MaxChunkCharacters).mapIndexed { index, chunk ->
            AgentContentPartEntity(
                conversationId = sessionId,
                ownerType = ownerType,
                ownerId = ownerId,
                partIndex = SnapshotPartIndex,
                kind = kind,
                text = "",
                payloadJson = chunk,
                chunkIndex = index,
            )
        })
    }

    private fun requireTransaction() = check(database.inTransaction()) {
        "设定库回滚点必须与聊天消息在同一 Room 事务中更新"
    }

    private fun encode(rows: List<ConversationSettingChangeEntity>): String =
        ElecKoiJson.encodeToString(rows.map(SettingChangeSnapshot::fromEntity))

    private companion object {
        const val SnapshotKind = "setting_library_state"
        const val SnapshotPartIndex = 1_000_000
        const val MaxChunkCharacters = 8_192
    }
}

@Serializable
internal data class SettingChangeSnapshot(
    val targetType: String,
    val targetId: String,
    val operation: String,
    val payloadJson: String,
    val updatedAt: String,
) {
    fun toEntity(sessionId: String) = ConversationSettingChangeEntity(
        sessionId = sessionId,
        targetType = targetType,
        targetId = targetId,
        operation = operation,
        payloadJson = payloadJson,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromEntity(row: ConversationSettingChangeEntity) = SettingChangeSnapshot(
            targetType = row.targetType,
            targetId = row.targetId,
            operation = row.operation,
            payloadJson = row.payloadJson,
            updatedAt = row.updatedAt,
        )
    }
}
