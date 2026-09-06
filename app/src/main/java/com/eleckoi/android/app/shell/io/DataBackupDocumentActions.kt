package com.eleckoi.android.app.shell

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.eleckoi.android.app.service.backup.BackupProgress
import com.eleckoi.android.app.service.backup.DataBackupService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class DataBackupActions(
    val export: () -> Unit,
    val import: () -> Unit,
    val busy: State<Boolean>,
    val progress: State<BackupProgress?>,
    val cancel: () -> Unit,
)

@Composable
internal fun rememberDataBackupActions(service: DataBackupService): DataBackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentService by rememberUpdatedState(service)
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<BackupProgress?>(null) }
    var operationJob by remember { mutableStateOf<Job?>(null) }
    val busyState = rememberUpdatedState(busy)
    val progressState = rememberUpdatedState(progress)
    val operationJobState = rememberUpdatedState(operationJob)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        operationJob = scope.launch {
            busy = true
            progress = null
            try {
                val result = currentService.exportTo(uri) { progress = it }
                android.widget.Toast.makeText(
                    context,
                    "备份已导出（${result.characters} 个角色，${result.creatorWorkspaces} 个助手项目）",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            } catch (_: CancellationException) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                showCancelled(context)
            } catch (error: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                showFailure(context, error)
            } finally {
                busy = false
                progress = null
                operationJob = null
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        operationJob = scope.launch {
            busy = true
            progress = null
            try {
                val result = currentService.importFrom(uri) { progress = it }
                android.widget.Toast.makeText(
                    context,
                    "备份已导入（${result.characters} 个角色，${result.creatorWorkspaces} 个助手项目）",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            } catch (_: CancellationException) {
                showCancelled(context)
            } catch (error: Throwable) {
                showFailure(context, error)
            } finally {
                busy = false
                progress = null
                operationJob = null
            }
        }
    }
    return remember(exportLauncher, importLauncher, busyState, progressState, operationJobState) {
        DataBackupActions(
            export = {
                if (!busyState.value) exportLauncher.launch("ElecKoi-数据备份.zip")
            },
            import = {
                if (!busyState.value) importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            },
            busy = busyState,
            progress = progressState,
            cancel = {
                if (progressState.value?.cancellable == true) operationJobState.value?.cancel()
            },
        )
    }
}

private fun showCancelled(context: Context) {
    android.widget.Toast.makeText(context, "备份操作已取消", android.widget.Toast.LENGTH_SHORT).show()
}

private fun showFailure(context: Context, error: Throwable) {
    android.widget.Toast.makeText(
        context,
        "备份失败：${error.message.orEmpty().ifBlank { "未知错误" }}",
        android.widget.Toast.LENGTH_LONG,
    ).show()
}
