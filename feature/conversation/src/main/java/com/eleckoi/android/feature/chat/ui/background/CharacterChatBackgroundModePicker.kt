package com.eleckoi.android.feature.chat.ui.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.feature.preferences.NewCharacterBackground
import androidx.compose.foundation.background

internal enum class BackgroundOrigin {
    AppDefault,
    CharacterCard,
    Global,
    CharacterCustom,
}

@Composable
internal fun NewCharacterBackgroundPicker(
    selected: NewCharacterBackground,
    appearance: AppearanceTheme,
    onSelect: (NewCharacterBackground) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "新角色默认背景",
            modifier = Modifier.weight(1f),
            color = appearance.mobileText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(appearance.mobileSearchBg)
                .padding(3.dp),
        ) {
            listOf(
                NewCharacterBackground.App to "纯色背景",
                NewCharacterBackground.Character to "角色立绘",
            ).forEach { (choice, label) ->
                val active = selected == choice
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) appearance.mobileSurface else androidx.compose.ui.graphics.Color.Transparent)
                        .noRippleClickable { onSelect(choice) }
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        color = if (active) appearance.mobileText else appearance.mobileMuted,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BackgroundModePicker(
    origin: BackgroundOrigin,
    characterCardEnabled: Boolean,
    globalEnabled: Boolean,
    appearance: AppearanceTheme,
    onSelect: (BackgroundOrigin) -> Unit,
) {
    val choices = listOf(
        Triple(BackgroundOrigin.AppDefault, "纯色背景", true),
        Triple(BackgroundOrigin.CharacterCard, "角色立绘", characterCardEnabled),
        Triple(BackgroundOrigin.CharacterCustom, "自定义图片", true),
        Triple(BackgroundOrigin.Global, "共享背景", globalEnabled),
    )
    Text(
        text = "背景来源",
        color = appearance.mobileText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.chunked(2).forEach { rowChoices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowChoices.forEach { (choice, label, enabled) ->
                    BackgroundModeOption(
                        label = label,
                        selected = origin == choice,
                        enabled = enabled,
                        appearance = appearance,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(choice) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) appearance.mobileText else appearance.mobileSearchBg,
            )
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            StrokeSvgIcon(
                paths = AppIconPaths.Check,
                color = appearance.mobileSurface,
                iconSize = 14.dp,
                strokeWidth = 2.5f,
            )
            Spacer(modifier = Modifier.size(7.dp))
        }
        Text(
            text = label,
            color = when {
                selected -> appearance.mobileSurface
                enabled -> appearance.mobileText
                else -> appearance.mobileSoft
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
