package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentInputImage
import com.eleckoi.android.engine.agent.api.AgentPrompt
import com.eleckoi.android.engine.agent.api.AgentSession
import com.eleckoi.android.engine.agent.api.AgentSessionFactory
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.tools.AgentToolContextSnapshot
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.supportsImageInput
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.story.variables.runtime.VariableRuntimeService
import com.eleckoi.android.engine.workspace.runtime.model.LocalRuntimeGateway
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleSurface
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.nowIso
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs every ordinary role conversation through the same Harness-neutral AgentSession API. */
class CharacterAgentGenerationService(
    private val characters: CharacterRepository,
    private val sessions: ChatSessionStore,
    private val settings: ModelConfigRepository,
    private val workspaces: CreatorWorkspaceRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val regexRules: RegexRuleRepository,
    private val variableConfig: VariableConfigRepository,
    private val variableRuntime: VariableRuntimeService,
    private val runtime: LocalRuntimeGateway,
    private val agentSessions: AgentSessionFactory,
    private val virtualFileSearch: AgentVirtualFileSearch,
    private val toolContextSnapshot: (Set<String>) -> AgentToolContextSnapshot,
    private val prepareDraftProjection: (ChatSession, ModelConfig) -> (ChatSession) -> ChatDraft,
    private val replyImageGenerator: ReplyImageGenerator,
    private val generationAttempts: GenerationAttemptRepository,
    private val activeAgentPreset: suspend () -> AgentPreset,
    private val publishRemoteDshTurnImages: (String, List<AgentInputImage>) -> Unit = { _, _ -> },
    private val captureProviderRequests: Boolean,
) {
    private val generations = GenerationLeaseRegistry()
    private val activeSession = AtomicReference<AgentSession?>(null)
    private val cancellationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val turnPreparer = CharacterAgentTurnPreparer(
        settings = settings,
        workspaces = workspaces,
        settingLibrary = settingLibrary,
        regexRules = regexRules,
        variableConfig = variableConfig,
        variableRuntime = variableRuntime,
        virtualFileSearch = virtualFileSearch,
        toolContextSnapshot = toolContextSnapshot,
        activeAgentPreset = activeAgentPreset,
        captureProviderRequests = captureProviderRequests,
    )
    private val environment = CharacterAgentGenerationEnvironment(settings, workspaces, sessions)
    private val turnCommitter = CharacterAgentTurnCommitter(generations, sessions, generationAttempts)
    private val turnRunner = CharacterAgentTurnRunner(
        sessions = sessions,
        regexRules = regexRules,
        runtime = runtime,
        agentSessions = agentSessions,
        prepareDraftProjection = prepareDraftProjection,
        replyImageGenerator = replyImageGenerator,
        generationAttempts = generationAttempts,
        publishRemoteDshTurnImages = publishRemoteDshTurnImages,
        captureProviderRequests = captureProviderRequests,
        generations = generations,
        cancellationScope = cancellationScope,
        turnPreparer = turnPreparer,
        turnCommitter = turnCommitter,
        activeSession = activeSession,
    )

    fun isStreamCancelled(error: Throwable): Boolean {
        return error is CancellationException || error.message == GenerationCancelled
    }

    suspend fun sendMessage(
        draft: ChatDraft,
        message: String,
        inputImages: List<ChatUserImageAttachment>,
        onDelta: (ChatDraft) -> Unit,
        onUserTurnPersisted: (ChatDraft, String) -> Unit = { _, _ -> },
    ): ChatSendResult {
        val regexConfig = regexRules.load(draft.session.characterId)
        val content = RegexRuleProcessor.transform(
            text = message.trim(),
            rules = regexRules.rulesFor(regexConfig, RegexRuleTarget.UserInput, RegexRuleSurface.Stored),
            target = RegexRuleTarget.UserInput,
        )
        if (content.isEmpty() && inputImages.isEmpty()) throw ElecKoiDataException("输入不能为空")
        val config = environment.selectedConfig(draft)
        if (inputImages.isNotEmpty() && !config.supportsImageInput()) {
            throw ElecKoiDataException("当前模型没有开启图片输入能力")
        }
        val persisted = sessions.load(draft.session.id, touch = true)
        var session = environment.ensureWorkspace(
            authoritativeGenerationSession(
                persisted = persisted,
                activeMessages = sessions.activeMessages(persisted.id),
            ),
        )
        val userMessage = ChatMessage(
            id = newId(10),
            role = MessageRole.User,
            content = content,
            provider = config.provider,
            model = config.model,
            createdAt = nowIso(),
            variableStateJson = session.variableStateJson,
            inputImageAttachments = inputImages,
        )
        session = session.copy(messages = session.messages + userMessage, updatedAt = nowIso())
        sessions.appendUserTurn(session, userMessage)
        onUserTurnPersisted(draft.copy(session = session), userMessage.id)
        sessions.applyHistorySavePolicy(session.characterId)
        return turnRunner.run(
            session = session,
            prompt = AgentPrompt(
                text = RegexRuleProcessor.transform(
                    text = content,
                    rules = regexRules.rulesFor(
                        regexConfig,
                        RegexRuleTarget.UserInput,
                        RegexRuleSurface.Prompt,
                    ),
                    target = RegexRuleTarget.UserInput,
                ),
                images = inputImages.map { image ->
                    AgentInputImage(image.localPath, image.mediaType, image.displayName)
                },
            ),
            config = config,
            replacementMessageId = null,
            onDelta = onDelta,
        )
    }

    suspend fun prepareRegeneration(
        draft: ChatDraft,
        targetMessageId: String,
        replacementMessage: String?,
        pendingMessageId: String,
    ): PreparedChatRegeneration {
        val regexConfig = regexRules.load(draft.session.characterId)
        val config = environment.selectedConfig(draft)
        val persisted = sessions.load(draft.session.id, touch = true)
        var session = environment.ensureWorkspace(
            authoritativeGenerationSession(
                persisted = persisted,
                activeMessages = sessions.activeMessages(persisted.id),
            ),
        )
        val editedReplacement = replacementMessage?.let { replacement ->
            RegexRuleProcessor.transform(
                text = replacement,
                rules = regexRules.rulesFor(
                    regexConfig,
                    RegexRuleTarget.UserInput,
                    RegexRuleSurface.Stored,
                ).filter { it.runOnEdit },
                target = RegexRuleTarget.UserInput,
            )
        }
        val regeneration = truncateForRegeneration(
            messages = session.messages,
            targetMessageId = targetMessageId,
            replacementMessage = editedReplacement,
            provider = config.provider,
            model = config.model,
        )
        val variablesConfigured = characterVariableCatalog(variableConfig.load(session.characterId)).isNotEmpty()
        session = session.copy(
            messages = regeneration.messages,
            variableStateJson = regenerationSessionVariableState(
                currentStateJson = session.variableStateJson,
                retainedStateJson = regeneration.retainedVariableStateJson,
                variablesConfigured = variablesConfigured,
            ),
            updatedAt = nowIso(),
            generationStats = com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats(),
        )
        sessions.truncateForRegeneration(
            session = session,
            retainedMessage = regeneration.messages.last(),
        )
        // The old branch is now durably gone. Remove only the image files it referenced; a newly
        // generated revision receives a different image identity and path.
        replyImageGenerator.deleteGeneratedFiles(regeneration.removedImagePaths)
        val resolvedPendingMessageId = regeneration.replacementMessageId ?: pendingMessageId
        return PreparedChatRegeneration(
            truncatedDraft = CharacterAgentTurnDraftProjection(
                initialSession = session,
                config = config,
                prepare = prepareDraftProjection,
            ).project(session),
            session = session,
            prompt = RegexRuleProcessor.transform(
                text = regeneration.prompt,
                rules = regexRules.rulesFor(
                    regexConfig,
                    RegexRuleTarget.UserInput,
                    RegexRuleSurface.Prompt,
                ),
                target = RegexRuleTarget.UserInput,
            ),
            config = config,
            pendingMessageId = resolvedPendingMessageId,
            inputImages = session.messages.asReversed()
                .firstOrNull { it.role == MessageRole.User }
                ?.inputImageAttachments
                .orEmpty(),
            obsoleteRuntimeThreadIds = regeneration.obsoleteRuntimeThreadIds,
        )
    }

    suspend fun runPreparedRegeneration(
        prepared: PreparedChatRegeneration,
        onDelta: (ChatDraft) -> Unit,
    ): ChatSendResult {
        return turnRunner.run(
            session = prepared.session,
            prompt = AgentPrompt(
                text = prepared.prompt,
                images = prepared.inputImages.map { image ->
                    AgentInputImage(image.localPath, image.mediaType, image.displayName)
                },
            ),
            config = prepared.config,
            replacementMessageId = prepared.pendingMessageId,
            obsoleteRuntimeThreadIds = prepared.obsoleteRuntimeThreadIds,
            onDelta = onDelta,
        )
    }

    fun cancelActiveStream() {
        generations.cancelActive()
        activeSession.get()?.let { session ->
            cancellationScope.launch { runCatching { session.interrupt() } }
        }
    }

    fun settleOrphanedPendingResponses(sessionId: String) {
        generations.runIfSessionInactive(sessionId) {
            sessions.settleOrphanedPendingResponses(sessionId)
        }
    }
}
