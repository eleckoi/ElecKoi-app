package com.eleckoi.android.app.service.backup

import com.eleckoi.android.engine.agent.eleckoi.conversation.LedgerMessage
import com.eleckoi.android.engine.agent.eleckoi.conversation.RoomConversationLedger
import com.eleckoi.android.engine.agent.eleckoi.conversation.SurfaceAssistant
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Lossless backup boundary for the Room-owned creator-assistant conversation ledger. */
internal class CreatorAssistantBackupStore(
    private val workspaces: CreatorWorkspaceRepository,
    private val ledger: CreatorAssistantBackupLedger,
) {
    constructor(database: ElecKoiDatabase, workspaces: CreatorWorkspaceRepository) : this(
        workspaces = workspaces,
        ledger = RoomCreatorAssistantBackupLedger(database),
    )

    suspend fun exportJson(): CreatorAssistantBackupExport {
        val workspaceSnapshot = workspaces.list()
        val conversations = workspaceSnapshot.flatMap { workspace ->
            workspace.conversations.map { conversation ->
                CreatorAssistantConversationBackup(
                    workspaceId = workspace.id,
                    conversationId = conversation.id,
                    messages = ledger.messages(conversation.id),
                )
            }
        }
        require(conversations.map { it.conversationId }.distinct().size == conversations.size) {
            "创作助手对话编号重复"
        }
        val document = CreatorAssistantBackupDocument(conversations = conversations)
        return CreatorAssistantBackupExport(
            json = ElecKoiPrettyJson.encodeToString(document),
            workspaceCount = workspaceSnapshot.size,
            conversationCount = conversations.size,
        )
    }

    suspend fun targets(): List<CreatorAssistantBackupTarget> = workspaces.list().flatMap { workspace ->
        workspace.conversations.map { conversation ->
            CreatorAssistantBackupTarget(workspace.id, conversation.id)
        }
    }

    fun exportConversationJson(target: CreatorAssistantBackupTarget): String =
        ElecKoiPrettyJson.encodeToString(
            CreatorAssistantBackupDocument(
                conversations = listOf(
                    CreatorAssistantConversationBackup(
                        workspaceId = target.workspaceId,
                        conversationId = target.conversationId,
                        messages = ledger.messages(target.conversationId),
                    ),
                ),
            ),
        )

    fun restoreConversationJson(json: String, restoredWorkspaces: List<CreatorWorkspace>): Int {
        val document = decodeDocument(json)
        require(document.conversations.size == 1) { "创作助手分块必须只包含一场对话" }
        val backup = document.conversations.single()
        val expected = restoredWorkspaces
            .firstOrNull { it.id == backup.workspaceId }
            ?.conversations
            ?.firstOrNull { it.id == backup.conversationId }
            ?: error("创作助手分块找不到对应对话")
        ledger.restore(
            listOf(
                CreatorAssistantLedgerRestore(
                    conversationId = expected.id,
                    createdAt = expected.createdAt,
                    updatedAt = expected.updatedAt,
                    messages = backup.messages,
                ),
            ),
        )
        return 1
    }

    suspend fun restoreJson(json: String, restoredWorkspaces: List<CreatorWorkspace>): Int {
        val document = decodeDocument(json)

        val expected = restoredWorkspaces.flatMap { workspace ->
            workspace.conversations.map { conversation ->
                ConversationKey(workspace.id, conversation.id) to conversation
            }
        }.toMap()
        require(expected.size == restoredWorkspaces.sumOf { it.conversations.size }) {
            "恢复后的创作助手对话编号重复"
        }
        val entries = document.conversations.associateBy {
            ConversationKey(it.workspaceId, it.conversationId)
        }
        require(entries.size == document.conversations.size) { "创作助手备份包含重复对话" }
        require(entries.keys == expected.keys) { "创作助手备份与工作区对话不一致" }

        ledger.restore(
            entries.map { (key, backup) ->
                val conversation = expected.getValue(key)
                CreatorAssistantLedgerRestore(
                    conversationId = conversation.id,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    messages = backup.messages,
                )
            },
        )
        return entries.size
    }

    private fun decodeDocument(json: String): CreatorAssistantBackupDocument =
        ElecKoiJson.decodeFromString<CreatorAssistantBackupDocument>(json).also { document ->
            require(document.format == Format) { "创作助手备份格式不正确" }
            require(document.version == Version) { "不支持的创作助手备份版本" }
        }

    private data class ConversationKey(val workspaceId: String, val conversationId: String)

    private companion object {
        const val Format = "eleckoi.creator-assistant-backup"
        const val Version = 1
    }
}

internal data class CreatorAssistantBackupTarget(
    val workspaceId: String,
    val conversationId: String,
)

internal interface CreatorAssistantBackupLedger {
    fun messages(conversationId: String): List<LedgerMessage>
    fun restore(conversations: List<CreatorAssistantLedgerRestore>)
}

internal data class CreatorAssistantLedgerRestore(
    val conversationId: String,
    val createdAt: String,
    val updatedAt: String,
    val messages: List<LedgerMessage>,
)

private class RoomCreatorAssistantBackupLedger(
    private val database: ElecKoiDatabase,
    private val ledger: RoomConversationLedger = RoomConversationLedger(database),
) : CreatorAssistantBackupLedger {
    override fun messages(conversationId: String): List<LedgerMessage> =
        ledger.allMessages(conversationId)

    override fun restore(conversations: List<CreatorAssistantLedgerRestore>) {
        database.runInTransaction {
            conversations.forEach { conversation ->
                ledger.replaceActiveTimelineForImportInTransaction(
                    conversationId = conversation.conversationId,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    messages = conversation.messages,
                    surface = SurfaceAssistant,
                )
            }
        }
    }
}

internal data class CreatorAssistantBackupExport(
    val json: String,
    val workspaceCount: Int,
    val conversationCount: Int,
)

@Serializable
internal data class CreatorAssistantBackupDocument(
    val format: String = "eleckoi.creator-assistant-backup",
    val version: Int = 1,
    val conversations: List<CreatorAssistantConversationBackup>,
)

@Serializable
internal data class CreatorAssistantConversationBackup(
    val workspaceId: String,
    val conversationId: String,
    val messages: List<LedgerMessage>,
)
