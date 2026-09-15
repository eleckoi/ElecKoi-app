package com.eleckoi.android.app.navigation

import androidx.navigation3.runtime.NavKey
import com.eleckoi.android.feature.modelconfig.ui.ModelTarget
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface MobileRoute : NavKey {
    @Serializable
    data object Root : MobileRoute
    @Serializable
    data object Chat : MobileRoute
    @Serializable
    data object Settings : MobileRoute
    @Serializable
    data object About : MobileRoute
    @Serializable
    data object AppUpdate : MobileRoute
    @Serializable
    data object Profile : MobileRoute
    @Serializable
    data object UserAvatars : MobileRoute
    @Serializable
    data object Theme : MobileRoute
    @Serializable
    data object MarkdownReadingColors : MobileRoute
    @Serializable
    data object ChatDisplay : MobileRoute
    data object FontSettings : MobileRoute
    @Serializable
    data object ListCharacterArtworkSettings : MobileRoute
    @Serializable
    data object RuntimeSettings : MobileRoute
    @Serializable
    data object CrashDiagnostics : MobileRoute
    @Serializable
    data object WebSearchSettings : MobileRoute
    @Serializable
    data class RemoteDshSettings(val toolScopeId: String = "") : MobileRoute
    @Serializable
    data class RemoteDshSession(val sessionId: String) : MobileRoute
    @Serializable
    data class CharacterSettings(val characterId: String) : MobileRoute
    @Serializable
    data class CharacterDraft(val characterId: String) : MobileRoute
    @Serializable
    data class CharacterAvatars(val characterId: String) : MobileRoute
    @Serializable
    data class VariableConfig(val characterId: String) : MobileRoute
    @Serializable
    data class RegexRules(val characterId: String) : MobileRoute
    @Serializable
    data class SettingLibrary(val characterId: String) : MobileRoute
    @Serializable
    data object AgentPresets : MobileRoute
    @Serializable
    data class DynamicSettings(
        val characterId: String,
        val sessionId: String = "",
    ) : MobileRoute
    @Serializable
    data class FrontendBeauty(val characterId: String) : MobileRoute
    @Serializable
    data object AiCreationAssistant : MobileRoute
    @Serializable
    data class ModelSettings(val target: ModelTarget) : MobileRoute
}
