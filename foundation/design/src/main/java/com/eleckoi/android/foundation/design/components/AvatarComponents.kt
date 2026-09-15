package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.PhosphorRegular
import com.eleckoi.android.foundation.design.selectionPalette
import java.io.File

private val MissingAvatarIcon = listOf(PhosphorRegular.UserFill)

@Composable
fun AvatarCircle(
    name: String,
    size: Int,
    fontSize: Int,
    appearance: AppearanceTheme,
    avatarPath: String = "",
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    height: Int = size,
) {
    val selection = appearance.selectionPalette()
    val avatarFile = remember(avatarPath) {
        avatarPath.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
    }
    Box(
        modifier = modifier
            .size(width = size.dp, height = height.dp)
            .clip(shape)
            .background(selection.activeContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarFile != null) {
            AsyncImage(
                model = avatarFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            FilledSvgIcon(
                paths = MissingAvatarIcon,
                color = selection.indicator,
                iconSize = (fontSize * 1.35f).dp,
                viewportSize = 256f,
            )
        }
    }
}

/**
 * PC-compatible character artwork for compact lists. Cover mode uses the dedicated 3:4 artwork
 * and only falls back to the avatar when the card has no cover.
 */
@Composable
fun ListCharacterArtwork(
    name: String,
    avatarPath: String,
    coverPath: String,
    useCover: Boolean,
    appearance: AppearanceTheme,
    avatarSize: Int,
    coverWidth: Int,
    coverHeight: Int,
    coverCornerRadius: Int,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    val resolvedPath = if (useCover) coverPath.ifBlank { avatarPath } else avatarPath
    AvatarCircle(
        name = name,
        size = if (useCover) coverWidth else avatarSize,
        height = if (useCover) coverHeight else avatarSize,
        fontSize = fontSize,
        appearance = appearance,
        avatarPath = resolvedPath,
        modifier = modifier,
        shape = if (useCover) RoundedCornerShape(coverCornerRadius.dp) else CircleShape,
    )
}
