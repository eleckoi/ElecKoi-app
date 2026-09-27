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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.eleckoi.android.foundation.design.components.ErrorDialog
import com.eleckoi.android.foundation.design.components.UnsavedChangesDialog
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
import kotlinx.coroutines.launch
import java.text.DecimalFormat

// Every value on this page describes what the chat looks like, so the page shows the chat. The
// preview follows the local draft immediately; storage changes only when the user saves.
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
    var editor by remember {
        mutableStateOf(ChatLayoutEditorState(stored, preferences.chatGenerationStatsEnabled))
    }
    val draft = editor.draft
    var confirmReset by remember { mutableStateOf(false) }
    var unsavedDialogOpen by remember { mutableStateOf(false) }
    var pendingBackAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var saving by remember { mutableStateOf(false) }
    var switchingMode by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var awaitingStored by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    val sectionStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(Unit) {
        try {
            val actual = viewModel.readStored()
            if (!editor.hasUnsavedChanges) {
                editor = ChatLayoutEditorState(
                    ChatLayoutDraft(actual),
                    actual.chatGenerationStatsEnabled,
                )
            }
            initialized = true
        } catch (error: Exception) {
            saveError = error.message ?: "聊天显示设置读取失败"
        }
    }
    LaunchedEffect(stored, preferences.chatGenerationStatsEnabled, awaitingStored) {
        if (awaitingStored) {
            if (stored == editor.draft &&
                preferences.chatGenerationStatsEnabled == editor.generationStatsEnabled
            ) awaitingStored = false
        } else if (!saving) {
            editor = editor.storedChanged(stored, preferences.chatGenerationStatsEnabled)
        }
    }

    // One route, its own little stack. Every section edits the same draft and watches the same
    // preview, so pushing them onto the app's navigator would mean threading that draft through it.
    var section by rememberSaveable { mutableStateOf<ChatDisplaySection?>(null) }
    fun saveDraft(onSaved: () -> Unit = {}) {
        if (!initialized || saving || switchingMode || !editor.hasUnsavedChanges) return
        val snapshot = editor
        saving = true
        scope.launch {
            try {
                val saved = viewModel.save(snapshot)
                editor = ChatLayoutEditorState(ChatLayoutDraft(saved), saved.chatGenerationStatsEnabled)
                awaitingStored = true
                unsavedDialogOpen = false
                onSaved()
                pendingBackAction = null
            } catch (error: Exception) {
                saveError = error.message ?: "聊天显示设置保存失败"
            } finally {
                saving = false
            }
        }
    }
    fun requestBack(action: () -> Unit) {
        if (saving || switchingMode) return
        if (editor.hasUnsavedChanges) {
            pendingBackAction = action
            unsavedDialogOpen = true
        } else {
            action()
        }
    }
    fun requestPageBack() = requestBack { if (section != null) section = null else onBack() }
    BackHandler(onBack = ::requestPageBack)

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
                            requestPageBack()
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
                val saveEnabled = initialized && editor.hasUnsavedChanges && !saving && !switchingMode
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .widthIn(min = 72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(appearance.mobileBlue.copy(alpha = if (saveEnabled) 1f else 0.38f))
                        .noRippleClickable(enabled = saveEnabled) { saveDraft() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (saving) "保存中" else "保存",
                        color = appearance.mobileAccentFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp),
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
                            enabled = editor.generationStatsEnabled,
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
                        generationStatsEnabled = editor.generationStatsEnabled,
                        onDraftChange = { change ->
                            val next = editor.draft.change()
                            if (initialized && !saving && !switchingMode) {
                                if (next.layoutMode == editor.selectedMode) {
                                    editor = editor.updated(change)
                                } else {
                                    switchingMode = true
                                    scope.launch {
                                        try {
                                            editor = editor.switched(
                                                next.layoutMode,
                                                viewModel.previewLayout(next.layoutMode),
                                            )
                                        } catch (error: Exception) {
                                            saveError = error.message ?: "布局预览加载失败"
                                        } finally {
                                            switchingMode = false
                                        }
                                    }
                                }
                            }
                        },
                        onOpenSection = { section = it },
                        onOpenMarkdownReadingColors = { requestBack(onOpenMarkdownReadingColors) },
                        onResetLayout = { confirmReset = true },
                        onGenerationStatsEnabledChange = { enabled ->
                            if (initialized && !saving) {
                                editor = editor.copy(generationStatsEnabled = enabled)
                            }
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
                            editor = editor.resetCurrentLayout()
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

    if (unsavedDialogOpen) {
        UnsavedChangesDialog(
            message = "离开前是否保存聊天显示的修改？",
            appearance = appearance,
            saving = saving,
            onSave = {
                val action = pendingBackAction
                saveDraft { action?.invoke() }
            },
            onDiscard = {
                val action = pendingBackAction
                editor = ChatLayoutEditorState(
                    editor.baselines.getValue(editor.storedMode),
                    editor.storedGenerationStatsEnabled,
                )
                pendingBackAction = null
                unsavedDialogOpen = false
                action?.invoke()
            },
            onCancel = {
                pendingBackAction = null
                unsavedDialogOpen = false
            },
        )
    }
    if (saveError.isNotBlank()) {
        ErrorDialog(
            message = saveError,
            appearance = appearance,
            onDismiss = { saveError = "" },
        )
    }
}

private val chatValueFormatter = DecimalFormat("0.#")

internal fun formatChatValue(value: Float): String = chatValueFormatter.format(value)
