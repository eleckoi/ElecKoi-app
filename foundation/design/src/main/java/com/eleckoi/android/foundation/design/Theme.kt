package com.eleckoi.android.foundation.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ElecKoiDanger = Color(0xFFE05260)
val ElecKoiSuccess = Color(0xFF2FA866)

private val ElecKoiLightColorScheme = lightColorScheme(
    primary = Color(0xFF13A8FF),
    background = Color(0xFFF0F3F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF4F5F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFCFD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF5F6F8),
    surfaceContainerHighest = Color(0xFFEEF0F3),
    onPrimary = Color.White,
    onBackground = Color(0xFF161821),
    onSurface = Color(0xFF161821),
    onSurfaceVariant = Color(0xFF6F7580),
    outline = Color(0xFFBFC4CC),
    outlineVariant = Color(0xFFE2E5E9),
)

private val ElecKoiDarkColorScheme = darkColorScheme(
    primary = Color(0xFF36AEF7),
    background = Color(0xFF13131A),
    surface = Color(0xFF1D1D25),
    surfaceVariant = Color(0xFF202029),
    surfaceContainerLowest = Color(0xFF13131A),
    surfaceContainerLow = Color(0xFF1A1A21),
    surfaceContainer = Color(0xFF1D1D25),
    surfaceContainerHigh = Color(0xFF202029),
    surfaceContainerHighest = Color(0xFF262630),
    onPrimary = Color.White,
    onBackground = Color(0xFFF2F2F5),
    onSurface = Color(0xFFF2F2F5),
    onSurfaceVariant = Color(0xFFA4A4AD),
    outline = Color(0xFF74747E),
    outlineVariant = Color(0x20FFFFFF),
)

@Composable
fun ElecKoiTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ElecKoiDarkColorScheme else ElecKoiLightColorScheme,
        content = content,
    )
}
