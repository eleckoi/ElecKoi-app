package com.eleckoi.android.feature.chat.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.AppSearchField
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.foundation.design.overlayScrim
import com.eleckoi.android.foundation.design.selectionPalette

@Composable
internal fun BottomLayer(appearance: AppearanceTheme, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(appearance.overlayScrim()).noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        content()
    }
}

@Composable
internal fun SheetHeader(title: String, subtitle: String, appearance: AppearanceTheme, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = appearance.mobileText, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = appearance.mobileMuted, fontSize = 12.sp)
        }
        Box(modifier = Modifier.size(40.dp).noRippleClickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            StrokeSvgIcon(AppIconPaths.X, appearance.mobileText, iconSize = 24.dp)
        }
    }
}

@Composable
internal fun SearchField(value: String, placeholder: String, appearance: AppearanceTheme, onChange: (String) -> Unit) {
    AppSearchField(
        keyword = value,
        placeholder = placeholder,
        appearance = appearance,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        onKeywordChange = onChange,
    )
}

@Composable
internal fun SelectRow(
    title: String,
    subtitle: String,
    active: Boolean,
    appearance: AppearanceTheme,
    leading: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val selection = appearance.selectionPalette()
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).background(if (active) selection.activeContainer else Color.Transparent).noRippleClickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f).padding(start = if (leading == null) 0.dp else 10.dp)) {
            Text(title, color = if (active) selection.activeText else selection.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = selection.mutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (active) Text("已选", color = selection.indicator, fontSize = 12.sp)
    }
}
