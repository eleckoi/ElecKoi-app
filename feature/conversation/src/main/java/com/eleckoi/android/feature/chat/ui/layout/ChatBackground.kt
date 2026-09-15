package com.eleckoi.android.feature.chat.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eleckoi.android.feature.characters.model.AppDefaultChatBackground
import com.eleckoi.android.feature.characters.model.CustomChatBackground
import com.eleckoi.android.feature.characters.model.GlobalChatBackground
import com.eleckoi.android.foundation.design.AppearanceTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every input [ChatBackground] needs, as one value. The glass panels in the composer redraw this
 * same background inside their own lens layer, so the parameters have to travel to a second call
 * site without being spelled out twice — and being a data class keeps the CompositionLocal that
 * carries them from invalidating its readers on every recomposition.
 */
@androidx.compose.runtime.Immutable
data class ChatBackdropSpec(
    val appearance: AppearanceTheme,
    val characterBackgroundPath: String = "",
    val defaultCharacterBackgroundPath: String = "",
    val characterBackgroundOpacity: Float = 0.72f,
    val characterBackgroundBlur: Float = 0f,
    val characterBackgroundScrim: Float = 0.22f,
    val characterBackgroundResolved: Boolean = true,
)

@Composable
fun ChatBackground(spec: ChatBackdropSpec, modifier: Modifier = Modifier) {
    ChatBackground(
        appearance = spec.appearance,
        characterBackgroundPath = spec.characterBackgroundPath,
        defaultCharacterBackgroundPath = spec.defaultCharacterBackgroundPath,
        characterBackgroundOpacity = spec.characterBackgroundOpacity,
        characterBackgroundBlur = spec.characterBackgroundBlur,
        characterBackgroundScrim = spec.characterBackgroundScrim,
        characterBackgroundResolved = spec.characterBackgroundResolved,
        modifier = modifier,
    )
}

@Composable
fun ChatBackground(
    appearance: AppearanceTheme,
    characterBackgroundPath: String = "",
    defaultCharacterBackgroundPath: String = "",
    characterBackgroundOpacity: Float = 0.72f,
    characterBackgroundBlur: Float = 0f,
    characterBackgroundScrim: Float = 0.22f,
    // False while the character's own background is still being loaded. Without this an empty path
    // reads as "this character has no background" and the global texture gets painted for a frame
    // before the character's own image replaces it.
    characterBackgroundResolved: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val characterFile = remember(characterBackgroundPath) {
        characterBackgroundPath
            .takeIf {
                    it.isNotBlank() &&
                    it != AppDefaultChatBackground &&
                    it != CustomChatBackground &&
                    it != GlobalChatBackground
            }
            ?.let(::File)
            ?.takeIf { it.exists() }
    }
    val globalFile = remember(appearance.textureImagePath) {
        appearance.textureImagePath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
    }
    val defaultCharacterFile = remember(defaultCharacterBackgroundPath) {
        defaultCharacterBackgroundPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
    }
    val backgroundChoice = if (characterBackgroundResolved) {
        resolveChatBackgroundChoiceForMode(
            characterBackgroundPath = characterBackgroundPath,
            characterFile = characterFile,
            globalFile = globalFile,
            defaultCharacterFile = defaultCharacterFile,
        )
    } else {
        ChatBackgroundChoice()
    }
    val textureFile = backgroundChoice.file
    val usingGlobalTexture = backgroundChoice.source == ChatBackgroundSource.Global
    val effectiveOpacity = if (usingGlobalTexture) appearance.textureOpacity else characterBackgroundOpacity
    // Blur and reading veil are entirely the background editor's business.
    val effectiveBlur = if (usingGlobalTexture) appearance.textureBlur else characterBackgroundBlur
    val effectiveScrim = if (usingGlobalTexture) appearance.textureScrim else characterBackgroundScrim
    val scrimBase = appearance.mobileChatTextureScrim
        .takeIf { it.alpha > 0f }
        ?.copy(alpha = 1f)
        ?: appearance.mobileChatBg.copy(alpha = 1f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                // No composer-coloured stop at the bottom. It darkened the page exactly where the
                // composer floats, which stacked with the panel's own shadow and read as a second
                // shadow smeared across the lower third of the chat.
                Brush.verticalGradient(
                    listOf(
                        appearance.mobileChatHeaderBg,
                        appearance.mobileChatBg,
                    )
                )
            )
    ) {
        if (textureFile != null) {
            AsyncImage(
                model = textureFile,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(effectiveBlur.coerceIn(0f, 24f).dp)
                    .graphicsLayer { alpha = effectiveOpacity.coerceIn(0f, 1f) },
                contentScale = ContentScale.Crop,
            )
            // The character's own background never went through the analyzer, so it only gets the
            // flat veil. The global texture gets the directional one the analyzer measured for it.
            if (!usingGlobalTexture) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimBase.copy(alpha = effectiveScrim.coerceIn(0f, 1f))),
                )
            } else {
                DirectionalScrim(appearance = appearance, base = scrimBase, strength = effectiveScrim)
            }
        }
    }
}

internal enum class ChatBackgroundSource {
    Character,
    Global,
    CharacterDefault,
    None,
}

internal data class ChatBackgroundChoice(
    val file: File? = null,
    val source: ChatBackgroundSource = ChatBackgroundSource.None,
)

/**
 * Blank is the untouched/new-character state and therefore resolves to card art even if a global
 * image exists. App-default and an empty custom slot suppress all fallbacks; only the explicit
 * global marker follows the shared image.
 */
internal fun resolveChatBackgroundChoiceForMode(
    characterBackgroundPath: String,
    characterFile: File?,
    globalFile: File?,
    defaultCharacterFile: File?,
): ChatBackgroundChoice = when (characterBackgroundPath) {
    AppDefaultChatBackground -> ChatBackgroundChoice()
    CustomChatBackground -> ChatBackgroundChoice()
    GlobalChatBackground -> resolveChatBackgroundChoice(null, globalFile, defaultCharacterFile)
    else -> resolveChatBackgroundChoice(characterFile, null, defaultCharacterFile)
}

/** Resolves one explicit mode. Callers decide whether the global file belongs in that mode. */
internal fun resolveChatBackgroundChoice(
    characterFile: File?,
    globalFile: File?,
    defaultCharacterFile: File?,
): ChatBackgroundChoice = when {
    characterFile != null -> ChatBackgroundChoice(characterFile, ChatBackgroundSource.Character)
    globalFile != null -> ChatBackgroundChoice(globalFile, ChatBackgroundSource.Global)
    defaultCharacterFile != null ->
        ChatBackgroundChoice(defaultCharacterFile, ChatBackgroundSource.CharacterDefault)
    else -> ChatBackgroundChoice()
}



/**
 * A veil whose opacity varies across the screen. One flat alpha cannot serve an image whose halves
 * differ in brightness: the value that rescues the dark half bleaches the bright half to paper.
 * The analyzer measures how much cover each region needs and stores it as an angle plus three
 * stops; this paints it.
 */
@Composable
private fun DirectionalScrim(appearance: AppearanceTheme, base: Color, strength: Float) {
    val overall = strength.coerceIn(0f, 1f)
    val start = (overall * appearance.textureScrimStart).coerceIn(0f, 1f)
    val mid = (overall * appearance.textureScrimMid).coerceIn(0f, 1f)
    val end = (overall * appearance.textureScrimEnd).coerceIn(0f, 1f)
    if (start <= 0f && mid <= 0f && end <= 0f) return

    // Each stop keeps the colour the analyzer sampled from the image under it, so a picture with two
    // colours does not get flattened to one averaged tint.
    val startColor = appearance.textureScrimStartColor.takeIf { it.alpha > 0f } ?: base
    val endColor = appearance.textureScrimEndColor.takeIf { it.alpha > 0f } ?: base

    val radians = Math.toRadians(appearance.textureScrimAngle.toDouble())
    val dirX = cos(radians).toFloat()
    val dirY = sin(radians).toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Run the gradient axis through the centre and out to the edge of the box's
                // projection, so the end stops land on the corners rather than short of them.
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val reach = (abs(dirX) * size.width + abs(dirY) * size.height) / 2f
                drawRect(
                    brush = Brush.linearGradient(
                        0f to startColor.copy(alpha = start),
                        0.5f to base.copy(alpha = mid),
                        1f to endColor.copy(alpha = end),
                        start = Offset(centerX - dirX * reach, centerY - dirY * reach),
                        end = Offset(centerX + dirX * reach, centerY + dirY * reach),
                    ),
                )
            },
    )
}
