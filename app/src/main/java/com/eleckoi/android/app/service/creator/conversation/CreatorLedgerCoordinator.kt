package com.eleckoi.android.app.service

import com.eleckoi.android.engine.agent.api.AgentHistoryItem
import com.eleckoi.android.engine.agent.eleckoi.conversation.RoomConversationLedger
import com.eleckoi.android.engine.agent.eleckoi.conversation.ConversationAttachmentCleanup
import com.eleckoi.android.feature.chat.ui.blocks.markdown.MarkdownRebuildableCaches
import com.eleckoi.android.engine.agent.eleckoi.conversation.SurfaceAssistant
import com.eleckoi.android.engine.agent.eleckoi.conversation.assistantFullHistory
import com.eleckoi.android.engine.agent.eleckoi.conversation.creatorTimelineFromLedger
import com.eleckoi.android.engine.agent.eleckoi.conversation.creatorTimelineLedgerMessages
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineItem
import com.eleckoi.android.engine.workspace.model.CreatorConversationTimelineKind
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.chat.data.markdown.CompletedMarkdownDocumentLoader
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.PersistentCleanupRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CreatorLedgerCoordinator(
    private val ledger: RoomConversationLedger,
    private val database: ElecKoiDatabase,
    private val creatorWorkspaces: CreatorWorkspaceRepository,
    private val attachmentCleanup: ConversationAttachmentCleanup,
    private val onWorkspacesDeleted: suspend (List<String>) -> Unit,
    private val beforeDeletion: (Collection<String>) -> Unit,
    private val cleanupRunner: PersistentCleanupRunner,
) {
    /**
     * RoomConversationLedger exposes synchronous transaction primitives. This is their suspend
     * service boundary, so no ledger read or write escapes the IO context.
     */
    suspend fun withTimelines(workspace: CreatorWorkspace): CreatorWorkspace = withContext(Dispatchers.IO) {
        workspace.copy(
            conversations = workspace.conversations.map { conversation ->
                ensureLedger(workspace, conversation.id)
                val firstFrame = ledger.displayCache(conversation.id) ?: ledger.page(
                    conversationId = conversation.id,
                    beforeSequence = null,
                    limit = InitialTimelineTurns,
                ).messages
                conversation.copy(timeline = creatorTimelineFromLedger(firstFrame))
            },
        )
    }

    suspend fun ensureLedger(
        workspace: CreatorWorkspace,
        conversationId: String,
    ) = withContext(Dispatchers.IO) {
        if (!ledger.containsConversation(conversationId)) {
            val conversation = workspace.conversations.firstOrNull { it.id == conversationId }
                ?: return@withContext
            database.runInTransaction {
                ledger.ensureConversationInTransaction(
                    conversationId = conversation.id,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    initialMessages = creatorTimelineLedgerMessages(conversation.timeline),
                    surface = SurfaceAssistant,
                )
            }
        }
    }

    suspend fun deleteWorkspace(workspaceId: String) = withContext(Dispatchers.IO) {
        cleanupRunner.run(WorkspaceCleanupKind, workspaceId) { deleteWorkspaceNow(workspaceId) }
    }

    internal suspend fun deleteWorkspaceNow(workspaceId: String) = withContext(Dispatchers.IO) {
        val workspace = creatorWorkspaces.get(workspaceId)
        if (workspace == null) {
            creatorWorkspaces.delete(workspaceId)
            return@withContext
        }
        val conversations = workspace.conversations
        val ids = conversations.map { it.id }
        beforeDeletion(ids)
        onWorkspacesDeleted(listOf(workspaceId))
        MarkdownRebuildableCaches.clearAfterConversationDeletion(ids)
        attachmentCleanup.deleteConversations(ids) {
            conversations.forEach { conversation ->
                ledger.deleteConversationInTransaction(conversation.id)
            }
        }
        creatorWorkspaces.delete(workspaceId)
    }

    suspend fun saveTimeline(
        workspaceId: String,
        conversationId: String,
        timeline: List<CreatorConversationTimelineItem>,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        val updated = creatorWorkspaces.saveConversationTimeline(workspaceId, conversationId, emptyList())
        val conversation = updated.conversations.firstOrNull { it.id == conversationId }
            ?: error("创作助手对话不存在")
        val ledgerMessages = creatorTimelineLedgerMessages(timeline)
        database.runInTransaction {
            if (!ledger.containsConversation(conversationId)) {
                ledger.ensureConversationInTransaction(
                    conversationId = conversationId,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    initialMessages = ledgerMessages,
                    surface = SurfaceAssistant,
                )
            } else {
                val response = ledgerMessages.lastOrNull()?.takeIf { it.role == "assistant" }
                val turn = ledgerMessages.getOrNull(ledgerMessages.lastIndex - 1)
                    ?.takeIf { it.role == "user" }
                if (turn != null) {
                    ledger.upsertTurnInTransaction(
                        conversationId = conversationId,
                        createdAt = conversation.createdAt,
                        updatedAt = conversation.updatedAt,
                        turn = turn,
                        response = response,
                        clearResponseWhenMissing = false,
                        surface = SurfaceAssistant,
                    )
                }
            }
        }
        ledgerMessages.lastOrNull()
            ?.takeIf { it.role == "assistant" && !it.pending }
            ?.let { response ->
                CompletedMarkdownDocumentLoader.warm(
                    ownerKey = "creation:$conversationId",
                    markdown = response.content,
                )
            }
        withTimelines(updated)
    }

    suspend fun checkpointTurn(
        workspaceId: String,
        conversationId: String,
        turnTimeline: List<CreatorConversationTimelineItem>,
    ) = withContext(Dispatchers.IO) {
        val workspace = creatorWorkspaces.get(workspaceId) ?: return@withContext
        val conversation = workspace.conversations.firstOrNull { it.id == conversationId }
            ?: return@withContext
        val messages = creatorTimelineLedgerMessages(turnTimeline)
        val response = messages.lastOrNull()?.takeIf { it.role == "assistant" }
        val turn = messages.getOrNull(messages.lastIndex - 1)?.takeIf { it.role == "user" }
            ?: return@withContext
        database.runInTransaction {
            if (!ledger.containsConversation(conversationId)) {
                ledger.ensureConversationInTransaction(
                    conversationId = conversationId,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    initialMessages = creatorTimelineLedgerMessages(conversation.timeline),
                    surface = SurfaceAssistant,
                )
            } else {
                ledger.upsertTurnInTransaction(
                    conversationId = conversationId,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    turn = turn,
                    response = response,
                    clearResponseWhenMissing = false,
                    surface = SurfaceAssistant,
                    rebuildDisplayCache = false,
                )
            }
        }
    }

    suspend fun truncateForRegeneration(
        workspaceId: String,
        conversationId: String,
        retainedUser: CreatorConversationTimelineItem,
    ): CreatorLedgerTruncation = withContext(Dispatchers.IO) {
        require(retainedUser.kind == CreatorConversationTimelineKind.User) {
            "重新生成只能保留用户回合"
        }
        val workspace = creatorWorkspaces.get(workspaceId) ?: error("创作工作区不存在")
        ensureLedger(workspace, conversationId)
        val removedAssetIds = generatedMediaAssetIdsAfter(
            timeline = creatorTimelineFromLedger(ledger.allMessages(conversationId)),
            retainedUserId = retainedUser.id,
        )
        val updated = creatorWorkspaces.saveConversationTimeline(workspaceId, conversationId, emptyList())
        val conversation = updated.conversations.firstOrNull { it.id == conversationId }
            ?: error("创作助手对话不存在")
        val retainedTurn = creatorTimelineLedgerMessages(listOf(retainedUser))
            .firstOrNull { it.role == "user" }
            ?: error("无法创建重新生成回合")
        attachmentCleanup.discardMessages(conversationId) {
            ledger.truncateAfterTurnInTransaction(
                conversationId = conversationId,
                updatedAt = conversation.updatedAt,
                retainedTurn = retainedTurn,
            )
        }
        CreatorLedgerTruncation(withTimelines(updated), removedAssetIds)
    }

    suspend fun loadAgentHistory(
        workspaceId: String,
        conversationId: String,
        excludeTrailingUser: Boolean,
    ): List<AgentHistoryItem> = withContext(Dispatchers.IO) {
        val workspace = creatorWorkspaces.get(workspaceId) ?: return@withContext emptyList()
        ensureLedger(workspace, conversationId)
        val messages = ledger.allMessages(conversationId).let { loaded ->
            if (excludeTrailingUser && loaded.lastOrNull()?.role == "user") loaded.dropLast(1) else loaded
        }
        assistantFullHistory(messages)
    }

    suspend fun deleteConversation(
        workspaceId: String,
        conversationId: String,
    ): CreatorWorkspace = withContext(Dispatchers.IO) {
        val workspace = creatorWorkspaces.get(workspaceId) ?: error("创作工作区不存在")
        require(workspace.conversations.any { it.id == conversationId }) { "创作助手对话不存在" }
        cleanupRunner.run(
            CreatorConversationCleanupKind,
            conversationCleanupTarget(workspaceId, conversationId),
        ) {
            deleteConversationNow(workspaceId, conversationId)
        } ?: error("创作工作区不存在")
    }

    internal suspend fun resumeConversationDeletion(targetId: String) {
        val parts = targetId.split(CleanupTargetSeparator, limit = 2)
        require(parts.size == 2) { "创作助手对话清理目标无效" }
        deleteConversationNow(parts[0], parts[1])
    }

    private suspend fun deleteConversationNow(
        workspaceId: String,
        conversationId: String,
    ): CreatorWorkspace? = withContext(Dispatchers.IO) {
        val workspace = creatorWorkspaces.get(workspaceId) ?: return@withContext null
        if (workspace.conversations.none { it.id == conversationId }) {
            return@withContext withTimelines(workspace)
        }
        beforeDeletion(listOf(conversationId))
        MarkdownRebuildableCaches.clearAfterConversationDeletion(listOf(conversationId))
        attachmentCleanup.deleteConversations(listOf(conversationId)) {
            ledger.deleteConversationInTransaction(conversationId)
        }
        withTimelines(creatorWorkspaces.deleteConversation(workspaceId, conversationId))
    }

    private companion object {
        const val InitialTimelineTurns = 10
        const val WorkspaceCleanupKind = "creator_workspace"
        const val CreatorConversationCleanupKind = "creator_conversation"
        const val CleanupTargetSeparator = "::"

        fun conversationCleanupTarget(workspaceId: String, conversationId: String): String {
            require(CleanupTargetSeparator !in workspaceId && CleanupTargetSeparator !in conversationId) {
                "创作助手对话清理目标无效"
            }
            return "$workspaceId$CleanupTargetSeparator$conversationId"
        }
    }
}

internal data class CreatorLedgerTruncation(
    val workspace: CreatorWorkspace,
    val removedAssetIds: Set<String>,
)
