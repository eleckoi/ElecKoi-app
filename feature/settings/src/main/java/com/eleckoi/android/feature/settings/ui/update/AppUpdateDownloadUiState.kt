package com.eleckoi.android.feature.settings.ui.update

sealed interface AppUpdateDownloadUiState {
    data object Unavailable : AppUpdateDownloadUiState
    data class Available(val sizeBytes: Long) : AppUpdateDownloadUiState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0L,
    ) : AppUpdateDownloadUiState
    data object Verifying : AppUpdateDownloadUiState
    data object Ready : AppUpdateDownloadUiState
    data class Failed(val sizeBytes: Long, val message: String) : AppUpdateDownloadUiState
}
