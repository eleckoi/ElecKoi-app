package com.eleckoi.android.feature.chat.ui.roleplay.web.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.eleckoi.android.feature.chat.ui.roleplay.web.host.RoleplayWebChatHost
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptModel
import com.eleckoi.android.sdk.author.AuthorChatGateway

@Composable
internal fun RoleplayWebChatSurface(
    model: RoleplayTranscriptModel,
    updatesPaused: Boolean,
    controller: RoleplayWebChatController,
    callbacks: RoleplayWebChatCallbacks,
    messageGateway: AuthorChatGateway,
    modifier: Modifier = Modifier,
) {
    key(model.sessionId) {
        RoleplayWebChatSessionSurface(
            model = model,
            updatesPaused = updatesPaused,
            controller = controller,
            callbacks = callbacks,
            messageGateway = messageGateway,
            modifier = modifier,
        )
    }
}

@Composable
private fun RoleplayWebChatSessionSurface(
    model: RoleplayTranscriptModel,
    updatesPaused: Boolean,
    controller: RoleplayWebChatController,
    callbacks: RoleplayWebChatCallbacks,
    messageGateway: AuthorChatGateway,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val host = remember(messageGateway) {
        RoleplayWebChatHost(
            context = context,
            initialCallbacks = callbacks,
            messageGateway = messageGateway,
        )
    }
    host.updateCallbacks(callbacks)
    host.setUpdatesPaused(updatesPaused)
    DisposableEffect(host, controller) {
        controller.attach(host)
        onDispose {
            controller.detach(host)
            host.release()
        }
    }
    AndroidView(
        factory = { host.webView },
        update = { host.bind(model) },
        modifier = modifier,
    )
}
