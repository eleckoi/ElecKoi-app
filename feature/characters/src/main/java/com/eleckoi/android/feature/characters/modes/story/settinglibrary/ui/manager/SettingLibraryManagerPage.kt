package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.themedListRowClickable
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StoryEditorCardSpacing
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import kotlinx.coroutines.delay

internal val ManagerCardShape = RoundedCornerShape(18.dp)

/**
 * Setting library management.
 *
 * The name and the version picker both stay, but they no longer look alike. Before, the name sat
 * in a plain white row and the picker sat in an identical plain white row directly below it,
 * showing the same string — two controls that looked the same and did different things. The name
 * is now a well, which is how this app draws anything you can type into, and the versions are a
 * list. The label was never what told them apart.
 *
 * The picker was also a dropdown that unfolded over the actions beneath it. A list has nothing to
 * unfold over.
 */
@Composable
internal fun SettingLibraryManagerPage(
    activeName: String,
    versions: List<SettingLibraryVersion>,
    activeVersionId: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onActiveNameChange: (String) -> Unit,
    onSelectVersion: (SettingLibraryVersion) -> Unit,
    onCreateVersion: () -> Unit,
    onMergeFromCharacter: () -> Unit,
    onImportAsNewVersion: () -> Unit,
    onExport: () -> Unit,
    onDeleteActiveVersion: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var visible by remember { mutableStateOf(false) }

    BackHandler(enabled = visible) { visible = false }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(visible) {
        if (!visible) {
            delay(230)
            onDismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
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
                    .noRippleClickable {}
                    .verticalScroll(scrollState)
                    .padding(bottom = 30.dp),
            ) {
                ManagerTopBar("设定库管理", appearance) { visible = false }
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            VersionNameField(
                                value = activeName,
                                appearance = appearance,
                                onValueChange = onActiveNameChange,
                            )

                            ManagerCard(appearance, modifier = Modifier.padding(top = StoryEditorCardSpacing)) {
                                ManagerCardTitle("版本", appearance)
                                versions.forEachIndexed { index, version ->
                                    if (index > 0) ManagerRowDivider(appearance)
                                    VersionRow(
                                        version = version,
                                        active = version.id == activeVersionId,
                                        appearance = appearance,
                                        onSelect = { onSelectVersion(version) },
                                    )
                                }
                                if (versions.isNotEmpty()) ManagerRowDivider(appearance)
                                ManagerRow(
                                    icon = SettingLibraryIcons.Plus,
                                    title = "新建版本",
                                    appearance = appearance,
                                    accent = true,
                                    showChevron = false,
                                    onClick = onCreateVersion,
                                )
                            }

                            ManagerCard(appearance, modifier = Modifier.padding(top = StoryEditorCardSpacing)) {
                                ManagerCardTitle("导入与导出", appearance)
                                ManagerRow(
                                    icon = SettingLibraryIcons.Merge,
                                    title = "并入设定库",
                                    appearance = appearance,
                                    onClick = onMergeFromCharacter,
                                )
                                ManagerRowDivider(appearance)
                                ManagerRow(
                                    icon = SettingLibraryIcons.Import,
                                    title = "导入设定库",
                                    appearance = appearance,
                                    onClick = onImportAsNewVersion,
                                )
                                ManagerRowDivider(appearance)
                                ManagerRow(
                                    icon = SettingLibraryIcons.Export,
                                    title = "导出设定库",
                                    appearance = appearance,
                                    onClick = onExport,
                                )
                            }

                            Box(modifier = Modifier.padding(top = StoryEditorCardSpacing)) {
                                ManagerCard(appearance) {
                                    ManagerRow(
                                        icon = SettingLibraryIcons.Trash,
                                        title = "删除当前版本",
                                        appearance = appearance,
                                        danger = true,
                                        showChevron = false,
                                        onClick = onDeleteActiveVersion,
                                    )
                                }
                            }
                }
            }
        }
    }
}

/**
 * The active version's name, editable in place.
 *
 * On the page's own surface, not a recessed one. The search field's well is that control's
 * identity — it is tuned at 38dp around a leading magnifier, and stretched to a name row it is
 * just a grey slab with a word in it, the only thing on a page of white cards that is not one.
 * What separates this from the version rows below is direct text editing: the rows carry a
 * selection check and you tap them, while this field carries a cursor and accepts text.
 */
@Composable
private fun VersionNameField(
    value: String,
    appearance: AppearanceTheme,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ManagerCardShape)
            .background(appearance.mobileSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            "当前版本名称",
            color = appearance.mobileText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        AppInsetTextField(
            value = value,
            onValueChange = { onValueChange(it.take(60)) },
            appearance = appearance,
            placeholder = "待命名",
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
            textStyle = TextStyle(
                color = appearance.mobileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun VersionRow(
    version: SettingLibraryVersion,
    active: Boolean,
    appearance: AppearanceTheme,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .themedListRowClickable(appearance = appearance, onClick = onSelect)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (active) {
                StrokeSvgIcon(SettingLibraryIcons.Check, appearance.mobileBlue, iconSize = 19.dp, strokeWidth = 2f)
            }
        }
        Text(
            version.name.trim().ifBlank { "待命名" },
            modifier = Modifier.weight(1f).padding(start = 14.dp, end = 10.dp),
            color = appearance.mobileText,
            fontSize = 16.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ManagerTopBar(title: String, appearance: AppearanceTheme, onBack: () -> Unit) {
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
internal fun ManagerCardTitle(
    title: String,
    appearance: AppearanceTheme,
) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 15.dp, end = 18.dp, bottom = 5.dp),
        color = appearance.mobileText,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun ManagerCard(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ManagerCardShape)
            .background(appearance.mobileSurface),
        content = content,
    )
}

@Composable
internal fun ManagerRowDivider(appearance: AppearanceTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp)
            .height(1.dp)
            .background(appearance.mobileLine),
    )
}

@Composable
internal fun ManagerRow(
    icon: List<String>,
    title: String,
    appearance: AppearanceTheme,
    danger: Boolean = false,
    accent: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = when {
        danger -> ElecKoiDanger
        accent -> appearance.mobileBlue
        else -> appearance.mobileText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .themedListRowClickable(appearance = appearance, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeSvgIcon(icon, contentColor, iconSize = 21.dp, strokeWidth = 1.75f)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = 15.dp),
            color = contentColor,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showChevron) {
            StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileSoft, iconSize = 18.dp, strokeWidth = 1.7f)
        }
    }
}
