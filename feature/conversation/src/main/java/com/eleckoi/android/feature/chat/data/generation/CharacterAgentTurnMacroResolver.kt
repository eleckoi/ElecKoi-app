package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary

internal fun VariableConfig.resolveCharacterCardMacros(
    values: CharacterCardMacroValues,
): VariableConfig = copy(
    objects = objects.map { item ->
        item.copy(
            description = item.description.resolveCharacterCardMacros(values),
            updateRule = item.updateRule.resolveCharacterCardMacros(values),
        )
    },
    variables = variables.map { item ->
        item.copy(
            description = item.description.resolveCharacterCardMacros(values),
            updateRule = item.updateRule.resolveCharacterCardMacros(values),
        )
    },
)

internal fun SettingLibrary.resolveCharacterCardMacros(
    values: CharacterCardMacroValues,
): SettingLibrary = copy(
    entries = entries.map { entry ->
        entry.copy(
            content = entry.content.resolveCharacterCardMacros(values),
            agentSelectionHint = entry.agentSelectionHint.resolveCharacterCardMacros(values),
            openingMessages = entry.openingMessages.map { opening ->
                opening.copy(content = opening.content.resolveCharacterCardMacros(values))
            },
        )
    },
)

internal fun SettingLibraryAgentTurnContext.resolveCharacterCardMacros(
    values: CharacterCardMacroValues,
): SettingLibraryAgentTurnContext = copy(
    automaticLibrary = automaticLibrary.resolveCharacterCardMacros(values),
    readableEntries = readableEntries.map { entry ->
        entry.copy(
            content = entry.content.resolveCharacterCardMacros(values),
            selectionHint = entry.selectionHint.resolveCharacterCardMacros(values),
        )
    },
)
