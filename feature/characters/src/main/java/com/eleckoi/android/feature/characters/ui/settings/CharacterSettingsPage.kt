package com.eleckoi.android.feature.characters.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.AvatarSet
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.modes.story.ui.StoryToolsPanel
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.UnsavedChangesDialog
import com.eleckoi.android.feature.characters.ui.components.AvatarSlotsPage
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import java.io.File

internal class CharacterSettingsEditorState(character: CharacterSlot) {
    var name by mutableStateOf(initialName(character))
    var opening by mutableStateOf(character.persona.opening)
    var dirty by mutableStateOf(false)
    var confirmDelete by mutableStateOf(false)
    var pendingAvatarFiles by mutableStateOf<Map<AvatarSlot, File>>(emptyMap())
    var unsavedDialogOpen by mutableStateOf(false)
    private var pendingAction: (() -> Unit)? = null

    fun syncFrom(character: CharacterSlot) {
        if (dirty) return
        name = initialName(character)
        opening = character.persona.opening
    }

    fun draft(source: CharacterCard): CharacterCard {
        return source.copy(
            assistantName = name,
            opening = opening,
            showOpening = opening.isNotBlank(),
        )
    }

    fun updateName(value: String) {
        name = value.take(48)
        dirty = true
    }

    fun updateOpening(value: String) {
        opening = value
        dirty = true
    }

    fun markSaved() {
        dirty = false
    }

    fun requestAction(alwaysUnsaved: Boolean, action: () -> Unit) {
        if (!alwaysUnsaved && !dirty) {
            action()
            return
        }
        pendingAction = action
        unsavedDialogOpen = true
    }

    fun cancelPendingAction() {
        pendingAction = null
        unsavedDialogOpen = false
    }

    fun continuePendingAction() {
        val action = pendingAction
        pendingAction = null
        unsavedDialogOpen = false
        dirty = false
        action?.invoke()
    }

    fun discardAndContinue() {
        discardPendingAvatars()
        continuePendingAction()
    }

    fun stageAvatars(files: Map<AvatarSlot, File>) {
        files.forEach { (slot, file) ->
            pendingAvatarFiles[slot]?.takeIf { it != file }?.delete()
        }
        pendingAvatarFiles = pendingAvatarFiles + files
        dirty = true
    }

    fun clearStagedAvatar(slot: AvatarSlot) {
        pendingAvatarFiles[slot]?.delete()
        pendingAvatarFiles = pendingAvatarFiles - slot
        dirty = true
    }

    fun displayedAvatars(source: AvatarSet): AvatarSet = AvatarSet(
        circle = pendingAvatarFiles[AvatarSlot.Circle]?.absolutePath ?: source.circle,
        square = pendingAvatarFiles[AvatarSlot.Square]?.absolutePath ?: source.square,
        portrait = pendingAvatarFiles[AvatarSlot.Portrait]?.absolutePath ?: source.portrait,
    )

    fun discardPendingAvatars() {
        pendingAvatarFiles.values.forEach(File::delete)
        pendingAvatarFiles = emptyMap()
    }

    private fun initialName(character: CharacterSlot): String {
        return character.persona.assistantName.ifBlank {
            character.name.takeUnless { it == "未命名角色" }.orEmpty()
        }
    }
}

@Composable
private fun rememberCharacterSettingsEditorState(character: CharacterSlot): CharacterSettingsEditorState {
    val state = remember(character.id) { CharacterSettingsEditorState(character) }
    LaunchedEffect(character.persona) {
        state.syncFrom(character)
    }
    return state
}

@Composable
fun CharacterSettingsPage(
    character: CharacterSlot?,
    appearance: AppearanceTheme,
    saving: Boolean,
    isDraft: Boolean = false,
    onBack: () -> Unit,
    onSavePersona: (CharacterCard, (Result<CharacterSlot>) -> Unit) -> Unit,
    onCreateCharacter: (CharacterCard, Map<AvatarSlot, File>, (Result<CharacterSlot>) -> Unit) -> Unit = { _, _, callback ->
        callback(Result.failure(IllegalStateException("当前页面不能创建角色")))
    },
    onCharacterCreated: (String) -> Unit = {},
    onSaveAvatars: (Map<AvatarSlot, File>) -> Unit,
    onClearAvatar: (AvatarSlot) -> Unit,
    onSendMessage: (persona: CharacterCard) -> Unit,
    onOpenAiCreationAssistant: () -> Unit,
    onOpenSettingLibrary: () -> Unit,
    onOpenDynamicSettings: () -> Unit,
    onOpenVariableConfig: () -> Unit,
    onOpenRegexRules: () -> Unit,
    onOpenFrontendBeauty: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var avatarPageOpen by remember { mutableStateOf(false) }
    var avatarPageSlot by remember { mutableStateOf<AvatarSlot?>(null) }

    if (character == null) {
        EmptyCharacterSettings(appearance, onBack)
        return
    }

    val editorState = rememberCharacterSettingsEditorState(character)
    var activeSection by remember(character.id) { mutableStateOf(CharacterSettingsSection.Story) }
    with(editorState) {
    val visibleAvatars = if (isDraft) displayedAvatars(character.persona.assistantAvatars) else character.persona.assistantAvatars
    val avatarPath = visibleAvatars.circle.ifBlank { character.avatar }
    val coverPath = visibleAvatars.portrait.ifBlank { character.coverImage }
    val fallbackName = character.name.takeUnless { it == "未命名角色" }.orEmpty()

    if (avatarPageOpen) {
        AvatarSlotsPage(
            avatars = visibleAvatars,
            displayName = name.ifBlank { character.name },
            cachePrefix = "character",
            appearance = appearance,
            initialSlot = avatarPageSlot,
            onBack = {
                avatarPageOpen = false
                avatarPageSlot = null
            },
            onSave = { files ->
                if (isDraft) stageAvatars(files) else onSaveAvatars(files)
            },
            onClear = { slot ->
                if (isDraft) clearStagedAvatar(slot) else onClearAvatar(slot)
            },
        )
        return
    }

    fun requestBack() {
        requestAction(alwaysUnsaved = isDraft, action = onBack)
    }

    fun saveAndContinue(action: () -> Unit) {
        onSavePersona(draft(character.persona)) { result ->
            result.onSuccess {
                markSaved()
                action()
            }
        }
    }

    fun createAndContinue(action: (CharacterSlot) -> Unit) {
        onCreateCharacter(draft(character.persona), pendingAvatarFiles) { result ->
            result.onSuccess { created ->
                pendingAvatarFiles = emptyMap()
                markSaved()
                action(created)
            }
        }
    }

    BackHandler(onBack = ::requestBack)

    PinnedStatusScaffold(
        appearance = appearance,
        modifier = Modifier.characterScrapbookBoard(appearance),
        imeAware = false,
        backgroundColor = Color.Transparent,
    ) {
        CharacterScrapbookFrame(
            name = name,
            fallbackName = fallbackName,
            avatarPath = avatarPath,
            coverPath = coverPath,
            appearance = appearance,
            onBack = ::requestBack,
            onExport = if (isDraft) null else onExport,
            onDelete = if (isDraft) null else ({ confirmDelete = true }),
            onAvatarClick = {
                avatarPageSlot = null
                avatarPageOpen = true
            },
            onCoverClick = {
                avatarPageSlot = AvatarSlot.Portrait
                avatarPageOpen = true
            },
            onNameChange = ::updateName,
        ) { layoutScale ->
            CharacterSectionSwitch(
                activeSection = activeSection,
                appearance = appearance,
                layoutScale = layoutScale,
                onChange = { section -> activeSection = section },
            )

            when (activeSection) {
                CharacterSettingsSection.Profile -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "开发中",
                            color = appearance.mobileMuted,
                            fontSize = 15.sp,
                        )
                    }
                }
                CharacterSettingsSection.Story -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = (6f * layoutScale).dp),
                    ) {
                        StoryToolsPanel(
                            appearance = appearance,
                            layoutScale = layoutScale,
                            onOpenAiCreationAssistant = {
                                requestAction(isDraft, onOpenAiCreationAssistant)
                            },
                            onOpenSettingLibrary = {
                                requestAction(isDraft, onOpenSettingLibrary)
                            },
                            onOpenDynamicSettings = {
                                requestAction(isDraft, onOpenDynamicSettings)
                            },
                            onOpenVariableConfig = {
                                requestAction(isDraft, onOpenVariableConfig)
                            },
                            onOpenRegexRules = {
                                requestAction(isDraft, onOpenRegexRules)
                            },
                            onOpenFrontendBeauty = {
                                requestAction(isDraft, onOpenFrontendBeauty)
                            },
                        )
                    }
                }
            }
            if (isDraft) {
                CharacterDraftFooter(
                    layoutScale = layoutScale,
                    enabled = !saving,
                    onCreate = {
                        createAndContinue { created ->
                            onCharacterCreated(created.id)
                        }
                    },
                )
            } else {
                ScrapbookFooter(
                    layoutScale = layoutScale,
                    enabled = !saving,
                    onSend = {
                        onSendMessage(draft(character.persona))
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除角色？",
            message = "角色资料和该角色聊天记录都会从本地删除。",
            appearance = appearance,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
    if (unsavedDialogOpen) {
        UnsavedChangesDialog(
            title = if (isDraft) "创建角色？" else "保存修改？",
            message = if (isDraft) {
                "离开前是否创建当前角色？"
            } else {
                "离开前是否保存当前角色的修改？"
            },
            appearance = appearance,
            saving = saving,
            saveText = if (isDraft) "创建角色" else "保存",
            discardText = if (isDraft) "不创建" else "不保存",
            onSave = {
                if (isDraft) {
                    createAndContinue { created ->
                        onCharacterCreated(created.id)
                        continuePendingAction()
                    }
                } else {
                    saveAndContinue(::continuePendingAction)
                }
            },
            onDiscard = ::discardAndContinue,
            onCancel = ::cancelPendingAction,
        )
    }
    }
}

