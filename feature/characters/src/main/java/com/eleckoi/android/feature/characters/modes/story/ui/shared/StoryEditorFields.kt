package com.eleckoi.android.feature.characters.modes.story.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppSearchField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.focusDismissInputRegion
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.fieldPalette
/**
 * The one section label.
 *
 * Shared editor headings use one size, weight, and alignment so a page reads as one hierarchy.
 */
@Composable
internal fun EditorFieldLabel(
    text: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    // No indent of its own. The label used to sit 14dp inside its own section, which put its first
    // glyph 14dp to the right of the left edge of the card it names — the two never lined up.
    Text(
        text,
        modifier = modifier.padding(start = 2.dp, bottom = 9.dp),
        color = appearance.mobileText,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun SearchInput(
    value: String,
    placeholder: String,
    appearance: AppearanceTheme,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    AppSearchField(
        keyword = value,
        placeholder = placeholder,
        appearance = appearance,
        modifier = modifier.focusDismissInputRegion(),
        iconSize = 18.dp,
        onKeywordChange = onChange,
    )
}

@Composable
internal fun DropdownField(
    label: String,
    value: DropdownOption?,
    placeholder: String,
    options: List<DropdownOption>,
    appearance: AppearanceTheme,
    showDescriptions: Boolean = true,
    groupedStyle: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val field = appearance.fieldPalette()
    val inputShape = if (groupedStyle) RoundedCornerShape(13.dp) else StoryEditorShapes.Control
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (groupedStyle) StoryEditorCardSpacing else 13.dp)
            .then(
                if (groupedStyle) {
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(appearance.mobileSurface)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            label,
            color = appearance.mobileText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (groupedStyle) 52.dp else 42.dp)
                    .onSizeChanged { size -> anchorWidth = with(density) { size.width.toDp() } }
                    .border(0.5.dp, field.border, inputShape)
                    .noRippleClickable { expanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DropdownLeading(option = value, appearance = appearance)
                Text(
                    value?.title ?: placeholder,
                    modifier = Modifier.weight(1f).padding(start = if (value?.hasLeading == true) 8.dp else 0.dp),
                    color = if (value == null) appearance.mobileMuted else appearance.mobileText,
                    fontSize = 14.sp,
                    fontWeight = if (value == null) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StrokeSvgIcon(AppIconPaths.ChevronDown, appearance.mobileMuted, iconSize = 17.dp, strokeWidth = 1.8f)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .then(if (anchorWidth > 0.dp) Modifier.width(anchorWidth) else Modifier)
                    .background(appearance.mobileSurface),
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DropdownLeading(option = option, appearance = appearance)
                                Column(modifier = Modifier.padding(start = if (option.hasLeading) 10.dp else 0.dp)) {
                                    Text(
                                        option.title,
                                        color = appearance.mobileText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (showDescriptions) {
                                        Text(
                                            option.description,
                                            color = appearance.mobileMuted,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(index)
                        },
                    )
                }
            }
        }
    }
}

private val DropdownOption.hasLeading: Boolean
    get() = leadingText.isNotBlank() || leadingIcon.isNotEmpty() || leadingImageVector != null

@Composable
private fun DropdownLeading(option: DropdownOption?, appearance: AppearanceTheme) {
    if (option == null) return
    when {
        option.leadingImageVector != null -> Icon(
            imageVector = option.leadingImageVector,
            contentDescription = null,
            tint = appearance.mobileMuted,
            modifier = Modifier.size(18.dp),
        )
        option.leadingIcon.isNotEmpty() -> StrokeSvgIcon(
            option.leadingIcon,
            appearance.mobileMuted,
            iconSize = 17.dp,
            strokeWidth = 1.75f,
        )
        option.leadingText.isNotBlank() -> Text(
            option.leadingText,
            color = appearance.mobileMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

