package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import com.eleckoi.android.feature.chat.prewarm.RecentChatPrewarmer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns active-draft selection, loading and observation. Paging rows remain owned by
 * [ChatHistoryController]; metadata refreshes must never replace its visible history window.
 */
internal class ChatDraftController(
    private val scope: CoroutineScope,
    private val chatService: ChatService,
    private val states: StateFlow<ChatUiState>,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
    private val historyController: ChatHistoryController,
    private val recentChatPrewarmer: RecentChatPrewarmer,
    private val initialPreferencesReady: CompletableDeferred<Unit>,
) {
    private val currentSessionId = MutableStateFlow("")
    private var loadJob: Job? = null

    fun start() {
        scope.launch {
            combine(
                currentSessionId,
                states.map { state: ChatUiState -> state.isSending }.distinctUntilChanged(),
            ) { sessionId, isSending -> sessionId to isSending }
                .distinctUntilChanged()
                .collectLatest { (sessionId, isSending) ->
                    if (sessionId.isBlank()) return@collectLatest
                    historyController.bind(sessionId)
                    historyController.setTimelineMutationActive(isSending)
                    // Live deltas already own the visible tail. Suspending the authoritative
                    // collector prevents Room checkpoints from rebuilding a discarded draft.
                    if (isSending) return@collectLatest
                    chatService.chatDraftFlow(sessionId)
                        .catch { error ->
                            if (isCurrentAndIdle(sessionId)) {
                                updateState {
                                    it.copy(errorMessage = error.message ?: "刷新当前聊天失败")
                                }
                            }
                        }
                        .collectLatest { draft ->
                            if (isCurrent(sessionId)) {
                                updateState { current ->
                                    if (
                                        shouldAcceptObservedChatDraft(
                                            currentSessionId = current.draft?.session?.id,
                                            currentUpdatedAt = current.draft?.session?.updatedAt,
                                            observedSessionId = draft.session.id,
                                            observedUpdatedAt = draft.session.updatedAt,
                                            isSending = current.isSending,
                                        )
                                    ) {
                                        current.withDraft(
                                            mergeRefreshedDraftMetadata(current.draft, draft),
                                        )
                                    } else {
                                        current
                                    }
                                }
                            }
                        }
                }
        }
    }

    fun selectSession(sessionId: String) {
        currentSessionId.value = sessionId
    }

    fun clearSession() {
        currentSessionId.value = ""
    }

    fun isCurrent(sessionId: String): Boolean = currentSessionId.value == sessionId

    fun loadInitialDraft() {
        if (states.value.draft != null || loadJob?.isActive == true) return
        updateState { it.copy(isDraftLoading = true, errorMessage = "") }
        loadJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { chatService.currentDraft() }
            }.onSuccess { next ->
                if (next == null) {
                    clearSession()
                    updateState {
                        it.copy(draft = null, isDraftLoading = false, errorMessage = "")
                    }
                } else {
                    initialPreferencesReady.await()
                    showPreparedDraft(next, loadedWindow = true)
                }
            }.onRealFailure { error ->
                updateState {
                    it.copy(isDraftLoading = false, errorMessage = error.message ?: "加载聊天失败")
                }
            }
        }
    }

    fun loadDraft(sessionId: String) {
        if (sessionId.isBlank()) return
        val current = states.value
        if (!current.isDraftLoading && current.draft?.session?.id == sessionId) {
            selectSession(sessionId)
            return
        }
        recentChatPrewarmer.prioritize(sessionId)
        val prepared = recentChatPrewarmer.preparedDraft(sessionId)
        resetForLoad()
        if (prepared != null) {
            updateState { it.withLoadedWindow(prepared) }
            selectSession(prepared.session.id)
            recentChatPrewarmer.onPreparedDraftShown(prepared)
            loadJob = scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { chatService.loadChatDraft(sessionId) }
                }.onSuccess { refreshed ->
                    if (isCurrent(sessionId)) {
                        updateState { state ->
                            state.withDraft(mergeRefreshedDraftMetadata(state.draft, refreshed))
                        }
                    }
                }.onRealFailure { error ->
                    if (isCurrent(sessionId)) {
                        updateState { it.copy(errorMessage = error.message ?: "刷新聊天失败") }
                    }
                }
            }
            return
        }
        updateState {
            it.copy(
                draft = null,
                isDraftLoading = true,
                historyHasMore = false,
                historyPageLoading = true,
                historyInitialPageReady = false,
                errorMessage = "",
            )
        }
        loadJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { chatService.loadChatDraft(sessionId) }
            }.onSuccess { next ->
                showPreparedDraft(next, loadedWindow = true)
            }.onRealFailure { error ->
                updateState {
                    it.copy(isDraftLoading = false, errorMessage = error.message ?: "加载聊天失败")
                }
            }
        }
    }

    fun createChat(characterId: String) {
        resetForLoad()
        updateState {
            it.copy(
                draft = null,
                isDraftLoading = true,
                chatCharacterId = characterId,
                historyHasMore = false,
                historyPageLoading = false,
                historyInitialPageReady = false,
                errorMessage = "",
            )
        }
        loadJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    chatService.createNewChat(characterId)
                }
            }.onSuccess { next ->
                showPreparedDraft(next, loadedWindow = false)
            }.onRealFailure { error ->
                updateState {
                    it.copy(isDraftLoading = false, errorMessage = error.message ?: "新建对话失败")
                }
            }
        }
    }

    fun openCharacterChat(characterId: String) {
        resetForLoad()
        updateState {
            it.copy(
                draft = null,
                isDraftLoading = true,
                chatCharacterId = characterId,
                historyHasMore = false,
                historyPageLoading = true,
                historyInitialPageReady = false,
                errorMessage = "",
            )
        }
        loadJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    chatService.chatDraftForCharacter(characterId)
                }
            }.onSuccess { next ->
                showPreparedDraft(next, loadedWindow = true)
            }.onRealFailure { error ->
                updateState {
                    it.copy(isDraftLoading = false, errorMessage = error.message ?: "打开角色聊天失败")
                }
            }
        }
    }

    fun refreshCurrentDraft() {
        val sessionId = states.value.draft?.session?.id.orEmpty()
        if (sessionId.isBlank()) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { chatService.loadChatDraft(sessionId) }
            }.onSuccess { refreshed ->
                updateState { current ->
                    if (current.draft?.session?.id != sessionId) {
                        current
                    } else {
                        current.withDraft(
                            mergeRefreshedDraftMetadata(current.draft, refreshed),
                        )
                    }
                }
            }.onRealFailure { error ->
                if (states.value.draft?.session?.id == sessionId) {
                    updateState {
                        it.copy(errorMessage = error.message ?: "刷新当前聊天失败")
                    }
                }
            }
        }
    }

    fun deleteHistoryChat(sessionId: String) {
        val current = states.value.draft
        val deletingCurrent = current?.session?.id == sessionId
        if (deletingCurrent) {
            clearSession()
            updateState {
                it.copy(
                    isDraftLoading = true,
                    chatCharacterId = current.session.characterId,
                    chatCharacterName = current.session.characterName,
                    errorMessage = "",
                )
            }
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    chatService.deleteChat(sessionId)
                    if (current?.session?.id == sessionId) {
                        chatService.nextChatDraftForCharacter(current.session.characterId)
                    } else {
                        null
                    }
                }
            }.onSuccess { nextDraft ->
                if (deletingCurrent) {
                    if (nextDraft == null) {
                        updateState {
                            it.copy(
                                draft = null,
                                isDraftLoading = false,
                                historyHasMore = false,
                                historyPageLoading = false,
                                errorMessage = "",
                            )
                        }
                    } else {
                        showPreparedDraft(nextDraft, loadedWindow = true)
                    }
                }
            }.onRealFailure { error ->
                if (deletingCurrent) selectSession(requireNotNull(current).session.id)
                updateState {
                    it.copy(
                        draft = current ?: it.draft,
                        isDraftLoading = false,
                        errorMessage = error.message ?: "删除历史失败",
                    )
                }
            }
        }
    }

    private fun resetForLoad() {
        historyController.resetPaging()
        clearSession()
        loadJob?.cancel()
    }

    private suspend fun showPreparedDraft(next: ChatDraft, loadedWindow: Boolean) {
        recentChatPrewarmer.prepareForFirstFrame(next)
        updateState { current ->
            if (loadedWindow) current.withLoadedWindow(next) else current.withDraft(next)
        }
        selectSession(next.session.id)
        recentChatPrewarmer.onPreparedDraftShown(next)
    }

    private fun isCurrentAndIdle(sessionId: String): Boolean =
        isCurrent(sessionId) &&
            states.value.draft?.session?.id == sessionId &&
            !states.value.isSending
}

internal fun ChatUiState.withDraft(next: ChatDraft): ChatUiState = copy(
    draft = next,
    isDraftLoading = false,
    chatCharacterId = next.session.characterId,
    chatCharacterName = next.session.characterName,
)

internal fun ChatUiState.withLoadedWindow(next: ChatDraft): ChatUiState = withDraft(next).copy(
    // The first-frame projection paints immediately; Paging replaces it authoritatively.
    historyHasMore = initialHistoryHasMore(next.session.messages),
    historyPageLoading = false,
    historyInitialPageReady = false,
)

/** The synthetic opening row proves that the current projection already reached the true start. */
internal fun initialHistoryHasMore(messages: List<ChatMessage>): Boolean =
    messages.isNotEmpty() && messages.firstOrNull()?.id != OpeningMessageId

/** Metadata refreshes do not own history rows; the shared Paging presenter does. */
internal fun mergeRefreshedDraftMetadata(
    active: ChatDraft?,
    refreshed: ChatDraft,
): ChatDraft {
    if (active == null || active.session.id != refreshed.session.id) return refreshed
    return refreshed.copy(
        session = refreshed.session.copy(messages = active.session.messages),
    )
}
