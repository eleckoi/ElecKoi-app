package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateMessage
import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateSource
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryResolvedReference
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.chat.model.ChatMessage

internal suspend fun SettingLibraryAgentTurnContext.resolveDynamicEntries(
    messages: List<ChatMessage>,
    stateJson: String,
    runtime: VariableRuntimeService,
): SettingLibraryAgentTurnContext {
    val keywordResolved = withKeywordPromotions(messages)
    val candidateEntries = keywordResolved.readableEntries
    val visibleEntries = candidateEntries
        .filter { entry ->
            entry.readStrategy != SettingLibraryAgentReadStrategy.VariableCondition ||
                entry.dynamicMode == SettingLibraryDynamicMode.EjsController
        }
        .map { entry: SettingLibraryAgentEntry ->
            if (entry.readStrategy == SettingLibraryAgentReadStrategy.VariableCondition &&
                entry.dynamicMode == SettingLibraryDynamicMode.EjsController) {
                entry.copy(promotedToRequiredThisTurn = true)
            } else {
                entry
            }
        }
    val ejsTargetIds = visibleEntries.asSequence()
        .filter { entry ->
            entry.readStrategy == SettingLibraryAgentReadStrategy.VariableCondition &&
                entry.dynamicMode == SettingLibraryDynamicMode.EjsController
        }
        .map(SettingLibraryAgentEntry::id)
        .toSet()
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
            candidates = candidateEntries,
            targets = visibleEntries.filter { entry -> entry.id in ejsTargetIds },
        ),
        targetIds = ejsTargetIds,
    )
    return keywordResolved.copy(
        readableEntries = visibleEntries.mapNotNull { entry ->
            val renderResult = rendered[entry.id]
            val content = renderResult?.content ?: entry.content
            entry.copy(
                content = content,
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
}

internal fun ejsTemplateSources(
    candidates: List<SettingLibraryAgentEntry>,
    targets: List<SettingLibraryAgentEntry>,
): List<EjsTemplateSource> = targets.flatMap { entry ->
    listOf(
        EjsTemplateSource(
            id = entry.id,
            controllerId = entry.id,
            title = entry.title,
            path = entry.path,
            content = entry.content,
        ),
    ) + candidates
        .asSequence()
        .filter { source ->
            source.readStrategy == SettingLibraryAgentReadStrategy.VariableCondition &&
                when (source.dynamicMode) {
                    SettingLibraryDynamicMode.EjsReference -> true
                    SettingLibraryDynamicMode.EjsController -> source.id != entry.id
                    SettingLibraryDynamicMode.Standard -> false
                }
        }
        .map { source ->
            EjsTemplateSource(
                id = if (source.dynamicMode == SettingLibraryDynamicMode.EjsController) {
                    "${entry.id}::controller-ref::${source.id}"
                } else {
                    source.id
                },
                controllerId = entry.id,
                title = source.title,
                path = source.path,
                content = source.content,
            )
        }
        .toList()
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
