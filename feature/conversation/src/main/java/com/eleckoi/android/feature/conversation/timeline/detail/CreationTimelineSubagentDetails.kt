package com.eleckoi.android.feature.conversation.timeline.detail

import com.eleckoi.android.feature.conversation.timeline.*
import com.eleckoi.android.feature.conversation.timeline.components.*
import com.eleckoi.android.feature.conversation.timeline.overview.CreationOperationOverview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
fun SubagentInvocationDetail(
    item: CreationTimelineItem,
    delegation: SubagentToolPresentation,
    appearance: AppearanceTheme,
    onOpenItem: (CreationTimelineItem) -> Unit,
) {
    val returnResult = item.subagentReturnResult()
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        if (delegation.prompt.isNotBlank()) {
            DetailTextBlock(
                label = "委派指令",
                text = delegation.prompt,
                appearance = appearance,
                monospace = false,
            )
        }
        DetailTextBlock(
            label = "使用模型",
            text = item.delegatedModel.ifBlank { "跟随主模型" },
            appearance = appearance,
            monospace = false,
        )
        DetailTextBlock(
            label = "执行方式",
            text = if (delegation.background) "后台运行" else "等待子 Agent 返回",
            appearance = appearance,
            monospace = false,
        )
        if (item.childTimeline.isNotEmpty()) {
            Text(
                text = "子 Agent 思考与完整回复",
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            CreationOperationOverview(
                items = item.childTimeline,
                turnDiff = item.childTimeline.asReversed()
                    .firstOrNull { child -> child.diff.isNotBlank() }
                    ?.diff
                    .orEmpty(),
                isLive = item.running,
                includeAssistantMessages = true,
                appearance = appearance,
                onOpenItem = onOpenItem,
            )
        } else if (item.running) {
            Text(
                text = "正在等待子 Agent 返回执行事件",
                color = appearance.mobileMuted,
                fontSize = 13.sp,
            )
        }
        if (returnResult.isNotBlank()) {
            DetailTextBlock(
                label = if (item.failed) "失败原因" else "返回结果",
                text = returnResult,
                appearance = appearance,
                monospace = item.failed,
            )
        }
    }
}

@Composable
fun PlanUpdateDetail(
    plan: AgentPlanUpdatePresentation,
    animateInProgress: Boolean,
    appearance: AppearanceTheme,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        if (plan.explanation.isNotBlank()) {
            DetailTextBlock(
                label = "计划说明",
                text = plan.explanation,
                appearance = appearance,
                monospace = false,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = "任务清单",
                color = appearance.mobileMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (plan.steps.isEmpty()) {
                Text(
                    text = "计划已清空",
                    color = appearance.mobileMuted,
                    fontSize = 13.sp,
                )
            } else {
                plan.steps.forEach { step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        PlanStepStatusIndicator(
                            status = step.status,
                            animateInProgress = animateInProgress,
                            appearance = appearance,
                        )
                        Text(
                            text = step.text,
                            modifier = Modifier.weight(1f),
                            color = when (step.status) {
                                AgentPlanStepStatus.Pending -> appearance.mobileMuted
                                else -> appearance.mobileText
                            },
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlanStepStatusIndicator(
    status: AgentPlanStepStatus,
    animateInProgress: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    settledInProgressDescription: String = "当时进行中",
) {
    when (status) {
        AgentPlanStepStatus.InProgress -> if (animateInProgress) {
            CircularProgressIndicator(
                modifier = modifier.size(16.dp),
                color = appearance.mobileBlue,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.RadioButtonChecked,
                contentDescription = settledInProgressDescription,
                modifier = modifier.size(17.dp),
                tint = appearance.mobileBlue,
            )
        }
        AgentPlanStepStatus.Completed -> Icon(
            imageVector = Icons.Rounded.CheckCircleOutline,
            contentDescription = "已完成",
            modifier = modifier.size(17.dp),
            tint = appearance.mobileBlue,
        )
        AgentPlanStepStatus.Pending -> Icon(
            imageVector = Icons.Rounded.RadioButtonUnchecked,
            contentDescription = "待处理",
            modifier = modifier.size(17.dp),
            tint = appearance.mobileMuted.copy(alpha = 0.7f),
        )
    }
}
