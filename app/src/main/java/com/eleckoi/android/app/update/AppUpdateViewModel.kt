package com.eleckoi.android.app.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.feature.settings.ui.update.AppUpdateDownloadUiState
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0L,
    ) :
        AppUpdateDownloadState
    data object Verifying : AppUpdateDownloadState
    data class Ready(val file: File, val release: AppRelease) : AppUpdateDownloadState
    data class Failed(val message: String) : AppUpdateDownloadState
}

internal data class AppUpdateUiState(
    val installedVersion: String,
    val latestRelease: AppRelease? = null,
    val remindersEnabled: Boolean = true,
    val checking: Boolean = false,
    val checkedOnce: Boolean = false,
    val errorMessage: String = "",
    val downloadState: AppUpdateDownloadState = AppUpdateDownloadState.Idle,
) {
    val updateAvailable: Boolean
        get() = latestRelease?.let { AppVersion.isNewer(it.tagName, installedVersion) } == true

    val latestVersion: String
        get() = latestRelease?.tagName?.let(AppVersion::display).orEmpty()

    val downloadUiState: AppUpdateDownloadUiState
        get() = when (val state = downloadState) {
            AppUpdateDownloadState.Idle -> latestRelease?.apk?.let {
                AppUpdateDownloadUiState.Available(it.sizeBytes)
            } ?: AppUpdateDownloadUiState.Unavailable
            is AppUpdateDownloadState.Downloading -> AppUpdateDownloadUiState.Downloading(
                state.downloadedBytes,
                state.totalBytes,
                state.bytesPerSecond,
            )
            AppUpdateDownloadState.Verifying -> AppUpdateDownloadUiState.Verifying
            is AppUpdateDownloadState.Ready -> AppUpdateDownloadUiState.Ready
            is AppUpdateDownloadState.Failed -> AppUpdateDownloadUiState.Failed(
                latestRelease?.apk?.sizeBytes ?: 0L,
                state.message,
            )
        }
}

internal class AppUpdateViewModel(
    private val repository: AppUpdateRepository,
    private val installedVersion: String,
    private val scheduler: AppUpdateScheduler,
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val updateDirectory = File(context.applicationContext.filesDir, "app_updates")
    private val downloadService = AppUpdateDownloadService(updateDirectory)
    private val installer = AppUpdateInstaller(context)
    val connection = AppUpdateConnectionController(viewModelScope, repository)
    private var downloadJob: Job? = null
    private val _uiState = MutableStateFlow(AppUpdateUiState(installedVersion = installedVersion))
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                val previousRelease = _uiState.value.latestRelease
                val releaseChanged = previousRelease != null &&
                    !previousRelease.hasSameDownload(snapshot.latestRelease)
                if (releaseChanged) cancelDownload()
                _uiState.update { state ->
                    state.copy(
                        latestRelease = snapshot.latestRelease,
                        remindersEnabled = snapshot.remindersEnabled,
                        checkedOnce = snapshot.lastCheckedAtMillis > 0L,
                    )
                }
                restoreReady(snapshot)
            }
        }
        viewModelScope.launch {
            val snapshot = repository.current()
            scheduler.setEnabled(snapshot.remindersEnabled)
            val stale = nowMillis() - snapshot.lastCheckedAtMillis >= ForegroundRefreshIntervalMillis
            if (snapshot.lastCheckedAtMillis == 0L || stale) refresh()
        }
    }

    fun download() {
        val release = _uiState.value.latestRelease ?: return
        val apk = release.apk ?: return
        when (_uiState.value.downloadState) {
            AppUpdateDownloadState.Idle, is AppUpdateDownloadState.Failed -> Unit
            else -> return
        }
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val operation = currentCoroutineContext()
            try {
                _uiState.update {
                    it.copy(
                        downloadState = AppUpdateDownloadState.Downloading(0L, apk.sizeBytes),
                        errorMessage = "",
                    )
                }
                val file = downloadService.download(
                    apk = apk,
                    fileStem = fileStem(release),
                    connection = repository.current().connection,
                    onProgress = { downloadedBytes, bytesPerSecond ->
                        updateDownloadState(
                            operation,
                            release,
                            AppUpdateDownloadState.Downloading(downloadedBytes, apk.sizeBytes, bytesPerSecond),
                        )
                    },
                    verify = { file ->
                        updateDownloadState(operation, release, AppUpdateDownloadState.Verifying)
                        installer.verify(file, apk)
                    },
                )
                updateDownloadState(operation, release, AppUpdateDownloadState.Ready(file, release))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateDownloadState(
                    operation,
                    release,
                    AppUpdateDownloadState.Failed(error.message ?: "下载更新失败"),
                )
            }
        }
    }

    private suspend fun restoreReady(snapshot: AppUpdateSnapshot) {
        val release = snapshot.latestRelease ?: return
        val apk = release.apk ?: return
        val expected = File(downloadServiceDirectory(), "${fileStem(release)}.apk")
        downloadServiceDirectory().listFiles()?.filter {
            it.isFile && it.name.startsWith("eleckoi-") && it != expected
        }?.forEach(File::delete)
        runCatching { installer.verify(expected, apk) }
            .onSuccess {
                _uiState.update { state ->
                    if (state.latestRelease?.hasSameDownload(release) == true &&
                        state.downloadState is AppUpdateDownloadState.Idle
                    ) state.copy(downloadState = AppUpdateDownloadState.Ready(expected, release)) else state
                }
            }
            .onFailure { expected.delete() }
        cleanupPartialFiles()
    }

    private fun downloadServiceDirectory(): File = updateDirectory

    private fun cleanupPartialFiles() {
        downloadServiceDirectory().listFiles()?.filter { it.name.startsWith("eleckoi-") && it.name.endsWith(".part") }
            ?.forEach(File::delete)
    }

    private fun fileStem(release: AppRelease): String =
        "eleckoi-${release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")}"

    private fun updateDownloadState(
        operation: CoroutineContext,
        release: AppRelease,
        downloadState: AppUpdateDownloadState,
    ) {
        _uiState.update { state ->
            if (operation.isActive && release.hasSameDownload(state.latestRelease)) {
                state.copy(downloadState = downloadState)
            } else {
                state
            }
        }
    }

    fun cancelDownload() {
        val ready = _uiState.value.downloadState as? AppUpdateDownloadState.Ready
        downloadJob?.cancel()
        _uiState.update { it.copy(downloadState = AppUpdateDownloadState.Idle, errorMessage = "") }
        if (ready != null) viewModelScope.launch { downloadService.discard(ready.file) }
    }

    fun install() {
        val ready = _uiState.value.downloadState as? AppUpdateDownloadState.Ready ?: return
        if (downloadJob?.isActive == true) return
        if (!ready.release.hasSameDownload(_uiState.value.latestRelease)) return
        if (!ready.file.isFile) {
            _uiState.update {
                it.copy(downloadState = AppUpdateDownloadState.Failed("安装包已清理，请重新下载"))
            }
            return
        }
        _uiState.update { it.copy(errorMessage = "") }
        try {
            if (installer.canRequestPackageInstalls()) {
                installer.install(ready.file)
            } else {
                installer.openInstallPermissionSettings()
                _uiState.update { it.copy(errorMessage = "请允许 ElecKoi 安装应用后，再点击安装更新") }
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(errorMessage = error.message ?: "无法打开系统安装页面，请重试") }
        }
    }

    fun refresh() {
        if (_uiState.value.checking) return
        viewModelScope.launch {
            _uiState.update { it.copy(checking = true, errorMessage = "") }
            runCatching { repository.checkForUpdate() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = error.message?.trim().orEmpty()
                                .ifBlank { "暂时无法连接 GitHub" },
                        )
                    }
                }
            _uiState.update { it.copy(checking = false) }
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRemindersEnabled(enabled)
            scheduler.setEnabled(enabled)
        }
    }

    companion object {
        private val ForegroundRefreshIntervalMillis = TimeUnit.HOURS.toMillis(6)

        fun factory(
            repository: AppUpdateRepository,
            installedVersion: String,
            scheduler: AppUpdateScheduler,
            context: Context,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppUpdateViewModel(repository, installedVersion, scheduler, context) as T
        }
    }
}
