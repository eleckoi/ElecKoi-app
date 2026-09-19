package com.eleckoi.android.feature.chat.ui.roleplay.web.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.ui.roleplay.web.host.RoleplayWebChatHost
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayRendererFailure

internal class RoleplayWebChatController {
    private var host: RoleplayWebChatHost? = null

    internal fun attach(next: RoleplayWebChatHost) {
        host = next
    }

    internal fun detach(current: RoleplayWebChatHost) {
        if (host === current) host = null
    }

    fun scrollToBottom() {
        host?.scrollToBottom()
    }
}

@Composable
internal fun rememberRoleplayWebChatController(): RoleplayWebChatController =
    remember { RoleplayWebChatController() }

internal data class RoleplayWebChatCallbacks(
    val onReady: () -> Unit,
    val onMessageRendered: (String) -> Unit,
    val onScrollStateChanged: (browsingHistory: Boolean, canScrollForward: Boolean) -> Unit,
    val onLoadOlder: () -> Unit,
    val onSelectOpeningOption: (String) -> Unit,
    val onRequestOpeningJump: () -> Unit,
    val onSelectDeleteFrom: (String) -> Unit = {},
    val onMessageAction: (action: String, message: ChatMessage) -> Unit,
    val onImageAction: (
        action: String,
        message: ChatMessage,
        attachment: ChatImageAttachment,
    ) -> Unit,
    val onUserAvatarClick: () -> Unit,
    val onAssistantAvatarClick: () -> Unit,
    val onRendererUnavailable: (RoleplayRendererFailure) -> Unit,
)
