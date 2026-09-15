package com.eleckoi.android.feature.characters.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.characters.model.AvatarSet
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.*
import java.io.File

private const val SlotWidth = 88
private const val PortraitSlotHeight = 117

private val AvatarSlot.shortLabel: String
    get() = when (this) {
        AvatarSlot.Circle -> "圆形"
        AvatarSlot.Square -> "方形"
        AvatarSlot.Portrait -> "立绘"
    }

private val AvatarSlot.usageLabel: String
    get() = when (this) {
        AvatarSlot.Circle -> "列表和头部"
        AvatarSlot.Square -> "聊天气泡"
        AvatarSlot.Portrait -> "半身 / 大头"
    }

/**
 * 三张头像的配置页，角色和用户共用。三个槽位完全平级：每一行都能点进去换图、调取景，谁也不是
 * "主头像"。全新资料第一次设置时另外两个槽位会按同一张原图居中裁好；之后允许单独留空。
 */
@Composable
fun AvatarSlotsPage(
    avatars: AvatarSet,
    displayName: String,
    // 落盘的文件名前缀，角色和用户各存各的目录，不会撞。
    cachePrefix: String,
    appearance: AppearanceTheme,
    initialSlot: AvatarSlot? = null,
    onBack: () -> Unit,
    onSave: (Map<AvatarSlot, File>) -> Unit,
    onClear: ((AvatarSlot) -> Unit)? = null,
) {
    val context = LocalContext.current
    val initialSource = remember(avatars, initialSlot) {
        initialSlot?.let { slot ->
            slot.pathIn(avatars)
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.exists() }
                ?.let(Uri::fromFile)
        }
    }
    var editing by remember(initialSlot) { mutableStateOf(initialSlot) }
    var cropSource by remember(initialSlot, initialSource) { mutableStateOf(initialSource) }
    var requestInitialPick by remember(initialSlot, initialSource) {
        mutableStateOf(initialSlot != null && initialSource == null)
    }
    var deleting by remember { mutableStateOf<AvatarSlot?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            if (cropSource == null) {
                if (initialSlot != null) onBack() else editing = null
            }
        } else {
            cropSource = uri
        }
    }

    LaunchedEffect(requestInitialPick) {
        if (requestInitialPick) {
            requestInitialPick = false
            picker.launch("image/*")
        }
    }

    fun open(slot: AvatarSlot) {
        editing = slot
        val existing = slot.pathIn(avatars)
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
        val source = existing?.let(Uri::fromFile)
        if (source == null) picker.launch("image/*") else cropSource = source
    }

    fun closeEditor() {
        if (initialSlot != null) {
            onBack()
        } else {
            editing = null
            cropSource = null
        }
    }

    val slot = editing
    val source = cropSource
    if (slot != null && source != null) {
        BackHandler(onBack = ::closeEditor)
        Box {
            ImageCropPage(
                sourceUri = source,
                title = slot.label,
                cropAspect = slot.aspect,
                circularFrame = slot.circularFrame,
                outputWidth = slot.outputWidth,
                ratioLabel = slot.ratioLabel,
                appearance = appearance,
                onBack = ::closeEditor,
                onPickAnother = { picker.launch("image/*") },
                onDelete = onClear
                    ?.takeIf { slot.pathIn(avatars).isNotBlank() }
                    ?.let { { deleting = slot } },
                onCropped = { cropped, sourceBitmap ->
                    onSave(fillSlots(context.cacheDir, cachePrefix, avatars, slot, cropped, sourceBitmap))
                    closeEditor()
                },
            )
            deleting?.let { target ->
                AvatarSlotDeleteDialog(
                    slot = target,
                    appearance = appearance,
                    onDismiss = { deleting = null },
                    onConfirm = {
                        deleting = null
                        onClear?.invoke(target)
                        closeEditor()
                    },
                )
            }
        }
        return
    }

    BackHandler(onBack = onBack)

    PinnedStatusScaffold(appearance = appearance, imeAware = false, backgroundColor = appearance.mobileBg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp).noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) {
                StrokeSvgIcon(AppIconPaths.Back, appearance.mobileText, iconSize = 29.dp)
            }
            Text("头像", color = appearance.mobileText, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarSlotCell(
                    slot = AvatarSlot.Circle,
                    avatars = avatars,
                    displayName = displayName,
                    appearance = appearance,
                    onClick = { open(AvatarSlot.Circle) },
                )
                AvatarSlotCell(
                    slot = AvatarSlot.Square,
                    avatars = avatars,
                    displayName = displayName,
                    appearance = appearance,
                    onClick = { open(AvatarSlot.Square) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarSlotCell(
                    slot = AvatarSlot.Portrait,
                    avatars = avatars,
                    displayName = displayName,
                    appearance = appearance,
                    onClick = { open(AvatarSlot.Portrait) },
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(32.dp))
        }
    }

    deleting?.let { target ->
        AvatarSlotDeleteDialog(
            slot = target,
            appearance = appearance,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                onClear?.invoke(target)
            },
        )
    }
}

@Composable
private fun RowScope.AvatarSlotCell(
    slot: AvatarSlot,
    avatars: AvatarSet,
    displayName: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val slotWidth = SlotWidth.dp
    val shape = when (slot) {
        AvatarSlot.Circle -> CircleShape
        AvatarSlot.Square -> RoundedCornerShape(18.dp)
        AvatarSlot.Portrait -> RoundedCornerShape(14.dp)
    }
    val slotHeight = if (slot == AvatarSlot.Portrait) PortraitSlotHeight else SlotWidth
    val path = slot.pathIn(avatars)
    Column(
        modifier = Modifier
            .weight(1f)
            .semantics { contentDescription = "编辑${slot.shortLabel}头像" }
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarCircle(
            name = displayName,
            size = SlotWidth,
            fontSize = 28,
            appearance = appearance,
            avatarPath = path,
            modifier = Modifier.border(1.dp, appearance.mobileLine, shape),
            shape = shape,
            height = slotHeight,
        )
        Column(
            modifier = Modifier.padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                slot.shortLabel,
                color = appearance.mobileText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                slot.usageLabel,
                color = appearance.mobileMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AvatarSlotDeleteDialog(
    slot: AvatarSlot,
    appearance: AppearanceTheme,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        title = "删除${slot.label}？",
        message = "只删除这个${slot.label}图片，另外两个图片槽位不会改变。",
        appearance = appearance,
        confirmText = "删除",
        destructive = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 用户裁好的那一张原样落盘；只有三张都还没配置的第一次上传，才按同一张未裁剪原图补齐另外
 * 两个槽位。主动删除的空槽位不会在以后替换别的图片时被悄悄恢复。
 */
private fun fillSlots(
    cacheDir: File,
    cachePrefix: String,
    avatars: AvatarSet,
    edited: AvatarSlot,
    cropped: Bitmap,
    sourceBitmap: Bitmap,
): Map<AvatarSlot, File> {
    val files = linkedMapOf(edited to cacheDir.stage(cachePrefix, edited, cropped))
    AvatarSlot.emptySlotsBesides(avatars, edited).forEach { empty ->
        val bitmap = centerCropBitmap(sourceBitmap, empty.aspect, empty.outputWidth)
        files[empty] = cacheDir.stage(cachePrefix, empty, bitmap)
        if (bitmap !== sourceBitmap) bitmap.recycle()
    }
    return files
}

private fun File.stage(cachePrefix: String, slot: AvatarSlot, bitmap: Bitmap): File {
    // 圆形头像切出来的四角是透明的，只有 PNG 留得住；另外两张是实心矩形，JPEG 更省。
    val png = slot == AvatarSlot.Circle
    return saveBitmapToCache(
        this,
        bitmap,
        "$cachePrefix-${slot.fileNamePrefix}",
        if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
        if (png) 100 else 94,
    )
}
