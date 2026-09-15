package com.eleckoi.android.feature.settings.ui.personalization.profile

import com.eleckoi.android.feature.settings.ui.personalization.components.*

import com.eleckoi.android.foundation.design.components.*
import com.eleckoi.android.feature.characters.ui.components.AvatarSlotsPage

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.UserProfile
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

// 名字之外的东西（头像、背景）都是选好图就立刻落盘，所以这里只需要托住正在输入的名字。
private class ProfileEditState(user: UserProfile) {
    var name by mutableStateOf(initialName(user))
    var dirty by mutableStateOf(false)

    fun syncFrom(user: UserProfile) {
        if (dirty) return
        name = initialName(user)
    }

    fun updateName(value: String) {
        if (value.length <= 32) {
            name = value
            dirty = true
        }
    }

    fun markSaved() {
        dirty = false
    }

    private fun initialName(user: UserProfile): String {
        return user.userName
    }
}

@Composable
private fun rememberProfileEditState(user: UserProfile): ProfileEditState {
    val state = remember { ProfileEditState(user) }
    LaunchedEffect(user.userName) {
        state.syncFrom(user)
    }
    return state
}

@Composable
fun ProfileEditPage(
    user: UserProfile,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveAvatars: (Map<AvatarSlot, File>) -> Unit,
    onSaveCover: (Uri) -> Unit,
    onClearCover: () -> Unit,
) {
    val context = LocalContext.current
    val editState = rememberProfileEditState(user)
    var avatarPageOpen by remember { mutableStateOf(false) }
    var coverCropSource by remember { mutableStateOf<Uri?>(null) }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        coverCropSource = uri
    }

    with(editState) {
    LaunchedEffect(name, dirty) {
        if (!dirty) return@LaunchedEffect
        delay(1000)
        onSaveName(name)
        markSaved()
    }

    if (avatarPageOpen) {
        AvatarSlotsPage(
            avatars = user.avatars,
            displayName = name,
            cachePrefix = "user",
            appearance = appearance,
            onBack = { avatarPageOpen = false },
            onSave = onSaveAvatars,
        )
        return
    }

    coverCropSource?.let { source ->
        ImageCropPage(
            sourceUri = source,
            title = "裁剪资料背景",
            cropAspect = ProfileCoverAspectRatio,
            circularFrame = false,
            outputWidth = 1280,
            appearance = appearance,
            onBack = { coverCropSource = null },
            onCropped = { bitmap, _ ->
                val file = saveBitmapToCache(context.cacheDir, bitmap, "profile-cover-crop", Bitmap.CompressFormat.JPEG, 92)
                onSaveCover(Uri.fromFile(file))
                coverCropSource = null
            },
        )
        return
    }

    val requestBack = {
        if (dirty) onSaveName(name)
        onBack()
    }
    BackHandler(onBack = requestBack)

    PinnedStatusScaffold(appearance = appearance, backgroundColor = appearance.mobileBg) {
        // Large title sits straight on the page background — no separate bar colour, so the
        // header and the content read as one continuous surface.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietBackButton(
                color = appearance.mobileText.copy(alpha = 0.84f),
                onClick = requestBack,
                modifier = Modifier.size(48.dp),
            )
            Text(
                "编辑资料",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                color = appearance.mobileText,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .noRippleClickable { avatarPageOpen = true }
                    .padding(top = 10.dp, bottom = 22.dp),
            ) {
                AvatarCircle(
                    name = name,
                    size = 82,
                    fontSize = 30,
                    appearance = appearance,
                    avatarPath = user.userAvatar,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .background(appearance.mobileBg, RoundedCornerShape(999.dp))
                        .padding(1.5.dp)
                        .background(appearance.mobileBlue, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeSvgIcon(AppIconPaths.PictureEdit, appearance.mobileSurface, iconSize = 16.dp)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusDismissInputRegion()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appearance.mobileSurface)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 13.dp),
            ) {
                Text("名字", color = appearance.mobileMuted, fontSize = 12.sp)
                AppInsetTextField(
                    value = name,
                    onValueChange = ::updateName,
                    appearance = appearance,
                    modifier = Modifier.padding(top = 8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = appearance.mobileText,
                        fontSize = 17.sp,
                    ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 60.dp),
            ) {
                Text(
                    "资料背景",
                    color = appearance.mobileMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ProfileCoverAspectRatio)
                        .clickable { coverLauncher.launch("image/*") },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = appearance.mobilePinnedBg),
                ) {
                    val hasCustomCover = user.userCover.isNotBlank()
                    val coverFile = remember(user.userCover) {
                        user.userCover.takeIf(String::isNotBlank)
                            ?.let(::File)
                            ?.takeIf(File::isFile)
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (coverFile != null) {
                            AsyncImage(
                                model = coverFile,
                                contentDescription = "资料背景预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hasCustomCover) {
                                ProfileCoverAction(
                                    label = "恢复默认",
                                    icon = AppIconPaths.Refresh,
                                    appearance = appearance,
                                    onClick = onClearCover,
                                )
                            }
                            ProfileCoverAction(
                                label = if (coverFile == null) "选择背景" else "更换背景",
                                icon = AppIconPaths.PictureFrame,
                                appearance = appearance,
                                onClick = { coverLauncher.launch("image/*") },
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ProfileCoverAction(
    label: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(999.dp),
        color = appearance.mobileSurface.copy(alpha = 0.90f),
        contentColor = appearance.mobileText,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrokeSvgIcon(icon, appearance.mobileText, iconSize = 16.dp)
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
