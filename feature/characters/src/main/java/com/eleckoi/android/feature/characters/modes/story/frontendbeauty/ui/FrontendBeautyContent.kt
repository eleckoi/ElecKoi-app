package com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.components.FrontendWorkspaceHeader
import com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.components.MessageFrontendRendererControl
import com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.drawer.FrontendFileDrawer
import com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.gallery.FrontendProjectGallery
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.ErrorDialog
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import kotlinx.coroutines.launch

@Composable
internal fun FrontendBeautyContent(
    characterName: String,
    state: FrontendBeautyUiState,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onCreateHtmlTheme: () -> Unit,
    onEditHtmlTheme: (String) -> Unit,
    onChangeHtmlThemeName: (String) -> Unit,
    onChangeHtmlThemeSource: (String) -> Unit,
    onPreviewHtmlTheme: () -> Unit,
    onSaveHtmlTheme: () -> Unit,
    onCloseHtmlThemeEditor: () -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onMessageRendererEnabledChange: (Boolean) -> Unit,
    onDismissError: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    if (state.editor != null) {
        HtmlThemeEditorPage(
            editor = state.editor,
            loading = state.isLoadingEditor,
            saving = state.isSavingHtmlTheme,
            appearance = appearance,
            onBack = onCloseHtmlThemeEditor,
            onNameChange = onChangeHtmlThemeName,
            onSourceChange = onChangeHtmlThemeSource,
            onPreview = onPreviewHtmlTheme,
            onSave = onSaveHtmlTheme,
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                FrontendFileDrawer(
                    characterName = characterName,
                    projects = state.projects,
                    selectedProjectId = state.selectedProjectId,
                    appearance = appearance,
                    onClose = { scope.launch { drawerState.close() } },
                    onCreateHtmlTheme = onCreateHtmlTheme,
                    onEdit = onEditHtmlTheme,
                    onImport = onImport,
                    onDelete = onDelete,
                    modifier = Modifier
                        .width(306.dp)
                        .fillMaxHeight(),
                )
            },
            scrimColor = Color.Black.copy(alpha = 0.28f),
        ) {
            PinnedStatusScaffold(
                appearance = appearance,
                imeAware = false,
                backgroundColor = appearance.mobileBg,
            ) {
                FrontendWorkspaceHeader(
                    title = "前端美化",
                    appearance = appearance,
                    onBack = onBack,
                    onOpenFiles = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                )
                MessageFrontendRendererControl(
                    enabled = state.messageRendererEnabled,
                    appearance = appearance,
                    onEnabledChange = onMessageRendererEnabledChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                FrontendProjectGallery(
                    projects = state.projects,
                    isImporting = state.isImporting,
                    selectedProjectId = state.selectedProjectId,
                    appearance = appearance,
                    onSelect = onSelect,
                    onCreateHtmlTheme = onCreateHtmlTheme,
                    onEdit = onEditHtmlTheme,
                    onImport = onImport,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (state.errorMessage.isNotBlank()) {
        ErrorDialog(
            message = state.errorMessage,
            appearance = appearance,
            onDismiss = onDismissError,
        )
    }
}
