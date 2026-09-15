package com.eleckoi.android.feature.characters.presets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetCatalog
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetProfile
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.engine.agent.tools.AgentToolGroupSnapshot
import com.eleckoi.android.engine.generation.model.ModelConfig
import java.io.File
import com.eleckoi.android.feature.characters.presets.ui.editor.AgentPresetEditor
import com.eleckoi.android.feature.characters.presets.ui.library.AgentPresetLibrary

@Composable
fun AgentPresetPage(
    catalog: AgentPresetCatalog,
    editorPreset: AgentPreset?,
    editorEntryId: String? = null,
    editorStartOnTools: Boolean = false,
    returnToCallerAfterEntry: Boolean = false,
    loadingEditor: Boolean = false,
    exporting: Boolean = false,
    appearance: AppearanceTheme,
    toolGroups: List<AgentToolGroupSnapshot> = emptyList(),
    modelConfigs: List<ModelConfig> = emptyList(),
    showRootBackButton: Boolean = true,
    onOpenSidebar: (() -> Unit)? = null,
    onBack: () -> Unit,
    onOpenPreset: (String) -> Unit,
    onEditorEntryOpened: () -> Unit = {},
    onCloseEditor: () -> Unit,
    onReturnFromExternalEntry: () -> Unit = onCloseEditor,
    onSetActive: (String) -> Unit,
    onCreate: (String, List<AgentPresetModelTag>, String) -> Unit,
    onImport: () -> Unit,
    onExport: (Set<String>) -> Unit,
    onUpdate: (AgentPreset) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onMoveToGroup: (String, String) -> Unit,
    onUpdateProfile: (String, AgentPresetProfile) -> Unit,
    onUpdateModelTags: (String, List<AgentPresetModelTag>) -> Unit,
    onUpdateAuthorAvatar: (String, Map<AvatarSlot, File>) -> Unit,
    onOpenWebSearchSettings: () -> Unit = {},
    onSaveModelConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit = { _, callback ->
        callback(Result.failure(IllegalStateException("当前页面不能保存模型配置")))
    },
) {
    DisposableEffect(returnToCallerAfterEntry) {
        onDispose {
            if (returnToCallerAfterEntry) onCloseEditor()
        }
    }
    BackHandler(enabled = editorPreset != null) {
        if (returnToCallerAfterEntry) onReturnFromExternalEntry() else onCloseEditor()
    }

    if (editorPreset != null) {
        AgentPresetEditor(
            preset = editorPreset,
            initialEntryId = editorEntryId,
            startOnTools = editorStartOnTools,
            active = editorPreset.id == catalog.activePresetId,
            appearance = appearance,
            toolGroups = toolGroups,
            modelConfigs = modelConfigs,
            onBack = onCloseEditor,
            returnToCallerAfterEntry = returnToCallerAfterEntry,
            onReturnFromExternalEntry = onReturnFromExternalEntry,
            onInitialEntryHandled = onEditorEntryOpened,
            onUpdate = onUpdate,
            onSetActive = { onSetActive(editorPreset.id) },
            onRename = { name -> onRename(editorPreset.id, name) },
            onUpdateProfile = { profile -> onUpdateProfile(editorPreset.id, profile) },
            onUpdateModelTags = { tags -> onUpdateModelTags(editorPreset.id, tags) },
            onUpdateAuthorAvatar = { files -> onUpdateAuthorAvatar(editorPreset.id, files) },
            onOpenWebSearchSettings = onOpenWebSearchSettings,
            onSaveModelConfig = onSaveModelConfig,
        )
        return
    }

    if (loadingEditor) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appearance.mobileBg),
        )
        return
    }

    AgentPresetLibrary(
        catalog = catalog,
        appearance = appearance,
        onBack = onBack.takeIf { showRootBackButton },
        onOpenSidebar = onOpenSidebar,
        onOpenPreset = onOpenPreset,
        onOpenOverview = onOpenPreset,
        onSetActive = onSetActive,
        onCreate = onCreate,
        onImport = onImport,
        exporting = exporting,
        onExport = onExport,
        onCreateGroup = onCreateGroup,
        onRenameGroup = onRenameGroup,
        onDeleteGroup = onDeleteGroup,
        onMoveToGroup = onMoveToGroup,
        onRename = onRename,
        onDuplicate = onDuplicate,
        onDelete = onDelete,
    )
}
