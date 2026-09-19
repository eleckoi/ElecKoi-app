package com.eleckoi.android.feature.chat.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.components.FilledSvgIcon
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.PhosphorRegular
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatLayoutDefaults
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.feature.preferences.UiPreferencesRepository

// Background and layout editors need to show what the real chat will look like. Every metric here
// is read from the user's own chat layout preferences rather than guessed, so a preview is the
// real thing — change the bubble radius in chat settings and every preview follows.
data class ChatPreviewMetrics(
    val assistantBubbleEnabled: Boolean,
    val cornerRadius: Dp,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val letterSpacing: TextUnit,
    val paragraphSpacing: Dp,
    val replySpacing: Dp,
    val turnSpacing: Dp,
    val avatarSize: Dp,
    val nameFontSize: TextUnit,
    val nameSpacing: Dp,
    val horizontalPadding: Dp,
    val layoutMode: ChatLayoutMode,
    val avatarShape: ChatAvatarShape,
    val cardPanel: Boolean,
)

@Composable
fun rememberChatPreviewMetrics(): ChatPreviewMetrics {
    val context = LocalContext.current
    val repository = remember(context) { UiPreferencesRepository(context) }
    val preferences by repository.preferencesFlow.collectAsState(initial = UiPreferences())
    val fontSize = resolveChatBodyFontSizeSp(preferences.chatMessageFontSize)
    val multiplier = preferences.chatLineHeightMultiplier.coerceIn(0.8f, 2f)
    return ChatPreviewMetrics(
        assistantBubbleEnabled = preferences.assistantBubbleEnabled,
        cornerRadius = preferences.chatBubbleCornerRadius.coerceIn(0f, 24f).dp,
        fontSize = fontSize.sp,
        lineHeight = resolveChatBodyLineHeightSp(fontSize, multiplier).sp,
        letterSpacing = preferences.chatLetterSpacing.coerceIn(-1f, 4f).sp,
        paragraphSpacing = preferences.chatParagraphSpacing.coerceIn(0f, 24f).dp,
        replySpacing = preferences.chatReplySpacing.coerceIn(0f, 40f).dp,
        turnSpacing = preferences.chatTurnSpacing.coerceIn(0f, 40f).dp,
        avatarSize = preferences.chatAvatarSize.coerceIn(
            ChatLayoutDefaults.AvatarSizeMin,
            ChatLayoutDefaults.AvatarSizeMax,
        ).dp,
        nameFontSize = preferences.chatNameFontSize
            .coerceIn(ChatLayoutDefaults.NameFontSizeMin, ChatLayoutDefaults.NameFontSizeMax)
            .sp,
        nameSpacing = preferences.chatNameAvatarSpacing.coerceIn(0f, 24f).dp,
        horizontalPadding = preferences.chatAreaHorizontalPadding.coerceIn(0f, 40f).dp,
        layoutMode = preferences.chatLayoutMode,
        avatarShape = preferences.resolvedChatAvatarShape,
        cardPanel = preferences.chatRoleplayCardPanel,
    )
}

@Composable
fun ChatPreviewBubble(
    text: String,
    user: Boolean,
    appearance: AppearanceTheme,
    metrics: ChatPreviewMetrics,
    modifier: Modifier = Modifier,
) {
    val roleplay = metrics.layoutMode == ChatLayoutMode.Roleplay
    val bubbleVisible = !roleplay && (user || metrics.assistantBubbleEnabled)
    val bubblePalette = resolveChatBubblePalette(appearance, metrics.layoutMode, user)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (user && !roleplay) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = (if (bubbleVisible) Modifier.fillMaxWidth(0.78f) else Modifier.fillMaxWidth())
                .then(
                    if (bubbleVisible) {
                        Modifier
                            .clip(RoundedCornerShape(metrics.cornerRadius))
                            .background(bubblePalette.container)
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                text,
                color = when {
                    !bubbleVisible -> appearance.mobileText
                    else -> bubblePalette.content
                },
                fontSize = metrics.fontSize,
                lineHeight = metrics.lineHeight,
                letterSpacing = metrics.letterSpacing,
            )
        }
    }
}

// A miniature two-turn conversation that every chat layout preference feeds into, so the settings
// page can show the effect of a slider instead of describing it in a sentence.
@Composable
fun ChatLayoutPreview(
    appearance: AppearanceTheme,
    metrics: ChatPreviewMetrics,
    assistantName: String,
    userName: String,
    modifier: Modifier = Modifier,
    backgroundOverride: Color? = null,
) {
    val previewAppearance = remember(appearance, metrics.layoutMode, backgroundOverride) {
        if (backgroundOverride != null) {
            // The settings figure compares layout geometry, not the roleplay reading veil. Keep
            // its plate neutral while real roleplay chat continues to use the dark reading theme.
            appearance.copy(
                mobileChatBg = backgroundOverride,
                mobileChatHeaderBg = backgroundOverride,
            )
        } else if (metrics.layoutMode == ChatLayoutMode.Roleplay) {
            appearance.asRoleplayReadingTheme()
        } else {
            appearance
        }
    }
    // Scrollable, and stuck to the bottom whenever the content grows. Bottom alignment alone cannot
    // do this: once the content is taller than the box, child offsets cannot go negative, so the
    // overflow spills past the bottom edge and gets clipped either way. Scrolling also lets the
    // reader go back over the whole preview when large metrics push it past one screen.
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier
            .background(previewAppearance.mobileChatBg)
            .verticalScroll(scrollState)
            .padding(horizontal = metrics.horizontalPadding, vertical = 14.dp),
    ) {
        PreviewTurn(
            name = assistantName,
            user = false,
            appearance = previewAppearance,
            metrics = metrics,
        ) {
            PreviewBody("调一下滑杆，这里会跟着变。", previewAppearance, metrics, user = false)
            Spacer(modifier = Modifier.height(metrics.paragraphSpacing))
            PreviewBody("字距和段距也能在这看到。", previewAppearance, metrics, user = false)
        }
        Spacer(modifier = Modifier.height(metrics.turnSpacing))
        PreviewTurn(
            name = userName,
            user = true,
            appearance = previewAppearance,
            metrics = metrics,
        ) {
            PreviewBody("我发出去的消息长这样", previewAppearance, metrics, user = true)
        }
    }
}

@Composable
private fun PreviewTurn(
    name: String,
    user: Boolean,
    appearance: AppearanceTheme,
    metrics: ChatPreviewMetrics,
    content: @Composable () -> Unit,
) {
    val roleplay = metrics.layoutMode == ChatLayoutMode.Roleplay
    val social = metrics.layoutMode == ChatLayoutMode.Social
    val bubbleVisible = !roleplay && (user || metrics.assistantBubbleEnabled)
    val bubblePalette = resolveChatBubblePalette(appearance, metrics.layoutMode, user)
    val bubble: @Composable () -> Unit = {
        if (bubbleVisible) {
            val shape = if (social) {
                SocialChatBubbleShape(
                    user = user,
                    cornerRadius = minOf(metrics.cornerRadius, SocialBubbleCornerRadiusMax),
                    tailCenterY = metrics.avatarShape.heightFor(metrics.avatarSize) / 2f,
                )
            } else {
                RoundedCornerShape(metrics.cornerRadius)
            }
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(bubblePalette.container)
                    .padding(
                        start = 12.dp + if (social && !user) SocialBubbleTailWidth else 0.dp,
                        top = 9.dp,
                        end = 12.dp + if (social && user) SocialBubbleTailWidth else 0.dp,
                        bottom = 9.dp,
                    ),
            ) {
                Column { content() }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
    val avatar: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .width(metrics.avatarSize)
                .height(metrics.avatarShape.heightFor(metrics.avatarSize))
                .clip(metrics.avatarShape.shape(metrics.avatarSize))
                .background(appearance.mobileSearchBg),
            contentAlignment = Alignment.Center,
        ) {
            FilledSvgIcon(
                paths = listOf(PhosphorRegular.UserFill),
                color = appearance.mobileSoft,
                iconSize = metrics.avatarSize * 0.58f,
                viewportSize = 256f,
            )
        }
    }
    val label: @Composable () -> Unit = {
        Text(
            name,
            // Without a bubble the name is the only thing marking where a turn starts, so it carries
            // the weight the bubble edge used to.
            color = if (roleplay) appearance.mobileText else appearance.mobileMuted,
            fontSize = metrics.nameFontSize,
            fontWeight = if (roleplay) FontWeight.Medium else FontWeight.Normal,
            lineHeight = ChatLayoutDefaults.nameLineHeight(metrics.nameFontSize.value).sp,
        )
    }

    if (roleplay) {
        // One column for the portrait, one for everything the character says. Both speakers use it —
        // side-switching is what a messaging app does, and this is meant to read like a script.
        val turn: @Composable () -> Unit = {
            Row(modifier = Modifier.fillMaxWidth()) {
                avatar()
                Spacer(modifier = Modifier.width(metrics.nameSpacing))
                Column(modifier = Modifier.fillMaxWidth()) {
                    label()
                    Spacer(modifier = Modifier.height(metrics.replySpacing))
                    content()
                }
            }
        }
        if (metrics.cardPanel) {
            // The panel wraps the whole turn, avatar included — that is what separates it from a
            // bubble, which only ever wraps the text.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(metrics.cornerRadius))
                    .background(appearance.mobileChatMessageBg)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                turn()
            }
        } else {
            turn()
        }
    } else if (metrics.layoutMode.usesFullWidthBody) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (user) {
                    label()
                    Spacer(modifier = Modifier.width(metrics.nameSpacing))
                    avatar()
                } else {
                    avatar()
                    Spacer(modifier = Modifier.width(metrics.nameSpacing))
                    label()
                }
            }
            Spacer(modifier = Modifier.height(metrics.replySpacing))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                bubble()
            }
        }
    } else {
        // A social message starts beside its avatar; matching empty avatar slots on the opposite
        // side keep both speakers inside the same maximum-width message lane.
        val avatarSlotWidth = metrics.avatarSize + metrics.nameSpacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (!user) {
                avatar()
                Spacer(modifier = Modifier.width(metrics.nameSpacing))
            } else {
                Spacer(modifier = Modifier.width(avatarSlotWidth))
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = if (user) Alignment.TopEnd else Alignment.TopStart,
            ) {
                bubble()
            }
            if (user) {
                Spacer(modifier = Modifier.width(metrics.nameSpacing))
                avatar()
            } else {
                Spacer(modifier = Modifier.width(avatarSlotWidth))
            }
        }
    }
}

@Composable
private fun PreviewBody(
    text: String,
    appearance: AppearanceTheme,
    metrics: ChatPreviewMetrics,
    user: Boolean,
) {
    val bubbleVisible = metrics.layoutMode != ChatLayoutMode.Roleplay &&
        (user || metrics.assistantBubbleEnabled)
    val bubblePalette = resolveChatBubblePalette(appearance, metrics.layoutMode, user)
    Text(
        text,
        // The bubble foreground colours are picked to sit on the bubble. With no bubble under it the
        // text sits on the chat background instead, where the page's own text colour is the one that
        // was contrast-checked.
        color = when {
            !bubbleVisible -> appearance.mobileText
            else -> bubblePalette.content
        },
        fontSize = metrics.fontSize,
        lineHeight = metrics.lineHeight,
        letterSpacing = metrics.letterSpacing,
    )
}
