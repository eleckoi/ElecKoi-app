package com.eleckoi.android.app.update

import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionSettings
import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionSource
import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionTestMode
import com.eleckoi.android.feature.settings.ui.update.GitHubConnectionUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class AppUpdateConnectionController(
    private val scope: CoroutineScope,
    private val repository: AppUpdateRepository,
    private val probe: GitHubConnectionProbe = GitHubConnectionProbe(),
) {
    private val mutableState = MutableStateFlow(GitHubConnectionUiState())
    val state = mutableState.asStateFlow()
    private var testJob: Job? = null

    init {
        scope.launch {
            repository.snapshot.collect { snapshot ->
                mutableState.update {
                    it.copy(
                        settings = snapshot.connection,
                        results = if (it.settings.customPrefix == snapshot.connection.customPrefix) {
                            it.results
                        } else it.results - GitHubConnectionSource.Custom,
                    )
                }
            }
        }
    }

    fun save(settings: GitHubConnectionSettings) {
        if (mutableState.value.saving) return
        cancelTest()
        mutableState.update { it.copy(saving = true, error = "") }
        scope.launch {
            try {
                repository.setConnection(settings)
                val saved = repository.current().connection
                mutableState.update {
                    it.copy(
                        settings = saved,
                        results = if (saved.customPrefix == it.settings.customPrefix) it.results
                        else it.results - GitHubConnectionSource.Custom,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "保存连接设置失败") }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    fun testApi() {
        if (mutableState.value.testing || mutableState.value.saving) return
        startTest(GitHubConnectionTestMode.Api) {
            val snapshot = repository.current()
            kotlinx.coroutines.coroutineScope {
                    GitHubConnectionSource.entries.map { source ->
                        launch {
                            if (source == GitHubConnectionSource.Custom && snapshot.connection.customPrefix.isBlank()) {
                                result(source, "保存自定义地址后可测速")
                            } else result(source, probe.measureApi(snapshot.connection.copy(source = source)))
                        }
                    }.forEach { it.join() }
            }
        }
    }

    fun testDownloads() {
        if (mutableState.value.testing || mutableState.value.saving) return
        startTest(GitHubConnectionTestMode.Download) {
            val snapshot = repository.current()
            for (source in GitHubConnectionSource.entries) {
                    currentCoroutineContext().ensureActive()
                    if (source == GitHubConnectionSource.Custom && snapshot.connection.customPrefix.isBlank()) {
                        result(source, "保存自定义地址后可测速")
                    } else {
                        result(source, "下载测速中…")
                        result(source, probe.measureDownload(snapshot.connection.copy(source = source), snapshot.latestRelease?.apk))
                    }
            }
        }
    }

    private fun startTest(mode: GitHubConnectionTestMode, block: suspend () -> Unit) {
        mutableState.update { it.copy(testMode = mode, results = emptyMap(), error = "") }
        testJob = scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(error = "测速失败，请重试") }
            } finally {
                mutableState.update { it.copy(testMode = GitHubConnectionTestMode.Idle) }
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        mutableState.update { state ->
            state.copy(testMode = GitHubConnectionTestMode.Idle, results = state.results.mapValues { (_, value) ->
                if (value.contains("测速中")) "测速已取消" else value
            })
        }
    }

    private fun result(source: GitHubConnectionSource, value: String) {
        mutableState.update { it.copy(results = it.results + (source to value)) }
    }
}
