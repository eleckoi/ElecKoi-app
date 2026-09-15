package com.eleckoi.android.sdk.author

import com.eleckoi.android.engine.creator.capability.CreatorCapability
import com.eleckoi.android.engine.creator.capability.CreatorCapabilityRegistry
import com.eleckoi.android.sdk.author.app.AppAuthorApi
import com.eleckoi.android.sdk.author.audio.AudioAuthorApi
import com.eleckoi.android.sdk.author.character.CharacterAuthorApi
import com.eleckoi.android.sdk.author.chat.ChatAuthorApi
import com.eleckoi.android.sdk.author.context.ContextAuthorApi
import com.eleckoi.android.sdk.author.events.AuthorEventApi
import com.eleckoi.android.sdk.author.input.InputAuthorApi
import com.eleckoi.android.sdk.author.messages.MessageAuthorApi
import com.eleckoi.android.sdk.author.media.MediaAuthorApi
import com.eleckoi.android.sdk.author.openings.OpeningAuthorApi
import com.eleckoi.android.sdk.author.setting_library.SettingLibraryAuthorApi
import com.eleckoi.android.sdk.author.variables.VariableAuthorApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Public Author API binding for one neutral creator capability. */
internal typealias AuthorApiRoute =
    CreatorCapability<AuthorApiEnvironment, AuthorApiDefinition>

internal typealias AuthorCreatorCapabilityRegistry =
    CreatorCapabilityRegistry<AuthorApiEnvironment, AuthorApiDefinition>

/** The Author API adapter's composition of the capabilities it exposes to creator frontends. */
internal object AuthorCapabilityRegistry {
    val Default: AuthorCreatorCapabilityRegistry by lazy {
        CreatorCapabilityRegistry(
            listOf(
                AppAuthorApi.routes,
                ContextAuthorApi.routes,
                VariableAuthorApi.routes,
                OpeningAuthorApi.routes,
                MessageAuthorApi.routes,
                MediaAuthorApi.routes,
                AudioAuthorApi.routes,
                ChatAuthorApi.routes,
                CharacterAuthorApi.routes,
                SettingLibraryAuthorApi.routes,
                InputAuthorApi.routes,
                AuthorEventApi.routes,
            ).flatten(),
        )
    }
}

/** Applies the Author API permission grant before invoking the shared capability. */
internal class AuthorCapabilityExecutor(
    private val environment: AuthorApiEnvironment,
    private val registry: AuthorCreatorCapabilityRegistry = AuthorCapabilityRegistry.Default,
) {
    suspend fun invoke(method: String, params: JsonObject): JsonElement {
        val capability = registry.find(method) ?: throw AuthorApiCallException(
            AuthorApiErrorCode.MethodNotFound,
            "没有找到创作能力：$method",
        )
        val requiredPermission = AuthorApiPermission.fromWireName(capability.definition.permission)
        if (requiredPermission == null || requiredPermission !in environment.permissions) {
            throw AuthorApiCallException(
                AuthorApiErrorCode.PermissionDenied,
                "当前调用方没有 ${capability.definition.permission} 权限",
            )
        }
        return capability.handler(environment, params)
    }
}
