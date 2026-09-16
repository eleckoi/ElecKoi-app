package com.eleckoi.android.feature.characters.modes.story.regex.api

import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportDocument
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportResult
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import kotlinx.coroutines.flow.StateFlow

interface RegexRuleService {
    /** Emits after any persisted regex source changes, including the active preset. */
    val regexRulesRevision: StateFlow<Long>

    fun loadRegexRules(characterId: String): RegexRuleCollection
    fun saveRegexRules(characterId: String, collection: RegexRuleCollection): RegexRuleCollection
    fun importRegexRules(
        characterId: String,
        scope: RegexRuleScope,
        documents: List<RegexRuleImportDocument>,
    ): RegexRuleImportResult
    fun exportRegexRules(characterId: String, ruleIds: Set<String>): String
}
