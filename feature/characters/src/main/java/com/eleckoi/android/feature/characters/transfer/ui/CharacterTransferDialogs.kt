package com.eleckoi.android.feature.characters.transfer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportPreview
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportPreviewItem
import com.eleckoi.android.feature.characters.transfer.model.CharacterExportFormat
import com.eleckoi.android.feature.characters.transfer.model.ExportedCharacterCard
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
fun CharacterImportSourceDialog(
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onImportElecKoi: () -> Unit,
    onImportSillyTavern: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导入来源", color = appearance.mobileText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CharacterTransferOptionRow(
                    title = "本项目角色卡",
                    description = "导入 ElecKoi 导出的角色卡",
                    appearance = appearance,
                    onClick = onImportElecKoi,
                )
                CharacterTransferOptionRow(
                    title = "酒馆角色卡",
                    description = "导入 SillyTavern 角色卡并转换",
                    appearance = appearance,
                    onClick = onImportSillyTavern,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
private fun CharacterTransferOptionRow(
    title: String,
    description: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StrokeSvgIcon(
            paths = AppIconPaths.Import,
            color = appearance.mobileText,
            iconSize = 23.dp,
            strokeWidth = 1.8f,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = appearance.mobileText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = appearance.mobileMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CharacterExportFormatDialog(
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onSelect: (CharacterExportFormat) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导出格式", color = appearance.mobileText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CharacterTransferOptionRow(
                    title = "PNG 角色卡",
                    description = "可预览图片，内含完整 ElecKoi 数据",
                    appearance = appearance,
                    onClick = { onSelect(CharacterExportFormat.Png) },
                )
                CharacterTransferOptionRow(
                    title = "JSON 角色卡",
                    description = "完整原始数据，适合跨端传输与检查",
                    appearance = appearance,
                    onClick = { onSelect(CharacterExportFormat.Json) },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
fun CharacterImportDialog(
    preview: CharacterImportPreview,
    busy: Boolean,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(if (preview.count > 1) "批量导入角色" else "导入角色", color = appearance.mobileText)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when {
                        preview.failedCount == 0 -> "${preview.importableCount} 张角色卡可以导入"
                        preview.importableCount == 0 -> "所选角色卡均无法导入"
                        else -> "${preview.importableCount} 张可以导入，${preview.failedCount} 张已阻挡"
                    },
                    color = appearance.mobileMuted,
                    fontSize = 13.sp,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(preview.items, key = CharacterImportPreviewItem::id) { item ->
                        CharacterImportPreviewRow(item, appearance)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            TextButton(enabled = !busy && preview.importableCount > 0, onClick = onConfirm) {
                Text(
                    when {
                        busy -> "导入中"
                        preview.count > 1 -> "导入 ${preview.importableCount} 个角色"
                        else -> "导入角色"
                    },
                )
            }
        },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
private fun CharacterImportPreviewRow(
    item: CharacterImportPreviewItem,
    appearance: AppearanceTheme,
) {
    val statusColor = if (item.importable) appearance.mobileBlue else ImportErrorColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appearance.mobileBg)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.imageFile,
            contentDescription = "${item.name} 角色卡预览",
            modifier = Modifier
                .size(width = 54.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(appearance.mobileSearchBg),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = appearance.mobileText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (item.importable) item.summary else item.errorMessage,
                color = if (item.importable) appearance.mobileMuted else ImportErrorColor,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            StrokeSvgIcon(
                paths = if (item.importable) AppIconPaths.Check else AppIconPaths.X,
                color = statusColor,
                iconSize = 15.dp,
                strokeWidth = 2.4f,
            )
        }
    }
}

@Composable
fun CharacterExportDialog(
    card: ExportedCharacterCard,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onShareOriginal: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出角色", color = appearance.mobileText) },
        text = {
            Column {
                CharacterCardSummary(
                    name = card.name,
                    summary = if (card.format == CharacterExportFormat.Png) "PNG 角色卡" else "JSON 角色卡",
                    imageModel = card.file.takeIf { card.format == CharacterExportFormat.Png },
                    appearance = appearance,
                )
                Spacer(Modifier.height(14.dp))
                TransferActionRow(
                    label = "分享原文件",
                    icon = AppIconPaths.Export,
                    appearance = appearance,
                    onClick = onShareOriginal,
                )
                TransferActionRow(
                    label = if (card.format == CharacterExportFormat.Png) "保存图片" else "保存 JSON",
                    icon = AppIconPaths.Import,
                    appearance = appearance,
                    onClick = onSave,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
fun CharacterBatchExportDialog(
    cards: List<ExportedCharacterCard>,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onShareOriginal: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出角色卡", color = appearance.mobileText) },
        text = {
            Column {
                Text("${cards.size} 个角色卡", color = appearance.mobileMuted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(cards, key = ExportedCharacterCard::characterId) { card ->
                        Column(modifier = Modifier.width(82.dp)) {
                            if (card.format == CharacterExportFormat.Png) {
                                AsyncImage(
                                    model = card.file,
                                    contentDescription = "${card.name} 角色卡预览",
                                    modifier = Modifier
                                        .size(width = 82.dp, height = 110.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(appearance.mobileBg),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                StrokeSvgIcon(
                                    paths = AppIconPaths.Export,
                                    color = appearance.mobileMuted,
                                    iconSize = 32.dp,
                                    strokeWidth = 1.5f,
                                    modifier = Modifier
                                        .size(width = 82.dp, height = 110.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(appearance.mobileBg)
                                        .padding(25.dp),
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                card.name,
                                color = appearance.mobileText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TransferActionRow(
                    label = "保存到本地",
                    icon = AppIconPaths.Import,
                    appearance = appearance,
                    onClick = onSave,
                )
                TransferActionRow(
                    label = "分享原文件",
                    icon = AppIconPaths.Export,
                    appearance = appearance,
                    onClick = onShareOriginal,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = appearance.mobileSurface,
    )
}

@Composable
private fun CharacterCardSummary(
    name: String,
    summary: String,
    imageModel: Any?,
    appearance: AppearanceTheme,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = "$name 角色卡预览",
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.mobileBg),
                contentScale = ContentScale.Crop,
            )
        } else {
            StrokeSvgIcon(
                paths = AppIconPaths.Export,
                color = appearance.mobileMuted,
                iconSize = 30.dp,
                strokeWidth = 1.5f,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.mobileBg)
                    .padding(21.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = appearance.mobileText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(summary, color = appearance.mobileMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TransferActionRow(
    label: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StrokeSvgIcon(
            paths = icon,
            color = appearance.mobileText,
            iconSize = 22.dp,
            strokeWidth = 1.8f,
        )
        Text(label, color = appearance.mobileText, fontSize = 16.sp)
    }
}

private val ImportErrorColor = Color(0xFFE5484D)
