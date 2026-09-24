package com.eleckoi.android.feature.characters.modes.story.ui.shared

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.ElecKoiDanger
import kotlinx.coroutines.delay

private val ManagerGroupShape = RoundedCornerShape(18.dp)

internal data class ManagedFeatureVersion(
    val id: String,
    val name: String,
)

@Composable
internal fun FeatureVersionManagerSheet(
    title: String,
    showFeatureToggle: Boolean = true,
    showNameField: Boolean = true,
    showDeleteAction: Boolean = true,
    showTransferActions: Boolean = true,
    deleteActionEnabled: Boolean = true,
    featureTitle: String,
    featureDescription: String,
    namePlaceholder: String,
    nameSectionTitle: String? = null,
    versionSectionTitle: String,
    createActionTitle: String,
    importActionTitle: String = "导入为新版本",
    exportActionTitle: String = "导出当前版本",
    deleteActionTitle: String,
    name: String,
    enabled: Boolean,
    versions: List<ManagedFeatureVersion>,
    activeVersionId: String,
    appearance: AppearanceTheme,
    onNameChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCreateVersion: () -> Unit,
    onSelectVersion: (ManagedFeatureVersion) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDeleteVersion: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val visible = remember { mutableStateOf(false) }
    var versionMenuOpen by remember(activeVersionId, versions.size) { mutableStateOf(false) }

    fun requestClose() {
        visible.value = false
    }

    BackHandler(enabled = visible.value) { requestClose() }
    LaunchedEffect(Unit) { visible.value = true }
    LaunchedEffect(visible.value) {
        if (!visible.value) {
            delay(230)
            onDismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible.value,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
            ) + fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 360f),
            ) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appearance.mobileBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .noRippleClickable {}
                    .verticalScroll(scrollState)
                    .padding(bottom = 28.dp),
            ) {
                ManagerTopBar(title, appearance, ::requestClose)
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    if (showFeatureToggle) {
                        ManagerSectionCard("功能", appearance, Modifier.padding(top = 8.dp)) {
                            FeatureEnabledSettingRow(featureTitle, featureDescription, enabled, appearance, onEnabledChange)
                        }
                    }

                    if (showNameField) {
                        ManagerSectionCard(
                            title = nameSectionTitle ?: "${title.removeSuffix("管理")}名称",
                            appearance = appearance,
                            modifier = Modifier.padding(top = if (showFeatureToggle) StoryEditorCardSpacing else 8.dp),
                        ) {
                            ManagerNameRow(name, namePlaceholder, appearance, onNameChange)
                        }
                    }

                    ManagerSectionCard(
                        title = versionSectionTitle,
                        appearance = appearance,
                        modifier = Modifier.padding(top = if (showFeatureToggle || showNameField) StoryEditorCardSpacing else 8.dp),
                    ) {
                        VersionSelectBox(
                            versions = versions,
                            activeVersionId = activeVersionId,
                            expanded = versionMenuOpen,
                            appearance = appearance,
                            emptyName = namePlaceholder,
                            onExpandedChange = { versionMenuOpen = it },
                            onSelectVersion = { version ->
                                versionMenuOpen = false
                                onSelectVersion(version)
                            },
                        )
                    }
                    ManagerActionGroup(
                        appearance = appearance,
                        rows = buildList {
                            add(ManagerActionRow(createActionTitle, AppIconPaths.Plus, onCreateVersion))
                            if (showTransferActions) {
                                add(ManagerActionRow(importActionTitle, AppIconPaths.Import, onImport))
                                add(ManagerActionRow(exportActionTitle, AppIconPaths.Export, onExport))
                            }
                        },
                        modifier = Modifier.padding(top = StoryEditorCardSpacing),
                    )
                    if (showDeleteAction) {
                        ManagerActionGroup(
                            appearance = appearance,
                            rows = listOf(
                                ManagerActionRow(
                                    title = deleteActionTitle,
                                    icon = AppIconPaths.Trash,
                                    onClick = onDeleteVersion,
                                    enabled = deleteActionEnabled,
                                ),
                            ),
                            modifier = Modifier.padding(top = StoryEditorCardSpacing),
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerTopBar(title: String, appearance: AppearanceTheme, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(999.dp))
                .background(appearance.mobileSurface)
                .noRippleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(AppIconPaths.Back, appearance.mobileText, iconSize = 23.dp, strokeWidth = 1.9f)
        }
        Text(title, color = appearance.mobileText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun FeatureEnabledSettingRow(
    title: String,
    description: String,
    enabled: Boolean,
    appearance: AppearanceTheme,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(ManagerGroupShape)
            .background(appearance.mobileSurface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = appearance.mobileText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                description,
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 5.dp, end = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StoryToolSwitch(checked = enabled, appearance = appearance, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun ManagerSectionCard(
    title: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ManagerGroupShape)
            .background(appearance.mobileSurface),
    ) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 15.dp, end = 18.dp, bottom = 2.dp),
            color = appearance.mobileText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun ManagerNameRow(
    value: String,
    placeholder: String,
    appearance: AppearanceTheme,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(ManagerGroupShape)
            .background(appearance.mobileSurface)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AppInsetTextField(
            value = value,
            onValueChange = onChange,
            appearance = appearance,
            placeholder = placeholder,
            modifier = Modifier.height(44.dp),
            textStyle = TextStyle(
                color = appearance.mobileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
            ),
        )
    }
}

private data class ManagerActionRow(
    val title: String,
    val icon: List<String>,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun ManagerActionGroup(
    appearance: AppearanceTheme,
    rows: List<ManagerActionRow>,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth().clip(ManagerGroupShape).background(appearance.mobileSurface)) {
        rows.forEachIndexed { index, action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(if (action.enabled) Modifier.noRippleClickable(onClick = action.onClick) else Modifier)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val enabledAlpha = if (action.enabled) 1f else 0.36f
                val color = appearance.mobileText.copy(alpha = enabledAlpha)
                val iconColor = if (destructive) ElecKoiDanger.copy(alpha = enabledAlpha) else color
                StrokeSvgIcon(action.icon, iconColor, iconSize = 21.dp, strokeWidth = 1.75f)
                Text(
                    action.title,
                    modifier = Modifier.weight(1f).padding(start = 15.dp),
                    color = if (destructive) ElecKoiDanger.copy(alpha = enabledAlpha) else color,
                    fontSize = 16.sp,
                )
                StrokeSvgIcon(
                    AppIconPaths.ChevronRight,
                    appearance.mobileSoft.copy(alpha = enabledAlpha),
                    iconSize = 18.dp,
                    strokeWidth = 1.7f,
                )
            }
            if (index < rows.lastIndex) ManagerDivider(appearance)
        }
    }
}

@Composable
private fun ManagerDivider(appearance: AppearanceTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp)
            .height(1.dp)
            .background(appearance.mobileLine),
    )
}

@Composable
internal fun VersionSelectBox(
    versions: List<ManagedFeatureVersion>,
    activeVersionId: String,
    expanded: Boolean,
    appearance: AppearanceTheme,
    emptyName: String,
    onExpandedChange: (Boolean) -> Unit,
    onSelectVersion: (ManagedFeatureVersion) -> Unit,
) {
    val density = LocalDensity.current
    val activeVersion = versions.firstOrNull { it.id == activeVersionId } ?: versions.firstOrNull()
    var anchorWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                anchorWidth = with(density) { coordinates.size.width.toDp() }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(ManagerGroupShape)
                .background(appearance.mobileSurface)
                .noRippleClickable { onExpandedChange(!expanded) }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                activeVersion?.name?.trim()?.ifBlank { emptyName } ?: "暂无版本",
                modifier = Modifier.weight(1f),
                color = appearance.mobileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StrokeSvgIcon(
                AppIconPaths.ChevronDown,
                appearance.mobileSoft,
                modifier = Modifier.padding(start = 10.dp),
                iconSize = 18.dp,
                strokeWidth = 1.7f,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.versionDropdownWidth(anchorWidth).background(appearance.mobileSurface),
        ) {
            versions.forEachIndexed { index, version ->
                if (index > 0) ManagerDivider(appearance)
                val selected = version.id == activeVersionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(if (selected) appearance.mobileBlue.copy(alpha = 0.07f) else appearance.mobileSurface)
                        .noRippleClickable { onSelectVersion(version) }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        version.name.trim().ifBlank { emptyName },
                        modifier = Modifier.weight(1f),
                        color = appearance.mobileText,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) Text("当前", color = appearance.mobileMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun Modifier.versionDropdownWidth(width: Dp): Modifier = if (width > 0.dp) this.width(width) else this
