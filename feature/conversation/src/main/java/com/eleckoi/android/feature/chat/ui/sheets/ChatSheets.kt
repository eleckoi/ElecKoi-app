package com.eleckoi.android.feature.chat.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.chat.model.ChatListItem
import com.eleckoi.android.feature.modelconfig.model.ModelParameters
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun EditMessageSheet(
    editorKey: String,
    value: String,
    isAssistant: Boolean,
    appearance: AppearanceTheme,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRegenerate: ((String) -> Unit)?,
) {
    val textState = remember(editorKey) {
        TextFieldState(
            initialText = value,
            initialSelection = TextRange(value.length),
        )
    }
    val textScrollState = rememberScrollState()
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val visibility = remember(editorKey) {
        MutableTransitionState(false).apply { targetState = true }
    }
    val scope = rememberCoroutineScope()
    var dismissing by remember(editorKey) { mutableStateOf(false) }
    val requestDismiss = {
        if (!dismissing) {
            dismissing = true
            visibility.targetState = false
            scope.launch {
                delay(180)
                onDismiss()
            }
        }
    }

    BackHandler(enabled = !dismissing, onBack = requestDismiss)

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
            .noRippleClickable(onClick = requestDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            enter = fadeIn(tween(140)) + slideInVertically(
                animationSpec = tween(220),
                initialOffsetY = { it },
            ),
            exit = fadeOut(tween(120)) + slideOutVertically(
                animationSpec = tween(180),
                targetOffsetY = { it },
            ),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val editorMaxHeight = (maxHeight - 96.dp).coerceAtLeast(96.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable {}
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(appearance.mobileChatBg)
                        .navigationBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .background(appearance.mobileChatHeaderBg)
                            .padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = appearance.mobileBlue,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (isAssistant) "修改输出" else "修改输入",
                            modifier = Modifier.padding(start = 7.dp),
                            color = appearance.mobileText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = appearance.mobileMuted.copy(alpha = 0.16f),
                    )
                    BasicTextField(
                        state = textState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = editorMaxHeight)
                            .focusDismissInputRegion()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        textStyle = TextStyle(
                            color = appearance.mobileText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        ),
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        cursorBrush = SolidColor(appearance.mobileBlue),
                        scrollState = textScrollState,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        onRegenerate?.let { regenerate ->
                            EditMessageAction(
                                contentDescription = "保存并重新生成",
                                enabled = textState.text.isNotBlank(),
                                onClick = { regenerate(textState.text.toString()) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = if (textState.text.isNotBlank()) {
                                        appearance.mobileText
                                    } else {
                                        appearance.mobileMuted.copy(alpha = 0.42f)
                                    },
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        EditMessageAction(
                            contentDescription = "保存修改",
                            enabled = textState.text.isNotBlank(),
                            onClick = { onSave(textState.text.toString()) },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (textState.text.isNotBlank()) {
                                    appearance.mobileText
                                } else {
                                    appearance.mobileMuted.copy(alpha = 0.42f)
                                },
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditMessageAction(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .noRippleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
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
