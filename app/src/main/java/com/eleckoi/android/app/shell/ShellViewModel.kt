package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.RootTab
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.app.service.ShellService
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.preferences.ListCharacterArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ShellUiState(
    val activeTab: RootTab = RootTab.Messages,
    val moreOpen: Boolean = false,
    val chats: List<ChatListItem> = emptyList(),
    val pinnedChatIds: List<String> = emptyList(),
    val hiddenChatIds: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val listCharacterArtwork: ListCharacterArtwork = ListCharacterArtwork.Default,
    val activeChatSessionIds: Map<String, String> = emptyMap(),
    val rootLoading: Boolean = true,
    val rootErrorMessage: String = "",
)

internal sealed interface ShellIntent {
    data class ChangeTab(val tab: RootTab) : ShellIntent
    data class SetMoreOpen(val open: Boolean) : ShellIntent
    data object OpenProfile : ShellIntent
    data object OpenTheme : ShellIntent
    data class TogglePinnedChat(val sessionId: String) : ShellIntent
    data class HideChat(val sessionId: String) : ShellIntent
    data class RememberSearch(val term: String) : ShellIntent
    data class ForgetSearch(val term: String) : ShellIntent
    data object ClearSearchHistory : ShellIntent
    data class SetListCharacterArtwork(val artwork: ListCharacterArtwork) : ShellIntent
}

internal class ShellViewModel(
    private val shellService: ShellService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    init {
        observeRootData()
    }

    fun onIntent(intent: ShellIntent) {
        when (intent) {
            is ShellIntent.ChangeTab -> _uiState.update { it.copy(activeTab = intent.tab) }
            is ShellIntent.SetMoreOpen -> _uiState.update { it.copy(moreOpen = intent.open) }
            ShellIntent.OpenProfile -> _uiState.update { it.copy(moreOpen = false) }
            ShellIntent.OpenTheme -> _uiState.update { it.copy(moreOpen = false) }
            is ShellIntent.TogglePinnedChat -> togglePinnedChat(intent.sessionId)
            is ShellIntent.HideChat -> hideChat(intent.sessionId)
            is ShellIntent.RememberSearch -> rememberSearch(intent.term)
            is ShellIntent.ForgetSearch -> writeSearchHistory(
                forgetSearchTerm(_uiState.value.searchHistory, intent.term),
            )
            ShellIntent.ClearSearchHistory -> writeSearchHistory(emptyList())
            is ShellIntent.SetListCharacterArtwork -> setListCharacterArtwork(intent.artwork)
        }
    }

    private fun observeRootData() {
        viewModelScope.launch {
            combine(
                shellService.chatListFlow,
                shellService.uiPreferencesFlow,
            ) { chats, preferences -> chats to preferences }
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            rootLoading = false,
                            rootErrorMessage = error.message ?: "加载首页数据失败",
                        )
                    }
                }
                .collectLatest { (chats, preferences) ->
                    _uiState.update {
                        it.copy(
                            chats = chats,
                            pinnedChatIds = preferences.pinnedChatIds,
                            hiddenChatIds = preferences.hiddenChatIds,
                            searchHistory = preferences.searchHistory,
                            listCharacterArtwork = preferences.listCharacterArtwork,
                            activeChatSessionIds = chats
                                .map(ChatListItem::characterId)
                                .filter(String::isNotBlank)
                                .distinct()
                                .associateWith(preferences::activeChatSessionId)
                                .filterValues(String::isNotBlank),
                            rootLoading = false,
                            rootErrorMessage = "",
                        )
                    }
                }
        }
    }

    fun togglePinnedChat(sessionId: String) {
        if (sessionId.isBlank()) return
        val previous = _uiState.value.pinnedChatIds
        val next = if (sessionId in previous) {
            previous.filterNot { it == sessionId }
        } else {
            listOf(sessionId) + previous.filterNot { it == sessionId }
        }
        _uiState.update { it.copy(pinnedChatIds = next, rootErrorMessage = "") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { shellService.setPinnedChatIds(next) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        pinnedChatIds = previous,
                        rootErrorMessage = error.message ?: "保存置顶失败",
                    )
                }
            }
        }
    }

    private fun hideChat(sessionId: String) {
        if (sessionId.isBlank() || sessionId in _uiState.value.hiddenChatIds) return
        val previous = _uiState.value.hiddenChatIds
        val next = listOf(sessionId) + previous
        _uiState.update { it.copy(hiddenChatIds = next, rootErrorMessage = "") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { shellService.setHiddenChatIds(next) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        hiddenChatIds = previous,
                        rootErrorMessage = error.message ?: "隐藏消息入口失败",
                    )
                }
            }
        }
    }

    private fun rememberSearch(term: String) {
        val next = rememberSearchTerm(_uiState.value.searchHistory, term)
        if (next != _uiState.value.searchHistory) writeSearchHistory(next)
    }

    private fun writeSearchHistory(terms: List<String>) {
        _uiState.update { it.copy(searchHistory = terms) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { shellService.setSearchHistory(terms) }
            }
        }
    }

    private fun setListCharacterArtwork(artwork: ListCharacterArtwork) {
        val previous = _uiState.value.listCharacterArtwork
        if (artwork == previous) return
        _uiState.update { it.copy(listCharacterArtwork = artwork, rootErrorMessage = "") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    shellService.setListCharacterArtwork(artwork)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        listCharacterArtwork = previous,
                        rootErrorMessage = error.message ?: "保存列表角色图失败",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(shellService: ShellService): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ShellViewModel::class.java)) {
                        return ShellViewModel(shellService) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

internal fun rememberSearchTerm(
    history: List<String>,
    term: String,
    limit: Int = 8,
): List<String> {
    val normalized = term.trim()
    if (normalized.isBlank() || limit <= 0) return history
    return (listOf(normalized) + history.filterNot { it.equals(normalized, ignoreCase = true) })
        .take(limit)
}

internal fun forgetSearchTerm(history: List<String>, term: String): List<String> {
    val normalized = term.trim()
    if (normalized.isBlank()) return history
    return history.filterNot { it.equals(normalized, ignoreCase = true) }
}
