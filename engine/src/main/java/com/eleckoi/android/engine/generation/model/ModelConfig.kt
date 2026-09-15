package com.eleckoi.android.engine.generation.model

import java.net.URI

const val NovelAiImageProviderId: String = "novelai_image"
const val NovelAiDefaultBaseUrl: String = "https://image.novelai.net"
const val NovelAiDefaultModel: String = "nai-diffusion-4-5-full"
const val OpenAiDefaultBaseUrl: String = "https://api.openai.com/v1"
const val DeepSeekDefaultBaseUrl: String = "https://api.deepseek.com"
const val ZhipuDefaultBaseUrl: String = "https://open.bigmodel.cn/api/paas/v4"
const val ZaiDefaultBaseUrl: String = "https://api.z.ai/api/paas/v4"
const val MoonshotDefaultBaseUrl: String = "https://api.moonshot.cn/v1"

val DefaultNovelAiPromptCompilerInstruction: String = """
    You are a professional prompt compiler for NovelAI Diffusion V4.5.

    TASK
    Create one production-ready NovelAI image prompt for each requested story frame in the final
    roleplay reply you are about to output in the same response. The final reply is authoritative
    for what happens in every frame. Never copy or summarize the roleplay prose into a prompt.

    SCENE SELECTION
    - For each frame, select one visually decisive frozen moment that actually occurs in the final
      reply. When several frames are requested, distribute them across distinct story beats instead
      of producing variations of the same moment.
    - Depict only visible facts: subjects, appearance, clothing state, pose, physical interaction,
      expression, gaze, environment, objects, weather, lighting, and camera composition.
    - Convert motion or a sequence into the single strongest readable instant.
    - Do not invent later events, dialogue, captions, thoughts, sounds, smells, or invisible feelings.
    - Preserve established character identity and canonical franchise traits. If a known character or
      franchise is supplied, use its commonly recognized English/romanized NovelAI-style tags.

    PROMPT FORMAT
    - Write concise English visual tags and short tag phrases separated by commas.
    - Do not output Chinese, narrative prose, complete sentences, explanations, markdown, or quotes.
    - Order the positive prompt roughly as: quality/style, subject count and identity, appearance,
      clothing, pose/action and interaction, expression/gaze, framing and angle, environment,
      lighting/color, atmosphere/effects.
    - Include a clear subject count and camera framing. Keep mutually exclusive details out.
    - Do not add artist names or a new house style unless they are explicitly present in the input;
      fixed style and character tags are appended by the application after compilation.

    NEGATIVE PROMPT
    - Include defects relevant to the selected scene as well as low quality, bad anatomy, bad hands,
      extra or missing limbs/digits, duplicate subjects, malformed interaction, text, subtitles,
      speech bubbles, signatures, logos, and watermarks.

    OUTPUT CONTRACT
    Fill exactly these two non-empty string fields inside every frame supplied by the
    generate_image action protocol:
    {"prompt":"comma-separated English NovelAI tags","negative_prompt":"comma-separated negative tags"}
""".trimIndent()

const val MaxStoryImagesPerTurn: Int = 24

data class ModelConfig(
    val id: String = "",
    val name: String = "",
    val provider: String = "custom",
    val apiKey: String = "",
    val baseUrl: String = "",
    val proxyUrl: String = "",
    val model: String = "",
    val modelOptions: List<ModelOption> = emptyList(),
    /**
     * The persisted key existed but could not be decrypted (for example after an
     * Android Keystore reset). The key itself is deliberately not exposed; the
     * rest of the model configuration remains usable and editable.
     */
    val apiKeyNeedsReentry: Boolean = false,
    /** Extra headers sent with every request to this endpoint. Gateways often require them. */
    val customHeaders: Map<String, String> = emptyMap(),
    /** The selected image-provider configuration; feature access is controlled by tool switches. */
    val enabled: Boolean = false,
    val imageSettings: ImageGenerationSettings = ImageGenerationSettings(),
    /** Wire format selected for this connection. Generic configurations prefer Responses. */
    val apiFormat: ModelApiFormat = ModelApiFormat.Responses,
)

enum class ModelApiFormat(val storageValue: String) {
    ChatCompletions("chat_completions"),
    Responses("responses"),
    AnthropicMessages("anthropic_messages"),
    GoogleGemini("google_gemini"),
    ;

    companion object {
        fun fromStorageValue(value: String): ModelApiFormat = entries
            .firstOrNull { it.storageValue == value.trim().lowercase() }
            ?: throw IllegalArgumentException("未知模型接口格式：$value")
    }
}

/**
 * Wire format used when a provider configuration is created without an existing user choice.
 * Stored configurations keep their persisted format; this is only the creation default.
 */
fun defaultApiFormatForProvider(providerId: String): ModelApiFormat = when (
    providerId.trim().lowercase()
) {
    "custom", "deepseek" -> ModelApiFormat.Responses
    else -> ModelApiFormat.ChatCompletions
}

/** Official provider address used when its optional address field is blank. */
fun defaultBaseUrlForProvider(providerId: String): String? = when (
    providerId.trim().lowercase()
) {
    "deepseek" -> DeepSeekDefaultBaseUrl
    "zhipu" -> ZhipuDefaultBaseUrl
    "zai" -> ZaiDefaultBaseUrl
    "moonshot" -> MoonshotDefaultBaseUrl
    NovelAiImageProviderId -> NovelAiDefaultBaseUrl
    OpenAiImageProviderId -> OpenAiDefaultBaseUrl
    else -> null
}

fun ModelConfig.resolvedProviderBaseUrl(): String = baseUrl
    .trim()
    .ifBlank { defaultBaseUrlForProvider(provider).orEmpty() }
    .trimEnd('/')

data class ImageGenerationSettings(
    val width: Int = 832,
    val height: Int = 1216,
    val steps: Int = 28,
    val scale: Double = 5.0,
    val sampler: String = NovelAiSamplerCatalog.DefaultApiValue,
    val quality: ImageQuality = ImageQuality.Auto,
    val background: ImageBackground = ImageBackground.Auto,
    /** When true, the Agent selects a visible number between [automaticImageMin] and [automaticImageMax]. */
    val automaticImageCount: Boolean = false,
    /** Exact number of distinct story frames when [automaticImageCount] is false. */
    val fixedImageCount: Int = 1,
    val automaticImageMin: Int = 1,
    val automaticImageMax: Int = 6,
    /**
     * Instruction used by the roleplay Agent to fill generate_image action arguments while writing
     * the final reply. Stored with the image model so users can tune the compiler independently.
     */
    val promptCompilerInstruction: String = "",
    /** Inserted verbatim before the generated scene prompt for a shared house style. */
    val promptPrefix: String = "",
    /** Appended verbatim to the built-in and scene-specific negative prompts. */
    val negativePrompt: String = "",
)

fun ImageGenerationSettings.storyImageCountRange(): IntRange {
    val fixed = fixedImageCount.coerceIn(1, MaxStoryImagesPerTurn)
    if (!automaticImageCount) return fixed..fixed
    val minimum = automaticImageMin.coerceIn(1, MaxStoryImagesPerTurn)
    val maximum = automaticImageMax.coerceIn(minimum, MaxStoryImagesPerTurn)
    return minimum..maximum
}

fun ModelConfig.isImageGenerationConfig(): Boolean = imageGenerationProvider() != null

fun ModelConfig.isChatModelConfig(): Boolean = !isImageGenerationConfig()

fun ModelConfig.configuredMaxOutputTokens(): Int? = modelOptions
    .firstOrNull { it.id == model.trim() }
    ?.maxOutputTokens
    ?.takeIf { it in ModelOption.MinMaxOutputTokens..ModelOption.MaxContextWindowTokens }

/** Sampling controls are stored per model and applied at the provider boundary. */
fun ModelConfig.configuredTemperature(): Double? = modelOptions
    .firstOrNull { it.id == model.trim() }
    ?.temperature
    ?.takeIf { it in ModelOption.MinTemperature..ModelOption.MaxTemperature }

fun ModelConfig.configuredTopP(): Double? = modelOptions
    .firstOrNull { it.id == model.trim() }
    ?.topP
    ?.takeIf { it in ModelOption.MinTopP..ModelOption.MaxTopP }

/** DeepSeek's official preview vision route is provider-declared rather than user-asserted. */
fun ModelConfig.isOfficialDeepSeekVisionModel(): Boolean =
    model.trim().equals(DeepSeekOfficialVisionModel, ignoreCase = true) &&
        isOfficialDeepSeekEndpoint()

/** True only for DeepSeek's first-party API, never for an arbitrary relay using its provider id. */
fun ModelConfig.isOfficialDeepSeekEndpoint(): Boolean {
    val normalizedProvider = provider.trim().lowercase()
    val configuredBaseUrl = baseUrl.trim()
    if (normalizedProvider == "deepseek" && configuredBaseUrl.isBlank()) return true
    if (normalizedProvider !in setOf("deepseek", "custom")) return false
    return runCatching { URI(configuredBaseUrl).host?.lowercase() }
        .getOrNull() == DeepSeekOfficialApiHost
}

/** Explicit custom declaration, with the official DeepSeek vision model enabled automatically. */
fun ModelConfig.supportsImageInput(): Boolean =
    supportsImageInput(model)

/**
 * Resolves image input for a conversation-selected model rather than assuming the connection's
 * default model is still active.
 */
fun ModelConfig.supportsImageInput(selectedModel: String): Boolean =
    (selectedModel.trim().equals(DeepSeekOfficialVisionModel, ignoreCase = true) &&
        isOfficialDeepSeekEndpoint()) ||
        (modelOptions
            .firstOrNull { it.id == selectedModel.trim() }
            ?.supportsImageInput
            ?: false)

/** The selected model's one capacity source for the Harness and the composer. */
fun ModelConfig.configuredContextWindowTokens(): Int = modelOptions
    .firstOrNull { it.id == model.trim() }
    ?.contextWindowTokens
    ?.takeIf { it in ModelOption.MinContextWindowTokens..ModelOption.MaxContextWindowTokens }
    ?: defaultContextWindowTokens()

/** Provider-aware capacity used only when the selected model has no explicit metadata. */
fun ModelConfig.defaultContextWindowTokens(): Int =
    if (isOfficialDeepSeekEndpoint()) DeepSeekOfficialContextWindowTokens
    else ModelOption.AgentFallbackContextWindowTokens

/** Optional absolute pressure point for DSH automatic history compaction. */
fun ModelConfig.configuredAutoCompactTokenLimit(): Int? {
    val contextWindow = configuredContextWindowTokens()
    return modelOptions
        .firstOrNull { it.id == model.trim() }
        ?.autoCompactTokenLimit
        ?.takeIf { it in ModelOption.MinAutoCompactTokenLimit..contextWindow }
}

/** The selected model may override the connection-wide wire format. */
fun ModelConfig.effectiveApiFormat(): ModelApiFormat = modelOptions
    .firstOrNull { it.id == model.trim() }
    ?.apiFormatOverride
    ?: apiFormat

fun ModelConfig.usesDeepSeekThinkingContract(): Boolean =
    provider.contains("deepseek", ignoreCase = true) ||
        model.contains("deepseek", ignoreCase = true) ||
        baseUrl.contains("api.deepseek.com", ignoreCase = true)

/** Chat-compatible providers that expose an explicit `thinking.type` switch. */
fun ModelConfig.usesChatThinkingToggleContract(): Boolean =
    usesDeepSeekThinkingContract() ||
        provider.contains("minimax", ignoreCase = true) ||
        model.contains("minimax", ignoreCase = true) ||
        baseUrl.contains("api.minimax", ignoreCase = true)

fun ModelConfig.withProviderDefaults(): ModelConfig {
    val imageProvider = imageGenerationProvider() ?: return this
    return copy(
        baseUrl = baseUrl.ifBlank { defaultBaseUrlForProvider(provider).orEmpty() },
        model = model.ifBlank { imageProvider.defaultModel },
    )
}

data class ModelOption(
    val id: String,
    val name: String = id,
    /** Explicit provider/model metadata override used by a Harness. Null keeps automatic detection. */
    val contextWindowTokens: Int? = null,
    /** Optional earlier compaction point. Null keeps the Harness model-derived threshold. */
    val autoCompactTokenLimit: Int? = null,
    /** Optional per-request output cap. Null leaves the limit to the upstream model/provider. */
    val maxOutputTokens: Int? = null,
    /** Optional sampling temperature. Null leaves the parameter to the provider default. */
    val temperature: Double? = null,
    /** Optional nucleus-sampling threshold. Null leaves the parameter to the provider default. */
    val topP: Double? = null,
    /** Selected DSH reasoning effort id. Null preserves the provider/model default. */
    val reasoningEffort: String? = null,
    /** Null follows the connection format; non-null overrides it for this model only. */
    val apiFormatOverride: ModelApiFormat? = null,
    /** User assertion that this exact endpoint/model accepts image input. */
    val supportsImageInput: Boolean = false,
    /** Only models explicitly added by the user can be removed from the local list. */
    val isUserAdded: Boolean = false,
) {
    companion object {
        /** Conservative context fallback for Harnesses whose model catalog lacks this model. */
        const val AgentFallbackContextWindowTokens = 272_000
        /** Matches the Harness basic-compaction default when no override is supplied. */
        const val AgentDefaultAutoCompactPercent = 80
        const val MinContextWindowTokens = 4_096
        const val MaxContextWindowTokens = 4_000_000
        const val MinAutoCompactTokenLimit = 1_024
        const val MinMaxOutputTokens = 1
        const val MinTemperature = 0.0
        const val MaxTemperature = 2.0
        const val MinTopP = 0.0
        const val MaxTopP = 1.0
    }
}

const val DeepSeekOfficialVisionModel: String = "deepseek-v4-flash-vision-exp"
const val DeepSeekOfficialContextWindowTokens: Int = 1_000_000
private const val DeepSeekOfficialApiHost: String = "api.deepseek.com"
