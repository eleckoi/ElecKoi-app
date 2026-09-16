package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.SquareSelectionCheck
import com.eleckoi.android.foundation.design.components.noRippleClickable

internal fun isDeleteSuffixSelected(messageIndex: Int, deleteFromIndex: Int): Boolean =
    deleteFromIndex >= 0 && messageIndex >= deleteFromIndex

@Composable
internal fun ChatDeleteSelectionRow(
    active: Boolean,
    selected: Boolean,
    enabled: Boolean,
    isFirstInMessage: Boolean,
    isLastInMessage: Boolean,
    appearance: AppearanceTheme,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!active) {
        Box(modifier = modifier) { content() }
        return
    }
    val shape = RoundedCornerShape(
        topStart = if (isFirstInMessage) 10.dp else 0.dp,
        topEnd = if (isFirstInMessage) 10.dp else 0.dp,
        bottomStart = if (isLastInMessage) 10.dp else 0.dp,
        bottomEnd = if (isLastInMessage) 10.dp else 0.dp,
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) appearance.mobileBlue.copy(alpha = 0.07f) else Color.Transparent,
            ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(48.dp))
            Box(modifier = Modifier.weight(1f)) { content() }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    role = Role.Checkbox
                    this.selected = selected
                    contentDescription = if (selected) {
                        "从这条消息开始删除，已选中"
                    } else {
                        "从这条消息开始删除"
                    }
                }
                .noRippleClickable(enabled = enabled, onClick = onSelect),
        ) {
            if (isFirstInMessage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SquareSelectionCheck(
                        selected = selected,
                        appearance = appearance,
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
