package com.eleckoi.android.feature.chat.ui.message

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eleckoi.android.engine.agent.api.AgentUpdatePlanTool
import com.eleckoi.android.engine.agent.api.AgentUpdateRoleplayPlanTool
import com.eleckoi.android.engine.agent.api.AgentWorkItemType
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.ui.LocalStaticListExpansionObserver
import com.eleckoi.android.feature.conversation.timeline.AgentPlanStepStatus
import com.eleckoi.android.feature.conversation.timeline.AgentPlanUpdatePresentation
import com.eleckoi.android.feature.conversation.timeline.CreationDetailPayload
import com.eleckoi.android.feature.conversation.timeline.detail.CreationOperationDetailContent
import com.eleckoi.android.feature.conversation.timeline.detail.PlanStepStatusIndicator
import com.eleckoi.android.feature.conversation.timeline.activePlanUpdateId
import com.eleckoi.android.feature.conversation.timeline.formatCreationElapsedTime
import com.eleckoi.android.feature.conversation.timeline.findTimelineItem
import com.eleckoi.android.feature.conversation.timeline.initialSelectedItemPath
import com.eleckoi.android.feature.conversation.timeline.parseAgentPlanUpdatePresentation
import com.eleckoi.android.feature.conversation.timeline.preferredCreationDiff
import com.eleckoi.android.foundation.design.AppearanceTheme

/**
 * A turn's complete process, inspected live from its status row or read back after the fact.
 *
 * The conversation shows this timeline while the turn runs and then lets it go entirely; the record
 * lives here instead of folding into a header above the character's first line. Opening it is a
 * deliberate act, so the timeline arrives already unfolded, and its operation rows still drill into
 * their own detail from inside.
 *
 * A dialog rather than an inline expansion: growing the record in place would push the scene around
 * it, and a turn's steps are not part of the scene.
 */
@Composable
internal fun ChatAgentProcessSheet(
    message: ChatMessage,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    val durationMillis = message.turnStartedAtMillis.let { started ->
        val completed = message.turnCompletedAtMillis
        if (started > 0L && completed != null) (completed - started).coerceAtLeast(0L) else 0L
    }
    val latestPlanCall = remember(message.toolCalls) {
        message.toolCalls
            .asReversed()
            .firstOrNull { call ->
                call.toolName == AgentUpdatePlanTool ||
                    call.toolName == AgentUpdateRoleplayPlanTool
            }
    }
    val acceptedFinalBody = remember(
        message.content,
        message.pending,
        message.toolCalls,
    ) {
        message.hasAcceptedRoleplayFinalBody()
    }
    val latestPlan = remember(latestPlanCall, acceptedFinalBody) {
        latestPlanCall?.let { call ->
            parseAgentPlanUpdatePresentation(
                toolName = call.toolName,
                toolArguments = call.arguments,
                toolResult = call.result,
            )?.withAutoCompletedRoleplayFinal(
                enabled = acceptedFinalBody && call.toolName == AgentUpdateRoleplayPlanTool,
            )
        }
    }
    val timelineItems = remember(
        message.id,
        message.reasoningContent,
        message.toolCalls,
        message.pending,
    ) {
        chatAgentTimelineItems(
            messageId = message.id,
            reasoningContent = message.reasoningContent,
            calls = message.toolCalls,
            running = message.pending,
        )
    }
    val activePlanId = remember(timelineItems, message.pending) {
        activePlanUpdateId(timelineItems, turnRunning = message.pending)
    }
    val processScrollState = rememberScrollState()
    var detailPayload by remember(message.id) { mutableStateOf<CreationDetailPayload?>(null) }
    var selectedDetailItemPath by remember(message.id) { mutableStateOf<List<String>>(emptyList()) }
    val selectedDetailItem = detailPayload
        ?.items
        ?.findTimelineItem(selectedDetailItemPath)
        ?.let { item ->
            if (item.workItemType == AgentWorkItemType.FileChange) {
                item.copy(diff = preferredCreationDiff(item.diff, detailPayload?.diff.orEmpty()))
            } else {
                item
            }
        }
    LaunchedEffect(timelineItems, detailPayload?.liveTurnId, activePlanId, message.pending) {
        val current = detailPayload ?: return@LaunchedEffect
        detailPayload = current.refreshFromLiveChatProcess(
            messageId = message.id,
            timelineItems = timelineItems,
            turnRunning = message.pending,
            activePlanUpdateId = activePlanId,
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(enabled = detailPayload != null) {
            val nextPath = chatProcessDetailPathAfterBack(
                payload = detailPayload ?: return@BackHandler,
                selectedPath = selectedDetailItemPath,
            )
            if (nextPath == null) {
                selectedDetailItemPath = emptyList()
                detailPayload = null
            } else {
                selectedDetailItemPath = nextPath
            }
        }
        // This timeline is rendered in a separate dialog window, not inside the conversation's
        // LazyColumn. Its expanded/collapsing lifecycle must never claim the chat viewport or mute
        // live tail deltas while the user inspects the current turn.
        CompositionLocalProvider(
            LocalStaticListExpansionObserver provides IgnoreStaticListExpansion,
        ) {
            SelectionContainer {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.84f)
                            .navigationBarsPadding(),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = appearance.mobileSurface,
                        shadowElevation = 14.dp,
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .padding(top = 9.dp)
                                    .width(42.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(appearance.mobileMuted.copy(alpha = 0.32f))
                                    .align(Alignment.CenterHorizontally),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (detailPayload != null) {
                                        "详情"
                                    } else {
                                        buildString {
                                            append("处理过程")
                                            if (durationMillis > 0L) {
                                                append(" · ${formatCreationElapsedTime(durationMillis)}")
                                            }
                                        }
                                    },
                                    color = appearance.mobileText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (detailPayload != null) {
                                    IconButton(
                                        onClick = {
                                            val payload = detailPayload ?: return@IconButton
                                            val nextPath = chatProcessDetailPathAfterBack(
                                                payload = payload,
                                                selectedPath = selectedDetailItemPath,
                                            )
                                            if (nextPath == null) {
                                                selectedDetailItemPath = emptyList()
                                                detailPayload = null
                                            } else {
                                                selectedDetailItemPath = nextPath
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.CenterStart),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = if (selectedDetailItemPath.isNotEmpty()) {
                                                "返回详情列表"
                                            } else {
                                                "返回处理过程"
                                            },
                                            tint = appearance.mobileText,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = if (detailPayload != null) {
                                            "关闭详情"
                                        } else {
                                            "关闭处理过程"
                                        },
                                        tint = appearance.mobileText,
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.7.dp,
                                color = appearance.mobileMuted.copy(alpha = 0.16f),
                            )
                            val currentDetail = detailPayload
                            if (currentDetail != null) {
                                CreationOperationDetailContent(
                                    payload = currentDetail,
                                    selectedItem = selectedDetailItem,
                                    appearance = appearance,
                                    onOpenItem = { item ->
                                        selectedDetailItemPath = selectedDetailItemPath + item.id
                                    },
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(processScrollState),
                                ) {
                                    if (latestPlan != null) {
                                        CurrentTaskPlanSummary(
                                            plan = latestPlan,
                                            animateInProgress = activePlanId != null,
                                            appearance = appearance,
                                        )
                                        HorizontalDivider(
                                            thickness = 0.7.dp,
                                            color = appearance.mobileMuted.copy(alpha = 0.16f),
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                    ) {
                                        ChatAgentProcessedTimeline(
                                            messageId = message.id,
                                            reasoningContent = message.reasoningContent,
                                            calls = message.toolCalls,
                                            running = message.pending,
                                            alwaysExpanded = true,
                                            turnStartedAtMillis = message.turnStartedAtMillis,
                                            turnCompletedAtMillis = message.turnCompletedAtMillis,
                                            appearance = appearance,
                                            fontSize = 14.sp,
                                            lineHeight = 21.sp,
                                            letterSpacing = 0.sp,
                                            paragraphSpacing = 8f,
                                            onOpenDetail = { payload ->
                                                selectedDetailItemPath = payload.initialSelectedItemPath()
                                                detailPayload = payload.bindToLiveChatProcess(
                                                    messageId = message.id,
                                                    turnRunning = message.pending,
                                                    activePlanUpdateId = activePlanId,
                                                )
                                            },
                                        )
                                        RoleplayProtocolResultSection(
                                            traces = message.roleplayProtocolTrace(),
                                            appearance = appearance,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTaskPlanSummary(
    plan: AgentPlanUpdatePresentation,
    animateInProgress: Boolean,
    appearance: AppearanceTheme,
) {
    val completed = plan.steps.count { step ->
        step.status == AgentPlanStepStatus.Completed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "当前任务计划",
                modifier = Modifier.weight(1f),
                color = appearance.mobileText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (plan.steps.isEmpty()) {
                    "已清空"
                } else {
                    "$completed/${plan.steps.size} 已完成"
                },
                color = appearance.mobileMuted,
                fontSize = 12.sp,
            )
        }
        plan.steps.forEach { step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlanStepStatusIndicator(
                    status = step.status,
                    animateInProgress = animateInProgress,
                    appearance = appearance,
                    settledInProgressDescription = "回合结束时进行中",
                )
                Text(
                    text = step.text,
                    modifier = Modifier.weight(1f),
                    color = if (step.status == AgentPlanStepStatus.Pending) {
                        appearance.mobileMuted
                    } else {
                        appearance.mobileText
                    },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun RoleplayProtocolResultSection(
    traces: List<RoleplayProtocolTrace>,
    appearance: AppearanceTheme,
) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(
        thickness = 0.7.dp,
        color = appearance.mobileMuted.copy(alpha = 0.14f),
    )
    Spacer(Modifier.height(11.dp))
    Text(
        text = "输出协议",
        color = appearance.mobileMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(5.dp))
    traces.forEach { trace ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = trace.tag,
                modifier = Modifier.weight(1f),
                color = appearance.mobileMuted.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Text(
                text = trace.state.label,
                color = appearance.mobileMuted.copy(alpha = 0.82f),
                fontSize = 11.5.sp,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

private val IgnoreStaticListExpansion: (Any, Boolean) -> Unit = { _, _ -> }

/**
 * Returns the parent detail path, or null when Back should leave detail entirely.
 *
 * A lone compaction deliberately opens at its summary. That selected item is the detail root, not
 * a child page, so Back must return to the processing timeline without exposing a synthetic
 * one-item list in between.
 */
internal fun chatProcessDetailPathAfterBack(
    payload: CreationDetailPayload,
    selectedPath: List<String>,
): List<String>? {
    val rootDepth = payload.initialSelectedItemPath().size
    return if (selectedPath.size > rootDepth) selectedPath.dropLast(1) else null
}
