package com.eleckoi.android.feature.modelconfig.ui.modelpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.SquareSelectionCheck
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.engine.generation.model.ImageBackground
import com.eleckoi.android.engine.generation.model.ImageQuality
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.MaxStoryImagesPerTurn
import com.eleckoi.android.engine.generation.model.NovelAiSamplerCatalog
import com.eleckoi.android.engine.generation.model.imagePromptCompilerInstruction
import com.eleckoi.android.engine.generation.model.imageSizeError
import com.eleckoi.android.engine.generation.model.isOpenAiImageConfig
import com.eleckoi.android.feature.modelconfig.ui.ImageOutputSelector
import com.eleckoi.android.feature.modelconfig.ui.components.ModelInlineField
import com.eleckoi.android.feature.modelconfig.ui.NovelAiSamplerSelector
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger

@Composable
fun ImageModelParamsPage(
    selectedConfig: ModelConfig,
    characterImagePrompt: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onSaveConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onCharacterImagePromptChange: (String, (Result<String>) -> Unit) -> Unit,
    mode: ImageModelParamsMode,
    showCharacterImagePrompt: Boolean,
) {
    val current = selectedConfig.imageSettings
    val openAi = selectedConfig.isOpenAiImageConfig()
    val automaticIllustration = mode == ImageModelParamsMode.AutomaticIllustration
    var quality by rememberSaveable(selectedConfig.id, current.quality) {
        mutableStateOf(current.quality)
    }
    var background by rememberSaveable(selectedConfig.id, current.background) {
        mutableStateOf(current.background)
    }
    var width by rememberSaveable(selectedConfig.id, current.width) { mutableStateOf(current.width.toString()) }
    var height by rememberSaveable(selectedConfig.id, current.height) { mutableStateOf(current.height.toString()) }
    var steps by rememberSaveable(selectedConfig.id, current.steps) { mutableStateOf(current.steps.toString()) }
    var scale by rememberSaveable(selectedConfig.id, current.scale) { mutableStateOf(current.scale.toString()) }
    var sampler by rememberSaveable(selectedConfig.id, current.sampler) {
        mutableStateOf(NovelAiSamplerCatalog.normalizeApiValue(current.sampler))
    }
    var automaticImageCount by rememberSaveable(selectedConfig.id, current.automaticImageCount) {
        mutableStateOf(current.automaticImageCount)
    }
    var fixedImageCount by rememberSaveable(selectedConfig.id, current.fixedImageCount) {
        mutableStateOf(current.fixedImageCount.toString())
    }
    var automaticImageMin by rememberSaveable(selectedConfig.id, current.automaticImageMin) {
        mutableStateOf(current.automaticImageMin.toString())
    }
    var automaticImageMax by rememberSaveable(selectedConfig.id, current.automaticImageMax) {
        mutableStateOf(current.automaticImageMax.toString())
    }
    var promptCompilerInstruction by remember(
        selectedConfig.id,
        current.promptCompilerInstruction,
    ) {
        mutableStateOf(selectedConfig.imagePromptCompilerInstruction())
    }
    var promptPrefix by rememberSaveable(selectedConfig.id, current.promptPrefix) {
        mutableStateOf(current.promptPrefix)
    }
    var negativePrompt by rememberSaveable(selectedConfig.id, current.negativePrompt) {
        mutableStateOf(current.negativePrompt)
    }
    var rolePrompt by rememberSaveable(selectedConfig.id, characterImagePrompt) {
        mutableStateOf(characterImagePrompt)
    }
    var saveState by remember { mutableStateOf(ParamsSaveState.Idle) }

    val widthValue = width.toIntOrNull()
    val heightValue = height.toIntOrNull()
    val stepsValue = steps.toIntOrNull()
    val scaleValue = scale.toDoubleOrNull()
    val fixedImageCountValue = fixedImageCount.toIntOrNull()
    val automaticImageMinValue = automaticImageMin.toIntOrNull()
    val automaticImageMaxValue = automaticImageMax.toIntOrNull()
    val sizeError = imageSizeError(selectedConfig.provider, widthValue, heightValue)
    val widthError = if (openAi) sizeError != null else widthValue?.let { it in 512..2048 } != true
    val heightError = if (openAi) sizeError != null else heightValue?.let { it in 512..2048 } != true
    val stepsError = !openAi && stepsValue?.let { it in 1..50 } != true
    val scaleError = !openAi && (scaleValue == null || scaleValue !in 0.1..10.0)
    val fixedImageCountError = fixedImageCountValue !in 1..MaxStoryImagesPerTurn
    val automaticImageRangeError = automaticImageMinValue !in 1..MaxStoryImagesPerTurn ||
        automaticImageMaxValue !in 1..MaxStoryImagesPerTurn ||
        (automaticImageMinValue != null && automaticImageMaxValue != null &&
            automaticImageMinValue > automaticImageMaxValue)
    val imageCountError = automaticIllustration &&
        if (automaticImageCount) automaticImageRangeError else fixedImageCountError
    val automaticIllustrationSettingsChanged = automaticIllustration && (
        automaticImageCount != current.automaticImageCount ||
            fixedImageCountValue != current.fixedImageCount ||
            automaticImageMinValue != current.automaticImageMin ||
            automaticImageMaxValue != current.automaticImageMax ||
            promptCompilerInstruction.trim() != selectedConfig.imagePromptCompilerInstruction() ||
            promptPrefix.trim() != current.promptPrefix ||
            negativePrompt.trim() != current.negativePrompt
        )
    val settingsChanged = widthValue != null && heightValue != null &&
        !widthError && !heightError && !stepsError && !scaleError && !imageCountError && (
        widthValue != current.width || heightValue != current.height || stepsValue != current.steps ||
            scaleValue != current.scale || quality != current.quality || background != current.background ||
            sampler != NovelAiSamplerCatalog.normalizeApiValue(current.sampler) ||
            automaticIllustrationSettingsChanged
        )

    LaunchedEffect(
        settingsChanged,
        widthValue,
        heightValue,
        stepsValue,
        scaleValue,
        sampler,
        quality,
        background,
        automaticImageCount,
        fixedImageCountValue,
        automaticImageMinValue,
        automaticImageMaxValue,
        promptCompilerInstruction,
        promptPrefix,
        negativePrompt,
    ) {
        if (!settingsChanged) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        saveState = ParamsSaveState.Saving
        onSaveConfig(
            selectedConfig.copy(
                imageSettings = current.copy(
                    width = widthValue,
                    height = heightValue,
                    steps = if (openAi) current.steps else requireNotNull(stepsValue),
                    scale = if (openAi) current.scale else requireNotNull(scaleValue),
                    quality = quality,
                    background = background,
                    sampler = NovelAiSamplerCatalog.normalizeApiValue(sampler),
                    automaticImageCount = if (automaticIllustration) {
                        automaticImageCount
                    } else {
                        current.automaticImageCount
                    },
                    fixedImageCount = if (automaticIllustration) {
                        fixedImageCountValue ?: current.fixedImageCount
                    } else {
                        current.fixedImageCount
                    },
                    automaticImageMin = if (automaticIllustration) {
                        automaticImageMinValue ?: current.automaticImageMin
                    } else {
                        current.automaticImageMin
                    },
                    automaticImageMax = if (automaticIllustration) {
                        automaticImageMaxValue ?: current.automaticImageMax
                    } else {
                        current.automaticImageMax
                    },
                    promptCompilerInstruction = if (automaticIllustration) {
                        promptCompilerInstruction.trim()
                    } else {
                        current.promptCompilerInstruction
                    },
                    promptPrefix = if (automaticIllustration) {
                        promptPrefix.trim().take(4_000)
                    } else {
                        current.promptPrefix
                    },
                    negativePrompt = if (automaticIllustration) {
                        negativePrompt.trim().take(2_000)
                    } else {
                        current.negativePrompt
                    },
                ),
            ),
        ) { result -> saveState = if (result.isSuccess) ParamsSaveState.Saved else ParamsSaveState.Failed }
    }
    LaunchedEffect(rolePrompt, showCharacterImagePrompt) {
        if (!showCharacterImagePrompt) return@LaunchedEffect
        val normalized = rolePrompt.trim().take(4_000)
        if (normalized == characterImagePrompt) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        saveState = ParamsSaveState.Saving
        onCharacterImagePromptChange(normalized) { result ->
            saveState = if (result.isSuccess) ParamsSaveState.Saved else ParamsSaveState.Failed
        }
    }
    LaunchedEffect(saveState) {
        if (saveState != ParamsSaveState.Saved) return@LaunchedEffect
        kotlinx.coroutines.delay(1800)
        saveState = ParamsSaveState.Idle
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
    ) {
        if (automaticIllustration) item("image-count") {
            ParamsGroupLabel("剧情分镜数量", appearance)
            SheetGroupCard(appearance) {
                ImageCountModeRow(
                    label = "固定数量",
                    detail = "每轮严格生成指定张数",
                    selected = !automaticImageCount,
                    appearance = appearance,
                    onClick = { automaticImageCount = false },
                )
                if (!automaticImageCount) {
                    SheetGroupDivider(appearance)
                    ModelInlineField(
                        label = "每轮分镜数",
                        value = fixedImageCount,
                        placeholder = "1–$MaxStoryImagesPerTurn",
                        appearance = appearance,
                        keyboardType = KeyboardType.Number,
                        isError = fixedImageCountError,
                        onChange = { fixedImageCount = it.onlyDigits().take(2) },
                    )
                }
                SheetGroupDivider(appearance)
                ImageCountModeRow(
                    label = "自动数量",
                    detail = "Agent 根据剧情节点在可见范围内选择",
                    selected = automaticImageCount,
                    appearance = appearance,
                    onClick = { automaticImageCount = true },
                )
                if (automaticImageCount) {
                    SheetGroupDivider(appearance)
                    ModelInlineField(
                        label = "最少",
                        value = automaticImageMin,
                        placeholder = "1",
                        appearance = appearance,
                        keyboardType = KeyboardType.Number,
                        isError = automaticImageRangeError,
                        onChange = { automaticImageMin = it.onlyDigits().take(2) },
                    )
                    SheetGroupDivider(appearance)
                    ModelInlineField(
                        label = "最多",
                        value = automaticImageMax,
                        placeholder = MaxStoryImagesPerTurn.toString(),
                        appearance = appearance,
                        keyboardType = KeyboardType.Number,
                        isError = automaticImageRangeError,
                        onChange = { automaticImageMax = it.onlyDigits().take(2) },
                    )
                }
            }
        }
        item("image-size") {
            ParamsGroupLabel("绘画参数", appearance)
            SheetGroupCard(appearance) {
                ModelInlineField("宽度", width, if (openAi) "1024" else "832", appearance, isError = widthError) {
                    width = it.onlyDigits().take(4)
                }
                SheetGroupDivider(appearance)
                ModelInlineField("高度", height, if (openAi) "1536" else "1216", appearance, isError = heightError) {
                    height = it.onlyDigits().take(4)
                }
                SheetGroupDivider(appearance)
                if (openAi) {
                    ImageOutputSelector(
                        label = "质量",
                        selected = quality,
                        options = ImageQuality.entries,
                        optionLabel = ImageQuality::label,
                        appearance = appearance,
                    ) { quality = it }
                    SheetGroupDivider(appearance)
                    ImageOutputSelector(
                        label = "背景",
                        selected = background,
                        options = ImageBackground.entries,
                        optionLabel = ImageBackground::label,
                        appearance = appearance,
                    ) { background = it }
                } else {
                    ModelInlineField("步数", steps, "28", appearance, isError = stepsError) {
                        steps = it.onlyDigits().take(2)
                    }
                    SheetGroupDivider(appearance)
                    ModelInlineField("提示词相关性", scale, "5.0", appearance, isError = scaleError) {
                        scale = it.onlyDecimal().take(5)
                    }
                    SheetGroupDivider(appearance)
                    NovelAiSamplerSelector(
                        selectedApiValue = sampler,
                        appearance = appearance,
                        onSelect = { sampler = it },
                    )
                }
            }
        }
        if (automaticIllustration) item("image-prompts") {
            ParamsGroupLabel("提示词", appearance)
            SheetGroupCard(appearance) {
                ImagePromptField(
                    label = "生图动作指令",
                    hint = "配图开启时交给角色 Agent；<ACTION_CALL> 元素启动后台生图，正文中的 [[IMAGE:n]] 决定显示位置。可修改，清空后恢复默认",
                    value = promptCompilerInstruction,
                    appearance = appearance,
                    expanded = true,
                    onChange = { promptCompilerInstruction = it },
                )
                SheetGroupDivider(appearance)
                ImagePromptField(
                    label = "全局固定词",
                    hint = if (openAi) {
                        "描述所有图片共用的画风、构图与光线"
                    } else {
                        "每个角色都会原样插在最前面，例如画风、质量、镜头标签"
                    },
                    value = promptPrefix,
                    appearance = appearance,
                    onChange = { promptPrefix = it.take(4_000) },
                )
                SheetGroupDivider(appearance)
                if (showCharacterImagePrompt) {
                    ImagePromptField(
                        label = "当前角色专属词",
                        hint = if (openAi) "描述角色外观、服装和专属画风" else "原样插入；适合角色名、作品名、服装和专属画风标签",
                        value = rolePrompt,
                        appearance = appearance,
                        onChange = { rolePrompt = it.take(4_000) },
                    )
                    SheetGroupDivider(appearance)
                }
                ImagePromptField(
                    label = if (openAi) "避免的内容" else "负面追加词",
                    hint = if (openAi) {
                        "以自然语言说明不希望出现在图片中的内容"
                    } else {
                        "追加到默认负面词和本轮场景负面词之后"
                    },
                    value = negativePrompt,
                    appearance = appearance,
                    onChange = { negativePrompt = it.take(2_000) },
                )
            }
        }
        val statusMessage = when {
            imageCountError -> "分镜数量需要在 1 到 $MaxStoryImagesPerTurn 之间，且最少不能大于最多。"
            sizeError != null -> sizeError
            stepsError -> "步数需要在 1 到 50 之间。"
            scaleError -> "提示词相关性需要在 0.1 到 10 之间。"
            saveState == ParamsSaveState.Failed -> "保存失败，改一下再试。"
            saveState == ParamsSaveState.Saving -> "保存中…"
            saveState == ParamsSaveState.Saved -> "已保存"
            else -> null
        }
        if (statusMessage != null) item("image-note") {
            Text(
                text = statusMessage,
                color = if (
                    imageCountError || widthError || heightError || stepsError || scaleError ||
                        saveState == ParamsSaveState.Failed
                ) ElecKoiDanger else appearance.mobileMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 9.dp),
            )
        }
    }
}

@Composable
private fun ImageCountModeRow(
    label: String,
    detail: String,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareSelectionCheck(selected = selected, appearance = appearance)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, color = appearance.mobileText, fontSize = 14.5.sp)
            Text(detail, color = appearance.mobileMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun ImagePromptField(
    label: String,
    hint: String,
    value: String,
    appearance: AppearanceTheme,
    expanded: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, color = appearance.mobileText, fontSize = 14.5.sp)
        Text(hint, color = appearance.mobileMuted, fontSize = 11.sp, lineHeight = 16.sp)
        AppInsetTextField(
            value = value,
            onValueChange = onChange,
            appearance = appearance,
            singleLine = false,
            placeholder = if (expanded) "留空后自动恢复默认编译指令" else "留空则不插入",
            modifier = Modifier
                .height(if (expanded) 220.dp else 86.dp)
                .padding(top = 8.dp),
            shape = RoundedCornerShape(9.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
            textStyle = TextStyle(color = appearance.mobileText, fontSize = 13.sp, lineHeight = 18.sp),
        )
    }
}

// These values share one debounced model-option write. A full-width filled button was the heaviest
// thing on the sheet for a background write, so the note underneath doubles as its status line.

private fun String.onlyDecimal(): String {
    var dotSeen = false
    return filter { character ->
        when {
            character.isDigit() -> true
            character == '.' && !dotSeen -> {
                dotSeen = true
                true
            }
            else -> false
        }
    }
}
