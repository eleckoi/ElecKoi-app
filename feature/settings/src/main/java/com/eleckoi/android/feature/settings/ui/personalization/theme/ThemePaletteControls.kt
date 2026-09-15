package com.eleckoi.android.feature.settings.ui.personalization.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.common.TunerSlider
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.feature.preferences.AppearanceMode
import kotlin.math.roundToInt

internal enum class ThemeEditorTab(val label: String) {
    Palette("主题色"),
    RootBackground("主页背景"),
}

@Composable
internal fun AppearanceModePicker(
    selected: AppearanceMode,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onSelect: (AppearanceMode) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        ThemeSectionLabel("外观模式", appearance)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(appearance.mobileMuted.copy(alpha = 0.10f))
                .padding(3.dp),
        ) {
            listOf(
                AppearanceMode.Light to "浅色",
                AppearanceMode.Dark to "深色",
                AppearanceMode.System to "跟随系统",
            ).forEach { (mode, label) ->
                val active = selected == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) appearance.mobileSurface else Color.Transparent)
                        .noRippleClickable { onSelect(mode) }
                        .semantics {
                            role = Role.RadioButton
                            this.selected = active
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (active) appearance.mobileText else appearance.mobileMuted,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThemeEditorHeader(
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(appearance.mobileMuted.copy(alpha = 0.12f))
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                StrokeSvgIcon(AppIconPaths.X, appearance.mobileText, iconSize = 18.dp)
            }
        }
        Text(
            text = "主题风格",
            modifier = Modifier.weight(1f),
            color = appearance.mobileText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(48.dp)
                .noRippleClickable(onClick = onReset),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "恢复默认",
                color = appearance.mobileMuted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
internal fun ThemeEditorTabs(
    activeTab: ThemeEditorTab,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onChange: (ThemeEditorTab) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(appearance.mobileMuted.copy(alpha = 0.10f))
            .padding(3.dp),
    ) {
        ThemeEditorTab.entries.forEach { tab ->
            val selected = tab == activeTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) appearance.mobileSurface else Color.Transparent)
                    .noRippleClickable { onChange(tab) }
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (selected) appearance.mobileText else appearance.mobileMuted,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
internal fun ThemeSectionLabel(
    text: String,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        color = appearance.mobileText,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun FullWidthAction(
    label: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(appearance.mobileText)
            .noRippleClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isNotEmpty()) {
            StrokeSvgIcon(icon, appearance.mobileSurface, iconSize = 18.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label, color = appearance.mobileSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SecondaryAction(
    label: String,
    icon: List<String>,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(appearance.mobileMuted.copy(alpha = 0.10f))
            .noRippleClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeSvgIcon(icon, appearance.mobileText, iconSize = 18.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = appearance.mobileText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun RootBackgroundTuningSheet(
    appearance: AppearanceTheme,
    opacity: Float,
    blur: Float,
    scrim: Float,
    canClear: Boolean,
    onOpacityChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    onScrimChange: (Float) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = "主页背景",
            color = appearance.mobileText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(18.dp))
        CompactTuningRow(
            label = "透明度",
            valueText = "${((1f - opacity) * 100f).roundToInt()}%",
            value = 1f - opacity,
            range = 0f..0.8f,
            appearance = appearance,
            onValueChange = { onOpacityChange(1f - it) },
            onValueChangeFinished = onSave,
        )
        CompactTuningRow(
            label = "模糊",
            valueText = "${blur.roundToInt()} dp",
            value = blur,
            range = 0f..24f,
            appearance = appearance,
            onValueChange = onBlurChange,
            onValueChangeFinished = onSave,
        )
        CompactTuningRow(
            label = "阅读遮罩",
            valueText = "${(scrim * 100f).roundToInt()}%",
            value = scrim,
            range = 0f..0.65f,
            appearance = appearance,
            onValueChange = onScrimChange,
            onValueChangeFinished = onSave,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (canClear) {
                SecondaryAction(
                    label = "清除",
                    icon = AppIconPaths.Trash,
                    appearance = appearance,
                    modifier = Modifier.weight(1f),
                    onClick = onClear,
                )
            }
            FullWidthAction(
                label = "完成",
                icon = emptyList(),
                appearance = appearance,
                modifier = Modifier.weight(1f),
                onClick = onDone,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CompactTuningRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    appearance: AppearanceTheme,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = appearance.mobileText,
                fontSize = 13.5.sp,
            )
            Text(valueText, color = appearance.mobileMuted, fontSize = 12.5.sp)
        }
        TunerSlider(
            value = value,
            range = range,
            appearance = appearance,
            onValueChange = onValueChange,
            onInteractionFinished = onValueChangeFinished,
        )
    }
}
