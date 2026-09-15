package com.eleckoi.android.feature.characters.presets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.presets.model.AgentPresetTimelineItem
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetTimelineEditorSheet(
    timeline: List<AgentPresetTimelineItem>,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onUpdate: (List<AgentPresetTimelineItem>) -> Unit,
) {
    var draft by remember(timeline) { mutableStateOf(timeline) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var title by remember { mutableStateOf("") }
    var dateLabel by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    fun edit(index: Int) {
        editingIndex = index
        val item = draft.getOrNull(index)
        title = item?.title.orEmpty()
        dateLabel = item?.dateLabel.orEmpty()
        note = item?.note.orEmpty()
    }
    fun leaveEditor() {
        editingIndex = -1
        title = ""
        dateLabel = ""
        note = ""
    }
    BackHandler(enabled = editingIndex >= 0) { leaveEditor() }

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
                .padding(bottom = 10.dp),
        ) {
            if (editingIndex >= 0) {
                TimelineItemForm(
                    existing = editingIndex < draft.size,
                    index = editingIndex,
                    count = draft.size,
                    title = title,
                    dateLabel = dateLabel,
                    note = note,
                    appearance = appearance,
                    onTitleChange = { title = it.take(80) },
                    onDateChange = { dateLabel = it.take(24) },
                    onNoteChange = { note = it.take(800) },
                    onBack = { leaveEditor() },
                    onMoveUp = {
                        val index = editingIndex
                        if (index > 0) {
                            draft = draft.toMutableList().apply { add(index - 1, removeAt(index)) }
                            editingIndex -= 1
                            onUpdate(draft)
                        }
                    },
                    onMoveDown = {
                        val index = editingIndex
                        if (index in 0 until draft.lastIndex) {
                            draft = draft.toMutableList().apply { add(index + 1, removeAt(index)) }
                            editingIndex += 1
                            onUpdate(draft)
                        }
                    },
                    onDelete = {
                        if (editingIndex in draft.indices) {
                            draft = draft.toMutableList().apply { removeAt(editingIndex) }
                            onUpdate(draft)
                        }
                        leaveEditor()
                    },
                    onSave = {
                        if (title.isBlank()) return@TimelineItemForm
                        val current = draft.getOrNull(editingIndex)
                        val saved = AgentPresetTimelineItem(
                            id = current?.id ?: "timeline-${UUID.randomUUID()}",
                            title = title.trim(),
                            dateLabel = dateLabel.trim(),
                            note = note.trim(),
                        )
                        draft = draft.toMutableList().apply {
                            if (editingIndex in indices) set(editingIndex, saved) else add(0, saved)
                        }
                        onUpdate(draft)
                        leaveEditor()
                    },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("编辑更新时间线", color = appearance.mobileText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("顺序、标题和日期都由作者决定", modifier = Modifier.padding(top = 3.dp), color = appearance.mobileMuted, fontSize = 12.sp)
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp)) {
                    if (draft.isEmpty()) {
                        item {
                            Text(
                                "还没有更新记录",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp),
                                color = appearance.mobileMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    itemsIndexed(draft, key = { _, item -> "edit:${item.id}" }) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { edit(index) }
                                .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(8.dp).background(
                                    if (index == 0) appearance.mobileBlue else appearance.mobileSoft,
                                    CircleShape,
                                ),
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(item.title, color = appearance.mobileText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (item.dateLabel.isNotBlank() || item.note.isNotBlank()) {
                                    Text(
                                        listOf(item.dateLabel, item.note).filter(String::isNotBlank).joinToString(" · "),
                                        modifier = Modifier.padding(top = 3.dp),
                                        color = appearance.mobileMuted,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                StrokeSvgIcon(
                                    AppIconPaths.EditSquare,
                                    appearance.mobileMuted,
                                    iconSize = 18.dp,
                                    strokeWidth = 1.65f,
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 40.dp, end = 20.dp), thickness = 0.5.dp, color = appearance.mobileLine)
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .noRippleClickable { edit(draft.size) }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StrokeSvgIcon(AppIconPaths.Plus, appearance.mobileBlue, iconSize = 18.dp)
                            Text("新增记录", modifier = Modifier.padding(start = 12.dp), color = appearance.mobileBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItemForm(
    existing: Boolean,
    index: Int,
    count: Int,
    title: String,
    dateLabel: String,
    note: String,
    appearance: AppearanceTheme,
    onTitleChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onBack: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).noRippleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            StrokeSvgIcon(AppIconPaths.ChevronLeft, appearance.mobileText, iconSize = 21.dp)
        }
        Text(
            if (existing) "编辑记录" else "新增记录",
            modifier = Modifier.weight(1f),
            color = appearance.mobileText,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "保存",
            modifier = Modifier.noRippleClickable(enabled = title.isNotBlank(), onClick = onSave).padding(12.dp),
            color = if (title.isNotBlank()) appearance.mobileBlue else appearance.mobileSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SheetFieldLabel("标题", appearance)
        AppInsetTextField(title, onTitleChange, appearance, placeholder = "例如：优化长剧情节奏")
        SheetFieldLabel("日期标注", appearance, Modifier.padding(top = 13.dp))
        AppInsetTextField(dateLabel, onDateChange, appearance, placeholder = "例如：08.26（可留空）")
        SheetFieldLabel("更新说明", appearance, Modifier.padding(top = 13.dp))
        AppInsetTextField(
            value = note,
            onValueChange = onNoteChange,
            appearance = appearance,
            placeholder = "这次改了什么",
            singleLine = false,
            textFieldModifier = Modifier.heightIn(min = 82.dp),
            textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        )
        if (existing) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                TextButton(enabled = index > 0, onClick = onMoveUp) {
                    Text("上移", color = if (index > 0) appearance.mobileText else appearance.mobileSoft)
                }
                TextButton(enabled = index < count - 1, onClick = onMoveDown) {
                    Text("下移", color = if (index < count - 1) appearance.mobileText else appearance.mobileSoft)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("删除", color = ElecKoiDanger) }
            }
        }
    }
}
