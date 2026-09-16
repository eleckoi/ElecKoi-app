package com.eleckoi.android.app.service

import com.eleckoi.android.feature.characters.modes.story.regex.api.RegexRuleService
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportDocument
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportResult
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import kotlinx.coroutines.flow.StateFlow

internal class RegexRuleServiceImpl(
    private val rules: RegexRuleRepository,
) : RegexRuleService {
    override val regexRulesRevision: StateFlow<Long> = rules.revision

    override fun loadRegexRules(characterId: String): RegexRuleCollection = rules.load(characterId)

    override fun saveRegexRules(characterId: String, collection: RegexRuleCollection): RegexRuleCollection {
        return rules.save(characterId, collection)
    }

    override fun importRegexRules(
        characterId: String,
        scope: RegexRuleScope,
        documents: List<RegexRuleImportDocument>,
    ): RegexRuleImportResult {
        return rules.importRules(characterId, scope, documents)
    }

    override fun exportRegexRules(characterId: String, ruleIds: Set<String>): String {
        return rules.exportRules(characterId, ruleIds)
    }
}
