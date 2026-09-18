package com.eleckoi.android.feature.appfont.ui

import android.graphics.Typeface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.eleckoi.android.feature.appfont.data.AppFontDownloader
import com.eleckoi.android.feature.appfont.data.AppFontRepository
import com.eleckoi.android.feature.appfont.data.AppFontScope
import com.eleckoi.android.feature.appfont.data.AppFontSelection

// Material3's Text merges LocalTextStyle with whatever the call site passes explicitly. Call sites
// in this app set fontSize but not fontFamily, so overriding LocalTextStyle here reaches all ~460
// of them without touching one. The dozen places that ask for Monospace pass fontFamily themselves
// and therefore keep it — code blocks and logs stay aligned.
@Composable
fun ProvideAppFont(
    chatSubtree: Boolean = false,
    onTypefaceChanged: ((Typeface?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { AppFontRepository(context) }
    // Null until the store answers. An AppFontSelection() placeholder would be indistinguishable
    // from "the user picked the system font", and this composable is mounted a second time when a
    // chat opens — so every chat entry pushed a null typeface for a frame, resetting the engine and
    // laying out whatever was on screen in the default font before the real value arrived.
    val selection by repository.selectionFlow.collectAsState(initial = null)
    val installRevision by AppFontDownloader.completions.collectAsState()
    val typeface = remember(selection?.fontId, installRevision) {
        selection?.let { repository.typefaceFor(it.fontId) }
    }

    // If the process was killed mid-download the staging file outlives it, and the settings page
    // may never be opened again. Sweeping at startup keeps that from accumulating.
    LaunchedEffect(Unit) { AppFontDownloader.sweepAbandoned(repository) }

    // Canvas-based text renderers can opt in without making this reusable font runtime depend on
    // a particular feature's rendering engine.
    LaunchedEffect(selection != null, typeface, onTypefaceChanged) {
        if (selection == null) return@LaunchedEffect
        onTypefaceChanged?.invoke(typeface)
    }

    val fontFamily = rememberAppFontFamily(typeface, selection, chatSubtree)

    if (fontFamily == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily),
            content = content,
        )
    }
}

@Composable
private fun rememberAppFontFamily(
    typeface: Typeface?,
    selection: AppFontSelection?,
    chatSubtree: Boolean,
): FontFamily? {
    val applies = when (selection?.scope) {
        AppFontScope.All -> true
        AppFontScope.ChatOnly -> chatSubtree
        null -> false
    }
    return remember(typeface, applies) {
        if (!applies || typeface == null) return@remember null
        FontFamily(typeface)
    }
}
