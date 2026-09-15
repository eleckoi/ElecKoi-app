package com.eleckoi.android.app.service

import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.feature.preferences.ListCharacterArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

internal class ShellServiceImpl(
    private val sessions: ChatSessionStore,
    private val uiPreferences: UiPreferencesRepository,
) : ShellService {
    override val chatListFlow: Flow<List<ChatListItem>> = sessions.chatListFlow()
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
    override val uiPreferencesFlow: Flow<UiPreferences> = uiPreferences.preferencesFlow

    override suspend fun setPinnedChatIds(ids: List<String>): UiPreferences {
        return uiPreferences.setPinnedChatIds(ids)
    }

    override suspend fun setHiddenChatIds(ids: List<String>): UiPreferences {
        return uiPreferences.setHiddenChatIds(ids)
    }

    override suspend fun setSearchHistory(terms: List<String>): UiPreferences {
        return uiPreferences.setSearchHistory(terms)
    }

    override suspend fun setListCharacterArtwork(artwork: ListCharacterArtwork): UiPreferences {
        return uiPreferences.setListCharacterArtwork(artwork)
    }
}
