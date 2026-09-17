package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme

/** Shared header geometry for every full-width mobile bottom sheet. */
@Composable
fun MobileBottomSheetHeader(
    title: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitleContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .semantics { contentDescription = "返回" }
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(
                    AppIconPaths.ChevronLeft,
                    appearance.mobileText,
                    iconSize = 21.dp,
                    strokeWidth = 1.9f,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) 4.dp else 10.dp),
        ) {
            Text(
                title,
                color = appearance.mobileText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitleContent?.invoke(this)
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .semantics { contentDescription = "关闭" }
                .noRippleClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(
                AppIconPaths.X,
                appearance.mobileText,
                iconSize = 19.dp,
                strokeWidth = 1.9f,
            )
        }
    }
}
