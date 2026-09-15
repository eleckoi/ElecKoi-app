package com.eleckoi.android.feature.settings.ui.personalization.profile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.feature.settings.api.ProfileService
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.UserProfile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val user: UserProfile = UserProfile(),
    val saving: Boolean = false,
    val errorMessage: String = "",
)

sealed interface ProfileIntent {
    data class SaveName(val name: String) : ProfileIntent
    data class SaveAvatars(val files: Map<AvatarSlot, File>) : ProfileIntent
    data class SaveCover(val coverUri: Uri) : ProfileIntent
    data object ClearCover : ProfileIntent
}

sealed interface ProfileEffect {
    data object Saved : ProfileEffect
}

class ProfileViewModel(
    private val profileService: ProfileService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<ProfileEffect>()
    val effects: SharedFlow<ProfileEffect> = _effects.asSharedFlow()

    init {
        observeProfile()
    }

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.SaveName -> save { profileService.saveUserName(intent.name) }
            is ProfileIntent.SaveAvatars -> save(
                cleanup = { intent.files.values.forEach { it.delete() } },
            ) {
                profileService.saveUserAvatars(intent.files)
            }
            is ProfileIntent.SaveCover -> save { profileService.saveUserCover(intent.coverUri) }
            ProfileIntent.ClearCover -> save { profileService.clearUserCover() }
        }
    }

    private fun observeProfile() {
        viewModelScope.launch {
            profileService.userProfileFlow
                .catch { error -> _uiState.update { it.copy(errorMessage = error.message ?: "加载用户资料失败") } }
                .collectLatest { user ->
                    _uiState.update { it.copy(user = user, errorMessage = "") }
                }
        }
    }

    private fun save(
        cleanup: () -> Unit = {},
        block: () -> UserProfile,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
            withContext(Dispatchers.IO) { cleanup() }
            result.onSuccess { user ->
                _uiState.update { it.copy(user = user, saving = false, errorMessage = "") }
                _effects.emit(ProfileEffect.Saved)
            }.onFailure { error ->
                Log.e(LogTag, "Failed to update user profile", error)
                _uiState.update { it.copy(saving = false, errorMessage = error.message ?: "保存用户资料失败") }
            }
        }
    }

    companion object {
        private const val LogTag = "ElecKoiProfile"

        fun factory(profileService: ProfileService): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                        return ProfileViewModel(profileService) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
