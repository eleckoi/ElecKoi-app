package com.eleckoi.android.feature.modelconfig.ui.modelpicker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppSearchField
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.feature.modelconfig.ui.configVersionName
import com.eleckoi.android.feature.modelconfig.ui.hasModelConfigContent
import com.eleckoi.android.feature.modelconfig.ui.modelProviders
import com.eleckoi.android.feature.modelconfig.ui.normalizeProviderId
import com.eleckoi.android.feature.modelconfig.ui.providerMeta
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.fieldPalette

private const val VersionSearchThreshold = 6

internal data class ModelVersionGroup(
    val providerId: String,
    val label: String,
    val initials: String,
    val configs: List<ModelConfig>,
)

@Composable
internal fun ModelVersionsPage(
    groups: List<ModelVersionGroup>,
    totalVersions: Int,
    selectedChatConfigId: String,
    selectedImageConfigId: String,
    query: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onSelectConfig: (ModelConfig) -> Unit,
    onOpenConfig: (ModelConfig) -> Unit,
    leadingChoice: ModelPickerLeadingChoice? = null,
) {
    // Past a screenful, scrolling to find a version costs more than typing three letters.
    val searchable = totalVersions > VersionSearchThreshold
    val visible = remember(groups, query, searchable) {
        if (!searchable) groups else filterGroups(groups, query)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (searchable) {
            ModelSearchField(
                value = query,
                placeholder = "搜索 $totalVersions 个配置版本",
                appearance = appearance,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
                onValueChange = onQueryChange,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 24.dp),
        ) {
            if (leadingChoice != null && query.isBlank()) {
                item(key = "leading-choice", contentType = "card") {
                    SheetGroupCard(appearance) {
                        LeadingModelChoiceRow(
                            choice = leadingChoice,
                            appearance = appearance,
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                item("versions-empty", contentType = "empty") {
                    Text(
                        if (totalVersions == 0) {
                            "还没有配置好的模型，先到「模型」页新建一个。"
                        } else {
                            "没有匹配的配置版本"
                        },
                        color = appearance.mobileMuted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 20.dp),
                    )
                }
            }
            visible.forEach { group ->
                item(key = "label-${group.providerId}", contentType = "label") {
                    ProviderGroupLabel(group, appearance)
                }
                item(key = "card-${group.providerId}", contentType = "card") {
                    SheetGroupCard(appearance) {
                        group.configs.forEachIndexed { index, config ->
                            if (index > 0) SheetGroupDivider(appearance)
                            ModelVersionRow(
                                config = config,
                                selected = if (config.isImageGenerationConfig()) {
                                    config.id == selectedImageConfigId
                                } else {
                                    config.id == selectedChatConfigId
                                },
                                appearance = appearance,
                                onSelect = { onSelectConfig(config) },
                                onOpen = { onOpenConfig(config) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeadingModelChoiceRow(
    choice: ModelPickerLeadingChoice,
    appearance: AppearanceTheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = choice.onSelect)
            .padding(start = 2.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelSelectionIndicator(
            selected = choice.selected,
            appearance = appearance,
            onClick = choice.onSelect,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp, top = 11.dp, bottom = 11.dp),
        ) {
            Text(
                text = choice.title,
                color = appearance.mobileText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = choice.subtitle,
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// The provider is the group label, so it is stated once per group instead of once per row. That
// is the same shape as the settings page's 个性化 / 开发 sections, and it puts a seam every few
// rows so a long list reads as several short ones.
@Composable
private fun ProviderGroupLabel(group: ModelVersionGroup, appearance: AppearanceTheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelProviderIcon(
            providerId = group.providerId,
            initials = group.initials,
            appearance = appearance,
            modifier = Modifier.size(15.dp),
        )
        Text(
            group.label,
            modifier = Modifier.padding(start = 6.dp),
            color = appearance.mobileMuted,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Selection and navigation are deliberately separate: the check activates the saved config and
// keeps its default model, while the labelled row opens that config for an optional model change.
@Composable
private fun ModelVersionRow(
    config: ModelConfig,
    selected: Boolean,
    appearance: AppearanceTheme,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelSelectionIndicator(
            selected = selected,
            appearance = appearance,
            onClick = onSelect,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .noRippleClickable(onClick = onOpen)
                .padding(start = 2.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                configVersionName(config),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                color = appearance.mobileText,
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StrokeSvgIcon(
                AppIconPaths.ChevronRight,
                appearance.mobileSoft,
                iconSize = 15.dp,
            )
        }
    }
}

@Composable
private fun ModelSelectionIndicator(
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp).noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(7.dp)
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(shape)
                .background(if (selected) appearance.mobileBlue else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (selected) appearance.mobileBlue else appearance.mobileSoft,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                StrokeSvgIcon(
                    paths = AppIconPaths.Check,
                    color = appearance.mobileSurface,
                    iconSize = 14.dp,
                    strokeWidth = 2.2f,
                )
            }
        }
    }
}

// mobileSurface is the sheet's own colour, so a group card painted in it has no edge at all. On
// this sheet the recessed tone is what draws the boundary.

@Composable
internal fun ConcreteModelsPage(
    config: ModelConfig,
    selectedConfigId: String,
    selectedModel: String,
    query: String,
    refreshing: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val all = remember(config) { concreteModels(config) }
    val models = remember(all, query) {
        val key = query.trim().lowercase()
        all.filter { option ->
            key.isBlank() || option.id.lowercase().contains(key)
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelSearchField(
                value = query,
                placeholder = if (all.isEmpty()) "搜索模型" else "搜索 ${all.size} 个模型",
                appearance = appearance,
                modifier = Modifier.weight(1f),
                onValueChange = onQueryChange,
            )
            RefreshButton(refreshing, appearance, onRefresh)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 24.dp),
        ) {
            if (models.isEmpty()) {
                item("empty-model", contentType = "empty") {
                    Text(
                        when {
                            all.isNotEmpty() -> "没有匹配的模型"
                            refreshing -> "正在读取模型列表"
                            else -> "还没有模型，点右上角刷新，或检查这个版本的地址和密钥。"
                        },
                        color = appearance.mobileMuted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                }
            }
            items(models, key = { it.id }, contentType = { "model" }) { model ->
                ConcreteModelRow(
                    model = model,
                    selected = config.id == selectedConfigId && model.id == selectedModel,
                    appearance = appearance,
                    onClick = { onSelect(model.id) },
                )
            }
        }
    }
}

@Composable
private fun ConcreteModelRow(
    model: ModelOption,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable(onClick = onClick)
                .padding(start = 2.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelSelectionIndicator(
                selected = selected,
                appearance = appearance,
                onClick = onClick,
            )
            Text(
                model.id,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
                color = appearance.mobileText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(appearance.mobileLine),
        )
    }
}

@Composable
private fun ModelSearchField(
    value: String,
    placeholder: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    AppSearchField(
        keyword = value,
        placeholder = placeholder,
        appearance = appearance,
        modifier = modifier,
        height = 44.dp,
        fontSize = 14.sp,
        iconSize = 15.dp,
        onKeywordChange = onValueChange,
    )
}

@Composable
private fun RefreshButton(refreshing: Boolean, appearance: AppearanceTheme, onRefresh: () -> Unit) {
    val spin = if (refreshing) {
        val transition = rememberInfiniteTransition(label = "modelRefreshSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
            label = "modelRefreshAngle",
        )
        angle
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(appearance.fieldPalette().container)
            .noRippleClickable(enabled = !refreshing, onClick = onRefresh),
        contentAlignment = Alignment.Center,
    ) {
        StrokeSvgIcon(
            paths = AppIconPaths.Refresh,
            color = if (refreshing) appearance.mobileSoft else appearance.mobileMuted,
            iconSize = 16.dp,
            modifier = Modifier.graphicsLayer(rotationZ = spin),
        )
    }
}

internal fun pickerGroups(configs: List<ModelConfig>, selectedConfigId: String): List<ModelVersionGroup> {
    val eligible = configs.filter { hasModelConfigContent(it) || it.id == selectedConfigId }
        .ifEmpty { configs }
    val providerOrder = modelProviders.mapIndexed { index, provider -> provider.id to index }.toMap()
    return eligible
        .groupBy { normalizeProviderId(it.provider) }
        .map { (providerId, providerConfigs) ->
            val provider = providerMeta(providerId)
            ModelVersionGroup(
                providerId = providerId,
                label = provider.label,
                initials = provider.initials,
                configs = providerConfigs.sortedWith(
                    compareByDescending<ModelConfig> { it.id == selectedConfigId }
                        .thenBy(::configVersionName),
                ),
            )
        }
        // The group holding the current selection comes first, so the sheet opens on the thing
        // you are already using rather than on whatever the provider order happens to put on top.
        .sortedWith(
            compareByDescending<ModelVersionGroup> { group -> group.configs.any { it.id == selectedConfigId } }
                .thenBy { providerOrder[it.providerId] ?: Int.MAX_VALUE }
                .thenBy { it.label },
        )
}

private fun filterGroups(groups: List<ModelVersionGroup>, query: String): List<ModelVersionGroup> {
    val key = query.trim().lowercase()
    if (key.isBlank()) return groups
    return groups.mapNotNull { group ->
        if (group.label.lowercase().contains(key)) return@mapNotNull group
        val matched = group.configs.filter { config ->
            configVersionName(config).lowercase().contains(key) || config.model.lowercase().contains(key)
        }
        if (matched.isEmpty()) null else group.copy(configs = matched)
    }
}

private fun concreteModels(config: ModelConfig?): List<ModelOption> {
    config ?: return emptyList()
    val current = config.model.trim()
    val options = config.modelOptions.toMutableList()
    if (current.isNotBlank() && options.none { it.id == current }) options.add(0, ModelOption(current, current))
    return options.distinctBy(ModelOption::id)
}
