package com.eleckoi.android.feature.settings.ui.personalization.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.preferences.AppearanceMode
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.ImageCropPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePalettePage(
    appearance: AppearanceTheme,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onApplyPalette: (Bitmap) -> Unit,
    onSetRootBackground: (Bitmap, Float, Float, Float) -> Unit,
    onTuneRootBackground: (Float, Float, Float) -> Unit,
    onClearRootBackground: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(ThemeEditorTab.RootBackground) }
    var pendingSelection by remember { mutableStateOf<Pair<Uri, ThemeEditorTab>?>(null) }
    var cropSource by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tuningOpen by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(appearance.rootBackgroundOpacity) }
    var blur by remember { mutableFloatStateOf(appearance.rootBackgroundBlur) }
    var scrim by remember { mutableFloatStateOf(appearance.rootBackgroundScrim) }
    val tuningSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            when (activeTab) {
                ThemeEditorTab.Palette -> pendingSelection = uri to activeTab
                ThemeEditorTab.RootBackground -> cropSource = uri
            }
        }
    }

    LaunchedEffect(
        appearance.rootBackgroundOpacity,
        appearance.rootBackgroundBlur,
        appearance.rootBackgroundScrim,
    ) {
        opacity = appearance.rootBackgroundOpacity
        blur = appearance.rootBackgroundBlur
        scrim = appearance.rootBackgroundScrim
    }
    LaunchedEffect(pendingSelection) {
        val selection = pendingSelection ?: return@LaunchedEffect
        try {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(selection.first)?.use(BitmapFactory::decodeStream)
                }.getOrNull()
            } ?: return@LaunchedEffect
            when (selection.second) {
                ThemeEditorTab.Palette -> onApplyPalette(bitmap)
                ThemeEditorTab.RootBackground -> {
                    previewBitmap = bitmap
                    onSetRootBackground(bitmap, opacity, blur, scrim)
                }
            }
        } finally {
            // Clearing this before decode changed the LaunchedEffect key and cancelled the decode
            // coroutine itself. That made both image pickers appear to accept a file and then do
            // nothing. Clear only after the selected image has been delivered.
            if (pendingSelection == selection) pendingSelection = null
        }
    }

    cropSource?.let { source ->
        ImageCropPage(
            sourceUri = source,
            title = "裁剪主页背景",
            cropAspect = 9f / 20f,
            circularFrame = false,
            outputWidth = 1080,
            appearance = appearance,
            onBack = { cropSource = null },
            onPickAnother = { launcher.launch("image/*") },
            onCropped = { bitmap, _ ->
                previewBitmap = bitmap
                onSetRootBackground(bitmap, opacity, blur, scrim)
                cropSource = null
            },
            ratioLabel = "9:20",
        )
        return
    }

    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.mobileBg)
            .statusBarsPadding(),
    ) {
        ThemeEditorHeader(appearance, onBack, onReset)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            AppearanceModePicker(
                selected = appearanceMode,
                appearance = appearance,
                modifier = Modifier
                    .padding(start = 20.dp, top = 12.dp, end = 20.dp),
                onSelect = onAppearanceModeChange,
            )
            ThemeSectionLabel(
                text = "编辑内容",
                appearance = appearance,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp),
            )
            ThemeEditorTabs(
                activeTab = activeTab,
                appearance = appearance,
                modifier = Modifier.padding(horizontal = 20.dp),
                onChange = { activeTab = it },
            )
            ThemeSectionLabel(
                text = "实时预览",
                appearance = appearance,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp),
            )
            val previewAppearance = appearance.copy(
                rootBackgroundOpacity = opacity,
                rootBackgroundBlur = blur,
                rootBackgroundScrim = scrim,
            )
            ProportionalHomePreview(
                appearance = previewAppearance,
                previewBitmap = previewBitmap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .padding(horizontal = 20.dp),
            )
            if (activeTab == ThemeEditorTab.Palette) {
                FullWidthAction(
                    label = "从图片取色",
                    icon = AppIconPaths.Palette,
                    appearance = appearance,
                    modifier = Modifier
                        .padding(start = 20.dp, top = 14.dp, end = 20.dp)
                        .fillMaxWidth(),
                    onClick = { launcher.launch("image/*") },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 14.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FullWidthAction(
                        label = "选择图片",
                        icon = AppIconPaths.Image,
                        appearance = appearance,
                        modifier = Modifier.weight(1f),
                        onClick = { launcher.launch("image/*") },
                    )
                    SecondaryAction(
                        label = "调节",
                        icon = AppIconPaths.Gear,
                        appearance = appearance,
                        modifier = Modifier.weight(1f),
                        onClick = { tuningOpen = true },
                    )
                }
            }
        }
    }

    if (tuningOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                onTuneRootBackground(opacity, blur, scrim)
                tuningOpen = false
            },
            sheetState = tuningSheetState,
            containerColor = appearance.mobileSurface,
            contentColor = appearance.mobileText,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 34.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(appearance.mobileMuted.copy(alpha = 0.28f)),
                )
            },
        ) {
            RootBackgroundTuningSheet(
                appearance = appearance,
                opacity = opacity,
                blur = blur,
                scrim = scrim,
                canClear = appearance.rootBackgroundImagePath.isNotBlank() || previewBitmap != null,
                onOpacityChange = { opacity = it },
                onBlurChange = { blur = it },
                onScrimChange = { scrim = it },
                onSave = { onTuneRootBackground(opacity, blur, scrim) },
                onClear = {
                    previewBitmap = null
                    onClearRootBackground()
                },
                onDone = {
                    onTuneRootBackground(opacity, blur, scrim)
                    tuningOpen = false
                },
            )
        }
    }
}
