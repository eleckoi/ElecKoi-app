package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.fieldPalette

private val SearchMinimumTouchHeight = 44.dp

@Composable
fun SearchIcon(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    iconSize: Dp = 17.dp,
) {
    val field = appearance.fieldPalette()
    DshSearchGlyph(
        tint = field.icon,
        modifier = modifier,
        iconSize = iconSize,
    )
}

/**
 * The shared search control used by roots, editors, sheets, pickers, and drawers.
 *
 * Its visual material deliberately matches the root search: one flat theme search surface, a full
 * capsule silhouette, and no inset shading, border, glass, or feature-owned colour overrides.
 */
@Composable
fun AppSearchField(
    keyword: String,
    placeholder: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    height: Dp = SearchMinimumTouchHeight,
    fontSize: TextUnit = 15.sp,
    iconSize: Dp = 16.dp,
    inputModifier: Modifier = Modifier,
    containerColor: Color = appearance.mobileSearchBg,
    clearContentDescription: String = "清除搜索",
    onSearch: (() -> Unit)? = null,
    onKeywordChange: (String) -> Unit,
) {
    val field = appearance.fieldPalette()
    val keyboardController = LocalSoftwareKeyboardController.current
    val resolvedHeight = maxOf(height, SearchMinimumTouchHeight)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .height(resolvedHeight)
            .clip(shape)
            .background(containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon(appearance, iconSize = iconSize)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (keyword.isBlank()) {
                    Text(
                        text = placeholder,
                        color = field.placeholder,
                        fontSize = fontSize,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    modifier = inputModifier
                        .fillMaxWidth()
                        .semantics { contentDescription = placeholder },
                    textStyle = TextStyle(color = field.text, fontSize = fontSize),
                    cursorBrush = SolidColor(appearance.mobileBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (onSearch != null) onSearch() else keyboardController?.hide()
                        },
                    ),
                    singleLine = true,
                )
            }
            if (keyword.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(SearchMinimumTouchHeight)
                        .semantics {
                            contentDescription = clearContentDescription
                            role = Role.Button
                        }
                        .noRippleClickable { onKeywordChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeSvgIcon(
                        paths = AppIconPaths.X,
                        color = field.icon,
                        iconSize = 16.dp,
                        strokeWidth = 1.8f,
                    )
                }
            } else {
                Box(modifier = Modifier.width(13.dp))
            }
        }
    }
}

/** Shared geometry and material for the search row at the top of every bottom-navigation root. */
@Composable
fun RootSearchField(
    keyword: String,
    placeholder: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onKeywordChange: (String) -> Unit,
) {
    AppSearchField(
        keyword = keyword,
        placeholder = placeholder,
        appearance = appearance,
        modifier = modifier,
        onKeywordChange = onKeywordChange,
    )
}

@Composable
fun AppSearchSideButton(
    paths: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    size: Dp = SearchMinimumTouchHeight,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    val field = appearance.fieldPalette()
    val resolvedSize = maxOf(size, SearchMinimumTouchHeight)
    Box(
        modifier = modifier
            .size(resolvedSize)
            .clip(RoundedCornerShape(percent = 50))
            .background(appearance.mobileSearchBg)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StrokeSvgIcon(paths, field.icon, iconSize = iconSize, strokeWidth = 1.8f)
    }
}
