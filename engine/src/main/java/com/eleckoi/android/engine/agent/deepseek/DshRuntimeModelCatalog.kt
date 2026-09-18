package com.eleckoi.android.engine.agent.deepseek

import com.eleckoi.android.engine.generation.model.ModelConfig

/** Builds the complete process catalog while letting the active turn override its stored copies. */
internal object DshRuntimeModelCatalog {
    fun merge(
        stored: List<ModelConfig>,
        selected: List<ModelConfig>,
    ): List<ModelConfig> {
        val byId = linkedMapOf<String, ModelConfig>()
        stored
            .filter { config -> config.id.isNotBlank() && config.model.isNotBlank() }
            .forEach { config -> byId[config.id] = config }
        selected.forEach { config ->
            require(config.id.isNotBlank()) { "模型配置编号不能为空" }
            require(config.model.isNotBlank()) { "模型配置缺少模型名" }
            byId[config.id] = config
        }
        return byId.values.toList().also { require(it.isNotEmpty()) { "没有可用的模型配置" } }
    }
}
