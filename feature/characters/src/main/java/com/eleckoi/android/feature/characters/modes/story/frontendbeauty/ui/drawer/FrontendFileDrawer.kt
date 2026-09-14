package com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun FrontendFileDrawer(
    characterName: String,
    projects: List<FrontendProject>,
    selectedProjectId: String?,
    appearance: AppearanceTheme,
    onClose: () -> Unit,
    onCreateHtmlTheme: () -> Unit,
    onEdit: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = appearance.mobileSurface,
        drawerContentColor = appearance.mobileText,
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(appearance.mobileBlue.copy(alpha = 0.13f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeSvgIcon(AppIconPaths.PictureFrame, appearance.mobileBlue, iconSize = 21.dp)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = "前端文件",
                        color = appearance.mobileText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = characterName.trim().ifBlank { "当前角色" },
                        color = appearance.mobileMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .noRippleClickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeSvgIcon(AppIconPaths.X, appearance.mobileMuted, iconSize = 22.dp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(appearance.mobileLine),
            )

            FrontendFileToolbar(
                appearance = appearance,
                onCreateHtmlTheme = onCreateHtmlTheme,
                onImport = onImport,
            )

            Text(
                text = "前端项目",
                modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp),
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )

            if (projects.isEmpty()) {
                EmptyFrontendFiles(appearance)
            } else {
                projects.forEach { project ->
                    FrontendProjectFiles(
                        project = project,
                        selected = project.id == selectedProjectId,
                        appearance = appearance,
                        onEdit = { onEdit(project.id) },
                        onDelete = { onDelete(project.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptyFrontendFiles(appearance: AppearanceTheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .border(1.dp, appearance.mobileLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StrokeSvgIcon(AppIconPaths.Menu, appearance.mobileSoft, iconSize = 23.dp)
        Text(
            text = "暂无项目文件",
            color = appearance.mobileText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "导入后将在这里显示完整文件树",
            color = appearance.mobileMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun FrontendFileToolbar(
    appearance: AppearanceTheme,
    onCreateHtmlTheme: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .background(appearance.mobileSearchBg, RoundedCornerShape(12.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FrontendFileToolButton(
            icon = AppIconPaths.Pencil,
            label = "编写 HTML",
            appearance = appearance,
            onClick = onCreateHtmlTheme,
            modifier = Modifier.weight(1f),
        )
        FrontendFileToolButton(
            icon = AppIconPaths.Import,
            label = "导入 HTML / ZIP",
            appearance = appearance,
            onClick = onImport,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FrontendProjectFiles(
    project: FrontendProject,
    selected: Boolean,
    appearance: AppearanceTheme,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .border(
                1.dp,
                if (selected) appearance.mobileBlue.copy(alpha = 0.5f) else appearance.mobileLine,
                RoundedCornerShape(12.dp),
            )
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    color = appearance.mobileText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Text("当前使用", color = appearance.mobileBlue, fontSize = 10.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .noRippleClickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.Pencil, appearance.mobileMuted, iconSize = 17.dp)
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .noRippleClickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.Trash, appearance.mobileMuted, iconSize = 17.dp)
            }
        }
        project.files.take(8).forEach { path ->
            Text(
                text = "· $path",
                color = appearance.mobileMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (project.files.size > 8) {
            Text(
                text = "另有 ${project.files.size - 8} 个文件",
                color = appearance.mobileSoft,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun FrontendFileToolButton(
    icon: List<String>,
    label: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        StrokeSvgIcon(icon, appearance.mobileText, iconSize = 20.dp)
        Text(
            text = label,
            color = appearance.mobileText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
