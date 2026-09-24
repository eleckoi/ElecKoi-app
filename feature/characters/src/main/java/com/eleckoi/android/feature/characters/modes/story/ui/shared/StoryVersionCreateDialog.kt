package com.eleckoi.android.feature.characters.modes.story.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.foundation.design.components.DialogConfirmButton
import com.eleckoi.android.foundation.design.components.DialogDismissButton
import com.eleckoi.android.foundation.design.components.SquareSelectionCheck

@Composable
internal fun StoryVersionCreateDialog(
    versions: List<ManagedFeatureVersion>,
    activeVersionId: String,
    blankDescription: String,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onConfirm: (name: String, sourceVersionId: String?) -> Unit,
) {
    val orderedVersions = remember(versions, activeVersionId) {
        versions.sortedBy { if (it.id == activeVersionId) 0 else 1 }
    }
    val initialSourceId = activeVersionId.takeIf { id -> versions.any { it.id == id } }
        ?: versions.firstOrNull()?.id.orEmpty()
    var selectedSourceId by remember(activeVersionId) { mutableStateOf(initialSourceId) }
    var name by remember(activeVersionId) {
        mutableStateOf(suggestedVersionName(versions.firstOrNull { it.id == initialSourceId }, versions))
    }
    var nameEdited by remember(activeVersionId) { mutableStateOf(false) }
    var validationMessage by remember(activeVersionId) { mutableStateOf("") }

    fun selectSource(sourceId: String) {
        selectedSourceId = sourceId
        if (!nameEdited) {
            name = suggestedVersionName(versions.firstOrNull { it.id == sourceId }, versions)
        }
        validationMessage = ""
    }

    fun submit() {
        val normalizedName = name.trim()
        validationMessage = when {
            normalizedName.isBlank() -> "请输入版本名称"
            versions.any { it.name.trim() == normalizedName } -> "版本名称已存在，请换一个名称"
            else -> ""
        }
        if (validationMessage.isEmpty()) onConfirm(normalizedName, selectedSourceId.ifBlank { null })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建版本", color = appearance.mobileText) },
        text = {
            Column {
                Text(
                    "版本名称",
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = appearance.mobileText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                BasicTextField(
                    value = name,
                    onValueChange = { value ->
                        name = value.take(60)
                        nameEdited = true
                        validationMessage = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(Color.Transparent, RoundedCornerShape(10.dp))
                        .border(
                            width = 1.dp,
                            color = if (validationMessage.isEmpty()) appearance.mobileLine else ElecKoiDanger,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    textStyle = TextStyle(color = appearance.mobileText, fontSize = 16.sp),
                    cursorBrush = SolidColor(appearance.mobileBlue),
                    singleLine = true,
                )
                if (validationMessage.isNotEmpty()) {
                    Text(
                        validationMessage,
                        modifier = Modifier.padding(top = 6.dp),
                        color = ElecKoiDanger,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "创建方式",
                    modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                    color = appearance.mobileText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    orderedVersions.forEach { version ->
                        val current = version.id == activeVersionId
                        VersionSourceRow(
                            title = if (current) "复制当前版本" else version.name.trim().ifBlank { "未命名版本" },
                            description = if (current) version.name.trim().ifBlank { "未命名版本" }
                                else "从这个历史版本复制",
                            selected = selectedSourceId == version.id,
                            appearance = appearance,
                            onClick = { selectSource(version.id) },
                        )
                    }
                    VersionSourceRow(
                        title = "创建空白版本",
                        description = blankDescription,
                        selected = selectedSourceId.isBlank(),
                        appearance = appearance,
                        onClick = { selectSource("") },
                    )
                }
            }
        },
        confirmButton = { DialogConfirmButton("创建版本", appearance, onClick = ::submit) },
        dismissButton = { DialogDismissButton("取消", appearance, onDismiss) },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
private fun VersionSourceRow(
    title: String,
    description: String,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareSelectionCheck(
            selected = selected,
            appearance = appearance,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = appearance.mobileText,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                description,
                modifier = Modifier.padding(top = 2.dp),
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun suggestedVersionName(source: ManagedFeatureVersion?, versions: List<ManagedFeatureVersion>): String {
    val base = if (source == null) "新版本" else "${source.name.trim().ifBlank { "未命名版本" }} · 副本"
    val existing = versions.map { it.name.trim() }.toSet()
    if (base !in existing) return base
    var index = 2
    while ("$base $index" in existing) index += 1
    return "$base $index"
}
