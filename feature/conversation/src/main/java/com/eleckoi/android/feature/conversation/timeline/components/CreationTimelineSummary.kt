package com.eleckoi.android.feature.conversation.timeline.components

import com.eleckoi.android.feature.conversation.timeline.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.agent.api.AgentCommandActionType
import com.eleckoi.android.engine.agent.api.AgentApplyVariablePatchTool
import com.eleckoi.android.engine.agent.api.AgentApplySettingPatchTool
import com.eleckoi.android.engine.agent.api.AgentSettingFileMutationTools
import com.eleckoi.android.engine.agent.api.AgentSubagentTool
import com.eleckoi.android.engine.agent.api.AgentGlobSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGlobVariablesTool
import com.eleckoi.android.engine.agent.api.AgentGrepSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentGrepVariablesTool
import com.eleckoi.android.engine.agent.api.AgentReadSettingFilesTool
import com.eleckoi.android.engine.agent.api.AgentReadVariablesTool
import com.eleckoi.android.feature.chat.ui.blocks.image.UserInputImageGallery
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBubblePalette
import com.eleckoi.android.engine.agent.api.AgentUpdatePlanTool
import com.eleckoi.android.engine.agent.api.AgentUpdateRoleplayPlanTool
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.engine.agent.api.singleTypeOrNull
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.markdown.CreationMarkdownText
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.DshTimelineIcons

fun creationOperationIcon(items: List<CreationTimelineItem>): ImageVector {
    val workItems = operationPresentationItems(items)
    val types = workItems.mapNotNull(CreationTimelineItem::workItemType).toSet()
    val toolNames = workItems.map(CreationTimelineItem::toolName).toSet()
    return when {
        workItems.singleOrNull()?.isFinalProtocolDetection() == true ->
            Icons.Rounded.CheckCircleOutline
        AgentWorkItemType.Action in types -> Icons.Rounded.Bolt
        AgentUpdatePlanTool in toolNames ||
            AgentUpdateRoleplayPlanTool in toolNames -> Icons.AutoMirrored.Rounded.FormatListBulleted
        AgentSubagentTool in toolNames -> Icons.Rounded.Groups
        AgentWorkItemType.ContextCompaction in types -> Icons.Rounded.Description
        AgentWorkItemType.FileChange in types -> Icons.Rounded.Edit
        AgentApplyVariablePatchTool in toolNames ||
            AgentApplySettingPatchTool in toolNames ||
            toolNames.any(AgentSettingFileMutationTools::contains) -> Icons.Rounded.Edit
        AgentGlobSettingFilesTool in toolNames ||
            AgentGlobVariablesTool in toolNames ->
            DshTimelineIcons.SearchSetting
        AgentGrepSettingFilesTool in toolNames ||
            AgentGrepVariablesTool in toolNames ->
            DshTimelineIcons.SearchSetting
        AgentReadSettingFilesTool in toolNames ||
            AgentReadVariablesTool in toolNames -> Icons.Outlined.AutoStories
        workItems.isNotEmpty() && workItems.all(CreationTimelineItem::isReadOperation) ->
            Icons.Outlined.AutoStories
        workItems.any {
            it.commandActions.singleTypeOrNull() == AgentCommandActionType.Search
        } -> Icons.Rounded.Search
        workItems.any {
            it.commandActions.singleTypeOrNull() == AgentCommandActionType.ListFiles
        } -> Icons.Rounded.FolderOpen
        AgentWorkItemType.Command in types -> Icons.Rounded.Terminal
        else -> DshTimelineIcons.ToolWrench
    }
}

@Composable
fun UserTimelineItem(
    item: CreationTimelineItem,
    appearance: AppearanceTheme,
    onEdit: (() -> Unit)? = null,
    onEditBoundsChanged: ((Rect?) -> Unit)? = null,
) {
    val bubblePalette = resolveChatBubblePalette(
        appearance = appearance,
        layoutMode = ChatLayoutMode.Agent,
        user = true,
    )
    DisposableEffect(item.id, onEditBoundsChanged) {
        onDispose { onEditBoundsChanged?.invoke(null) }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = maxWidth * 0.82f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = bubbleMaxWidth)
                    .fillMaxWidth(0.82f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                UserInputImageGallery(
                    images = item.inputImages,
                    appearance = appearance,
                )
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomStart = 24.dp,
                                    bottomEnd = 5.dp,
                                ),
                            )
                            .background(bubblePalette.container)
                            .then(
                                if (onEdit != null) {
                                    Modifier
                                        .onGloballyPositioned { coordinates ->
                                            onEditBoundsChanged?.invoke(coordinates.boundsInWindow())
                                        }
                                        .semantics {
                                            onClick {
                                                onEdit()
                                                true
                                            }
                                        }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        color = bubblePalette.content,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun FinalAssistantAnswer(
    item: CreationTimelineItem,
    appearance: AppearanceTheme,
) {
    CreationMarkdownText(item = item, appearance = appearance)
}

@Composable
fun FileChangeSummaryCard(
    stats: List<CreationFileDiffStat>,
    canUndo: Boolean,
    isRestoring: Boolean,
    appearance: AppearanceTheme,
    onUndo: () -> Unit,
    onReview: () -> Unit,
) {
    val knownStats = stats.filter(CreationFileDiffStat::countsKnown)
    val additions = knownStats.sumOf(CreationFileDiffStat::additions)
    val deletions = knownStats.sumOf(CreationFileDiffStat::deletions)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = appearance.mobileSurface,
        border = BorderStroke(0.8.dp, appearance.mobileMuted.copy(alpha = 0.20f)),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(appearance.mobileMuted.copy(alpha = 0.07f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = appearance.mobileMuted,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "已编辑 ${stats.size} 个文件",
                        color = appearance.mobileText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (additions > 0 || deletions > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (additions > 0) {
                                Text("+$additions", color = DiffAdditionColor, fontSize = 12.sp)
                            }
                            if (deletions > 0) {
                                Text("-$deletions", color = DiffDeletionColor, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Text(
                    text = "查看",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onReview)
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                )
                if (canUndo || isRestoring) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = canUndo && !isRestoring, onClick = onUndo)
                            .padding(horizontal = 7.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 1.8.dp,
                                color = appearance.mobileMuted,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = appearance.mobileMuted,
                            )
                        }
                        Text(
                            text = if (isRestoring) "恢复中" else "撤销",
                            color = appearance.mobileMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = 0.7.dp,
                color = appearance.mobileMuted.copy(alpha = 0.16f),
            )
            stats.take(MaxVisibleFileStats).forEach { stat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CreationFileTypeBadge(stat.path)
                    Text(
                        text = stat.path,
                        modifier = Modifier.weight(1f),
                        color = appearance.mobileMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (stat.countsKnown && stat.additions > 0) {
                        Text("+${stat.additions}", color = DiffAdditionColor, fontSize = 12.sp)
                    }
                    if (stat.countsKnown && stat.deletions > 0) {
                        Text("-${stat.deletions}", color = DiffDeletionColor, fontSize = 12.sp)
                    }
                }
            }
            if (stats.size > MaxVisibleFileStats) {
                Text(
                    text = "还有 ${stats.size - MaxVisibleFileStats} 个文件",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    color = appearance.mobileMuted,
                    fontSize = 11.5.sp,
                )
            }
        }
    }
}

@Composable
fun CreationFileTypeBadge(path: String) {
    val label = creationFileTypeLabel(path)
    val color = when (label) {
        "HTML" -> Color(0xFFE4572E)
        "CSS" -> Color(0xFF7B61E8)
        "JS", "JSX" -> Color(0xFFD49A00)
        "TS", "TSX" -> Color(0xFF2F74C0)
        "KT", "KTS" -> Color(0xFF7C4DFF)
        "PY" -> Color(0xFF3776AB)
        "RS" -> Color(0xFFB45309)
        "GO" -> Color(0xFF00A6C7)
        "JSON", "MD", "TXT", "FILE" -> Color(0xFF64748B)
        else -> Color(0xFF5E6C84)
    }
    Text(
        text = label,
        color = color,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}
