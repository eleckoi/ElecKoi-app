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
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.withRoleplayPlanEnabled
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.DynamicSettingsPage
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigPage
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigIntent
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRulesPage
import com.eleckoi.android.app.navigation.MobileRoute
import com.eleckoi.android.engine.agent.tools.AgentToolScopes
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.immersive.security.AuthorFrontendStoragePrincipal
import com.eleckoi.android.feature.agenttools.ui.tools.AgentToolGroupDetailPage
import com.eleckoi.android.feature.agenttools.ui.tools.AgentToolsPage
import com.eleckoi.android.feature.chat.ui.immersive.ImmersiveChatScreen

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
                LaunchedEffect(currentRoute.characterId) {
                    if (currentRegexRulesState.value.characterId != currentRoute.characterId) {
                        regexRulesViewModel.load(currentRoute.characterId)
                    }
                }
                RegexRulesPage(
                    rules = currentRegexRulesState.value.rules,
                    appearance = currentThemeState.value.appearance,
                    errorMessage = currentRegexRulesState.value.errorMessage,
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
                    previewContent = { project, directory, onExit ->
                        ImmersiveChatScreen(
                            project = project,
                            projectDirectory = directory,
                            characterName = character?.name.orEmpty(),
                            characterAvatarPath = character?.persona?.assistantAvatar
                                ?.ifBlank { character.avatar }
                                .orEmpty(),
                            chatGateway = chatViewModel,
                            appearance = pageAppearance,
                            storagePrincipal = AuthorFrontendStoragePrincipal.publishedProject(project.id),
                            onExit = onExit,
                            onFallbackToNative = onExit,
                        )
                    },
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
                    onOpenPlugins = { navigateTo(MobileRoute.AgentTools()) },
                )
        }
        is MobileRoute.AgentTools -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        AgentToolsPage(
                            appearance = pageAppearance,
                            viewModel = agentToolsViewModel,
                            toolScopeId = AgentToolScopes.character(currentRoute.characterId),
                            title = if (currentRoute.rootTab) "插件" else "工具",
                            showRootBackButton = !currentRoute.rootTab,
                            onBack = goBackInsideApp,
                            onOpenGroup = { groupId ->
                                navigateTo(
                                    MobileRoute.AgentToolGroup(currentRoute.characterId, groupId),
                                )
                            },
                            onOpenWebSearch = { navigateTo(MobileRoute.WebSearchSettings) },
                            onOpenRemoteDsh = {
                                navigateTo(
                                    MobileRoute.RemoteDshSettings(
                                        AgentToolScopes.character(currentRoute.characterId),
                                    ),
                                )
                            },
                        )
                    }
                    if (currentRoute.rootTab) {
                        MobileTabBar(
                            activeTab = BottomTab.Plugins,
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
        is MobileRoute.AgentToolGroup -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                AgentToolGroupDetailPage(
                    appearance = pageAppearance,
                    viewModel = agentToolsViewModel,
                    toolScopeId = AgentToolScopes.character(currentRoute.characterId),
                    groupId = currentRoute.groupId,
                    onBack = goBackInsideApp,
                )
        }
        is MobileRoute.SettingLibrary -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val pageLibrary = currentSettingLibraryState.value.library
                val toolContext = toolContextSnapshotProvider(
                    AgentToolScopes.character(currentRoute.characterId),
                )
                val roleplayPlanEnabled = toolContext.isEnabled(
                    AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
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
                    library = pageLibrary
                        ?.takeIf { it.characterId == currentRoute.characterId }
                        ?.withRoleplayPlanEnabled(roleplayPlanEnabled),
                    activePreset = currentStoryPresetState.value.activePreset,
                    appearance = pageAppearance,
                    toolContextNames = toolContext.groups
                        .filter { it.enabled }
                        .map { it.name },
                    onBack = goBackInsideApp,
                    onSave = { library ->
                        settingLibraryViewModel.onIntent(SettingLibraryIntent.Save(currentRoute.characterId, library))
                    },
                    onUpdateActivePreset = storyPresetViewModel::update,
                    onOpenActivePreset = { presetId ->
                        storyPresetViewModel.openPreset(presetId)
                        navigateTo(MobileRoute.StoryPresets())
                    },
                    onOpenActivePresetEntry = { presetId, entryId ->
                        storyPresetViewModel.openPresetEntry(presetId, entryId)
                        navigateTo(MobileRoute.StoryPresets())
                    },
                    onRoleplayPlanEnabledChange = { enabled ->
                        agentToolsViewModel.setRoleplayPlanEnabledFromSettingLibrary(
                            scopeId = AgentToolScopes.character(currentRoute.characterId),
                            enabled = enabled,
                        )
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
