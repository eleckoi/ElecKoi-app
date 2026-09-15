package com.eleckoi.android.feature.studio.ui.assistant.workspace.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.workspace.model.CreatorConversation
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.DshFolderGlyph
import com.eleckoi.android.foundation.design.components.DshIconPaths
import com.eleckoi.android.foundation.design.components.DshTreeDisclosureGlyph
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.dshTreeRowEntrance

@Composable
internal fun DrawerSearchField(
    query: String,
    appearance: AppearanceTheme,
    onQueryChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(0.5.dp, appearance.mobileLine.copy(alpha = 0.62f), RoundedCornerShape(26.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = appearance.mobileMuted,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            singleLine = true,
            textStyle = TextStyle(color = appearance.mobileText, fontSize = 14.sp),
            decorationBox = { inner ->
                Box {
                    if (query.isBlank()) {
                        Text("搜索对话内容…", color = appearance.mobileMuted, fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
        if (query.isNotBlank()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "清空搜索", tint = appearance.mobileMuted)
            }
        }
    }
}

@Composable
internal fun DrawerProjectGroup(
    workspace: CreatorWorkspace,
    query: String,
    pinned: Boolean,
    expanded: Boolean,
    showAll: Boolean,
    activeConversationId: String?,
    projectMenuExpanded: Boolean,
    conversationMenuId: String?,
    appearance: AppearanceTheme,
    onToggleExpanded: () -> Unit,
    onToggleShowAll: () -> Unit,
    onCreateConversation: () -> Unit,
    onOpenProjectMenu: () -> Unit,
    onDismissProjectMenu: () -> Unit,
    onTogglePin: () -> Unit,
    onRenameProject: () -> Unit,
    onDeleteProject: () -> Unit,
    onOpenConversation: (CreatorConversation) -> Unit,
    onOpenConversationMenu: (String) -> Unit,
    onDismissConversationMenu: () -> Unit,
    onRenameConversation: (CreatorConversation) -> Unit,
    onDeleteConversation: (CreatorConversation) -> Unit,
) {
    val conversations = workspace.drawerConversations(query)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Transparent)
                .clickable(onClick = onToggleExpanded)
                .padding(start = 10.dp, end = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshFolderIcon(
                expanded = expanded,
                tint = appearance.mobileText,
                modifier = Modifier.size(21.dp),
            )
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                DshTreeDisclosureGlyph(
                    expanded = expanded,
                    tint = appearance.mobileMuted,
                )
            }
            Text(
                workspace.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 7.dp),
                color = appearance.mobileText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                IconButton(onClick = onOpenProjectMenu, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = "项目菜单",
                        modifier = Modifier.size(19.dp),
                        tint = appearance.mobileMuted,
                    )
                }
                DropdownMenu(
                    expanded = projectMenuExpanded,
                    onDismissRequest = onDismissProjectMenu,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = appearance.mobileSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, appearance.mobileLine),
                ) {
                    DropdownMenuItem(
                        text = { Text(if (pinned) "取消置顶" else "置顶项目") },
                        leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                        onClick = onTogglePin,
                    )
                    DropdownMenuItem(
                        text = { Text("重命名项目") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = onRenameProject,
                    )
                    DropdownMenuItem(
                        text = { Text("删除项目") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = onDeleteProject,
                    )
                }
            }
            IconButton(onClick = onCreateConversation, modifier = Modifier.size(36.dp)) {
                FilledSvgIcon(
                    paths = DshIconPaths.NewChat,
                    color = appearance.mobileMuted,
                    iconSize = 22.dp,
                    viewportSize = DshIconPaths.Viewport16,
                    modifier = Modifier.semantics { contentDescription = "新建对话" },
                )
            }
        }
        if (expanded) {
            val visible = if (showAll) conversations else conversations.take(5)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                visible.forEach { conversation ->
                    key(conversation.id) {
                        DrawerConversationRow(
                            conversation = conversation,
                            active = activeConversationId == conversation.id,
                            menuExpanded = conversationMenuId == conversation.id,
                            appearance = appearance,
                            modifier = Modifier.dshTreeRowEntrance(),
                            onOpen = { onOpenConversation(conversation) },
                            onOpenMenu = { onOpenConversationMenu(conversation.id) },
                            onDismissMenu = onDismissConversationMenu,
                            onRename = { onRenameConversation(conversation) },
                            onDelete = { onDeleteConversation(conversation) },
                        )
                    }
                }
            }
            if (conversations.size > 5) {
                Text(
                    text = if (showAll) "收起" else "展开显示其余 ${conversations.size - 5} 条",
                    modifier = Modifier
                        .padding(start = 43.dp, top = 2.dp, bottom = 5.dp)
                        .clickable(onClick = onToggleShowAll)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                )
            }
            if (conversations.isEmpty()) {
                Text(
                    "暂无对话",
                    modifier = Modifier.padding(start = 51.dp, top = 5.dp, bottom = 8.dp),
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun DshFolderIcon(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    DshFolderGlyph(
        expanded = expanded,
        tint = tint,
        modifier = modifier.semantics {
            contentDescription = if (expanded) "收起项目" else "展开项目"
        },
        iconSize = 21.dp,
    )
}

@Composable
private fun DrawerConversationRow(
    conversation: CreatorConversation,
    active: Boolean,
    menuExpanded: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) appearance.mobileText.copy(alpha = 0.065f) else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(start = 58.dp, end = 37.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            conversation.title,
            modifier = Modifier.weight(1f),
            color = appearance.mobileText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
            IconButton(onClick = onOpenMenu, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = "对话菜单",
                    modifier = Modifier.size(18.dp),
                    tint = appearance.mobileMuted,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onDismissMenu,
                shape = RoundedCornerShape(14.dp),
                containerColor = appearance.mobileSurface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, appearance.mobileLine),
            ) {
                DropdownMenuItem(
                    text = { Text("重命名对话") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = onRename,
                )
                DropdownMenuItem(
                    text = { Text("删除对话") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
internal fun DrawerSectionTitle(
    title: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        color = appearance.mobileMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}
