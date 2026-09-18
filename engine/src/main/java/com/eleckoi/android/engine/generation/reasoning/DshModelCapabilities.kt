package com.eleckoi.android.engine.generation.reasoning

import com.eleckoi.android.engine.generation.model.ModelApiFormat
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.ModelOption
import com.eleckoi.android.engine.generation.model.effectiveApiFormat
import com.eleckoi.android.engine.generation.model.isOfficialDeepSeekEndpoint
import com.eleckoi.android.engine.generation.model.resolvedProviderBaseUrl
import java.net.URI

/** DSH-owned model capabilities and the adapter route that must interpret them. */
data class DshModelCapabilityProfile(
    val wireProfile: String,
    val reasoningEfforts: List<DshReasoningEffortOption>,
)

object DshModelCapabilities {
    const val PiAiWireProfile = "pi-ai"
    const val GoogleWireProfile = "google"
    const val DeepSeekOfficialWireProfile = "deepseek-official"
    const val DeepSeekResponsesProviderRoute = "eleckoi-deepseek-responses"
    const val DeepSeekAnthropicProviderRoute = "eleckoi-deepseek-anthropic"

    private val noDeclaredReasoning = DshModelCapabilityProfile(
        wireProfile = PiAiWireProfile,
        reasoningEfforts = emptyList(),
    )
    private val deepSeekReasoningEfforts = DshReasoningEfforts.optionsFor(
        listOf("off", "low", "high", "max"),
    )

    fun active(config: ModelConfig): DshModelCapabilityProfile {
        val selected = config.model.trim()
        val option = config.modelOptions.firstOrNull { it.id == selected }
            ?: ModelOption(id = selected)
        return forModel(config, option)
    }

    fun forModel(config: ModelConfig, option: ModelOption): DshModelCapabilityProfile {
        if (option.id.isBlank()) return noDeclaredReasoning
        val selectedConfig = config.copy(model = option.id)
        if (selectedConfig.isOfficialDeepSeekEndpoint()) {
            return DshModelCapabilityProfile(
                wireProfile = if (selectedConfig.usesDshDeepSeekOfficialRoute()) {
                    DeepSeekOfficialWireProfile
                } else {
                    PiAiWireProfile
                },
                reasoningEfforts = deepSeekReasoningEfforts,
            )
        }
        val profile = if (selectedConfig.effectiveDshApi() == DshProviderApi.Google) {
            GoogleWireProfile
        } else {
            PiAiWireProfile
        }
        val declared = option.reasoningEfforts
        return DshModelCapabilityProfile(
            wireProfile = profile,
            reasoningEfforts = when {
                declared == null -> DshReasoningEfforts.optionsFor(
                    option.dshReasoningEffortIds.orEmpty(),
                )
                declared.isEmpty() -> emptyList()
                else -> DshReasoningEfforts.optionsFor(declared.keys)
            },
        )
    }

    /** Mirrors the PC capability source contract for models whose DSH profile is user-extensible. */
    fun usesCustomReasoningList(config: ModelConfig, option: ModelOption): Boolean {
        val selectedConfig = config.copy(model = option.id)
        if (selectedConfig.isOfficialDeepSeekEndpoint()) return false
        if (option.reasoningEfforts != null) return true
        return DshPiAiProviderCatalog.providerRoute(selectedConfig).startsWith("eleckoi-custom-")
    }

    /**
     * Mirrors PC's pi-ai route match: protocol and normalized upstream endpoint must both match the
     * pinned catalog. The dedicated DeepSeek entry is the sole product-owned exception because it
     * is wired to DSH's official DeepSeek adapter instead of inferred from an endpoint or model id.
     */
    fun piAiCatalogProvider(config: ModelConfig): String {
        if (config.provider.trim().equals("deepseek", ignoreCase = true)) {
            return when (config.effectiveDshApi()) {
            DshProviderApi.OpenAiResponses -> DeepSeekResponsesProviderRoute
            DshProviderApi.AnthropicMessages -> DeepSeekAnthropicProviderRoute
            DshProviderApi.OpenAiCompletions -> DeepSeekOfficialWireProfile
            DshProviderApi.Google -> error("DeepSeek 专用入口不支持 Google Generative AI")
            }
        }

        val api = config.effectiveDshApi()
        val endpoint = normalizedCatalogEndpoint(config, api) ?: return "eleckoi-custom"
        return NativePiAiRoutes.firstOrNull { route ->
            route.api == api && route.endpoint == endpoint
        }?.provider ?: "eleckoi-custom"
    }

    private fun normalizedCatalogEndpoint(config: ModelConfig, api: DshProviderApi): String? {
        val uri = runCatching { URI(config.resolvedProviderBaseUrl()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return null
        val rawPath = uri.path.orEmpty().replace(Regex("/+$"), "")
        val path = if (api == DshProviderApi.Google) {
            rawPath
                .replace(Regex("/v1beta/openai$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("/v1beta$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("/v1$", RegexOption.IGNORE_CASE), "") + "/v1beta"
        } else {
            rawPath
        }
        return "https://${uri.host.lowercase()}${path.lowercase()}"
    }

    private data class NativePiAiRoute(
        val provider: String,
        val api: DshProviderApi,
        val endpoint: String,
    )

    /** Endpoint rows from the pi-ai catalog bundled by pinned DSH 0.1.5-rc.2. */
    private val NativePiAiRoutes = listOf(
        NativePiAiRoute("anthropic", DshProviderApi.AnthropicMessages, "https://api.anthropic.com"),
        NativePiAiRoute(
            "google",
            DshProviderApi.Google,
            "https://generativelanguage.googleapis.com/v1beta",
        ),
        NativePiAiRoute(
            "moonshotai-cn",
            DshProviderApi.OpenAiCompletions,
            "https://api.moonshot.cn/v1",
        ),
        NativePiAiRoute("openai", DshProviderApi.OpenAiResponses, "https://api.openai.com/v1"),
        NativePiAiRoute(
            "zai",
            DshProviderApi.OpenAiCompletions,
            "https://api.z.ai/api/coding/paas/v4",
        ),
    )
}

enum class DshProviderApi(val wireValue: String) {
    OpenAiResponses("openai-responses"),
    OpenAiCompletions("openai-completions"),
    AnthropicMessages("anthropic-messages"),
    Google("google-generative-ai"),
}

fun ModelConfig.usesDshDeepSeekOfficialRoute(): Boolean =
    isOfficialDeepSeekEndpoint() && effectiveApiFormat() == ModelApiFormat.ChatCompletions

fun ModelConfig.usesDshDeepSeekResponsesRoute(): Boolean =
    isOfficialDeepSeekEndpoint() && effectiveApiFormat() == ModelApiFormat.Responses

fun ModelConfig.usesDshDeepSeekAnthropicRoute(): Boolean =
    isOfficialDeepSeekEndpoint() && effectiveApiFormat() == ModelApiFormat.AnthropicMessages

fun ModelConfig.usesDshDeepSeekPiAiRoute(): Boolean =
    usesDshDeepSeekResponsesRoute() || usesDshDeepSeekAnthropicRoute()

fun ModelConfig.effectiveDshApi(): DshProviderApi {
    if (usesDshDeepSeekOfficialRoute()) return DshProviderApi.OpenAiCompletions
    if (usesDshDeepSeekResponsesRoute()) return DshProviderApi.OpenAiResponses
    if (usesDshDeepSeekAnthropicRoute()) return DshProviderApi.AnthropicMessages
    require(!isOfficialDeepSeekEndpoint()) {
        "DeepSeek 专用入口只支持 Chat Completions、Responses API 或 Anthropic Messages"
    }
    return when (effectiveApiFormat()) {
        ModelApiFormat.Responses -> DshProviderApi.OpenAiResponses
        ModelApiFormat.ChatCompletions -> DshProviderApi.OpenAiCompletions
        ModelApiFormat.AnthropicMessages -> DshProviderApi.AnthropicMessages
        ModelApiFormat.GoogleGemini -> DshProviderApi.Google
    }
}
