package com.eleckoi.android.feature.characters.presets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelFamily
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetSummary
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AvatarCircle
import com.eleckoi.android.foundation.design.components.DshGeneralGlyph
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.dropShadow
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun PresetProfileCard(
    preset: AgentPresetSummary,
    active: Boolean,
    appearance: AppearanceTheme,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
) {
    var tagsExpanded by rememberSaveable(preset.id) { mutableStateOf(false) }
    val visibleTags = if (tagsExpanded) preset.modelTags else preset.modelTags.take(4)
    val hiddenTagCount = preset.modelTags.size - visibleTags.size
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dropShadow(shape, appearance.mobileText.copy(alpha = 0.05f), blur = 13.dp, offsetY = 4.dp)
            .clip(shape)
            .background(appearance.mobileSurface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(
                name = preset.profile.authorName.ifBlank { preset.name },
                size = 46,
                fontSize = 17,
                appearance = appearance,
                avatarPath = preset.profile.authorAvatarPath,
                shape = RoundedCornerShape(12.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
                Text(
                    preset.profile.authorName.ifBlank { "未填写作者" },
                    color = appearance.mobileText,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    preset.name,
                    modifier = Modifier.padding(top = 4.dp),
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .semantics {
                        contentDescription = "编辑作者与预设资料"
                        role = Role.Button
                    }
                    .noRippleClickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(
                    AppIconPaths.EditSquare,
                    appearance.mobileMuted,
                    iconSize = 18.dp,
                    strokeWidth = 1.65f,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            visibleTags.forEach { tag -> ProfileTagChip(tag, appearance) }
            if (hiddenTagCount > 0) {
                ProfileTagOverflow(hiddenTagCount, appearance) {
                    tagsExpanded = true
                }
            }
            Text(
                if (active) "使用中" else "设为使用中",
                modifier = Modifier
                    .height(23.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(appearance.mobileBlue.copy(alpha = if (appearance.isDark) 0.18f else 0.12f))
                    .noRippleClickable(enabled = !active, onClick = onSetActive)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = appearance.mobileBlue,
                fontSize = 11.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProfileTagChip(tag: AgentPresetModelTag, appearance: AppearanceTheme) {
    Row(
        modifier = Modifier
            .height(23.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(appearance.mobileBlue.copy(alpha = if (appearance.isDark) 0.17f else 0.12f))
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            tag.id == AgentPresetModelFamily.General.storageValue -> DshGeneralGlyph(
                tint = appearance.mobileBlue,
                modifier = Modifier.size(13.dp),
                iconSize = 13.dp,
            )
            tag.providerId.isNotBlank() -> ModelProviderIcon(
                providerId = tag.providerId,
                initials = tag.label.take(1),
                appearance = appearance,
                modifier = Modifier.size(13.dp),
            )
        }
        if (tag.id == AgentPresetModelFamily.General.storageValue || tag.providerId.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
        }
        Text(
            tag.label,
            color = appearance.mobileBlue,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileTagOverflow(
    count: Int,
    appearance: AppearanceTheme,
    onExpand: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(23.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(appearance.mobileBlue.copy(alpha = if (appearance.isDark) 0.11f else 0.07f))
            .semantics {
                contentDescription = "展开其余$count 个模型标签"
                role = Role.Button
            }
            .noRippleClickable(onClick = onExpand)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+$count",
            color = appearance.mobileBlue.copy(alpha = 0.86f),
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
