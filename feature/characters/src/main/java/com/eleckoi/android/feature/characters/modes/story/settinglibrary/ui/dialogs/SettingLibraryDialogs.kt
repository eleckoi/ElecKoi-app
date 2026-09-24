package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.ElecKoiSuccess
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.DialogConfirmButton
import com.eleckoi.android.foundation.design.components.DialogDismissButton
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.ui.shared.ManagedFeatureVersion
import com.eleckoi.android.feature.characters.modes.story.ui.shared.StoryVersionCreateDialog

@Composable
internal fun SettingLibraryRequiredFieldsDialog(
    triggerSelected: Boolean,
    positionSelected: Boolean,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设定检查", color = appearance.mobileText) },
        text = {
            Column {
                RequiredFieldStatusRow(
                    selected = triggerSelected,
                    selectedText = "触发方式已选择",
                    missingText = "触发方式未选择",
                )
                RequiredFieldStatusRow(
                    selected = positionSelected,
                    selectedText = "插入位置已选择",
                    missingText = "插入位置未选择",
                )
            }
        },
        confirmButton = { DialogConfirmButton("知道了", appearance, onClick = onDismiss) },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
internal fun SettingLibraryOrderConflictDialog(
    order: Int,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序数字重复", color = appearance.mobileText) },
        text = {
            Text(
                "当前位置已存在排序数字 $order。条目已关闭，请换一个数字。",
                color = ElecKoiDanger,
            )
        },
        confirmButton = { DialogConfirmButton("知道了", appearance, onClick = onDismiss) },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
internal fun SettingLibraryCreateVersionDialog(
    versions: List<SettingLibraryVersion>,
    activeVersionId: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onConfirm: (name: String, sourceVersionId: String?) -> Unit,
) {
    StoryVersionCreateDialog(
        versions = versions.map { ManagedFeatureVersion(it.id, it.name) },
        activeVersionId = activeVersionId,
        blankDescription = "仅保留空白开场白，不复制其他设定",
        appearance = appearance,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun RequiredFieldStatusRow(
    selected: Boolean,
    selectedText: String,
    missingText: String,
) {
    val statusColor = if (selected) ElecKoiSuccess else ElecKoiDanger
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "✓" else "×",
            modifier = Modifier.size(30.dp),
            color = statusColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (selected) selectedText else missingText,
            color = statusColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SettingLibraryRenameNodeDialog(
    value: String,
    appearance: AppearanceTheme,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val name = value.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名", color = appearance.mobileText) },
        text = {
            AppInsetTextField(
                value = value,
                onValueChange = { onValueChange(it.take(60)) },
                appearance = appearance,
                textStyle = TextStyle(color = appearance.mobileText, fontSize = 16.sp),
            )
        },
        confirmButton = {
            DialogConfirmButton("保存", appearance, enabled = name.isNotBlank(), onClick = onConfirm)
        },
        dismissButton = { DialogDismissButton("取消", appearance, onDismiss) },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
internal fun SettingLibraryGroupNameDialog(
    value: String,
    groups: List<SettingLibraryGroup>,
    appearance: AppearanceTheme,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val name = value.trim()
    val duplicate = name.isNotBlank() && groups.any { it.name.trim() == name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹", color = appearance.mobileText) },
        text = {
            Column {
                AppInsetTextField(
                    value = value,
                    onValueChange = { onValueChange(it.take(40)) },
                    appearance = appearance,
                    textStyle = TextStyle(color = appearance.mobileText, fontSize = 16.sp),
                )
                if (duplicate) {
                    Text("文件夹名已存在", color = appearance.mobileBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            DialogConfirmButton("创建", appearance, enabled = name.isNotBlank() && !duplicate, onClick = onConfirm)
        },
        dismissButton = { DialogDismissButton("取消", appearance, onDismiss) },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
internal fun SettingLibraryEntryGroupPickerDialog(
    groups: List<SettingLibraryGroup>,
    selectedGroupId: String,
    appearance: AppearanceTheme,
    onSelectGroup: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建设定到", color = appearance.mobileText) },
        text = {
            Column {
                CreateDialogRow(
                    title = "全部设定条目",
                    selected = selectedGroupId.isBlank(),
                    appearance = appearance,
                    onClick = { onSelectGroup("") },
                )
                groups.forEach { group ->
                    CreateDialogRow(
                        title = group.name.ifBlank { "未命名分组" },
                        selected = selectedGroupId == group.id,
                        appearance = appearance,
                        onClick = { onSelectGroup(group.id) },
                    )
                }
            }
        },
        confirmButton = { DialogConfirmButton("创建", appearance, onClick = onConfirm) },
        dismissButton = { DialogDismissButton("取消", appearance, onDismiss) },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
private fun CreateDialogRow(
    title: String,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .noRippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "✓" else "",
            modifier = Modifier.size(28.dp),
            color = appearance.mobileBlue,
            fontSize = 18.sp,
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = appearance.mobileText,
            fontSize = 16.sp,
        )
    }
}
