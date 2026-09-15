package com.eleckoi.android.foundation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Immutable

/**
 * Ceiling on a single veil stop, as a multiple of [AppearanceTheme.textureScrim]. A picture with one
 * very dark end has a low average demand and a high demand at that end, so the stops need real
 * headroom above the average; capping tighter would quietly under-cover the region that needed the
 * veil most. The product is clamped to 1.0 at paint time regardless.
 */
const val ScrimStopCeiling = 4f

/**
 * Optional reading-colour overrides for Markdown message content. A missing value deliberately
 * means "follow the current theme", which lets a palette sampled from an image remain useful
 * without overwriting colours the reader explicitly chose.
 */
@Immutable
data class MarkdownReadingColorOverrides(
    val italic: Color? = null,
    val underline: Color? = null,
    val quote: Color? = null,
    val inlineCode: Color? = null,
    val codeForeground: Color? = null,
    val codeBackground: Color? = null,
)

data class AppearanceTheme(
    val mobileBg: Color = Color(0xFFF0F3F6),
    // The four root libraries share a cleaner neutral canvas than editor and settings pages.
    // Keeping this role separate prevents a root-page polish from recolouring the whole app.
    val mobileRootBg: Color = Color(0xFFF7F7F8),
    val mobilePinnedBg: Color = Color(0x0F263148),
    val mobileSurface: Color = Color.White,
    val mobileText: Color = Color(0xFF111111),
    val mobileMuted: Color = Color(0xFF8B8B8B),
    val mobileSoft: Color = Color(0xFFADADAD),
    val mobileLine: Color = Color(0x0E000000),
    val mobileSearchBg: Color = Color(0xFFEBEBEB),
    // Root chrome owns explicit colours so the glass never collapses into the message surface when
    // there is no wallpaper underneath it. The bottom bar stays neutral instead of carrying the old
    // purple-grey tint into an otherwise white list surface.
    val mobileTopbarBg: Color = Color(0xFFFCFCFC),
    val mobileTabbarBg: Color = Color(0xFFFCFCFC),
    val mobileChatBg: Color = Color.White,
    val mobileChatHeaderBg: Color = Color.White,
    val mobileChatMessageBg: Color = Color.White,
    val mobileChatMessageFg: Color = Color(0xFF181818),
    val mobileChatUserBg: Color = Color(0xFFCDEEFF),
    val mobileChatUserFg: Color = Color(0xFF181818),
    val mobileChatTextureScrim: Color = Color.Transparent,
    val mobileComposerBg: Color = Color(0xFFF2F2F3),
    val mobileInputBg: Color = Color(0xFFFEFEFF),
    val mobileBlue: Color = Color(0xFF13A8FF),
    val mobileAccentFg: Color = Color.White,
    val rootBackgroundImagePath: String = "",
    val rootBackgroundOpacity: Float = 1f,
    val rootBackgroundBlur: Float = 12f,
    val rootBackgroundScrim: Float = 0f,
    val textureImagePath: String = "",
    val textureOpacity: Float = 0.72f,
    val textureBlur: Float = 0f,
    val textureScrim: Float = 0.22f,
    // Whether the generated palette is a dark one. Derived colours used to infer this by measuring
    // mobileBg every time; now the analyzer states it once.
    val isDark: Boolean = false,
    // The reading veil is directional. `textureScrim` is its overall strength (and what the user's
    // slider drives); the three stops are multipliers on that strength along `textureScrimAngle`,
    // so dragging the slider scales the shape instead of flattening it. All 1.0 means a flat veil.
    val textureScrimAngle: Float = 90f,
    val textureScrimStart: Float = 1f,
    val textureScrimMid: Float = 1f,
    val textureScrimEnd: Float = 1f,
    // The veil also carries the picture's own colour along that axis: the end lying over blonde hair
    // veils in a warm white, the end over pink ribbons in a pink one. `mobileChatTextureScrim` is the
    // middle stop. Transparent means "no measurement, reuse the middle".
    val textureScrimStartColor: Color = Color.Transparent,
    val textureScrimEndColor: Color = Color.Transparent,
    val markdownReadingColors: MarkdownReadingColorOverrides = MarkdownReadingColorOverrides(),
)

/** PC-aligned dark tokens. Background paths and tuning are presentation state and are retained. */
fun AppearanceTheme.withDarkAppearance(dark: Boolean): AppearanceTheme {
    if (isDark == dark) return this
    val colors = if (dark) DarkAppearanceTheme else AppearanceTheme()
    return colors.copy(
        rootBackgroundImagePath = rootBackgroundImagePath,
        rootBackgroundOpacity = rootBackgroundOpacity,
        rootBackgroundBlur = rootBackgroundBlur,
        rootBackgroundScrim = rootBackgroundScrim,
        textureImagePath = textureImagePath,
        textureOpacity = textureOpacity,
        textureBlur = textureBlur,
        textureScrim = textureScrim,
        textureScrimAngle = textureScrimAngle,
        textureScrimStart = textureScrimStart,
        textureScrimMid = textureScrimMid,
        textureScrimEnd = textureScrimEnd,
        textureScrimStartColor = textureScrimStartColor,
        textureScrimEndColor = textureScrimEndColor,
        markdownReadingColors = markdownReadingColors,
    )
}

private val DarkAppearanceTheme = AppearanceTheme(
    mobileBg = Color(0xFF13131A),
    mobileRootBg = Color(0xFF13131A),
    mobilePinnedBg = Color(0x12FFFFFF),
    mobileSurface = Color(0xFF1D1D25),
    mobileText = Color(0xFFF2F2F5),
    mobileMuted = Color(0xFFA4A4AD),
    mobileSoft = Color(0xFF74747E),
    mobileLine = Color(0x13FFFFFF),
    mobileSearchBg = Color(0xFF262630),
    mobileTopbarBg = Color.Black,
    mobileTabbarBg = Color.Black,
    mobileChatBg = Color(0xFF13131A),
    mobileChatHeaderBg = Color(0xFF13131A),
    mobileChatMessageBg = Color(0xFF1D1D25),
    mobileChatMessageFg = Color(0xFFF2F2F5),
    mobileChatUserBg = Color(0xFF203B4E),
    mobileChatUserFg = Color(0xFFF2F2F5),
    mobileChatTextureScrim = Color.Transparent,
    mobileComposerBg = Color(0xFF1D1D25),
    mobileInputBg = Color(0xFF1D1D25),
    mobileBlue = Color(0xFF36AEF7),
    mobileAccentFg = Color.White,
    isDark = true,
)
