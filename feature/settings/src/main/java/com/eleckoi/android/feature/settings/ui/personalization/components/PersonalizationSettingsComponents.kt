package com.eleckoi.android.feature.settings.ui.personalization.components

import com.eleckoi.android.foundation.design.components.noRippleClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.AppSwitch
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.QuietBackButton
import com.eleckoi.android.foundation.design.components.SquareSelectionCheck
import com.eleckoi.android.foundation.design.AppearanceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactSettingsScaffold(
    title: String,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = appearance.mobileBg,
        topBar = {
            SettingsLargeTitleBar(
                title = title,
                appearance = appearance,
                onBack = onBack,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(
                    if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                )
                .padding(horizontal = 14.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            content()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// iOS-style large title: the bar sits on the page background instead of its own white slab,
// so the header reads as part of the page rather than a separate chrome layer.
@Composable
private fun SettingsLargeTitleBar(
    title: String,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appearance.mobileBg)
            .statusBarsPadding()
            .padding(start = 6.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuietBackButton(
            color = appearance.mobileText.copy(alpha = 0.84f),
            onClick = onBack,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp),
            color = appearance.mobileText,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    title: String,
    appearance: AppearanceTheme,
    onBack: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                color = appearance.mobileText,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = appearance.mobileText,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = appearance.mobileSurface,
            scrolledContainerColor = appearance.mobileSurface,
        ),
    )
}

@Composable
private fun SettingsIntro(
    title: String,
    description: String,
    icon: ImageVector,
    appearance: AppearanceTheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = appearance.mobileBlue,
            modifier = Modifier.size(23.dp),
        )
        Column(modifier = Modifier.padding(start = 9.dp)) {
            Text(
                text = title,
                color = appearance.mobileBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = appearance.mobileMuted,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    appearance: AppearanceTheme,
    shape: Shape = RoundedCornerShape(20.dp),
    showBorder: Boolean = true,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showBorder) Modifier.border(1.dp, appearance.mobileLine, shape) else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = appearance.mobileSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(content = { content() })
    }
}

internal val SettingsRowPadding = 14.dp
internal val SettingsRowIconSize = 21.dp
internal val SettingsRowIconGap = 12.dp

// Divider starts where the row text starts, so every label lines up on one optical baseline.
internal val SettingsRowTextStart =
    SettingsRowPadding + SettingsRowIconSize + SettingsRowIconGap

@Composable
internal fun SettingsSelectionCheck(
    selected: Boolean,
    appearance: AppearanceTheme,
    modifier: Modifier = Modifier,
) {
    SquareSelectionCheck(selected = selected, appearance = appearance, modifier = modifier)
}

@Composable
internal fun SettingsSection(
    label: String,
    appearance: AppearanceTheme,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = appearance.mobileMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        SettingsGroup(
            appearance = appearance,
            shape = RoundedCornerShape(14.dp),
            showBorder = false,
            content = content,
        )
    }
}

@Composable
internal fun SettingsDestinationRow(
    iconPath: String,
    iconViewportSize: Float = 256f,
    title: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    iconTint: Color = appearance.mobileMuted,
) {
    SettingsDestinationRowContent(
        icon = {
            FilledSvgIcon(
                paths = listOf(iconPath),
                color = iconTint,
                iconSize = SettingsRowIconSize,
                viewportSize = iconViewportSize,
            )
        },
        title = title,
        subtitle = subtitle,
        value = value,
        appearance = appearance,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
internal fun SettingsDestinationRow(
    icon: ImageVector,
    title: String,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    iconTint: Color = appearance.mobileMuted,
) {
    SettingsDestinationRowContent(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(SettingsRowIconSize),
            )
        },
        title = title,
        subtitle = subtitle,
        value = value,
        appearance = appearance,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
private fun SettingsDestinationRowContent(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    value: String?,
    appearance: AppearanceTheme,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .then(if (enabled) Modifier else Modifier.alpha(0.5f))
            .padding(SettingsRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = SettingsRowIconGap),
        ) {
            Text(
                text = title,
                color = appearance.mobileText,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = appearance.mobileSoft,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                color = appearance.mobileSoft,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = appearance.mobileSoft,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    iconPath: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    appearance: AppearanceTheme,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(SettingsRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledSvgIcon(
            paths = listOf(iconPath),
            color = appearance.mobileMuted,
            iconSize = SettingsRowIconSize,
            viewportSize = 256f,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = SettingsRowIconGap, end = 12.dp),
        ) {
            Text(
                text = title,
                color = appearance.mobileText,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = subtitle,
                color = appearance.mobileSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = null,
            appearance = appearance,
        )
    }
}

@Composable
internal fun SettingsDivider(appearance: AppearanceTheme, startIndent: Dp = 64.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        color = appearance.mobileLine,
    )
}
