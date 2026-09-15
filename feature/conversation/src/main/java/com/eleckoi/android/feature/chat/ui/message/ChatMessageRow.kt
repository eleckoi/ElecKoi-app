package com.eleckoi.android.feature.chat.ui.message

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatOpeningOption
import com.eleckoi.android.feature.chat.roleplay.protocol.stripRoleplayImageMarkers
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatLayoutDefaults
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBodyFontSizeSp
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBodyLineHeightSp
import com.eleckoi.android.feature.chat.ui.layout.SocialBubbleTailWidth
import com.eleckoi.android.feature.chat.ui.layout.SocialChatBubbleShape
import com.eleckoi.android.sdk.author.AuthorChatGateway

@Composable
internal fun ChatMessageRow(
    message: ChatMessage,
    character: CharacterCard,
    appearance: AppearanceTheme,
    assistantBubbleEnabled: Boolean,
    layoutMode: ChatLayoutMode,
    roleplayCardPanel: Boolean,
    chatAreaInset: Dp,
    bubbleCornerRadius: Float,
    avatarSize: Float,
    avatarShape: ChatAvatarShape,
    nameFontSize: Float,
    nameAvatarSpacing: Float,
    replySpacing: Float,
    messageFontSize: Float,
    lineHeightMultiplier: Float,
    letterSpacing: Float,
    paragraphSpacing: Float,
    authorGateway: AuthorChatGateway,
    roleplayToolbarController: RoleplayToolbarController,
    onRegenerate: (ChatMessage) -> Unit,
    onRegenerateImage: (String, String) -> Unit,
    openingOptions: List<ChatOpeningOption>,
    selectedOpeningOptionId: String,
    openingSelectionEnabled: Boolean,
    onSelectOpeningOption: (String) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onSelectText: (String) -> Unit,
    onUserAvatarClick: () -> Unit = {},
    onAssistantAvatarClick: () -> Unit = {},
    visualGeneration: Int = 0,
    awaitingAssistantVisualCompletion: Boolean = false,
    onAssistantVisualComplete: (String, Int) -> Unit = { _, _ -> },
    onContentReady: () -> Unit = {},
    cacheOwnerKey: String = message.id,
    fragment: ChatMessageFragment? = null,
    isFirstInMessage: Boolean = true,
    isLastInMessage: Boolean = true,
) {
    val presentation = rememberChatMessageRowPresentation(
        message = message,
        character = character,
        appearance = appearance,
        layoutMode = layoutMode,
        avatarShape = avatarShape,
        openingOptions = openingOptions,
        selectedOpeningOptionId = selectedOpeningOptionId,
        openingSelectionEnabled = openingSelectionEnabled,
    )
    val isUser = presentation.isUser
    val isOpening = presentation.isOpening
    val roleplay = presentation.roleplay
    val social = presentation.social
    val readingAppearance = presentation.readingAppearance
    val wideBubbleLayout = presentation.wideBubbleLayout
    val selectedOpeningIndex = presentation.selectedOpeningIndex
    val openingPagerVisible = presentation.openingPagerVisible
    val name = presentation.name
    val avatarPath = presentation.avatarPath
    val displayMessage = presentation.displayMessage
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }
    var userMenuOpen by remember(message.id) { mutableStateOf(false) }
    var visualComplete by remember(
        message.id,
        visualGeneration,
        awaitingAssistantVisualCompletion,
    ) {
        mutableStateOf(
            isUser || (!message.pending && !awaitingAssistantVisualCompletion),
        )
    }
    val streamingBodyEligible = shouldAnimateChatStreamingBody(
        isUser = isUser,
        fragmented = fragment != null,
    )
    var streamingBodyMotionArmed by remember(message.id, visualGeneration) {
        mutableStateOf(message.pending && streamingBodyEligible)
    }
    var streamingBodySizeSettled by remember(message.id, visualGeneration) {
        mutableStateOf(true)
    }
    var streamingBodyTargetSize by remember(message.id, visualGeneration) {
        mutableStateOf<IntSize?>(null)
    }
    val contentState = rememberChatMessageContentState(displayMessage)
    val fontSize = resolveChatBodyFontSizeSp(messageFontSize).sp
    val lineHeight = resolveChatBodyLineHeightSp(messageFontSize, lineHeightMultiplier).sp
    val textLetterSpacing = letterSpacing.coerceIn(-1f, 4f).sp
    val resolvedNameFontSize = nameFontSize.coerceIn(
        ChatLayoutDefaults.NameFontSizeMin,
        ChatLayoutDefaults.NameFontSizeMax,
    )
    val avatarNameGap = nameAvatarSpacing.coerceIn(0f, 20f).dp
    LaunchedEffect(message.pending, visualGeneration) {
        if (message.pending) {
            visualComplete = false
        }
    }
    LaunchedEffect(message.pending, streamingBodyEligible) {
        if (message.pending && streamingBodyEligible) {
            // Keep the same motion owner through the final Markdown hand-off. Removing the size
            // modifier as soon as `pending` flips would snap an in-flight spring to its target.
            streamingBodyMotionArmed = true
        }
    }

    LaunchedEffect(
        visualComplete,
        streamingBodySizeSettled,
        message.pending,
        awaitingAssistantVisualCompletion,
        visualGeneration,
        isLastInMessage,
    ) {
        val completionCandidate =
            !isUser &&
            isLastInMessage &&
            !message.pending &&
            visualComplete &&
            awaitingAssistantVisualCompletion
        if (completionCandidate) {
            // Give the final content one real layout pass to publish a changed target size. If that
            // arms the size spring, this effect is cancelled and restarts only after it settles.
            withFrameNanos { }
        }
        if (completionCandidate && streamingBodySizeSettled) {
            onAssistantVisualComplete(message.id, visualGeneration)
        }
    }

    fun copyMessage() {
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText("ElecKoi message", stripRoleplayImageMarkers(displayMessage.content)),
        )
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxWidth()) {
            val avatar: @Composable () -> Unit = {
                AvatarBubble(
                    name = name,
                    avatarPath = avatarPath,
                    appearance = readingAppearance,
                    size = avatarSize,
                    shape = avatarShape,
                    onClick = if (isUser) onUserAvatarClick else onAssistantAvatarClick,
                )
            }
            val nameText: @Composable (Modifier) -> Unit = { modifier ->
                Text(
                    text = name,
                    modifier = modifier,
                    color = readingAppearance.mobileText,
                    fontSize = resolvedNameFontSize.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = ChatLayoutDefaults.nameLineHeight(resolvedNameFontSize).sp,
                )
            }
            val messageBubble: @Composable (Modifier) -> Unit = { widthModifier ->
                // The roleplay layout has no bubble by definition — a panel, when it has one, wraps
                // the whole turn rather than just the text.
                val inlineAgentProcessVisible = fragment == null && shouldShowInlineAgentProcess(
                    message = message,
                    displayedText = contentState.displayedText,
                )
                val assistantBodyVisible = fragment != null ||
                    contentState.displayedText.isNotBlank() ||
                    message.imageAttachments.isNotEmpty() ||
                    inlineAgentProcessVisible
                val bubbleVisible = (isUser || assistantBubbleEnabled) &&
                    !roleplay &&
                    (isUser || assistantBodyVisible)
                // Long assistant documents are virtualized as one LazyColumn item per Markdown
                // block. Every item must still occupy the same constrained bubble width;
                // otherwise each Surface wraps its own block and the shared right edge becomes
                // jagged while the left edge remains aligned.
                val sharedFragmentWidthModifier = if (fragment != null && bubbleVisible) {
                    widthModifier.fillMaxWidth()
                } else {
                    widthModifier
                }
                val reasoningOnly = !isUser &&
                    displayMessage.content.isBlank() &&
                    displayMessage.reasoningContent.isNotBlank()
                val bubbleHorizontalPadding = if (reasoningOnly) 8.dp else 12.dp
                val bubbleVerticalPadding = when {
                    reasoningOnly -> 8.dp
                    social -> 9.dp
                    else -> 12.dp
                }
                val bubbleStartPadding = bubbleHorizontalPadding +
                    if (social && !isUser) SocialBubbleTailWidth else 0.dp
                val bubbleEndPadding = bubbleHorizontalPadding +
                    if (social && isUser) SocialBubbleTailWidth else 0.dp
                val fragmentGap = 7.dp
                val contentModifier = when {
                    fragment == null && bubbleVisible -> Modifier.padding(
                        start = bubbleStartPadding,
                        top = bubbleVerticalPadding,
                        end = bubbleEndPadding,
                        bottom = bubbleVerticalPadding,
                    )
                    fragment == null -> Modifier
                    bubbleVisible -> Modifier.padding(
                        start = bubbleStartPadding,
                        top = if (isFirstInMessage) bubbleVerticalPadding else fragmentGap / 2,
                        end = bubbleEndPadding,
                        bottom = if (isLastInMessage) bubbleVerticalPadding else fragmentGap / 2,
                    )
                    isFirstInMessage -> Modifier
                    else -> Modifier.padding(top = fragmentGap)
                }
                val content: @Composable () -> Unit = {
                    val markContentReady = onContentReady
                    val markVisualComplete = {
                        visualComplete = true
                    }
                    if (fragment == null) {
                        ChatMessageContent(
                            message = displayMessage,
                            state = contentState,
                            appearance = readingAppearance,
                            modifier = contentModifier,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            letterSpacing = textLetterSpacing,
                            authorGateway = authorGateway,
                            paragraphSpacing = paragraphSpacing,
                            messageContainerVisible = bubbleVisible,
                            visualGeneration = visualGeneration,
                            onContentReady = markContentReady,
                            onVisualComplete = markVisualComplete,
                            cacheOwnerKey = cacheOwnerKey,
                            onRegenerateImage = { attachmentId ->
                                onRegenerateImage(message.id, attachmentId)
                            },
                        )
                    } else {
                        ChatMessageFragmentContent(
                            fragment = fragment,
                            appearance = readingAppearance,
                            modifier = contentModifier,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            letterSpacing = textLetterSpacing,
                            paragraphSpacing = paragraphSpacing,
                            messageContainerVisible = bubbleVisible,
                            visualGeneration = visualGeneration,
                            onContentReady = markContentReady,
                            onVisualComplete = markVisualComplete,
                            onRegenerateImage = { attachmentId ->
                                onRegenerateImage(message.id, attachmentId)
                            },
                        )
                    }
                }
                val body: @Composable () -> Unit = {
                    val bodySizeModifier = when {
                        streamingBodyMotionArmed -> Modifier.animateContentSize(
                            animationSpec = ChatStreamingBodySizeAnimationSpec,
                            alignment = Alignment.TopStart,
                            finishedListener = { _, _ ->
                                streamingBodySizeSettled = true
                            },
                        )
                        isOpening -> Modifier.animateContentSize(
                            animationSpec = tween(
                                durationMillis = OpeningPageTransitionDurationMillis,
                                easing = OpeningPageTransitionEasing,
                            ),
                            alignment = Alignment.TopStart,
                        )
                        else -> Modifier
                    }
                    Box(
                        modifier = bodySizeModifier.then(
                            if (bubbleVisible) Modifier.heightIn(min = 44.dp) else Modifier,
                        ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = if (streamingBodyMotionArmed) {
                                Modifier.onSizeChanged { size ->
                                    val previous = streamingBodyTargetSize
                                    if (previous != null && previous != size) {
                                        streamingBodySizeSettled = false
                                    }
                                    streamingBodyTargetSize = size
                                }
                            } else {
                                Modifier
                            },
                        ) {
                            if (isUser) content() else SelectionContainer { content() }
                        }
                    }
                }
                Box {
                    if (bubbleVisible) {
                        Surface(
                            modifier = sharedFragmentWidthModifier.then(
                                if (isUser) {
                                    Modifier.userMessageActions(
                                        onClick = { onEdit(message) },
                                        onLongClick = { userMenuOpen = true },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                            color = if (isUser) {
                                appearance.mobileChatUserBg
                            } else {
                                appearance.mobileChatMessageBg
                            },
                            shape = if (social) {
                                SocialChatBubbleShape(
                                    user = isUser,
                                    cornerRadius = bubbleCornerRadius.coerceIn(0f, 24f).dp,
                                    roundTop = fragment == null || isFirstInMessage,
                                    roundBottom = fragment == null || isLastInMessage,
                                    tailVisible = fragment == null || isFirstInMessage,
                                    tailCenterY = avatarShape.heightFor(avatarSize.dp) / 2f,
                                )
                            } else if (fragment == null) {
                                RoundedCornerShape(bubbleCornerRadius.coerceIn(0f, 24f).dp)
                            } else {
                                val radius = bubbleCornerRadius.coerceIn(0f, 24f).dp
                                RoundedCornerShape(
                                    topStart = if (isFirstInMessage) radius else 0.dp,
                                    topEnd = if (isFirstInMessage) radius else 0.dp,
                                    bottomStart = if (isLastInMessage) radius else 0.dp,
                                    bottomEnd = if (isLastInMessage) radius else 0.dp,
                                )
                            },
                        ) {
                            body()
                        }
                    } else {
                        Box(modifier = sharedFragmentWidthModifier) { body() }
                    }
                    if (isUser && !message.pending) {
                        UserMessageMenu(
                            expanded = userMenuOpen,
                            appearance = appearance,
                            onDismiss = { userMenuOpen = false },
                            onCopy = {
                                userMenuOpen = false
                                copyMessage()
                            },
                            onSelectText = {
                                userMenuOpen = false
                                onSelectText(stripRoleplayImageMarkers(displayMessage.content))
                            },
                            onEdit = {
                                userMenuOpen = false
                                onEdit(message)
                            },
                        )
                    }
                }
            }
            val assistantTools: @Composable () -> Unit = {
                val ownsAssistantFooter = isLastInMessage && !isUser
                Box(
                    modifier = Modifier.height(if (ownsAssistantFooter) AssistantFooterHeight else 0.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    AnimatedVisibility(
                        visible = ownsAssistantFooter &&
                            !message.pending &&
                            visualComplete,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = 220, delayMillis = 40),
                        ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 70)),
                    ) {
                        AssistantTools(
                            message = message,
                            appearance = appearance,
                            regenerateEnabled = !isOpening,
                            onRegenerate = onRegenerate,
                            onCopy = ::copyMessage,
                            openingOptions = if (openingPagerVisible) openingOptions else emptyList(),
                            selectedOpeningIndex = selectedOpeningIndex,
                            onSelectOpeningOption = onSelectOpeningOption,
                        )
                    }
                }
            }

            when {
                roleplay -> RoleplayMessageLayout(
                    message = message,
                    appearance = readingAppearance,
                    toolbarController = roleplayToolbarController,
                    avatarSize = avatarSize,
                    avatarShape = avatarShape,
                    avatarNameGap = avatarNameGap,
                    replySpacing = replySpacing,
                    cardPanel = roleplayCardPanel,
                    chatAreaInset = chatAreaInset,
                    isUser = isUser,
                    isOpening = isOpening,
                    isFirstInMessage = isFirstInMessage,
                    openingPagerVisible = openingPagerVisible,
                    openingOptions = openingOptions,
                    selectedOpeningIndex = selectedOpeningIndex,
                    onSelectOpeningOption = onSelectOpeningOption,
                    onRegenerate = onRegenerate,
                    onEdit = onEdit,
                    onCopy = ::copyMessage,
                    avatar = avatar,
                    nameText = nameText,
                    messageBubble = messageBubble,
                )
                wideBubbleLayout -> WideChatMessageLayout(
                    isUser = isUser,
                    isFirstInMessage = isFirstInMessage,
                    avatarNameGap = avatarNameGap,
                    avatar = avatar,
                    nameText = nameText,
                    messageBubble = messageBubble,
                    assistantTools = assistantTools,
                )
                else -> SocialChatMessageLayout(
                    isUser = isUser,
                    isFirstInMessage = isFirstInMessage,
                    avatarSize = avatarSize,
                    avatarNameGap = avatarNameGap,
                    avatar = avatar,
                    messageBubble = messageBubble,
                )
            }
    }
}
