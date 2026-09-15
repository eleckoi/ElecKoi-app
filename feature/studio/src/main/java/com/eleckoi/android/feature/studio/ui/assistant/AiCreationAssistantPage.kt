package com.eleckoi.android.feature.studio.ui.assistant

import androidx.compose.runtime.Composable
import com.eleckoi.android.sdk.author.AuthorChatGateway
import com.eleckoi.android.feature.studio.ui.assistant.screen.AiCreationAssistantScreen
import com.eleckoi.android.foundation.design.AppearanceTheme

/**
 * Stable feature entry point used by the application shell.
 *
 * The implementation lives in the screen package so navigation depends only on this small facade.
 */
@Composable
fun AiCreationAssistantPage(
    appearance: AppearanceTheme,
    viewModel: AiCreationAssistantViewModel,
    chatGateway: AuthorChatGateway,
    onBack: () -> Unit,
    onOpenTools: () -> Unit,
) {
    AiCreationAssistantScreen(
        appearance = appearance,
        viewModel = viewModel,
        chatGateway = chatGateway,
        onBack = onBack,
        onOpenTools = onOpenTools,
    )
}
