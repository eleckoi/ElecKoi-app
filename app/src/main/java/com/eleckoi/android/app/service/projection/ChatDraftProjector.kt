package com.eleckoi.android.app.service

import com.eleckoi.android.engine.agent.eleckoi.conversation.LedgerMessage
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.display.MessageDisplayCompatibility
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleProcessor
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleSurface
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleTarget
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryOpeningMessage
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.chat.data.rich.decorateRichDisplayReplacement
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatOpeningOption
import com.eleckoi.android.feature.modelconfig.model.ChatModelSelection
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.ImmutableAppendedList
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.model.OpeningMessageId
import kotlinx.coroutines.flow.map

internal class ChatDraftProjector(
    private val sessions: ChatSessionStore,
    private val settings: ModelConfigRepository,
    private val modelSelections: ChatModelSelectionResolver,
    private val regexRules: RegexRuleRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val displayCompatibility: MessageDisplayCompatibility,
) {
    private val displayRegexCache = DisplayRegexProjectionCache()
    private val displayMessageListProjector = DisplayRegexMessageListProjector()

    fun clearCaches() {
        displayRegexCache.clear()
        displayMessageListProjector.clear()
    }

    fun project(
        session: ChatSession,
        config: ModelConfig? = null,
    ): ChatDraft = projectDraft(
        session = session,
        context = prepareDraftProjectionContext(
            session = session,
            config = config,
            hasUserMessages = sessions.hasUserMessages(session.id),
        ),
    )

    fun prepareStreaming(
        session: ChatSession,
        config: ModelConfig,
    ): (ChatSession) -> ChatDraft {
        val context = prepareDraftProjectionContext(
            session = session,
            config = config,
            // Every model turn is prepared from an authoritative branch that already ends in the
            // owning user message. Do not query Room again for every streamed UI snapshot.
            hasUserMessages = true,
        )
        return { current -> projectDraft(current, context) }
    }

    private fun prepareDraftProjectionContext(
        session: ChatSession,
        config: ModelConfig?,
        hasUserMessages: Boolean,
    ): ChatDraftProjectionContext {
        val collection = settings.loadModelConfigCollection()
        val selection = modelSelections.defaultCached(collection)
        val selectedConfig = config
            ?.takeIf { it.id == selection.configId }
            ?: collection.chatConfigs.firstOrNull { it.id == selection.configId }
            ?: collection.chatConfigs.firstOrNull { it.model.isNotBlank() }
            ?: collection.chatConfigs.firstOrNull()
            ?: ModelConfig()
        val selectedModel = selection.model.ifBlank { selectedConfig.model }
        val regexConfig = regexRules.load(session.characterId)
        val regexRevision = regexRules.revision.value
        val effectiveSettingLibrary = settingLibrary.loadEffective(session.characterId, session.id)
        val openingMessages = effectiveSettingLibrary.entries
            .firstOrNull { it.isOpeningEntry() && it.enabled }
            ?.openingMessages
            .orEmpty()
        return ChatDraftProjectionContext(
            selectedConfig = selectedConfig,
            selectedModel = selectedModel,
            selection = selection,
            regexConfig = regexConfig,
            regexRevision = regexRevision,
            settingLibrary = effectiveSettingLibrary,
            openingMessages = openingMessages,
            hasUserMessages = hasUserMessages,
        )
    }

    private fun projectDraft(
        session: ChatSession,
        context: ChatDraftProjectionContext,
    ): ChatDraft {
        val displaySession = session.copy(
            messages = displayMessageListProjector.project(
                messages = session.messages,
                characterId = session.characterId,
                regexRevision = context.regexRevision,
            ) { message ->
                message.withDisplayRegex(
                    config = context.regexConfig,
                    characterId = session.characterId,
                    regexRevision = context.regexRevision,
                )
            },
        )
        val openingMessages = context.openingMessages
        val currentOpening = session.messages.firstOrNull { it.id == OpeningMessageId }
        val selectedOpeningId = currentOpening?.let { selected ->
            openingMessages.firstOrNull { candidate ->
                candidate.content.trim() == selected.content.trim() &&
                    candidate.initialVariableStateJson.trim() == session.initialVariableStateJson.trim()
            }?.id ?: openingMessages.firstOrNull { candidate ->
                candidate.content.trim() == selected.content.trim()
            }?.id
        }.orEmpty()
        return ChatDraft(
            session = displaySession,
            selectedModelConfig = context.selectedConfig,
            selectedModel = context.selectedModel,
            settingLibrary = context.settingLibrary,
            openingOptions = openingMessages.map { message ->
                ChatOpeningOption(
                    id = message.id,
                    title = message.title,
                    content = message.content,
                    initialVariableStateJson = message.initialVariableStateJson,
                )
            },
            selectedOpeningOptionId = selectedOpeningId,
            openingSelectionEnabled = openingMessages.size > 1 &&
                !context.hasUserMessages,
        )
    }

    private fun ChatMessage.withDisplayRegex(
        config: RegexRuleCollection,
        characterId: String,
        regexRevision: Long,
    ): ChatMessage {
        val target = if (role == MessageRole.User) RegexRuleTarget.UserInput else RegexRuleTarget.AiOutput
        val projection = displayRegexCache.project(
            characterId = characterId,
            regexRevision = regexRevision,
            messageId = id,
            target = target,
            content = content,
            reasoningContent = reasoningContent,
            variableStateJson = variableStateJson,
            completedAssistant = role == MessageRole.Assistant && !pending,
        ) {
            DisplayRegexProjection(
                content = displayRegex(
                    text = content,
                    config = config,
                    target = target,
                    variableStateJson = variableStateJson,
                    completedAssistant = role == MessageRole.Assistant && !pending,
                ),
                reasoningContent = displayRegex(
                    text = reasoningContent,
                    config = config,
                    target = RegexRuleTarget.Reasoning,
                    variableStateJson = variableStateJson,
                ),
            )
        }
        return if (
            projection.content == content &&
            projection.reasoningContent == reasoningContent
        ) {
            this
        } else {
            copy(
                content = projection.content,
                reasoningContent = projection.reasoningContent,
            )
        }
    }

    fun projectLedgerMessage(
        message: LedgerMessage,
        config: RegexRuleCollection,
        characterId: String,
        regexRevision: Long,
    ): LedgerMessage = message.withDisplayRegex(
        config = config,
        characterId = characterId,
        regexRevision = regexRevision,
    )

    private fun LedgerMessage.withDisplayRegex(
        config: RegexRuleCollection,
        characterId: String,
        regexRevision: Long,
    ): LedgerMessage {
        val target = if (role == "user") RegexRuleTarget.UserInput else RegexRuleTarget.AiOutput
        val projection = displayRegexCache.project(
            characterId = characterId,
            regexRevision = regexRevision,
            messageId = id,
            target = target,
            content = content,
            reasoningContent = reasoningContent,
            variableStateJson = variableStateJson,
            completedAssistant = role == "assistant" && !pending,
        ) {
            DisplayRegexProjection(
                content = displayRegex(
                    text = content,
                    config = config,
                    target = target,
                    variableStateJson = variableStateJson,
                    completedAssistant = role == "assistant" && !pending,
                ),
                reasoningContent = displayRegex(
                    text = reasoningContent,
                    config = config,
                    target = RegexRuleTarget.Reasoning,
                    variableStateJson = variableStateJson,
                ),
            )
        }
        return copy(
            content = projection.content,
            reasoningContent = projection.reasoningContent,
        )
    }

    private fun displayRegex(
        text: String,
        config: RegexRuleCollection,
        target: RegexRuleTarget,
        variableStateJson: String,
        completedAssistant: Boolean = false,
    ): String {
        val rules = regexRules.rulesFor(config, target, RegexRuleSurface.Display)
        val compatibleSource = displayCompatibility.prepareAssistantText(
            text = text,
            complete = completedAssistant,
            displayRulePatterns = rules.map { rule -> rule.pattern },
        )
        val projected = RegexRuleProcessor.transform(
            text = compatibleSource,
            rules = rules,
            target = target,
            replacementDecorator = ::decorateRichDisplayReplacement,
            protectDecoratedReplacements = true,
        )
        return displayCompatibility.resolveVariableMacros(projected, variableStateJson)
    }

}

private data class ChatDraftProjectionContext(
    val selectedConfig: ModelConfig,
    val selectedModel: String,
    val selection: ChatModelSelection,
    val regexConfig: RegexRuleCollection,
    val regexRevision: Long,
    val settingLibrary: SettingLibrary,
    val openingMessages: List<SettingLibraryOpeningMessage>,
    val hasUserMessages: Boolean,
)

internal data class DisplayRegexProjection(
    val content: String,
    val reasoningContent: String,
)

/**
 * Shares one bounded display projection across the full-draft, Paging, and streaming paths.
 *
 * The synchronized miss path is intentional: without it those three paths can all start the same
 * expensive regex at once. Callers run cache misses away from the main thread.
 */
internal class DisplayRegexProjectionCache(
    private val maxEntries: Int = DefaultMaxEntries,
    private val maxRetainedCharacters: Long = DefaultMaxRetainedCharacters,
    private val maxEntryCharacters: Long = DefaultMaxEntryCharacters,
) {
    private data class Key(
        val characterId: String,
        val regexRevision: Long,
        val messageId: String,
        val target: RegexRuleTarget,
    )

    private data class Entry(
        val sourceContent: String,
        val sourceReasoningContent: String,
        val variableStateJson: String,
        val completedAssistant: Boolean,
        val projection: DisplayRegexProjection,
    ) {
        val retainedCharacters: Long =
            sourceContent.length.toLong() +
                sourceReasoningContent.length.toLong() +
                variableStateJson.length.toLong() +
                projection.content.length.toLong() +
                projection.reasoningContent.length.toLong()
    }

    private val entries = LinkedHashMap<Key, Entry>(maxEntries.coerceAtLeast(1), 0.75f, true)
    private var retainedCharacters = 0L

    fun clear() = synchronized(entries) {
        entries.clear()
        retainedCharacters = 0L
    }

    fun project(
        characterId: String,
        regexRevision: Long,
        messageId: String,
        target: RegexRuleTarget,
        content: String,
        reasoningContent: String,
        variableStateJson: String = "",
        completedAssistant: Boolean = false,
        transform: () -> DisplayRegexProjection,
    ): DisplayRegexProjection = synchronized(entries) {
        val key = Key(characterId, regexRevision, messageId, target)
        entries[key]
            ?.takeIf { entry ->
                entry.sourceContent == content &&
                    entry.sourceReasoningContent == reasoningContent &&
                    entry.variableStateJson == variableStateJson &&
                    entry.completedAssistant == completedAssistant
            }
            ?.projection
            ?: transform().also { projection ->
                val candidate = Entry(
                    sourceContent = content,
                    sourceReasoningContent = reasoningContent,
                    variableStateJson = variableStateJson,
                    completedAssistant = completedAssistant,
                    projection = projection,
                )
                if (
                    maxEntries > 0 &&
                    maxRetainedCharacters > 0L &&
                    maxEntryCharacters > 0L &&
                    candidate.retainedCharacters <= maxEntryCharacters &&
                    candidate.retainedCharacters <= maxRetainedCharacters
                ) {
                    entries.put(key, candidate)?.let { previous ->
                        retainedCharacters -= previous.retainedCharacters
                    }
                    retainedCharacters += candidate.retainedCharacters
                    trimToBudget()
                } else {
                    entries.remove(key)?.let { previous ->
                        retainedCharacters -= previous.retainedCharacters
                    }
                }
            }
    }

    private fun trimToBudget() {
        val iterator = entries.entries.iterator()
        while (
            iterator.hasNext() &&
            (entries.size > maxEntries || retainedCharacters > maxRetainedCharacters)
        ) {
            retainedCharacters -= iterator.next().value.retainedCharacters
            iterator.remove()
        }
    }

    private companion object {
        const val DefaultMaxEntries = 128
        const val DefaultMaxRetainedCharacters = 2L * 1024L * 1024L
        const val DefaultMaxEntryCharacters = 512L * 1024L
    }
}

/**
 * Projects a stable history prefix once while a streamed assistant tail keeps changing.
 *
 * The cache uses referential source identity deliberately: structural hashing/equality on a long
 * message list would itself rescan the conversation on every frame.
 */
internal class DisplayRegexMessageListProjector(
    private val maxStableLists: Int = 4,
) {
    private data class Context(
        val characterId: String,
        val regexRevision: Long,
    )

    private data class Entry(
        val source: List<ChatMessage>,
        val context: Context,
        val projected: List<ChatMessage>,
    )

    private val stableLists = ArrayDeque<Entry>()

    fun clear() = synchronized(stableLists) { stableLists.clear() }

    init {
        require(maxStableLists > 0) { "显示消息列表缓存容量必须大于 0" }
    }

    fun project(
        messages: List<ChatMessage>,
        characterId: String,
        regexRevision: Long,
        transform: (ChatMessage) -> ChatMessage,
    ): List<ChatMessage> = synchronized(stableLists) {
        val context = Context(characterId, regexRevision)
        @Suppress("UNCHECKED_CAST")
        val appended = messages as? ImmutableAppendedList<ChatMessage>
        if (appended != null) {
            ImmutableAppendedList(
                prefix = projectStable(appended.prefix, context, transform),
                tail = transform(appended.tail),
            )
        } else {
            projectStable(messages, context, transform)
        }
    }

    private fun projectStable(
        messages: List<ChatMessage>,
        context: Context,
        transform: (ChatMessage) -> ChatMessage,
    ): List<ChatMessage> {
        val iterator = stableLists.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.source === messages && entry.context == context) {
                iterator.remove()
                stableLists.addLast(entry)
                return entry.projected
            }
        }
        val projected = messages.map(transform)
        stableLists.addLast(Entry(messages, context, projected))
        while (stableLists.size > maxStableLists) stableLists.removeFirst()
        return projected
    }
}

