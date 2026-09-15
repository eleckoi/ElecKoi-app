package com.eleckoi.android.feature.characters.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.paperCutPalette

internal enum class CharacterSettingsSection(
    val label: String,
    val iconPaths: List<String>? = null,
    val materialIcon: ImageVector? = null,
) {
    Profile("简介", iconPaths = AppIconPaths.User),
    Story("剧情小说", materialIcon = Icons.AutoMirrored.Outlined.MenuBook),
}

@Composable
internal fun CharacterSectionSwitch(
    activeSection: CharacterSettingsSection,
    appearance: AppearanceTheme,
    layoutScale: Float = 1f,
    onChange: (CharacterSettingsSection) -> Unit,
) {
    val paper = appearance.paperCutPalette()
    val trackColor = characterSettingsTrayColor(appearance)
    val typeScale = layoutScale / LocalDensity.current.fontScale
    val trackShape = RoundedCornerShape((12f * layoutScale).dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height((52f * layoutScale).dp)
            .shadow(
                elevation = (6f * layoutScale).dp,
                shape = trackShape,
                clip = false,
                ambientColor = paper.shadow.copy(alpha = 0.12f),
                spotColor = paper.shadow.copy(alpha = 0.10f),
            )
            .shadow(
                elevation = (2f * layoutScale).dp,
                shape = trackShape,
                clip = false,
                ambientColor = paper.shadow.copy(alpha = 0.07f),
                spotColor = paper.shadow.copy(alpha = 0.09f),
            )
            .clip(trackShape)
            .background(trackColor)
            .padding((2f * layoutScale).dp),
    ) {
        CharacterSettingsSection.entries.forEach { section ->
            CharacterSectionTab(
                label = section.label,
                iconPaths = section.iconPaths,
                materialIcon = section.materialIcon,
                selected = activeSection == section,
                appearance = appearance,
                layoutScale = layoutScale,
                typeScale = typeScale,
                onClick = { onChange(section) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun CharacterSectionTab(
    label: String,
    iconPaths: List<String>?,
    materialIcon: ImageVector?,
    selected: Boolean,
    appearance: AppearanceTheme,
    layoutScale: Float,
    typeScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(120, easing = motion),
        label = "characterSectionPress-$label",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            appearance.mobileText.copy(alpha = 0.90f)
        } else {
            appearance.mobileText.copy(alpha = 0.45f)
        },
        animationSpec = tween(160, easing = motion),
        label = "characterSectionText-$label",
    )
    val shape = RoundedCornerShape((9f * layoutScale).dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(
                if (selected) {
                    Modifier
                        .shadow(
                            elevation = (4f * layoutScale).dp,
                            shape = shape,
                            clip = false,
                            ambientColor = appearance.paperCutPalette().shadow.copy(alpha = 0.12f),
                            spotColor = appearance.paperCutPalette().shadow.copy(alpha = 0.14f),
                        )
                        .clip(shape)
                        .background(appearance.paperCutPalette().face)
                } else {
                    Modifier.clip(shape)
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (materialIcon != null) {
                Icon(
                    imageVector = materialIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size((16f * layoutScale).dp),
                )
            } else if (iconPaths != null) {
                StrokeSvgIcon(
                    paths = iconPaths,
                    color = textColor,
                    iconSize = (16f * layoutScale).dp,
                    strokeWidth = 1.7f,
                )
            }
            Text(
                text = label,
                modifier = Modifier.padding(start = (5f * layoutScale).dp),
                color = textColor,
                fontSize = (14f * typeScale).sp,
                lineHeight = (20f * typeScale).sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}
