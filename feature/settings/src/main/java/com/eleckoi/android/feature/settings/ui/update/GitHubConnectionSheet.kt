package com.eleckoi.android.feature.settings.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubConnectionSheet(
    appearance: AppearanceTheme,
    state: GitHubConnectionUiState,
    onSave: (GitHubConnectionSettings) -> Unit,
    onTest: () -> Unit,
    onTestDownloads: () -> Unit,
    onCancelTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editingCustom by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(state.settings.customPrefix) {
        mutableStateOf(state.settings.customPrefix)
    }
    val customVisible = editingCustom || state.settings.source == GitHubConnectionSource.Custom
    val normalized = normalizeMirrorPrefix(draft)
    val invalid = draft.isNotBlank() && normalized == null
    val unsaved = customVisible && (normalized != state.settings.customPrefix || editingCustom)
    DisposableEffect(Unit) { onTest(); onDispose { onCancelTest() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = appearance.mobileSurface,
        contentColor = appearance.mobileText,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GitHub 连接", modifier = Modifier.weight(1f), fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            Text("用于版本检查与安装包下载", color = appearance.mobileMuted, fontSize = 12.sp)
            Column(Modifier.selectableGroup()) {
                GitHubConnectionSource.entries.forEach { source ->
                    val selected = if (editingCustom) source == GitHubConnectionSource.Custom
                    else state.settings.source == source
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = selected, enabled = !state.saving, role = Role.RadioButton,
                            onClick = {
                                if (source == GitHubConnectionSource.Custom) {
                                    editingCustom = state.settings.source != source
                                } else {
                                    editingCustom = false
                                    onSave(state.settings.copy(source = source))
                                }
                            },
                        ).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null, enabled = !state.saving)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(source.title + if (source == GitHubConnectionSource.Official) "（默认）" else "",
                                fontSize = 14.sp)
                            Text(
                                when (source) {
                                    GitHubConnectionSource.Official -> "github.com"
                                    GitHubConnectionSource.Custom -> state.settings.customPrefix.ifBlank { "设置镜像地址" }
                                    else -> source.prefix.removePrefix("https://").trimEnd('/')
                                }, color = appearance.mobileMuted, fontSize = 12.sp,
                            )
                            state.results[source]?.let { result ->
                                Text(result, color = appearance.mobileMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (customVisible) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(), enabled = !state.saving && !state.testing,
                    label = { Text("镜像地址前缀") },
                    placeholder = { Text("https://mirror.example/") },
                    singleLine = true, isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = {
                        Text(if (invalid) "请填写有效的 HTTPS 地址，使用纯地址前缀"
                        else "自动在此前缀后拼接 GitHub 原始链接")
                    },
                )
                if (normalized != null) {
                    Text("预览：${normalized}https://github.com/eleckoi/ElecKoi/releases/…",
                        color = appearance.mobileMuted, fontSize = 11.sp)
                }
                Button(
                    enabled = normalized != null && !state.saving && !state.testing && unsaved,
                    onClick = {
                        onSave(GitHubConnectionSettings(GitHubConnectionSource.Custom, normalized.orEmpty()))
                        editingCustom = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.saving) "正在保存…" else "保存并使用") }
            }
            TextButton(
                onClick = if (state.downloadTesting) onCancelTest else onTestDownloads,
                enabled = state.downloadTesting || (!state.saving && !state.testing && !unsaved),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.downloadTesting) "取消测速" else "下载测速") }
            Text("每条线路最多读取 1 MiB，下载速度为估算值",
                color = appearance.mobileMuted, fontSize = 12.sp)
            Text("当前使用：${state.settings.source.title}", color = appearance.mobileMuted, fontSize = 12.sp)
            if (state.error.isNotBlank()) {
                Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}
