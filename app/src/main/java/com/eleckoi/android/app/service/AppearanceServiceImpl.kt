package com.eleckoi.android.app.service

import android.graphics.Bitmap
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.feature.preferences.AppearanceMode
import com.eleckoi.android.feature.preferences.NewCharacterBackground
import com.eleckoi.android.feature.settings.api.AppearanceService
import com.eleckoi.android.feature.settings.data.appearance.AppearanceRepository
import com.eleckoi.android.foundation.design.AppearanceTheme
import kotlinx.coroutines.flow.Flow

internal class AppearanceServiceImpl(
    private val appearance: AppearanceRepository,
    private val uiPreferences: UiPreferencesRepository,
) : AppearanceService {
    override val uiPreferencesFlow: Flow<UiPreferences> = uiPreferences.preferencesFlow

    override suspend fun loadAppearanceTheme(): AppearanceTheme = appearance.load()

    override suspend fun saveThemePalette(source: Bitmap): AppearanceTheme = appearance.savePalette(source)

    override suspend fun saveRootBackground(
        source: Bitmap,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): AppearanceTheme = appearance.saveRootBackground(source, opacity, blur, scrim)

    override suspend fun saveRootBackgroundTuning(
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): AppearanceTheme = appearance.saveRootBackgroundTuning(opacity, blur, scrim)

    override suspend fun clearRootBackground(): AppearanceTheme = appearance.clearRootBackground()

    override suspend fun saveAppearanceMode(mode: AppearanceMode): UiPreferences =
        uiPreferences.setAppearanceMode(mode)

    override suspend fun saveNewCharacterBackground(
        background: NewCharacterBackground,
    ): UiPreferences = uiPreferences.setNewCharacterBackground(background)

    override suspend fun saveAppearanceTheme(theme: AppearanceTheme): AppearanceTheme = appearance.save(theme)

    override suspend fun resetAppearanceTheme(): AppearanceTheme = appearance.reset()
}
