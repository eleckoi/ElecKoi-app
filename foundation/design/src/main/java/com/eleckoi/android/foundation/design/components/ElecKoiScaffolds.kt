package com.eleckoi.android.foundation.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.skydoves.cloudy.Sky
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.sky
import java.io.File

@Composable
fun PinnedStatusScaffold(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    imeAware: Boolean = false,
    includeStatusBarPadding: Boolean = true,
    backgroundColor: Color = appearance.mobilePinnedBg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusDismissRegistry = remember { FocusDismissRegistry() }
    val unpaddedBase = modifier
        .fillMaxSize()
        .background(backgroundColor)
        .clearFocusOnBlankTap()
    val base = if (includeStatusBarPadding) {
        unpaddedBase.statusBarsPadding()
    } else {
        unpaddedBase
    }
    CompositionLocalProvider(LocalFocusDismissRegistry provides focusDismissRegistry) {
        Column(
            modifier = if (imeAware) base.imePadding() else base,
            content = content,
        )
    }
}

val LocalMobileRootGlassSky = staticCompositionLocalOf<Sky?> { null }

enum class MobileRootGlassPlacement {
    Top,
    Bottom,
}

@Composable
fun MobileRootGlassProvider(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sky = com.skydoves.cloudy.rememberSky()
    CompositionLocalProvider(LocalMobileRootGlassSky provides sky) {
        Box(modifier = modifier, content = { content() })
    }
}

@Composable
fun MobileRootBackdrop(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    previewModel: Any? = null,
) {
    val sky = LocalMobileRootGlassSky.current
    val storedFile = remember(appearance.rootBackgroundImagePath) {
        appearance.rootBackgroundImagePath
            .takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::exists)
    }
    val model = previewModel
        ?: storedFile
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (sky != null) Modifier.sky(sky) else Modifier)
            .background(mobileRootContentColor(appearance)),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { sky?.invalidate() },
                modifier = Modifier
                    .fillMaxSize()
                    .blur(
                        radius = appearance.rootBackgroundBlur.coerceIn(0f, 24f).dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    )
                    .graphicsLayer {
                        alpha = appearance.rootBackgroundOpacity.coerceIn(0f, 1f)
                    },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    appearance.mobileSurface.copy(
                        alpha = appearance.rootBackgroundScrim.coerceIn(0f, 1f),
                    ),
                ),
        )
    }
}

@Composable
private fun Modifier.mobileRootLiquidGlass(
    appearance: AppearanceTheme,
    chromeColor: Color,
): Modifier {
    val sky = LocalMobileRootGlassSky.current
        ?: return background(chromeColor)
    return cloudy(
            sky = sky,
            radius = 26,
            tint = chromeColor.copy(alpha = if (appearance.isDark) 0.30f else 0.22f),
            cpuBlurEnabled = false,
            shape = RectangleShape,
        )
        .background(chromeColor.copy(alpha = if (appearance.isDark) 0.16f else 0.10f))
}

/**
 * Paints the root wallpaper at this composable's screen position without sampling foreground list
 * items. Sticky content can therefore remain visually continuous with the page while still being
 * opaque enough to cover rows scrolling underneath it.
 */
@Composable
fun Modifier.mobileRootBackdropSample(appearance: AppearanceTheme): Modifier {
    val sky = LocalMobileRootGlassSky.current
        ?: return background(appearance.mobilePinnedBg)
    return cloudy(
        sky = sky,
        radius = 1,
        tint = Color.Transparent,
        cpuBlurEnabled = false,
        shape = RectangleShape,
    )
}

@Composable
fun MobileRootGlassBar(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    placement: MobileRootGlassPlacement = MobileRootGlassPlacement.Top,
    chromeColor: Color? = null,
    glassEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedChromeColor = chromeColor ?: when (placement) {
        MobileRootGlassPlacement.Top -> appearance.mobileTopbarBg
        MobileRootGlassPlacement.Bottom -> appearance.mobileTabbarBg
    }
    if (!glassEnabled || (appearance.isDark && resolvedChromeColor == Color.Black)) {
        Box(modifier = modifier.background(resolvedChromeColor), content = content)
        return
    }
    val stableAlpha = if (appearance.isDark) 0.88f else 0.92f
    val stableFill = Modifier.background(resolvedChromeColor.copy(alpha = stableAlpha))
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .mobileRootLiquidGlass(appearance, resolvedChromeColor),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                // A high-alpha semantic fill is the stable base colour. The glass still samples the
                // wallpaper through the remaining fraction, but can no longer disappear into rows.
                .then(stableFill),
        )
        content()
    }
}

/** Root-page chrome that paints behind the status bar with the page title. */
@Composable
fun MobileRootTopBar(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    includeStatusBarInset: Boolean = true,
    chromeColor: Color? = null,
    glassEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    MobileRootGlassBar(
        appearance = appearance,
        placement = MobileRootGlassPlacement.Top,
        chromeColor = chromeColor ?: mobileRootTopBarContainerColor(appearance),
        glassEnabled = glassEnabled,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (includeStatusBarInset) Modifier.statusBarsPadding() else Modifier),
        ) {
            content()
        }
    }
}

@Composable
fun MobileRootSurface(
    appearance: AppearanceTheme,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MobileRootTopBar(appearance = appearance) {
            header()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            content()
        }
    }
}

fun mobileRootContentColor(appearance: AppearanceTheme): Color =
    appearance.mobileRootBg

fun mobileRootTopBarContainerColor(appearance: AppearanceTheme): Color =
    appearance.mobileTopbarBg
