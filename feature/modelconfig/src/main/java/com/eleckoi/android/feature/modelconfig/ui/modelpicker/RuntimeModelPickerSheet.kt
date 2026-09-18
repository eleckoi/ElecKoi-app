package com.eleckoi.android.feature.modelconfig.ui.modelpicker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.MobileBottomSheetOverlay
import com.eleckoi.android.foundation.design.components.MobileBottomSheetHeader
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.isChatModelConfig
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.feature.modelconfig.ui.ModelApiFormatSheet
import com.eleckoi.android.feature.modelconfig.ui.apiFormatsForProvider
import com.eleckoi.android.feature.modelconfig.ui.configVersionName
import com.eleckoi.android.feature.modelconfig.ui.modelOptionsKey
import com.eleckoi.android.feature.modelconfig.ui.providerMeta
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.fieldPalette

private enum class ModelPickerPage { Models, Params }

enum class ModelPickerConfigKind { Chat, Image }

data class ModelPickerLeadingChoice(
    val title: String,
    val subtitle: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

@Composable
fun ModelPickerSheet(
    visible: Boolean = true,
    configs: List<ModelConfig>,
    selectedConfigId: String,
    selectedModel: String,
    characterImagePrompt: String = "",
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onSelect: (configId: String, modelId: String) -> Unit,
    onSaveConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onCharacterImagePromptChange: (String, (Result<String>) -> Unit) -> Unit = { _, callback ->
        callback(Result.failure(IllegalStateException("当前页面没有角色提示词")))
    },
    onRefreshModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    title: String = "选择模型",
    leadingChoice: ModelPickerLeadingChoice? = null,
    showParameters: Boolean = true,
    configKind: ModelPickerConfigKind = ModelPickerConfigKind.Chat,
    imageParamsMode: ImageModelParamsMode = ImageModelParamsMode.AutomaticIllustration,
    showCharacterImagePrompt: Boolean = true,
) {
    CompositionLocalProvider(LocalContentColor provides appearance.mobileText) {
        // Root-level callers keep the overlay host; nested editors reuse the content below inside
        // their existing window-level sheet so navigation does not create a second modal.
        MobileBottomSheetOverlay(
            visible = visible,
            appearance = appearance,
            onDismiss = onDismiss,
            sheetModifier = Modifier.fillMaxHeight(0.88f),
            showHandle = true,
        ) {
            ModelPickerContent(
                configs = configs,
                selectedConfigId = selectedConfigId,
                selectedModel = selectedModel,
                characterImagePrompt = characterImagePrompt,
                appearance = appearance,
                onDismiss = onDismiss,
                onSelect = onSelect,
                onSaveConfig = onSaveConfig,
                onCharacterImagePromptChange = onCharacterImagePromptChange,
                onRefreshModels = onRefreshModels,
                title = title,
                leadingChoice = leadingChoice,
                showParameters = showParameters,
                configKind = configKind,
                imageParamsMode = imageParamsMode,
                showCharacterImagePrompt = showCharacterImagePrompt,
                backHandlerEnabled = visible,
            )
        }
    }
}

@Composable
fun ColumnScope.ModelPickerContent(
    configs: List<ModelConfig>,
    selectedConfigId: String,
    selectedModel: String,
    characterImagePrompt: String = "",
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSelect: (configId: String, modelId: String) -> Unit,
    onSaveConfig: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    onCharacterImagePromptChange: (String, (Result<String>) -> Unit) -> Unit = { _, callback ->
        callback(Result.failure(IllegalStateException("当前页面没有角色提示词")))
    },
    onRefreshModels: (ModelConfig, (Result<ModelConfig>) -> Unit) -> Unit,
    title: String = "选择模型",
    leadingChoice: ModelPickerLeadingChoice? = null,
    showParameters: Boolean = true,
    configKind: ModelPickerConfigKind = ModelPickerConfigKind.Chat,
    imageParamsMode: ImageModelParamsMode = ImageModelParamsMode.AutomaticIllustration,
    showCharacterImagePrompt: Boolean = true,
    backHandlerEnabled: Boolean = true,
) {
    var refreshedCatalogs by remember { mutableStateOf<Map<String, RefreshedModelCatalog>>(emptyMap()) }
    val runtimeConfigs = remember(configs, refreshedCatalogs) {
        applyRefreshedModelCatalogs(configs, refreshedCatalogs)
    }
    val visibleConfigs = remember(runtimeConfigs, configKind) {
        when (configKind) {
            ModelPickerConfigKind.Chat -> runtimeConfigs.filter(ModelConfig::isChatModelConfig)
            ModelPickerConfigKind.Image -> runtimeConfigs.filter(ModelConfig::isImageGenerationConfig)
        }
    }
    var focusedConfigId by rememberSaveable { mutableStateOf(selectedConfigId) }
    val selectedChatConfig = visibleConfigs.firstOrNull { it.id == selectedConfigId }
    val focusedConfig = visibleConfigs.firstOrNull { it.id == focusedConfigId }
        ?: selectedChatConfig
        ?: visibleConfigs.firstOrNull().takeUnless { leadingChoice?.selected == true }
    val activeModel = if (focusedConfig?.id == selectedConfigId) {
        selectedModel.ifBlank { focusedConfig.model }
    } else {
        focusedConfig?.model.orEmpty()
    }
    val activeModelLabel = activeModel
    // Grouped by provider, but on one page. Provider used to be its own drill-down level, which
    // cost a tap and a screen to say what a grey label says in place; flattening it entirely was
    // worse, because five providers with five versions each is one 25-row run with no seams.
    val groups = remember(visibleConfigs, focusedConfig?.id) {
        pickerGroups(visibleConfigs, focusedConfig?.id.orEmpty())
    }
    val versions = remember(groups) { groups.flatMap(ModelVersionGroup::configs) }

    var page by rememberSaveable { mutableStateOf(ModelPickerPage.Models) }
    var openConfigId by rememberSaveable { mutableStateOf("") }
    var versionQuery by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var refreshingConfigId by remember { mutableStateOf("") }
    var apiFormatSheetOpen by rememberSaveable { mutableStateOf(false) }

    val openConfig = versions.firstOrNull { it.id == openConfigId }
    val selectedImageConfigId = selectedConfigId.takeIf {
        configKind == ModelPickerConfigKind.Image
    }.orEmpty()
    val canNavigateBack = openConfig != null || page != ModelPickerPage.Models || onBack != null
    val navigateBack: () -> Unit = {
        when {
            openConfig != null -> openConfigId = ""
            page != ModelPickerPage.Models -> page = ModelPickerPage.Models
            else -> onBack?.invoke()
        }
    }
    // A config can disappear underneath us while its model list is open.
    LaunchedEffect(openConfigId, openConfig) {
        if (openConfigId.isNotBlank() && openConfig == null) openConfigId = ""
    }
    BackHandler(enabled = backHandlerEnabled) {
        if (canNavigateBack) navigateBack() else onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .imePadding(),
    ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ModelPickerHeader(
                        title = openConfig?.let(::configVersionName) ?: title,
                        subtitle = if (leadingChoice?.selected == true) leadingChoice.title else activeModelLabel,
                        subtitleProviderId = focusedConfig?.provider.orEmpty(),
                        showSubtitle = openConfig == null,
                        appearance = appearance,
                        onBack = navigateBack.takeIf { canNavigateBack },
                        onDismiss = onDismiss,
                    )
                    // The segment picks between two views of the same config; inside a config's
                    // model list it would be picking between the current page and another page.
                    if (openConfig == null && showParameters) {
                        ModelPickerSegment(page, appearance) { page = it }
                    }
                    when {
                        openConfig != null -> ConcreteModelsPage(
                            config = openConfig,
                            selectedConfigId = focusedConfig?.id.orEmpty(),
                            selectedModel = activeModel,
                            query = query,
                            refreshing = openConfig.id == refreshingConfigId,
                            appearance = appearance,
                            modifier = Modifier.weight(1f),
                            onQueryChange = { query = it },
                            onSelect = { modelId ->
                                focusedConfigId = openConfig.id
                                onSelect(openConfig.id, modelId)
                            },
                            onRefresh = {
                                if (refreshingConfigId.isNotEmpty()) return@ConcreteModelsPage
                                refreshingConfigId = openConfig.id
                                val connectionKey = modelOptionsKey(openConfig)
                                onRefreshModels(openConfig) { result ->
                                    result.onSuccess { refreshed ->
                                        refreshedCatalogs = refreshedCatalogs + (
                                            openConfig.id to RefreshedModelCatalog(
                                                connectionKey = connectionKey,
                                                config = refreshed,
                                            )
                                        )
                                    }
                                    refreshingConfigId = ""
                                }
                            },
                        )

                        page == ModelPickerPage.Models -> ModelVersionsPage(
                            groups = groups,
                            totalVersions = versions.size,
                            selectedChatConfigId = selectedConfigId,
                            selectedImageConfigId = selectedImageConfigId,
                            query = versionQuery,
                            appearance = appearance,
                            modifier = Modifier.weight(1f),
                            onQueryChange = { versionQuery = it },
                            onSelectConfig = { config ->
                                focusedConfigId = config.id
                                if (config.isImageGenerationConfig()) {
                                    onSelect(config.id, config.model)
                                } else {
                                    val defaultModel = config.model.ifBlank {
                                        config.modelOptions.firstOrNull()?.id.orEmpty()
                                    }
                                    onSelect(config.id, defaultModel)
                                }
                            },
                            onOpenConfig = { config ->
                                query = ""
                                focusedConfigId = config.id
                                if (config.isImageGenerationConfig()) {
                                    page = ModelPickerPage.Params
                                    openConfigId = ""
                                } else {
                                    openConfigId = config.id
                                }
                            },
                            leadingChoice = leadingChoice,
                        )

                        focusedConfig?.isImageGenerationConfig() == true -> ImageModelParamsPage(
                            selectedConfig = focusedConfig,
                            characterImagePrompt = characterImagePrompt,
                            appearance = appearance,
                            modifier = Modifier.weight(1f),
                            onSaveConfig = onSaveConfig,
                            onCharacterImagePromptChange = onCharacterImagePromptChange,
                            mode = imageParamsMode,
                            showCharacterImagePrompt = showCharacterImagePrompt,
                        )

                        configKind == ModelPickerConfigKind.Image -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Text(
                                text = "还没有配置图片生成模型，请先到“模型”页新建一个。",
                                color = appearance.mobileMuted,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        }

                        else -> ModelParamsPage(
                            selectedConfig = focusedConfig,
                            selectedModel = activeModel,
                            appearance = appearance,
                            modifier = Modifier.weight(1f),
                            onOpenApiFormat = { apiFormatSheetOpen = true },
                            onSaveConfig = onSaveConfig,
                        )
                    }
                }
                if (
                    apiFormatSheetOpen &&
                    focusedConfig != null &&
                    activeModel.isNotBlank()
                ) {
                    val activeOption = focusedConfig.modelOptions.firstOrNull { it.id == activeModel }
                    ModelApiFormatSheet(
                        selected = activeOption?.apiFormatOverride,
                        inherited = focusedConfig.apiFormat,
                        allowInherited = true,
                        formats = apiFormatsForProvider(focusedConfig.provider),
                        appearance = appearance,
                        onClose = { apiFormatSheetOpen = false },
                        onSelect = { format ->
                            apiFormatSheetOpen = false
                            val current = activeOption ?: ModelOption(activeModel, activeModel)
                            val updated = current.copy(apiFormatOverride = format)
                            val options = focusedConfig.modelOptions.toMutableList().apply {
                                val index = indexOfFirst { it.id == activeModel }
                                if (index >= 0) this[index] = updated else add(updated)
                            }
                            onSaveConfig(
                                focusedConfig.copy(model = activeModel, modelOptions = options),
                            ) {}
                        },
                    )
                }
    }
}

internal data class RefreshedModelCatalog(
    val connectionKey: String,
    val config: ModelConfig,
)

/**
 * A model read is runtime discovery, not a save. Keep the fresh catalog visible without writing it
 * to Room, and invalidate it as soon as the saved connection fields change underneath the sheet.
 */
internal fun applyRefreshedModelCatalogs(
    configs: List<ModelConfig>,
    catalogs: Map<String, RefreshedModelCatalog>,
): List<ModelConfig> = configs.map { persisted ->
    val refreshed = catalogs[persisted.id]
        ?.takeIf { it.connectionKey == modelOptionsKey(persisted) }
        ?.config
    if (refreshed == null) persisted else persisted.copy(modelOptions = refreshed.modelOptions)
}

@Composable
private fun ModelPickerHeader(
    title: String,
    subtitle: String,
    subtitleProviderId: String,
    showSubtitle: Boolean,
    appearance: AppearanceTheme,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    MobileBottomSheetHeader(
        title = title,
        appearance = appearance,
        onBack = onBack,
        onDismiss = onDismiss,
        subtitleContent = if (showSubtitle) {
            {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (subtitle.isNotBlank()) {
                        ModelProviderIcon(
                            providerId = subtitleProviderId,
                            initials = providerMeta(subtitleProviderId).initials,
                            appearance = appearance,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        subtitle.ifBlank { "尚未选择" },
                        modifier = Modifier.padding(start = if (subtitle.isNotBlank()) 5.dp else 0.dp),
                        color = appearance.mobileMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else null,
    )
}

// 32dp, not 48. It used to be a full-height pill in semibold, louder than everything it switches
// between; the segment is a signpost, and the content is what matters.
@Composable
private fun ModelPickerSegment(
    page: ModelPickerPage,
    appearance: AppearanceTheme,
    onChange: (ModelPickerPage) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(appearance.fieldPalette().container)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // "模型设置" under a heading that already says 选择模型 spent two of its four characters
        // repeating the heading.
        ModelPickerSegmentItem("模型", page == ModelPickerPage.Models, appearance, Modifier.weight(1f)) {
            onChange(ModelPickerPage.Models)
        }
        ModelPickerSegmentItem("参数", page == ModelPickerPage.Params, appearance, Modifier.weight(1f)) {
            onChange(ModelPickerPage.Params)
        }
    }
}

@Composable
private fun ModelPickerSegmentItem(
    label: String,
    selected: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) appearance.mobileSurface else Color.Transparent)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) appearance.mobileText else appearance.mobileMuted,
            fontSize = 12.5.sp,
        )
    }
}
