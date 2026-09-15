package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryKeywordCondition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHiddenToolTimelineEntry
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.roleplay.protocol.augmentRoleplayOutputProtocolForImage

internal object CharacterSettingContextResolver {
    fun resolve(
        library: SettingLibrary?,
        messages: List<ChatMessage>,
        imageActionEnabled: Boolean = false,
    ): List<AgentContextInjection> {
        val promptPositions = library?.promptPositions.orEmpty().associateBy { it.id }
        val candidates = library?.entries.orEmpty().filter { entry ->
            !entry.isFixedEntry() &&
                entry.enabled &&
                entry.content.isNotBlank() &&
                entry.position != null &&
                entry.triggerMode == SettingLibraryTriggerMode.Always
        }
        if (candidates.isEmpty()) return emptyList()

        return candidates.asSequence()
            .sortedWith(
                compareBy<SettingLibraryEntry> { entry ->
                    promptPositions[entry.promptPositionId]?.anchor?.ordinal ?: entry.position!!.ordinal
                }
                    .thenBy { entry -> promptPositions[entry.promptPositionId]?.order ?: Int.MIN_VALUE }
                    .thenBy(SettingLibraryEntry::order)
                    .thenBy(SettingLibraryEntry::id),
            )
            .take(MaxInjectionCount)
            .mapIndexed { runtimeOrder, entry ->
                val runtimePosition = promptPositions[entry.promptPositionId]?.anchor ?: entry.position!!
                AgentContextInjection(
                    id = entry.id.take(MaxIdLength),
                    anchor = runtimePosition.toAgentAnchor(),
                    role = entry.insertRole.toAgentRole(runtimePosition),
                    activation = AgentContextActivation.Immediate,
                    content = if (imageActionEnabled && entry.isHiddenToolTimelineEntry()) {
                        augmentRoleplayOutputProtocolForImage(entry.content)
                    } else {
                        entry.content
                    }.take(MaxEntryCharacters),
                    // Entry order is local to its configured position. Several product positions
                    // can share one provider boundary, so carry the fully resolved placement order
                    // into the runtime instead of sorting those local order values against each other.
                    order = runtimeOrder + 1,
                )
            }
            .toList()
    }

    internal fun SettingLibraryEntry.matchesKeywords(messages: List<ChatMessage>): Boolean {
        return matchesKeywordText(messages.recentKeywordText(keywordScanDepth))
    }

    /**
     * Finds direct conversation matches, then follows only the author-enabled association rounds.
     * A round uses one matched entry's body as the next search text; already matched entries are
     * never reconsidered, so cycles cannot make a setting reappear forever.
     */
    internal fun matchingKeywordEntryIds(
        entries: List<SettingLibraryEntry>,
        messages: List<ChatMessage>,
    ): Set<String> {
        val directMatches = entries.filter { it.matchesKeywords(messages) }
        val matchedIds = directMatches.mapTo(linkedSetOf()) { it.id }
        var frontier = directMatches.associateWith { it.keywordRecursionDepth.coerceAtLeast(0) }

        while (frontier.isNotEmpty()) {
            val nextFrontier = linkedMapOf<SettingLibraryEntry, Int>()
            frontier.forEach { (source, remainingRounds) ->
                if (remainingRounds <= 0 || source.content.isBlank()) return@forEach
                entries.asSequence()
                    .filter { candidate -> candidate.id !in matchedIds }
                    .filter { candidate -> candidate.matchesKeywordText(source.content) }
                    .forEach { candidate ->
                        matchedIds += candidate.id
                        nextFrontier[candidate] = maxOf(
                            remainingRounds - 1,
                            candidate.keywordRecursionDepth.coerceAtLeast(0),
                        )
                    }
            }
            frontier = nextFrontier
        }
        return matchedIds
    }

    private fun List<ChatMessage>.recentKeywordText(scanDepth: Int): String {
        return asSequence()
            .filter { it.role == MessageRole.User || it.role == MessageRole.Assistant }
            .map(ChatMessage::content)
            .filter(String::isNotBlank)
            .toList()
            .takeLast(scanDepth.coerceAtLeast(1))
            .joinToString("\n")
    }

    private fun SettingLibraryEntry.matchesKeywordText(text: String): Boolean {
        if (keywords.isEmpty() || keywords.none { text.containsKeyword(it, this) }) return false
        val secondaryMatches = conditionKeywords.map { text.containsKeyword(it, this) }
        val effectiveCondition = when {
            conditionKeywords.isEmpty() -> SettingLibraryKeywordCondition.None
            keywordCondition == SettingLibraryKeywordCondition.None -> SettingLibraryKeywordCondition.Any
            else -> keywordCondition
        }
        return when (effectiveCondition) {
            SettingLibraryKeywordCondition.None -> true
            SettingLibraryKeywordCondition.Any -> secondaryMatches.any { it }
            SettingLibraryKeywordCondition.All -> secondaryMatches.isNotEmpty() && secondaryMatches.all { it }
            SettingLibraryKeywordCondition.NotAny -> secondaryMatches.none { it }
        }
    }

    private fun String.containsKeyword(keyword: String, entry: SettingLibraryEntry): Boolean {
        val needle = keyword.trim()
        if (needle.isEmpty()) return false
        if (entry.keywordUseRegex) {
            return compileTavernKeywordRegex(needle, entry.keywordIgnoreCase)
                ?.containsMatchIn(this) == true
        }
        if (!entry.keywordWholeWord || needle.any(::isCjk)) {
            return contains(needle, ignoreCase = entry.keywordIgnoreCase)
        }
        val options = if (entry.keywordIgnoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(needle)}(?![\\p{L}\\p{N}_])", options)
            .containsMatchIn(this)
    }

    /**
     * SillyTavern accepts both JavaScript-style `/pattern/flags` values and bare patterns.
     * Kotlin's engine is not identical to JavaScript's, so unsupported syntax simply does not
     * match; one malformed world-book key must never break an Agent context lookup.
     */
    private fun compileTavernKeywordRegex(value: String, ignoreCase: Boolean): Regex? {
        val delimited = value.startsWith('/') && value.lastUnescapedSlash() > 0
        val slash = if (delimited) value.lastUnescapedSlash() else -1
        val body = if (delimited) value.substring(1, slash) else value
        val flags = if (delimited) value.substring(slash + 1) else ""
        if (flags.any { it !in "gimsuy" }) return null
        val options = buildSet {
            if (ignoreCase || 'i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        return runCatching { Regex(body, options) }.getOrNull()
    }

    private fun String.lastUnescapedSlash(): Int {
        for (index in lastIndex downTo 1) {
            if (this[index] != '/') continue
            var escapes = 0
            var cursor = index - 1
            while (cursor >= 0 && this[cursor] == '\\') {
                escapes += 1
                cursor -= 1
            }
            if (escapes % 2 == 0) return index
        }
        return -1
    }

    private fun isCjk(char: Char): Boolean = char.code in 0x3400..0x9FFF

    private fun SettingLibraryPosition.toAgentAnchor(): AgentContextAnchor = when (this) {
        SettingLibraryPosition.Instructions -> AgentContextAnchor.Instructions
        SettingLibraryPosition.AfterInstructions -> AgentContextAnchor.BeforeToolContext
        SettingLibraryPosition.BeforeHistory -> AgentContextAnchor.BeforeHistory
        SettingLibraryPosition.AfterHistory -> AgentContextAnchor.AfterHistory
        SettingLibraryPosition.BeforeLatestUserInput -> AgentContextAnchor.BeforeLatestUserInput
        SettingLibraryPosition.AfterLatestUserInput -> AgentContextAnchor.AfterLatestUserInput
        SettingLibraryPosition.BeforeToolFlow -> AgentContextAnchor.BeforeToolFlow
        SettingLibraryPosition.AfterToolFlow -> AgentContextAnchor.AfterToolFlow
    }

    private fun SettingLibraryInsertRole.toAgentRole(position: SettingLibraryPosition): AgentContextRole = when {
        position == SettingLibraryPosition.Instructions -> AgentContextRole.System
        this == SettingLibraryInsertRole.Assistant -> AgentContextRole.Assistant
        else -> AgentContextRole.User
    }

    private const val MaxInjectionCount = 128
    private const val MaxIdLength = 128
    private const val MaxEntryCharacters = 40_000
}
