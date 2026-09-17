package com.eleckoi.android.feature.modelconfig.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppSearchField
import com.eleckoi.android.foundation.design.components.ConfirmDialog
import com.eleckoi.android.foundation.design.components.MobileBottomSheetDialog
import com.eleckoi.android.foundation.design.components.MobileBottomSheetHeader
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.selectionPalette

@Composable
internal fun ModelPickerSheet(
    items: List<ModelOption>,
    activeModel: String,
    appearance: AppearanceTheme,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deletingModel by rememberSaveable { mutableStateOf<String?>(null) }
    val filteredItems = remember(items, keyword) { filterModelPickerItems(items, keyword) }

    MobileBottomSheetDialog(
        appearance = appearance,
        onDismiss = onClose,
        sheetModifier = Modifier.statusBarsPadding(),
        showHandle = false,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .imePadding(),
        ) {
            MobileBottomSheetHeader(
                title = "模型列表",
                appearance = appearance,
                onDismiss = onClose,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSearchField(
                    keyword = keyword,
                    placeholder = "搜索模型",
                    appearance = appearance,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    onKeywordChange = { keyword = it },
                )
                TextButton(
                    onClick = { adding = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = appearance.mobileBlue),
                ) {
                    StrokeSvgIcon(AppIconPaths.Plus, appearance.mobileBlue, iconSize = 16.dp)
                    Text("添加模型", modifier = Modifier.padding(start = 6.dp))
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (filteredItems.isEmpty()) {
                    item("empty") {
                        Text(
                            if (items.isEmpty()) "还没有模型，点击「添加模型」填写。" else "没有匹配的模型",
                            color = appearance.mobileMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 12.dp),
                        )
                    }
                }
                items(filteredItems, key = { "model:${it.id}" }) { item ->
                    ModelPickerRow(
                        item = item,
                        selected = item.id == activeModel,
                        appearance = appearance,
                        onSelect = { onSelect(item.id) },
                        onDelete = { deletingModel = item.id },
                    )
                }
            }
        }
        if (adding) {
            AddModelDialog(
                items = items,
                appearance = appearance,
                onDismiss = { adding = false },
                onAdd = { model ->
                    onAdd(model)
                    keyword = ""
                    adding = false
                },
            )
        }
        deletingModel?.let { model ->
            val target = items.firstOrNull { it.id == model && it.isUserAdded }
            if (target != null) {
                val next = items.firstOrNull { it.id != model }?.id
                val selectionMessage = when {
                    model != activeModel -> ""
                    next != null -> "删除后将选中「$next」。"
                    else -> "删除后需重新添加或读取模型。"
                }
                ConfirmDialog(
                    title = "删除手动模型？",
                    message = "将从当前配置的列表中删除「$model」。$selectionMessage",
                    appearance = appearance,
                    confirmText = "删除",
                    destructive = true,
                    onDismiss = { deletingModel = null },
                    onConfirm = {
                        onDelete(model)
                        deletingModel = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelPickerRow(
    item: ModelOption,
    selected: Boolean,
    appearance: AppearanceTheme,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val selection = appearance.selectionPalette()
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) selection.activeContainer else selection.inactiveContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f)
                .selectable(selected, role = Role.RadioButton, onClick = onSelect)
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.id,
                    color = if (selected) selection.activeText else selection.text,
                    fontSize = 15.sp,
                )
                if (item.isUserAdded) {
                    Text(
                        "手动添加",
                        color = appearance.mobileMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (selected) StrokeSvgIcon(AppIconPaths.Check, appearance.mobileBlue, iconSize = 18.dp)
        }
        if (item.isUserAdded) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = "删除手动模型 ${item.id}" },
            ) {
                StrokeSvgIcon(AppIconPaths.Trash, appearance.mobileMuted, iconSize = 18.dp)
            }
        }
    }
}
