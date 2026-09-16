package com.eleckoi.android.feature.characters.modes.story.regex.ui

import com.eleckoi.android.feature.characters.modes.story.regex.api.RegexRuleService
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportDocument
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportResult
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegexRulesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun activePresetRevisionReloadsRulesForTheCurrentCharacter() = runTest(dispatcher) {
        val service = FakeRegexRuleService()
        val viewModel = RegexRulesViewModel(service, dispatcher)

        viewModel.load("character-a")
        advanceUntilIdle()

        service.collection = RegexRuleCollection(
            promptPresetRules = listOf(
                RegexRule(id = "imported-1", name = "导入规则"),
                RegexRule(id = "imported-2", name = "导入规则 2"),
            ),
        )
        service.regexRulesRevision.value += 1L
        advanceUntilIdle()

        assertEquals(
            listOf("imported-1", "imported-2"),
            viewModel.uiState.value.rules?.promptPresetRules?.map(RegexRule::id),
        )
        assertEquals(2, service.loadedCharacterIds.size)
        assertEquals("character-a", service.loadedCharacterIds.first())
        assertEquals("character-a", service.loadedCharacterIds.last())
    }

    @Test
    fun repeatedPresetChangesAlwaysReloadTheLatestRules() = runTest(dispatcher) {
        val service = FakeRegexRuleService()
        val viewModel = RegexRulesViewModel(service, dispatcher)
        viewModel.load("character-a")
        advanceUntilIdle()

        repeat(20) { index ->
            service.collection = RegexRuleCollection(
                promptPresetRules = listOf(RegexRule(id = "preset-rule-$index")),
            )
            service.regexRulesRevision.value += 1L
            advanceUntilIdle()
        }

        assertEquals(
            "preset-rule-19",
            viewModel.uiState.value.rules?.promptPresetRules?.single()?.id,
        )
        assertEquals(21, service.loadedCharacterIds.size)
    }
}

private class FakeRegexRuleService : RegexRuleService {
    override val regexRulesRevision = MutableStateFlow(0L)
    var collection = RegexRuleCollection()
    val loadedCharacterIds = mutableListOf<String>()

    override fun loadRegexRules(characterId: String): RegexRuleCollection {
        loadedCharacterIds += characterId
        return collection
    }

    override fun saveRegexRules(
        characterId: String,
        collection: RegexRuleCollection,
    ): RegexRuleCollection = collection

    override fun importRegexRules(
        characterId: String,
        scope: RegexRuleScope,
        documents: List<RegexRuleImportDocument>,
    ): RegexRuleImportResult = RegexRuleImportResult(collection, 0, 0)

    override fun exportRegexRules(characterId: String, ruleIds: Set<String>): String = "{}"
}
