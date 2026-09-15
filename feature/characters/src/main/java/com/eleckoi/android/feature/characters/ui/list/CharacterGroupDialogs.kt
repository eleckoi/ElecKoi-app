package com.eleckoi.android.feature.characters.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.noRippleClickable

@Composable
internal fun CharacterGroupPickerDialog(
    groups: List<String>,
    selectedGroup: String,
    appearance: AppearanceTheme,
    onSelectGroup: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建角色到", color = appearance.mobileText) },
        text = {
            Column {
                CharacterCreateDialogRow(
                    title = ALL_CHARACTERS,
                    selected = selectedGroup.isBlank(),
                    appearance = appearance,
                    onClick = { onSelectGroup(DEFAULT_GROUP) },
                )
                groups.forEach { group ->
                    CharacterCreateDialogRow(
                        title = group,
                        selected = selectedGroup == group,
                        appearance = appearance,
                        onClick = { onSelectGroup(group) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
private fun CharacterCreateDialogRow(
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
