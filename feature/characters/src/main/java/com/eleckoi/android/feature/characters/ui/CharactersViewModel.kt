package com.eleckoi.android.feature.characters.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.feature.characters.api.CharacterService
import com.eleckoi.android.feature.characters.transfer.api.CharacterTransferService
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportPreview
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportSource
import com.eleckoi.android.feature.characters.transfer.model.CharacterExportFormat
import com.eleckoi.android.feature.characters.transfer.model.ExportedCharacterCard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CharactersUiState(
    val characters: CharactersPayload? = null,
    val saving: Boolean = false,
    val errorMessage: String = "",
    val transferBusy: Boolean = false,
    val importPreview: CharacterImportPreview? = null,
    val characterDraft: CharacterSlot? = null,
    val pendingExportCharacterIds: List<String> = emptyList(),
    val exportedCard: ExportedCharacterCard? = null,
    val exportedCards: List<ExportedCharacterCard> = emptyList(),
)

sealed interface CharactersIntent {
    data class CreateCharacter(val group: String) : CharactersIntent
    data class CreateCharacterGroup(val name: String) : CharactersIntent
    data class SelectCharacter(val characterId: String) : CharactersIntent
    data object ToggleAllCharactersExpanded : CharactersIntent
    data class ToggleCharacterGroupExpanded(val name: String) : CharactersIntent
    data class SaveCharacterCollection(val characters: CharactersPayload) : CharactersIntent
    data class DeleteCharacters(val characterIds: List<String>) : CharactersIntent
    data class ImportCharacters(val json: String) : CharactersIntent
    data object ExportCharacters : CharactersIntent
    data class PrepareCharacterImport(
        val files: List<File>,
        val source: CharacterImportSource,
    ) : CharactersIntent
    data object ConfirmCharacterImport : CharactersIntent
    data object DismissCharacterImport : CharactersIntent
    data class PrepareCharacterExport(val characterId: String) : CharactersIntent
    data class ExportCharacterCards(val characterIds: List<String>) : CharactersIntent
    data class ConfirmCharacterCardExport(val format: CharacterExportFormat) : CharactersIntent
    data object DismissCharacterExportFormat : CharactersIntent
    data object DismissCharacterExport : CharactersIntent
    data object DismissCharacterCardsExport : CharactersIntent
    data class SaveCharacterPersona(val characterId: String, val persona: CharacterCard) : CharactersIntent
    data class SaveCharacterAvatars(
        val characterId: String,
        val files: Map<AvatarSlot, File>,
    ) : CharactersIntent
    data class ClearCharacterAvatarSlot(
        val characterId: String,
        val slot: AvatarSlot,
    ) : CharactersIntent
    data class SaveCharacterCover(val characterId: String, val coverFile: File) : CharactersIntent
}

sealed interface CharactersEffect {
    data class OpenCharacterSettings(val characterId: String) : CharactersEffect
    data class OpenCharacterDraft(val characterId: String) : CharactersEffect
    data class CharactersDeleted(val characterIds: List<String>) : CharactersEffect
    data object CharacterCreated : CharactersEffect
    data object CharactersChanged : CharactersEffect
    data class ExportReady(val json: String) : CharactersEffect
    data class CharactersImported(val characterIds: List<String>) : CharactersEffect
}

class CharactersViewModel(
    private val characterService: CharacterService,
    private val characterTransferService: CharacterTransferService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharactersUiState())
    val uiState: StateFlow<CharactersUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<CharactersEffect>()
    val effects: SharedFlow<CharactersEffect> = _effects.asSharedFlow()
    private val collectionMutationMutex = Mutex()

    init {
        observeCharacters()
    }

    fun onIntent(intent: CharactersIntent) {
        when (intent) {
            is CharactersIntent.CreateCharacter -> createCharacter(intent.group)
            is CharactersIntent.CreateCharacterGroup -> createCharacterGroup(intent.name)
            is CharactersIntent.SelectCharacter -> selectCharacter(intent.characterId)
            CharactersIntent.ToggleAllCharactersExpanded -> toggleAllCharactersExpanded()
            is CharactersIntent.ToggleCharacterGroupExpanded -> toggleCharacterGroupExpanded(intent.name)
            is CharactersIntent.SaveCharacterCollection -> saveCharacterCollection(intent.characters)
            is CharactersIntent.DeleteCharacters -> deleteCharacters(intent.characterIds)
            is CharactersIntent.ImportCharacters -> importCharacters(intent.json)
            CharactersIntent.ExportCharacters -> exportCharacters()
            is CharactersIntent.PrepareCharacterImport -> prepareCharacterImport(intent.files, intent.source)
            CharactersIntent.ConfirmCharacterImport -> confirmCharacterImport()
            CharactersIntent.DismissCharacterImport -> dismissCharacterImport()
            is CharactersIntent.PrepareCharacterExport -> requestCharacterExport(listOf(intent.characterId))
            is CharactersIntent.ExportCharacterCards -> requestCharacterExport(intent.characterIds)
            is CharactersIntent.ConfirmCharacterCardExport -> exportCharacterCards(intent.format)
            CharactersIntent.DismissCharacterExportFormat -> {
                _uiState.update { it.copy(pendingExportCharacterIds = emptyList()) }
            }
            CharactersIntent.DismissCharacterExport -> _uiState.update { it.copy(exportedCard = null) }
            CharactersIntent.DismissCharacterCardsExport -> _uiState.update { it.copy(exportedCards = emptyList()) }
            is CharactersIntent.SaveCharacterPersona -> saveCharacterPersona(intent.characterId, intent.persona)
            is CharactersIntent.SaveCharacterAvatars -> saveCharacterAvatars(
                intent.characterId,
                intent.files,
            )
            is CharactersIntent.ClearCharacterAvatarSlot -> clearCharacterAvatarSlot(
                intent.characterId,
                intent.slot,
            )
            is CharactersIntent.SaveCharacterCover -> saveCharacterCover(intent.characterId, intent.coverFile)
        }
    }

    private fun observeCharacters() {
        viewModelScope.launch {
            characterService.characterCollectionFlow
                .catch { error -> _uiState.update { it.copy(errorMessage = error.message ?: "加载角色失败") } }
                .collectLatest { characters ->
                    _uiState.update { it.copy(characters = characters, errorMessage = "") }
                }
        }
    }

    fun createCharacter(group: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { characterService.createCharacterDraft(group) }
            }.onSuccess { draft ->
                _uiState.update { it.copy(characterDraft = draft, errorMessage = "") }
                _effects.emit(CharactersEffect.OpenCharacterDraft(draft.id))
            }.onFailure { error ->
                Log.e(LogTag, "Failed to create character", error)
                _uiState.update { it.copy(errorMessage = error.message ?: "新建角色失败") }
            }
        }
    }

    private fun createCharacterGroup(name: String) {
        viewModelScope.launch {
            collectionMutationMutex.withLock {
                runCatching {
                    withContext(Dispatchers.IO) { characterService.createCharacterGroup(name) }
                }.onSuccess { saved ->
                    _uiState.update { it.copy(characters = saved, errorMessage = "") }
                }.onFailure { error ->
                    Log.e(LogTag, "Failed to create character group", error)
                    _uiState.update { it.copy(errorMessage = error.message ?: "新建分组失败") }
                }
            }
        }
    }

    private fun toggleAllCharactersExpanded() {
        viewModelScope.launch {
            collectionMutationMutex.withLock {
                runCatching {
                    withContext(Dispatchers.IO) { characterService.toggleAllCharactersExpanded() }
                }.onSuccess { saved ->
                    _uiState.update { it.copy(characters = saved, errorMessage = "") }
                }.onFailure { error ->
                    Log.e(LogTag, "Failed to toggle all characters", error)
                    _uiState.update { it.copy(errorMessage = error.message ?: "切换角色列表失败") }
                }
            }
        }
    }

    private fun toggleCharacterGroupExpanded(name: String) {
        viewModelScope.launch {
            collectionMutationMutex.withLock {
                runCatching {
                    withContext(Dispatchers.IO) {
                        characterService.toggleCharacterGroupExpanded(name)
                    }
                }.onSuccess { saved ->
                    _uiState.update { it.copy(characters = saved, errorMessage = "") }
                }.onFailure { error ->
                    Log.e(LogTag, "Failed to toggle character group", error)
                    _uiState.update { it.copy(errorMessage = error.message ?: "切换分组失败") }
                }
            }
        }
    }

    fun selectCharacter(characterId: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { characterService.selectCharacter(characterId) }
            }.onSuccess {
                _uiState.update { it.copy(errorMessage = "") }
                _effects.emit(CharactersEffect.OpenCharacterSettings(characterId))
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "打开角色失败") }
            }
        }
    }

    fun saveCharacterCollection(characters: CharactersPayload) {
        viewModelScope.launch {
            collectionMutationMutex.withLock {
                runCatching {
                    withContext(Dispatchers.IO) { characterService.saveCharacterCollection(characters) }
                }.onSuccess { saved ->
                    _uiState.update { it.copy(characters = saved, errorMessage = "") }
                }.onFailure { error ->
                    Log.e(LogTag, "Failed to save character collection", error)
                    _uiState.update { it.copy(errorMessage = error.message ?: "保存角色列表失败") }
                }
            }
        }
    }

    fun deleteCharacters(characterIds: List<String>) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { characterService.deleteCharacters(characterIds) }
            }.onSuccess {
                _uiState.update { it.copy(errorMessage = "") }
                _effects.emit(CharactersEffect.CharactersDeleted(characterIds))
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "删除角色失败") }
            }
        }
    }

    fun importCharacters(json: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { characterService.importCharacters(json) }
            }.onSuccess {
                _uiState.update { it.copy(errorMessage = "") }
                _effects.emit(CharactersEffect.CharactersChanged)
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "导入角色失败") }
            }
        }
    }

    fun exportCharacters() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { characterService.exportCharacters() }
            }.onSuccess { json ->
                _uiState.update { it.copy(errorMessage = "") }
                _effects.emit(CharactersEffect.ExportReady(json))
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "导出角色失败") }
            }
        }
    }

    private fun prepareCharacterImport(files: List<File>, source: CharacterImportSource) {
        viewModelScope.launch {
            _uiState.update { it.copy(transferBusy = true, importPreview = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    characterTransferService.prepareCharacterImports(files, source)
                }
            }.onSuccess { preview ->
                _uiState.update {
                    it.copy(transferBusy = false, importPreview = preview, errorMessage = "")
                }
            }.onFailure { error ->
                Log.e(LogTag, "Failed to prepare character import", error)
                _uiState.update {
                    it.copy(
                        transferBusy = false,
                        errorMessage = error.message ?: "读取角色卡失败",
                    )
                }
            }
        }
    }

    private fun confirmCharacterImport() {
        val token = _uiState.value.importPreview?.token ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(transferBusy = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    characterTransferService.importPreparedCharacters(token)
                }
            }.onSuccess { characters ->
                _uiState.update {
                    it.copy(
                        transferBusy = false,
                        importPreview = null,
                        errorMessage = "",
                    )
                }
                _effects.emit(CharactersEffect.CharactersImported(characters.map { it.id }))
            }.onFailure { error ->
                Log.e(LogTag, "Failed to import character card", error)
                _uiState.update {
                    it.copy(
                        transferBusy = false,
                        errorMessage = error.message ?: "导入角色失败",
                    )
                }
            }
        }
    }

    private fun dismissCharacterImport() {
        val token = _uiState.value.importPreview?.token
        _uiState.update { it.copy(importPreview = null) }
        if (token != null) {
            viewModelScope.launch(Dispatchers.IO) {
                characterTransferService.discardPreparedCharacter(token)
            }
        }
    }

    private fun requestCharacterExport(characterIds: List<String>) {
        val ids = characterIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return
        _uiState.update {
            it.copy(
                pendingExportCharacterIds = ids,
                exportedCard = null,
                exportedCards = emptyList(),
                errorMessage = "",
            )
        }
    }

    fun commitCharacterDraft(
        persona: CharacterCard,
        avatarFiles: Map<AvatarSlot, File>,
        onResult: (Result<CharacterSlot>) -> Unit = {},
    ) {
        val draft = _uiState.value.characterDraft ?: run {
            onResult(Result.failure(IllegalStateException("角色草稿不存在")))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorMessage = "") }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    characterService.createCharacter(draft.copy(persona = persona), avatarFiles)
                }
            }
            result.onSuccess {
                withContext(Dispatchers.IO) {
                    avatarFiles.values.forEach { file -> file.delete() }
                }
                _uiState.update { state ->
                    state.copy(characterDraft = null, saving = false, errorMessage = "")
                }
                _effects.emit(CharactersEffect.CharacterCreated)
            }.onFailure { error ->
                Log.e(LogTag, "Failed to commit character draft", error)
                _uiState.update {
                    it.copy(
                        saving = false,
                        errorMessage = error.message ?: "创建角色失败",
                    )
                }
            }
            onResult(result)
        }
    }

    fun discardCharacterDraft(characterId: String) {
        _uiState.update { state ->
            if (state.characterDraft?.id == characterId) state.copy(characterDraft = null) else state
        }
    }

    private fun exportCharacterCards(format: CharacterExportFormat) {
        val characterIds = _uiState.value.pendingExportCharacterIds
        if (characterIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    transferBusy = true,
                    pendingExportCharacterIds = emptyList(),
                    exportedCard = null,
                    exportedCards = emptyList(),
                    errorMessage = "",
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    characterTransferService.exportCharacterCards(characterIds, format)
                }
            }.onSuccess { cards ->
                _uiState.update {
                    it.copy(
                        transferBusy = false,
                        exportedCard = cards.singleOrNull(),
                        exportedCards = cards.takeIf { it.size > 1 }.orEmpty(),
                        errorMessage = "",
                    )
                }
            }.onFailure { error ->
                Log.e(LogTag, "Failed to export character cards", error)
                _uiState.update {
                    it.copy(
                        transferBusy = false,
                        errorMessage = error.message ?: "导出角色失败",
                    )
                }
            }
        }
    }

    fun saveCharacterPersona(
        characterId: String,
        persona: CharacterCard,
        onResult: (Result<CharacterSlot>) -> Unit = {},
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) { characterService.saveCharacterPersona(characterId, persona) }
            }
            result.onSuccess {
                _uiState.update { it.copy(saving = false, errorMessage = "") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        saving = false,
                        errorMessage = error.message ?: "保存角色失败",
                    )
                }
            }
            onResult(result)
        }
    }

    fun saveCharacterAvatars(
        characterId: String,
        files: Map<AvatarSlot, File>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    characterService.saveCharacterAvatars(characterId, files)
                }
            }
            withContext(Dispatchers.IO) {
                files.values.forEach { it.delete() }
            }
            result.onSuccess {
                _uiState.update { it.copy(saving = false, errorMessage = "") }
                _effects.emit(CharactersEffect.CharactersChanged)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        saving = false,
                        errorMessage = error.message ?: "保存角色头像失败",
                    )
                }
            }
        }
    }

    fun clearCharacterAvatarSlot(characterId: String, slot: AvatarSlot) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    characterService.clearCharacterAvatarSlots(characterId, setOf(slot))
                }
            }.onSuccess {
                _uiState.update { it.copy(saving = false, errorMessage = "") }
                _effects.emit(CharactersEffect.CharactersChanged)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        saving = false,
                        errorMessage = error.message ?: "删除角色图片失败",
                    )
                }
            }
        }
    }

    fun saveCharacterCover(characterId: String, coverFile: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) { characterService.saveCharacterCover(characterId, coverFile) }
            }
            withContext(Dispatchers.IO) { coverFile.delete() }
            result.onSuccess {
                _uiState.update { it.copy(saving = false, errorMessage = "") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        saving = false,
                        errorMessage = error.message ?: "保存角色封面失败",
                    )
                }
            }
        }
    }

    companion object {
        private const val LogTag = "ElecKoiCharacters"

        fun factory(
            characterService: CharacterService,
            characterTransferService: CharacterTransferService,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CharactersViewModel::class.java)) {
                        return CharactersViewModel(characterService, characterTransferService) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
