package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isPinnedEntry
import com.eleckoi.android.foundation.design.components.DshFolderGlyph
import com.eleckoi.android.foundation.design.components.DshTreeDisclosureGlyph
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.dshTreeRowEntrance
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.themedListRowClickable
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode

@Composable
internal fun SettingTreeNodeRow(
    node: SettingTreeNode,
    selected: Boolean,
    dropTarget: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    reorderModifier: Modifier = Modifier,
    expanded: Boolean,
    horizontalScrollState: ScrollState,
    appearance: AppearanceTheme,
    readOnly: Boolean = false,
    externalPresetSource: Boolean = false,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onFileEnabledChange: (SettingLibraryEntry, Boolean) -> Unit,
    onToggle: () -> Unit,
) {
    val fileEnabled = (node as? SettingTreeNode.File)?.entry?.enabled ?: true
    var lastClickAt by remember(node.id) { mutableLongStateOf(0L) }
    // A disabled file dims its name and glyph, not the whole row. Fading the switch along with
    // everything else took the one control that is still worth touching down to a smudge.
    val contentAlpha = if (fileEnabled) 1f else 0.42f
    Row(
        modifier = modifier
            .dshTreeRowEntrance(enabled = node.depth > 0)
            .horizontalScroll(horizontalScrollState)
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .fillMaxWidth()
            .widthIn(min = 300.dp + (node.depth * 14).dp)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    dragging -> appearance.mobileSurface
                    selected -> appearance.mobileSurface
                    externalPresetSource -> appearance.mobileSurface
                    else -> appearance.mobileBg
                },
            )
            .border(
                width = if (selected || dropTarget) 1.dp else 0.dp,
                color = when {
                    dropTarget -> appearance.mobileBlue.copy(alpha = 0.65f)
                    selected -> appearance.mobileBlue
                    else -> appearance.mobileBg
                },
                shape = RoundedCornerShape(8.dp),
            )
            .then(reorderModifier)
            // themedListRowClickable rather than noRippleClickable: the double-click routing still
            // lives in onClick, and this is what gives the row the same press settle every other
            // list in the app has. Tapping a tree row used to look like nothing had happened.
            .themedListRowClickable(appearance = appearance) {
                val now = SystemClock.uptimeMillis()
                if (now - lastClickAt <= DoubleClickMillis) {
                    lastClickAt = 0L
                    onOpen()
                } else {
                    lastClickAt = now
                    onSelect()
                }
            }
            .padding(start = (4 + node.depth * 18).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (node) {
            is SettingTreeNode.Folder -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .noRippleClickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    DshTreeDisclosureGlyph(
                        expanded = expanded,
                        tint = appearance.mobileMuted,
                        iconSize = 14.dp,
                    )
                }
                Spacer(modifier = Modifier.width(3.dp))
                SettingFolderGlyph(
                    expanded = expanded,
                    selected = selected,
                    externalPresetSource = externalPresetSource,
                    appearance = appearance,
                )
            }
            is SettingTreeNode.File -> {
                if (node.isEjsController) {
                    Spacer(modifier = Modifier.width(27.dp))
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = if (node.entry.enabled) appearance.mobileBlue else appearance.mobileMuted,
                        modifier = Modifier.size(20.dp).alpha(contentAlpha),
                    )
                } else if (node.isEjsReference) {
                    Spacer(modifier = Modifier.width(27.dp))
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = if (node.entry.enabled) appearance.mobileBlue else appearance.mobileMuted,
                        modifier = Modifier.size(19.dp).alpha(contentAlpha),
                    )
                } else {
                    if (node.entry.isPinnedEntry()) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = appearance.mobileMuted.copy(alpha = 0.74f),
                                modifier = Modifier.size(15.dp).alpha(contentAlpha),
                            )
                        }
                        Spacer(modifier = Modifier.width(3.dp))
                    } else {
                        Spacer(modifier = Modifier.width(27.dp))
                    }
                    SettingFileGlyph(
                        dynamic = false,
                        enabled = node.entry.enabled,
                        opening = node.entry.isOpeningEntry(),
                        cache = node.entry.triggerMode == SettingLibraryTriggerMode.Cache,
                        externalPresetSource = externalPresetSource,
                        appearance = appearance,
                        modifier = Modifier.alpha(contentAlpha),
                    )
                }
            }
        }
        Text(
            node.title,
            modifier = Modifier.weight(1f).padding(start = 7.dp, end = 8.dp).alpha(contentAlpha),
            color = appearance.mobileText,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (externalPresetSource && node.depth == 0) {
            Text(
                text = "预设",
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(appearance.mobileBlue.copy(alpha = if (appearance.isDark) 0.22f else 0.12f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = appearance.mobileBlue,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        when (node) {
            is SettingTreeNode.Folder -> Text(
                // No brackets. On a line that holds exactly one number, they are punctuation doing
                // no work.
                node.count.toString(),
                color = appearance.mobileMuted.copy(alpha = 0.72f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            is SettingTreeNode.File -> {
                if (!readOnly) {
                    SmallFileEnabledSwitch(
                        checked = node.entry.enabled,
                        appearance = appearance,
                        onClick = { onFileEnabledChange(node.entry, !node.entry.enabled) },
                    )
                }
            }
        }
    }
}

private val DoubleClickMillis = (ViewConfiguration.getDoubleTapTimeout() + ViewConfiguration.getTapTimeout()).toLong()

@Composable
private fun SettingFolderGlyph(
    expanded: Boolean,
    selected: Boolean,
    externalPresetSource: Boolean,
    appearance: AppearanceTheme,
) {
    val outline = when {
        selected -> appearance.mobileBlue
        externalPresetSource -> appearance.mobileBlue.copy(alpha = 0.92f)
        expanded -> appearance.mobileText.copy(alpha = 0.58f)
        else -> appearance.mobileMuted.copy(alpha = 0.78f)
    }
    DshFolderGlyph(
        expanded = expanded,
        tint = outline,
        iconSize = 20.dp,
    )
}

@Composable
private fun SettingFileGlyph(
    dynamic: Boolean,
    enabled: Boolean,
    opening: Boolean,
    cache: Boolean,
    externalPresetSource: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        !enabled -> appearance.mobileMuted.copy(alpha = 0.72f)
        externalPresetSource -> appearance.mobileBlue.copy(alpha = 0.9f)
        dynamic -> appearance.mobileBlue.copy(alpha = 0.78f)
        else -> appearance.mobileBlue.copy(alpha = 0.56f)
    }
    when {
        cache -> StrokeSvgIcon(
            paths = SettingLibraryIcons.Cache,
            color = tint,
            modifier = modifier,
            iconSize = 19.dp,
            strokeWidth = 1.7f,
        )
        opening -> Icon(
            imageVector = Icons.Rounded.ChatBubble,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(19.dp),
        )
        else -> SettingLibraryPromptGlyph(
            tint = tint,
            modifier = modifier,
            iconSize = 19.dp,
        )
    }
}

/**
 * The knob travels on a spring that overshoots a hair and settles; the track crossfades behind it
 * over 220ms. The switch used to jump between two states in one frame, which reads as the app
 * redrawing rather than as you having flipped something.
 *
 * The knob is deliberately drawn outside the clipped track so the overshoot can leave the rail
 * instead of being sliced flat against it.
 */
@Composable
private fun SmallFileEnabledSwitch(
    checked: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 14.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 760f),
        label = "setting_file_switch_knob",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            appearance.mobileBlue.copy(alpha = 0.86f)
        } else {
            appearance.mobileMuted.copy(alpha = 0.26f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "setting_file_switch_track",
    )
    Box(
        modifier = Modifier
            .size(width = 30.dp, height = 18.dp)
            .noRippleClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .background(trackColor),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobOffset)
                .size(14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(appearance.mobileSurface),
        )
    }
}
