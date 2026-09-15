package com.eleckoi.android.feature.characters.presets.ui.library

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelFamily
import com.eleckoi.android.feature.characters.presets.model.AgentPresetSummary
import com.eleckoi.android.feature.characters.presets.model.toTag
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AvatarCircle
import com.eleckoi.android.foundation.design.components.DshGeneralGlyph
import com.eleckoi.android.foundation.design.components.DshSettingsGlyph
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.dropShadow
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun PresetCardsList(
    presets: List<AgentPresetSummary>,
    activePresetId: String,
    selectedPresetIds: Set<String>,
    selectionMode: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    onOpenPreset: (String) -> Unit,
    onOpenActions: (AgentPresetSummary) -> Unit,
    onToggleSelected: (AgentPresetSummary) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (presets.isEmpty()) {
            item(key = "empty-presets") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("没有找到预设", color = appearance.mobileMuted, fontSize = 14.sp)
                }
            }
        } else {
            items(presets, key = { "preset:${it.id}" }) { preset ->
                PresetWideCard(
                    preset = preset,
                    active = preset.id == activePresetId,
                    selectionMode = selectionMode,
                    selected = preset.id in selectedPresetIds,
                    appearance = appearance,
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 150),
                        placementSpec = tween(durationMillis = 150),
                        fadeOutSpec = tween(durationMillis = 75),
                    ),
                    onOpen = { onOpenPreset(preset.id) },
                    onOpenActions = { onOpenActions(preset) },
                    onToggleSelected = { onToggleSelected(preset) },
                )
            }
        }
    }
}

@Composable
internal fun PresetChip(
    family: AgentPresetModelFamily,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val tag = family.toTag()
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) appearance.mobilePinnedBg else appearance.mobileSurface)
            .border(0.5.dp, if (selected) appearance.mobileBlue.copy(alpha = 0.16f) else appearance.mobileLine, shape)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (family == AgentPresetModelFamily.General) {
            DshGeneralGlyph(
                tint = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                modifier = Modifier.size(14.dp),
                iconSize = 14.dp,
            )
        } else if (tag.providerId.isNotBlank()) {
            ModelProviderIcon(
                providerId = tag.providerId,
                initials = tag.label.take(1),
                appearance = appearance,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            family.label,
            color = if (selected) appearance.mobileText else appearance.mobileMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun PresetWideCard(
    preset: AgentPresetSummary,
    active: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onOpenActions: () -> Unit,
    onToggleSelected: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 94.dp)
            .dropShadow(shape, appearance.mobileText.copy(alpha = 0.045f), blur = 10.dp, offsetY = 3.dp)
            .clip(shape)
            .background(if (active) appearance.mobilePinnedBg else appearance.mobileSurface)
            .border(
                width = 0.5.dp,
                color = if (active) {
                    appearance.mobileBlue.copy(alpha = if (appearance.isDark) 0.24f else 0.14f)
                } else {
                    appearance.mobileLine
                },
                shape = shape,
            )
            .noRippleClickable(onClick = if (selectionMode) onToggleSelected else onOpen)
            .padding(start = if (selectionMode) 3.dp else 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelected() },
                    modifier = Modifier.size(48.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = appearance.mobileBlue,
                        uncheckedColor = appearance.mobileMuted.copy(alpha = 0.72f),
                        checkmarkColor = appearance.mobileAccentFg,
                    ),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 9.dp, end = 11.dp, bottom = 8.dp),
            ) {
                Text(
                    preset.name,
                    modifier = Modifier.padding(end = if (selectionMode) 0.dp else 40.dp),
                    color = appearance.mobileText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarCircle(
                        name = preset.profile.authorName.ifBlank { preset.name },
                        size = 22,
                        fontSize = 9,
                        appearance = appearance,
                        avatarPath = preset.profile.authorAvatarPath,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    )
                    Text(
                        preset.profile.authorName.ifBlank { "未署名" },
                        modifier = Modifier.padding(start = 6.dp),
                        color = appearance.mobileMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PresetModelTags(
                        tags = preset.modelTags,
                        appearance = appearance,
                        modifier = Modifier.weight(1f),
                    )
                    if (active && !selectionMode) {
                        Text(
                            "使用中",
                            modifier = Modifier
                                .padding(start = 7.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                                .background(appearance.mobileBlue.copy(alpha = if (appearance.isDark) 0.18f else 0.09f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = appearance.mobileBlue,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (!selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .semantics {
                        contentDescription = "预设操作"
                        role = Role.Button
                    }
                    .noRippleClickable(onClick = onOpenActions),
                contentAlignment = Alignment.Center,
            ) {
                DshSettingsGlyph(
                    tint = appearance.mobileText.copy(alpha = 0.72f),
                    iconSize = 18.dp,
                )
            }
        }
    }
}

@Composable
private fun PresetModelTags(
    tags: List<AgentPresetModelTag>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        tags.firstOrNull()?.let { tag ->
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tag.id == AgentPresetModelFamily.General.storageValue) {
                    DshGeneralGlyph(
                        tint = appearance.mobileMuted,
                        modifier = Modifier.size(14.dp),
                        iconSize = 14.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                } else if (tag.providerId.isNotBlank()) {
                    ModelProviderIcon(
                        providerId = tag.providerId,
                        initials = tag.label.take(1),
                        appearance = appearance,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    tag.label,
                    modifier = Modifier.weight(1f, fill = false),
                    color = appearance.mobileMuted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tags.size > 1) {
            Text(
                "+${tags.size - 1}",
                modifier = Modifier.padding(start = 5.dp, end = 1.dp),
                color = appearance.mobileBlue.copy(alpha = 0.82f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
