package com.eleckoi.android.feature.characters.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.paperCutPalette

internal const val ScrapbookReferenceWidth = 390f

internal data class ScrapbookPalette(
    val board: Color = Color(0xFFF0F5FF),
    val paper: Color = Color.White,
    val appBackground: Color = Color(0xFFF5F8FF),
    val ink: Color = Color(0xFF151C2B),
    val inkSoft: Color = Color(0xFF5E6C82),
    val label: Color = Color(0xFF7B8BA3),
    val rule: Color = Color(0xFFD7E3F4),
    val coverTint: Color = Color(0xFFE7F0FF),
    val accent: Color = Color(0xFF1783FF),
)

internal fun Modifier.characterScrapbookBoard(appearance: AppearanceTheme): Modifier =
    // Early paper-cut layout: one quiet sheet colour with no full-screen accent wash.
    background(appearance.paperCutPalette().slot)

/**
 * Shared recessed-paper tone for the character mode switch and its tool panels.
 * It stays predominantly neutral gray and only borrows a trace of the app blue.
 */
internal fun characterSettingsTrayColor(appearance: AppearanceTheme): Color {
    val paper = appearance.paperCutPalette()
    val neutralGray = lerp(paper.slot, paper.text, 0.035f)
    return lerp(neutralGray, appearance.mobileBlue, 0.025f)
}

@Composable
internal fun CharacterScrapbookFrame(
    name: String,
    fallbackName: String,
    avatarPath: String,
    coverPath: String,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onExport: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onAvatarClick: () -> Unit,
    onCoverClick: () -> Unit,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(scale: Float) -> Unit,
) {
    val paper = appearance.paperCutPalette()
    val colors = ScrapbookPalette(
        board = paper.slot,
        paper = paper.face,
        appBackground = paper.slot,
        ink = paper.text,
        inkSoft = paper.mutedText,
        label = paper.mutedText,
        rule = paper.border,
        coverTint = paper.selectedFace,
        accent = appearance.mobileBlue,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        val scale = maxWidth.value / ScrapbookReferenceWidth
        fun u(value: Float): Dp = (value * scale).dp
        val typeScale = scale / LocalDensity.current.fontScale
        val designHeight = minOf(maxHeight, u(816f))
        val cardTop = u(58f)
        val cardInset = u(8f)

        Text(
            text = "角色设定",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = u(13f)),
            color = appearance.mobileText,
            fontSize = (20f * typeScale).sp,
            lineHeight = (28f * typeScale).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )

        Box(
            modifier = Modifier
                .offset(x = cardInset, y = cardTop)
                .width(maxWidth - cardInset * 2)
                .height(designHeight - cardTop - u(8f))
                .shadow(u(2f), RoundedCornerShape(u(16f)), clip = false)
                .background(colors.paper, RoundedCornerShape(u(16f))),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = u(12f), top = u(12f), end = u(12f), bottom = u(12f)),
            ) {
                CharacterIdentityHero(
                    name = name,
                    fallbackName = fallbackName,
                    avatarPath = avatarPath,
                    coverPath = coverPath,
                    appearance = appearance,
                    colors = colors,
                    scale = scale,
                    onAvatarClick = onAvatarClick,
                    onCoverClick = onCoverClick,
                    onNameChange = onNameChange,
                )
                content(scale)
            }
        }

        // The page redesign deliberately keeps the established navigation controls unchanged.
        ScrapbookBackButton(
            appearance = appearance,
            scale = scale,
            onClick = onBack,
            modifier = Modifier.offset(x = u(3f), y = u(3f)),
        )
        if (onExport != null && onDelete != null) {
            CharacterSettingsOverflow(
                appearance = appearance,
                scale = scale,
                onExport = onExport,
                onDelete = onDelete,
                modifier = Modifier.offset(x = maxWidth - 48.dp, y = u(3f)),
            )
        }
    }
}
