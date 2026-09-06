package com.eleckoi.android.feature.settings.ui.update

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eleckoi.android.feature.settings.ui.personalization.components.CompactSettingsScaffold
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsDestinationRow
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsDivider
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsRowTextStart
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsSection
import com.eleckoi.android.feature.settings.ui.personalization.components.SettingsToggleRow
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.PhosphorRegular
import android.provider.Settings as AndroidSettings

@Composable
fun AppUpdatePage(
    appearance: AppearanceTheme,
    installedVersion: String,
    latestVersion: String,
    releaseNotes: String,
    releasePageUrl: String,
    downloadState: AppUpdateDownloadUiState,
    updateAvailable: Boolean,
    remindersEnabled: Boolean,
    checking: Boolean,
    checkedOnce: Boolean,
    errorMessage: String,
    connectionState: GitHubConnectionUiState,
    onSaveConnection: (GitHubConnectionSettings) -> Unit,
    onTestConnections: () -> Unit,
    onTestConnectionDownloads: () -> Unit,
    onCancelConnectionTest: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onRemindersEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var connectionOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationPermissionGranted by remember {
        mutableStateOf(
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        )
    }
    val openNotificationSettings = {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
        if (!granted && context.findActivity()?.let { activity ->
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            } == true) {
            openNotificationSettings()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationPermissionGranted = context
                    .getSystemService(NotificationManager::class.java)
                    .areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (connectionOpen) {
        GitHubConnectionSheet(
            appearance = appearance,
            state = connectionState,
            onSave = onSaveConnection,
            onTest = onTestConnections,
            onTestDownloads = onTestConnectionDownloads,
            onCancelTest = onCancelConnectionTest,
            onDismiss = { connectionOpen = false },
        )
    }
    val errorColor = MaterialTheme.colorScheme.error
    val statusTitle = when {
        checking -> "正在检查更新"
        updateAvailable -> "版本 $latestVersion"
        checkedOnce -> "已经是最新版本"
        else -> "尚未检查更新"
    }
    val displayedNotes = remember(releaseNotes) {
        releaseNotes.trim().replace(Regex("\\r?\\n[\\t ]*(?:\\r?\\n)+"), "\n")
    }

    CompactSettingsScaffold(
        title = "应用更新",
        appearance = appearance,
        onBack = onBack,
    ) {
        Surface(
            color = appearance.mobileSurface,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusTitle,
                            color = appearance.mobileText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onRefresh, enabled = !checking) {
                            if (checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = appearance.mobileMuted,
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    "检查更新",
                                    tint = appearance.mobileMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "当前版本 $installedVersion",
                        color = appearance.mobileMuted,
                        fontSize = 12.sp,
                    )
                }
                if (updateAvailable) {
                    DownloadSection(
                        state = downloadState,
                        appearance = appearance,
                        onDownload = onDownload,
                        onCancelDownload = onCancelDownload,
                        onInstall = onInstall,
                    )
                }
                if (downloadState is AppUpdateDownloadUiState.Failed && downloadState.message.isNotBlank()) {
                    FailureMessage(
                        message = downloadState.message,
                        errorColor = errorColor,
                        onChangeConnection = { connectionOpen = true },
                    )
                }
                if (errorMessage.isNotBlank()) {
                    FailureMessage(
                        message = errorMessage,
                        errorColor = errorColor,
                        onChangeConnection = { connectionOpen = true },
                    )
                }
            }
        }

        if (updateAvailable && (releaseNotes.isNotBlank() || releasePageUrl.isNotBlank())) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "更新说明",
                        modifier = Modifier.weight(1f),
                        color = appearance.mobileText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (releasePageUrl.isNotBlank()) {
                        TextButton(onClick = {
                            val opened = runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releasePageUrl)))
                            }.isSuccess
                            if (!opened) {
                                Toast.makeText(context, "没有可打开页面的应用", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("查看发布页", color = appearance.mobileMuted, fontSize = 12.sp)
                        }
                    }
                }
                if (releaseNotes.isNotBlank()) {
                    Text(
                        text = displayedNotes,
                        color = appearance.mobileMuted,
                        fontSize = 13.sp,
                        lineHeight = 25.sp,
                    )
                }
            }
        }

        SettingsSection(label = "连接", appearance = appearance) {
            SettingsDestinationRow(
                icon = Icons.Rounded.Language,
                title = "GitHub 连接",
                subtitle = connectionState.settings.source.title,
                appearance = appearance,
                onClick = { connectionOpen = true },
            )
        }

        SettingsSection(label = "提醒", appearance = appearance) {
            SettingsToggleRow(
                iconPath = PhosphorRegular.Bell,
                title = "新版本提醒",
                subtitle = "每天检查一次，有更新时提醒",
                checked = remindersEnabled,
                appearance = appearance,
                onCheckedChange = onRemindersEnabledChange,
            )
            if (remindersEnabled && !notificationPermissionGranted) {
                SettingsDivider(appearance, startIndent = SettingsRowTextStart)
                SettingsDestinationRow(
                    icon = Icons.Rounded.NotificationsOff,
                    title = "允许系统通知",
                    subtitle = "当前只能显示 App 内更新提示",
                    appearance = appearance,
                    iconTint = errorColor,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        } else {
                            openNotificationSettings()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadSection(
    state: AppUpdateDownloadUiState,
    appearance: AppearanceTheme,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    when (state) {
        AppUpdateDownloadUiState.Unavailable -> Text(
            "当前 Release 暂无 ARM64 安装包",
            color = appearance.mobileMuted,
            fontSize = 13.sp,
        )
        is AppUpdateDownloadUiState.Available -> DownloadButton(
            text = "下载更新 · ${formatBytes(state.sizeBytes)}",
            appearance = appearance,
            onClick = onDownload,
        )
        is AppUpdateDownloadUiState.Failed -> DownloadButton(
            text = "重新下载 · ${formatBytes(state.sizeBytes)}",
            appearance = appearance,
            onClick = onDownload,
        )
        AppUpdateDownloadUiState.Ready -> DownloadButton(
            text = "安装更新",
            appearance = appearance,
            onClick = onInstall,
        )
        is AppUpdateDownloadUiState.Downloading, AppUpdateDownloadUiState.Verifying -> {
            val progress = state as? AppUpdateDownloadUiState.Downloading
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (progress == null) "正在检查安装包…" else
                            downloadProgressText(progress.downloadedBytes, progress.totalBytes),
                        modifier = Modifier.weight(1f),
                        color = appearance.mobileMuted,
                        fontSize = 13.sp,
                    )
                    if (progress != null) {
                        TextButton(onClick = onCancelDownload) {
                            Text("取消", color = appearance.mobileMuted)
                        }
                    }
                }
                if (progress != null) {
                    Text(
                        downloadSpeedText(progress.bytesPerSecond, progress.downloadedBytes, progress.totalBytes),
                        color = appearance.mobileMuted,
                        fontSize = 12.sp,
                    )
                }
                if (progress == null || progress.totalBytes <= 0L) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = appearance.mobileBlue,
                        trackColor = appearance.mobileLine,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = {
                            (progress.downloadedBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = appearance.mobileBlue,
                        trackColor = appearance.mobileLine,
                    )
                }
            }
        }
    }
}

@Composable
private fun FailureMessage(
    message: String,
    errorColor: androidx.compose.ui.graphics.Color,
    onChangeConnection: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = errorColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onChangeConnection) {
            Text("更换线路", color = errorColor, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DownloadButton(text: String, appearance: AppearanceTheme, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = appearance.mobileText.copy(alpha = 0.06f),
            contentColor = appearance.mobileText,
        ),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun downloadProgressText(downloadedBytes: Long, totalBytes: Long): String =
    if (totalBytes > 0L) {
        "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
    } else {
        "正在等待 GitHub 响应"
    }

private fun downloadSpeedText(speed: Long, downloaded: Long, total: Long): String {
    if (speed <= 0L) return "正在等待数据"
    val remainingSeconds = ((total - downloaded).coerceAtLeast(0L) + speed - 1L) / speed
    return "${formatBytes(speed)}/秒 · 预计剩余 ${formatDuration(remainingSeconds)}"
}

private fun formatDuration(seconds: Long): String = when {
    seconds >= 60L -> "${seconds / 60} 分 ${seconds % 60} 秒"
    else -> "$seconds 秒"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
