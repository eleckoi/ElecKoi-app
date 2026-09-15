package com.eleckoi.android.feature.chat.ui.variables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.DshTreeDisclosureGlyph
import com.eleckoi.android.foundation.design.components.QuietBackButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VariableViewerHeader(
    title: String,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appearance.mobileBg),
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietBackButton(
                color = appearance.mobileText,
                onClick = onBack,
                modifier = Modifier.size(48.dp),
                iconSize = 21.dp,
            )
            Text(
                text = title,
                color = appearance.mobileText,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = VariableViewerHorizontalPadding)
                    .semantics { heading() },
            )
        }
    }
}

@Composable
internal fun VariableViewerChevron(
    expanded: Boolean,
    color: Color,
    iconSize: Dp,
) {
    DshTreeDisclosureGlyph(
        expanded = expanded,
        tint = color,
        iconSize = iconSize,
    )
}

@Composable
internal fun VariableEmptyMessage(
    text: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = appearance.mobileMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = modifier,
    )
}

internal val VariableViewerHorizontalPadding = 20.dp
