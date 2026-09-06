package com.eleckoi.android.feature.characters.modes.story.presets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.feature.characters.modes.story.presets.data.StoryPresetRepository
import com.eleckoi.android.feature.characters.modes.story.presets.data.StoryPresetImportCodec
import com.eleckoi.android.feature.characters.modes.story.presets.data.StoryPresetCardExporter
import com.eleckoi.android.feature.characters.modes.story.presets.model.ExportedStoryPresetCard
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPreset
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetCatalog
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetImportDocument
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetImportSource
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetModelFamily
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetModelTag
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetProfile
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.modes.story.presets.model.defaultStoryPresetCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class StoryPresetUiState(
    val catalog: StoryPresetCatalog = defaultStoryPresetCatalog(),
    val activePreset: StoryPreset? = null,
    val editorPreset: StoryPreset? = null,
    val editorEntryId: String? = null,
    val returnToCallerAfterEntry: Boolean = false,
    val loadingEditor: Boolean = false,
    val importMessage: String = "",
    val exporting: Boolean = false,
    val exportedCards: List<ExportedStoryPresetCard> = emptyList(),
)

class StoryPresetViewModel(
    private val repository: StoryPresetRepository,
) : ViewModel() {
    private val updateMutex = Mutex()
    private val _uiState = MutableStateFlow(StoryPresetUiState())
    val uiState: StateFlow<StoryPresetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureInitialized()
            repository.catalog.collect { catalog ->
                val activePreset = repository.preset(catalog.activePresetId)
                _uiState.update { it.copy(catalog = catalog, activePreset = activePreset) }
            }
        }
    }

    fun openPreset(presetId: String) {
        _uiState.update {
            it.copy(
                editorEntryId = null,
                returnToCallerAfterEntry = false,
                loadingEditor = true,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val preset = repository.preset(presetId)
            _uiState.update { it.copy(editorPreset = preset, loadingEditor = false) }
        }
    }

    fun openPresetEntry(presetId: String, entryId: String) {
        _uiState.update {
            it.copy(
                editorEntryId = entryId,
                returnToCallerAfterEntry = true,
                loadingEditor = true,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val preset = repository.preset(presetId)
            _uiState.update { state ->
                state.copy(
                    editorPreset = preset,
                    editorEntryId = entryId.takeIf { target -> preset?.entries?.any { it.id == target } == true },
                    loadingEditor = false,
                )
            }
        }
    }

    fun editorEntryOpened() {
        _uiState.update { it.copy(editorEntryId = null) }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                editorPreset = null,
                editorEntryId = null,
                returnToCallerAfterEntry = false,
                loadingEditor = false,
            )
        }
    }

    fun setActive(presetId: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.setActive(presetId) }
    }

    fun create(
        name: String,
        modelTags: List<StoryPresetModelTag>,
        libraryGroupId: String = "",
    ) {
        _uiState.update { it.copy(loadingEditor = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val preset = repository.create(name, modelTags, libraryGroupId)
            _uiState.update { it.copy(editorPreset = preset, loadingEditor = false) }
        }
    }

    fun update(preset: StoryPreset) {
        val previous = _uiState.value.let { state ->
            state.editorPreset?.takeIf { it.id == preset.id }
                ?: state.activePreset?.takeIf { it.id == preset.id }
        }
        _uiState.update { state ->
            state.copy(
                editorPreset = if (state.editorPreset?.id == preset.id) preset else state.editorPreset,
                activePreset = if (state.catalog.activePresetId == preset.id) preset else state.activePreset,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            updateMutex.withLock { repository.update(previous, preset) }
        }
    }

    fun rename(presetId: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.rename(presetId, name) }
    }

    fun duplicate(presetId: String) {
        _uiState.update { it.copy(loadingEditor = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val preset = repository.duplicate(presetId)
            _uiState.update { it.copy(editorPreset = preset, loadingEditor = false) }
        }
    }

    fun importPresets(
        documents: List<StoryPresetImportDocument>,
        source: StoryPresetImportSource,
    ) {
        if (documents.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var importedCount = 0
            var importedEntryCount = 0
            var importedRegexCount = 0
            var skippedCount = 0
            var skippedDepthRegexCount = 0
            val failures = mutableListOf<String>()
            documents.forEach { document ->
                runCatching {
                    val conversion = StoryPresetImportCodec.decode(document, source)
                    repository.importPreset(conversion.preset, conversion.authorAvatarPng)
                    importedCount += 1
                    importedEntryCount += conversion.preset.entries.size
                    importedRegexCount += conversion.preset.regexRules.size
                    skippedCount += conversion.skippedUnsupportedEntries
                    skippedDepthRegexCount += conversion.skippedDepthRegexCount
                }.onFailure { error ->
                    failures += "${document.fileName}: ${error.message ?: "导入失败"}"
                }
            }
            val message = when {
                importedCount == 0 -> failures.firstOrNull() ?: "预设导入失败"
                else -> buildString {
                    append("已导入 $importedCount 个预设，共 $importedEntryCount 个条目")
                    if (importedRegexCount > 0) append("、$importedRegexCount 条正则")
                    if (skippedCount > 0) append("，忽略 $skippedCount 个酒馆动态占位")
                    if (skippedDepthRegexCount > 0) {
                        append("，跳过 $skippedDepthRegexCount 条不支持的深度正则")
                    }
                    if (failures.isNotEmpty()) append("；${failures.size} 个文件失败")
                }
            }
            _uiState.update { it.copy(importMessage = message) }
        }
    }

    fun importMessageShown() {
        _uiState.update { it.copy(importMessage = "") }
    }

    fun exportPresets(presetIds: Collection<String>) {
        val requestedIds = presetIds.filter(String::isNotBlank).distinct()
        if (requestedIds.isEmpty() || _uiState.value.exporting) return
        if (requestedIds.size > MaxPresetExportCount) {
            _uiState.update { it.copy(importMessage = "一次最多导出 $MaxPresetExportCount 个预设") }
            return
        }
        _uiState.update { it.copy(exporting = true, exportedCards = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cards = mutableListOf<ExportedStoryPresetCard>()
                requestedIds.forEach { presetId ->
                    repository.preset(presetId)?.let { preset ->
                        cards += StoryPresetCardExporter.export(preset)
                    }
                }
                cards.also { require(it.isNotEmpty()) { "找不到要导出的预设" } }
            }.onSuccess { cards ->
                _uiState.update { it.copy(exporting = false, exportedCards = cards) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        exporting = false,
                        importMessage = error.message ?: "生成预设卡失败",
                    )
                }
            }
        }
    }

    fun dismissPresetExport() {
        _uiState.update { it.copy(exportedCards = emptyList()) }
    }

    fun createLibraryGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.createLibraryGroup(name) }
    }

    fun renameLibraryGroup(groupId: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.renameLibraryGroup(groupId, name) }
    }

    fun updateProfile(presetId: String, profile: StoryPresetProfile) {
        viewModelScope.launch(Dispatchers.IO) { repository.updateProfile(presetId, profile) }
    }

    fun updateModelTags(presetId: String, modelTags: List<StoryPresetModelTag>) {
        viewModelScope.launch(Dispatchers.IO) { repository.updateModelTags(presetId, modelTags) }
    }

    fun updateAuthorAvatar(presetId: String, files: Map<AvatarSlot, File>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                files[AvatarSlot.Circle]?.let { repository.updateAuthorAvatar(presetId, it) }
            } finally {
                files.values.forEach(File::delete)
            }
        }
    }

    fun deleteLibraryGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteLibraryGroup(groupId) }
    }

    fun moveToLibraryGroup(presetId: String, groupId: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.moveToLibraryGroup(presetId, groupId) }
    }

    fun delete(presetId: String) {
        if (_uiState.value.editorPreset?.id == presetId) closeEditor()
        viewModelScope.launch(Dispatchers.IO) { repository.delete(presetId) }
    }

    companion object {
        fun factory(repository: StoryPresetRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(StoryPresetViewModel::class.java)) {
                        return StoryPresetViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

private const val MaxPresetExportCount = 20
