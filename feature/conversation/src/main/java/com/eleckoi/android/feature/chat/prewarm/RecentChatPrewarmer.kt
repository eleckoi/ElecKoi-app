package com.eleckoi.android.feature.chat.prewarm

import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.data.markdown.CompletedMarkdownDocumentLoader
import com.eleckoi.android.feature.chat.data.presentation.assembleChatContentBlocks
import com.eleckoi.android.feature.chat.data.stream.StreamingMarkupAssembler
import com.eleckoi.android.feature.chat.data.rich.detectRichMessageDocument
import com.eleckoi.android.feature.chat.model.content.ChatContentBlock
import com.eleckoi.android.feature.chat.ui.blocks.markdown.markdownCacheOwnerKey
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps recently active role conversations warm without turning the message-list screen into a
 * history loader. The bounded tail keeps user messages and assistant replies together, and warms
 * the same text/document projection used by the foreground conversation.
 */
internal class RecentChatPrewarmer(
    private val scope: CoroutineScope,
    private val chatService: ChatService,
    private val now: () -> Instant = Instant::now,
) {
    private var chats: List<ChatListItem> = emptyList()
    private var activeChatSessionIds: Map<String, String> = emptyMap()
    private var currentSessionId: String = ""
    private var backgroundJob: Job? = null
    private val completedRevisions = mutableMapOf<String, String>()
    private val preparedDrafts = mutableMapOf<String, PreparedDraft>()

    fun updateCatalog(
        chats: List<ChatListItem>,
        activeChatSessionIds: Map<String, String>,
    ) {
        this.chats = chats
        this.activeChatSessionIds = activeChatSessionIds
        prunePreparedDrafts()
        val revisions = chats.associate { it.id to it.updatedAt }
        completedRevisions.keys.retainAll(revisions.keys)
        scheduleBackground()
    }

    /** Rebuildable first-frame projection; Room remains the only owner of message history. */
    fun preparedDraft(sessionId: String): ChatDraft? {
        val revision = chats.firstOrNull { it.id == sessionId }?.updatedAt ?: return null
        return preparedDrafts[sessionId]
            ?.takeIf { it.catalogRevision == revision }
            ?.draft
    }

    /** Gives the newly selected conversation the dedicated foreground lane without stopping the queue. */
    fun prioritize(sessionId: String) {
        if (sessionId.isBlank()) return
        currentSessionId = sessionId
        prunePreparedDrafts()
    }

    /** Activates a draft only after [prepareForFirstFrame] or background preparation completed. */
    fun onPreparedDraftShown(draft: ChatDraft) {
        val session = draft.session
        currentSessionId = session.id
        chats.firstOrNull { it.id == session.id }?.let { chat ->
            rememberPreparedDraft(chat.updatedAt, draft)
        }
        scheduleBackground()
    }

    /**
     * Makes a Room projection safe to publish as the first visible frame.
     *
     * This is intentionally suspendable: a cache miss may delay the frame, but it must never
     * publish a draft whose ordinary Markdown documents are still represented by empty nodes.
     */
    suspend fun prepareForFirstFrame(draft: ChatDraft): ChatDraft {
        warmSession(
            sessionId = draft.session.id,
            messages = draft.session.messages.takeLast(ForegroundWarmMessageLimit),
        )
        return draft
    }

    private fun scheduleBackground() {
        if (chats.isEmpty() || backgroundJob?.isActive == true) return
        val targets = recentChatPrewarmTargets(
            chats = chats,
            activeChatSessionIds = activeChatSessionIds,
            currentSessionId = currentSessionId,
            now = now(),
        ).filterNot { target -> completedRevisions[target.id] == target.updatedAt }
        if (targets.isEmpty()) return
        backgroundJob = scope.launch {
            try {
                targets.forEach { target ->
                    warmTail(target)
                    // Avoid a tight retry loop after an opportunistic failure. A newer revision
                    // will still be attempted when the catalog timestamp changes.
                    completedRevisions[target.id] = target.updatedAt
                }
            } finally {
                backgroundJob = null
                // Catalog activity that arrived while this batch was running is picked up next.
                scheduleBackground()
            }
        }
    }

    private suspend fun warmTail(target: ChatListItem) {
        try {
            val draft = withContext(Dispatchers.IO) {
                chatService.previewChatDraft(target.id)
            }
            warmSession(
                sessionId = target.id,
                messages = draft.session.messages.takeLast(ForegroundWarmMessageLimit),
            )
            // A prepared draft is a presentation-ready projection, not merely a Room query result.
            rememberPreparedDraft(target.updatedAt, draft)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Prewarming is opportunistic. Navigation still performs the normal authoritative load.
        }
    }

    private fun rememberPreparedDraft(catalogRevision: String, draft: ChatDraft) {
        val sessionId = draft.session.id
        if (sessionId.isBlank()) return
        val currentRevision = chats.firstOrNull { it.id == sessionId }?.updatedAt ?: return
        if (currentRevision != catalogRevision) return
        if (sessionId != currentSessionId && sessionId !in eligiblePreparedDraftSessionIds()) return
        preparedDrafts[sessionId] = PreparedDraft(catalogRevision, draft)
    }

    private fun prunePreparedDrafts() {
        val revisions = chats.associate { it.id to it.updatedAt }
        val eligibleSessionIds = eligiblePreparedDraftSessionIds().toMutableSet()
        if (currentSessionId.isNotBlank() && currentSessionId in revisions) {
            eligibleSessionIds += currentSessionId
        }
        preparedDrafts.entries.removeAll { (id, prepared) ->
            id !in eligibleSessionIds || revisions[id] != prepared.catalogRevision
        }
    }

    private fun eligiblePreparedDraftSessionIds(): Set<String> =
        recentChatPrewarmTargets(
            chats = chats,
            activeChatSessionIds = activeChatSessionIds,
            currentSessionId = "",
            now = now(),
        ).mapTo(mutableSetOf(), ChatListItem::id)

    private suspend fun warmSession(
        sessionId: String,
        messages: List<ChatMessage>,
    ) {
        try {
            withContext(Dispatchers.Default) {
                prewarmCompletedMessageDocuments(
                    messages = messages,
                    cacheScopeKey = "chat:$sessionId",
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // A renderer failure must never prevent opening the conversation normally.
        }
    }
}

/** Warms completed Markdown documents for rebuildable caches and exact conversation owner keys. */
internal suspend fun prewarmCompletedMessageDocuments(
    messages: List<ChatMessage>,
    cacheScopeKey: String,
    load: suspend (ownerKey: String, markdown: String) -> Unit = { ownerKey, markdown ->
        CompletedMarkdownDocumentLoader.load(ownerKey, markdown)
    },
) {
    messages.asReversed().forEach { message ->
        if (message.pending || message.content.isBlank()) return@forEach
        if (detectRichMessageDocument(message.content) != null) return@forEach
        val ownerKey = markdownCacheOwnerKey(cacheScopeKey, message.id)
        val blocks = assembleChatContentBlocks(
            message = message,
            displayedText = message.content,
            markupAssembler = StreamingMarkupAssembler(ownerKey),
        )
        blocks.filterIsInstance<ChatContentBlock.Text>().forEach { block ->
            if (block.markdown.isNotBlank()) load(ownerKey, block.markdown)
        }
    }
}

private data class PreparedDraft(
    val catalogRevision: String,
    val draft: ChatDraft,
)

internal fun recentChatPrewarmTargets(
    chats: List<ChatListItem>,
    activeChatSessionIds: Map<String, String>,
    currentSessionId: String,
    now: Instant,
    activeWindow: Duration = Duration.ofHours(24),
): List<ChatListItem> {
    if (chats.isEmpty()) return emptyList()
    val byId = chats.associateBy(ChatListItem::id)
    val selectedByCharacter = linkedMapOf<String, ChatListItem>()
    chats.sortedByDescending { it.updatedAt }.forEach { chat ->
        val characterKey = chat.characterId.ifBlank { chat.characterName.ifBlank { chat.id } }
        if (characterKey !in selectedByCharacter) {
            val active = activeChatSessionIds[characterKey]
                ?.let(byId::get)
                ?.takeIf { it.characterId == chat.characterId }
            selectedByCharacter[characterKey] = active ?: chat
        }
    }
    val cutoff = now.minus(activeWindow)
    return selectedByCharacter.values
        .asSequence()
        .filterNot { it.id == currentSessionId }
        .mapNotNull { chat ->
            val updatedAt = runCatching { Instant.parse(chat.updatedAt) }.getOrNull()
                ?: return@mapNotNull null
            chat to updatedAt
        }
        .filter { (_, updatedAt) -> !updatedAt.isBefore(cutoff) }
        .sortedByDescending { (_, updatedAt) -> updatedAt }
        .map { (chat, _) -> chat }
        .toList()
}

private const val ForegroundWarmMessageLimit = 12
