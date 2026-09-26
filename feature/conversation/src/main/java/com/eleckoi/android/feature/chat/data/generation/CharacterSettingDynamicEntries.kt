package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateMessage
import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateRenderResult
import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateSource
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryResolvedReference
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryContentMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole

internal fun promptRegexedHistory(
    messages: List<ChatMessage>,
    rulesFor: (RegexRuleTarget) -> List<RegexRule>,
): List<ChatMessage> = messages.map { message ->
    val target = if (message.role == MessageRole.User) RegexRuleTarget.UserInput else RegexRuleTarget.AiOutput
    message.copy(
        content = RegexRuleProcessor.transform(
            text = message.content,
            rules = rulesFor(target),
            target = target,
        ),
    )
}

internal suspend fun SettingLibraryAgentTurnContext.resolveDynamicEntries(
    messages: List<ChatMessage>,
    keywordMessages: List<ChatMessage>,
    stateJson: String,
    runtime: VariableRuntimeService,
): SettingLibraryAgentTurnContext {
    val keywordResolved = withKeywordPromotions(keywordMessages)
    val visibleEntries = keywordResolved.readableEntries
        .filter { it.dynamicMode != SettingLibraryDynamicMode.EjsReference }
    val agentTargets = visibleEntries.ejsRenderTargets()
    val ejsTargetIds = agentTargets.map(SettingLibraryAgentEntry::id).toSet()
    val rendered = runtime.renderEjsTemplates(
        stateJson = stateJson,
        messages = messages.map { message ->
            EjsTemplateMessage(
                id = message.id,
                role = message.role.name.lowercase(),
                content = message.content,
            )
        },
        sources = ejsTemplateSources(
            targets = agentTargets.map { target ->
                EjsTemplateSource(target.id, target.id, target.title, target.path, target.content)
            },
            references = keywordResolved.referenceEntries,
        ),
        targetIds = ejsTargetIds,
    )
    return keywordResolved.copy(readableEntries = visibleEntries).withRenderedEjsResults(rendered)
}

internal fun SettingLibraryAgentTurnContext.withRenderedEjsResults(
    rendered: Map<String, EjsTemplateRenderResult>,
): SettingLibraryAgentTurnContext = copy(
    readableEntries = readableEntries.mapNotNull { entry ->
        val renderResult = rendered[entry.id]
        val conditionalEjs = entry.contentMode == SettingLibraryContentMode.Ejs &&
            entry.readStrategy != SettingLibraryAgentReadStrategy.Required
        if (conditionalEjs && renderResult == null) return@mapNotNull null
        val content = renderResult?.content ?: entry.content
        entry.copy(
            content = content,
            promotedToRequiredThisTurn = entry.promotedToRequiredThisTurn ||
                (conditionalEjs && content.isNotBlank()),
            resolvedReferences = renderResult?.references.orEmpty().map { reference ->
                SettingLibraryResolvedReference(
                    id = reference.id,
                    title = reference.title,
                    path = reference.path,
                )
            },
        ).takeIf { content.isNotBlank() }
    },
)

internal fun List<SettingLibraryAgentEntry>.ejsRenderTargets(): List<SettingLibraryAgentEntry> = filter { entry ->
    entry.dynamicMode == SettingLibraryDynamicMode.Standard &&
        entry.readStrategy != SettingLibraryAgentReadStrategy.Required &&
        entry.contentMode == SettingLibraryContentMode.Ejs
}

internal fun ejsTemplateSources(
    targets: List<EjsTemplateSource>,
    references: List<SettingLibraryAgentEntry>,
): List<EjsTemplateSource> = targets.flatMap { target ->
    listOf(target) + references.map { reference ->
        EjsTemplateSource(
            id = reference.id,
            controllerId = target.id,
            title = reference.title,
            path = reference.path,
            content = reference.content,
            enabled = reference.enabled,
            renderEjs = false,
        )
    }
}

internal fun SettingLibraryAgentTurnContext.withKeywordPromotions(
    messages: List<ChatMessage>,
): SettingLibraryAgentTurnContext {
    val matchingIds = CharacterSettingContextResolver.run {
        matchingKeywordEntryIds(keywordStrategyEntries, messages)
    }
    return copy(
        readableEntries = readableEntries
            .filter { entry ->
                entry.readStrategy != SettingLibraryAgentReadStrategy.Keyword || entry.id in matchingIds
            }
            .map { entry: SettingLibraryAgentEntry ->
                if (entry.id in matchingIds) {
                    entry.copy(promotedToRequiredThisTurn = true)
                } else {
                    entry
                }
            },
    )
}
