package com.eleckoi.android.feature.settings.api

import android.graphics.Bitmap
import android.net.Uri
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.feature.preferences.AppearanceMode
import com.eleckoi.android.feature.preferences.NewCharacterBackground
import com.eleckoi.android.foundation.design.AppearanceTheme
import java.io.File
import kotlinx.coroutines.flow.Flow

interface ProfileService {
    val userProfileFlow: Flow<UserProfile>

    fun saveUserName(name: String): UserProfile
    fun saveUserAvatars(files: Map<AvatarSlot, File>): UserProfile
    fun saveUserCover(coverUri: Uri): UserProfile
    fun clearUserCover(): UserProfile
}

interface AppearanceService {
    val uiPreferencesFlow: Flow<UiPreferences>

    suspend fun loadAppearanceTheme(): AppearanceTheme
    suspend fun saveThemePalette(source: Bitmap): AppearanceTheme
    suspend fun saveRootBackground(source: Bitmap, opacity: Float, blur: Float, scrim: Float): AppearanceTheme
    suspend fun saveRootBackgroundTuning(opacity: Float, blur: Float, scrim: Float): AppearanceTheme
    suspend fun clearRootBackground(): AppearanceTheme
    suspend fun saveAppearanceMode(mode: AppearanceMode): UiPreferences
    suspend fun saveNewCharacterBackground(background: NewCharacterBackground): UiPreferences
    suspend fun saveAppearanceTheme(theme: AppearanceTheme): AppearanceTheme
    suspend fun resetAppearanceTheme(): AppearanceTheme
}
