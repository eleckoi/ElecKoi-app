package com.eleckoi.android.feature.characters.ui.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.mobileRootContentColor
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.foundation.design.components.AvatarCircle
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.components.themedListRowClickable
import com.eleckoi.android.foundation.design.fieldPalette
import com.eleckoi.android.foundation.design.selectionPalette
import com.eleckoi.android.foundation.design.ElecKoiDanger
import coil3.compose.AsyncImage
import java.io.File
import com.eleckoi.android.feature.characters.ui.list.*

@Composable
internal fun CharacterWaterfallCard(
    user: UserProfile,
    character: CharacterSlot,
    artworkAspectRatio: Float,
    selected: Boolean,
    selectable: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val selection = appearance.selectionPalette()
    val authorName = user.userName.ifBlank { "用户" }
    val cardInfoBg = characterManagerCardInfoBg(appearance)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(cardInfoBg)
            .themedListRowClickable(appearance = appearance, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(artworkAspectRatio)
                .background(selection.activeContainer),
        ) {
            CharacterCoverImage(
                avatarPath = characterCover(character),
                appearance = appearance,
            )
            if (selectable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) selection.indicator else appearance.mobileSurface.copy(alpha = 0.82f))
                        .border(1.dp, appearance.mobileMuted.copy(alpha = 0.16f), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Text("✓", color = selection.activeText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            characterName(character),
            color = appearance.mobileText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            minLines = 1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, top = 7.dp, end = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 7.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarCircle(
                name = authorName,
                size = 18,
                fontSize = 8,
                appearance = appearance,
                avatarPath = user.userAvatar,
            )
            Text(
                authorName,
                modifier = Modifier.weight(1f).padding(start = 5.dp),
                color = appearance.mobileMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun characterManagerCardInfoBg(appearance: AppearanceTheme): Color {
    val base = mobileRootContentColor(appearance)
    return if (appearance.mobileText.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.88f).compositeOver(base)
    } else {
        Color.Black.copy(alpha = 0.12f).compositeOver(base)
    }
}

@Composable
private fun CharacterCoverImage(
    avatarPath: String,
    appearance: AppearanceTheme,
) {
    val avatarFile = remember(avatarPath) {
        avatarPath.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (avatarFile != null) {
            AsyncImage(
                model = avatarFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StrokeSvgIcon(
                    paths = AppIconPaths.PictureFrame,
                    color = appearance.mobileMuted,
                    iconSize = 38.dp,
                    strokeWidth = 1.5f,
                )
                Text(
                    text = "暂无封面",
                    color = appearance.mobileMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
        }
    }
}



@Composable
internal fun CharacterGroupAction(
    text: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val field = appearance.fieldPalette()
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(field.container)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeSvgIcon(icon, if (icon == AppIconPaths.Trash) ElecKoiDanger else field.icon, iconSize = 19.dp)
        Text(text, modifier = Modifier.padding(start = 5.dp), color = field.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
