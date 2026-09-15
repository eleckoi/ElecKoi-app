package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.selectionPalette

val MobileConversationAvatarRowHeight: Dp = 72.dp
val MobileConversationCoverRowHeight: Dp = 82.dp

fun mobileConversationRowHeight(useCoverArtwork: Boolean): Dp =
    if (useCoverArtwork) MobileConversationCoverRowHeight else MobileConversationAvatarRowHeight

@Composable
fun MobileConversationRow(
    title: String,
    subtitle: String,
    avatarName: String,
    avatarPath: String = "",
    coverPath: String = "",
    useCoverArtwork: Boolean = false,
    sideText: String,
    appearance: AppearanceTheme,
    selected: Boolean = false,
    pinned: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(mobileConversationRowHeight(useCoverArtwork))
            .themedListRowClickable(
                appearance = appearance,
                selected = selected,
                selectedBackground = appearance.mobileMuted.copy(alpha = 0.10f),
                onClick = onClick,
            )
            .mobileListHairline(
                appearance = appearance,
                startInset = 16.dp + (if (useCoverArtwork) 54.dp else 56.dp) + 9.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListCharacterArtwork(
                name = avatarName,
                avatarPath = avatarPath,
                coverPath = coverPath,
                useCover = useCoverArtwork,
                appearance = appearance,
                avatarSize = 56,
                coverWidth = 54,
                coverHeight = 72,
                coverCornerRadius = 9,
                fontSize = if (useCoverArtwork) 16 else 18,
            )
            MobileArtworkRowText(
                title = title,
                subtitle = subtitle,
                useCoverArtwork = useCoverArtwork,
                appearance = appearance,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 9.dp),
            )
            if (sideText.isNotBlank()) {
                if (pinned) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "已置顶",
                        tint = appearance.mobileSoft,
                        modifier = Modifier
                            .padding(end = 7.dp)
                            .size(16.dp),
                    )
                }
                Text(
                    text = sideText,
                    color = appearance.mobileSoft,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

fun Modifier.mobileListHairline(
    appearance: AppearanceTheme,
    startInset: Dp,
    endInset: Dp = 16.dp,
): Modifier = drawBehind {
    val startX = startInset.toPx()
    val endX = size.width - endInset.toPx()
    if (endX > startX && size.height >= 1f) {
        val y = size.height - 0.5f
        drawLine(
            color = appearance.mobileLine,
            start = Offset(startX, y),
            end = Offset(endX, y),
            strokeWidth = Stroke.HairlineWidth,
        )
    }
}

@Composable
fun MobileArtworkRowText(
    title: String,
    subtitle: String,
    useCoverArtwork: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.height(if (useCoverArtwork) 72.dp else 56.dp),
        verticalArrangement = if (useCoverArtwork) Arrangement.Top else Arrangement.Center,
    ) {
        Text(
            text = title,
            color = appearance.mobileText,
            fontSize = if (useCoverArtwork) 17.sp else 18.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = if (useCoverArtwork) 21.sp else 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = appearance.mobileMuted.copy(alpha = 0.72f),
            fontSize = if (useCoverArtwork) 13.sp else 14.sp,
            lineHeight = if (useCoverArtwork) 16.sp else 17.sp,
            maxLines = if (useCoverArtwork) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionGap(appearance: AppearanceTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .background(appearance.mobileSearchBg),
    )
}

@Composable
fun GroupRow(
    title: String,
    count: Int,
    appearance: AppearanceTheme,
    placeholder: String = count.toString(),
    collapsed: Boolean = false,
    onClick: () -> Unit = {},
) {
    val selection = appearance.selectionPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .noRippleClickable(onClick = onClick)
            .padding(start = 17.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeSvgIcon(
            paths = AppIconPaths.ChevronRight,
            color = appearance.mobileSoft,
            iconSize = 17.dp,
            strokeWidth = 1.9f,
            modifier = Modifier.graphicsLayer(rotationZ = if (collapsed) 0f else 90f),
        )
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 7.dp),
            color = selection.mutedText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
        )
        Text(placeholder, color = selection.mutedText, fontSize = 12.5.sp)
    }
}

@Composable
fun MobileEmptyState(text: String, appearance: AppearanceTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = appearance.mobileMuted, fontSize = 15.sp)
    }
}

@Composable
fun MobileRootEmptyState(
    title: String,
    message: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ThinkingMascotSpriteIcon(
            frame = ThinkingMascotSpriteFrame.Open,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    alpha = if (appearance.isDark) 0.36f else 0.28f
                },
        )
        Text(
            text = title,
            color = appearance.mobileMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = message,
            color = appearance.mobileSoft,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
