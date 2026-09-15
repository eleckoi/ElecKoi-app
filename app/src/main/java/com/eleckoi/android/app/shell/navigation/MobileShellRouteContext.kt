package com.eleckoi.android.app.shell

import androidx.compose.runtime.State
import com.eleckoi.android.engine.agent.remotedsh.RemoteDshPlugin
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.FrontendBeautyViewModel
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetUiState
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetViewModel
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRulesUiState
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRulesViewModel
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryUiState
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryViewModel
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigUiState
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigViewModel
import com.eleckoi.android.feature.characters.ui.CharactersUiState
import com.eleckoi.android.feature.characters.ui.CharactersViewModel
import com.eleckoi.android.feature.chat.ui.ChatUiState
import com.eleckoi.android.feature.chat.ui.ChatViewModel
import com.eleckoi.android.feature.modelconfig.ui.ModelsUiState
import com.eleckoi.android.feature.modelconfig.ui.ModelsViewModel
import com.eleckoi.android.feature.settings.ui.personalization.chat.ChatDisplaySettingsViewModel
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileUiState
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileViewModel
import com.eleckoi.android.feature.settings.ui.personalization.theme.ThemeUiState
import com.eleckoi.android.feature.settings.ui.personalization.theme.ThemeViewModel
import com.eleckoi.android.feature.settings.ui.remotedsh.RemoteDshSettingsViewModel
import com.eleckoi.android.feature.settings.ui.runtime.LocalRuntimeSettingsViewModel
import com.eleckoi.android.feature.settings.ui.websearch.WebSearchSettingsViewModel
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantUiState
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantViewModel
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.foundation.design.components.BottomTab
import com.eleckoi.android.app.shell.DataBackupActions
import com.eleckoi.android.app.update.AppUpdateUiState
import com.eleckoi.android.app.update.AppUpdateViewModel

/** Dependencies shared by the shell route families. */
internal class MobileShellRouteContext(
    val currentShellState: State<ShellUiState>,
    val currentCharactersState: State<CharactersUiState>,
    val currentSettingLibraryState: State<SettingLibraryUiState>,
    val currentVariableConfigState: State<VariableConfigUiState>,
    val currentRegexRulesState: State<RegexRulesUiState>,
    val currentAiCreationAssistantState: State<AiCreationAssistantUiState>,
    val currentModelsState: State<ModelsUiState>,
    val currentProfileState: State<ProfileUiState>,
    val currentThemeState: State<ThemeUiState>,
    val currentAppUpdateState: State<AppUpdateUiState>,
    val currentAgentBackgroundProtectionEnabled: State<Boolean>,
    val currentOnAgentBackgroundProtectionEnabledChange: State<(Boolean) -> Unit>,
    val currentOnAgentBackgroundProtectionPermissionChanged: State<() -> Unit>,
    val currentAgentPresetState: State<AgentPresetUiState>,
    val chatState: ChatUiState,
    val appearance: AppearanceTheme,
    val user: UserProfile,
    val shellViewModel: ShellViewModel,
    val charactersViewModel: CharactersViewModel,
    val settingLibraryViewModel: SettingLibraryViewModel,
    val agentPresetViewModel: AgentPresetViewModel,
    val variableConfigViewModel: VariableConfigViewModel,
    val regexRulesViewModel: RegexRulesViewModel,
    val frontendBeautyViewModel: FrontendBeautyViewModel,
    val aiCreationAssistantViewModel: AiCreationAssistantViewModel,
    val localRuntimeSettingsViewModel: LocalRuntimeSettingsViewModel,
    val appUpdateViewModel: AppUpdateViewModel,
    val webSearchSettingsViewModel: WebSearchSettingsViewModel,
    val remoteDshSettingsViewModel: RemoteDshSettingsViewModel,
    val remoteDshPlugin: RemoteDshPlugin,
    val modelsViewModel: ModelsViewModel,
    val profileViewModel: ProfileViewModel,
    val themeViewModel: ThemeViewModel,
    val chatDisplaySettingsViewModel: ChatDisplaySettingsViewModel,
    val chatViewModel: ChatViewModel,
    val toolContextSnapshotProvider: (Set<String>) -> AgentToolContextSnapshot,
    val toolGroupsProvider: (Set<String>) -> List<AgentToolGroupSnapshot>,
    val documentActions: ShellDocumentActions,
    val dataBackupActions: DataBackupActions,
    val activeCharacter: (String) -> CharacterSlot?,
    val navigateTo: (com.eleckoi.android.app.navigation.MobileRoute) -> Unit,
    val replaceTop: (com.eleckoi.android.app.navigation.MobileRoute) -> Unit,
    val selectBottomTab: (BottomTab) -> Unit,
    val goBackInsideApp: () -> Unit,
    val closeRoute: () -> Unit,
    val rootSearchOpen: State<Boolean>,
    val onRootSearchOpenChange: (Boolean) -> Unit,
    val onOpenCharacterImportSource: () -> Unit,
    val onOpenAgentPresetImportSource: () -> Unit,
    val onOpenPresetToolsDialog: () -> Unit,
)
