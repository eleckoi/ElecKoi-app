package com.eleckoi.android.feature.modelconfig.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eleckoi.android.engine.generation.config.ModelConfigCollection
import com.eleckoi.android.feature.modelconfig.api.ModelService
import com.eleckoi.android.engine.generation.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ModelsUiState(
    val models: ModelConfigCollection? = null,
    val errorMessage: String = "",
)

sealed interface ModelsIntent {
    data class SaveModelConfig(val config: ModelConfig) : ModelsIntent
    data class DeleteModelConfig(val configId: String) : ModelsIntent
    class FetchModelOptions(val config: ModelConfig, val onResult: (Result<ModelConfig>) -> Unit) : ModelsIntent
    class TestModelConnection(val config: ModelConfig, val onResult: (Result<Unit>) -> Unit) : ModelsIntent
}

class ModelsViewModel(
    private val modelService: ModelService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()
    init {
        observeModels()
    }

    fun onIntent(intent: ModelsIntent) {
        when (intent) {
            is ModelsIntent.SaveModelConfig -> saveModelConfig(intent.config)
            is ModelsIntent.DeleteModelConfig -> deleteModelConfig(intent.configId)
            is ModelsIntent.FetchModelOptions -> fetchModelOptions(intent.config, intent.onResult)
            is ModelsIntent.TestModelConnection -> testModelConnection(intent.config, intent.onResult)
        }
    }

    private fun observeModels() {
        viewModelScope.launch {
            modelService.modelCollectionFlow
                .catch { error -> _uiState.update { it.copy(errorMessage = error.message ?: "加载模型配置失败") } }
                .collectLatest { models ->
                    _uiState.update { it.copy(models = models, errorMessage = "") }
                }
        }
    }

    fun createDraftTarget(providerId: String): ModelTarget {
        return modelService.blankModelConfig(providerId).toDraftModelTarget()
    }

    fun saveModelConfig(
        config: ModelConfig,
        onResult: (Result<ModelConfig>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { modelService.saveModelConfig(config) }
            }
            result.onSuccess {
                _uiState.update { it.copy(errorMessage = "") }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "保存模型配置失败") }
            }
            onResult(result)
        }
    }

    fun deleteModelConfig(configId: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { modelService.deleteModelConfig(configId) }
            }.onSuccess {
                _uiState.update { it.copy(errorMessage = "") }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "删除模型配置失败") }
            }
        }
    }

    fun fetchModelOptions(config: ModelConfig, onResult: (Result<ModelConfig>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { modelService.fetchModelOptions(config) }
            }
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "拉取模型列表失败") }
            }
            onResult(result)
        }
    }

    fun testModelConnection(config: ModelConfig, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    modelService.testModelConnection(config)
                }
            }
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "测试模型连接失败") }
            }
            onResult(result)
        }
    }

    companion object {
        fun factory(modelService: ModelService): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ModelsViewModel::class.java)) {
                        return ModelsViewModel(modelService) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
