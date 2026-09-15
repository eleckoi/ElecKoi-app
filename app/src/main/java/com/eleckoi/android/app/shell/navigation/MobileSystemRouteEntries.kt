package com.eleckoi.android.app.shell

import com.eleckoi.android.feature.characters.ui.components.AvatarSlotsPage
import com.eleckoi.android.foundation.design.components.*
import com.eleckoi.android.feature.characters.model.AvatarSlot
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.eleckoi.android.feature.chat.ui.layout.asRoleplayReadingTheme
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.settings.ui.personalization.markdown.MarkdownReadingColorsPage
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileIntent
import com.eleckoi.android.feature.settings.ui.personalization.theme.ThemePalettePage
import com.eleckoi.android.feature.settings.ui.personalization.theme.ThemeIntent
import com.eleckoi.android.app.navigation.MobileRoute
import com.eleckoi.android.feature.settings.ui.remotedsh.RemoteDshSettingsPage
import com.eleckoi.android.feature.settings.ui.remotedsh.RemoteDshSessionPage

internal fun mobileSystemRouteEntry(
    currentRoute: MobileRoute,
    context: MobileShellRouteContext,
): NavEntry<NavKey>? = with(context) {
    when (currentRoute) {
        is MobileRoute.RemoteDshSettings -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                RemoteDshSettingsPage(
                    appearance = pageAppearance,
                    viewModel = remoteDshSettingsViewModel,
                    toolScopeId = currentRoute.toolScopeId,
                    onBack = goBackInsideApp,
                    onOpenSession = { sessionId ->
                        navigateTo(MobileRoute.RemoteDshSession(sessionId))
                    },
                )
        }
        is MobileRoute.RemoteDshSession -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                RemoteDshSessionPage(
                    sessionId = currentRoute.sessionId,
                    plugin = remoteDshPlugin,
                    appearance = pageAppearance,
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.UserAvatars -> NavEntry(currentRoute) {
                val pageUser = currentProfileState.value.user
                val pageAppearance = currentThemeState.value.appearance
                AvatarSlotsPage(
                    avatars = pageUser.avatars,
                    displayName = pageUser.userName,
                    cachePrefix = "user",
                    appearance = pageAppearance,
                    onBack = goBackInsideApp,
                    onSave = { files ->
                        profileViewModel.onIntent(ProfileIntent.SaveAvatars(files))
                    },
                )
        }
        MobileRoute.Theme -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                ThemePalettePage(
                    appearance = pageAppearance,
                    appearanceMode = currentThemeState.value.appearanceMode,
                    onAppearanceModeChange = { mode ->
                        themeViewModel.onIntent(ThemeIntent.SaveAppearanceMode(mode))
                    },
                    onApplyPalette = { bitmap ->
                        themeViewModel.onIntent(ThemeIntent.SaveThemePalette(bitmap))
                    },
                    onSetRootBackground = { bitmap, opacity, blur, scrim ->
                        themeViewModel.onIntent(
                            ThemeIntent.SaveRootBackground(bitmap, opacity, blur, scrim),
                        )
                    },
                    onTuneRootBackground = { opacity, blur, scrim ->
                        themeViewModel.onIntent(
                            ThemeIntent.SaveRootBackgroundTuning(opacity, blur, scrim),
                        )
                    },
                    onClearRootBackground = {
                        themeViewModel.onIntent(ThemeIntent.ClearRootBackground)
                    },
                    onReset = {
                        themeViewModel.onIntent(ThemeIntent.ResetAppearanceTheme)
                    },
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.MarkdownReadingColors -> NavEntry(currentRoute) {
                MarkdownReadingColorsPage(
                    appearance = currentThemeState.value.appearance,
                    previewAppearance = if (chatState.chatLayoutMode == ChatLayoutMode.Roleplay) {
                        currentThemeState.value.appearance.asRoleplayReadingTheme()
                    } else {
                        currentThemeState.value.appearance
                    },
                    onSave = { updated ->
                        themeViewModel.onIntent(ThemeIntent.SaveAppearanceTheme(updated))
                    },
                    onBack = goBackInsideApp,
                )
        }
        else -> null
    }
}
