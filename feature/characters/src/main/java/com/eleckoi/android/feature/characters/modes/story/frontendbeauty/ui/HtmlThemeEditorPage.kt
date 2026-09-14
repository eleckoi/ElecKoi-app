package com.eleckoi.android.feature.characters.modes.story.frontendbeauty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.focusDismissInputRegion
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun HtmlThemeEditorPage(
    editor: HtmlThemeEditorState,
    loading: Boolean,
    saving: Boolean,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = {
        if (editor.hasUnsavedChanges) confirmDiscard = true else onBack()
    }
    BackHandler(enabled = !saving, onBack = requestBack)

    PinnedStatusScaffold(
        appearance = appearance,
        imeAware = true,
        backgroundColor = appearance.mobileBg,
    ) {
        HtmlThemeEditorHeader(
            title = editor.name.trim().ifBlank { "HTML 主题" },
            loading = loading,
            saving = saving,
            canSubmit = editor.html.isNotBlank(),
            appearance = appearance,
            onBack = requestBack,
            onPreview = onPreview,
            onSave = onSave,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppInsetTextField(
                value = editor.name,
                onValueChange = onNameChange,
                appearance = appearance,
                placeholder = "主题名称",
                enabled = !loading && !saving,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = appearance.mobileText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(appearance.mobileSurface)
                    .border(1.dp, appearance.mobileLine, RoundedCornerShape(8.dp))
                    .focusDismissInputRegion(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(appearance.mobileSearchBg)
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "index.html",
                        modifier = Modifier.weight(1f),
                        color = appearance.mobileText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${editor.html.length} 字符",
                        color = appearance.mobileMuted,
                        fontSize = 10.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(26.dp),
                            color = appearance.mobileBlue,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        if (editor.html.isEmpty()) {
                            Text(
                                text = "<!doctype html>",
                                color = appearance.mobileMuted,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        BasicTextField(
                            value = editor.html,
                            onValueChange = onSourceChange,
                            enabled = !saving,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(
                                color = appearance.mobileText,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Default,
                            ),
                            cursorBrush = SolidColor(appearance.mobileBlue),
                        )
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "放弃修改？",
            message = "尚未保存的 HTML 代码将丢失。",
            appearance = appearance,
            confirmText = "放弃",
            destructive = true,
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
        )
    }
}

@Composable
private fun HtmlThemeEditorHeader(
    title: String,
    loading: Boolean,
    saving: Boolean,
    canSubmit: Boolean,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(appearance.mobileBg)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .noRippleClickable(enabled = !saving, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(AppIconPaths.Back, appearance.mobileText, iconSize = 25.dp)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 3.dp)) {
            Text(
                text = "HTML 主题",
                color = appearance.mobileText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                color = appearance.mobileMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onPreview,
            enabled = !loading && !saving && canSubmit,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "预览",
                tint = if (canSubmit) appearance.mobileText else appearance.mobileSoft,
                modifier = Modifier.size(23.dp),
            )
        }
        Surface(
            onClick = onSave,
            enabled = !loading && !saving && canSubmit,
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(8.dp),
            color = appearance.mobileBlue,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = appearance.mobileAccentFg,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "保存并使用",
                        tint = appearance.mobileAccentFg,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}
