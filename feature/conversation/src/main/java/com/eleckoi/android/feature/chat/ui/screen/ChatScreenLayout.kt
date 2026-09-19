package com.eleckoi.android.feature.chat.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.ui.ChatPresentationReadinessState
import com.eleckoi.android.feature.chat.ui.ChatUiState
import com.eleckoi.android.feature.chat.ui.layout.ChatTopBar
import com.eleckoi.android.foundation.design.AppearanceTheme

@Composable
internal fun ChatScreenTopBar(
    state: ChatUiState,
    draft: ChatDraft?,
    roleplay: Boolean,
    appearance: AppearanceTheme,
    effectiveBackgroundPath: String,
    menuExpanded: Boolean,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onCustomizeBackground: () -> Unit,
    onCreateChat: () -> Unit,
) {
    ChatTopBar(
        title = draft?.session?.characterPersona?.assistantName?.ifBlank { "聊天" }
            ?: state.chatCharacterName.ifBlank { "聊天" },
        appearance = appearance,
        onBack = onBack,
        onMore = onOpenMenu,
        moreMenuExpanded = menuExpanded,
        onDismissMoreMenu = onDismissMenu,
        onCustomizeBackground = onCustomizeBackground,
        onCreateChat = onCreateChat,
        effectiveBackgroundPath = effectiveBackgroundPath,
        bandColor = if (roleplay) {
            appearance.mobileSurface.copy(
                alpha = if (state.chatRoleplayCardPanel) 0.96f else 0f,
            )
        } else {
            null
        },
        stableStatusBarInset = roleplay,
    )
}

@Composable
internal fun ChatConversationStateContent(
    state: ChatUiState,
    draft: ChatDraft?,
    showLoadingStatus: Boolean,
    webTranscriptReady: Boolean,
    webRendererFailed: Boolean,
    presentationReadiness: ChatPresentationReadinessState,
    onCreateChat: () -> Unit,
    onRetryWebRenderer: () -> Unit,
    webContent: @Composable (ChatDraft, Float) -> Unit,
) {
    when {
        state.isDraftLoading -> if (showLoadingStatus) {
            ChatCenteredStatus(
                text = "正在加载本地聊天...",
                appearance = state.appearance,
            )
        }

        draft == null -> EmptyChatState(
            hasCharacter = state.chatCharacterId.isNotBlank(),
            appearance = state.appearance,
            onCreateChat = onCreateChat,
        )

        else -> {
            if (!webTranscriptReady) {
                ChatCenteredStatus(
                    text = "正在准备聊天渲染...",
                    appearance = state.appearance,
                )
                return
            }
            val presentationAlpha by animateFloatAsState(
                targetValue = if (presentationReadiness.revealed) 1f else 0f,
                animationSpec = snap(),
                label = "chat-presentation",
            )
            Box(Modifier.fillMaxSize()) {
                webContent(draft, presentationAlpha)
                if (webRendererFailed) {
                    ChatWebRendererFailure(
                        appearance = state.appearance,
                        onRetry = onRetryWebRenderer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatWebRendererFailure(
    appearance: AppearanceTheme,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.mobileChatBg.copy(alpha = 0.96f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(text = "聊天渲染失败", color = appearance.mobileText)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "未切换到另一套排版引擎",
            color = appearance.mobileMuted,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRetry) {
            Text(text = "重新加载", color = appearance.mobileBlue)
        }
    }
}

@Composable
internal fun ChatComposerBar(
    visible: Boolean,
    userBrowsedAwayFromBottom: Boolean,
    roleplayWebCanScrollForward: Boolean,
    appearance: AppearanceTheme,
    onJumpToBottom: () -> Unit,
    composer: @Composable (Modifier) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(100)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            composer(Modifier.fillMaxWidth())
            if (userBrowsedAwayFromBottom && roleplayWebCanScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp)
                        .offset(
                            y = -(ChatJumpToBottomButtonSize + ChatJumpToBottomButtonGap),
                        ),
                ) {
                    ChatJumpToBottomButton(
                        appearance = appearance,
                        onClick = onJumpToBottom,
                    )
                }
            }
        }
    }
}
