package com.eleckoi.android.engine.generation.reasoning

import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.configuredContextWindowTokens
import com.eleckoi.android.engine.generation.model.resolvedProviderBaseUrl
import com.eleckoi.android.engine.generation.model.supportsImageInput
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds the literal provider dictionary consumed by DSH's official `dsh-llm-pi-ai` plugin. */
internal object DshPiAiProviderCatalog {
    fun providerRoute(config: ModelConfig): String {
        val catalog = DshModelCapabilities.piAiCatalogProvider(config)
        if (catalog != "eleckoi-custom") return catalog
        val identity = config.id.trim().ifBlank {
            listOf(
                config.provider.trim(),
                config.effectiveDshApi().wireValue,
                config.baseUrl.trim(),
                config.model.trim(),
            ).joinToString("\u0000")
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "eleckoi-custom-$digest"
    }

    /**
     * Resolves a process-wide route without forcing two independent connections into one DSH
     * provider profile. A native catalog name is retained only when that name identifies exactly
     * one active connection; otherwise every connection receives a deterministic alias, matching
     * the PC catalog's connection-group boundary.
     */
    fun providerRoute(config: ModelConfig, activeConfigs: List<ModelConfig>): String {
        val direct = providerRoute(config)
        if (
            direct == "google" ||
            direct.startsWith("eleckoi-custom-") ||
            config.usesDshDeepSeekOfficialRoute()
        ) {
            return direct
        }
        val identities = activeConfigs
            .filterNot(ModelConfig::usesDshDeepSeekOfficialRoute)
            .filter { candidate -> providerRoute(candidate) == direct }
            .map(::connectionIdentity)
            .distinct()
        if (identities.size <= 1) return direct
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(connectionIdentity(config).toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "$direct-eleckoi-$digest"
    }

    fun runtimeModelId(config: ModelConfig): String {
        val model = config.model.trim()
        return if (config.effectiveDshApi() == DshProviderApi.Google) {
            model.replace(Regex("^models/", RegexOption.IGNORE_CASE), "")
        } else {
            model
        }
    }

    fun providersJson(configs: List<ModelConfig>, providerBaseUrl: String): String {
        require(ProviderBaseUrl.matches(providerBaseUrl)) { "DSH pi-ai Provider 地址不是安全的本机路由" }
        val generic = configs.filterNot(ModelConfig::usesDshDeepSeekOfficialRoute)
        val grouped = generic.groupBy { config -> providerRoute(config, generic) }
        return buildJsonObject {
            grouped.toSortedMap().forEach { (route, routeConfigs) ->
                val apis = routeConfigs.map(ModelConfig::effectiveDshApi).distinct()
                require(apis.size == 1) {
                    "同一 DSH Provider 路由不能同时使用多种接口格式：$route"
                }
                val api = apis.single()
                if (api == DshProviderApi.Google) {
                    require(route == "google") {
                        "Google Generative AI 只能使用 DSH/pi-ai 原生 google 路由"
                    }
                }
                val inheritsCatalog = !route.startsWith("eleckoi-custom-") &&
                    routeConfigs.all { config -> providerRoute(config) == route }
                put(route, providerProfile(route, api, routeConfigs, providerBaseUrl, inheritsCatalog))
            }
        }.toString()
    }

    private fun providerProfile(
        route: String,
        api: DshProviderApi,
        configs: List<ModelConfig>,
        providerBaseUrl: String,
        inheritsCatalog: Boolean,
    ): JsonObject = buildJsonObject {
        put("displayName", "ElecKoi Android ${route}")
        put("apiKeyEnv", "ELECKOI_PROVIDER_KEY")
        if (api != DshProviderApi.Google) put("api", api.wireValue)
        put("baseURL", wireBaseUrl(providerBaseUrl, api))
        put("streamIdleTimeoutMs", 300_000)
        put("maxRequestImageBytes", 12_582_912)
        put("requestImagePixelBudget", 4_194_304)
        put("requestImageMaxBytes", 1_048_576)
        if (!inheritsCatalog) {
            put("defaultContextWindow", configs.first().configuredContextWindowTokens())
            put("defaultMaxTokens", 32_768)
            put("defaultInput", buildJsonArray { add(JsonPrimitive("text")) })
        }
        put("models", modelProfiles(route, configs, inheritsCatalog))
    }

    private fun modelProfiles(
        route: String,
        configs: List<ModelConfig>,
        inheritsCatalog: Boolean,
    ): JsonArray {
        val models = linkedMapOf<String, JsonObject>()
        configs.forEach { config ->
            val routeApi = config.effectiveDshApi()
            val options = config.modelOptions.associateBy { it.id.trim() }
            (config.modelOptions.map(ModelOption::id) + config.model)
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .forEach modelLoop@{ configuredId ->
                    val modelConfig = config.copy(model = configuredId)
                    if (modelConfig.effectiveDshApi() != routeApi) return@modelLoop
                    val id = if (config.effectiveDshApi() == DshProviderApi.Google) {
                        configuredId.replace(Regex("^models/", RegexOption.IGNORE_CASE), "")
                    } else {
                        configuredId
                    }
                    val option = options[configuredId]
                    val profile = modelProfile(modelConfig, option, id, !inheritsCatalog)
                    val previous = models.putIfAbsent(id, profile)
                    require(previous == null || previous == profile) {
                        "同一 DSH Provider 路由中的模型能力声明冲突：$route/$id"
                    }
                }
        }
        return JsonArray(models.values.toList())
    }

    private fun modelProfile(
        config: ModelConfig,
        option: ModelOption?,
        modelId: String,
        customRoute: Boolean,
    ): JsonObject = buildJsonObject {
        put("id", modelId)
        option?.name
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != modelId }
            ?.let { put("name", it) }
        val contextWindow = option?.contextWindowTokens
            ?: config.configuredContextWindowTokens().takeIf {
                customRoute || config.usesDshDeepSeekPiAiRoute()
            }
        contextWindow?.let { put("contextWindow", it) }
        option?.maxOutputTokens?.let { put("maxTokens", it) }
        if (customRoute || config.supportsImageInput(option?.id ?: config.model)) {
            put("input", buildJsonArray {
                add(JsonPrimitive("text"))
                if (config.supportsImageInput(option?.id ?: config.model)) {
                    add(JsonPrimitive("image"))
                }
            })
        }
        val reasoningEfforts = if (config.usesDshDeepSeekPiAiRoute()) {
            DeepSeekPiAiReasoningEfforts
        } else {
            option?.reasoningEfforts
        }
        reasoningEfforts?.let { efforts ->
            if (efforts.isEmpty()) {
                put("reasoningEfforts", false)
            } else {
                put("reasoningEfforts", buildJsonObject {
                    efforts.forEach { (level, wireValue) ->
                        put(level, wireValue?.let(::JsonPrimitive) ?: JsonNull)
                    }
                })
            }
        }
        option?.reasoningThinkingFormat
            ?.takeIf { config.effectiveDshApi() == DshProviderApi.OpenAiCompletions }
            ?.let { format -> put("compat", buildJsonObject { put("thinkingFormat", format) }) }
    }

    private fun wireBaseUrl(baseUrl: String, api: DshProviderApi): String {
        val path = when (api) {
            DshProviderApi.OpenAiResponses -> "responses/v1"
            DshProviderApi.OpenAiCompletions -> "chat/v1"
            DshProviderApi.AnthropicMessages -> "anthropic"
            DshProviderApi.Google -> "google"
        }
        return "${baseUrl.removeSuffix("/")}/provider-wire/$path"
    }

    private fun connectionIdentity(config: ModelConfig): String = buildString {
        append(config.provider.trim().lowercase())
        append('\u0000')
        append(config.effectiveDshApi().wireValue)
        append('\u0000')
        append(config.resolvedProviderBaseUrl().lowercase())
        append('\u0000')
        append(config.proxyUrl.trim())
        append('\u0000')
        config.customHeaders.toSortedMap().forEach { (name, value) ->
            append(name.lowercase())
            append('=')
            append(value)
            append('\u0000')
        }
    }

    private val ProviderBaseUrl = Regex(
        "^http://127\\.0\\.0\\.1:[0-9]{1,5}/[A-Za-z0-9_-]{8,160}$",
    )

    private val DeepSeekPiAiReasoningEfforts = linkedMapOf(
        "off" to "none",
        "low" to "low",
        "high" to "high",
        "max" to "max",
    )

}
