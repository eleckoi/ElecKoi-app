package com.eleckoi.android.feature.characters.modes.story.settinglibrary.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.FilledSvgIcon

/**
 * The setting library's own glyphs, drawn for these screens rather than pulled from `AppIconPaths`.
 *
 * The shared set is built around the chat and settings surfaces, and at the 21–23dp these screens
 * use its weights read heavy — the old copy and move marks in particular were solid enough to look
 * like filled chips next to a hairline plus. Everything here is cut on the same 24 viewport at one
 * stroke weight so a row of them reads as one family.
 */
internal object SettingLibraryIcons {
    val Plus = listOf("M12 5v14", "M5 12h14")

    val Copy = listOf(
        "M7 9.7A2.7 2.7 0 0 1 9.7 7h8.6A2.7 2.7 0 0 1 21 9.7v8.6a2.7 2.7 0 0 1-2.7 2.7H9.7A2.7 2.7 0 0 1 7 18.3z",
        "M4 16.7A2 2 0 0 1 3 15V5a2 2 0 0 1 2-2h10c.75 0 1.16.39 1.5 1",
    )

    val Paste = listOf(
        "M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2",
        "M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2",
    )

    val Move = listOf("M14 12H4", "m14 12-4 4", "m14 12-4-4", "M20 4v16")

    val Cancel = listOf("M18 6 6 18", "M6 6l12 12")

    val Trash = listOf(
        "M4 7h16",
        "M10 11v6",
        "M14 11v6",
        "M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l1-12",
        "M9 7V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v3",
    )

    val More = listOf("M4 6h16", "M4 12h16", "M4 18h16")

    val Folder = listOf("M5 4h4l3 3h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2")

    val File = listOf(
        "M14 3v4a1 1 0 0 0 1 1h4",
        "M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2",
        "M9 13h6",
        "M9 17h6",
    )

    /** A small database stack for content kept in the stable cached-prefix section. */
    val Cache = listOf(
        "M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3Z",
        "M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6",
        "M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6",
    )

    val Rename = listOf("M4 20h4L18.5 9.5a2.83 2.83 0 1 0-4-4L4 16z", "M13.5 6.5l4 4")

    val Dots = listOf("M12 5.6h.01", "M12 12h.01", "M12 18.4h.01")

    val Check = listOf("m4.8 12.4 4.9 4.9L19.2 7.6")

    /** Two arrows passing each other: entries flowing into a library that is already there. */
    val Merge = listOf("M9 3.5v8", "m6 9 3 3 3-3", "M15 20.5v-8", "m18 15-3-3-3 3")

    /** One arrow into a tray: a whole package landing as its own thing. */
    val Import = listOf("M12 3v12", "m8 11 4 4 4-4", "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2")

    val Export = listOf("M12 15V3", "m8 7 4-4 4 4", "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2")

    val Users = listOf(
        "M9 7a3 3 0 1 0 6 0 3 3 0 0 0-6 0",
        "M3 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2",
        "M16 3.2a4 4 0 0 1 0 7.7",
        "M21 21v-2a4 4 0 0 0-3-3.9",
    )

    val Warning = listOf(
        "M12 8.5v4.5",
        "M12 16.5h.01",
        "M10.3 4.3 2.8 17.5A1.9 1.9 0 0 0 4.5 20.4h15a1.9 1.9 0 0 0 1.7-2.9L13.7 4.3a1.9 1.9 0 0 0-3.4 0Z",
    )

    val CheckboxEmpty = listOf("M7 3.5h10A3.5 3.5 0 0 1 20.5 7v10a3.5 3.5 0 0 1-3.5 3.5H7A3.5 3.5 0 0 1 3.5 17V7A3.5 3.5 0 0 1 7 3.5Z")

    val CheckboxPartial = CheckboxEmpty + listOf("M8 12h8")

    val CheckboxChecked = CheckboxEmpty + listOf("m7.8 12.2 2.9 2.9 5.5-6")
}

@Composable
internal fun SettingLibraryPromptGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 19.dp,
) {
    FilledSvgIcon(
        paths = AppIconPaths.PromptPosition,
        color = tint,
        modifier = modifier,
        iconSize = iconSize,
        viewportSize = 512f,
    )
}
