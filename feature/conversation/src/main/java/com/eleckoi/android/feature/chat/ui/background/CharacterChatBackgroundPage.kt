package com.eleckoi.android.feature.chat.ui.background

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.model.AppDefaultChatBackground
import com.eleckoi.android.feature.characters.model.CustomChatBackground
import com.eleckoi.android.feature.characters.model.GlobalChatBackground
import com.eleckoi.android.feature.chat.ui.layout.ChatBackground
import com.eleckoi.android.feature.chat.ui.layout.ChatPreviewBubble
import com.eleckoi.android.feature.chat.ui.layout.rememberChatPreviewMetrics
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.ErrorDialog
import com.eleckoi.android.foundation.design.components.ImageCropPage
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.common.BackgroundTunerPanel
import com.eleckoi.android.foundation.design.components.common.TunerSliderRow
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.saveBitmapToCache
import com.eleckoi.android.feature.preferences.NewCharacterBackground
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val DefaultOpacity = 0.72f
private const val DefaultBlur = 0f
private const val DefaultScrim = 0.22f

@Composable
fun CharacterChatBackgroundPage(
    characterName: String,
    defaultBackgroundPath: String,
    backgroundPath: String,
    backgroundOpacity: Float,
    backgroundBlur: Float,
    backgroundScrim: Float,
    newCharacterBackground: NewCharacterBackground,
    appearance: AppearanceTheme,
    errorMessage: String,
    onSave: (File?, Float, Float, Float, Boolean) -> Unit,
    onSetGlobal: (File, Float, Float, Float) -> Unit,
    onUseAppDefault: () -> Unit,
    onUseCharacterCard: () -> Unit,
    onUseCustom: () -> Unit,
    onUseGlobal: () -> Unit,
    onNewCharacterBackgroundChange: (NewCharacterBackground) -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val storedFile = remember(backgroundPath) {
        backgroundPath
            .takeIf {
                it.isNotBlank() &&
                    it != AppDefaultChatBackground &&
                    it != CustomChatBackground &&
                    it != GlobalChatBackground
            }
            ?.let(::File)
            ?.takeIf(File::exists)
    }
    val defaultFile = remember(defaultBackgroundPath) {
        defaultBackgroundPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::exists)
    }
    val globalFile = remember(appearance.textureImagePath) {
        appearance.textureImagePath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::exists)
    }
    val storedOrigin = when {
        backgroundPath == AppDefaultChatBackground -> BackgroundOrigin.AppDefault
        backgroundPath == CustomChatBackground -> BackgroundOrigin.CharacterCustom
        backgroundPath == GlobalChatBackground -> BackgroundOrigin.Global
        storedFile != null && defaultFile != null &&
            storedFile.absolutePath == defaultFile.absolutePath -> BackgroundOrigin.CharacterCard
        storedFile != null -> BackgroundOrigin.CharacterCustom
        defaultFile != null -> BackgroundOrigin.CharacterCard
        else -> BackgroundOrigin.AppDefault
    }

    val initialOpacity = when (storedOrigin) {
        BackgroundOrigin.Global -> appearance.textureOpacity
        BackgroundOrigin.CharacterCustom,
        BackgroundOrigin.CharacterCard,
        -> backgroundOpacity
        BackgroundOrigin.AppDefault -> DefaultOpacity
    }.coerceIn(0f, 1f)
    val initialBlur = when (storedOrigin) {
        BackgroundOrigin.Global -> appearance.textureBlur
        BackgroundOrigin.CharacterCustom,
        BackgroundOrigin.CharacterCard,
        -> backgroundBlur
        BackgroundOrigin.AppDefault -> DefaultBlur
    }.coerceIn(0f, 24f)
    val initialScrim = when (storedOrigin) {
        BackgroundOrigin.Global -> appearance.textureScrim
        BackgroundOrigin.CharacterCustom,
        BackgroundOrigin.CharacterCard,
        -> backgroundScrim
        BackgroundOrigin.AppDefault -> DefaultScrim
    }.coerceIn(0f, 1f)

    var selectedFile by remember(backgroundPath, appearance.textureImagePath) {
        mutableStateOf<File?>(null)
    }
    var submittedFile by remember(backgroundPath, appearance.textureImagePath) {
        mutableStateOf<File?>(null)
    }
    var originOverride by remember(backgroundPath, appearance.textureImagePath) {
        mutableStateOf<BackgroundOrigin?>(null)
    }
    var opacity by remember(characterName) { mutableStateOf(initialOpacity) }
    var blur by remember(characterName) { mutableStateOf(initialBlur) }
    var scrim by remember(characterName) { mutableStateOf(initialScrim) }
    var sliderInteractionActive by remember(characterName) { mutableStateOf(false) }
    var panelExpanded by remember { mutableStateOf(true) }
    var cropSource by remember { mutableStateOf<Uri?>(null) }
    var confirmGlobal by remember { mutableStateOf(false) }

    val origin = originOverride ?: storedOrigin
    val previewFile = when (origin) {
        BackgroundOrigin.AppDefault -> null
        BackgroundOrigin.CharacterCard -> defaultFile
        BackgroundOrigin.Global -> selectedFile ?: globalFile ?: defaultFile
        BackgroundOrigin.CharacterCustom -> selectedFile ?: storedFile
    }
    val hasBackground = previewFile != null
    val tuningChanged = hasBackground && (
        opacity != initialOpacity || blur != initialBlur || scrim != initialScrim
        )
    val isDirty = selectedFile != null || tuningChanged
    val metrics = rememberChatPreviewMetrics()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        cropSource = uri
    }
    cropSource?.let { source ->
        ImageCropPage(
            sourceUri = source,
            title = "裁剪聊天背景",
            cropAspect = 9f / 20f,
            circularFrame = false,
            outputWidth = 1080,
            appearance = appearance,
            onBack = { cropSource = null },
            onCropped = { bitmap, _ ->
                selectedFile = saveBitmapToCache(
                    dir = context.cacheDir,
                    bitmap = bitmap,
                    prefix = "chat-background",
                    format = Bitmap.CompressFormat.JPEG,
                    quality = 94,
                )
                if (origin != BackgroundOrigin.Global) {
                    originOverride = BackgroundOrigin.CharacterCustom
                }
                cropSource = null
            },
        )
        return
    }

    // A tuning change on the automatic card image first persists that image as a character choice.
    // Global mode is the only mode whose image and tuning are written to the shared appearance.
    LaunchedEffect(selectedFile, opacity, blur, scrim, sliderInteractionActive, origin) {
        if (!isDirty || sliderInteractionActive || !hasBackground) return@LaunchedEffect
        delay(400)
        val global = origin == BackgroundOrigin.Global
        val fileToSubmit = when {
            selectedFile != null && selectedFile != submittedFile -> selectedFile
            !global && storedFile == null && tuningChanged && previewFile != submittedFile -> previewFile
            else -> null
        }
        onSave(fileToSubmit, opacity, blur, scrim, global)
        if (fileToSubmit != null) submittedFile = fileToSubmit
    }

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        ChatBackground(
            appearance = appearance,
            characterBackgroundPath = when (origin) {
                BackgroundOrigin.AppDefault -> AppDefaultChatBackground
                BackgroundOrigin.CharacterCard -> defaultFile?.absolutePath.orEmpty()
                BackgroundOrigin.Global -> GlobalChatBackground
                BackgroundOrigin.CharacterCustom ->
                    previewFile?.absolutePath ?: CustomChatBackground
            },
            defaultCharacterBackgroundPath = defaultBackgroundPath,
            characterBackgroundOpacity = opacity,
            characterBackgroundBlur = blur,
            characterBackgroundScrim = scrim,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(Brush.verticalGradient(listOf(Color(0x4D000000), Color.Transparent))),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x52141F1F))
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.Back, Color.White, iconSize = 18.dp)
            }
            Text(
                "聊天背景",
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 92.dp, start = 14.dp, end = 14.dp),
        ) {
            ChatPreviewBubble(
                text = "你好",
                user = false,
                appearance = appearance,
                metrics = metrics,
            )
            Spacer(modifier = Modifier.height(metrics.turnSpacing))
            ChatPreviewBubble(
                text = "今天想聊什么？",
                user = true,
                appearance = appearance,
                metrics = metrics,
            )
        }

        BackgroundTunerPanel(
            appearance = appearance,
            expanded = panelExpanded,
            summary = listOf(
                when (origin) {
                    BackgroundOrigin.AppDefault -> "纯色背景"
                    BackgroundOrigin.CharacterCard -> "角色立绘"
                    BackgroundOrigin.CharacterCustom -> "自定义图片"
                    BackgroundOrigin.Global -> "共享背景"
                },
            ) + if (hasBackground) listOf("阅读遮罩 ${(scrim * 100).roundToInt()}%") else emptyList(),
            modifier = Modifier.align(Alignment.BottomCenter),
            onSetExpanded = { panelExpanded = it },
        ) {
            BackgroundModePicker(
                origin = origin,
                characterCardEnabled = defaultFile != null,
                globalEnabled = globalFile != null || previewFile != null,
                appearance = appearance,
                onSelect = { target ->
                    when (target) {
                        BackgroundOrigin.AppDefault -> {
                            originOverride = target
                            selectedFile = null
                            opacity = DefaultOpacity
                            blur = DefaultBlur
                            scrim = DefaultScrim
                            onUseAppDefault()
                        }
                        BackgroundOrigin.CharacterCard -> {
                            if (defaultFile != null) {
                                originOverride = target
                                selectedFile = null
                                opacity = DefaultOpacity
                                blur = DefaultBlur
                                scrim = DefaultScrim
                                onUseCharacterCard()
                            }
                        }
                        BackgroundOrigin.CharacterCustom -> {
                            if (origin != BackgroundOrigin.CharacterCustom) {
                                originOverride = target
                                selectedFile = null
                                opacity = DefaultOpacity
                                blur = DefaultBlur
                                scrim = DefaultScrim
                                onUseCustom()
                            }
                        }
                        BackgroundOrigin.Global -> when {
                            origin == BackgroundOrigin.Global -> Unit
                            origin == BackgroundOrigin.CharacterCustom && previewFile != null ->
                                confirmGlobal = true
                            globalFile != null -> {
                                originOverride = target
                                selectedFile = null
                                opacity = appearance.textureOpacity
                                blur = appearance.textureBlur
                                scrim = appearance.textureScrim
                                onUseGlobal()
                            }
                            previewFile != null -> confirmGlobal = true
                            else -> Unit
                        }
                    }
                },
            )

            if (hasBackground) {
                TunerSliderRow(
                    title = "背景透明度",
                    value = opacity,
                    range = 0.12f..1f,
                    appearance = appearance,
                    step = 0.01f,
                    valueScale = 100f,
                    decimalPlaces = 0,
                    suffix = "%",
                    onValueChange = { opacity = it },
                    onSliderInteractionStart = { sliderInteractionActive = true },
                    onSliderInteractionFinished = { sliderInteractionActive = false },
                )
                TunerSliderRow(
                    title = "背景模糊",
                    value = blur,
                    range = 0f..24f,
                    appearance = appearance,
                    step = 1f,
                    decimalPlaces = 0,
                    onValueChange = { blur = it },
                    onSliderInteractionStart = { sliderInteractionActive = true },
                    onSliderInteractionFinished = { sliderInteractionActive = false },
                )
                TunerSliderRow(
                    title = "阅读遮罩",
                    value = scrim,
                    range = 0f..1f,
                    appearance = appearance,
                    step = 0.01f,
                    valueScale = 100f,
                    decimalPlaces = 0,
                    suffix = "%",
                    onValueChange = { scrim = it },
                    onSliderInteractionStart = { sliderInteractionActive = true },
                    onSliderInteractionFinished = { sliderInteractionActive = false },
                )
            }

            if (origin == BackgroundOrigin.CharacterCustom) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(appearance.mobileText)
                        .noRippleClickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (hasBackground) "更换图片" else "选择图片",
                        color = appearance.mobileSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            NewCharacterBackgroundPicker(
                selected = newCharacterBackground,
                appearance = appearance,
                onSelect = onNewCharacterBackgroundChange,
            )
        }

        if (confirmGlobal) {
            ConfirmDialog(
                title = "设为共享背景？",
                message = "将当前图片设为共享背景。其他没有单独设置背景的角色会使用它，已上传独立背景的角色不受影响。",
                appearance = appearance,
                confirmText = "设为共享",
                onDismiss = { confirmGlobal = false },
                onConfirm = {
                    val file = previewFile
                    confirmGlobal = false
                    if (file != null) {
                        originOverride = BackgroundOrigin.Global
                        selectedFile = null
                        onSetGlobal(file, opacity, blur, scrim)
                    }
                },
            )
        }

        if (errorMessage.isNotBlank()) {
            ErrorDialog(
                message = errorMessage,
                appearance = appearance,
                onDismiss = onDismissError,
            )
        }
    }
}

