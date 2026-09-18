package com.eleckoi.android.feature.modelconfig.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.focusDismissInputRegion
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.feature.modelconfig.ui.configVersionName

@Composable
fun ModelSettingsHeader(
    title: String,
    appearance: AppearanceTheme,
    onBack: (() -> Unit)?,
    actionText: String? = null,
    actionDanger: Boolean = false,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(60.dp).background(appearance.mobileBg).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(appearance.mobileSurface)
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.Back, appearance.mobileText, iconSize = 23.dp, strokeWidth = 1.9f)
            }
        }
        Text(title, color = appearance.mobileText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (actionText != null && onAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(38.dp)
                    .widthIn(min = 72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (actionDanger) {
                            ElecKoiDanger.copy(alpha = if (actionEnabled) 0.12f else 0.06f)
                        } else {
                            appearance.mobileBlue.copy(alpha = if (actionEnabled) 1f else 0.38f)
                        },
                    )
                    .noRippleClickable(enabled = actionEnabled, onClick = onAction)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    actionText,
                    color = if (actionDanger) ElecKoiDanger else appearance.mobileAccentFg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
// A section label, not a headline. At 20sp semibold these read louder than the fields they
// introduce, which is backwards — the label is a signpost and the field is the content.
fun ModelSectionHeader(text: String, appearance: AppearanceTheme, actions: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = appearance.mobileMuted,
            fontSize = 12.sp,
        )
        actions()
    }
}

// Secondary actions belong beside their section label, not as full-size cards competing with the
// fields. "Create a config" and "run the connection test" are not the same weight of action.
@Composable
internal fun ModelSectionAction(
    text: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) ElecKoiDanger else appearance.mobileMuted
    Row(
        modifier = Modifier
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeSvgIcon(icon, tint, iconSize = 14.dp)
        Text(text, color = tint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

// Compact numeric fields still use the same label/value hierarchy as every other field in this
// form. A different, heavier label treatment made the lower groups look like a second screen.
@Composable
fun ModelInlineField(
    label: String,
    value: String,
    placeholder: String,
    appearance: AppearanceTheme,
    scrollState: ScrollState? = null,
    imeBottomPx: Int = 0,
    keyboardType: KeyboardType = KeyboardType.Number,
    // Colours the value rather than adding a second line: on a row whose whole content is one
    // number, the number is the only thing that can be wrong.
    isError: Boolean = false,
    onChange: (String) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val marginPx = with(density) { 12.dp.toPx() }

    LaunchedEffect(focused, imeBottomPx, bounds) {
        val currentBounds = bounds ?: return@LaunchedEffect
        val currentScrollState = scrollState ?: return@LaunchedEffect
        if (focused && imeBottomPx > 0) {
            val keyboardTop = view.height - imeBottomPx
            val overflow = currentBounds.bottom - (keyboardTop - marginPx)
            if (overflow > 0f) currentScrollState.scrollBy(overflow)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusDismissInputRegion()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = appearance.mobileMuted,
            fontSize = 11.sp,
        )
        AppInsetTextField(
            value = value,
            onValueChange = onChange,
            appearance = appearance,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            textFieldModifier = Modifier
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(
                color = if (isError) ElecKoiDanger else appearance.mobileText,
                fontSize = 15.sp,
                textAlign = TextAlign.Start,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

// A row that opens something else rather than editing in place, so it shows a summary of what is
// behind it instead of an input.
@Composable
internal fun ModelNavigationRow(
    label: String,
    value: String,
    appearance: AppearanceTheme,
    valueMuted: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = appearance.mobileMuted, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                color = if (valueMuted) appearance.mobileSoft else appearance.mobileText,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StrokeSvgIcon(
                AppIconPaths.ChevronRight,
                appearance.mobileSoft,
                iconSize = 16.dp,
            )
        }
    }
}

@Composable
internal fun ModelStackedNavigationField(
    label: String,
    value: String,
    appearance: AppearanceTheme,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(interaction)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (label.isNotBlank()) {
            Text(label, color = appearance.mobileMuted, fontSize = 11.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (label.isNotBlank()) Modifier.padding(top = 2.dp) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                color = appearance.mobileText,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (enabled) {
                StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileSoft, iconSize = 16.dp)
            }
        }
    }
}

// The white card that holds a group of fields. Grouping is what turns a stack of separate boxes
// into a form: one boundary per group of related settings instead of one per setting.
@Composable
fun ModelFieldGroup(appearance: AppearanceTheme, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appearance.mobileSurface),
    ) {
        content()
    }
}

@Composable
internal fun ModelFieldDivider(appearance: AppearanceTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .height(0.5.dp)
            .background(appearance.mobileLine),
    )
}

// One note for a whole group. Repeating "（可选）" on every label and a full sentence in every
// placeholder said the same thing four times over while crowding out the values themselves.
@Composable
fun ModelSectionNote(text: String, appearance: AppearanceTheme) {
    Text(
        text,
        color = appearance.mobileSoft,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
    )
}

@Composable
internal fun ModelIconButton(paths: List<String>, appearance: AppearanceTheme, onClick: () -> Unit) {
    Box(modifier = Modifier.size(38.dp).noRippleClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        StrokeSvgIcon(paths, appearance.mobileText, iconSize = 21.dp)
    }
}

@Composable
internal fun ModelVersionSelector(
    configs: List<ModelConfig>,
    currentId: String,
    appearance: AppearanceTheme,
    onSelect: (ModelConfig) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = configs.firstOrNull { it.id == currentId } ?: configs.firstOrNull()
    // Renders as a row inside the group card rather than its own box, matching the fields it sits
    // with; the card around them supplies the surface.
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { open = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("配置版本", color = appearance.mobileMuted, fontSize = 11.sp)
                    Text(
                        text = current?.let(::configVersionName).orEmpty(),
                        color = appearance.mobileText,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileSoft, iconSize = 16.dp)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(appearance.mobileSurface),
            ) {
                configs.forEach { config ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(configVersionName(config), color = appearance.mobileText, fontSize = 15.sp)
                                val summary = listOf(config.model, config.baseUrl).filter { it.isNotBlank() }.joinToString(" · ")
                                if (summary.isNotBlank()) {
                                    Text(summary, color = appearance.mobileMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        },
                        onClick = {
                            open = false
                            onSelect(config)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ModelField(
    label: String,
    value: String,
    placeholder: String,
    appearance: AppearanceTheme,
    scrollState: ScrollState? = null,
    imeBottomPx: Int = 0,
    trailingIcon: List<String>? = null,
    trailingContentDescription: String? = null,
    secureEntry: Boolean = false,
    secureEntryVisible: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onTrailingClick: () -> Unit = {},
    onChange: (String) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val marginPx = with(density) { 12.dp.toPx() }

    LaunchedEffect(focused, imeBottomPx, bounds) {
        val currentBounds = bounds ?: return@LaunchedEffect
        val currentScrollState = scrollState ?: return@LaunchedEffect
        if (focused && imeBottomPx > 0) {
            val keyboardTop = view.height - imeBottomPx
            val overflow = currentBounds.bottom - (keyboardTop - marginPx)
            if (overflow > 0f) {
                currentScrollState.scrollBy(overflow)
            }
        }
    }

    // A row inside a shared group card, not a standalone box. Each field used to be its own 62dp
    // island with a floating label above it, so five fields read as five separate things stacked up
    // rather than one form; the label now sits inside the row and the card draws the boundary once.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusDismissInputRegion()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = appearance.mobileMuted, fontSize = 11.sp)
        AppInsetTextField(
            value = value,
            onValueChange = onChange,
            appearance = appearance,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            textFieldModifier = Modifier
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(color = appearance.mobileText, fontSize = 15.sp),
            keyboardOptions = if (secureEntry) {
                // Keep the value visually masked without requesting an OEM "secure
                // keyboard". Password input types disable clipboard paste and cause some
                // Vivo screen-mirroring implementations to blank the projected window.
                KeyboardOptions(keyboardType = KeyboardType.Ascii)
            } else {
                KeyboardOptions(keyboardType = keyboardType)
            },
            visualTransformation = if (secureEntry && !secureEntryVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            singleLine = singleLine,
            trailingContent = if (trailingIcon == null) {
                null
            } else {
                {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (trailingContentDescription != null) {
                                    Modifier.semantics { contentDescription = trailingContentDescription }
                                } else {
                                    Modifier
                                },
                            )
                            .noRippleClickable(onClick = onTrailingClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        StrokeSvgIcon(
                            trailingIcon,
                            if (secureEntryVisible) appearance.mobileBlue else appearance.mobileSoft,
                            iconSize = 18.dp,
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun ModelActionButton(
    text: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier,
    // Marks the one action on the page worth filling in. Four identical white cards gave equal
    // weight to "rename a config" and "run the connection test", which are not equal.
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = when {
        primary -> appearance.mobileAccentFg
        icon == AppIconPaths.Trash -> ElecKoiDanger
        else -> appearance.mobileText
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) appearance.mobileBlue else appearance.mobileSurface)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        StrokeSvgIcon(icon, contentColor, iconSize = 17.dp)
        Text(
            text,
            modifier = Modifier.padding(start = 7.dp),
            color = contentColor,
            fontSize = 14.sp,
        )
    }
}

