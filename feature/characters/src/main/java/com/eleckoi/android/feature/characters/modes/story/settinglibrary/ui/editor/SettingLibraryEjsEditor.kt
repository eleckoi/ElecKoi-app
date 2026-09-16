package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.ui.shared.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun EjsReferenceEditorPage(
    entry: SettingLibraryEntry,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onEntryChange: ((SettingLibraryEntry) -> SettingLibraryEntry) -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val scrollState = rememberScrollState()
    var actionMenuExpanded by remember(entry.id) { mutableStateOf(false) }
    var confirmDelete by remember(entry.id) { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    PinnedStatusScaffold(
        appearance = appearance,
        imeAware = false,
        backgroundColor = appearance.storyEditorPalette().pageBg,
    ) {
        EntryEditorTopBar(
            title = "EJS引用设定",
            appearance = appearance,
            onBack = onBack,
            menuVisible = true,
            menuExpanded = actionMenuExpanded,
            onMenuExpandedChange = { actionMenuExpanded = it },
            onDelete = { confirmDelete = true },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .background(appearance.storyEditorPalette().pageBg)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            EntryTitleField(
                value = entry.title,
                appearance = appearance,
                scrollState = scrollState,
                imeBottomPx = imeBottomPx,
                placeholder = "条目标题（供 getwi 按名称读取）",
                onChange = { value -> onEntryChange { it.copy(title = value.take(60)) } },
            )
            PlainInput(
                label = "设定正文",
                value = entry.content,
                appearance = appearance,
                scrollState = scrollState,
                imeBottomPx = imeBottomPx,
                minHeight = 300,
                placeholder = "填写供一个或多个 EJS 控制器读取的内容",
                immersiveTitle = "设定正文",
                groupedStyle = true,
                onChange = { value -> onEntryChange { it.copy(content = value) } },
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除这条EJS引用设定？",
            message = "使用它的控制器将无法再通过 getwi 读取“${entry.title.ifBlank { "未命名EJS引用设定" }}”。",
            appearance = appearance,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDeleteConfirmed()
            },
        )
    }
}

@Composable
internal fun EjsControllerReferencesPanel(
    references: List<SettingLibraryEntry>,
    appearance: AppearanceTheme,
    onOpenReference: (String) -> Unit,
) {
    if (references.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = StoryEditorCardSpacing)
            .clip(RoundedCornerShape(18.dp))
            .background(appearance.mobileSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = appearance.mobileBlue,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "EJS引用设定（${references.size}）",
                modifier = Modifier.weight(1f).padding(start = 9.dp),
                color = appearance.mobileText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        references
            .sortedWith(compareBy<SettingLibraryEntry> { it.treeViewOrder }.thenBy { it.title })
            .forEach { reference ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 43.dp)
                        .height(1.dp)
                        .background(appearance.mobileMuted.copy(alpha = 0.09f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .noRippleClickable { onOpenReference(reference.id) }
                        .padding(start = 16.dp, end = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = if (reference.enabled) appearance.mobileBlue else appearance.mobileMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        reference.title.ifBlank { "未命名EJS引用设定" },
                        modifier = Modifier.weight(1f).padding(horizontal = 9.dp),
                        color = if (reference.enabled) appearance.mobileText else appearance.mobileMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (reference.enabled) "已开启" else "已关闭",
                        color = appearance.mobileMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 5.dp),
                    )
                    StrokeSvgIcon(
                        AppIconPaths.ChevronRight,
                        appearance.mobileMuted,
                        iconSize = 15.dp,
                        strokeWidth = 1.7f,
                    )
                }
            }
    }
}

internal fun literalGetwiTargets(template: String): Set<String> {
    return LiteralGetwiTarget.findAll(template).mapTo(linkedSetOf()) { it.groupValues[2] }
}

private val LiteralGetwiTarget = Regex(
    """getwi\s*\(\s*(?:(?:null|[\"'][^\"']*[\"'])\s*,\s*)?([\"'])([^\"']+)\1""",
    RegexOption.IGNORE_CASE,
)
