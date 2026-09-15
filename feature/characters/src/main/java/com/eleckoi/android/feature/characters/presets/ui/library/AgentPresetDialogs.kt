package com.eleckoi.android.feature.characters.presets.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelFamily
import com.eleckoi.android.feature.characters.presets.model.AgentPresetModelTag
import com.eleckoi.android.feature.characters.presets.model.toTag
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppInsetTextField
import com.eleckoi.android.foundation.design.components.DialogConfirmButton

@Composable
internal fun PresetNameDialog(
    title: String,
    placeholder: String,
    initialValue: String = "",
    confirmLabel: String = "创建",
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appearance.mobileSurface,
        title = { Text(title, color = appearance.mobileText, fontWeight = FontWeight.SemiBold) },
        text = {
            AppInsetTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = placeholder,
                appearance = appearance,
                singleLine = true,
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = appearance.mobileMuted) } },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(confirmLabel, color = if (name.isNotBlank()) appearance.mobileBlue else appearance.mobileSoft)
            }
        },
    )
}

@Composable
internal fun CreatePresetDialog(
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onConfirm: (String, List<AgentPresetModelTag>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedFamilies by remember { mutableStateOf(setOf(AgentPresetModelFamily.General)) }
    var customTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建预设", color = appearance.mobileText) },
        text = {
            Column {
                AppInsetTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    appearance = appearance,
                    placeholder = "预设名称",
                )
                Text("模型标签", color = appearance.mobileText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    AgentPresetModelFamily.entries.filterNot { it == AgentPresetModelFamily.Other }.forEach { option ->
                        PresetChip(option, option in selectedFamilies, appearance) {
                            selectedFamilies = if (option in selectedFamilies) {
                                (selectedFamilies - option).ifEmpty { setOf(AgentPresetModelFamily.General) }
                            } else {
                                if (option == AgentPresetModelFamily.General) setOf(option)
                                else (selectedFamilies - AgentPresetModelFamily.General) + option
                            }
                        }
                    }
                }
                AppInsetTextField(
                    value = customTag,
                    onValueChange = { customTag = it.take(20) },
                    appearance = appearance,
                    placeholder = "自定义模型标签（可选）",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            DialogConfirmButton("创建", appearance, enabled = name.trim().isNotBlank()) {
                val custom = customTag.trim().takeIf(String::isNotBlank)?.let { label ->
                    AgentPresetModelTag(
                        id = "custom-${label.lowercase().replace(Regex("[^a-z0-9\\p{L}]+"), "-").trim('-')}",
                        label = label,
                    )
                }
                onConfirm(name, selectedFamilies.map(AgentPresetModelFamily::toTag) + listOfNotNull(custom))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = appearance.mobileMuted) } },
        containerColor = appearance.mobileSurface,
    )
}
