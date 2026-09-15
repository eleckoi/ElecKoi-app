package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.api.ChatService
import com.eleckoi.android.feature.chat.model.ChatDraft
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class ChatBackgroundController(
    private val scope: CoroutineScope,
    private val chatService: ChatService,
    private val state: () -> ChatUiState,
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
) {
    // Uploading/re-encoding a wallpaper is slower than saving its tuning. Keep them ordered so a
    // fast slider commit can never overtake the image commit it depends on.
    private val writeMutex = Mutex()

    fun save(
        backgroundFile: File?,
        opacity: Float,
        blur: Float,
        scrim: Float,
        global: Boolean,
    ) {
        val current = state().draft ?: return
        val characterId = current.session.characterId.takeIf(String::isNotBlank) ?: return
        write(current, "保存聊天背景失败") {
            if (global) {
                chatService.saveGlobalChatBackground(
                    backgroundFile = backgroundFile,
                    opacity = opacity,
                    blur = blur,
                    scrim = scrim,
                )
            } else {
                chatService.saveCharacterChatBackground(
                    characterId = characterId,
                    backgroundFile = backgroundFile,
                    opacity = opacity,
                    blur = blur,
                    scrim = scrim,
                )
            }
        }
    }

    fun setGlobal(
        backgroundFile: File,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ) {
        val current = state().draft ?: return
        val characterId = current.session.characterId.takeIf(String::isNotBlank) ?: return
        write(current, "设置全局背景失败") {
            chatService.saveGlobalChatBackground(
                backgroundFile = backgroundFile,
                opacity = opacity,
                blur = blur,
                scrim = scrim,
            )
            // Once promoted, the current character follows that same stored global image. Keeping
            // a second character copy would make the selected Global option lie after the save.
            chatService.applyGlobalChatBackground(characterId)
        }
    }

    fun useAppDefault() {
        val current = state().draft ?: return
        val characterId = current.session.characterId.takeIf(String::isNotBlank) ?: return
        write(current, "设置默认背景色失败") {
            chatService.restoreCharacterChatBackgroundDefault(characterId)
        }
    }

    fun useCharacterCard() {
        val current = state().draft ?: return
        val characterId = current.session.characterId.takeIf(String::isNotBlank) ?: return
        write(current, "设置角色立绘失败") {
            chatService.useCharacterCardChatBackground(characterId)
        }
    }

    fun useCustom() {
        val current = state().draft ?: return
        val characterId = current.session.characterId.takeIf(String::isNotBlank) ?: return
        write(current, "设置自定义背景失败") {
            chatService.useCustomChatBackground(characterId)
        }
    }

    fun useExistingGlobal() {
        val current = state().draft ?: return
        val characterId = current.session.characterId.takeIf(String::isNotBlank) ?: return
        write(current, "使用全局背景失败") {
            chatService.useGlobalChatBackground(characterId)
        }
    }

    private fun write(
        current: ChatDraft,
        fallbackError: String,
        operation: suspend () -> Unit,
    ) {
        updateState { it.copy(chatBackgroundErrorMessage = "") }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writeMutex.withLock {
                        operation()
                        chatService.loadChatDraft(current.session.id)
                    }
                }
            }.onSuccess(::applyRefreshedDraft)
                .onRealFailure { error ->
                    updateState {
                        it.copy(chatBackgroundErrorMessage = error.message ?: fallbackError)
                    }
                }
        }
    }

    private fun applyRefreshedDraft(next: ChatDraft) {
        updateState { current ->
            val merged = mergeRefreshedDraftMetadata(current.draft, next)
            current.copy(
                draft = merged,
                isDraftLoading = false,
                chatCharacterId = merged.session.characterId,
                chatCharacterName = merged.session.characterName,
            )
        }
    }
}
