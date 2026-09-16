package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.FrontendBeautyPage
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantPage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryIntent
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryPage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.DynamicSettingsPage
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigPage
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigIntent
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRulesPage
import com.eleckoi.android.app.navigation.MobileRoute

internal fun mobileStoryRouteEntry(
    currentRoute: MobileRoute,
    context: MobileShellRouteContext,
): NavEntry<NavKey>? = with(context) {
    when (currentRoute) {
        is MobileRoute.VariableConfig -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val pageConfig = currentVariableConfigState.value.config
                LaunchedEffect(currentRoute.characterId) {
                    if (pageConfig?.characterId != currentRoute.characterId) {
                        variableConfigViewModel.onIntent(VariableConfigIntent.Load(currentRoute.characterId))
                    }
                }
                VariableConfigPage(
                    config = pageConfig?.takeIf { it.characterId == currentRoute.characterId },
                    appearance = pageAppearance,
                    onBack = goBackInsideApp,
                    onSave = { config ->
                        variableConfigViewModel.onIntent(VariableConfigIntent.Save(currentRoute.characterId, config))
                    },
                    onImport = {
                        documentActions.importVariableConfig(currentRoute.characterId)
                    },
                    onExport = { variableConfigViewModel.onIntent(VariableConfigIntent.Export(currentRoute.characterId)) },
                )
        }
        is MobileRoute.RegexRules -> NavEntry(currentRoute) {
                val regexState = currentRegexRulesState.value
                LaunchedEffect(currentRoute.characterId) {
                    regexRulesViewModel.load(currentRoute.characterId)
                }
                RegexRulesPage(
                    rules = regexState.rules.takeIf { regexState.characterId == currentRoute.characterId },
                    appearance = currentThemeState.value.appearance,
                    errorMessage = regexState.errorMessage,
                    onBack = goBackInsideApp,
                    onSave = { rules -> regexRulesViewModel.save(currentRoute.characterId, rules) },
                    onImportRules = { scope -> documentActions.importRegexRules(currentRoute.characterId, scope) },
                    onExportRules = { ruleIds ->
                        regexRulesViewModel.exportRules(currentRoute.characterId, ruleIds)
                    },
                )
        }
        is MobileRoute.FrontendBeauty -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val character = activeCharacter(currentRoute.characterId)
                FrontendBeautyPage(
                    characterId = currentRoute.characterId,
                    characterName = character?.name.orEmpty(),
                    appearance = pageAppearance,
                    viewModel = frontendBeautyViewModel,
                    onBack = goBackInsideApp,
                )
        }
        MobileRoute.AiCreationAssistant -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                AiCreationAssistantPage(
                    appearance = pageAppearance,
                    viewModel = aiCreationAssistantViewModel,
                    chatGateway = chatViewModel,
                    onBack = goBackInsideApp,
                    // The assistant's own switch set; blank means the shared scope.
                    onOpenTools = onOpenPresetToolsDialog,
                )
        }
        is MobileRoute.SettingLibrary -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val pageLibrary = currentSettingLibraryState.value.library
                val activePreset = currentAgentPresetState.value.activePreset
                val toolContext = toolContextSnapshotProvider(
                    activePreset?.toolConfiguration?.enabledGroupIds.orEmpty(),
                )
                LaunchedEffect(currentRoute.characterId) {
                    if (pageLibrary?.characterId != currentRoute.characterId) {
                        settingLibraryViewModel.onIntent(SettingLibraryIntent.Load(currentRoute.characterId))
                    }
                }
                val character = activeCharacter(currentRoute.characterId)
                SettingLibraryPage(
                    characterName = character?.name.orEmpty(),
                    characterAvatar = character?.avatar.orEmpty(),
                    library = pageLibrary?.takeIf { it.characterId == currentRoute.characterId },
                    appearance = pageAppearance,
                    toolContextNames = toolContext.groups
                        .filter { it.enabled }
                        .map { it.name },
                    onBack = goBackInsideApp,
                    onSave = { library ->
                        settingLibraryViewModel.onIntent(SettingLibraryIntent.Save(currentRoute.characterId, library))
                    },
                    onImport = {
                        documentActions.importSettingLibrary(currentRoute.characterId)
                    },
                    onExport = {
                        settingLibraryViewModel.onIntent(SettingLibraryIntent.Export(currentRoute.characterId))
                    },
                    importSources = currentSettingLibraryState.value.importSources,
                    loadingImportSources = currentSettingLibraryState.value.loadingImportSources,
                    onRequestImportSources = {
                        settingLibraryViewModel.onIntent(
                            SettingLibraryIntent.LoadImportSources(currentRoute.characterId),
                        )
                    },
                    onParseImportFile = settingLibraryViewModel::parseImportFile,
                )
        }
        is MobileRoute.DynamicSettings -> NavEntry(currentRoute) {
                LaunchedEffect(currentRoute.characterId) {
                    settingLibraryViewModel.onIntent(
                        SettingLibraryIntent.LoadConversationLibraries(currentRoute.characterId),
                    )
                }
                DynamicSettingsPage(
                    conversations = currentSettingLibraryState.value.conversationLibraries,
                    loading = currentSettingLibraryState.value.loadingConversationLibraries,
                    savingConversationVersionId = currentSettingLibraryState.value.savingConversationVersionId,
                    mutatingConversationId = currentSettingLibraryState.value.mutatingConversationId,
                    expandedGroupIdsByConversation = currentSettingLibraryState.value.dynamicExpandedGroupIds,
                    initialSessionId = currentRoute.sessionId,
                    appearance = currentThemeState.value.appearance,
                    onBack = goBackInsideApp,
                    onSaveAsVersion = { sessionId, name ->
                        settingLibraryViewModel.onIntent(
                            SettingLibraryIntent.SaveConversationVersion(
                                characterId = currentRoute.characterId,
                                sessionId = sessionId,
                                name = name,
                            ),
                        )
                    },
                    onUpdateEntry = { sessionId, entryId, title, content ->
                        settingLibraryViewModel.onIntent(
                            SettingLibraryIntent.UpdateConversationEntry(
                                characterId = currentRoute.characterId,
                                sessionId = sessionId,
                                entryId = entryId,
                                title = title,
                                content = content,
                            ),
                        )
                    },
                    onReplaceLibrary = { sessionId, library, successMessage ->
                        settingLibraryViewModel.onIntent(
                            SettingLibraryIntent.ReplaceConversationLibrary(
                                characterId = currentRoute.characterId,
                                sessionId = sessionId,
                                library = library,
                                successMessage = successMessage,
                            ),
                        )
                    },
                    onDeleteEntry = { sessionId, entryId ->
                        settingLibraryViewModel.onIntent(
                            SettingLibraryIntent.DeleteConversationEntry(
                                characterId = currentRoute.characterId,
                                sessionId = sessionId,
                                entryId = entryId,
                            ),
                        )
                    },
                    onDeleteGroup = { sessionId, groupId ->
                        settingLibraryViewModel.onIntent(
                            SettingLibraryIntent.DeleteConversationGroup(
                                characterId = currentRoute.characterId,
                                sessionId = sessionId,
                                groupId = groupId,
                            ),
                        )
                    },
                    onExpandedGroupIdsChange = settingLibraryViewModel::updateDynamicExpandedGroups,
                    onDeleteConversationSettings = { sessionId ->
                        settingLibraryViewModel.onIntent(
                            SettingLibraryIntent.DeleteConversationSettings(
                                characterId = currentRoute.characterId,
                                sessionId = sessionId,
                            ),
                        )
                    },
                )
        }
        else -> null
    }
}
