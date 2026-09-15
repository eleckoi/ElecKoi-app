package com.eleckoi.android.app.service

import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.feature.preferences.ListCharacterArtwork
import kotlinx.coroutines.flow.Flow

/** App-shell-only contract. Feature contracts live with their owning feature. */
interface ShellService {
    val chatListFlow: Flow<List<ChatListItem>>
    val uiPreferencesFlow: Flow<UiPreferences>

    suspend fun setPinnedChatIds(ids: List<String>): UiPreferences
    suspend fun setHiddenChatIds(ids: List<String>): UiPreferences
    suspend fun setSearchHistory(terms: List<String>): UiPreferences
    suspend fun setListCharacterArtwork(artwork: ListCharacterArtwork): UiPreferences
}
