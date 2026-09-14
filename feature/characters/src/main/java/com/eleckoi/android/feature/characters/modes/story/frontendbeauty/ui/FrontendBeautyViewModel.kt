package com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.engine.immersive.api.FrontendProjectService
import com.eleckoi.android.engine.immersive.model.FrontendProject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HtmlThemeEditorState(
    val projectId: String? = null,
    val name: String = "自定义 HTML 主题",
    val html: String = "",
    val hasUnsavedChanges: Boolean = false,
)

data class FrontendBeautyUiState(
    val characterId: String = "",
    val projects: List<FrontendProject> = emptyList(),
    val selectedProjectId: String? = null,
    val messageRendererEnabled: Boolean = true,
    val isImporting: Boolean = false,
    val editor: HtmlThemeEditorState? = null,
    val isLoadingEditor: Boolean = false,
    val isSavingHtmlTheme: Boolean = false,
    val previewProject: FrontendProject? = null,
    val errorMessage: String = "",
)

sealed interface FrontendBeautyIntent {
    data class Load(val characterId: String) : FrontendBeautyIntent
    data class Import(val uri: Uri) : FrontendBeautyIntent
    data class Select(val projectId: String?) : FrontendBeautyIntent
    data class SetMessageRendererEnabled(val enabled: Boolean) : FrontendBeautyIntent
    data class Delete(val projectId: String) : FrontendBeautyIntent
    data class CreateHtmlTheme(val template: String) : FrontendBeautyIntent
    data class EditHtmlTheme(val projectId: String) : FrontendBeautyIntent
    data class ChangeHtmlThemeName(val value: String) : FrontendBeautyIntent
    data class ChangeHtmlThemeSource(val value: String) : FrontendBeautyIntent
    data object PreviewHtmlTheme : FrontendBeautyIntent
    data object SaveHtmlTheme : FrontendBeautyIntent
    data object CloseHtmlThemeEditor : FrontendBeautyIntent
    data object CloseHtmlThemePreview : FrontendBeautyIntent
    data object DismissError : FrontendBeautyIntent
}

class FrontendBeautyViewModel(
    private val service: FrontendProjectService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FrontendBeautyUiState())
    val uiState: StateFlow<FrontendBeautyUiState> = _uiState.asStateFlow()
    private var workspaceJob: Job? = null

    fun onIntent(intent: FrontendBeautyIntent) {
        when (intent) {
            is FrontendBeautyIntent.Load -> load(intent.characterId)
            is FrontendBeautyIntent.Import -> import(intent.uri)
            is FrontendBeautyIntent.Select -> select(intent.projectId)
            is FrontendBeautyIntent.SetMessageRendererEnabled -> setMessageRendererEnabled(intent.enabled)
            is FrontendBeautyIntent.Delete -> delete(intent.projectId)
            is FrontendBeautyIntent.CreateHtmlTheme -> createHtmlTheme(intent.template)
            is FrontendBeautyIntent.EditHtmlTheme -> editHtmlTheme(intent.projectId)
            is FrontendBeautyIntent.ChangeHtmlThemeName -> changeHtmlThemeName(intent.value)
            is FrontendBeautyIntent.ChangeHtmlThemeSource -> changeHtmlThemeSource(intent.value)
            FrontendBeautyIntent.PreviewHtmlTheme -> saveHtmlTheme(preview = true)
            FrontendBeautyIntent.SaveHtmlTheme -> saveHtmlTheme(preview = false)
            FrontendBeautyIntent.CloseHtmlThemeEditor -> closeHtmlThemeEditor()
            FrontendBeautyIntent.CloseHtmlThemePreview -> closeHtmlThemePreview()
            FrontendBeautyIntent.DismissError -> _uiState.update { it.copy(errorMessage = "") }
        }
    }

    fun frontendProjectDirectory(projectId: String) = service.frontendProjectDirectory(projectId)

    private fun load(characterId: String) {
        if (characterId.isBlank() || characterId == _uiState.value.characterId) return
        workspaceJob?.cancel()
        _uiState.update { FrontendBeautyUiState(characterId = characterId) }
        workspaceJob = viewModelScope.launch {
            service.frontendWorkspaceFlow(characterId).collectLatest { workspace ->
                _uiState.update {
                    it.copy(
                        projects = workspace.projects,
                        selectedProjectId = workspace.selectedProjectId,
                        messageRendererEnabled = workspace.messageRendererEnabled,
                    )
                }
            }
        }
    }

    private fun import(uri: Uri) {
        val characterId = _uiState.value.characterId
        if (characterId.isBlank() || _uiState.value.isImporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = "") }
            runCatching { service.importFrontendProject(characterId, uri) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "导入前端项目失败")
                    }
                }
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    private fun select(projectId: String?) {
        val characterId = _uiState.value.characterId
        if (characterId.isBlank()) return
        viewModelScope.launch {
            runCatching { service.selectFrontendProject(characterId, projectId) }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "切换前端失败") }
                }
        }
    }

    private fun setMessageRendererEnabled(enabled: Boolean) {
        val characterId = _uiState.value.characterId
        if (characterId.isBlank()) return
        _uiState.update { it.copy(messageRendererEnabled = enabled, errorMessage = "") }
        viewModelScope.launch {
            runCatching { service.setMessageRendererEnabled(characterId, enabled) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            messageRendererEnabled = !enabled,
                            errorMessage = error.message ?: "保存消息前端渲染设置失败",
                        )
                    }
                }
        }
    }

    private fun delete(projectId: String) {
        val characterId = _uiState.value.characterId
        if (characterId.isBlank()) return
        viewModelScope.launch {
            runCatching { service.deleteFrontendProject(characterId, projectId) }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "删除前端项目失败") }
                }
        }
    }

    private fun createHtmlTheme(template: String) {
        if (_uiState.value.characterId.isBlank()) return
        _uiState.update {
            it.copy(
                editor = HtmlThemeEditorState(
                    html = template,
                    hasUnsavedChanges = true,
                ),
                isLoadingEditor = false,
                previewProject = null,
                errorMessage = "",
            )
        }
    }

    private fun editHtmlTheme(projectId: String) {
        val state = _uiState.value
        val project = state.projects.firstOrNull { it.id == projectId } ?: return
        if (state.isLoadingEditor || state.isSavingHtmlTheme) return
        _uiState.update {
            it.copy(
                editor = HtmlThemeEditorState(projectId = project.id, name = project.name),
                isLoadingEditor = true,
                previewProject = null,
                errorMessage = "",
            )
        }
        viewModelScope.launch {
            runCatching { service.readFrontendProjectEntry(state.characterId, project.id) }
                .onSuccess { html ->
                    _uiState.update { current ->
                        if (current.editor?.projectId != project.id) current else current.copy(
                            editor = current.editor.copy(html = html, hasUnsavedChanges = false),
                            isLoadingEditor = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        if (current.editor?.projectId != project.id) current else current.copy(
                            editor = null,
                            isLoadingEditor = false,
                            errorMessage = error.message ?: "读取 HTML 主题失败",
                        )
                    }
                }
        }
    }

    private fun changeHtmlThemeName(value: String) {
        if (_uiState.value.isSavingHtmlTheme) return
        _uiState.update { state ->
            state.copy(
                editor = state.editor?.copy(name = value, hasUnsavedChanges = true),
            )
        }
    }

    private fun changeHtmlThemeSource(value: String) {
        if (_uiState.value.isSavingHtmlTheme) return
        _uiState.update { state ->
            state.copy(
                editor = state.editor?.copy(html = value, hasUnsavedChanges = true),
            )
        }
    }

    private fun saveHtmlTheme(preview: Boolean) {
        val state = _uiState.value
        val editor = state.editor ?: return
        if (state.characterId.isBlank() || state.isLoadingEditor || state.isSavingHtmlTheme) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingHtmlTheme = true, errorMessage = "") }
            runCatching {
                service.saveHtmlFrontendProject(
                    characterId = state.characterId,
                    projectId = editor.projectId,
                    name = editor.name,
                    html = editor.html,
                    select = !preview,
                )
            }.onSuccess { project ->
                _uiState.update { current ->
                    if (preview) {
                        current.copy(
                            editor = current.editor?.copy(
                                projectId = project.id,
                                name = project.name,
                                hasUnsavedChanges = false,
                            ),
                            isSavingHtmlTheme = false,
                            previewProject = project,
                        )
                    } else {
                        current.copy(
                            editor = null,
                            isSavingHtmlTheme = false,
                            previewProject = null,
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSavingHtmlTheme = false,
                        errorMessage = error.message ?: "保存 HTML 主题失败",
                    )
                }
            }
        }
    }

    private fun closeHtmlThemeEditor() {
        if (_uiState.value.isSavingHtmlTheme) return
        _uiState.update {
            it.copy(editor = null, isLoadingEditor = false, previewProject = null)
        }
    }

    private fun closeHtmlThemePreview() {
        _uiState.update { it.copy(previewProject = null) }
    }

    companion object {
        fun factory(service: FrontendProjectService): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FrontendBeautyViewModel(service) as T
                }
            }
        }
    }
}
