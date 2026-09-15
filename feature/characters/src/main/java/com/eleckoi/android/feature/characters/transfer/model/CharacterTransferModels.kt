package com.eleckoi.android.feature.characters.transfer.model

import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.feature.characters.modes.story.regex.model.RegexRule
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import java.io.File

enum class CharacterImportSource {
    ElecKoi,
    SillyTavern,
}

enum class CharacterExportFormat(val extension: String, val mediaType: String) {
    Png(extension = "png", mediaType = "image/png"),
    Json(extension = "json", mediaType = "application/json"),
}

data class CharacterImportPreview(
    val token: String,
    val items: List<CharacterImportPreviewItem>,
) {
    val count: Int get() = items.size
    val importableCount: Int get() = items.count(CharacterImportPreviewItem::importable)
    val failedCount: Int get() = count - importableCount
}

data class CharacterImportPreviewItem(
    val id: String,
    val name: String,
    val summary: String,
    val imageFile: File?,
    val errorMessage: String = "",
) {
    val importable: Boolean get() = errorMessage.isBlank()
}
data class ExportedCharacterCard(
    val characterId: String,
    val name: String,
    val format: CharacterExportFormat,
    val file: File,
)

internal data class PortableCharacterPackage(
    val character: PortableCharacter,
    val assets: List<PortableAsset> = emptyList(),
    val settingLibraryJson: String = "",
    val variableConfigJson: String = "",
    val regexRules: List<RegexRule> = emptyList(),
)

internal data class PortableCharacter(
    val name: String,
    val group: String,
    val frontendBeautyEnabled: Boolean,
    val profileAge: String,
    val profileSex: String,
    val profileHeight: String,
    val profileBirthday: String,
    val profileLike: String,
    val imagePrompt: String,
    val opening: String,
    val showOpening: Boolean,
)

internal data class PortableAsset(
    val key: String,
    val mediaType: String,
    val bytes: ByteArray,
)

internal data class DecodedCharacterCard(
    val packageData: PortableCharacterPackage,
    val sourceImage: ByteArray?,
    val complete: Boolean,
    val summary: String = "",
    val settingLibrary: SettingLibrary? = null,
    val variableConfig: VariableConfig? = null,
    val regexRules: List<RegexRule> = emptyList(),
)
