package com.eleckoi.android.feature.chat.ui.screen

import com.eleckoi.android.feature.chat.ui.ChatIntent
import com.eleckoi.android.feature.chat.ui.ChatViewModel
import com.eleckoi.android.feature.chat.ui.LocalChatRenderingPreferences

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.eleckoi.android.feature.characters.model.AppDefaultChatBackground
import com.eleckoi.android.feature.characters.model.CustomChatBackground
import com.eleckoi.android.feature.characters.model.GlobalChatBackground
import com.eleckoi.android.feature.preferences.NewCharacterBackground
import com.eleckoi.android.feature.chat.ui.layout.ChatBackdropSpec
import com.eleckoi.android.feature.chat.ui.layout.ChatBackground
import com.eleckoi.android.feature.chat.ui.layout.asRoleplayReadingTheme
import com.eleckoi.android.feature.chat.ui.trajectory.trajectoryRuntimeThreadId
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.data.MaxChatInputImages
import com.eleckoi.android.foundation.design.components.FocusDismissRegistry
import com.eleckoi.android.foundation.design.components.LocalFocusDismissRegistry
import com.eleckoi.android.foundation.design.components.clearFocusOnBlankTap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eleckoi.android.feature.chat.ui.immersive.ImmersiveChatScreen
import com.eleckoi.android.engine.immersive.security.AuthorFrontendStoragePrincipal
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onOpenPresets: () -> Unit = {},
    dynamicSettingsSessionIds: Set<String> = emptySet(),
    onOpenDynamicSettings: (characterId: String, sessionId: String) -> Unit = { _, _ -> },
    onOpenUserAvatars: () -> Unit = {},
    onOpenCharacterSettings: (String) -> Unit = {},
    newCharacterBackground: NewCharacterBackground = NewCharacterBackground.Default,
    onNewCharacterBackgroundChange: (NewCharacterBackground) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaxChatInputImages),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onIntent(ChatIntent.AddInputImages(uris.map { it.toString() }))
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val frontendWorkspace by viewModel.frontendWorkspace.collectAsStateWithLifecycle()
    val immersiveProject = frontendWorkspace.selectedProject
    SyncChatOrientation(allowLandscape = immersiveProject != null)
    if (immersiveProject != null) {
        val projectDirectory = viewModel.frontendProjectDirectory(immersiveProject.id)
        if (projectDirectory != null) {
            ImmersiveChatScreen(
                project = immersiveProject,
                projectDirectory = projectDirectory,
                characterName = state.chatCharacterName,
                chatGateway = viewModel,
                appearance = state.appearance,
                storagePrincipal = AuthorFrontendStoragePrincipal.publishedProject(immersiveProject.id),
                onExit = onBack,
                onFallbackToNative = viewModel::clearFrontendProject,
            )
            return
        } else {
            LaunchedEffect(immersiveProject.id) {
                viewModel.clearFrontendProject()
            }
        }
    }
    val timeline = rememberChatTimelineRuntime(
        state = state,
        onIntent = viewModel::onIntent,
        onSubmittedMessageInserted = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        },
    )
    val draft = state.draft
    val sessionId = timeline.sessionId
    val presentedMessages = timeline.presentedMessages
    val visibleMessages = timeline.visibleMessages
    val roleplay = timeline.roleplay
    val roleplayWebActive = timeline.roleplayWebActive
    val userBrowsedAwayFromBottom = timeline.userBrowsedAwayFromBottom
    val trajectoryRuntimeThreadId = draft.trajectoryRuntimeThreadId()
    var showTrajectory by rememberSaveable(sessionId) { mutableStateOf(false) }
    var showLoadingStatus by remember { mutableStateOf(false) }
    val documentActions = rememberChatHistoryDocumentActions(viewModel)
    var selectedUserMessageText by remember(sessionId) { mutableStateOf<String?>(null) }
    var roleplayProcessMessageId by remember(sessionId) { mutableStateOf<String?>(null) }
    var roleplayOpeningJumpOpen by remember(sessionId) { mutableStateOf(false) }
    var topMenuOpen by remember(sessionId) { mutableStateOf(false) }
    var characterBackgroundSettingsOpen by remember(sessionId) { mutableStateOf(false) }
    var variableViewerOpen by rememberSaveable(sessionId) { mutableStateOf(false) }
    val focusDismissRegistry = remember(sessionId) { FocusDismissRegistry() }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val editingMessageOpen = state.editingMessage != null
    var suppressContentImePadding by remember(sessionId) { mutableStateOf(false) }

    LaunchedEffect(editingMessageOpen, imeBottomPx) {
        suppressContentImePadding = shouldSuppressChatContentImePadding(
            wasSuppressed = suppressContentImePadding,
            editorOpen = editingMessageOpen,
            imeBottomPx = imeBottomPx,
        )
    }
    val contentImePaddingSuppressed = editingMessageOpen || suppressContentImePadding
    val modalSurfaceOpen = editingMessageOpen ||
        selectedUserMessageText != null ||
        state.historyOpen ||
        state.modelPickerOpen ||
        state.errorMessage.isNotBlank() ||
        showTrajectory ||
        roleplayProcessMessageId != null ||
        roleplayOpeningJumpOpen
    val modalBackdropBlur by animateDpAsState(
        targetValue = if (modalSurfaceOpen) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "chatModalBackdropBlur",
    )

    if (characterBackgroundSettingsOpen && draft != null) {
        CharacterChatBackgroundDestination(
            state = state,
            draft = draft,
            newCharacterBackground = newCharacterBackground,
            onNewCharacterBackgroundChange = onNewCharacterBackgroundChange,
            onIntent = viewModel::onIntent,
            onBack = {
                characterBackgroundSettingsOpen = false
            },
        )
        return
    }

    if (variableViewerOpen && draft != null) {
        ChatVariableViewerDestination(
            state = state,
            draft = draft,
            onLoadOlder = { viewModel.onIntent(ChatIntent.LoadOlderMessages) },
            onBack = { variableViewerOpen = false },
        )
        return
    }

    KeepNativeChatWindowUnresized()

    LaunchedEffect(state.isDraftLoading) {
        showLoadingStatus = false
        if (state.isDraftLoading) {
            // A normal one-query Room projection should not flash a loading label between the
            // navigation transition and its first frame. Keep feedback for genuinely slow I/O.
            delay(350)
            showLoadingStatus = true
        }
    }

    val characterPersona = draft?.session?.characterPersona
    val characterBackgroundPath = characterPersona?.chatBackground.orEmpty()
    val defaultCharacterBackgroundPath = characterPersona?.defaultChatBackground.orEmpty()
    val topBarAppearance = remember(state.appearance, roleplay) {
        if (roleplay) state.appearance.asRoleplayReadingTheme() else state.appearance
    }
    val effectiveBackgroundPath = remember(
        characterBackgroundPath,
        state.appearance.textureImagePath,
        defaultCharacterBackgroundPath,
    ) {
        if (
            characterBackgroundPath == AppDefaultChatBackground ||
            characterBackgroundPath == CustomChatBackground
        ) {
            ""
        } else {
            val candidates = if (characterBackgroundPath == GlobalChatBackground) {
                listOf(state.appearance.textureImagePath, defaultCharacterBackgroundPath)
            } else {
                listOf(characterBackgroundPath, defaultCharacterBackgroundPath)
            }
            candidates.firstOrNull { path ->
                path.isNotBlank() && java.io.File(path).exists()
            }.orEmpty()
        }
    }
    val backdropSpec = ChatBackdropSpec(
        appearance = topBarAppearance,
        characterBackgroundPath = characterBackgroundPath,
        defaultCharacterBackgroundPath = defaultCharacterBackgroundPath,
        characterBackgroundOpacity = characterPersona?.chatBackgroundOpacity ?: 0.72f,
        characterBackgroundBlur = characterPersona?.chatBackgroundBlur ?: 0f,
        characterBackgroundScrim = characterPersona?.chatBackgroundScrim ?: 0.22f,
        characterBackgroundResolved = characterPersona != null,
    )
    val renderingPreferences = LocalChatRenderingPreferences.current
    val roleplayPresentation = rememberChatRoleplayPresentation(
        active = roleplayWebActive,
        sessionId = sessionId,
        draft = draft,
        visibleMessages = visibleMessages,
        state = state,
        renderingPreferences = renderingPreferences,
        frontendWorkspace = frontendWorkspace,
    )
    val roleplayPresentedMessages = roleplayPresentation.messages
    val roleplayTranscriptModel = roleplayPresentation.transcript
    val chatComposer: @Composable (Modifier) -> Unit = { modifier ->
        ChatScreenComposer(
            state = state,
            draft = draft,
            webWaitingSlotReserved = timeline.waitingReplySlotReserved,
            waitingIndicatorVisible = timeline.waitingIndicatorVisible,
            replyPresentationActive = timeline.replyPresentationActive,
            appearance = topBarAppearance,
            generationMetrics = timeline.generationMetrics,
            contextWindowUsage = timeline.contextWindowUsage,
            dynamicSettingsAvailable = roleplay &&
                sessionId.isNotBlank() &&
                dynamicSettingsSessionIds.contains(sessionId),
            canDeleteMessages = !timeline.replyPresentationActive &&
                presentedMessages.any { message ->
                    message.role != MessageRole.System && !message.pending
                },
            canRegenerateLatest = timeline.latestRegenerableMessage != null &&
                !timeline.replyPresentationActive,
            onIntent = viewModel::onIntent,
            onSubmit = timeline.submit,
            onPickImages = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onStop = timeline.stop,
            onOpenVariableViewer = { variableViewerOpen = true },
            onOpenTools = onOpenTools,
            onOpenDynamicSettings = {
                draft?.let { current ->
                    onOpenDynamicSettings(current.session.characterId, sessionId)
                }
            },
            onDeleteMessages = { viewModel.onIntent(ChatIntent.OpenDeleteMessages) },
            onRegenerateLatest = {
                timeline.latestRegenerableMessage?.let(timeline.regenerate)
            },
            modifier = modifier,
        )
    }

    CompositionLocalProvider(LocalFocusDismissRegistry provides focusDismissRegistry) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clearFocusOnBlankTap(
                    enabled = shouldEnableChatBlankTapFocusDismiss(
                        editingMessageOpen = state.editingMessage != null,
                    ),
                    onBlankTap = {
                        if (state.moreToolsOpen) {
                            viewModel.onIntent(ChatIntent.DismissMoreTools)
                        }
                    },
                )
                .background(state.appearance.mobileChatBg)
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (modalBackdropBlur > 0.dp) {
                        Modifier.blur(modalBackdropBlur, BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier
                    },
                ),
        ) {
        ChatBackground(spec = backdropSpec)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (contentImePaddingSuppressed) Modifier else Modifier.imePadding(),
                ),
        ) {
            ChatScreenTopBar(
                state = state,
                draft = draft,
                roleplay = roleplay,
                appearance = topBarAppearance,
                effectiveBackgroundPath = effectiveBackgroundPath,
                menuExpanded = topMenuOpen,
                onBack = onBack,
                onOpenMenu = { topMenuOpen = true },
                onDismissMenu = { topMenuOpen = false },
                onOpenPresets = onOpenPresets,
                onOpenTrajectory = { showTrajectory = true },
                onCustomizeBackground = { characterBackgroundSettingsOpen = true },
                onCreateChat = { viewModel.onIntent(ChatIntent.CreateChat) },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                ChatConversationStateContent(
                    state = state,
                    draft = draft,
                    showLoadingStatus = showLoadingStatus,
                    webTranscriptReady = roleplayWebActive && roleplayTranscriptModel != null,
                    webRendererFailure = timeline.roleplayWebRendererFailure,
                    presentationReadiness = timeline.presentationReadiness,
                    onCreateChat = { viewModel.onIntent(ChatIntent.CreateChat) },
                    onRetryWebRenderer = timeline.retryRoleplayRenderer,
                    webContent = { currentDraft, presentationAlpha ->
                        roleplayTranscriptModel?.let { transcript ->
                            ChatRoleplayConversationSurface(
                                context = context,
                                draft = currentDraft,
                                model = transcript,
                                visibleMessages = visibleMessages,
                                rendererRevision = timeline.roleplayWebRendererRevision,
                                updatesPaused = roleplayProcessMessageId != null,
                                controller = timeline.roleplayWebController,
                                presentationReadiness = timeline.presentationReadiness,
                                presentationAlpha = presentationAlpha,
                                onIntent = viewModel::onIntent,
                                onMessageRendered = timeline.onRoleplayMessageRendered,
                                onScrollStateChanged = timeline.onRoleplayScrollStateChanged,
                                onRequestOpeningJump = { roleplayOpeningJumpOpen = true },
                                onSelectDeleteFrom = {
                                    viewModel.onIntent(ChatIntent.SelectDeleteFromMessage(it))
                                },
                                onSelectText = { selectedUserMessageText = it },
                                onRegenerate = timeline.regenerate,
                                onOpenProcess = { roleplayProcessMessageId = it },
                                onOpenUserAvatars = onOpenUserAvatars,
                                onOpenCharacterSettings = onOpenCharacterSettings,
                                onRendererUnavailable = timeline.onRoleplayRendererUnavailable,
                                messageGateway = viewModel,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                )
            }
            ChatComposerBar(
                visible = draft != null && !state.isDraftLoading,
                userBrowsedAwayFromBottom = userBrowsedAwayFromBottom && !state.deleteMessagesOpen,
                roleplayWebCanScrollForward = timeline.roleplayWebCanScrollForward,
                appearance = state.appearance,
                onJumpToBottom = timeline.resumeToEnd,
                composer = chatComposer,
            )
        }

    }

        ChatScreenOverlays(
            state = state,
            draft = draft,
            onIntent = viewModel::onIntent,
            onSaveModelConfig = viewModel::saveModelConfig,
            onSaveCharacterImagePrompt = viewModel::saveCharacterImagePrompt,
            onRefreshModels = viewModel::refreshModels,
            selectedUserMessageText = selectedUserMessageText,
            onDismissSelectedText = { selectedUserMessageText = null },
            showTrajectory = showTrajectory,
            trajectoryRuntimeThreadId = trajectoryRuntimeThreadId,
            trajectoryIsSending = timeline.replyPresentationActive,
            loadTrajectory = { options ->
                viewModel.loadDshTrajectory(trajectoryRuntimeThreadId, options)
            },
            onDismissTrajectory = { showTrajectory = false },
            onImportHistory = documentActions.importHistory,
            onResumeToEnd = timeline.resumeToEnd,
        )
        ChatRoleplayOverlays(
            state = state,
            draft = draft,
            transcript = roleplayTranscriptModel,
            presentedMessages = roleplayPresentedMessages,
            processMessageId = roleplayProcessMessageId,
            openingJumpOpen = roleplayOpeningJumpOpen,
            onDismissProcess = { roleplayProcessMessageId = null },
            onSelectOpeningOption = { openingOptionId ->
                viewModel.onIntent(ChatIntent.SelectOpeningOption(openingOptionId))
            },
            onDismissOpeningJump = { roleplayOpeningJumpOpen = false },
        )
        }
    }
}

internal fun shouldSuppressChatContentImePadding(
    wasSuppressed: Boolean,
    editorOpen: Boolean,
    imeBottomPx: Int,
): Boolean = editorOpen || (wasSuppressed && imeBottomPx > 0)

