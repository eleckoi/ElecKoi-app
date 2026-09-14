package com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon

private data class FrontendGalleryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val isImport: Boolean = false,
    val isHtmlCreator: Boolean = false,
    val isNative: Boolean = false,
)

@Composable
internal fun FrontendProjectGallery(
    projects: List<FrontendProject>,
    isImporting: Boolean,
    selectedProjectId: String?,
    appearance: AppearanceTheme,
    onSelect: (String?) -> Unit,
    onCreateHtmlTheme: () -> Unit,
    onEdit: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val galleryItems = remember(projects, isImporting) {
        buildList {
            add(
                FrontendGalleryItem(
                    id = "native",
                    title = "原生聊天界面",
                    subtitle = "App 内置",
                    isNative = true,
                ),
            )
            projects.forEach { project ->
                add(
                    FrontendGalleryItem(
                        id = project.id,
                        title = project.name,
                        subtitle = "${project.files.size} 个文件",
                    ),
                )
            }
            add(
                FrontendGalleryItem(
                    id = "create-html",
                    title = "编写 HTML 主题",
                    subtitle = "HTML / CSS / JS",
                    isHtmlCreator = true,
                ),
            )
            add(
                FrontendGalleryItem(
                    id = "import",
                    title = if (isImporting) "正在导入…" else "导入前端项目",
                    subtitle = "HTML / ZIP",
                    isImport = true,
                ),
            )
        }
    }
    val selectedFrontendId = selectedProjectId ?: "native"

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(appearance.mobileBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(galleryItems, key = { it.id }) { item ->
            FrontendProjectCard(
                item = item,
                selected = !item.isImport && !item.isHtmlCreator && item.id == selectedFrontendId,
                appearance = appearance,
                onEdit = if (!item.isNative && !item.isImport && !item.isHtmlCreator) {
                    { onEdit(item.id) }
                } else {
                    null
                },
                onClick = {
                    when {
                        item.isImport && !isImporting -> onImport()
                        item.isHtmlCreator -> onCreateHtmlTheme()
                        item.isNative -> onSelect(null)
                        else -> onSelect(item.id)
                    }
                },
            )
        }
    }
}

@Composable
private fun FrontendProjectCard(
    item: FrontendGalleryItem,
    selected: Boolean,
    appearance: AppearanceTheme,
    onEdit: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(294.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = appearance.mobileSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                appearance.mobileBlue.copy(alpha = 0.58f)
            } else {
                appearance.mobileLine.copy(alpha = 0.74f)
            },
        ),
        shadowElevation = if (selected) 0.dp else 1.dp,
    ) {
        Column {
            when {
                item.isImport -> ImportFrontendThumbnail(appearance)
                item.isHtmlCreator -> HtmlThemeThumbnail(appearance)
                item.isNative -> NativeChatThumbnail(appearance)
                else -> ImportedProjectThumbnail(item.title, appearance)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.title,
                        color = appearance.mobileText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle,
                        color = appearance.mobileMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.isImport || item.isHtmlCreator) {
                    StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileSoft, iconSize = 20.dp)
                } else {
                    onEdit?.let { edit ->
                        IconButton(onClick = edit, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "编辑 HTML",
                                tint = appearance.mobileMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    FrontendSelectionIndicator(selected = selected, appearance = appearance)
                }
            }
        }
    }
}

@Composable
private fun FrontendSelectionIndicator(
    selected: Boolean,
    appearance: AppearanceTheme,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(
                color = if (selected) appearance.mobileBlue else Color.Transparent,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = if (selected) appearance.mobileBlue else appearance.mobileLine,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "当前使用",
                tint = appearance.mobileAccentFg,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun NativeChatThumbnail(appearance: AppearanceTheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(appearance.mobileChatBg)
            .padding(11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(25.dp)
                    .background(appearance.mobileBlue.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.Bot, appearance.mobileBlue, iconSize = 14.dp)
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Box(
                    modifier = Modifier
                        .width(55.dp)
                        .height(6.dp)
                        .background(appearance.mobileText.copy(alpha = 0.82f), CircleShape),
                )
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .background(appearance.mobileMuted.copy(alpha = 0.34f), CircleShape),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        ThumbnailMessageBubble(
            modifier = Modifier.fillMaxWidth(0.78f),
            color = appearance.mobileChatMessageBg,
            lineColor = appearance.mobileChatMessageFg,
        )
        Spacer(modifier = Modifier.height(12.dp))
        ThumbnailMessageBubble(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .align(Alignment.End),
            color = appearance.mobileChatUserBg,
            lineColor = appearance.mobileChatUserFg,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(29.dp)
                .background(appearance.mobileComposerBg, RoundedCornerShape(16.dp))
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(4.dp)
                    .background(appearance.mobileMuted.copy(alpha = 0.25f), CircleShape),
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(appearance.mobileBlue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.Send, appearance.mobileAccentFg, iconSize = 10.dp)
            }
        }
    }
}

@Composable
private fun ThumbnailMessageBubble(
    modifier: Modifier,
    color: Color,
    lineColor: Color,
) {
    Column(
        modifier = modifier
            .background(color, RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(lineColor.copy(alpha = 0.42f), CircleShape),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(5.dp)
                .background(lineColor.copy(alpha = 0.24f), CircleShape),
        )
    }
}

@Composable
private fun ImportFrontendThumbnail(appearance: AppearanceTheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(appearance.mobileSearchBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(appearance.mobileSurface, RoundedCornerShape(10.dp))
                .border(1.dp, appearance.mobileLine, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(AppIconPaths.Plus, appearance.mobileText, iconSize = 24.dp)
        }
        Spacer(modifier = Modifier.height(11.dp))
        Text(
            text = "选择 HTML / ZIP",
            color = appearance.mobileText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HtmlThemeThumbnail(appearance: AppearanceTheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(appearance.mobileSearchBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(appearance.mobileBlue.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .border(1.dp, appearance.mobileBlue.copy(alpha = 0.24f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("</>", color = appearance.mobileBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(11.dp))
        Text(
            text = "粘贴 HTML 代码",
            color = appearance.mobileText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ImportedProjectThumbnail(
    name: String,
    appearance: AppearanceTheme,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(appearance.mobileSearchBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(appearance.mobileBlue.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("</>", color = appearance.mobileBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = name,
                modifier = Modifier.padding(horizontal = 18.dp),
                color = appearance.mobileMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
