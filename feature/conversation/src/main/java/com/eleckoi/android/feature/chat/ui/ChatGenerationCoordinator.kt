package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.feature.chat.data.PreparedChatRegeneration
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.hasRenderableContent
import com.eleckoi.android.feature.chat.model.hasLiveGenerationState
import com.eleckoi.android.feature.chat.model.settleAbortedGeneration
import com.eleckoi.android.foundation.storage.newId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates one active UI generation without owning ChatUiState.
 */
internal class ChatGenerationCoordinator(
    private val scope: CoroutineScope,
    private val chatService: ChatService,
    private val state: () -> ChatUiState,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
    private val showModeConflictIfNeeded: (ChatUiState, ChatDraft) -> Boolean,
    private val onStopRequested: () -> Unit,
    private val onGenerationCompleted: (ChatDraft, assistantMessageId: String?) -> Unit = { _, _ -> },
) {
    private var generationJob: Job? = null
    private val generationEpoch = AtomicInteger(0)

    fun send(rawContent: String, inputImages: List<ChatUserImageAttachment> = emptyList()) {
        val snapshot = state()
        val content = rawContent.trim()
        val draft = snapshot.draft
        if ((content.isEmpty() && inputImages.isEmpty()) || snapshot.isSending) return
        if (draft == null) {
            updateState { it.copy(errorMessage = "聊天还没有加载完成") }
            return
        }
        if (showModeConflictIfNeeded(snapshot, draft)) return

        val sessionId = draft.session.id
        val existingMessageIds = draft.session.messages.mapTo(hashSetOf(), ChatMessage::id)
        val epoch = begin()
        updateState {
            it.copy(
                input = "",
                inputImages = emptyList(),
                isSending = true,
                generationPresentation = ChatGenerationPresentation(
                    generation = epoch,
                    sessionId = sessionId,
                ),
                errorMessage = "",
            )
        }
        generationJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    chatService.sendMessage(
                        draft = draft,
                        message = content,
                        inputImages = inputImages,
                        onDelta = { nextDraft ->
                            publishDraftIfCurrent(
                                epoch = epoch,
                                sessionId = sessionId,
                                draft = nextDraft,
                                assistantMessageId = nextDraft.session.messages.lastOrNull()
                                    ?.takeIf { message ->
                                        message.role == MessageRole.Assistant &&
                                            message.id !in existingMessageIds
                                    }
                                    ?.id,
                            )
                        },
                    )
                }
            }.onSuccess { result ->
                publishCompletedDraftIfCurrent(
                    epoch = epoch,
                    sessionId = sessionId,
                    draft = result.draft,
                    assistantMessageId = result.draft.session.messages.lastOrNull()
                        ?.takeIf { message ->
                            message.role == MessageRole.Assistant &&
                                message.id !in existingMessageIds
                        }
                        ?.id,
                )
            }.onFailure { error ->
                if (isCurrent(epoch, sessionId)) {
                    if (chatService.isStreamCancelled(error)) {
                        settlePendingReplies("生成已停止")
                        updateState {
                            it.copy(
                                generationPresentation = null,
                                errorMessage = "",
                            )
                        }
                    } else {
                        settlePendingReplies()
                        updateState {
                            it.copy(
                                generationPresentation = null,
                                errorMessage = error.message ?: "发送失败",
                            )
                        }
                    }
                }
            }
            finishIfCurrent(epoch, sessionId)
        }
    }

    fun submitEditedMessage() {
        val snapshot = state()
        val target = snapshot.editingMessage ?: return
        val draft = snapshot.draft ?: return
        val replacement = snapshot.editInput.trim()
        if (replacement.isEmpty() || snapshot.isSending) return
        if (showModeConflictIfNeeded(snapshot, draft)) return

        updateState { it.copy(editingMessage = null, editInput = "") }
        regenerate(
            draft = draft,
            target = target,
            replacement = replacement,
        )
    }

    fun regenerateFrom(message: ChatMessage) {
        val snapshot = state()
        val draft = snapshot.draft ?: return
        // isSending is the live request authority. A persisted pending bit can outlive a killed or
        // interrupted process and must never permanently disable recovery by regeneration.
        if (message.role == MessageRole.System || snapshot.isSending) return
        if (showModeConflictIfNeeded(snapshot, draft)) return
        regenerate(draft = draft, target = message, replacement = null)
    }

    fun stop() {
        onStopRequested()
        chatService.cancelActiveStream()
        generationEpoch.incrementAndGet()
        generationJob?.cancel()
        generationJob = null
        settlePendingReplies("生成已停止")
        updateState {
            it.copy(
                generationPresentation = null,
                errorMessage = "",
                isSending = false,
            )
        }
    }

    private fun regenerate(
        draft: ChatDraft,
        target: ChatMessage,
        replacement: String?,
    ) {
        val sessionId = draft.session.id
        val pendingMessageId = if (target.role == MessageRole.Assistant && replacement == null) {
            target.id
        } else {
            newId(10)
        }
        val epoch = begin()
        // Regeneration has deletion semantics: the old branch disappears as soon as the durable
        // truncation is ready. No empty assistant row is introduced while waiting for the model.
        updateState {
            it.copy(
                isSending = true,
                generationPresentation = ChatGenerationPresentation(
                    generation = epoch,
                    sessionId = sessionId,
                ),
                errorMessage = "",
            )
        }
        // Start undispatched so the non-cancellable destructive commit is registered before this
        // click returns to the UI. A stop tap can cancel model work, never the user's deletion.
        generationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var preparedForTurn: PreparedChatRegeneration? = null
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching {
                    chatService.prepareRegeneration(
                        draft = draft,
                        targetMessageId = target.id,
                        replacementMessage = replacement,
                        pendingMessageId = pendingMessageId,
                    )
                }.onSuccess { prepared ->
                    if (isCurrent(epoch, sessionId)) {
                        preparedForTurn = prepared
                        updateState { current ->
                            if (current.draft?.session?.id == sessionId) {
                                current.copy(draft = prepared.truncatedDraft)
                            } else {
                                current
                            }
                        }
                    } else {
                        // Stop won the race while Room was committing the truncation. Publish only
                        // the durable timeline while still inside NonCancellable; returning to the
                        // cancelled parent would otherwise discard this result.
                        updateState { current ->
                            if (current.draft?.session?.id == sessionId) {
                                current.copy(
                                    draft = prepared.truncatedDraft,
                                    isSending = false,
                                )
                            } else {
                                current
                            }
                        }
                    }
                }.onFailure { error ->
                    if (isCurrent(epoch, sessionId)) {
                        updateState {
                            it.copy(
                                draft = draft,
                                generationPresentation = null,
                                errorMessage = error.message ?: "重新生成失败",
                            )
                        }
                        finishIfCurrent(epoch, sessionId)
                    }
                }
            }
            val prepared = preparedForTurn ?: return@launch

            runCatching {
                withContext(Dispatchers.IO) {
                    chatService.runPreparedRegeneration(
                        prepared = prepared,
                        onDelta = { nextDraft ->
                            publishDraftIfCurrent(
                                epoch = epoch,
                                sessionId = sessionId,
                                draft = nextDraft,
                                assistantMessageId = nextDraft.session.messages.lastOrNull()
                                    ?.takeIf { message ->
                                        message.role == MessageRole.Assistant &&
                                            message.id == prepared.pendingMessageId
                                    }
                                    ?.id,
                            )
                        },
                    )
                }
            }.onSuccess { result ->
                publishCompletedDraftIfCurrent(
                    epoch = epoch,
                    sessionId = sessionId,
                    draft = result.draft,
                    assistantMessageId = result.draft.session.messages.lastOrNull()
                        ?.takeIf { message ->
                            message.role == MessageRole.Assistant &&
                                message.id == prepared.pendingMessageId
                        }
                        ?.id,
                )
            }.onFailure { error ->
                if (isCurrent(epoch, sessionId)) {
                    if (chatService.isStreamCancelled(error)) {
                        settlePendingReplies("生成已停止")
                        updateState {
                            it.copy(
                                generationPresentation = null,
                                errorMessage = "",
                            )
                        }
                    } else {
                        settlePendingReplies()
                        updateState {
                            it.copy(
                                generationPresentation = null,
                                errorMessage = error.message ?: "重新生成失败",
                            )
                        }
                    }
                }
            }
            finishIfCurrent(epoch, sessionId)
        }
    }

    private fun begin(): Int {
        generationJob?.cancel()
        generationJob = null
        return generationEpoch.incrementAndGet()
    }

    private fun finishIfCurrent(epoch: Int, sessionId: String) {
        if (isCurrent(epoch, sessionId)) {
            updateState { it.copy(isSending = false) }
            generationJob = null
        }
    }

    private fun isCurrent(epoch: Int, sessionId: String): Boolean {
        return generationEpoch.get() == epoch && state().draft?.session?.id == sessionId
    }

    private fun updateIfCurrent(
        epoch: Int,
        sessionId: String,
        transform: (ChatUiState) -> ChatUiState,
    ) {
        updateState { current ->
            if (generationEpoch.get() == epoch && current.draft?.session?.id == sessionId) {
                transform(current)
            } else {
                current
            }
        }
    }

    private fun publishDraftIfCurrent(
        epoch: Int,
        sessionId: String,
        draft: ChatDraft,
        assistantMessageId: String?,
    ) {
        updateIfCurrent(epoch, sessionId) { current ->
            val presentation = current.generationPresentation
            current.copy(
                draft = draft,
                generationPresentation = if (
                    assistantMessageId != null &&
                    presentation?.generation == epoch &&
                    presentation.sessionId == sessionId
                ) {
                    presentation.copy(assistantMessageId = assistantMessageId)
                } else {
                    presentation
                },
            )
        }
    }

    private fun publishCompletedDraftIfCurrent(
        epoch: Int,
        sessionId: String,
        draft: ChatDraft,
        assistantMessageId: String?,
    ) {
        publishDraftIfCurrent(epoch, sessionId, draft, assistantMessageId)
        if (isCurrent(epoch, sessionId)) onGenerationCompleted(draft, assistantMessageId)
    }

    private fun settlePendingReplies(reason: String = "生成未完成") {
        val current = state().draft ?: return
        val settledMessages = current.session.messages
            .filter {
                !it.pending ||
                    it.hasRenderableContent()
            }
            .map { message ->
                if (message.hasLiveGenerationState()) {
                    message.settleAbortedGeneration(
                        reason = reason,
                        completedAtMillis = System.currentTimeMillis(),
                    )
                } else {
                    message
                }
            }
        updateState {
            it.copy(draft = current.copy(session = current.session.copy(messages = settledMessages)))
        }
    }
}
