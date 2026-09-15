package com.eleckoi.android.feature.characters.presets.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.AgentPresetProfile
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.characters.presets.model.AgentPresetSummary
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.ImageCropPage
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.saveBitmapToCache
import java.io.File
@Composable
internal fun AgentPresetOverviewPage(
    preset: AgentPreset,
    active: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onSetActive: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateProfile: (AgentPresetProfile) -> Unit,
    onUpdateModelTags: (List<AgentPresetModelTag>) -> Unit,
    onUpdateAuthorAvatar: (Map<AvatarSlot, File>) -> Unit,
) {
    val context = LocalContext.current
    var profileEditorOpen by rememberSaveable(preset.id) { mutableStateOf(false) }
    var timelineEditorOpen by rememberSaveable(preset.id) { mutableStateOf(false) }
    var avatarCropSource by remember(preset.id) { mutableStateOf<Uri?>(null) }
    var timelineExpanded by rememberSaveable(preset.id) { mutableStateOf(false) }
    var usageDraft by remember(preset.id) { mutableStateOf(preset.profile.usageInstructions) }
    val summary = remember(preset) {
        AgentPresetSummary(
            id = preset.id,
            name = preset.name,
            modelFamily = preset.modelFamily,
            modelTags = preset.modelTags,
            libraryGroupId = preset.libraryGroupId,
            activeVersionId = preset.activeVersionId,
            activeVersionNumber = preset.activeVersionNumber,
            entryCount = preset.entries.size,
            profile = preset.profile,
        )
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) avatarCropSource = uri
    }
    LaunchedEffect(preset.profile.usageInstructions) {
        if (usageDraft != preset.profile.usageInstructions) usageDraft = preset.profile.usageInstructions
    }
    LaunchedEffect(usageDraft) {
        if (usageDraft == preset.profile.usageInstructions) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        onUpdateProfile(preset.profile.copy(usageInstructions = usageDraft))
    }

    avatarCropSource?.let { sourceUri ->
        ImageCropPage(
            sourceUri = sourceUri,
            title = "裁剪作者头像",
            cropAspect = 1f,
            circularFrame = false,
            outputWidth = 420,
            appearance = appearance,
            onBack = { avatarCropSource = null },
            onPickAnother = { avatarPicker.launch("image/*") },
            onCropped = { cropped, _ ->
                val file = saveBitmapToCache(
                    context.cacheDir,
                    cropped,
                    "agent-preset-author-${preset.id}",
                    Bitmap.CompressFormat.PNG,
                    96,
                )
                avatarCropSource = null
                onUpdateAuthorAvatar(mapOf(AvatarSlot.Circle to file))
            },
        )
        return
    }

        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                item(key = "profile:${preset.id}") {
                    PresetProfileCard(
                        preset = summary,
                        active = active,
                        appearance = appearance,
                        onEdit = { profileEditorOpen = true },
                        onSetActive = onSetActive,
                    )
                }
                item(key = "usage-title") {
                    Text(
                        "使用说明",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = appearance.mobileText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item(key = "usage-editor") {
                    AppInsetTextField(
                        value = usageDraft,
                        onValueChange = { usageDraft = it.take(8_000) },
                        appearance = appearance,
                        modifier = Modifier.heightIn(min = 132.dp),
                        placeholder = "写下这个预设适合什么场景、如何使用，以及需要注意的事项……",
                        singleLine = false,
                        textFieldModifier = Modifier.heightIn(min = 108.dp),
                    )
                }
                item(key = "timeline-title") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "更新时间线",
                            modifier = Modifier.weight(1f),
                            color = appearance.mobileText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier
                                .semantics {
                                    contentDescription = "编辑更新时间线"
                                    role = Role.Button
                                }
                                .noRippleClickable { timelineEditorOpen = true }
                                .height(48.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StrokeSvgIcon(
                                AppIconPaths.EditSquare,
                                appearance.mobileBlue,
                                iconSize = 17.dp,
                                strokeWidth = 1.65f,
                            )
                            Text(
                                "编辑",
                                modifier = Modifier.padding(start = 5.dp),
                                color = appearance.mobileBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (preset.profile.timeline.isEmpty()) {
                    item(key = "timeline-empty") {
                        EmptyTimelineCard(
                            appearance = appearance,
                            onClick = { timelineEditorOpen = true },
                        )
                    }
                } else {
                    val shown = if (timelineExpanded) {
                        preset.profile.timeline
                    } else {
                        preset.profile.timeline.take(3)
                    }
                    item(key = "timeline-list") {
                        Column {
                            shown.forEachIndexed { index, item ->
                                TimelineEntryCard(
                                    item = item,
                                    newest = index == 0,
                                    last = index == shown.lastIndex,
                                    appearance = appearance,
                                )
                            }
                        }
                    }
                    if (preset.profile.timeline.size > 3) {
                        item(key = "timeline-toggle") {
                            StackedTimelineToggle(
                                hiddenCount = preset.profile.timeline.size - 3,
                                expanded = timelineExpanded,
                                appearance = appearance,
                                onClick = { timelineExpanded = !timelineExpanded },
                            )
                        }
                    }
                }
        }

    if (profileEditorOpen) {
        PresetProfileEditorSheet(
            presetName = preset.name,
            modelTags = preset.modelTags,
            profile = preset.profile,
            appearance = appearance,
            onDismiss = { profileEditorOpen = false },
            onPickAvatar = { avatarPicker.launch("image/*") },
            onSave = { profile, presetName, modelTags ->
                profileEditorOpen = false
                onUpdateProfile(profile)
                onRename(presetName)
                onUpdateModelTags(modelTags)
            },
        )
    }
    if (timelineEditorOpen) {
        PresetTimelineEditorSheet(
            timeline = preset.profile.timeline,
            appearance = appearance,
            onDismiss = { timelineEditorOpen = false },
            onUpdate = { timeline -> onUpdateProfile(preset.profile.copy(timeline = timeline)) },
        )
    }
}
