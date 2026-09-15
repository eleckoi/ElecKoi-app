package com.eleckoi.android.feature.chat.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.feature.modelconfig.ui.configVersionName
import com.eleckoi.android.feature.modelconfig.ui.modelOptionsKey
import com.eleckoi.android.feature.modelconfig.ui.modelProviders
import com.eleckoi.android.feature.modelconfig.ui.normalizeProviderId
import com.eleckoi.android.feature.modelconfig.ui.providerMeta
import com.eleckoi.android.foundation.design.components.AvatarCircle
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.SearchIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.focusDismissInputRegion
import com.eleckoi.android.foundation.design.fieldPalette
import com.eleckoi.android.foundation.design.overlayScrim
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun EditMessageSheet(
    editorKey: String,
    value: String,
    appearance: AppearanceTheme,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val textState = remember(editorKey) {
        TextFieldState(
            initialText = value,
            initialSelection = TextRange(value.length),
        )
    }
    val textScrollState = rememberScrollState()
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != latestValue) latestOnValueChange(text)
            }
    }
    LaunchedEffect(value, textState) {
        if (textState.text.toString() != value) {
            textState.setTextAndPlaceCursorAtEnd(value)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.overlayScrim())
            .noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable {}
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(appearance.mobileSurface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 17.dp, top = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "修改输入",
                        modifier = Modifier.weight(1f),
                        color = appearance.mobileText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .noRippleClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        StrokeSvgIcon(AppIconPaths.X, appearance.mobileText, iconSize = 24.dp)
                    }
                }
                BasicTextField(
                    state = textState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp, max = 240.dp)
                        .focusDismissInputRegion()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = TextStyle(
                        color = appearance.mobileText,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    ),
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    cursorBrush = SolidColor(appearance.mobileBlue),
                    scrollState = textScrollState,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onSubmit(textState.text.toString()) },
                        modifier = Modifier.weight(1f),
                        enabled = textState.text.isNotBlank(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = appearance.mobileBlue,
                            disabledContentColor = appearance.mobileMuted.copy(alpha = 0.38f),
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "重新生成")
                        Text("重新生成", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SelectMessageTextSheet(
    text: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    val field = appearance.fieldPalette()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.overlayScrim())
            .noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .noRippleClickable {}
                .navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(appearance.mobileSurface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "选择文本",
                    color = appearance.mobileText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(field.container)
                        .noRippleClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeSvgIcon(AppIconPaths.X, appearance.mobileText, iconSize = 22.dp)
                }
            }
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 22.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = text,
                    color = appearance.mobileText,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}
