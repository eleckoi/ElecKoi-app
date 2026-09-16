package com.eleckoi.android.feature.chat.ui.screen

import com.eleckoi.android.feature.chat.ui.*

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.eleckoi.android.foundation.design.components.AppIconPaths
import com.eleckoi.android.foundation.design.components.DshIconPaths
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.components.StrokeSvgIcon
import com.eleckoi.android.foundation.design.components.noRippleClickable
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.data.rich.detectCompleteStreamingRichMessageDocument
import com.eleckoi.android.feature.chat.data.rich.detectRichMessageDocument
import com.eleckoi.android.feature.chat.ui.blocks.markdown.LocalMarkdownHostScrollInProgress
import com.eleckoi.android.feature.chat.ui.blocks.markdown.markdownCacheOwnerKey
import com.eleckoi.android.feature.chat.ui.layout.chatMessageSpacingAfter
import com.eleckoi.android.feature.chat.ui.message.ChatMessageRow
import com.eleckoi.android.feature.chat.ui.message.ChatTimelineItem
import com.eleckoi.android.feature.chat.ui.message.RoleplayToolbarController
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.appfont.ui.ProvideAppFont
import com.eleckoi.android.feature.chat.ui.blocks.markdown.layout.MarkdownRenderPlanEngine
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.sdk.author.AuthorChatGateway

internal data class ChatConversationListLayout(
    val listState: LazyListState,
    val endFollowBinding: LazyListEndFollowBinding,
    val bottomContentPadding: Dp,
    val keepFooterAnchoredOnItemResize: Boolean,
    val measuredItemHeightsPx: MutableMap<String, Int>,
    val onLiveReplyHeightChanged: (ChatVisualReplyKey, Int) -> Unit,
)

internal class ChatConversationListActions(
    val onAssistantVisualComplete: (String, Int) -> Unit,
    val onTimelineItemContentReady: (String) -> Unit,
    val onRegenerate: (ChatMessage) -> Unit,
    val onRegenerateImage: (String, String) -> Unit,
    val onSelectOpeningOption: (String) -> Unit,
    val onEdit: (ChatMessage) -> Unit,
    val onSelectText: (String) -> Unit,
    val onOpenUserAvatars: () -> Unit,
    val onOpenCharacterSettings: (String) -> Unit,
    val onSelectDeleteFrom: (String) -> Unit,
)

/**
 * Renders only the measured conversation list. Paging, follow ownership, and business intents stay
 * in ChatScreen; this component owns row geometry and message presentation.
 */
@Composable
internal fun ChatConversationList(
    state: ChatUiState,
    draft: ChatDraft,
    messages: List<ChatMessage>,
    timelineItems: List<ChatTimelineItem>,
    markdownCacheScopeKey: String,
    visualReplyState: ChatVisualReplyState,
    roleplayToolbarController: RoleplayToolbarController,
    staticExpansionObserver: (Any, Boolean) -> Unit,
    layout: ChatConversationListLayout,
    actions: ChatConversationListActions,
    authorGateway: AuthorChatGateway,
    modifier: Modifier = Modifier,
) {
    // Carries the user's font when they scoped it to chat text only. When the scope is the whole
    // app this is already provided further up and re-providing the same family costs nothing.
    ProvideAppFont(
        chatSubtree = true,
        onTypefaceChanged = MarkdownRenderPlanEngine::applyBodyTypeface,
    ) {
    CompositionLocalProvider(
        LocalStaticListExpansionObserver provides staticExpansionObserver,
        LocalMarkdownHostScrollInProgress provides layout.listState.isScrollInProgress,
    ) {
        val roleplay = state.chatLayoutMode == ChatLayoutMode.Roleplay
        val deleteFromIndex = remember(messages, state.deleteFromMessageId) {
            messages.indexOfFirst { it.id == state.deleteFromMessageId }
        }
        LazyColumn(
            state = layout.listState,
            verticalArrangement = Arrangement.Top,
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(layout.endFollowBinding.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = state.chatAreaHorizontalPadding.dp,
                top = if (roleplay) 0.dp else 24.dp,
                end = state.chatAreaHorizontalPadding.dp,
                bottom = layout.bottomContentPadding,
            ),
        ) {
            itemsIndexed(
                items = timelineItems,
                key = { _, item -> item.key },
                contentType = { _, item -> item.contentType },
            ) { itemIndex, timelineItem ->
                val sourceMessage = timelineItem.message
                val activePresentation = state.generationPresentation
                val generationOwnsMessage = state.isSending &&
                    activePresentation?.sessionId == draft.session.id &&
                    activePresentation.assistantMessageId == sourceMessage.id
                // `pending` is a durable crash checkpoint, not proof that work is still alive.
                // Stop settles the screen immediately while Room and the background Agent finish
                // their non-cancellable cleanup. If a stale checkpoint wins one Paging frame, do
                // not let it resurrect spinners after the active generation owner has gone away.
                val message = remember(sourceMessage, generationOwnsMessage) {
                    settleInactiveGenerationForPresentation(
                        message = sourceMessage,
                        generationOwnsMessage = generationOwnsMessage,
                        observedAtMillis = System.currentTimeMillis(),
                    )
                }
                val messageIndex = timelineItem.messageIndex
                val containsRichPlatformView = remember(message.content, message.pending) {
                    if (message.pending) {
                        detectCompleteStreamingRichMessageDocument(message.content)
                    } else {
                        detectRichMessageDocument(message.content)
                    } != null
                }
                KeepPlatformViewRowAliveNearViewport(
                    enabled = containsRichPlatformView,
                    itemIndex = itemIndex,
                    itemKey = timelineItem.key,
                    listState = layout.listState,
                )
                val liveReplyKey = visualReplyState.activeKey?.takeIf { key ->
                    key.messageId == message.id && timelineItem.isLastInMessage
                }
                ChatDeleteSelectionRow(
                    active = state.deleteMessagesOpen,
                    selected = isDeleteSuffixSelected(messageIndex, deleteFromIndex),
                    enabled = message.role != MessageRole.System && !message.pending,
                    isFirstInMessage = timelineItem.isFirstInMessage,
                    isLastInMessage = timelineItem.isLastInMessage,
                    appearance = state.appearance,
                    onSelect = { actions.onSelectDeleteFrom(message.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (
                                message.role == MessageRole.Assistant &&
                                message.pending &&
                                timelineItem.isLastInMessage
                            ) {
                                Modifier.animateItem(
                                    fadeInSpec = tween(150),
                                    placementSpec = null,
                                    fadeOutSpec = tween(90),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .onSizeChanged { size ->
                            val previousHeight = layout.measuredItemHeightsPx[timelineItem.key]
                            val heightChanged = previousHeight != size.height
                            if (heightChanged) {
                                layout.measuredItemHeightsPx[timelineItem.key] = size.height
                                if (layout.keepFooterAnchoredOnItemResize) {
                                    // requestScrollToItem participates in the next remeasure. Doing
                                    // this at the size boundary prevents a newly exposed cold row
                                    // from drawing one frame with the user tail under the composer.
                                    layout.listState.requestScrollToItem(timelineItems.size)
                                }
                            }
                            liveReplyKey?.let { layout.onLiveReplyHeightChanged(it, size.height) }
                        },
                ) {
                    ChatMessageRow(
                        message = message,
                        cacheOwnerKey = markdownCacheOwnerKey(
                            markdownCacheScopeKey,
                            message.id,
                        ),
                        character = draft.session.characterPersona,
                        appearance = state.appearance,
                        assistantBubbleEnabled = state.assistantBubbleEnabled,
                        layoutMode = state.chatLayoutMode,
                        roleplayCardPanel = state.chatRoleplayCardPanel,
                        chatAreaInset = state.chatAreaHorizontalPadding.dp,
                        bubbleCornerRadius = state.chatBubbleCornerRadius,
                        avatarSize = state.chatAvatarSize,
                        avatarShape = state.chatAvatarShape,
                        nameFontSize = state.chatNameFontSize,
                        nameAvatarSpacing = state.chatNameAvatarSpacing,
                        replySpacing = state.chatReplySpacing,
                        messageFontSize = state.chatMessageFontSize,
                        lineHeightMultiplier = state.chatLineHeightMultiplier,
                        letterSpacing = state.chatLetterSpacing,
                        paragraphSpacing = state.chatParagraphSpacing,
                        authorGateway = authorGateway,
                        roleplayToolbarController = roleplayToolbarController,
                        visualGeneration = visualReplyState.generationFor(message.id),
                        awaitingAssistantVisualCompletion =
                            visualReplyState.isCompleting &&
                                visualReplyState.activeKey?.messageId == message.id,
                        onAssistantVisualComplete = actions.onAssistantVisualComplete,
                        onContentReady = {
                            actions.onTimelineItemContentReady(timelineItem.key)
                        },
                        onRegenerate = actions.onRegenerate,
                        onRegenerateImage = actions.onRegenerateImage,
                        openingOptions = draft.openingOptions,
                        selectedOpeningOptionId = draft.selectedOpeningOptionId,
                        openingSelectionEnabled = draft.openingSelectionEnabled,
                        onSelectOpeningOption = actions.onSelectOpeningOption,
                        onEdit = actions.onEdit,
                        onSelectText = actions.onSelectText,
                        onUserAvatarClick = actions.onOpenUserAvatars,
                        onAssistantAvatarClick = {
                            draft.session.characterId
                                .takeIf(String::isNotBlank)
                                ?.let(actions.onOpenCharacterSettings)
                        },
                        fragment = timelineItem.fragment,
                        isFirstInMessage = timelineItem.isFirstInMessage,
                        isLastInMessage = timelineItem.isLastInMessage,
                    )
                }
                if (timelineItem.isLastInMessage) {
                    val spacingAfter = chatMessageSpacingAfter(
                        layoutMode = state.chatLayoutMode,
                        currentRole = message.role,
                        nextRole = messages.getOrNull(messageIndex + 1)?.role,
                        replySpacing = state.chatReplySpacing,
                        turnSpacing = state.chatTurnSpacing,
                    )
                    if (spacingAfter > 0f) {
                        Spacer(modifier = Modifier.height(spacingAfter.dp))
                    }
                }
            }
            item(ChatBottomAnchorKey) {
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
    }
}


@Composable
internal fun ChatCenteredStatus(
    text: String,
    appearance: AppearanceTheme,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = appearance.mobileMuted,
            fontSize = 15.sp,
        )
    }
}

@Composable
internal fun EmptyChatState(
    hasCharacter: Boolean,
    appearance: AppearanceTheme,
    onCreateChat: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledSvgIcon(
                paths = DshIconPaths.NewChat,
                color = appearance.mobileSoft,
                iconSize = 44.dp,
                viewportSize = DshIconPaths.Viewport16,
            )
            Text(
                text = if (hasCharacter) "还没有对话" else "还没有角色",
                color = appearance.mobileText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = if (hasCharacter) "点击新建，开始这段对话" else "请先创建角色",
                color = appearance.mobileMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (hasCharacter) {
                Row(
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .noRippleClickable(onClick = onCreateChat)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StrokeSvgIcon(
                        paths = AppIconPaths.Plus,
                        color = appearance.mobileAccentFg,
                        iconSize = 17.dp,
                        strokeWidth = 1.8f,
                    )
                    Text(
                        text = "新建对话",
                        color = appearance.mobileAccentFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
        }
    }
}
