package com.eleckoi.android.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
internal fun AppUpdatePromptDialog(
    appearance: AppearanceTheme,
    installedVersion: String,
    latestVersion: String,
    releaseNotes: String,
    onDismiss: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appearance.mobileSurface,
        icon = {
            Icon(
                imageVector = Icons.Rounded.NewReleases,
                contentDescription = null,
                tint = appearance.mobileBlue,
            )
        },
        title = {
            Text(
                text = "发现新版本",
                color = appearance.mobileText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    text = "ElecKoi $latestVersion",
                    color = appearance.mobileText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "当前版本 $installedVersion",
                    color = appearance.mobileMuted,
                    fontSize = 13.sp,
                )
                if (releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = releaseNotes.take(MaxReleaseNotesLength),
                        color = appearance.mobileMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenUpdate) {
                Text("查看更新", color = appearance.mobileBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后", color = appearance.mobileMuted)
            }
        },
    )
}

internal fun shouldShowAppUpdatePrompt(
    remindersEnabled: Boolean,
    updateAvailable: Boolean,
    latestTag: String,
    dismissedTag: String,
): Boolean = remindersEnabled && updateAvailable && latestTag.isNotBlank() && latestTag != dismissedTag

private const val MaxReleaseNotesLength = 500
