package com.eleckoi.android.feature.characters.presets.model

enum class AgentPresetImportSource {
    ElecKoi,
    SillyTavern,
}

enum class AgentPresetExportFormat(
    val extension: String,
    val mediaType: String,
) {
    Png("png", "image/png"),
    Json("json", "application/json"),
}

data class AgentPresetImportDocument(
    val fileName: String,
    val json: String = "",
    val bytes: ByteArray? = null,
)

data class ExportedAgentPresetFile(
    val presetId: String,
    val name: String,
    val format: AgentPresetExportFormat,
    val bytes: ByteArray,
)
