package com.eleckoi.android.feature.modelconfig.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.overlayScrim

val ModelApiFormat.displayName: String
    get() = when (this) {
        ModelApiFormat.ChatCompletions -> "Chat Completions"
        ModelApiFormat.Responses -> "Responses API"
        ModelApiFormat.AnthropicMessages -> "Anthropic Messages"
        ModelApiFormat.GoogleGemini -> "Generate Content API"
    }

@Composable
fun ModelApiFormatSheet(
    selected: ModelApiFormat?,
    appearance: AppearanceTheme,
    inherited: ModelApiFormat? = null,
    allowInherited: Boolean = false,
    formats: List<ModelApiFormat> = ModelApiFormat.entries,
    onClose: () -> Unit,
    onSelect: (ModelApiFormat?) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.overlayScrim())
            .noRippleClickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(appearance.mobileSurface)
                .navigationBarsPadding()
                .noRippleClickable {},
        ) {
            Text(
                "接口格式",
                color = appearance.mobileText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            )
            if (allowInherited) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { onSelect(null) }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "跟随连接${inherited?.let { " · ${it.displayName}" }.orEmpty()}",
                        modifier = Modifier.weight(1f),
                        color = appearance.mobileText,
                        fontSize = 15.sp,
                    )
                    if (selected == null) {
                        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            StrokeSvgIcon(AppIconPaths.Check, appearance.mobileBlue, iconSize = 17.dp)
                        }
                    }
                }
            }
            formats.forEach { format ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { onSelect(format) }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        format.displayName,
                        modifier = Modifier.weight(1f),
                        color = appearance.mobileText,
                        fontSize = 15.sp,
                    )
                    if (format == selected) {
                        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            StrokeSvgIcon(AppIconPaths.Check, appearance.mobileBlue, iconSize = 17.dp)
                        }
                    }
                }
            }
        }
    }
}

internal fun apiFormatsForProvider(providerId: String): List<ModelApiFormat> =
    if (providerId.trim().equals("deepseek", ignoreCase = true)) {
        listOf(
            ModelApiFormat.ChatCompletions,
            ModelApiFormat.Responses,
            ModelApiFormat.AnthropicMessages,
        )
    } else {
        ModelApiFormat.entries
    }
