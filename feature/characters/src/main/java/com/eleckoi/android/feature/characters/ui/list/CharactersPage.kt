package com.eleckoi.android.feature.characters.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.CharactersPayload
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.foundation.design.components.MobileHeaderMenuAction
import com.eleckoi.android.foundation.design.components.MobileRootActionHeader
import com.eleckoi.android.foundation.design.components.MobileRootSurface
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.feature.characters.ui.list.group.CharacterGroupManagerPage

private class CharactersRootEditorState {
    var selectedGroup by mutableStateOf(ALL_CHARACTERS)
    var createCharacterGroupPickerOpen by mutableStateOf(false)
    var selectedCreateCharacterGroup by mutableStateOf(DEFAULT_GROUP)
    var batchAction by mutableStateOf<CharacterBatchAction?>(null)

    fun syncGroups(groups: List<String>) {
        if (selectedGroup != ALL_CHARACTERS && selectedGroup !in groups) {
            selectedGroup = ALL_CHARACTERS
        }
    }

    fun prepareCreateCharacter(groups: List<String>): String? {
        return if (groups.isEmpty()) {
            DEFAULT_GROUP
        } else {
            selectedCreateCharacterGroup = groups.firstOrNull().orEmpty()
            createCharacterGroupPickerOpen = true
            null
        }
    }

    fun selectGroupForCreatedCharacter(group: String) {
        selectedGroup = group.ifBlank { ALL_CHARACTERS }
    }
}

@Composable
private fun rememberCharactersRootEditorState(): CharactersRootEditorState {
    return remember { CharactersRootEditorState() }
}

@Composable
fun CharactersRootPage(
    user: UserProfile,
    characters: CharactersPayload?,
    appearance: AppearanceTheme,
    onSearch: () -> Unit,
    groupManagerOpen: Boolean,
    onGroupManagerOpenChange: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onSaveCharacters: (CharactersPayload) -> Unit,
    onImportCharacterCard: () -> Unit,
    onExportCharacters: (List<String>) -> Unit,
    onDeleteCharacters: (List<String>) -> Unit,
    addMenuExpanded: Boolean? = null,
    onAddMenuExpandedChange: (Boolean) -> Unit = {},
) {
    val groups = buildCharacterGroups(characters)
    val editorState = rememberCharactersRootEditorState()

    with(editorState) {
    LaunchedEffect(groups.joinToString("\u0000")) {
        syncGroups(groups)
    }

    fun createInGroup(group: String) {
        selectGroupForCreatedCharacter(group)
        onAdd(group)
    }

    fun requestCreateCharacter() {
        prepareCreateCharacter(groups)?.let(::createInGroup)
    }

    MobileRootSurface(
        appearance = appearance,
        header = {
            MobileRootActionHeader(
                title = "角色",
                appearance = appearance,
                onOpenSidebar = onOpenSidebar,
                onSearch = onSearch,
                onAdd = ::requestCreateCharacter,
                addMenuActions = listOf(
                    MobileHeaderMenuAction("新建角色", AppIconPaths.CharacterPlus, onClick = ::requestCreateCharacter),
                    MobileHeaderMenuAction(
                        "分组管理",
                        AppIconPaths.Gear,
                        dividerBefore = true,
                        onClick = { onGroupManagerOpenChange(true) },
                    ),
                    MobileHeaderMenuAction("导入角色卡", AppIconPaths.Import, onClick = onImportCharacterCard),
                    MobileHeaderMenuAction(
                        "导出角色卡",
                        AppIconPaths.Export,
                        onClick = { batchAction = CharacterBatchAction.Export },
                    ),
                    MobileHeaderMenuAction(
                        "删除角色卡",
                        AppIconPaths.Trash,
                        tint = com.eleckoi.android.foundation.design.ElecKoiDanger,
                        dividerBefore = true,
                        onClick = { batchAction = CharacterBatchAction.Delete },
                    ),
                ),
                addMenuExpanded = addMenuExpanded,
                onAddMenuExpandedChange = onAddMenuExpandedChange,
                useOverflowAction = true,
            )
        },
    ) {
        CharacterWaterfall(
            user = user,
            characters = characters ?: CharactersPayload("", groups, emptyList()),
            groups = groups,
            selectedGroup = selectedGroup,
            batchAction = batchAction,
            appearance = appearance,
            onSelectGroup = { selectedGroup = it },
            onBatchActionChange = { batchAction = it },
            onOpenCharacter = onOpenCharacter,
            onExportCharacters = onExportCharacters,
            onDeleteCharacters = onDeleteCharacters,
        )
    }

    if (groupManagerOpen) {
        CharacterGroupManagerPage(
            groups = groups,
            characters = characters ?: CharactersPayload("", groups, emptyList()),
            appearance = appearance,
            onDismiss = { onGroupManagerOpenChange(false) },
            onSaveCharacters = { payload ->
                onSaveCharacters(payload)
                if (selectedGroup != ALL_CHARACTERS && selectedGroup !in buildCharacterGroups(payload)) {
                    selectedGroup = ALL_CHARACTERS
                }
            },
        )
    }

    if (createCharacterGroupPickerOpen) {
        CharacterGroupPickerDialog(
            groups = groups,
            selectedGroup = selectedCreateCharacterGroup,
            appearance = appearance,
            onSelectGroup = { selectedCreateCharacterGroup = it },
            onDismiss = { createCharacterGroupPickerOpen = false },
            onConfirm = {
                createCharacterGroupPickerOpen = false
                createInGroup(selectedCreateCharacterGroup)
            },
        )
    }
    }
}


