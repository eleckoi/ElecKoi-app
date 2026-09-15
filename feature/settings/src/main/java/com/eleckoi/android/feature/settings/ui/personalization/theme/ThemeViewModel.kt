package com.eleckoi.android.feature.settings.ui.personalization.theme

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.feature.settings.api.AppearanceService
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.preferences.AppearanceMode
import com.eleckoi.android.feature.preferences.NewCharacterBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ThemeUiState(
    val appearance: AppearanceTheme = AppearanceTheme(),
    val appearanceMode: AppearanceMode = AppearanceMode.Default,
    val newCharacterBackground: NewCharacterBackground = NewCharacterBackground.Default,
    val errorMessage: String = "",
)

sealed interface ThemeIntent {
    data class SaveThemePalette(val source: Bitmap) : ThemeIntent
    data class SaveRootBackground(
        val source: Bitmap,
        val opacity: Float,
        val blur: Float,
        val scrim: Float,
    ) : ThemeIntent
    data class SaveRootBackgroundTuning(
        val opacity: Float,
        val blur: Float,
        val scrim: Float,
    ) : ThemeIntent
    data object ClearRootBackground : ThemeIntent
    data class SaveAppearanceMode(val mode: AppearanceMode) : ThemeIntent
    data class SaveNewCharacterBackground(val background: NewCharacterBackground) : ThemeIntent
    data class SaveAppearanceTheme(val appearance: AppearanceTheme) : ThemeIntent
    data object ResetAppearanceTheme : ThemeIntent
}

class ThemeViewModel(
    private val appearanceService: AppearanceService,
    initialAppearance: AppearanceTheme = AppearanceTheme(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ThemeUiState(appearance = initialAppearance))
    val uiState: StateFlow<ThemeUiState> = _uiState.asStateFlow()

    init {
        observeAppearanceTheme()
    }

    fun onIntent(intent: ThemeIntent) {
        when (intent) {
            is ThemeIntent.SaveThemePalette -> saveThemePalette(intent.source)
            is ThemeIntent.SaveRootBackground -> saveRootBackground(intent)
            is ThemeIntent.SaveRootBackgroundTuning -> saveRootBackgroundTuning(intent)
            ThemeIntent.ClearRootBackground -> clearRootBackground()
            is ThemeIntent.SaveAppearanceMode -> saveAppearanceMode(intent.mode)
            is ThemeIntent.SaveNewCharacterBackground -> saveNewCharacterBackground(intent.background)
            is ThemeIntent.SaveAppearanceTheme -> saveAppearanceTheme(intent.appearance)
            ThemeIntent.ResetAppearanceTheme -> resetAppearanceTheme()
        }
    }

    private fun saveRootBackground(intent: ThemeIntent.SaveRootBackground) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appearanceService.saveRootBackground(
                        intent.source,
                        intent.opacity,
                        intent.blur,
                        intent.scrim,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "保存主页背景失败") }
            }
        }
    }

    private fun saveRootBackgroundTuning(intent: ThemeIntent.SaveRootBackgroundTuning) {
        val preview = _uiState.value.appearance.copy(
            rootBackgroundOpacity = intent.opacity.coerceIn(0f, 1f),
            rootBackgroundBlur = intent.blur.coerceIn(0f, 24f),
            rootBackgroundScrim = intent.scrim.coerceIn(0f, 1f),
        )
        _uiState.update { it.copy(appearance = preview, errorMessage = "") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appearanceService.saveRootBackgroundTuning(
                        intent.opacity,
                        intent.blur,
                        intent.scrim,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "保存主页背景参数失败") }
            }
        }
    }

    private fun clearRootBackground() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { appearanceService.clearRootBackground() }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "清除主页背景失败") }
            }
        }
    }

    private fun observeAppearanceTheme() {
        viewModelScope.launch {
            appearanceService.uiPreferencesFlow.collectLatest { preferences ->
                _uiState.update {
                    it.copy(
                        appearance = preferences.appearanceTheme,
                        appearanceMode = preferences.appearanceMode,
                        newCharacterBackground = preferences.newCharacterBackground,
                        errorMessage = "",
                    )
                }
            }
        }
    }

    fun saveThemePalette(source: Bitmap) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { appearanceService.saveThemePalette(source) }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "提取配色失败") }
            }
        }
    }

    fun saveAppearanceTheme(appearance: AppearanceTheme) {
        _uiState.update { it.copy(appearance = appearance, errorMessage = "") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { appearanceService.saveAppearanceTheme(appearance) }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "保存主题失败") }
            }
        }
    }

    private fun saveAppearanceMode(mode: AppearanceMode) {
        _uiState.update { it.copy(appearanceMode = mode, errorMessage = "") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { appearanceService.saveAppearanceMode(mode) }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "外观模式保存失败") }
            }
        }
    }

    private fun saveNewCharacterBackground(background: NewCharacterBackground) {
        _uiState.update { it.copy(newCharacterBackground = background, errorMessage = "") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appearanceService.saveNewCharacterBackground(background)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "新角色默认背景保存失败") }
            }
        }
    }

    fun resetAppearanceTheme() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { appearanceService.resetAppearanceTheme() }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "重置主题失败") }
            }
        }
    }

    companion object {
        fun factory(
            appearanceService: AppearanceService,
            initialAppearance: AppearanceTheme = AppearanceTheme(),
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ThemeViewModel::class.java)) {
                        return ThemeViewModel(appearanceService, initialAppearance) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
