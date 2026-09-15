package com.eleckoi.android.app.shell

import com.eleckoi.android.feature.characters.ui.components.AvatarSlotsPage
import com.eleckoi.android.foundation.design.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.eleckoi.android.feature.characters.ui.CharactersIntent
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryIntent
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigIntent
import com.eleckoi.android.feature.characters.ui.settings.CharacterSettingsPage
import com.eleckoi.android.feature.chat.ui.screen.ChatScreen
import com.eleckoi.android.app.navigation.MobileRoute

internal fun mobileCoreRouteEntry(
    currentRoute: MobileRoute,
    context: MobileShellRouteContext,
): NavEntry<NavKey>? = with(context) {
    when (currentRoute) {
        MobileRoute.Root -> NavEntry(currentRoute) {
            MobileRootTabs(
                shell = currentShellState.value,
                characters = currentCharactersState.value.characters,
                models = currentModelsState.value.models,
                user = currentProfileState.value.user,
                appearance = currentThemeState.value.appearance,
                shellViewModel = shellViewModel,
                charactersViewModel = charactersViewModel,
                modelsViewModel = modelsViewModel,
                chatViewModel = chatViewModel,
                presetEditorOpen = currentAgentPresetState.value.editorPreset != null,
                presetPage = {
                    AgentPresetPageContent(
                        showRootBackButton = false,
                        onOpenSidebar = {
                            shellViewModel.onIntent(ShellIntent.SetMoreOpen(true))
                        },
                    )
                },
                activeCharacter = activeCharacter,
                onImportCharacterCard = onOpenCharacterImportSource,
                onNavigate = navigateTo,
                rootSearchOpen = rootSearchOpen.value,
                onRootSearchOpenChange = onRootSearchOpenChange,
                onChangeBottomTab = selectBottomTab,
            )
        }
        MobileRoute.Chat -> NavEntry(currentRoute) {
                ChatScreen(
                    viewModel = chatViewModel,
                    onBack = goBackInsideApp,
                    onOpenTools = onOpenPresetToolsDialog,
                    onOpenPresets = {
                        agentPresetViewModel.closeEditor()
                        navigateTo(MobileRoute.AgentPresets)
                    },
                    dynamicSettingsSessionIds = currentSettingLibraryState.value.conversationLibraries
                        .mapTo(mutableSetOf()) { it.sessionId },
                    onOpenDynamicSettings = { characterId, sessionId ->
                        settingLibraryViewModel.onIntent(
                            SettingLibraryIntent.LoadConversationLibraries(characterId),
                        )
                        navigateTo(MobileRoute.DynamicSettings(characterId, sessionId))
                    },
                    onOpenUserAvatars = {
                        navigateTo(MobileRoute.UserAvatars)
                    },
                    onOpenCharacterSettings = { characterId ->
                        navigateTo(MobileRoute.CharacterSettings(characterId))
                    },
                    newCharacterBackground = currentThemeState.value.newCharacterBackground,
                    onNewCharacterBackgroundChange = { background ->
                        themeViewModel.onIntent(
                            com.eleckoi.android.feature.settings.ui.personalization.theme.ThemeIntent
                                .SaveNewCharacterBackground(background),
                        )
                    },
                )
        }
        is MobileRoute.CharacterAvatars -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val character = activeCharacter(currentRoute.characterId)
                if (character == null) {
                    LaunchedEffect(currentRoute.characterId) { goBackInsideApp() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(pageAppearance.mobileBg),
                    )
                } else {
                    AvatarSlotsPage(
                        avatars = character.persona.assistantAvatars,
                        displayName = character.name,
                        cachePrefix = "character",
                        appearance = pageAppearance,
                        onBack = goBackInsideApp,
                        onSave = { files ->
                            charactersViewModel.onIntent(
                                CharactersIntent.SaveCharacterAvatars(
                                    currentRoute.characterId,
                                    files,
                                ),
                            )
                        },
                        onClear = { slot ->
                            charactersViewModel.onIntent(
                                CharactersIntent.ClearCharacterAvatarSlot(
                                    currentRoute.characterId,
                                    slot,
                                ),
                            )
                        },
                    )
                }
        }
        is MobileRoute.CharacterSettings -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val pageCharacterSaving = currentCharactersState.value.saving
                CharacterSettingsPage(
                    character = activeCharacter(currentRoute.characterId),
                    appearance = pageAppearance,
                    saving = pageCharacterSaving,
                    onBack = goBackInsideApp,
                    onSavePersona = { persona, onResult ->
                        charactersViewModel.saveCharacterPersona(
                            characterId = currentRoute.characterId,
                            persona = persona,
                            onResult = onResult,
                        )
                    },
                    onSaveAvatars = { files ->
                        charactersViewModel.onIntent(
                            CharactersIntent.SaveCharacterAvatars(
                                currentRoute.characterId,
                                files,
                            ),
                        )
                    },
                    onClearAvatar = { slot ->
                        charactersViewModel.onIntent(
                            CharactersIntent.ClearCharacterAvatarSlot(
                                currentRoute.characterId,
                                slot,
                            ),
                        )
                    },
                    onSendMessage = { persona ->
                        charactersViewModel.saveCharacterPersona(
                            characterId = currentRoute.characterId,
                            persona = persona,
                            onResult = { result ->
                                result.onSuccess {
                                    chatViewModel.openCharacterChat(currentRoute.characterId)
                                    replaceTop(MobileRoute.Chat)
                                }
                            },
                        )
                    },
                    onOpenAiCreationAssistant = {
                        navigateTo(MobileRoute.AiCreationAssistant)
                    },
                    onOpenSettingLibrary = {
                        settingLibraryViewModel.onIntent(SettingLibraryIntent.Load(currentRoute.characterId))
                        navigateTo(MobileRoute.SettingLibrary(currentRoute.characterId))
                    },
                    onOpenDynamicSettings = {
                        settingLibraryViewModel.onIntent(SettingLibraryIntent.LoadConversationLibraries(currentRoute.characterId))
                        navigateTo(MobileRoute.DynamicSettings(currentRoute.characterId))
                    },
                    onOpenVariableConfig = {
                        variableConfigViewModel.onIntent(VariableConfigIntent.Load(currentRoute.characterId))
                        navigateTo(MobileRoute.VariableConfig(currentRoute.characterId))
                    },
                    onOpenRegexRules = {
                        regexRulesViewModel.load(currentRoute.characterId)
                        navigateTo(MobileRoute.RegexRules(currentRoute.characterId))
                    },
                    onOpenFrontendBeauty = {
                        variableConfigViewModel.onIntent(VariableConfigIntent.Load(currentRoute.characterId))
                        navigateTo(MobileRoute.FrontendBeauty(currentRoute.characterId))
                    },
                    onExport = {
                        charactersViewModel.onIntent(
                            CharactersIntent.PrepareCharacterExport(currentRoute.characterId),
                        )
                    },
                    onDelete = {
                        charactersViewModel.onIntent(CharactersIntent.DeleteCharacters(listOf(currentRoute.characterId)))
                    },
                )
        }
        is MobileRoute.CharacterDraft -> NavEntry(currentRoute) {
                val pageAppearance = currentThemeState.value.appearance
                val draft = currentCharactersState.value.characterDraft
                    ?.takeIf { it.id == currentRoute.characterId }
                if (draft == null) {
                    LaunchedEffect(currentRoute.characterId) { goBackInsideApp() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(pageAppearance.mobileBg),
                    )
                } else {
                    CharacterSettingsPage(
                        character = draft,
                        appearance = pageAppearance,
                        saving = currentCharactersState.value.saving,
                        isDraft = true,
                        onBack = {
                            charactersViewModel.discardCharacterDraft(currentRoute.characterId)
                            goBackInsideApp()
                        },
                        onSavePersona = { _, onResult ->
                            onResult(Result.failure(IllegalStateException("角色草稿尚未创建")))
                        },
                        onCreateCharacter = { persona, avatarFiles, onResult ->
                            charactersViewModel.commitCharacterDraft(
                                persona = persona,
                                avatarFiles = avatarFiles,
                                onResult = onResult,
                            )
                        },
                        onCharacterCreated = { characterId ->
                            replaceTop(MobileRoute.CharacterSettings(characterId))
                        },
                        onSaveAvatars = {},
                        onClearAvatar = {},
                        onSendMessage = {},
                        onOpenAiCreationAssistant = {
                            navigateTo(MobileRoute.AiCreationAssistant)
                        },
                        onOpenSettingLibrary = {
                            settingLibraryViewModel.onIntent(SettingLibraryIntent.Load(currentRoute.characterId))
                            navigateTo(MobileRoute.SettingLibrary(currentRoute.characterId))
                        },
                        onOpenDynamicSettings = {
                            settingLibraryViewModel.onIntent(
                                SettingLibraryIntent.LoadConversationLibraries(currentRoute.characterId),
                            )
                            navigateTo(MobileRoute.DynamicSettings(currentRoute.characterId))
                        },
                        onOpenVariableConfig = {
                            variableConfigViewModel.onIntent(VariableConfigIntent.Load(currentRoute.characterId))
                            navigateTo(MobileRoute.VariableConfig(currentRoute.characterId))
                        },
                        onOpenRegexRules = {
                            regexRulesViewModel.load(currentRoute.characterId)
                            navigateTo(MobileRoute.RegexRules(currentRoute.characterId))
                        },
                        onOpenFrontendBeauty = {
                            variableConfigViewModel.onIntent(VariableConfigIntent.Load(currentRoute.characterId))
                            navigateTo(MobileRoute.FrontendBeauty(currentRoute.characterId))
                        },
                        onExport = {},
                        onDelete = {},
                    )
                }
        }
        else -> null
    }
}
