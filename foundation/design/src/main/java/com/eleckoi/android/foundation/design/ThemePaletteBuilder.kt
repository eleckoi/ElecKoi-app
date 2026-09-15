package com.eleckoi.android.foundation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeMonochrome
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Building the palette
// ---------------------------------------------------------------------------------------------

/**
 * Turns the seed into every surface, by way of material-color-utilities' dynamic scheme.
 *
 * [SchemeContent] is the faithful one of the schemes: it keeps the seed's own chroma rather than
 * pulling everything towards a tasteful pastel, so a Klein-blue picture yields a Klein-blue
 * interface. The alternative, TonalSpot, is what Android uses by default and is deliberately quiet
 * — which is the complaint this whole rewrite exists to answer.
 *
 * The mapping below is where the app's vocabulary meets M3's. Two choices in it are worth naming:
 *
 *  - The pinned block takes `secondaryContainer` and the reader's own bubble takes
 *    `primaryContainer`. Both are container roles, meaning they are *supposed* to carry colour at
 *    strength, and both arrive with a matching `on…` foreground whose contrast M3 has already
 *    guaranteed. Marking these two by colour is what the old code was reaching for when it marked
 *    them by weight instead and made them muddy.
 *
 *  - Surfaces climb the `surfaceContainer` ladder rather than sitting at tones I picked. In a light
 *    scheme that ladder runs from white downwards; in a dark one it runs from near-black upwards,
 *    and its top rung is tone 22 — which is where the old dark theme's *card* sat, on a page that
 *    was itself brighter than M3's brightest container.
 */
internal fun buildTheme(seed: Seed, polarity: Polarity, veil: Veil): AppearanceTheme {
    val source = Hct.fromInt(seed.argb)
    val scheme = if (seed.achromatic) {
        SchemeMonochrome(source, polarity.dark, 0.0)
    } else {
        SchemeContent(source, polarity.dark, 0.0)
    }
    val roles = MaterialDynamicColors()
    fun role(pick: MaterialDynamicColors.() -> DynamicColor): Color = Color(roles.pick().getArgb(scheme))

    val dark = polarity.dark
    val onSurface = role { onSurface() }
    val rootTopbarBase = role { surfaceContainerLow() }
    val rootTabbarBase = if (dark) {
        role { surfaceContainerHigh() }
    } else {
        role { surfaceContainerLowest() }
    }
    val rootChromeAccent = role { primary() }
    return AppearanceTheme(
        mobileBg = role { surfaceContainer() },
        mobileRootBg = role { surfaceContainer() },
        // `secondaryContainer` rather than `primaryContainer`, even though the latter carries more
        // colour: under the fidelity scheme primaryContainer *is* the seed, at the seed's own tone,
        // in both polarities alike. A role that does not follow the theme can only be used where the
        // field owns its foreground, and this one is painted under the global text at nineteen call
        // sites. secondaryContainer moves with the theme — tone 90 light, tone 30 dark.
        mobilePinnedBg = role { secondaryContainer() },
        // Not `surfaceContainerLowest` — that role is tone 100 in every light scheme, so the card
        // came out flat white no matter what the picture was.
        mobileSurface = role { surfaceContainerLow() },
        mobileText = onSurface,
        mobileMuted = role { onSurfaceVariant() },
        mobileSoft = role { outline() },
        mobileLine = onSurface.copy(alpha = if (dark) 0.13f else 0.11f),
        mobileSearchBg = role { surfaceContainerHighest() },
        // Root chrome gets two deliberate roles. In light mode the bottom bar starts at the
        // lightest surface so it stays visibly separate from the list; a faint seed tint keeps
        // image-derived themes recognizable. Dark mode keeps the raised container used elsewhere.
        mobileTopbarBg = rootChromeAccent
            .copy(alpha = if (dark) 0.16f else 0.09f)
            .compositeOver(rootTopbarBase),
        mobileTabbarBg = rootChromeAccent
            .copy(alpha = if (dark) 0.08f else 0.035f)
            .compositeOver(rootTabbarBase),
        mobileChatBg = role { surfaceContainer() },
        mobileChatHeaderBg = role { surfaceContainerHigh() }.copy(alpha = if (dark) 0.94f else 0.96f),
        // The assistant bubble must remain a visible layer above the chat page. `High` sits too
        // close to `surfaceContainer` in light schemes and visually disappears on plain chats.
        mobileChatMessageBg = role { surfaceContainerHighest() },
        mobileChatMessageFg = onSurface,
        // A bubble's lightness has to follow the theme, never the seed. `primaryContainer` under the
        // fidelity scheme is the seed at the seed's own tone, so a picture with a deep blue in it
        // produced a deep blue bubble on a light page — and, worse, that same deep bubble on a dark
        // one, where it sank into the wallpaper it was supposed to sit on. Measured against the
        // luminance the veil guarantees for the wallpaper, that pairing ranged from 1.82 to 4.89
        // depending on the picture. `secondaryContainer` follows the theme instead — tone 90 light,
        // tone 30 dark — and holds a steady 1.56–1.69 whatever the seed happens to be.
        //
        // Which leaves hue to do the distinguishing, and that is how the chat apps that get this
        // right do it: both bubbles sit at the same lightness and only one of them is tinted.
        mobileChatUserBg = role { secondaryContainer() },
        mobileChatUserFg = role { onSecondaryContainer() },
        mobileChatTextureScrim = veilColor(veil.midHue, veil.midChroma, dark),
        mobileComposerBg = role { surfaceContainerHigh() }.copy(alpha = if (dark) 0.97f else 0.98f),
        mobileInputBg = role { surfaceContainerLowest() },
        mobileBlue = role { primary() },
        mobileAccentFg = role { onPrimary() },
        isDark = dark,
        textureScrim = veil.strength,
        textureScrimAngle = veil.angleDegrees,
        textureScrimStart = veil.start,
        textureScrimMid = veil.mid,
        textureScrimEnd = veil.end,
        textureScrimStartColor = veilColor(veil.startHue, veil.startChroma, dark),
        textureScrimEndColor = veilColor(veil.endHue, veil.endChroma, dark),
    )
}

/**
 * The veil's own colour. This one is still ours: it is not a theme surface but a film laid over the
 * wallpaper, and it carries the local colour of the picture underneath it rather than any role.
 */
private fun veilColor(hue: Double, chroma: Double, dark: Boolean): Color {
    if (chroma <= 0.0) return Color.Transparent
    return oklchColor(if (dark) 0.105 else 0.975, min(chroma * 0.32, 0.042), hue)
}
