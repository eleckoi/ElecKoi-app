package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import com.eleckoi.android.foundation.design.isVisuallyDark
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.characters.model.CharacterSlot
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eleckoi.android.feature.characters.ui.CharactersIntent
import com.eleckoi.android.feature.characters.ui.CharactersViewModel
import com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.FrontendBeautyViewModel
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantViewModel
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryIntent
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryViewModel
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetViewModel
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigIntent
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigViewModel
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRulesViewModel
import com.eleckoi.android.feature.chat.ui.ChatViewModel
import com.eleckoi.android.feature.chat.ui.layout.asRoleplayReadingTheme
import com.eleckoi.android.foundation.design.withDarkAppearance
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.settings.ui.personalization.chat.ChatDisplaySettingsViewModel
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileViewModel
import com.eleckoi.android.feature.settings.ui.personalization.theme.ThemeViewModel
import com.eleckoi.android.feature.modelconfig.ui.ModelsViewModel
import com.eleckoi.android.app.navigation.MobileBackHandler
import com.eleckoi.android.app.navigation.MobileRoute
import com.eleckoi.android.app.navigation.replaceTopWith
import com.eleckoi.android.feature.settings.ui.runtime.LocalRuntimeSettingsViewModel
import com.eleckoi.android.feature.settings.ui.websearch.WebSearchSettingsViewModel
import com.eleckoi.android.feature.settings.ui.remotedsh.RemoteDshSettingsViewModel
import com.eleckoi.android.engine.agent.remotedsh.RemoteDshPlugin
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.feature.characters.presets.ui.editor.AgentPresetQuickToolsDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eleckoi.android.app.service.backup.DataBackupService
import com.eleckoi.android.app.update.AppUpdateViewModel
import com.eleckoi.android.app.update.AppUpdatePromptDialog
import com.eleckoi.android.app.update.shouldShowAppUpdatePrompt

@Composable
internal fun MobileShell(
    shellViewModel: ShellViewModel,
    charactersViewModel: CharactersViewModel,
    settingLibraryViewModel: SettingLibraryViewModel,
    agentPresetViewModel: AgentPresetViewModel,
    variableConfigViewModel: VariableConfigViewModel,
    regexRulesViewModel: RegexRulesViewModel,
    frontendBeautyViewModel: FrontendBeautyViewModel,
    aiCreationAssistantViewModel: AiCreationAssistantViewModel,
    localRuntimeSettingsViewModel: LocalRuntimeSettingsViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    webSearchSettingsViewModel: WebSearchSettingsViewModel,
    remoteDshSettingsViewModel: RemoteDshSettingsViewModel,
    remoteDshPlugin: RemoteDshPlugin,
    modelsViewModel: ModelsViewModel,
    profileViewModel: ProfileViewModel,
    themeViewModel: ThemeViewModel,
    chatDisplaySettingsViewModel: ChatDisplaySettingsViewModel,
    chatViewModel: ChatViewModel,
    dataBackupService: DataBackupService,
    toolContextSnapshotProvider: (Set<String>) -> AgentToolContextSnapshot,
    toolGroupsProvider: (Set<String>) -> List<AgentToolGroupSnapshot>,
    agentBackgroundProtectionEnabled: Boolean,
    onAgentBackgroundProtectionEnabledChange: (Boolean) -> Unit,
    onAgentBackgroundProtectionPermissionChanged: () -> Unit,
) {
    val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()
    val charactersState by charactersViewModel.uiState.collectAsStateWithLifecycle()
    val settingLibraryState by settingLibraryViewModel.uiState.collectAsStateWithLifecycle()
    val agentPresetState by agentPresetViewModel.uiState.collectAsStateWithLifecycle()
    val variableConfigState by variableConfigViewModel.uiState.collectAsStateWithLifecycle()
    val regexRulesState by regexRulesViewModel.uiState.collectAsStateWithLifecycle()
    val aiCreationAssistantState by aiCreationAssistantViewModel.uiState.collectAsStateWithLifecycle()
    val modelsState by modelsViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val appearance = remember(themeState.appearance, themeState.appearanceMode, systemDark) {
        themeState.appearance.withDarkAppearance(
            themeState.appearanceMode.resolvesDark(systemDark),
        )
    }
    val user = profileState.user
    val moreOpen = shellState.moreOpen
    val backStack = rememberNavBackStack(MobileRoute.Root)
    val route = backStack.lastOrNull() as? MobileRoute ?: MobileRoute.Root
    val morePanelVisible = shouldShowMorePanel(moreOpen = moreOpen, route = route)
    val currentShellState = rememberUpdatedState(shellState)
    val currentCharactersState = rememberUpdatedState(charactersState)
    val currentSettingLibraryState = rememberUpdatedState(settingLibraryState)
    val currentAgentPresetState = rememberUpdatedState(agentPresetState)
    val currentVariableConfigState = rememberUpdatedState(variableConfigState)
    val currentRegexRulesState = rememberUpdatedState(regexRulesState)
    val currentAiCreationAssistantState = rememberUpdatedState(aiCreationAssistantState)
    val currentModelsState = rememberUpdatedState(modelsState)
    val currentProfileState = rememberUpdatedState(profileState)
    val currentThemeState = rememberUpdatedState(themeState.copy(appearance = appearance))
    val currentAppUpdateState = rememberUpdatedState(appUpdateState)
    val currentAgentBackgroundProtectionEnabled =
        rememberUpdatedState(agentBackgroundProtectionEnabled)
    val currentOnAgentBackgroundProtectionEnabledChange =
        rememberUpdatedState(onAgentBackgroundProtectionEnabledChange)
    val currentOnAgentBackgroundProtectionPermissionChanged =
        rememberUpdatedState(onAgentBackgroundProtectionPermissionChanged)
    val context = LocalContext.current
    var characterImportSourceOpen by rememberSaveable { mutableStateOf(false) }
    var agentPresetImportSourceOpen by rememberSaveable { mutableStateOf(false) }
    var presetToolsDialogOpen by rememberSaveable { mutableStateOf(false) }
    var rootSearchOpen by rememberSaveable { mutableStateOf(false) }
    var dismissedUpdateTag by rememberSaveable { mutableStateOf("") }
    val currentRootSearchOpen = rememberUpdatedState(rootSearchOpen)
    fun setRootSearchOpen(open: Boolean) {
        rootSearchOpen = open
    }
    val documentActions = rememberShellDocumentActions(
        onCharactersImported = { json ->
            charactersViewModel.onIntent(CharactersIntent.ImportCharacters(json))
        },
        onSettingLibraryImported = { characterId, json ->
            settingLibraryViewModel.onIntent(SettingLibraryIntent.Import(characterId, json))
        },
        onVariableConfigImported = { characterId, json ->
            variableConfigViewModel.onIntent(VariableConfigIntent.Import(characterId, json))
        },
        onRegexRulesImported = { characterId, scope, documents ->
            regexRulesViewModel.importRules(characterId, scope, documents)
        },
    )
    val characterCardActions = rememberCharacterCardDocumentActions(
        onCardsSelected = { files, source ->
            charactersViewModel.onIntent(CharactersIntent.PrepareCharacterImport(files, source))
        },
    )
    val agentPresetDocumentActions = rememberAgentPresetDocumentActions(
        onDocumentsSelected = agentPresetViewModel::importPresets,
    )
    val dataBackupActions = rememberDataBackupActions(dataBackupService)

    fun activeCharacter(characterId: String): CharacterSlot? {
        return currentCharactersState.value.characters?.items?.firstOrNull { it.id == characterId }
    }

    fun navigateTo(nextRoute: MobileRoute) {
        if (nextRoute == MobileRoute.Root) {
            backStack.clear()
            backStack.add(MobileRoute.Root)
            return
        }
        if (backStack.lastOrNull() != nextRoute) {
            backStack.add(nextRoute)
        }
    }

    fun selectBottomTab(tab: BottomTab) {
        if (tab == BottomTab.Presets) {
            agentPresetViewModel.closeEditor()
        }
        shellViewModel.onIntent(ShellIntent.ChangeTab(tab.rootTab()))
        navigateTo(MobileRoute.Root)
    }

    fun replaceTop(nextRoute: MobileRoute) {
        backStack.replaceTopWith(nextRoute)
    }

    fun closeRoute() {
        navigateTo(MobileRoute.Root)
        settingLibraryViewModel.onIntent(SettingLibraryIntent.Clear)
        variableConfigViewModel.onIntent(VariableConfigIntent.Clear)
    }

    fun goBackInsideApp() {
        val previousRoute = route
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        if (previousRoute != MobileRoute.Root && previousRoute !is MobileRoute.SettingLibrary && previousRoute !is MobileRoute.DynamicSettings) {
            settingLibraryViewModel.onIntent(SettingLibraryIntent.Clear)
        }
        if (previousRoute != MobileRoute.Root && previousRoute !is MobileRoute.VariableConfig) {
            variableConfigViewModel.onIntent(VariableConfigIntent.Clear)
        }
    }

    MobileShellEffects(
        route = route,
        chatState = chatState,
        appearance = appearance,
        charactersState = charactersState,
        profileState = profileState,
        settingLibraryState = settingLibraryState,
        agentPresetState = agentPresetState,
        shellViewModel = shellViewModel,
        charactersViewModel = charactersViewModel,
        settingLibraryViewModel = settingLibraryViewModel,
        variableConfigViewModel = variableConfigViewModel,
        agentPresetViewModel = agentPresetViewModel,
        regexRulesViewModel = regexRulesViewModel,
        profileViewModel = profileViewModel,
        chatViewModel = chatViewModel,
        documentActions = documentActions,
        latestRoute = rememberUpdatedState(route),
        navigateTo = ::navigateTo,
    )

    val roleplayChatOpen = route == MobileRoute.Chat &&
        chatState.chatLayoutMode == ChatLayoutMode.Roleplay
    val presetEditorRootOpen = route == MobileRoute.Root &&
        shellState.activeTab == RootTab.Presets &&
        agentPresetState.editorPreset != null
    val bottomChromeOpen = route == MobileRoute.Root && !presetEditorRootOpen
    val navigationBarColor = when {
        roleplayChatOpen -> appearance.asRoleplayReadingTheme().mobileSurface
        route == MobileRoute.Chat -> appearance.mobileChatBg
        route == MobileRoute.Root && rootSearchOpen -> appearance.mobileSurface
        bottomChromeOpen -> mobileTabBarContainerColor(appearance)
        else -> appearance.mobileBg
    }
    val systemNavigationBarColor = if (morePanelVisible) {
        mobileDrawerContainerColor(appearance)
    } else {
        navigationBarColor
    }
    val routeContext = MobileShellRouteContext(
        currentShellState = currentShellState,
        currentCharactersState = currentCharactersState,
        currentSettingLibraryState = currentSettingLibraryState,
        currentVariableConfigState = currentVariableConfigState,
        currentRegexRulesState = currentRegexRulesState,
        currentAiCreationAssistantState = currentAiCreationAssistantState,
        currentModelsState = currentModelsState,
        currentProfileState = currentProfileState,
        currentThemeState = currentThemeState,
        currentAppUpdateState = currentAppUpdateState,
        currentAgentBackgroundProtectionEnabled = currentAgentBackgroundProtectionEnabled,
        currentOnAgentBackgroundProtectionEnabledChange =
            currentOnAgentBackgroundProtectionEnabledChange,
        currentOnAgentBackgroundProtectionPermissionChanged =
            currentOnAgentBackgroundProtectionPermissionChanged,
        currentAgentPresetState = currentAgentPresetState,
        chatState = chatState,
        appearance = appearance,
        user = user,
        shellViewModel = shellViewModel,
        charactersViewModel = charactersViewModel,
        settingLibraryViewModel = settingLibraryViewModel,
        agentPresetViewModel = agentPresetViewModel,
        variableConfigViewModel = variableConfigViewModel,
        regexRulesViewModel = regexRulesViewModel,
        frontendBeautyViewModel = frontendBeautyViewModel,
        aiCreationAssistantViewModel = aiCreationAssistantViewModel,
        localRuntimeSettingsViewModel = localRuntimeSettingsViewModel,
        appUpdateViewModel = appUpdateViewModel,
        webSearchSettingsViewModel = webSearchSettingsViewModel,
        remoteDshSettingsViewModel = remoteDshSettingsViewModel,
        remoteDshPlugin = remoteDshPlugin,
        modelsViewModel = modelsViewModel,
        profileViewModel = profileViewModel,
        themeViewModel = themeViewModel,
        chatDisplaySettingsViewModel = chatDisplaySettingsViewModel,
        chatViewModel = chatViewModel,
        toolContextSnapshotProvider = toolContextSnapshotProvider,
        toolGroupsProvider = toolGroupsProvider,
        documentActions = documentActions,
        dataBackupActions = dataBackupActions,
        activeCharacter = ::activeCharacter,
        navigateTo = ::navigateTo,
        replaceTop = ::replaceTop,
        selectBottomTab = ::selectBottomTab,
        goBackInsideApp = ::goBackInsideApp,
        closeRoute = ::closeRoute,
        rootSearchOpen = currentRootSearchOpen,
        onRootSearchOpenChange = ::setRootSearchOpen,
        onOpenCharacterImportSource = { characterImportSourceOpen = true },
        onOpenAgentPresetImportSource = { agentPresetImportSourceOpen = true },
        onOpenPresetToolsDialog = { presetToolsDialogOpen = true },
    )
    SyncSystemBars(
        navigationBarColor = systemNavigationBarColor,
        darkStatusBarIcons = if (morePanelVisible) {
            !appearance.mobileSurface.isVisuallyDark()
        } else if (route == MobileRoute.Chat) {
            !appearance.asRoleplayReadingTheme().mobileChatHeaderBg.isVisuallyDark()
        } else if (route == MobileRoute.Root && rootSearchOpen) {
            !appearance.mobileSurface.isVisuallyDark()
        } else if (route == MobileRoute.Root) {
            !appearance.mobileTopbarBg.isVisuallyDark()
        } else {
            !appearance.mobileBg.isVisuallyDark()
        },
    )

    MobileBackHandler(
        enabled = morePanelVisible,
        onBack = { shellViewModel.onIntent(ShellIntent.SetMoreOpen(false)) },
    )

    val presetToolsBackdropBlur by animateDpAsState(
        targetValue = if (presetToolsDialogOpen) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "presetToolsBackdropBlur",
    )
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().then(
                if (presetToolsBackdropBlur > 0.dp) {
                    Modifier.blur(presetToolsBackdropBlur, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                },
            ),
        ) {
            MobileMorePanel(
                visible = morePanelVisible,
                gesturesEnabled = route == MobileRoute.Root,
                user = user,
                characters = charactersState.characters,
                useCoverArtwork = shellState.listCharacterArtwork == com.eleckoi.android.feature.preferences.ListCharacterArtwork.Cover,
                appearance = appearance,
                appUpdateAvailable = appUpdateState.updateAvailable,
                onOpen = { shellViewModel.onIntent(ShellIntent.SetMoreOpen(true)) },
                onClose = { shellViewModel.onIntent(ShellIntent.SetMoreOpen(false)) },
                onOpenProfile = { navigateTo(MobileRoute.Profile) },
                onToggleAllCharactersExpanded = {
                    charactersViewModel.onIntent(CharactersIntent.ToggleAllCharactersExpanded)
                },
                onToggleCharacterGroupExpanded = { group ->
                    charactersViewModel.onIntent(CharactersIntent.ToggleCharacterGroupExpanded(group))
                },
                onOpenCharacter = { characterId ->
                    shellViewModel.onIntent(ShellIntent.SetMoreOpen(false))
                    charactersViewModel.onIntent(CharactersIntent.SelectCharacter(characterId))
                },
                onSaveCharacters = { payload ->
                    charactersViewModel.onIntent(CharactersIntent.SaveCharacterCollection(payload))
                },
                onOpenSettings = {
                    shellViewModel.onIntent(ShellIntent.SetMoreOpen(false))
                    navigateTo(MobileRoute.Settings)
                },
                onOpenUpdate = {
                    shellViewModel.onIntent(ShellIntent.SetMoreOpen(false))
                    navigateTo(MobileRoute.AppUpdate)
                },
            ) {
                NavDisplay(
                    modifier = Modifier.then(
                        if (bottomChromeOpen || route == MobileRoute.Chat) {
                            Modifier
                        } else {
                            Modifier.navigationBarsPadding()
                        },
                    ),
                    backStack = backStack,
                    onBack = ::goBackInsideApp,
                    transitionSpec = { elecKoiForwardRoute() },
                    popTransitionSpec = {
                        elecKoiBackRoute(
                            restoreMorePanel = shouldRestoreMorePanelAtomically(moreOpen),
                        )
                    },
                    predictivePopTransitionSpec = {
                        elecKoiBackRoute(
                            restoreMorePanel = shouldRestoreMorePanelAtomically(moreOpen),
                        )
                    },
                    entryProvider = { key ->
                        mobileShellRouteEntry(key as? MobileRoute, routeContext)
                    },
                )

                MobileShellOverlays(
                    characterImportSourceOpen = characterImportSourceOpen,
                    onCloseCharacterImportSource = { characterImportSourceOpen = false },
                    agentPresetImportSourceOpen = agentPresetImportSourceOpen,
                    onCloseAgentPresetImportSource = { agentPresetImportSourceOpen = false },
                    charactersState = charactersState,
                    agentPresetState = agentPresetState,
                    appearance = appearance,
                    navigationBarColor = systemNavigationBarColor,
                    charactersViewModel = charactersViewModel,
                    agentPresetViewModel = agentPresetViewModel,
                    characterCardActions = characterCardActions,
                    agentPresetDocumentActions = agentPresetDocumentActions,
                )
            }
        }
        if (presetToolsDialogOpen) {
            currentAgentPresetState.value.activePreset?.let { preset ->
                AgentPresetQuickToolsDialog(
                    preset = preset,
                    availableGroups = toolGroupsProvider(preset.toolConfiguration.enabledGroupIds),
                    modelConfigs = modelsState.models?.configs.orEmpty(),
                    appearance = appearance,
                    onUpdate = agentPresetViewModel::update,
                    onManage = {
                        presetToolsDialogOpen = false
                        agentPresetViewModel.openPresetTools(preset.id)
                        navigateTo(MobileRoute.AgentPresets)
                    },
                    onOpenWebSearchSettings = {
                        presetToolsDialogOpen = false
                        navigateTo(MobileRoute.WebSearchSettings)
                    },
                    onSaveModelConfig = modelsViewModel::saveModelConfig,
                    onRefreshModels = modelsViewModel::fetchModelOptions,
                    onDismiss = { presetToolsDialogOpen = false },
                )
            }
        }
    }

    val updateRelease = appUpdateState.latestRelease
    val updateTag = updateRelease?.tagName.orEmpty()
    if (
        shouldShowAppUpdatePrompt(
            remindersEnabled = appUpdateState.remindersEnabled,
            updateAvailable = appUpdateState.updateAvailable,
            latestTag = updateTag,
            dismissedTag = dismissedUpdateTag,
        )
    ) {
        AppUpdatePromptDialog(
            appearance = appearance,
            installedVersion = appUpdateState.installedVersion,
            latestVersion = appUpdateState.latestVersion,
            releaseNotes = updateRelease?.notes.orEmpty(),
            onDismiss = { dismissedUpdateTag = updateTag },
            onOpenUpdate = {
                dismissedUpdateTag = updateTag
                navigateTo(MobileRoute.AppUpdate)
            },
        )
    }
}

internal fun shouldShowMorePanel(
    moreOpen: Boolean,
    route: MobileRoute,
): Boolean = moreOpen && route == MobileRoute.Root

internal fun shouldRestoreMorePanelAtomically(moreOpen: Boolean): Boolean = moreOpen
