package com.eleckoi.android.feature.characters.modes.story.regex.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.feature.characters.modes.story.regex.api.RegexRuleService
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleCollection
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleImportDocument
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRuleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RegexRulesUiState(
    val characterId: String = "",
    val rules: RegexRuleCollection? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val errorMessage: String = "",
)

sealed interface RegexRulesEffect {
    data class RulesExportReady(val json: String, val fileName: String) : RegexRulesEffect
    data class RulesImported(val message: String) : RegexRulesEffect
}

class RegexRulesViewModel(
    private val service: RegexRuleService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegexRulesUiState())
    val uiState: StateFlow<RegexRulesUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<RegexRulesEffect>()
    val effects: SharedFlow<RegexRulesEffect> = _effects.asSharedFlow()
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            service.regexRulesRevision.drop(1).collect {
                val characterId = _uiState.value.characterId
                if (characterId.isNotBlank()) refresh(characterId, showLoading = false)
            }
        }
    }

    fun load(characterId: String) {
        if (characterId.isBlank()) return
        viewModelScope.launch { refresh(characterId, showLoading = true) }
    }

    fun save(characterId: String, rules: RegexRuleCollection) {
        if (characterId.isBlank()) return
        _uiState.update {
            it.copy(characterId = characterId, rules = rules, saving = true, errorMessage = "")
        }
        viewModelScope.launch {
            runCatching {
                saveMutex.withLock {
                    withContext(ioDispatcher) { service.saveRegexRules(characterId, rules) }
                }
            }.onSuccess { saved ->
                _uiState.update { current ->
                    if (current.characterId == characterId && current.rules == rules) {
                        current.copy(rules = saved, saving = false)
                    } else {
                        current
                    }
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    if (current.characterId == characterId && current.rules == rules) {
                        current.copy(saving = false, errorMessage = error.message ?: "正则规则保存失败")
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun importRules(
        characterId: String,
        scope: RegexRuleScope,
        documents: List<RegexRuleImportDocument>,
    ) {
        if (characterId.isBlank() || documents.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(characterId = characterId, saving = true, errorMessage = "") }
            runCatching {
                saveMutex.withLock {
                    withContext(ioDispatcher) { service.importRegexRules(characterId, scope, documents) }
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(characterId = characterId, rules = result.collection, saving = false, errorMessage = "")
                }
                val failureSuffix = result.failedFileNames.takeIf(List<String>::isNotEmpty)?.let { failures ->
                    val names = failures.take(2).joinToString("、")
                    val remaining = (failures.size - 2).coerceAtLeast(0)
                    "；${failures.size} 个文件无法识别：$names${if (remaining > 0) " 等" else ""}"
                }.orEmpty()
                _effects.emit(
                    RegexRulesEffect.RulesImported(
                        "已从 ${result.importedFileCount} 个文件导入 ${result.importedRuleCount} 条正则" +
                            failureSuffix,
                    ),
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(saving = false, errorMessage = error.message ?: "导入正则失败")
                }
            }
        }
    }

    fun exportRules(characterId: String, ruleIds: Set<String>) {
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { service.exportRegexRules(characterId, ruleIds) } }
                .onSuccess { json ->
                    _effects.emit(RegexRulesEffect.RulesExportReady(json, "eleckoi-regex-rules.json"))
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "导出规则失败") } }
        }
    }

    private suspend fun refresh(
        characterId: String,
        showLoading: Boolean,
    ) {
        _uiState.update {
            it.copy(characterId = characterId, loading = showLoading, errorMessage = "")
        }
        runCatching {
            saveMutex.withLock {
                withContext(ioDispatcher) { service.loadRegexRules(characterId) }
            }
        }.onSuccess { rules ->
            _uiState.update { current ->
                if (current.characterId == characterId) {
                    current.copy(rules = rules, loading = false)
                } else {
                    current
                }
            }
        }.onFailure { error ->
            _uiState.update { current ->
                if (current.characterId == characterId) {
                    current.copy(loading = false, errorMessage = error.message ?: "正则规则加载失败")
                } else {
                    current
                }
            }
        }
    }

    companion object {
        fun factory(service: RegexRuleService): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(RegexRulesViewModel::class.java)) {
                    return RegexRulesViewModel(service) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
