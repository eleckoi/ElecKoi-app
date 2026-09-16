package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrarySource
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion

@Composable
internal fun SettingLibraryPageOverlays(
    editorState: SettingLibraryEditorState,
    library: SettingLibrary?,
    characterName: String,
    characterAvatar: String,
    importSources: List<SettingLibrarySource>,
    loadingImportSources: Boolean,
    appearance: AppearanceTheme,
    invalidEnableEntry: SettingLibraryEntry?,
    conflictingOrderEntry: SettingLibraryEntry?,
    onDismissInvalidEnableEntry: () -> Unit,
    onDismissConflictingOrderEntry: () -> Unit,
    onRequestImportSources: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onParseImportFile: (String) -> SettingLibraryVersion?,
) {
    val context = LocalContext.current
    var importSourceDialogOpen by remember { mutableStateOf(false) }
    with(editorState) {
        if (libraryManagerOpen) {
            SettingLibraryManagerPage(
                activeName = libraryName,
                versions = versions,
                activeVersionId = activeVersionId,
                appearance = appearance,
                onDismiss = { libraryManagerOpen = false },
                onActiveNameChange = ::updateLibraryName,
                onSelectVersion = ::switchVersion,
                onCreateVersion = { createLibraryVersionDialogOpen = true },
                onMergeFromCharacter = {
                    onRequestImportSources()
                    importPageOpen = true
                },
                onImportAsNewVersion = { importSourceDialogOpen = true },
                onExport = onExport,
                onDeleteActiveVersion = { confirmDeleteLibrary = true },
            )
        }

        if (importPageOpen && library != null) {
            SettingLibraryImportPage(
                sources = importSources,
                loading = loadingImportSources,
                currentCharacterName = characterName,
                currentCharacterAvatar = characterAvatar,
                // The live list, not the one on disk: a version renamed or created a moment ago
                // should already be offered here, before the autosave necessarily lands.
                currentVersions = versions,
                activeVersionId = activeVersionId,
                targetEntries = entries,
                targetGroups = groups,
                appearance = appearance,
                onParseFile = onParseImportFile,
                onDismiss = { importPageOpen = false },
                onApply = { result ->
                    updateTree(result.entries, result.groups)
                    Toast.makeText(context, "已并入 ${result.plan.entryCount} 条设定", Toast.LENGTH_SHORT).show()
                },
            )
        }

        if (importSourceDialogOpen) {
            SettingLibraryImportSourceDialog(
                appearance = appearance,
                onDismiss = { importSourceDialogOpen = false },
                onImportElecKoi = {
                    importSourceDialogOpen = false
                    onImport()
                },
                onImportSillyTavernWorldBook = {
                    importSourceDialogOpen = false
                    onImport()
                },
            )
        }

        if (createLibraryVersionDialogOpen) {
            SettingLibraryCreateVersionDialog(
                versions = versions,
                activeVersionId = activeVersionId,
                appearance = appearance,
                onDismiss = { createLibraryVersionDialogOpen = false },
                onConfirm = { name, sourceVersionId ->
                    createLibraryVersionDialogOpen = false
                    createLibraryVersion(name, sourceVersionId)
                },
            )
        }

        if (renameNodeDialogOpen) {
            SettingLibraryRenameNodeDialog(
                value = renameNodeName,
                appearance = appearance,
                onValueChange = { renameNodeName = it },
                onDismiss = { renameNodeDialogOpen = false },
                onConfirm = {
                    renameNodeDialogOpen = false
                    renameSelected(renameNodeName)
                },
            )
        }

        if (confirmDeleteNode) {
            ConfirmDialog(
                title = "删除${selectedTreeKindLabel()}？",
                message = if (selectedTreeNodeId.startsWith("folder:")) {
                    "会同时删除这个文件夹里的子文件夹和设定。"
                } else if (
                    entries.firstOrNull { fileNodeId(it.id) == selectedTreeNodeId }
                        ?.dynamicMode == SettingLibraryDynamicMode.EjsController
                ) {
                    "会删除这个控制器；它读取的EJS引用设定不会被删除。"
                } else {
                    "会删除这个设定条目。"
                },
                appearance = appearance,
                onDismiss = { confirmDeleteNode = false },
                onConfirm = {
                    confirmDeleteNode = false
                    deleteSelected()
                },
            )
        }

        invalidEnableEntry?.let { entry ->
            SettingLibraryRequiredFieldsDialog(
                triggerSelected = entry.triggerMode != null,
                positionSelected = entry.position != null,
                appearance = appearance,
                onDismiss = onDismissInvalidEnableEntry,
            )
        }

        conflictingOrderEntry?.let { entry ->
            SettingLibraryOrderConflictDialog(
                order = entry.order,
                appearance = appearance,
                onDismiss = onDismissConflictingOrderEntry,
            )
        }

        if (createEntryGroupPickerOpen) {
            SettingLibraryEntryGroupPickerDialog(
                groups = groups.sortedBy { it.order },
                selectedGroupId = selectedCreateEntryGroupId,
                appearance = appearance,
                onSelectGroup = { selectedCreateEntryGroupId = it },
                onDismiss = {
                    createEntryGroupPickerOpen = false
                    pendingCreateEntryTriggerMode = null
                },
                onConfirm = {
                    createEntryGroupPickerOpen = false
                    val triggerMode = pendingCreateEntryTriggerMode ?: SettingLibraryTriggerMode.AgentTool
                    pendingCreateEntryTriggerMode = null
                    addEntry(selectedCreateEntryGroupId, triggerMode)
                },
            )
        }

        if (createGroupNameDialogOpen) {
            SettingLibraryGroupNameDialog(
                value = createGroupName,
                groups = groups,
                appearance = appearance,
                onValueChange = { createGroupName = it },
                onDismiss = { createGroupNameDialogOpen = false },
                onConfirm = {
                    createGroupNameDialogOpen = false
                    addGroup(createGroupName)
                },
            )
        }

        if (confirmDeleteLibrary) {
            ConfirmDialog(
                title = "删除当前版本？",
                message = if (versions.size > 1) {
                    "将永久删除当前版本，并切换到另一个版本。其他历史版本会保留。"
                } else {
                    "将永久删除当前版本，并创建一个新的空白版本。"
                },
                appearance = appearance,
                onDismiss = { confirmDeleteLibrary = false },
                onConfirm = {
                    deleteActiveVersion()
                    libraryManagerOpen = false
                    confirmDeleteLibrary = false
                },
            )
        }
    }
}
