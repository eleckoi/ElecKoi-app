package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import com.eleckoi.android.feature.characters.modes.story.ui.shared.*

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.dropShadow
import com.eleckoi.android.foundation.design.components.innerShadow
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.imeBringIntoViewOnFocus
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.ElecKoiDanger

@Composable
internal fun EntryEditorTopBar(
    title: String,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    menuVisible: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    trailingActionLabel: String? = null,
    onTrailingAction: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(appearance.storyEditorPalette().pageBg)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
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
        Text(title, color = appearance.mobileText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (trailingActionLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(58.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(appearance.mobileSurface)
                    .noRippleClickable(onClick = onTrailingAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    trailingActionLabel,
                    color = appearance.mobileBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (menuVisible) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(appearance.mobileSurface)
                        .noRippleClickable { onMenuExpandedChange(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeSvgIcon(AppIconPaths.MoreDots, appearance.mobileText, iconSize = 22.dp, strokeWidth = 2.3f)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                    modifier = Modifier.background(appearance.mobileSurface),
                ) {
                    DropdownMenuItem(
                        text = { Text("删除条目", color = appearance.mobileText, fontSize = 14.sp) },
                        leadingIcon = { StrokeSvgIcon(AppIconPaths.Trash, ElecKoiDanger, iconSize = 19.dp, strokeWidth = 1.7f) },
                        onClick = {
                            onMenuExpandedChange(false)
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun EntryTitleField(
    value: String,
    appearance: AppearanceTheme,
    scrollState: androidx.compose.foundation.ScrollState,
    imeBottomPx: Int,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = StoryEditorCardSpacing)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(appearance.mobileSurface)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text("条目标题", color = appearance.mobileText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            AppInsetTextField(
                value = value,
                onValueChange = onChange,
                appearance = appearance,
                placeholder = placeholder,
                modifier = Modifier.padding(top = 10.dp),
                textFieldModifier = Modifier.imeBringIntoViewOnFocus(scrollState, imeBottomPx),
                textStyle = TextStyle(color = appearance.mobileText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
internal fun EntryTriggerModePicker(
    selected: SettingLibraryTriggerMode?,
    appearance: AppearanceTheme,
    onSelect: (SettingLibraryTriggerMode) -> Unit,
) {
    val modes = listOf(
        SettingLibraryTriggerMode.AgentTool,
        SettingLibraryTriggerMode.Always,
    )
    val activeIndex = modes.indexOf(selected).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = StoryEditorCardSpacing)
            .clip(RoundedCornerShape(18.dp))
            .background(appearance.mobileSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        EditorFieldLabel("触发方式", appearance)
        // A recessed track with one raised thumb sliding in it, rather than three equal boxes where
        // the chosen one changes fill. Three boxes taking turns going pale blue has no hierarchy in
        // it — nothing is in front of anything — which is what made this row read as a template.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(appearance.storyEditorPalette().track)
                // The groove, transcribed: 1.5dp down, 2.5dp of blur, ink at 7.5%. The gradient
                // that stood in for it had no blur and touched only the top edge.
                .innerShadow(
                    shape = RoundedCornerShape(14.dp),
                    color = appearance.mobileText.copy(alpha = 0.075f),
                    blur = 2.5.dp,
                    offsetY = 1.5.dp,
                ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val thumbWidth = maxWidth / modes.size
            val thumbOffset by animateDpAsState(
                targetValue = thumbWidth * activeIndex,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                label = "triggerModeThumb",
            )
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .dropShadow(RoundedCornerShape(11.dp), appearance.mobileText.copy(alpha = 0.07f), blur = 1.dp, offsetY = 1.dp)
                    .dropShadow(RoundedCornerShape(11.dp), appearance.mobileText.copy(alpha = 0.22f), blur = 8.dp, offsetY = 3.dp, spread = (-2).dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(appearance.mobileSurface),
            )
            Row(modifier = Modifier.fillMaxSize()) {
                modes.forEach { mode ->
                    val active = mode == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .noRippleClickable { onSelect(mode) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (mode) {
                                SettingLibraryTriggerMode.AgentTool -> "Agent 读取"
                                SettingLibraryTriggerMode.Always -> "提示词常驻"
                                SettingLibraryTriggerMode.Cache -> "缓存设定"
                            },
                            color = if (active) appearance.mobileText else appearance.mobileMuted,
                            fontSize = 13.5.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
internal fun EntryEditorSectionTabs(
    selected: EntryEditorSection,
    sections: List<EntryEditorSection> = EntryEditorSection.entries,
    contentLabel: String,
    appearance: AppearanceTheme,
    onSelect: (EntryEditorSection) -> Unit,
) {
    // 24dp discs, the rule running through their centres, a tick on the steps already behind you.
    // The old rail spent 58dp of height and still left the connector floating above the numbers.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 26.dp, top = 2.dp, bottom = 14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            sections.forEachIndexed { index, section ->
                // The node is exactly as wide as its disc, so the connector between two nodes runs
                // edge to edge and meets them. At 44dp the disc had 10dp of empty column on each
                // side and the rule stopped short of it at both ends — four dashes, not a rail.
                EntryEditorStepNode(
                    section = section,
                    label = if (section == EntryEditorSection.Content) contentLabel else section.label,
                    selected = selected,
                    stepNumber = index + 1,
                    completed = index < sections.indexOf(selected),
                    appearance = appearance,
                    modifier = Modifier.width(24.dp),
                    onClick = { onSelect(section) },
                )
                if (index < sections.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 11.dp)
                            .height(2.dp)
                            .background(
                                if (index < sections.indexOf(selected)) {
                                    appearance.mobileBlue.copy(alpha = 0.34f)
                                } else {
                                    appearance.mobileSoft.copy(alpha = 0.34f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryEditorStepNode(
    section: EntryEditorSection,
    label: String,
    selected: EntryEditorSection,
    stepNumber: Int,
    completed: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val active = section == selected
    Column(
        modifier = modifier.noRippleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when {
                        active -> appearance.mobileBlue
                        completed -> appearance.mobileBlue.copy(alpha = 0.22f)
                        else -> appearance.mobileSoft.copy(alpha = 0.34f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                StrokeSvgIcon(AppIconPaths.Check, appearance.mobileBlue, iconSize = 13.dp, strokeWidth = 2.6f)
            } else {
                Text(
                    stepNumber.toString(),
                    color = if (active) appearance.mobileAccentFg else appearance.mobileMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Unbounded so the label can be wider than the disc it belongs to without pushing the
        // connectors apart.
        Text(
            label,
            modifier = Modifier
                .padding(top = 6.dp)
                .wrapContentWidth(unbounded = true),
            color = if (active) appearance.mobileText else appearance.mobileMuted,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
