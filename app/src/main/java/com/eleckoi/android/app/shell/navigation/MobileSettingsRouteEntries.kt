package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.eleckoi.android.feature.characters.modes.story.presets.ui.StoryPresetPage
import com.eleckoi.android.feature.modelconfig.ui.ModelSettingsPage
import com.eleckoi.android.feature.settings.ui.personalization.SettingsPage
import com.eleckoi.android.feature.settings.ui.personalization.CrashDiagnosticsPage
import com.eleckoi.android.feature.settings.ui.personalization.about.AboutElecKoiPage
import com.eleckoi.android.feature.settings.ui.personalization.chat.ChatDisplaySettingsPage
import com.eleckoi.android.feature.settings.ui.personalization.common.CommonPagesSettingsPage
import com.eleckoi.android.feature.settings.ui.personalization.font.FontSettingsPage
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileEditPage
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileIntent
import com.eleckoi.android.feature.modelconfig.ui.ModelsIntent
import com.eleckoi.android.app.navigation.MobileRoute
import com.eleckoi.android.feature.settings.ui.runtime.LocalRuntimeSettingsPage
import com.eleckoi.android.feature.settings.ui.websearch.WebSearchSettingsPage
import com.eleckoi.android.feature.settings.ui.update.AppUpdatePage
import com.eleckoi.android.app.service.backup.BackupMode
import com.eleckoi.android.app.service.backup.BackupPhase
import com.eleckoi.android.app.service.backup.BackupProgress

internal fun mobileSettingsRouteEntry(
    currentRoute: MobileRoute,
    context: MobileShellRouteContext,
): NavEntry<NavKey>? = with(context) {
    when (currentRoute) {
        is MobileRoute.ModelSettings -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                ModelSettingsPage(
                    models = currentModelsState.value.models,
                    target = currentRoute.target,
                    appearance = pageAppearance,
                    onBack = closeRoute,
                    onSave = { config ->
                        modelsViewModel.onIntent(ModelsIntent.SaveModelConfig(config))
                    },
                    onCreateConfig = { providerId ->
                        replaceTop(
                            MobileRoute.ModelSettings(modelsViewModel.createDraftTarget(providerId)),
                        )
                    },
                    onDeleteConfig = { configId ->
                        modelsViewModel.onIntent(ModelsIntent.DeleteModelConfig(configId))
                    },
                    onFetchModels = { config, onResult ->
                        modelsViewModel.onIntent(ModelsIntent.FetchModelOptions(config, onResult))
                    },
                    onTestConnection = { config, onResult ->
                        modelsViewModel.onIntent(ModelsIntent.TestModelConnection(config, onResult))
                    },
                )
        }
        MobileRoute.Settings -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val backupProgress = dataBackupActions.progress.value
                SettingsPage(
                    appearance = pageAppearance,
                    onBack = goBackInsideApp,
                    onOpenUserProfile = { navigateTo(MobileRoute.Profile) },
                    onOpenThemeStyle = { navigateTo(MobileRoute.Theme) },
                    onOpenChatDisplay = { navigateTo(MobileRoute.ChatDisplay) },
                    onOpenCommonPages = { navigateTo(MobileRoute.CommonPages) },
                    onOpenFont = { navigateTo(MobileRoute.FontSettings) },
                    onOpenAbout = { navigateTo(MobileRoute.About) },
                    onOpenLocalRuntime = { navigateTo(MobileRoute.RuntimeSettings) },
                    onOpenCrashDiagnostics = { navigateTo(MobileRoute.CrashDiagnostics) },
                    onOpenAppUpdate = { navigateTo(MobileRoute.AppUpdate) },
                    appUpdateAvailable = currentAppUpdateState.value.updateAvailable,
                    appUpdateLatestVersion = currentAppUpdateState.value.latestVersion,
                    appUpdateChecking = currentAppUpdateState.value.checking,
                    appUpdateCheckedOnce = currentAppUpdateState.value.checkedOnce,
                    agentBackgroundProtectionEnabled =
                        currentAgentBackgroundProtectionEnabled.value,
                    onAgentBackgroundProtectionEnabledChange =
                        currentOnAgentBackgroundProtectionEnabledChange.value,
                    onAgentBackgroundProtectionPermissionChanged =
                        currentOnAgentBackgroundProtectionPermissionChanged.value,
                    backupBusy = dataBackupActions.busy.value,
                    backupProgressText = backupProgress?.displayText(),
                    backupProgressFraction = backupProgress?.fraction,
                    backupCancellable = backupProgress?.cancellable == true,
                    onExportBackup = dataBackupActions.export,
                    onImportBackup = dataBackupActions.import,
                    onCancelBackup = dataBackupActions.cancel,
                )
        }
        MobileRoute.About -> NavEntry(currentRoute) {
                AboutElecKoiPage(
                    appearance = currentThemeState.value.appearance,
                    appIconResId = com.eleckoi.android.R.drawable.whale_maid_app_icon_20260814,
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.AppUpdate -> NavEntry(currentRoute) {
                val state = currentAppUpdateState.value
                AppUpdatePage(
                    appearance = currentThemeState.value.appearance,
                    installedVersion = state.installedVersion,
                    latestVersion = state.latestVersion,
                    releaseNotes = state.latestRelease?.notes.orEmpty(),
                    releasePageUrl = state.latestRelease?.pageUrl.orEmpty(),
                    updateAvailable = state.updateAvailable,
                    remindersEnabled = state.remindersEnabled,
                    checking = state.checking,
                    checkedOnce = state.checkedOnce,
                    errorMessage = state.errorMessage,
                    onRefresh = appUpdateViewModel::refresh,
                    onRemindersEnabledChange = appUpdateViewModel::setRemindersEnabled,
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.FontSettings -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                FontSettingsPage(
                    appearance = pageAppearance,
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.CommonPages -> NavEntry(currentRoute) {
                CommonPagesSettingsPage(
                    appearance = currentThemeState.value.appearance,
                    presetPagePinned = currentShellState.value.presetPagePinned,
                    pluginPagePinned = currentShellState.value.pluginPagePinned,
                    commonPageOrder = currentShellState.value.commonPageOrder,
                    onOptionalPageChange = { tab ->
                        shellViewModel.onIntent(
                            ShellIntent.SetOptionalCommonPage(tab),
                        )
                    },
                    onOrderChange = { visibleTabs ->
                        shellViewModel.onIntent(
                            ShellIntent.SetCommonPageOrder(visibleTabs),
                        )
                    },
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.RuntimeSettings -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                LocalRuntimeSettingsPage(
                    appearance = pageAppearance,
                    viewModel = localRuntimeSettingsViewModel,
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.CrashDiagnostics -> NavEntry(currentRoute) {
                CrashDiagnosticsPage(
                    appearance = currentThemeState.value.appearance,
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.WebSearchSettings -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                WebSearchSettingsPage(
                    appearance = pageAppearance,
                    viewModel = webSearchSettingsViewModel,
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.ChatDisplay -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                ChatDisplaySettingsPage(
                    viewModel = chatDisplaySettingsViewModel,
                    appearance = pageAppearance,
                    onOpenMarkdownReadingColors = {
                        navigateTo(MobileRoute.MarkdownReadingColors)
                    },
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.Profile -> NavEntry(currentRoute) {
                val pageUser = currentProfileState.value.user
                val pageAppearance = currentThemeState.value.appearance
                ProfileEditPage(
                    user = pageUser,
                    appearance = pageAppearance,
                    onBack = goBackInsideApp,
                    onSaveName = { name ->
                        profileViewModel.onIntent(ProfileIntent.SaveName(name))
                    },
                    onSaveAvatars = { files ->
                        profileViewModel.onIntent(ProfileIntent.SaveAvatars(files))
                    },
                    onSaveCover = { uri ->
                        profileViewModel.onIntent(ProfileIntent.SaveCover(uri))
                    },
                )
        }
        is MobileRoute.StoryPresets -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val pagePresetState = currentStoryPresetState.value
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        StoryPresetPage(
                            catalog = pagePresetState.catalog,
                            editorPreset = pagePresetState.editorPreset,
                            editorEntryId = pagePresetState.editorEntryId,
                            returnToCallerAfterEntry = pagePresetState.returnToCallerAfterEntry,
                            loadingEditor = pagePresetState.loadingEditor,
                            exporting = pagePresetState.exporting,
                            appearance = pageAppearance,
                            showRootBackButton = !currentRoute.rootTab,
                            onBack = goBackInsideApp,
                            onOpenPreset = storyPresetViewModel::openPreset,
                            onEditorEntryOpened = storyPresetViewModel::editorEntryOpened,
                            onReturnFromExternalEntry = goBackInsideApp,
                            onCloseEditor = storyPresetViewModel::closeEditor,
                            onSetActive = storyPresetViewModel::setActive,
                            onCreate = storyPresetViewModel::create,
                            onImport = onOpenStoryPresetImportSource,
                            onExport = storyPresetViewModel::exportPresets,
                            onUpdate = storyPresetViewModel::update,
                            onRename = storyPresetViewModel::rename,
                            onDuplicate = storyPresetViewModel::duplicate,
                            onDelete = storyPresetViewModel::delete,
                            onCreateGroup = storyPresetViewModel::createLibraryGroup,
                            onRenameGroup = storyPresetViewModel::renameLibraryGroup,
                            onDeleteGroup = storyPresetViewModel::deleteLibraryGroup,
                            onMoveToGroup = storyPresetViewModel::moveToLibraryGroup,
                            onUpdateProfile = storyPresetViewModel::updateProfile,
                            onUpdateModelTags = storyPresetViewModel::updateModelTags,
                            onUpdateAuthorAvatar = storyPresetViewModel::updateAuthorAvatar,
                        )
                    }
                    if (currentRoute.rootTab && pagePresetState.editorPreset == null) {
                        MobileTabBar(
                            activeTab = BottomTab.Presets,
                            tabs = BottomTab.visibleTabs(
                                presetsPinned = currentShellState.value.presetPagePinned,
                                pluginsPinned = currentShellState.value.pluginPagePinned,
                                order = currentShellState.value.commonPageOrder,
                            ),
                            appearance = pageAppearance,
                            onChange = selectBottomTab,
                        )
                    }
                }
        }
        else -> null
    }
}

private fun BackupProgress.displayText(): String {
    val action = if (mode == BackupMode.Export) "导出" else "导入"
    val count = if (total > 0) " $completed/$total" else ""
    return when (phase) {
        BackupPhase.Preparing -> "正在准备${action}"
        BackupPhase.Validating -> "正在校验备份$count"
        BackupPhase.Transferring -> "正在${action}数据$count"
        BackupPhase.Restoring -> "正在重建数据库$count"
    }
}
