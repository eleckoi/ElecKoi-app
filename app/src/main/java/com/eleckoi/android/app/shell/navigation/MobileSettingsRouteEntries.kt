package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetPage
import com.eleckoi.android.feature.modelconfig.ui.ModelSettingsPage
import com.eleckoi.android.feature.settings.ui.personalization.SettingsPage
import com.eleckoi.android.feature.settings.ui.personalization.CrashDiagnosticsPage
import com.eleckoi.android.feature.settings.ui.personalization.about.AboutElecKoiPage
import com.eleckoi.android.feature.settings.ui.personalization.artwork.ListCharacterArtworkSettingsPage
import com.eleckoi.android.feature.settings.ui.personalization.chat.ChatDisplaySettingsPage
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
                    onSave = { config, onResult ->
                        modelsViewModel.saveModelConfig(config, onResult)
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
                var communityDialogOpen by rememberSaveable { mutableStateOf(false) }
                SettingsPage(
                    appearance = pageAppearance,
                    onBack = goBackInsideApp,
                    onOpenUserProfile = { navigateTo(MobileRoute.Profile) },
                    onOpenThemeStyle = { navigateTo(MobileRoute.Theme) },
                    onOpenChatDisplay = { navigateTo(MobileRoute.ChatDisplay) },
                    onOpenFont = { navigateTo(MobileRoute.FontSettings) },
                    listCharacterArtwork = currentShellState.value.listCharacterArtwork,
                    onOpenListCharacterArtwork = {
                        navigateTo(MobileRoute.ListCharacterArtworkSettings)
                    },
                    onOpenAbout = { navigateTo(MobileRoute.About) },
                    onOpenCommunity = { communityDialogOpen = true },
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
                if (communityDialogOpen) {
                    MobileCommunityDialog(
                        appearance = pageAppearance,
                        onDismiss = { communityDialogOpen = false },
                    )
                }
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
        MobileRoute.ListCharacterArtworkSettings -> NavEntry(currentRoute) {
                ListCharacterArtworkSettingsPage(
                    appearance = currentThemeState.value.appearance,
                    selectedArtwork = currentShellState.value.listCharacterArtwork,
                    onArtworkChange = { artwork ->
                        shellViewModel.onIntent(ShellIntent.SetListCharacterArtwork(artwork))
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
                    onClearCover = {
                        profileViewModel.onIntent(ProfileIntent.ClearCover)
                    },
                )
        }
        MobileRoute.AgentPresets -> NavEntry(currentRoute) {
                AgentPresetPageContent(showRootBackButton = true)
        }
        else -> null
    }
}

@Composable
internal fun MobileShellRouteContext.AgentPresetPageContent(
    showRootBackButton: Boolean,
    onOpenSidebar: (() -> Unit)? = null,
) {
    val pageAppearance = currentThemeState.value.appearance
    val pagePresetState = currentAgentPresetState.value
    AgentPresetPage(
        catalog = pagePresetState.catalog,
        editorPreset = pagePresetState.editorPreset,
        editorEntryId = pagePresetState.editorEntryId,
        editorStartOnTools = pagePresetState.editorStartOnTools,
        returnToCallerAfterEntry = pagePresetState.returnToCallerAfterEntry,
        loadingEditor = pagePresetState.loadingEditor,
        exporting = pagePresetState.exporting,
        appearance = pageAppearance,
        toolGroups = pagePresetState.editorPreset?.let { preset ->
            toolGroupsProvider(preset.toolConfiguration.enabledGroupIds)
        }.orEmpty(),
        modelConfigs = currentModelsState.value.models?.configs.orEmpty(),
        showRootBackButton = showRootBackButton,
        onOpenSidebar = onOpenSidebar,
        onBack = goBackInsideApp,
        onOpenPreset = agentPresetViewModel::openPreset,
        onEditorEntryOpened = agentPresetViewModel::editorEntryOpened,
        onReturnFromExternalEntry = goBackInsideApp,
        onCloseEditor = agentPresetViewModel::closeEditor,
        onSetActive = agentPresetViewModel::setActive,
        onCreate = agentPresetViewModel::create,
        onImport = onOpenAgentPresetImportSource,
        onExport = agentPresetViewModel::exportPresets,
        onUpdate = agentPresetViewModel::update,
        onRename = agentPresetViewModel::rename,
        onDuplicate = agentPresetViewModel::duplicate,
        onDelete = agentPresetViewModel::delete,
        onCreateGroup = agentPresetViewModel::createLibraryGroup,
        onRenameGroup = agentPresetViewModel::renameLibraryGroup,
        onDeleteGroup = agentPresetViewModel::deleteLibraryGroup,
        onMoveToGroup = agentPresetViewModel::moveToLibraryGroup,
        onUpdateProfile = agentPresetViewModel::updateProfile,
        onUpdateModelTags = agentPresetViewModel::updateModelTags,
        onUpdateAuthorAvatar = agentPresetViewModel::updateAuthorAvatar,
        onOpenWebSearchSettings = {
            navigateTo(MobileRoute.WebSearchSettings)
        },
        onSaveModelConfig = modelsViewModel::saveModelConfig,
    )
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
