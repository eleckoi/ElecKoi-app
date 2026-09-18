package com.eleckoi.android.engine.agent.adapter

/** Provider protocol already selected and serialized by DSH/pi-ai. */
internal enum class ProviderWireFormat(val piApi: String) {
    Responses("openai-responses"),
    ChatCompletions("openai-completions"),
    AnthropicMessages("anthropic-messages"),
    GoogleGemini("google-generative-ai"),
}
