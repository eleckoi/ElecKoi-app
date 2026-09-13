package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.engine.agent.api.AgentFileAccessScope
import com.eleckoi.android.engine.agent.api.AgentHistoryPolicy
import com.eleckoi.android.engine.agent.api.AgentPrompt
import com.eleckoi.android.engine.agent.api.AgentSessionOptions
import com.eleckoi.android.engine.agent.api.AgentThreadStart
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.eleckoi.conversation.roomConversationHistory
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.agent.tools.AgentToolScopes
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateMessage
import com.eleckoi.android.engine.story.variables.runtime.EjsTemplateSource
import com.eleckoi.android.engine.story.variables.runtime.VariableConditionExpression
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.model.CharacterMode
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
import com.eleckoi.android.feature.chat.roleplay.protocol.roleplayOutputProtocolInstructions
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPreset
import com.eleckoi.android.feature.characters.modes.story.presets.model.historyCompactionInstructions
import com.eleckoi.android.foundation.storage.ElecKoiDataException

internal data class CharacterAgentTurnPreparation(
    val options: AgentSessionOptions,
    val prompt: AgentPrompt,
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
    private val toolContextSnapshot: (String) -> AgentToolContextSnapshot,
    private val toolModelConfigId: (scopeId: String, groupId: String) -> String,
    private val activeStoryPreset: suspend () -> StoryPreset,
    private val captureProviderRequests: Boolean,
) {
    suspend fun prepare(
        session: ChatSession,
        prompt: AgentPrompt,
        config: ModelConfig,
        replacementMessageId: String?,
        obsoleteRuntimeThreadIds: Set<String> = emptySet(),
    ): CharacterAgentTurnPreparation {
        val workspaceProjectPath = workspaces.get(session.workspaceId)
            ?.let(workspaces::runtimeProjectPath)
            ?: throw ElecKoiDataException("角色模式工作区不存在")
        val characterMode = CharacterMode.fromStorage(session.characterMode)
        val storyPreset = if (characterMode == CharacterMode.Story) activeStoryPreset() else null
        val toolScopeId = AgentToolScopes.character(session.characterId)
        val activeToolContext = toolContextSnapshot(toolScopeId)
        val settingLibraryEnabled = activeToolContext.isEnabled(
            AgentToolRequestPolicy.BuiltInSettingLibrary,
        )
        val roleplayPlanEnabled = activeToolContext.isEnabled(
            AgentToolRequestPolicy.BuiltInRoleplayWorkflow,
        )
        val variablesEnabled = characterMode == CharacterMode.Story &&
            activeToolContext.isEnabled(AgentToolRequestPolicy.BuiltInVariables)
        val macroValues = CharacterCardMacroValues(
            userName = session.characterPersona.userName.ifBlank { "用户" },
            characterName = session.characterName.ifBlank {
                session.characterPersona.assistantName.ifBlank { "AI" }
            },
        )
        // GenerationService already restored the authoritative Room branch before entering this
        // preparer. Re-reading the same branch here doubled every turn's history materialization.
        val roomHistorySource = session.messages.map { message ->
            message.copy(
                content = message.content.resolveCharacterCardMacros(macroValues),
                reasoningContent = message.reasoningContent.resolveCharacterCardMacros(macroValues),
            )
        }
        val regexConfig = regexRules.load(session.characterId)
        val storyTurnContext = if (characterMode == CharacterMode.Story) {
            settingLibrary.loadAgentTurnContext(
                characterId = session.characterId,
                sessionId = session.id,
                additionalLibrary = storyPreset?.asRuntimeSettingLibrary(),
            )
        } else {
            null
        }
        val activeVariableConfig = if (
            characterMode == CharacterMode.Story && (variablesEnabled || settingLibraryEnabled)
        ) {
            variableConfig.load(session.characterId)
                .resolveCharacterCardMacros(macroValues)
        } else {
            null
        }
        val variableTurnState = if (characterMode == CharacterMode.Story) {
            CharacterVariableTurnState(
                session.variableStateJson.ifBlank {
                    activeVariableConfig?.initialStateJson.orEmpty().ifBlank { "{}" }
                },
            )
        } else {
            null
        }
        val promotedStoryTurnContext = storyTurnContext
            ?.resolveCharacterCardMacros(macroValues)
            ?.resolveDynamicEntries(
                messages = roomHistorySource,
                stateJson = variableTurnState?.stateJson.orEmpty(),
                runtime = variableRuntime,
            )
        val storyLibrary = promotedStoryTurnContext?.automaticLibrary?.let { library ->
            if (settingLibraryEnabled) {
                library
            } else {
                library.copy(
                    entries = library.entries.filter { it.id.startsWith("story-preset:") },
                    groups = library.groups.filter { it.id.startsWith("story-preset:") },
                )
            }
        }
        val selectedImageConfigId = toolModelConfigId(
            toolScopeId,
            AgentToolRequestPolicy.BuiltInAutoIllustration,
        )
        val imageConfig = settings.loadModelConfigCollection().configs.firstOrNull {
            it.id == selectedImageConfigId && it.isImageGenerationConfig()
        }
            ?.takeIf {
                activeToolContext.isEnabled(AgentToolRequestPolicy.BuiltInAutoIllustration)
            }
        val settingContextSource = CharacterSettingContextResolver.resolve(
            library = storyLibrary,
            messages = roomHistorySource,
            imageActionEnabled = imageConfig != null,
        )
        val promptMacroResolver = PromptMacroResolver(
            stateJson = variableTurnState?.stateJson ?: session.variableStateJson,
        )

        // Prompt macros use two passes. Every active fragment applies state changes first, then
        // every getvar sees the completed turn state regardless of the entry's prompt position.
        val stagedSettingContext = settingContextSource.map { injection ->
            injection.copy(content = promptMacroResolver.applyMutations(injection.content))
        }
        val stagedRoleplayPlanItems = promotedStoryTurnContext?.fixedRoleplayPlanItems.orEmpty()
            .map(promptMacroResolver::applyMutations)
        val stagedAuthorPrompt = promptMacroResolver.applyMutations(
            session.characterPersona.assistantPrompt.resolveCharacterCardMacros(macroValues),
        )
        val stagedPrompt = prompt.copy(
            text = promptMacroResolver.applyMutations(
                prompt.text.resolveCharacterCardMacros(macroValues),
            ),
        )
        variableTurnState?.replaceState(promptMacroResolver.stateJson)

        val roomHistory = roomHistorySource.map { message ->
            message.copy(
                content = promptMacroResolver.resolveValues(message.content),
                reasoningContent = promptMacroResolver.resolveValues(message.reasoningContent),
            )
        }
        val currentUserMessageId = session.messages.asReversed()
            .firstOrNull { it.role == MessageRole.User }
            ?.id
            .orEmpty()
        val promptHistory = roomHistory.map { message ->
            val target = if (message.role == MessageRole.User) RegexRuleTarget.UserInput else RegexRuleTarget.AiOutput
            message.copy(
                content = RegexRuleProcessor.transform(
                    text = message.content,
                    rules = regexRules.rulesFor(regexConfig, target, RegexRuleSurface.Prompt),
                    target = target,
                ),
            )
        }
        val roomHistoryItems = roomConversationHistory(
            messages = promptHistory.map { it.toLedgerMessage() },
            currentUserMessageId = currentUserMessageId,
        )
        val resolvedRoleplayPlanItems = effectiveRoleplayPlanItems(
            items = stagedRoleplayPlanItems.map(promptMacroResolver::resolveValues),
            imageActionEnabled = imageConfig != null,
        )
        val instructions = characterAgentInstructions(
            mode = characterMode,
            authorPrompt = promptMacroResolver.resolveValues(stagedAuthorPrompt),
            protocolInstructions = if (characterMode == CharacterMode.Story) {
                ""
            } else {
                roleplayOutputProtocolInstructions(actionCallEnabled = imageConfig != null)
            },
        )
        val contextInjections = buildList {
            addAll(stagedSettingContext.map { injection ->
                injection.copy(
                    content = RegexRuleProcessor.transform(
                        text = promptMacroResolver.resolveValues(injection.content),
                        rules = regexRules.rulesFor(
                            regexConfig,
                            RegexRuleTarget.SettingContent,
                            RegexRuleSurface.Prompt,
                        ),
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
                                activation = AgentContextActivation.Immediate,
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
            if (characterMode == CharacterMode.Story && settingLibraryEnabled) {
                val liveSettingContext: suspend () -> SettingLibraryAgentTurnContext = {
                    settingLibrary.loadAgentTurnContext(
                        characterId = session.characterId,
                        sessionId = session.id,
                        additionalLibrary = storyPreset?.asRuntimeSettingLibrary(),
                    )
                        .resolveCharacterCardMacros(macroValues)
                        .resolveDynamicEntries(
                            messages = roomHistory,
                            stateJson = variableTurnState?.stateJson.orEmpty(),
                            runtime = variableRuntime,
                        )
                }
                addAll(
                    characterSettingLibraryTools(
                        contextProvider = liveSettingContext,
                        virtualFileSearch = virtualFileSearch,
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
            session.messages.asReversed()
                .firstOrNull { it.role == MessageRole.Assistant && it.runtimeThreadId.isNotBlank() }
                ?.runtimeThreadId
                ?.let(AgentThreadStart::Resume)
                ?: AgentThreadStart.BoundOrNew
        }
        return CharacterAgentTurnPreparation(
            options = AgentSessionOptions(
                workspaceId = session.workspaceId,
                workspaceProjectPath = workspaceProjectPath,
                conversationId = roleConversationId(session.id),
                toolScopeId = toolScopeId,
                modelConfigId = config.id,
                model = config.model,
                modelProvider = config.provider,
                baseInstructions = instructions.baseInstructions,
                developerInstructions = instructions.developerInstructions,
                threadStart = threadStart,
                discardThreadIds = obsoleteRuntimeThreadIds,
                ephemeral = false,
                initialHistoryItems = roomHistoryItems,
                historyPolicy = AgentHistoryPolicy.ProductDialogue,
                historyCompactionInstructions = storyPreset?.historyCompactionInstructions(),
                captureProviderRequests = captureProviderRequests,
                permissionMode = session.permissionMode,
                fileAccessScope = AgentFileAccessScope.CurrentWorkspace,
                dynamicTools = dynamicTools,
                toolContextBlocks = activeToolContext.blocks,
            ),
            prompt = stagedPrompt.copy(text = promptMacroResolver.resolveValues(stagedPrompt.text)),
            contextInjections = contextInjections,
            imageConfig = imageConfig,
            variableTurnState = variableTurnState,
        )
    }
}

