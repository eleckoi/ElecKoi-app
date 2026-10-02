package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.creator.plugins.PluginPromptPipeline
import com.eleckoi.android.engine.creator.plugins.projectPluginMessages
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.*
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.chat.model.*
import kotlinx.serialization.json.*

/** Supporting model calls share native preset, regex, macro and world-info preparation. */
suspend fun composePluginContext(
    session: ChatSession,
    input: JsonArray,
    preset: AgentPreset,
    libraries: SettingLibraryRepository,
    regex: RegexRuleRepository,
    variableConfig: VariableConfigRepository,
    variableRuntime: VariableRuntimeService,
): JsonArray {
    val values = CharacterCardMacroValues(session.characterPersona.userName.ifBlank { "用户" }, session.characterName.ifBlank { "AI" })
    val history = session.messages.map { it.copy(content = it.content.resolveCharacterCardMacros(values)) }
    val rules = regex.load(session.characterId)
    val promptHistory = promptRegexedHistory(history) { regex.rulesFor(rules, it, RegexRuleSurface.Prompt) }
    val scan = PluginPromptPipeline.scanText(session.id)
    val supplied = input.map { ChatMessage("plugin-input", MessageRole.entries.first { role -> role.name.equals(it.jsonObject.getValue("role").jsonPrimitive.content, true) }, it.jsonObject.getValue("content").jsonPrimitive.content) }
    val resolved = libraries.loadAgentTurnContext(session.characterId, session.id, preset.asRuntimeSettingLibrary())
        .resolveCharacterCardMacros(values)
        .resolveDynamicEntries(history + supplied, promptHistory + supplied + if (scan.isEmpty()) emptyList() else listOf(ChatMessage("plugin-scan", MessageRole.System, scan)),
            session.variableStateJson.ifBlank { variableConfig.load(session.characterId).initialStateJson }, variableRuntime)
    val settingRules = regex.rulesFor(rules, RegexRuleTarget.SettingContent, RegexRuleSurface.Prompt)
    val required = RequiredSettingLibraryCache(resolved.readableEntries) {
        RegexRuleProcessor.transform(it, settingRules, RegexRuleTarget.SettingContent)
    }
    val native = CharacterSettingContextResolver.resolve(resolved.automaticLibrary, history + supplied, requiredCache = required).map { injection ->
        if (required.ownsInjection(injection.id)) injection else injection.copy(content = RegexRuleProcessor.transform(injection.content, settingRules, RegexRuleTarget.SettingContent))
    }
    val messages = JsonArray(promptHistory.map { buildJsonObject { put("role", it.role.name.lowercase()); put("content", it.content) } } + input)
    return projectPluginMessages(messages, native + PluginPromptPipeline.snapshot(session.id))
}
