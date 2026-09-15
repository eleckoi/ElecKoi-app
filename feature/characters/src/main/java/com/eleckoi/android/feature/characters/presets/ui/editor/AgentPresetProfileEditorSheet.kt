package com.eleckoi.android.feature.characters.presets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelFamily
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetProfile
import com.eleckoi.android.feature.characters.presets.model.toTag
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.AvatarCircle
import com.eleckoi.android.foundation.design.components.DshGeneralGlyph
import com.eleckoi.android.foundation.design.components.ModelProviderIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetProfileEditorSheet(
    presetName: String,
    modelTags: List<AgentPresetModelTag>,
    profile: AgentPresetProfile,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onPickAvatar: () -> Unit,
    onSave: (AgentPresetProfile, String, List<AgentPresetModelTag>) -> Unit,
) {
    var authorName by remember(profile) { mutableStateOf(profile.authorName) }
    var editedPresetName by remember(presetName) { mutableStateOf(presetName) }
    val tagChoices = remember {
        AgentPresetModelFamily.entries
            .filterNot { it == AgentPresetModelFamily.Other }
            .map(AgentPresetModelFamily::toTag)
    }
    var selectedTagIds by remember(modelTags) {
        val standardIds = tagChoices.mapTo(mutableSetOf()) { it.id }
        mutableStateOf(modelTags.map { it.id.trim().lowercase() }.filter { it in standardIds }.toSet())
    }
    var customTagText by remember(modelTags) {
        val standardIds = tagChoices.mapTo(mutableSetOf()) { it.id }
        mutableStateOf(
            modelTags
                .filterNot { it.id.trim().lowercase() in standardIds }
                .joinToString("、", transform = AgentPresetModelTag::label),
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = appearance.mobileSurface,
        contentColor = appearance.mobileText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Text("编辑作者资料", color = appearance.mobileText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .noRippleClickable(onClick = onPickAvatar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(56.dp)) {
                    AvatarCircle(
                        name = authorName.ifBlank { editedPresetName },
                        size = 52,
                        fontSize = 19,
                        appearance = appearance,
                        avatarPath = profile.authorAvatarPath,
                        shape = RoundedCornerShape(10.dp),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(appearance.mobileBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        StrokeSvgIcon(
                            AppIconPaths.EditSquare,
                            appearance.mobileAccentFg,
                            iconSize = 14.dp,
                            strokeWidth = 1.75f,
                        )
                    }
                }
                Text(
                    "更换作者头像",
                    modifier = Modifier.weight(1f).padding(start = 13.dp),
                    color = appearance.mobileText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                StrokeSvgIcon(AppIconPaths.ChevronRight, appearance.mobileMuted, iconSize = 17.dp)
            }
            SheetFieldLabel("作者名字", appearance)
            AppInsetTextField(
                value = authorName,
                onValueChange = { authorName = it.take(40) },
                placeholder = "作者名字",
                appearance = appearance,
            )
            SheetFieldLabel("预设名字", appearance, Modifier.padding(top = 13.dp))
            AppInsetTextField(
                value = editedPresetName,
                onValueChange = { editedPresetName = it.take(60) },
                placeholder = "预设名字",
                appearance = appearance,
            )
            SheetFieldLabel("模型标签（可多选）", appearance, Modifier.padding(top = 13.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tagChoices.forEach { tag ->
                    ModelTagToggle(
                        tag = tag,
                        selected = tag.id.lowercase() in selectedTagIds,
                        appearance = appearance,
                        onClick = {
                            val id = tag.id.lowercase()
                            selectedTagIds = if (id in selectedTagIds) {
                                (selectedTagIds - id).ifEmpty { setOf(AgentPresetModelFamily.General.storageValue) }
                            } else if (id == AgentPresetModelFamily.General.storageValue) {
                                setOf(id)
                            } else {
                                (selectedTagIds - AgentPresetModelFamily.General.storageValue) + id
                            }
                        },
                    )
                }
            }
            SheetFieldLabel("自定义模型标签", appearance, Modifier.padding(top = 13.dp))
            AppInsetTextField(
                value = customTagText,
                onValueChange = { customTagText = it.take(100) },
                placeholder = "用逗号或顿号分隔（可选）",
                appearance = appearance,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消", color = appearance.mobileMuted) }
                TextButton(
                    onClick = {
                        val selectedTags = tagChoices.filter { it.id.lowercase() in selectedTagIds }
                        val customTags = customTagText
                            .split(',', '，', '、')
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .distinct()
                            .take(5)
                            .map { label ->
                                AgentPresetModelTag(
                                    id = "custom-${label.lowercase().replace(Regex("[^a-z0-9\\p{L}]+"), "-").trim('-')}",
                                    label = label.take(20),
                                )
                            }
                        onSave(
                            profile.copy(authorName = authorName),
                            editedPresetName,
                            (selectedTags + customTags).ifEmpty { listOf(AgentPresetModelFamily.General.toTag()) },
                        )
                    },
                ) {
                    Text("保存", color = appearance.mobileBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ModelTagToggle(
    tag: AgentPresetModelTag,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(
                if (selected) appearance.mobileBlue.copy(alpha = if (appearance.isDark) 0.18f else 0.09f)
                else appearance.mobilePinnedBg,
            )
            .border(
                1.dp,
                if (selected) appearance.mobileBlue.copy(alpha = 0.42f) else Color.Transparent,
                shape,
            )
            .semantics {
                contentDescription = "${tag.label}模型标签${if (selected) "，已选择" else ""}"
                role = Role.Checkbox
            }
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tag.id == AgentPresetModelFamily.General.storageValue) {
            DshGeneralGlyph(
                tint = if (selected) appearance.mobileBlue else appearance.mobileMuted,
                modifier = Modifier.size(16.dp),
                iconSize = 16.dp,
            )
            Spacer(Modifier.width(6.dp))
        } else if (tag.providerId.isNotBlank()) {
            ModelProviderIcon(
                providerId = tag.providerId,
                initials = tag.label.take(1),
                appearance = appearance,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            tag.label,
            color = if (selected) appearance.mobileBlue else appearance.mobileText,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        if (selected) {
            StrokeSvgIcon(
                AppIconPaths.Check,
                appearance.mobileBlue,
                iconSize = 13.dp,
                strokeWidth = 2.2f,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
internal fun SheetFieldLabel(text: String, appearance: AppearanceTheme, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(bottom = 7.dp), color = appearance.mobileMuted, fontSize = 12.sp)
}
