package com.eleckoi.android.app.shell

import com.eleckoi.android.foundation.design.components.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.eleckoi.android.feature.characters.ui.CharactersIntent
import com.eleckoi.android.feature.characters.ui.CharactersViewModel
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetImportSourceDialog
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetExportFormatDialog
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetExportDialog
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetBatchExportDialog
import com.eleckoi.android.feature.characters.presets.ui.AgentPresetViewModel
import com.eleckoi.android.feature.characters.transfer.ui.CharacterExportDialog
import com.eleckoi.android.feature.characters.transfer.ui.CharacterExportFormatDialog
import com.eleckoi.android.feature.characters.transfer.ui.CharacterBatchExportDialog
import com.eleckoi.android.feature.characters.transfer.ui.CharacterImportDialog
import com.eleckoi.android.feature.characters.transfer.ui.CharacterImportSourceDialog
import com.eleckoi.android.app.navigation.MobileRoute

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.MobileShellOverlays(
    characterImportSourceOpen: Boolean,
    onCloseCharacterImportSource: () -> Unit,
    agentPresetImportSourceOpen: Boolean,
    onCloseAgentPresetImportSource: () -> Unit,
    charactersState: com.eleckoi.android.feature.characters.ui.CharactersUiState,
    useCoverArtwork: Boolean,
    agentPresetState: com.eleckoi.android.feature.characters.presets.ui.AgentPresetUiState,
    appearance: com.eleckoi.android.foundation.design.AppearanceTheme,
    moreOpen: Boolean,
    user: com.eleckoi.android.feature.characters.model.UserProfile,
    appUpdateAvailable: Boolean,
    navigationBarColor: Color,
    shellViewModel: ShellViewModel,
    charactersViewModel: CharactersViewModel,
    agentPresetViewModel: AgentPresetViewModel,
    characterCardActions: CharacterCardDocumentActions,
    agentPresetDocumentActions: AgentPresetDocumentActions,
    navigateTo: (MobileRoute) -> Unit,
) {
    if (characterImportSourceOpen) {
        CharacterImportSourceDialog(
            appearance = appearance,
            onDismiss = { onCloseCharacterImportSource() },
            onImportElecKoi = {
                onCloseCharacterImportSource()
                characterCardActions.importCard()
            },
            onImportSillyTavern = {
                onCloseCharacterImportSource()
                characterCardActions.importSillyTavernCard()
            },
        )
    }
    if (agentPresetImportSourceOpen) {
        AgentPresetImportSourceDialog(
            appearance = appearance,
            onDismiss = { onCloseAgentPresetImportSource() },
            onImportElecKoi = {
                onCloseAgentPresetImportSource()
                agentPresetDocumentActions.importElecKoiPresets()
            },
            onImportSillyTavern = {
                onCloseAgentPresetImportSource()
                agentPresetDocumentActions.importSillyTavernPresets()
            },
        )
    }
    charactersState.importPreview?.let { preview ->
        CharacterImportDialog(
            preview = preview,
            busy = charactersState.transferBusy,
            appearance = appearance,
            onDismiss = {
                charactersViewModel.onIntent(CharactersIntent.DismissCharacterImport)
            },
            onConfirm = {
                charactersViewModel.onIntent(CharactersIntent.ConfirmCharacterImport)
            },
        )
    }
    if (charactersState.pendingExportCharacterIds.isNotEmpty()) {
        CharacterExportFormatDialog(
            appearance = appearance,
            onDismiss = {
                charactersViewModel.onIntent(CharactersIntent.DismissCharacterExportFormat)
            },
            onSelect = { format ->
                charactersViewModel.onIntent(CharactersIntent.ConfirmCharacterCardExport(format))
            },
        )
    }
    charactersState.exportedCard?.let { card ->
        CharacterExportDialog(
            card = card,
            appearance = appearance,
            onDismiss = {
                charactersViewModel.onIntent(CharactersIntent.DismissCharacterExport)
            },
            onShareOriginal = {
                characterCardActions.shareOriginal(card)
                charactersViewModel.onIntent(CharactersIntent.DismissCharacterExport)
            },
            onSave = {
                characterCardActions.saveCard(card)
                charactersViewModel.onIntent(CharactersIntent.DismissCharacterExport)
            },
        )
    }
    if (charactersState.exportedCards.isNotEmpty()) {
        CharacterBatchExportDialog(
            cards = charactersState.exportedCards,
            appearance = appearance,
            onDismiss = {
                charactersViewModel.onIntent(CharactersIntent.DismissCharacterCardsExport)
            },
            onShareOriginal = {
                characterCardActions.shareOriginals(charactersState.exportedCards)
                charactersViewModel.onIntent(CharactersIntent.DismissCharacterCardsExport)
            },
            onSave = {
                characterCardActions.saveCards(charactersState.exportedCards)
                charactersViewModel.onIntent(CharactersIntent.DismissCharacterCardsExport)
            },
        )
    }
    if (agentPresetState.pendingExportPresetIds.isNotEmpty()) {
        AgentPresetExportFormatDialog(
            appearance = appearance,
            onDismiss = agentPresetViewModel::dismissPresetExportFormat,
            onSelect = agentPresetViewModel::confirmPresetExport,
        )
    }
    agentPresetState.exportedFiles.singleOrNull()?.let { card ->
        AgentPresetExportDialog(
            card = card,
            appearance = appearance,
            onDismiss = agentPresetViewModel::dismissPresetExport,
            onShareOriginal = {
                agentPresetDocumentActions.shareOriginal(card)
                agentPresetViewModel.dismissPresetExport()
            },
            onSave = {
                agentPresetDocumentActions.saveCard(card)
                agentPresetViewModel.dismissPresetExport()
            },
        )
    }
    if (agentPresetState.exportedFiles.size > 1) {
        AgentPresetBatchExportDialog(
            cards = agentPresetState.exportedFiles,
            appearance = appearance,
            onDismiss = agentPresetViewModel::dismissPresetExport,
            onShareOriginal = {
                agentPresetDocumentActions.shareOriginals(agentPresetState.exportedFiles)
                agentPresetViewModel.dismissPresetExport()
            },
            onSave = {
                agentPresetDocumentActions.saveCards(agentPresetState.exportedFiles)
                agentPresetViewModel.dismissPresetExport()
            },
        )
    }

    MobileMorePanel(
        visible = moreOpen,
        user = user,
        characters = charactersState.characters,
        useCoverArtwork = useCoverArtwork,
        appearance = appearance,
        appUpdateAvailable = appUpdateAvailable,
        onClose = { shellViewModel.onIntent(ShellIntent.SetMoreOpen(false)) },
        onOpenProfile = {
            shellViewModel.onIntent(ShellIntent.SetMoreOpen(false))
            navigateTo(MobileRoute.Profile)
        },
        onToggleAllCharactersExpanded = {
            charactersViewModel.onIntent(CharactersIntent.ToggleAllCharactersExpanded)
        },
        onToggleCharacterGroupExpanded = { group ->
            charactersViewModel.onIntent(CharactersIntent.ToggleCharacterGroupExpanded(group))
        },
        onOpenCharacter = { characterId ->
            shellViewModel.onIntent(ShellIntent.SetMoreOpen(false))
            charactersViewModel.onIntent(CharactersIntent.SelectCharacter(characterId))
        },
        onSaveCharacters = { payload ->
            charactersViewModel.onIntent(CharactersIntent.SaveCharacterCollection(payload))
        },
        onOpenSettings = {
            shellViewModel.onIntent(ShellIntent.SetMoreOpen(false))
            navigateTo(MobileRoute.Settings)
        },
        onOpenUpdate = {
            shellViewModel.onIntent(ShellIntent.SetMoreOpen(false))
            navigateTo(MobileRoute.AppUpdate)
        },
    )
    ThreeButtonNavigationBarProtection(
        color = navigationBarColor,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}
