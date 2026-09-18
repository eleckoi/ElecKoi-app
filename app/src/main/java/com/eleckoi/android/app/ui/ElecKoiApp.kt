package com.eleckoi.android.app.ui

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eleckoi.android.app.ElecKoiApplication
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.eleckoi.android.feature.characters.ui.CharactersViewModel
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryViewModel
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetViewModel
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigViewModel
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRulesViewModel
import com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.FrontendBeautyViewModel
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantViewModel
import com.eleckoi.android.feature.chat.ui.ChatViewModel
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryReader
import com.eleckoi.android.feature.chat.ui.ChatRenderingPreferences
import com.eleckoi.android.feature.chat.ui.LocalChatRenderingPreferences
import com.eleckoi.android.feature.modelconfig.ui.ModelsViewModel
import com.eleckoi.android.app.shell.MobileShell
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileViewModel
import com.eleckoi.android.feature.appfont.ui.ProvideAppFont
import com.eleckoi.android.app.shell.ShellViewModel
import com.eleckoi.android.feature.settings.ui.personalization.theme.ThemeViewModel
import com.eleckoi.android.feature.settings.ui.personalization.chat.ChatDisplaySettingsViewModel
import com.eleckoi.android.feature.settings.ui.runtime.LocalRuntimeSettingsViewModel
import com.eleckoi.android.feature.settings.ui.websearch.WebSearchSettingsViewModel
import com.eleckoi.android.feature.settings.ui.remotedsh.RemoteDshSettingsViewModel
import com.eleckoi.android.feature.chat.data.markdown.preloadNativeMarkdownRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.app.update.AppUpdateViewModel
import com.eleckoi.android.app.update.installedVersionName
import com.eleckoi.android.foundation.design.ElecKoiTheme
import com.eleckoi.android.foundation.design.withDarkAppearance

@Composable
fun ElecKoiApp() {
    val context = LocalContext.current.applicationContext
    val application = context as ElecKoiApplication
    val container = remember(application) { application.container }
    val repository = container.repository
    val agentBackgroundProtectionEnabled by
        container.agentBackgroundProtection.enabled.collectAsStateWithLifecycle()
    val uiPreferences by repository.uiPreferencesFlow.collectAsStateWithLifecycle(
        initialValue = UiPreferences(),
    )
    val darkAppearance = uiPreferences.appearanceMode.resolvesDark(isSystemInDarkTheme())
    val initialAppearance = uiPreferences.appearanceTheme.withDarkAppearance(darkAppearance)
    val dshTrajectoryReader = remember(context, container) {
        DshTrajectoryReader(context, container::inspectDshSession)
    }
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            chatService = repository,
            frontendProjectService = repository,
            initialAppearance = initialAppearance,
            isSettingLibraryToolEnabled = container::isCharacterSettingLibraryToolEnabled,
            enableSettingLibraryTool = container::enableCharacterSettingLibraryTool,
            readDshTrajectory = dshTrajectoryReader::read,
        ),
    )
    val shellViewModel: ShellViewModel = viewModel(
        factory = ShellViewModel.factory(repository),
    )
    val charactersViewModel: CharactersViewModel = viewModel(
        factory = CharactersViewModel.factory(repository, repository),
    )
    val settingLibraryViewModel: SettingLibraryViewModel = viewModel(
        factory = SettingLibraryViewModel.factory(repository),
    )
    val agentPresetViewModel: AgentPresetViewModel = viewModel(
        factory = AgentPresetViewModel.factory(repository.agentPresetRepository),
    )
    val variableConfigViewModel: VariableConfigViewModel = viewModel(
        factory = VariableConfigViewModel.factory(repository),
    )
    val regexRulesViewModel: RegexRulesViewModel = viewModel(
        factory = RegexRulesViewModel.factory(repository),
    )
    val frontendBeautyViewModel: FrontendBeautyViewModel = viewModel(
        factory = FrontendBeautyViewModel.factory(repository),
    )
    val aiCreationAssistantViewModel: AiCreationAssistantViewModel = viewModel(
        factory = AiCreationAssistantViewModel.factory(
            creatorService = repository,
            frontendProjectService = repository,
            modelService = repository,
            agentSessionFactory = container.agentSessions,
            agentRuns = container.agentRuns,
            localRuntime = container.localRuntime,
            toolGroupsProvider = container::agentToolGroups,
            loadEnabledToolGroupIds = container::creatorAssistantEnabledToolGroupIds,
            saveEnabledToolGroupIds = container::setCreatorAssistantEnabledToolGroupIds,
            loadImageModelConfigId = container::creatorAssistantImageModelConfigId,
            saveImageModelConfigId = container::setCreatorAssistantImageModelConfigId,
        ),
    )
    val localRuntimeSettingsViewModel: LocalRuntimeSettingsViewModel = viewModel(
        factory = LocalRuntimeSettingsViewModel.factory(
            runtime = container.localRuntime,
        ),
    )
    val appUpdateViewModel: AppUpdateViewModel = viewModel(
        factory = AppUpdateViewModel.factory(
            repository = application.appUpdateRepository,
            installedVersion = context.installedVersionName(),
            scheduler = application.appUpdateScheduler,
        ),
    )
    val webSearchSettingsViewModel: WebSearchSettingsViewModel = viewModel(
        factory = WebSearchSettingsViewModel.factory(
            repository = container.webSearchSettingsRepository,
            apiClient = container.tavilyApiClient,
        ),
    )
    val remoteDshSettingsViewModel: RemoteDshSettingsViewModel = viewModel(
        factory = RemoteDshSettingsViewModel.factory(
            repository = container.remoteDshSettingsRepository,
            plugin = container.remoteDshPlugin,
        ),
    )
    val modelsViewModel: ModelsViewModel = viewModel(
        factory = ModelsViewModel.factory(repository),
    )
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(repository),
    )
    val themeViewModel: ThemeViewModel = viewModel(
        factory = ThemeViewModel.factory(repository, initialAppearance),
    )
    val chatDisplaySettingsViewModel: ChatDisplaySettingsViewModel = viewModel(
        factory = ChatDisplaySettingsViewModel.factory(repository.uiPreferencesRepository),
    )
    LaunchedEffect(chatViewModel) {
        chatViewModel.loadInitialDraft()
    }

    LaunchedEffect(Unit) {
        // Keep native library loading out of both the application launch frame and the first
        // conversation-navigation frame. The loader itself is process-wide and thread-safe.
        withFrameNanos { }
        withContext(Dispatchers.IO) {
            preloadNativeMarkdownRuntime()
        }
    }

    ElecKoiTheme(darkTheme = darkAppearance) {
        CompositionLocalProvider(
            LocalOverscrollFactory provides null,
            LocalChatRenderingPreferences provides ChatRenderingPreferences(
                reasoningDisplayMode = uiPreferences.chatReasoningDisplayMode,
                toolTimelineStyle = uiPreferences.chatToolTimelineStyle,
                codeBlockStyle = uiPreferences.chatCodeBlockStyle,
                codeBlockWrapEnabled = uiPreferences.chatCodeBlockWrapEnabled,
                codeBlockShowAllEnabled = uiPreferences.chatCodeBlockShowAllEnabled,
                timelineThinkingAnimation = uiPreferences.chatTimelineThinkingAnimation,
            ),
        ) {
            ProvideAppFont {
                MobileShell(
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
                remoteDshPlugin = container.remoteDshPlugin,
                modelsViewModel = modelsViewModel,
                profileViewModel = profileViewModel,
                themeViewModel = themeViewModel,
                chatDisplaySettingsViewModel = chatDisplaySettingsViewModel,
                chatViewModel = chatViewModel,
                dataBackupService = container.dataBackupService,
                toolContextSnapshotProvider = container::agentToolContextSnapshot,
                toolGroupsProvider = container::agentToolGroups,
                agentBackgroundProtectionEnabled = agentBackgroundProtectionEnabled,
                onAgentBackgroundProtectionEnabledChange =
                    container.agentBackgroundProtection::setEnabled,
                onAgentBackgroundProtectionPermissionChanged =
                    container.agentBackgroundProtection::refreshPermission,
                )
            }
        }
    }
}
