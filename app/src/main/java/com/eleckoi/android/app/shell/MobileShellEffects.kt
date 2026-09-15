package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.eleckoi.android.feature.characters.ui.CharactersEffect
import com.eleckoi.android.feature.characters.ui.CharactersViewModel
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryEffect
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryIntent
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryViewModel
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetViewModel
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigEffect
import com.eleckoi.android.feature.characters.modes.story.variables.ui.VariableConfigViewModel
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRulesEffect
import com.eleckoi.android.feature.characters.modes.story.regex.ui.RegexRulesViewModel
import com.eleckoi.android.feature.chat.ui.ChatViewModel
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileEffect
import com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileViewModel
import com.eleckoi.android.app.navigation.MobileRoute

@Composable
internal fun MobileShellEffects(
    route: MobileRoute,
    chatState: com.eleckoi.android.feature.chat.ui.ChatUiState,
    appearance: com.eleckoi.android.foundation.design.AppearanceTheme,
    charactersState: com.eleckoi.android.feature.characters.ui.CharactersUiState,
    profileState: com.eleckoi.android.feature.settings.ui.personalization.profile.ProfileUiState,
    settingLibraryState: com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui.SettingLibraryUiState,
    agentPresetState: com.eleckoi.android.feature.characters.presets.ui.AgentPresetUiState,
    shellViewModel: ShellViewModel,
    charactersViewModel: CharactersViewModel,
    settingLibraryViewModel: SettingLibraryViewModel,
    variableConfigViewModel: VariableConfigViewModel,
    agentPresetViewModel: AgentPresetViewModel,
    regexRulesViewModel: RegexRulesViewModel,
    profileViewModel: ProfileViewModel,
    chatViewModel: ChatViewModel,
    documentActions: ShellDocumentActions,
    latestRoute: androidx.compose.runtime.State<MobileRoute>,
    navigateTo: (MobileRoute) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(
        route,
        chatState.draft?.session?.id,
        chatState.draft?.session?.characterId,
        chatState.isSending,
    ) {
        val session = chatState.draft?.session
        if (
            route == MobileRoute.Chat &&
            session != null &&
            !chatState.isSending
        ) {
            settingLibraryViewModel.onIntent(
                SettingLibraryIntent.LoadConversationLibraries(session.characterId),
            )
        }
    }

    LaunchedEffect(appearance) {
        chatViewModel.applyAppearanceTheme(appearance)
    }

    LaunchedEffect(charactersState.errorMessage) {
        charactersState.errorMessage.takeIf(String::isNotBlank)?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(profileState.errorMessage) {
        profileState.errorMessage.takeIf(String::isNotBlank)?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(charactersViewModel) {
        charactersViewModel.effects.collect { effect ->
            when (effect) {
                is CharactersEffect.OpenCharacterSettings -> {
                    shellViewModel.onIntent(ShellIntent.ChangeTab(RootTab.Characters))
                    navigateTo(MobileRoute.CharacterSettings(effect.characterId))
                }
                is CharactersEffect.OpenCharacterDraft -> {
                    shellViewModel.onIntent(ShellIntent.ChangeTab(RootTab.Characters))
                    navigateTo(MobileRoute.CharacterDraft(effect.characterId))
                }
                is CharactersEffect.CharactersDeleted -> {
                    val current = latestRoute.value
                    val currentCharacterId = when (current) {
                        is MobileRoute.CharacterSettings -> current.characterId
                        is MobileRoute.CharacterDraft -> current.characterId
                        is MobileRoute.CharacterAvatars -> current.characterId
                        is MobileRoute.SettingLibrary -> current.characterId
                        is MobileRoute.DynamicSettings -> current.characterId
                        is MobileRoute.VariableConfig -> current.characterId
                        is MobileRoute.RegexRules -> current.characterId
                        is MobileRoute.FrontendBeauty -> current.characterId
                        else -> ""
                    }
                    if (currentCharacterId in effect.characterIds) {
                        navigateTo(MobileRoute.Root)
                    }
                }
                CharactersEffect.CharacterCreated -> {
                    Toast.makeText(context, "角色创建成功", Toast.LENGTH_SHORT).show()
                    chatViewModel.refreshCurrentDraft()
                }
                CharactersEffect.CharactersChanged -> chatViewModel.refreshCurrentDraft()
                is CharactersEffect.CharactersImported -> {
                    shellViewModel.onIntent(ShellIntent.ChangeTab(RootTab.Characters))
                    if (effect.characterIds.size == 1) {
                        navigateTo(MobileRoute.CharacterSettings(effect.characterIds.single()))
                    } else {
                        Toast.makeText(
                            context,
                            "已导入 ${effect.characterIds.size} 个角色",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                is CharactersEffect.ExportReady -> {
                    documentActions.exportCharacters(effect.json)
                }
            }
        }
    }

    LaunchedEffect(settingLibraryViewModel) {
        settingLibraryViewModel.effects.collect { effect ->
            when (effect) {
                is SettingLibraryEffect.ExportReady -> {
                    documentActions.exportSettingLibrary(effect.fileName, effect.json)
                }
                is SettingLibraryEffect.ConversationVersionSaved -> {
                    Toast.makeText(context, "已保存为设定版本：${effect.name}", Toast.LENGTH_SHORT).show()
                }
                SettingLibraryEffect.ConversationEntrySaved -> {
                    Toast.makeText(context, "动态设定已保存", Toast.LENGTH_SHORT).show()
                }
                is SettingLibraryEffect.ConversationLibraryChanged -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                SettingLibraryEffect.ConversationEntryDeleted -> {
                    Toast.makeText(context, "设定已从当前对话删除", Toast.LENGTH_SHORT).show()
                }
                SettingLibraryEffect.ConversationGroupDeleted -> {
                    Toast.makeText(context, "文件夹已从当前对话删除", Toast.LENGTH_SHORT).show()
                }
                SettingLibraryEffect.ConversationSettingsDeleted -> {
                    Toast.makeText(context, "已回归母设定", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(settingLibraryState.errorMessage) {
        settingLibraryState.errorMessage.takeIf(String::isNotBlank)?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(variableConfigViewModel) {
        variableConfigViewModel.effects.collect { effect ->
            when (effect) {
                is VariableConfigEffect.ExportReady -> {
                    documentActions.exportVariableConfig(effect.fileName, effect.json)
                }
            }
        }
    }

    LaunchedEffect(agentPresetState.importMessage) {
        agentPresetState.importMessage.takeIf(String::isNotBlank)?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            agentPresetViewModel.importMessageShown()
        }
    }

    LaunchedEffect(regexRulesViewModel) {
        regexRulesViewModel.effects.collect { effect ->
            when (effect) {
                is RegexRulesEffect.RulesExportReady -> {
                    documentActions.exportRegexRules(effect.fileName, effect.json)
                }
                is RegexRulesEffect.RulesImported -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(profileViewModel) {
        profileViewModel.effects.collect { effect ->
            when (effect) {
                // 只负责让依赖用户资料的地方跟着更新。以前这里还顺手退一层，那是"保存即离开"留下
                // 的；改成自动保存之后，裁头像点一下完成就会把整个资料页弹掉。
                ProfileEffect.Saved -> chatViewModel.refreshCurrentDraft()
            }
        }
    }
}
