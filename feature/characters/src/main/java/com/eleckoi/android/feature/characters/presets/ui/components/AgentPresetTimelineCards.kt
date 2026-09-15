package com.eleckoi.android.feature.characters.presets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.presets.model.AgentPresetTimelineItem
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.dropShadow
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun EmptyTimelineCard(appearance: AppearanceTheme, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(appearance.mobileSurface)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(appearance.mobileBlue.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(AppIconPaths.Plus, appearance.mobileBlue, iconSize = 16.dp)
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text("添加第一条更新记录", color = appearance.mobileText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("标题和日期都由作者自己填写", modifier = Modifier.padding(top = 3.dp), color = appearance.mobileMuted, fontSize = 11.5.sp)
        }
    }
}

@Composable
internal fun TimelineEntryCard(
    item: AgentPresetTimelineItem,
    newest: Boolean,
    last: Boolean,
    appearance: AppearanceTheme,
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val markerY = 14.dp.toPx()
                    if (!newest) drawLine(appearance.mobileLine, Offset(x, 0f), Offset(x, markerY), 1.dp.toPx())
                    if (!last) drawLine(appearance.mobileLine, Offset(x, markerY), Offset(x, size.height), 1.dp.toPx())
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(if (newest) 13.dp else 11.dp)
                    .background(
                        if (newest) appearance.mobileBlue else appearance.mobileBg,
                        CircleShape,
                    )
                    .border(
                        1.5.dp,
                        if (newest) appearance.mobileBlue.copy(alpha = 0.24f) else appearance.mobileSoft,
                        CircleShape,
                    ),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 5.dp, bottom = if (last) 0.dp else 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (newest) appearance.mobileBlue.copy(alpha = if (appearance.isDark) 0.12f else 0.06f)
                    else appearance.mobileSurface,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    color = appearance.mobileText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (newest) FontWeight.SemiBold else FontWeight.Medium,
                )
                if (item.dateLabel.isNotBlank()) {
                    Text(
                        item.dateLabel,
                        modifier = Modifier.padding(start = 12.dp),
                        color = appearance.mobileMuted,
                        fontSize = 11.5.sp,
                    )
                }
            }
            if (item.note.isNotBlank()) {
                Text(
                    item.note,
                    modifier = Modifier.padding(top = 3.dp),
                    color = appearance.mobileMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
internal fun StackedTimelineToggle(
    hiddenCount: Int,
    expanded: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(68.dp).noRippleClickable(onClick = onClick)) {
        if (!expanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appearance.mobileSurface.copy(alpha = 0.42f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 5.dp, start = 18.dp, end = 18.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appearance.mobileSurface.copy(alpha = 0.72f)),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(52.dp)
                .dropShadow(RoundedCornerShape(12.dp), appearance.mobileText.copy(alpha = 0.035f), blur = 8.dp, offsetY = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(appearance.mobileSurface),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "收起更早记录" else "展开更早 $hiddenCount 条记录",
                color = appearance.mobileBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            StrokeSvgIcon(
                if (expanded) AppIconPaths.ChevronDown else AppIconPaths.ChevronRight,
                appearance.mobileBlue,
                iconSize = 15.dp,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
    }
}
