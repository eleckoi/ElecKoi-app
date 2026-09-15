package com.eleckoi.android.feature.settings.ui.personalization.artwork

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.preferences.ListCharacterArtwork
import com.eleckoi.android.feature.settings.ui.personalization.components.CompactSettingsScaffold
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsDivider
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsRowPadding
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsSection
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsSelectionCheck
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
fun ListCharacterArtworkSettingsPage(
    appearance: AppearanceTheme,
    selectedArtwork: ListCharacterArtwork,
    onArtworkChange: (ListCharacterArtwork) -> Unit,
    onBack: () -> Unit,
) {
    CompactSettingsScaffold(
        title = "列表角色图",
        appearance = appearance,
        onBack = onBack,
    ) {
        SettingsSection(label = "显示方式", appearance = appearance) {
            ArtworkChoiceRow(
                title = "封面立绘",
                selected = selectedArtwork == ListCharacterArtwork.Cover,
                appearance = appearance,
                onClick = { onArtworkChange(ListCharacterArtwork.Cover) },
            )
            SettingsDivider(appearance, startIndent = SettingsRowPadding)
            ArtworkChoiceRow(
                title = "头像",
                selected = selectedArtwork == ListCharacterArtwork.Avatar,
                appearance = appearance,
                onClick = { onArtworkChange(ListCharacterArtwork.Avatar) },
            )
        }
    }
}

@Composable
private fun ArtworkChoiceRow(
    title: String,
    selected: Boolean,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = SettingsRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = appearance.mobileText,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
        SettingsSelectionCheck(
            selected = selected,
            appearance = appearance,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
