package com.eleckoi.android.feature.modelconfig.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.MobileRootSurface
import com.eleckoi.android.foundation.design.components.GroupRow
import com.eleckoi.android.foundation.design.components.MobileEmptyState
import com.eleckoi.android.foundation.design.components.MobileRootActionHeader
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.themedListRowClickable
import com.eleckoi.android.foundation.design.selectionPalette

private class ModelsRootEditorState {
    var collapsedGeneral by mutableStateOf(false)

    fun toggleGeneralCollapsed() {
        collapsedGeneral = !collapsedGeneral
    }
}

@Composable
private fun rememberModelsRootEditorState(): ModelsRootEditorState {
    return remember { ModelsRootEditorState() }
}

@Composable
fun ModelsRootPage(
    models: ModelConfigCollection?,
    appearance: AppearanceTheme,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenModel: (String, String) -> Unit,
) {
    val configs = models?.configs.orEmpty()
    val activeConfigId = models?.activeConfigId.orEmpty()
    val editorState = rememberModelsRootEditorState()
    val generalSection = modelLibrarySections.first { it.id == ModelLibrarySectionId.General }
    val imageSection = modelLibrarySections.first { it.id == ModelLibrarySectionId.Image }

    with(editorState) {
    val visibleProviders = visibleModelProviders(configs)
    val generalProviders = visibleProviders.filter { it.section == ModelLibrarySectionId.General }
    val imageProviders = visibleProviders.filter { it.section == ModelLibrarySectionId.Image }
    val voiceProviders = visibleProviders.filter { it.section == ModelLibrarySectionId.Voice }

    MobileRootSurface(
        appearance = appearance,
        header = {
            MobileRootActionHeader(
                title = "模型",
                appearance = appearance,
                onOpenSidebar = onOpenSidebar,
                onSearch = onSearch,
                onAdd = onAdd,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp),
        ) {
            item {
                GroupRow(
                    title = generalSection.title,
                    count = generalProviders.size,
                    placeholder = "",
                    appearance = appearance,
                    collapsed = collapsedGeneral,
                    onClick = ::toggleGeneralCollapsed,
                )
            }
            if (!collapsedGeneral) {
                if (generalProviders.isEmpty()) {
                    item { MobileEmptyState("没有匹配的模型库", appearance) }
                }
                items(generalProviders, key = { it.id }) { provider ->
                    val first = firstConfigForProvider(configs, provider.id, activeConfigId)
                    ModelProviderRow(
                        provider = provider,
                        summary = latestConfigSummary(configs, provider, activeConfigId),
                        appearance = appearance,
                        onClick = { onOpenModel(provider.id, first?.id.orEmpty()) },
                    )
                }
            }
            if (imageProviders.isNotEmpty()) {
                item {
                    GroupRow(
                        title = imageSection.title,
                        count = imageProviders.size,
                        placeholder = "",
                        appearance = appearance,
                    )
                }
                items(imageProviders, key = { it.id }) { provider ->
                    val first = firstConfigForProvider(configs, provider.id, activeConfigId)
                    ModelProviderRow(
                        provider = provider,
                        summary = latestConfigSummary(configs, provider, activeConfigId),
                        appearance = appearance,
                        onClick = { onOpenModel(provider.id, first?.id.orEmpty()) },
                    )
                }
            }
            if (voiceProviders.isNotEmpty()) {
                item {
                    GroupRow(
                        title = modelLibrarySections.first { it.id == ModelLibrarySectionId.Voice }.title,
                        count = voiceProviders.size,
                        placeholder = "",
                        appearance = appearance,
                    )
                }
                items(voiceProviders, key = { it.id }) { provider ->
                    val first = firstConfigForProvider(configs, provider.id, activeConfigId)
                    ModelProviderRow(
                        provider = provider,
                        summary = latestConfigSummary(configs, provider, activeConfigId),
                        appearance = appearance,
                        onClick = { onOpenModel(provider.id, first?.id.orEmpty()) },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ModelProviderRow(
    provider: ModelProviderMeta,
    summary: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val selection = appearance.selectionPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .themedListRowClickable(appearance = appearance, onClick = onClick)
            .padding(start = 22.dp, end = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            ModelProviderIcon(provider.id, provider.initials, appearance, Modifier.size(38.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(provider.label, color = selection.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, color = selection.mutedText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileMuted, iconSize = 19.dp, strokeWidth = 1.9f)
    }
}
