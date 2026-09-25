package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.engine.agent.api.AgentFileAccessScope
import com.eleckoi.android.engine.agent.api.AgentSessionOptions
import com.eleckoi.android.engine.agent.api.AgentThreadStart
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.eleckoi.conversation.roomConversationHistory
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateMessage
import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateSource
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentTurnContext
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryAgentEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryResolvedReference
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleSurface
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.roleplay.actions.generateImageActionContextInjection
import com.eleckoi.android.feature.chat.roleplay.protocol.effectiveRoleplayPlanItems
import com.eleckoi.android.feature.chat.roleplay.protocol.roleplayPlanDynamicTool
import com.eleckoi.android.feature.chat.roleplay.protocol.roleplayPlanFixedItemsInstructions
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.historyCompactionInstructions
import com.eleckoi.android.foundation.storage.ElecKoiDataException

internal data class CharacterAgentTurnPreparation(
    val options: AgentSessionOptions,
    val contextInjections: List<AgentContextInjection>,
    val imageConfig: ModelConfig?,
    val variableTurnState: CharacterVariableTurnState?,
)

/** Builds the immutable inputs for one role-chat Agent turn before streaming begins. */
internal class CharacterAgentTurnPreparer(
    private val settings: ModelConfigRepository,
    private val workspaces: CreatorWorkspaceRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val regexRules: RegexRuleRepository,
    private val variableConfig: VariableConfigRepository,
    private val variableRuntime: VariableRuntimeService,
    private val virtualFileSearch: AgentVirtualFileSearch,
    private val toolContextSnapshot: (Set<String>) -> AgentToolContextSnapshot,
    private val activeAgentPreset: suspend () -> AgentPreset,
    private val generationAttempts: GenerationAttemptRepository,
    private val captureProviderRequests: Boolean,
) {
    suspend fun prepare(
        session: ChatSession,
        config: ModelConfig,
        replacementMessageId: String?,
        obsoleteRuntimeThreadIds: Set<String> = emptySet(),
    ): CharacterAgentTurnPreparation {
        val workspaceProjectPath = workspaces.get(session.workspaceId)
            ?.let(workspaces::runtimeProjectPath)
            ?: throw ElecKoiDataException("角色工作区不存在")
        val agentPreset = activeAgentPreset()
        val toolConfiguration = agentPreset.toolConfiguration.normalized()
        val activeToolContext = toolContextSnapshot(toolConfiguration.enabledGroupIds)
        val settingLibraryEnabled = activeToolContext.isEnabled(
            AgentToolRequestPolicy.BuiltInSettingLibrary,
        )
        val roleplayPlanEnabled = activeToolContext.isEnabled(
            AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
        )
        val variablesEnabled = activeToolContext.isEnabled(AgentToolRequestPolicy.BuiltInVariables)
        val macroValues = CharacterCardMacroValues(
            userName = session.characterPersona.userName.ifBlank { "用户" },
            characterName = session.characterName.ifBlank {
                session.characterPersona.assistantName.ifBlank { "AI" }
            },
        )
        // GenerationService already restored the authoritative Room branch before entering this
        // preparer. Re-reading the same branch here doubled every turn's history materialization.
        val roomHistory = session.messages.map { message ->
            message.copy(
                content = message.content.resolveCharacterCardMacros(macroValues),
                reasoningContent = message.reasoningContent.resolveCharacterCardMacros(macroValues),
            )
        }
        val regexConfig = regexRules.load(session.characterId)
        val promptHistory = promptRegexedHistory(roomHistory) { target ->
            regexRules.rulesFor(regexConfig, target, RegexRuleSurface.Prompt)
        }
        val storyTurnContext = settingLibrary.loadAgentTurnContext(
            characterId = session.characterId,
            sessionId = session.id,
            additionalLibrary = agentPreset.asRuntimeSettingLibrary(),
        )
        val activeVariableConfig = if (variablesEnabled || settingLibraryEnabled) {
            variableConfig.load(session.characterId)
                .resolveCharacterCardMacros(macroValues)
        } else {
            null
        }
        val variableTurnState = activeVariableConfig?.let { activeConfig ->
            CharacterVariableTurnState(
                session.variableStateJson.ifBlank { activeConfig.initialStateJson },
            )
        }
        val promotedStoryTurnContext = storyTurnContext
            .resolveCharacterCardMacros(macroValues)
            .resolveDynamicEntries(
                messages = roomHistory,
                keywordMessages = promptHistory,
                stateJson = variableTurnState?.stateJson.orEmpty(),
                runtime = variableRuntime,
            )
        val storyLibrary = promotedStoryTurnContext.automaticLibrary.let { library ->
            if (settingLibraryEnabled) {
                library
            } else {
                library.copy(
                    entries = library.entries.filter { it.id.startsWith("agent-preset:") },
                    groups = library.groups.filter { it.id.startsWith("agent-preset:") },
                )
            }
        }
        val settingPromptRules = regexRules.rulesFor(
            regexConfig,
            RegexRuleTarget.SettingContent,
            RegexRuleSurface.Prompt,
        )
        val requiredSettingCache = if (settingLibraryEnabled) {
            RequiredSettingLibraryCache(promotedStoryTurnContext.readableEntries) { content ->
                RegexRuleProcessor.transform(
                    text = content,
                    rules = settingPromptRules,
                    target = RegexRuleTarget.SettingContent,
                )
            }
        } else {
            null
        }
        val currentUserMessageId = session.messages.asReversed()
            .firstOrNull { it.role == MessageRole.User }
            ?.id
            .orEmpty()
        val roomHistoryItems = roomConversationHistory(
            messages = promptHistory.map { it.toLedgerMessage() },
            currentUserMessageId = currentUserMessageId,
        )
        val selectedImageConfigId = toolConfiguration.toolModelConfigIds[
            AgentToolRequestPolicy.BuiltInAutoIllustration
        ].orEmpty()
        val imageConfig = settings.loadModelConfigCollection().configs.firstOrNull {
            it.id == selectedImageConfigId && it.isImageGenerationConfig()
        }
            ?.takeIf {
                activeToolContext.isEnabled(AgentToolRequestPolicy.BuiltInAutoIllustration)
            }
        val resolvedRoleplayPlanItems = effectiveRoleplayPlanItems(
            items = agentPreset.roleplayPlan.steps.map { it.resolveCharacterCardMacros(macroValues) },
            imageActionEnabled = imageConfig != null,
        )
        val protocolInstructions = ""
        val contextInjections = buildList {
            addAll(CharacterSettingContextResolver.resolve(
                library = storyLibrary,
                messages = roomHistory,
                imageActionEnabled = imageConfig != null,
                requiredCache = requiredSettingCache,
            ).map { injection ->
                if (requiredSettingCache?.ownsInjection(injection.id) == true) return@map injection
                injection.copy(
                    content = RegexRuleProcessor.transform(
                        text = injection.content,
                        rules = settingPromptRules,
                        target = RegexRuleTarget.SettingContent,
                    ),
                )
            })
            if (roleplayPlanEnabled) {
                roleplayPlanFixedItemsInstructions(resolvedRoleplayPlanItems)
                    .takeIf(String::isNotBlank)
                    ?.let { planInstructions ->
                        add(
                            AgentContextInjection(
                                id = "author-roleplay-plan-items",
                                anchor = AgentContextAnchor.BeforeToolContext,
                                role = AgentContextRole.System,
                                activation = AgentContextActivation.FirstModelRequest,
                                content = planInstructions,
                                order = activeToolContext.orderOf(
                                    AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
                                ),
                            ),
                        )
                    }
            }
            imageConfig?.let { active ->
                add(
                    generateImageActionContextInjection(
                        imageConfig = active,
                        order = (activeToolContext.blocks.maxOfOrNull { it.order } ?: 0) + 1,
                    ),
                )
            }
        }
        val dynamicTools = buildList {
            if (roleplayPlanEnabled && resolvedRoleplayPlanItems.isNotEmpty()) {
                add(roleplayPlanDynamicTool(resolvedRoleplayPlanItems))
            }
            if (settingLibraryEnabled) {
                val liveSettingContext: suspend () -> SettingLibraryAgentTurnContext = {
                    settingLibrary.loadAgentTurnContext(
                        characterId = session.characterId,
                        sessionId = session.id,
                        additionalLibrary = agentPreset.asRuntimeSettingLibrary(),
                    )
                        .resolveCharacterCardMacros(macroValues)
                        .resolveDynamicEntries(
                            messages = roomHistory,
                            keywordMessages = promptHistory,
                            stateJson = variableTurnState?.stateJson.orEmpty(),
                            runtime = variableRuntime,
                        )
                }
                addAll(
                    characterSettingLibraryTools(
                        contextProvider = liveSettingContext,
                        virtualFileSearch = virtualFileSearch,
                        requiredCache = requireNotNull(requiredSettingCache),
                    ) { mutations ->
                        settingLibrary.applySessionMutations(
                            characterId = session.characterId,
                            sessionId = session.id,
                            mutations = mutations,
                        )
                    },
                )
            }
            if (variablesEnabled && activeVariableConfig != null && variableTurnState != null) {
                addAll(
                    characterVariableTools(
                        config = activeVariableConfig,
                        turnState = variableTurnState,
                        runtime = variableRuntime,
                        virtualFileSearch = virtualFileSearch,
                    ),
                )
            }
        }
        val threadStart = if (replacementMessageId != null) {
            // Regeneration replaces Room's active branch, so seed a fresh durable runtime branch
            // from the retained Room history and persist the returned id on the replacement.
            AgentThreadStart.Fresh
        } else {
            continuationThreadStart(
                conversationId = session.id,
                messages = session.messages,
            ) { conversationId, messageId ->
                generationAttempts.latestReplyForMessage(conversationId, messageId)?.state
            }
        }
        return CharacterAgentTurnPreparation(
            options = AgentSessionOptions(
                workspaceId = session.workspaceId,
                workspaceProjectPath = workspaceProjectPath,
                conversationId = roleConversationId(session.id),
                enabledToolGroupIds = toolConfiguration.enabledGroupIds,
                presetId = agentPreset.id,
                modelConfigId = config.id,
                subagentModelConfigId = toolConfiguration.subagentModelConfigId
                    .takeIf(String::isNotBlank),
                subagentModel = toolConfiguration.subagentModel.takeIf(String::isNotBlank),
                model = config.model,
                modelProvider = config.provider,
                baseInstructions = protocolInstructions,
                developerInstructions = "",
                threadStart = threadStart,
                discardThreadIds = obsoleteRuntimeThreadIds,
                ephemeral = false,
                initialHistoryItems = roomHistoryItems,
                historyCompactionInstructions = agentPreset.historyCompactionInstructions(),
                captureProviderRequests = captureProviderRequests,
                permissionMode = session.permissionMode,
                fileAccessScope = AgentFileAccessScope.CurrentWorkspace,
                dynamicTools = dynamicTools,
                toolContextBlocks = activeToolContext.blocks,
            ),
            contextInjections = contextInjections,
            imageConfig = imageConfig,
            variableTurnState = variableTurnState,
        )
    }
}

/**
 * Viewing a trace may use the latest attempted thread, but continuation may resume only a
 * successfully committed reply. A failed or cancelled latest thread is abandoned so late DSH
 * events cannot enter the next turn.
 */
internal fun continuationThreadStart(
    conversationId: String,
    messages: List<com.eleckoi.android.feature.chat.model.ChatMessage>,
    attemptState: (conversationId: String, outputMessageId: String) -> GenerationAttemptState?,
): AgentThreadStart {
    val latestRuntimeReply = messages.asReversed().firstOrNull { message ->
        message.role == MessageRole.Assistant && message.runtimeThreadId.isNotBlank()
    } ?: return AgentThreadStart.BoundOrNew
    return if (attemptState(conversationId, latestRuntimeReply.id) == GenerationAttemptState.Succeeded) {
        AgentThreadStart.Resume(latestRuntimeReply.runtimeThreadId)
    } else {
        AgentThreadStart.Fresh
    }
}

