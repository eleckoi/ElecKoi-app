package com.eleckoi.android.feature.characters.presets.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme

internal enum class AgentPresetEditorTab(val label: String) {
    Usage("使用说明"),
    Prompts("预设提示词"),
    Tools("工具"),
    Regex("预设正则"),
}

@Composable
internal fun AgentPresetEditorTabBar(
    selected: AgentPresetEditorTab,
    appearance: AppearanceTheme,
    onSelect: (AgentPresetEditorTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(appearance.mobileBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .selectableGroup()
                .padding(horizontal = 8.dp),
        ) {
            AgentPresetEditorTab.entries.forEach { tab ->
                val active = selected == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label,
                        color = if (active) appearance.mobileText else appearance.mobileMuted,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(28.dp)
                                .height(2.dp)
                                .background(appearance.mobileBlue, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = appearance.mobileText.copy(alpha = 0.07f))
    }
}
