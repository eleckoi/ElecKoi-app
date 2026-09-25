package com.eleckoi.android.feature.settings.ui.personalization.chat

import com.eleckoi.android.feature.settings.ui.personalization.components.*

import com.eleckoi.android.foundation.design.components.noRippleClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.ElecKoiDanger
import com.eleckoi.android.feature.chat.ui.layout.ChatLayoutPreview
import com.eleckoi.android.feature.chat.ui.layout.ChatPreviewMetrics
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBodyFontSizeSp
import com.eleckoi.android.feature.chat.ui.layout.resolveChatBodyLineHeightSp
import com.eleckoi.android.feature.preferences.ChatAvatarShape
import com.eleckoi.android.feature.preferences.ChatCodeBlockStyle
import com.eleckoi.android.feature.preferences.ChatLayoutMode
import com.eleckoi.android.feature.preferences.ChatReasoningDisplayMode
import com.eleckoi.android.feature.preferences.ChatTimelineThinkingAnimation
import com.eleckoi.android.feature.preferences.ChatToolTimelineStyle
import com.eleckoi.android.feature.preferences.UiPreferences
import com.eleckoi.android.feature.preferences.layoutDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat

// Every value on this page describes what the chat looks like, so the page shows the chat. The
// preview is pinned above the controls and driven by the local draft rather than by storage, so a
// slider moves it on the same frame while the write is debounced behind it.
internal data class ChatLayoutDraft(
    val layoutMode: ChatLayoutMode,
    val assistantBubbleEnabled: Boolean,
    val avatarShape: ChatAvatarShape,
    val roleplayCardPanel: Boolean,
    val roleplayTimestampsEnabled: Boolean,
    val roleplayMessageFloorsEnabled: Boolean,
    val cornerRadius: Float,
    val avatarSize: Float,
    val nameFontSize: Float,
    val nameSpacing: Float,
    val horizontalPadding: Float,
    val replySpacing: Float,
    val turnSpacing: Float,
    val fontSize: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val paragraphSpacing: Float,
    val timelineThinkingAnimation: ChatTimelineThinkingAnimation,
    val reasoningDisplayMode: ChatReasoningDisplayMode,
    val toolTimelineStyle: ChatToolTimelineStyle,
    val codeBlockStyle: ChatCodeBlockStyle,
    val codeBlockWrapEnabled: Boolean,
    val codeBlockShowAllEnabled: Boolean,
) {
    constructor(preferences: UiPreferences) : this(
        layoutMode = preferences.chatLayoutMode,
        assistantBubbleEnabled = preferences.assistantBubbleEnabled,
        avatarShape = preferences.chatAvatarShape,
        roleplayCardPanel = preferences.chatRoleplayCardPanel,
        roleplayTimestampsEnabled = preferences.chatRoleplayTimestampsEnabled,
        roleplayMessageFloorsEnabled = preferences.chatRoleplayMessageFloorsEnabled,
        cornerRadius = preferences.chatBubbleCornerRadius,
        avatarSize = preferences.chatAvatarSize,
        nameFontSize = preferences.chatNameFontSize,
        nameSpacing = preferences.chatNameAvatarSpacing,
        horizontalPadding = preferences.chatAreaHorizontalPadding,
        replySpacing = preferences.chatReplySpacing,
        turnSpacing = preferences.chatTurnSpacing,
        fontSize = preferences.chatMessageFontSize,
        lineHeight = preferences.chatLineHeightMultiplier,
        letterSpacing = preferences.chatLetterSpacing,
        paragraphSpacing = preferences.chatParagraphSpacing,
        timelineThinkingAnimation = preferences.chatTimelineThinkingAnimation,
        reasoningDisplayMode = preferences.chatReasoningDisplayMode,
        toolTimelineStyle = preferences.chatToolTimelineStyle,
        codeBlockStyle = preferences.chatCodeBlockStyle,
        codeBlockWrapEnabled = preferences.chatCodeBlockWrapEnabled,
        codeBlockShowAllEnabled = preferences.chatCodeBlockShowAllEnabled,
    )

    fun toMetrics(): ChatPreviewMetrics = ChatPreviewMetrics(
        assistantBubbleEnabled = assistantBubbleEnabled,
        cornerRadius = cornerRadius.dp,
        fontSize = resolveChatBodyFontSizeSp(fontSize).sp,
        lineHeight = resolveChatBodyLineHeightSp(fontSize, lineHeight).sp,
        letterSpacing = letterSpacing.sp,
        paragraphSpacing = paragraphSpacing.dp,
        replySpacing = replySpacing.dp,
        turnSpacing = turnSpacing.dp,
        avatarSize = avatarSize.dp,
        nameFontSize = nameFontSize.sp,
        nameSpacing = nameSpacing.dp,
        horizontalPadding = horizontalPadding.dp,
        layoutMode = layoutMode,
        avatarShape = resolvedAvatarShape,
        cardPanel = roleplayCardPanel,
        roleplayTimestampsEnabled = roleplayTimestampsEnabled,
        roleplayMessageFloorsEnabled = roleplayMessageFloorsEnabled,
    )

    // Switching to a layout that cannot hold a portrait avatar must not silently rewrite the stored
    // shape — go back to roleplay and the portrait is still the one selected.
    val resolvedAvatarShape: ChatAvatarShape
        get() = if (avatarShape.isSupportedBy(layoutMode)) {
            avatarShape
        } else {
            layoutMode.layoutDefaults.avatarShape
        }
}

@Composable
fun ChatDisplaySettingsPage(
    viewModel: ChatDisplaySettingsViewModel,
    appearance: AppearanceTheme,
    onOpenMarkdownReadingColors: () -> Unit,
    onBack: () -> Unit,
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val stored = ChatLayoutDraft(preferences)
    var draft by remember { mutableStateOf(stored) }
    var generationStatsEnabled by remember { mutableStateOf(preferences.chatGenerationStatsEnabled) }
    val defaults = draft.layoutMode.layoutDefaults
    var confirmReset by remember { mutableStateOf(false) }
    val sectionStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(stored) { draft = stored }
    LaunchedEffect(preferences.chatGenerationStatsEnabled) {
        generationStatsEnabled = preferences.chatGenerationStatsEnabled
    }
    LaunchedEffect(draft) {
        if (draft == stored) return@LaunchedEffect
        // A profile switch must load that profile's own values immediately. Debouncing the enum
        // leaves the old layout's numbers on screen and makes independent profiles look shared.
        if (draft.layoutMode != stored.layoutMode) {
            viewModel.selectLayoutMode(draft.layoutMode)
            return@LaunchedEffect
        }
        delay(250)
        viewModel.commitChangedLayout(draft, stored)
    }

    // One route, its own little stack. Every section edits the same draft and watches the same
    // preview, so pushing them onto the app's navigator would mean threading that draft through it.
    var section by rememberSaveable { mutableStateOf<ChatDisplaySection?>(null) }
    fun finishPage() {
        scope.launch {
            viewModel.commitChangedLayout(draft, stored)
            onBack()
        }
    }
    BackHandler { if (section != null) section = null else finishPage() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = appearance.mobileBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appearance.mobileBg)
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val openSection = section
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = if (openSection == null) "返回设置" else "返回聊天显示",
                    tint = appearance.mobileText,
                    modifier = Modifier
                        .noRippleClickable {
                            if (openSection == null) finishPage() else section = null
                        }
                        .padding(end = 10.dp, bottom = 5.dp)
                        .size(22.dp),
                )
                Text(
                    openSection?.titleFor(draft.layoutMode) ?: "聊天显示",
                    modifier = Modifier.weight(1f),
                    color = appearance.mobileText,
                    fontSize = if (openSection != null) 22.sp else 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (openSection == null) {
                    Text(
                        "恢复当前布局",
                        color = appearance.mobileMuted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .noRippleClickable { confirmReset = true }
                            .padding(bottom = 4.dp),
                    )
                }
            }
        },
    ) { paddingValues ->
        // Scrolls with the controls rather than staying pinned. Each section is short enough that
        // the preview is on screen while you work anyway, and a pinned copy just spends a third of
        // the screen proving it.
        sectionStateHolder.SaveableStateProvider(section?.name ?: "chat-display-hub") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Fixed height on purpose: letting the preview grow with the font size would shove
                // the controls around while you are still dragging one of them.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(248.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    when (section) {
                        ChatDisplaySection.ToolTimeline -> AiAssistantTimelinePreview(
                            style = draft.toolTimelineStyle,
                            appearance = appearance,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ChatDisplaySection.WaitingAnimation -> TimelineAnimationPreview(
                            thinkingAnimation = draft.timelineThinkingAnimation,
                            appearance = appearance,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ChatDisplaySection.GenerationStats -> ChatGenerationStatsPreview(
                            enabled = generationStatsEnabled,
                            appearance = appearance,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ChatDisplaySection.ReasoningDisplay -> ChatReasoningDisplayPreview(
                            mode = draft.reasoningDisplayMode,
                            appearance = appearance,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ChatDisplaySection.CodeBlockStyle -> ChatCodeBlockPreview(
                            style = draft.codeBlockStyle,
                            wrapLines = draft.codeBlockWrapEnabled,
                            showAll = draft.codeBlockShowAllEnabled,
                            appearance = appearance,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> ChatLayoutPreview(
                            appearance = appearance,
                            metrics = draft.toMetrics(),
                            assistantName = "AI",
                            userName = "我",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                ) {
                    ChatDisplaySettingsControls(
                        section = section,
                        draft = draft,
                        appearance = appearance,
                        generationStatsEnabled = generationStatsEnabled,
                        onDraftChange = { draft = it },
                        onOpenSection = { section = it },
                        onOpenMarkdownReadingColors = onOpenMarkdownReadingColors,
                        onGenerationStatsEnabledChange = { enabled ->
                            generationStatsEnabled = enabled
                            scope.launch { viewModel.setGenerationStatsEnabled(enabled) }
                        },
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = appearance.mobileSurface,
            title = { Text("恢复当前布局默认值", color = appearance.mobileText, fontSize = 17.sp) },
            text = {
                Text(
                    "只把${draft.layoutMode.label}的气泡、头像、间距、文字和等待动画恢复默认，" +
                        "另外两种布局不会改变。",
                    color = appearance.mobileMuted,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                Text(
                    "恢复",
                    color = ElecKoiDanger,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .noRippleClickable {
                            confirmReset = false
                            scope.launch { viewModel.resetLayout(draft.layoutMode) }
                        }
                        .padding(12.dp),
                )
            },
            dismissButton = {
                Text(
                    "取消",
                    color = appearance.mobileMuted,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .noRippleClickable { confirmReset = false }
                        .padding(12.dp),
                )
            },
        )
    }
}

private val chatValueFormatter = DecimalFormat("0.#")

internal fun formatChatValue(value: Float): String = chatValueFormatter.format(value)
