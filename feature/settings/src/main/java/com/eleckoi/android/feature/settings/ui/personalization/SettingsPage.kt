package com.eleckoi.android.feature.settings.ui.personalization

import com.eleckoi.android.feature.settings.ui.personalization.components.*

import com.eleckoi.android.foundation.design.components.noRippleClickable
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.PhosphorRegular
import com.eleckoi.android.feature.preferences.ListCharacterArtwork

@Composable
fun SettingsPage(
    appearance: AppearanceTheme,
    onBack: () -> Unit,
    onOpenUserProfile: () -> Unit,
    onOpenThemeStyle: () -> Unit,
    onOpenChatDisplay: () -> Unit,
    onOpenFont: () -> Unit,
    listCharacterArtwork: ListCharacterArtwork,
    onOpenListCharacterArtwork: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenLocalRuntime: () -> Unit,
    onOpenCrashDiagnostics: () -> Unit,
    onOpenAppUpdate: () -> Unit,
    appUpdateAvailable: Boolean,
    appUpdateLatestVersion: String,
    appUpdateChecking: Boolean,
    appUpdateCheckedOnce: Boolean,
    agentBackgroundProtectionEnabled: Boolean,
    onAgentBackgroundProtectionEnabledChange: (Boolean) -> Unit,
    onAgentBackgroundProtectionPermissionChanged: () -> Unit,
    backupBusy: Boolean = false,
    backupProgressText: String? = null,
    backupProgressFraction: Float? = null,
    backupCancellable: Boolean = false,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onCancelBackup: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayPermissionGranted by remember {
        mutableStateOf(AndroidSettings.canDrawOverlays(context))
    }
    var notificationsEnabled by remember {
        mutableStateOf(
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        )
    }
    var showPermissionExplanation by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayPermissionGranted = AndroidSettings.canDrawOverlays(context)
        onAgentBackgroundProtectionEnabledChange(overlayPermissionGranted)
        onAgentBackgroundProtectionPermissionChanged()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = AndroidSettings.canDrawOverlays(context)
                notificationsEnabled = context
                    .getSystemService(NotificationManager::class.java)
                    .areNotificationsEnabled()
                onAgentBackgroundProtectionPermissionChanged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val protectionSubtitle = when {
        agentBackgroundProtectionEnabled && !overlayPermissionGranted ->
            "需要授予悬浮窗权限才能生效"
        agentBackgroundProtectionEnabled -> "仅在 AI 生成或 Agent 执行时启用"
        else -> "防止部分国产系统冻结后台 Agent"
    }

    CompactSettingsScaffold(
        title = "设置",
        appearance = appearance,
        onBack = onBack,
    ) {
        SettingsSection(label = "个性化", appearance = appearance) {
            SettingsDestinationRow(
                iconPath = PhosphorRegular.UserCircle,
                title = "用户资料",
                appearance = appearance,
                onClick = onOpenUserProfile,
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                iconPath = PhosphorRegular.Palette,
                title = "主题风格",
                appearance = appearance,
                onClick = onOpenThemeStyle,
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                iconPath = PhosphorRegular.ChatCircle,
                title = "聊天显示",
                appearance = appearance,
                onClick = onOpenChatDisplay,
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                iconPath = PhosphorRegular.TextAa,
                title = "字体",
                appearance = appearance,
                onClick = onOpenFont,
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                icon = Icons.Outlined.Portrait,
                title = "列表角色图",
                value = when (listCharacterArtwork) {
                    ListCharacterArtwork.Cover -> "封面立绘"
                    ListCharacterArtwork.Avatar -> "头像"
                },
                appearance = appearance,
                onClick = onOpenListCharacterArtwork,
            )
        }
        SettingsSection(label = "后台运行", appearance = appearance) {
            SettingsDestinationRow(
                iconPath = PhosphorRegular.Bell,
                title = "系统通知",
                subtitle = if (notificationsEnabled) {
                    "已开启 · 显示生成状态和完成通知"
                } else {
                    "已关闭 · 点此到系统设置开启"
                },
                appearance = appearance,
                onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsToggleRow(
                iconPath = PhosphorRegular.Cpu,
                title = "增强后台运行",
                subtitle = protectionSubtitle,
                checked = agentBackgroundProtectionEnabled,
                appearance = appearance,
                onCheckedChange = { enabled ->
                    onAgentBackgroundProtectionEnabledChange(enabled)
                    if (enabled && !overlayPermissionGranted) {
                        showPermissionExplanation = true
                    }
                },
            )
        }
        SettingsSection(label = "数据备份", appearance = appearance) {
            if (backupProgressText != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = backupProgressText,
                            color = appearance.mobileMuted,
                            fontSize = 13.sp,
                        )
                        if (backupCancellable) {
                            Text(
                                text = "取消",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.noRippleClickable { onCancelBackup() }.padding(6.dp),
                            )
                        }
                    }
                    if (backupProgressFraction == null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = appearance.mobileBlue,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { backupProgressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = appearance.mobileBlue,
                        )
                    }
                }
                SettingsDivider(appearance, startIndent = 0.dp)
            }
            SettingsDestinationRow(
                iconPath = PhosphorRegular.UploadSimple,
                title = "导出数据备份",
                subtitle = "角色、预设、聊天、偏好和模型",
                appearance = appearance,
                onClick = onExportBackup,
                enabled = !backupBusy,
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                iconPath = PhosphorRegular.DownloadSimple,
                title = "导入数据备份",
                subtitle = "用于干净安装后的恢复",
                appearance = appearance,
                onClick = onImportBackup,
                enabled = !backupBusy,
            )
        }
        SettingsSection(label = "开发与更新", appearance = appearance) {
            SettingsDestinationRow(
                iconPath = PhosphorRegular.Cpu,
                title = "本地创作环境",
                subtitle = "Ubuntu · Agent Harness",
                appearance = appearance,
                onClick = onOpenLocalRuntime,
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                iconPath = PhosphorRegular.DownloadSimple,
                title = "故障诊断",
                subtitle = "崩溃日志与运行记录",
                appearance = appearance,
                onClick = onOpenCrashDiagnostics,
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                icon = if (appUpdateAvailable) {
                    Icons.Rounded.ErrorOutline
                } else {
                    Icons.Rounded.SystemUpdate
                },
                title = "应用更新",
                subtitle = when {
                    appUpdateChecking -> "正在检查 GitHub Release"
                    appUpdateAvailable -> "发现新版本 $appUpdateLatestVersion"
                    appUpdateCheckedOnce -> "当前已是最新版本"
                    else -> "检查新版本与更新提醒"
                },
                appearance = appearance,
                iconTint = if (appUpdateAvailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    appearance.mobileMuted
                },
                onClick = onOpenAppUpdate,
            )
        }
        SettingsSection(label = "社区与关于", appearance = appearance) {
            SettingsDestinationRow(
                icon = Icons.Rounded.Groups,
                title = "社区",
                subtitle = "ElecKoi 测试群",
                appearance = appearance,
                onClick = onOpenCommunity,
            )
            SettingsDivider(appearance, startIndent = SettingsRowTextStart)
            SettingsDestinationRow(
                icon = Icons.Rounded.Info,
                title = "关于电子爱",
                subtitle = "版本、GitHub 与开源许可证",
                appearance = appearance,
                onClick = onOpenAbout,
            )
        }
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = {
                showPermissionExplanation = false
                onAgentBackgroundProtectionEnabledChange(false)
            },
            containerColor = appearance.mobileSurface,
            title = {
                Text(
                    "允许增强后台运行？",
                    color = appearance.mobileText,
                    fontSize = 17.sp,
                )
            },
            text = {
                Text(
                    "部分国产系统会冻结后台 Agent。开启后，ElecKoi 只在 AI 生成或 " +
                        "Agent 执行期间创建一个不可见的 1×1 悬浮层，任务结束后立即移除。",
                    color = appearance.mobileMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Text(
                    "继续设置",
                    color = appearance.mobileBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .noRippleClickable {
                            showPermissionExplanation = false
                            permissionLauncher.launch(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                        .padding(12.dp),
                )
            },
            dismissButton = {
                Text(
                    "取消",
                    color = appearance.mobileMuted,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .noRippleClickable {
                            showPermissionExplanation = false
                            onAgentBackgroundProtectionEnabledChange(false)
                        }
                        .padding(12.dp),
                )
            },
        )
    }
}
