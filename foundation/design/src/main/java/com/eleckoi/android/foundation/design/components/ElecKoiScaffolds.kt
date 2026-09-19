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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eleckoi.android.foundation.design.AppearanceTheme
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

enum class MobileRootChromePlacement {
    Top,
    Bottom,
}

@Composable
fun MobileRootBackdrop(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    previewModel: Any? = null,
) {
    val storedFile = remember(appearance.rootBackgroundImagePath) {
        appearance.rootBackgroundImagePath
            .takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::exists)
    }
    val model = previewModel ?: storedFile
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(mobileRootContentColor(appearance)),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
fun MobileRootChromeBar(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    placement: MobileRootChromePlacement = MobileRootChromePlacement.Top,
    chromeColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedChromeColor = chromeColor ?: when (placement) {
        MobileRootChromePlacement.Top -> appearance.mobileTopbarBg
        MobileRootChromePlacement.Bottom -> appearance.mobileTabbarBg
    }
    Box(modifier = modifier.background(resolvedChromeColor), content = content)
}

/** Root-page chrome that paints behind the status bar with the page title. */
@Composable
fun MobileRootTopBar(
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    includeStatusBarInset: Boolean = true,
    chromeColor: Color? = null,
    content: @Composable () -> Unit,
) {
    MobileRootChromeBar(
        appearance = appearance,
        placement = MobileRootChromePlacement.Top,
        chromeColor = chromeColor ?: mobileRootTopBarContainerColor(appearance),
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
