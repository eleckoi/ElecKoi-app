package com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.foundation.design.AppearanceTheme
import java.io.File

private val FrontendImportMimeTypes = arrayOf(
    "text/html",
    "application/zip",
    "application/octet-stream",
)

@Composable
fun FrontendBeautyPage(
    characterId: String,
    characterName: String,
    appearance: AppearanceTheme,
    viewModel: FrontendBeautyViewModel,
    onBack: () -> Unit,
    previewContent: @Composable (FrontendProject, File, () -> Unit) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val htmlThemeTemplate = remember(context) {
        context.assets.open(HtmlThemeTemplateAsset).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onIntent(FrontendBeautyIntent.Import(it)) }
    }
    LaunchedEffect(characterId) {
        viewModel.onIntent(FrontendBeautyIntent.Load(characterId))
    }
    state.previewProject?.let { project ->
        viewModel.frontendProjectDirectory(project.id)?.let { directory ->
            previewContent(project, directory) {
                viewModel.onIntent(FrontendBeautyIntent.CloseHtmlThemePreview)
            }
            return
        }
    }

    FrontendBeautyContent(
        characterName = characterName,
        state = state,
        appearance = appearance,
        onBack = onBack,
        onCreateHtmlTheme = {
            viewModel.onIntent(FrontendBeautyIntent.CreateHtmlTheme(htmlThemeTemplate))
        },
        onEditHtmlTheme = { viewModel.onIntent(FrontendBeautyIntent.EditHtmlTheme(it)) },
        onChangeHtmlThemeName = {
            viewModel.onIntent(FrontendBeautyIntent.ChangeHtmlThemeName(it))
        },
        onChangeHtmlThemeSource = {
            viewModel.onIntent(FrontendBeautyIntent.ChangeHtmlThemeSource(it))
        },
        onPreviewHtmlTheme = { viewModel.onIntent(FrontendBeautyIntent.PreviewHtmlTheme) },
        onSaveHtmlTheme = { viewModel.onIntent(FrontendBeautyIntent.SaveHtmlTheme) },
        onCloseHtmlThemeEditor = {
            viewModel.onIntent(FrontendBeautyIntent.CloseHtmlThemeEditor)
        },
        onImport = { importLauncher.launch(FrontendImportMimeTypes) },
        onDelete = { viewModel.onIntent(FrontendBeautyIntent.Delete(it)) },
        onSelect = { viewModel.onIntent(FrontendBeautyIntent.Select(it)) },
        onMessageRendererEnabledChange = {
            viewModel.onIntent(FrontendBeautyIntent.SetMessageRendererEnabled(it))
        },
        onDismissError = { viewModel.onIntent(FrontendBeautyIntent.DismissError) },
    )
}

private const val HtmlThemeTemplateAsset = "frontend/templates/chat-theme.html"
