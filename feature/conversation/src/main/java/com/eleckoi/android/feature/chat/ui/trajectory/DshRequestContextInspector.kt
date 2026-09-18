package com.eleckoi.android.feature.chat.ui.trajectory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshRequestContextItem
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshRequestContextRole
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRequest
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
internal fun RequestContext(
    request: DshTrajectoryRequest?,
    expandedItems: Set<String>,
    onToggle: (String) -> Unit,
    appearance: AppearanceTheme,
    compact: Boolean,
) {
    if (request == null) {
        Text("当前事件没有 Request 上下文", color = appearance.mobileMuted, fontSize = 13.sp)
        return
    }
    if (request.context.isEmpty()) {
        Text("这次 Request 暂无可读取的上下文快照", color = appearance.mobileMuted, fontSize = 13.sp)
        return
    }
    request.context.sortedBy(DshRequestContextItem::order).forEachIndexed { index, item ->
        val key = "${item.order}:${item.messageId}:${item.kind}"
        RequestContextItem(
            item = item,
            expanded = key in expandedItems,
            onToggle = { onToggle(key) },
            appearance = appearance,
            compact = compact,
        )
        if (index != request.context.lastIndex) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = appearance.mobileLine.copy(alpha = 0.18f),
            )
        }
    }
}

@Composable
private fun RequestContextItem(
    item: DshRequestContextItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    appearance: AppearanceTheme,
    compact: Boolean,
) {
    val presentation = remember(item.content, item.kind) { requestContextPresentation(item) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = item.order.toString().padStart(2, '0'),
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                modifier = Modifier.width(24.dp),
            )
            ContextRolePill(item.role)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title.ifBlank { item.role.label() },
                    color = appearance.mobileText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val position = contextPosition(item)
                if (position.isNotBlank()) {
                    Text(
                        text = position,
                        color = appearance.mobileMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
        val shownContent = if (presentation.collapsible && !expanded) presentation.preview else item.content
        Text(
            text = shownContent,
            color = appearance.mobileText,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(appearance.mobileInputBg, RoundedCornerShape(10.dp))
                .then(
                    if (presentation.collapsible && expanded) {
                        Modifier
                            .heightIn(max = if (compact) 320.dp else 380.dp)
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        if (presentation.collapsible) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${presentation.characterCount} 字符 · ${presentation.lineCount} 行",
                    color = appearance.mobileMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(
                        text = if (expanded) "收起" else "展开全文",
                        color = appearance.mobileBlue,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextRolePill(role: DshRequestContextRole) {
    val color = when (role) {
        DshRequestContextRole.System -> Color(0xFF7C5CE7)
        DshRequestContextRole.User -> Color(0xFF1688D4)
        DshRequestContextRole.Assistant -> Color(0xFF18A673)
    }
    Surface(shape = RoundedCornerShape(7.dp), color = color.copy(alpha = 0.13f)) {
        Text(
            text = role.label(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

internal data class RequestContextPresentation(
    val collapsible: Boolean,
    val preview: String,
    val characterCount: Int,
    val lineCount: Int,
)

internal fun requestContextPresentation(item: DshRequestContextItem): RequestContextPresentation {
    val normalized = item.content.replace("\r\n", "\n")
    val lines = normalized.lines()
    val toolResultIsLong = item.kind.isToolResult() &&
        (normalized.length > ToolResultCollapseCharacters || lines.size > ToolResultCollapseLines)
    val extremeContent = normalized.length > FallbackCollapseCharacters || lines.size > FallbackCollapseLines
    val collapsible = toolResultIsLong || extremeContent
    val preview = if (collapsible) {
        val byLines = lines.take(ToolResultCollapseLines).joinToString("\n")
        val shortened = byLines.take(ToolResultCollapseCharacters).trimEnd()
        if (shortened.length < normalized.trimEnd().length) "$shortened…" else shortened
    } else {
        normalized
    }
    return RequestContextPresentation(
        collapsible = collapsible,
        preview = preview,
        characterCount = normalized.length,
        lineCount = lines.size,
    )
}

private fun contextPosition(item: DshRequestContextItem): String = buildList {
    item.source.trim().takeIf(String::isNotBlank)?.let(::add)
    val anchor = when (item.anchor) {
        "beforeToolContext" -> "设定插入点 1"
        "toolContext" -> "缓存设定区"
        "afterToolContext", "beforeHistory" -> "设定插入点 2"
        "afterHistory", "beforeLatestUserInput" -> "设定插入点 3"
        "afterLatestUserInput", "beforeToolFlow" -> "设定插入点 4"
        "afterToolFlow" -> "设定插入点 5"
        else -> ""
    }
    anchor.takeIf { it.isNotBlank() && it !in this }?.let(::add)
}.joinToString(" · ")

private const val ToolResultCollapseCharacters = 320
private const val ToolResultCollapseLines = 8
private const val FallbackCollapseCharacters = 1_600
private const val FallbackCollapseLines = 32
