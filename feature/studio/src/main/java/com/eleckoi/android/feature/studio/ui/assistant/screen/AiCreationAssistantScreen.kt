package com.eleckoi.android.feature.studio.ui.assistant.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eleckoi.android.engine.generation.model.supportsImageInput
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.engine.immersive.security.AuthorFrontendStoragePrincipal
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.design.components.PinnedStatusScaffold
import com.eleckoi.android.feature.chat.data.MaxChatInputImages
import com.eleckoi.android.feature.chat.ui.immersive.ImmersiveChatScreen
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineKind
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantIntent
import com.eleckoi.android.feature.studio.ui.assistant.AiCreationAssistantViewModel
import com.eleckoi.android.feature.studio.ui.assistant.approval.CreationApprovalCard
import com.eleckoi.android.feature.studio.ui.assistant.components.CreationHeader
import com.eleckoi.android.feature.studio.ui.assistant.components.EmptyCreationWorkspace
import com.eleckoi.android.feature.studio.ui.assistant.components.LoadingWorkspace
import com.eleckoi.android.feature.studio.ui.assistant.composer.CreationComposer
import com.eleckoi.android.feature.studio.ui.assistant.screen.content.CreationAssistantRuntimeBootstrap
import com.eleckoi.android.feature.studio.ui.assistant.screen.content.CreationAssistantWorkspaceDrawer
import com.eleckoi.android.feature.studio.ui.assistant.screen.conversation.CreationConversation
import com.eleckoi.android.feature.studio.ui.assistant.screen.overlay.CreationAssistantOverlays
import com.eleckoi.android.feature.studio.ui.assistant.tools.CreationAssistantToolsDialog
import com.eleckoi.android.feature.studio.ui.assistant.workspace.drawer.CreationWorkspaceDrawerLayout
import com.eleckoi.android.feature.studio.ui.assistant.workspace.FileEditor
import com.eleckoi.android.feature.studio.ui.assistant.workspace.WorkspaceFiles
import com.eleckoi.android.sdk.author.AuthorApiPermission
import com.eleckoi.android.sdk.author.AuthorChatGateway
import kotlinx.coroutines.launch

@Composable
internal fun AiCreationAssistantScreen(
    appearance: AppearanceTheme,
    viewModel: AiCreationAssistantViewModel,
    chatGateway: AuthorChatGateway,
    onOpenWebSearchSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MaxChatInputImages),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onIntent(
                AiCreationAssistantIntent.AddInputImages(uris.map { it.toString() }),
            )
        }
    }
    var showFiles by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    var showCharacterRoots by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var composerOverlayHeightPx by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(AiCreationAssistantIntent.Load)
    }
    LaunchedEffect(state.notice, state.errorMessage) {
        val message = state.errorMessage.ifBlank { state.notice }
        if (message.isNotBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.onIntent(AiCreationAssistantIntent.DismissMessage)
        }
    }
    val workspace = state.workspace
    val directory = state.projectDirectory
    val previewEntryFile = state.previewEntryFile
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val conversationComposerVisible = !showPreview &&
        !state.shouldShowRuntimeBootstrap &&
        !state.isLoading &&
        state.editingUserMessage == null &&
        state.selectedFilePath == null &&
        !showFiles &&
        workspace != null &&
        state.conversation != null
    val editingUserMessageOpen = state.editingUserMessage != null
    val keepWindowUnresized = shouldKeepCreationWindowUnresized(
        conversationComposerVisible = conversationComposerVisible,
        editingUserMessageOpen = editingUserMessageOpen,
    )
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    var suppressConversationImeLift by remember(state.conversation?.id) {
        mutableStateOf(false)
    }
    LaunchedEffect(editingUserMessageOpen, imeBottomPx) {
        suppressConversationImeLift = shouldSuppressCreationConversationImeLift(
            wasSuppressed = suppressConversationImeLift,
            editorOpen = editingUserMessageOpen,
            imeBottomPx = imeBottomPx,
        )
    }
    val conversationImeLiftPx = if (
        conversationComposerVisible &&
        !suppressConversationImeLift
    ) {
        (imeBottomPx - navigationBottomPx).coerceAtLeast(0).toFloat()
    } else {
        0f
    }
    val conversationKeyboardClearance = with(density) { conversationImeLiftPx.toDp() }
    val imeBackdropHeight = with(density) { imeBottomPx.toDp() }
    val conversationViewportBottomInset = creationConversationViewportBottomInset(
        measuredComposerHeight = with(density) { composerOverlayHeightPx.toDp() },
        imeLift = conversationKeyboardClearance,
    )
    DisposableEffect(context, keepWindowUnresized) {
        if (!keepWindowUnresized) {
            onDispose { }
        } else {
            val activity = context.findHostActivity()
            val previousSoftInputMode = activity?.window?.attributes?.softInputMode
            activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose {
                activity?.window?.setSoftInputMode(
                    previousSoftInputMode ?: WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                )
            }
        }
    }
    val showSnackbar: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val selectedModelSupportsImages = state.modelConfigs
        .firstOrNull { it.id == state.selectedModelConfigId }
        ?.supportsImageInput(state.selectedModelId) == true
    if (showPreview && workspace != null && directory != null && previewEntryFile != null) {
        ImmersiveChatScreen(
            project = FrontendProject(
                id = "creator-preview-${workspace.id}-${state.reloadRevision}",
                characterId = workspace.linkedCharacterId.orEmpty(),
                name = workspace.name,
                entryFile = previewEntryFile,
                files = state.files.map { it.path },
                importedAt = workspace.updatedAt,
            ),
            projectDirectory = directory,
            characterName = workspace.name,
            chatGateway = chatGateway,
            appearance = appearance,
            storagePrincipal = AuthorFrontendStoragePrincipal.creationWorkspace(workspace.id),
            authorApiPermissions = AuthorApiPermission.previewReadOnly,
            onExit = { showPreview = false },
            onFallbackToNative = { showPreview = false },
        )
        return
    }

    BackHandler(enabled = state.selectedFilePath != null || showFiles) {
        if (state.selectedFilePath != null) {
            viewModel.onIntent(AiCreationAssistantIntent.CloseFile)
        } else {
            showFiles = false
        }
    }

    val toolBackdropBlur by animateDpAsState(
        targetValue = if (showTools) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "creationToolBackdropBlur",
    )
    Box(modifier = Modifier.fillMaxSize().blur(toolBackdropBlur)) {
        CreationWorkspaceDrawerLayout(
            drawerOpen = drawerOpen,
            appearance = appearance,
            onOpenDrawer = { drawerOpen = true },
            onCloseDrawer = { drawerOpen = false },
            drawerContent = {
                CreationAssistantWorkspaceDrawer(
                    state = state,
                    appearance = appearance,
                    onIntent = viewModel::onIntent,
                    onUnavailable = showSnackbar,
                )
            },
        ) {
            PinnedStatusScaffold(
                appearance = appearance,
                imeAware = shouldApplyCreationRootImePadding(
                    conversationComposerVisible = conversationComposerVisible,
                    editingUserMessageOpen = editingUserMessageOpen,
                ),
                backgroundColor = appearance.mobileSurface,
            ) {
                if (state.shouldShowRuntimeBootstrap) {
                    CreationAssistantRuntimeBootstrap(
                        state = state,
                        appearance = appearance,
                        onBack = onBack,
                        onIntent = viewModel::onIntent,
                    )
                } else {
                    val subpageOpen = state.selectedFilePath != null || showFiles
                    CreationHeader(
                        appearance = appearance,
                        showBack = subpageOpen,
                        editorOpen = state.selectedFilePath != null,
                        canOpenFiles = workspace != null,
                        canPreview = workspace != null && directory != null && previewEntryFile != null,
                        canPublish = workspace?.linkedCharacterId?.isNotBlank() == true,
                        isPublishing = state.isPublishing,
                        onExit = onBack,
                        onNavigation = {
                            when {
                                state.selectedFilePath != null -> viewModel.onIntent(AiCreationAssistantIntent.CloseFile)
                                showFiles -> showFiles = false
                                else -> drawerOpen = true
                            }
                        },
                        onFiles = { showFiles = !showFiles },
                        onPreview = { showPreview = true },
                        onPublish = { viewModel.onIntent(AiCreationAssistantIntent.Publish) },
                        onNewConversation = {
                            workspace?.id?.let { id ->
                                viewModel.onIntent(AiCreationAssistantIntent.CreateConversation(id))
                            }
                        },
                        onSave = { viewModel.onIntent(AiCreationAssistantIntent.SaveFile) },
                        fileDirty = state.fileDraftDirty,
                    )

                    when {
                        state.isLoading -> LoadingWorkspace(appearance, Modifier.weight(1f))
                        state.selectedFilePath != null -> FileEditor(
                            content = state.fileDraft,
                            appearance = appearance,
                            onChange = { viewModel.onIntent(AiCreationAssistantIntent.ChangeFileDraft(it)) },
                            modifier = Modifier.weight(1f),
                        )
                        showFiles -> WorkspaceFiles(
                            files = state.files,
                            appearance = appearance,
                            onOpen = { viewModel.onIntent(AiCreationAssistantIntent.OpenFile(it)) },
                            modifier = Modifier.weight(1f),
                        )
                        workspace == null || state.conversation == null -> EmptyCreationWorkspace(
                            appearance = appearance,
                            onOpenDrawer = { drawerOpen = true },
                            modifier = Modifier.weight(1f),
                        )
                        else -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clipToBounds(),
                            ) {
                                CreationConversation(
                                    workspaceId = state.workspace?.id.orEmpty(),
                                    conversationId = state.conversation?.id.orEmpty(),
                                    timeline = state.timeline,
                                    historyHasMore = state.historyHasMore,
                                    historyPageLoading = state.historyPageLoading,
                                    pendingSteerInputs = state.pendingSteerInputs,
                                    isRunning = state.isRunning,
                                    currentWorkspacePaths = state.files.map { it.path },
                                    canUndo = state.undoCheckpoint != null &&
                                        !state.isRunning &&
                                        !state.isPublishing &&
                                        !state.isRestoringCheckpoint,
                                    isRestoring = state.isRestoringCheckpoint,
                                    onUndo = { viewModel.onIntent(AiCreationAssistantIntent.UndoLastAiChanges) },
                                    onLoadOlder = { viewModel.onIntent(AiCreationAssistantIntent.LoadOlderTimeline) },
                                    onEditUserMessage = {
                                        viewModel.onIntent(
                                            AiCreationAssistantIntent.OpenUserMessageEditor(it),
                                        )
                                    },
                                    appearance = appearance,
                                    // The message viewport ends at the composer's top edge. Bottom
                                    // content padding is only reading whitespace; it is not allowed
                                    // to masquerade as collision avoidance for an overlay.
                                    bottomContentPadding = 10.dp,
                                    keyboardClearance = 0.dp,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = conversationViewportBottomInset),
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .onSizeChanged { composerOverlayHeightPx = it.height }
                                        .graphicsLayer { translationY = -conversationImeLiftPx },
                                ) {
                                    state.pendingApproval?.let { approval ->
                                        CreationApprovalCard(
                                            approval = approval,
                                            pendingCount = state.pendingApprovals.size,
                                            appearance = appearance,
                                            onDecision = { decision ->
                                                viewModel.onIntent(
                                                    AiCreationAssistantIntent.ResolveApproval(
                                                        approval.requestId,
                                                        decision,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                    CreationComposer(
                                        value = state.input,
                                        inputImages = state.inputImages,
                                        isPreparingImages = state.isPreparingInputImages,
                                        inputEnabled = !state.isRestoringCheckpoint &&
                                            !state.isPreparingInputImages,
                                        sendEnabled = state.isRuntimeInstalled &&
                                            !state.isRestoringCheckpoint &&
                                            !state.isPreparingInputImages,
                                        modelLabel = state.modelLabel,
                                        contextWindowUsage = state.contextWindowUsage,
                                        permissionMode = state.permissionMode,
                                        isRunning = state.isRunning,
                                        canRegenerate = state.timeline.any {
                                            it.kind == CreationTimelineKind.User
                                        },
                                        appearance = appearance,
                                        onChange = { viewModel.onIntent(AiCreationAssistantIntent.ChangeInput(it)) },
                                        onAddImage = {
                                            if (selectedModelSupportsImages) {
                                                imagePicker.launch(
                                                    PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                    ),
                                                )
                                            } else {
                                                showSnackbar("请先在当前模型设置中开启图片输入")
                                            }
                                        },
                                        onRemoveImage = { imageId ->
                                            viewModel.onIntent(
                                                AiCreationAssistantIntent.RemoveInputImage(imageId),
                                            )
                                        },
                                        onPermissionModeChange = {
                                            viewModel.onIntent(AiCreationAssistantIntent.ChangePermissionMode(it))
                                        },
                                        onModelSelector = { showModelPicker = true },
                                        onRoleSelector = {
                                            viewModel.onIntent(AiCreationAssistantIntent.LoadCharacterDirectory)
                                            showCharacterRoots = true
                                        },
                                        onOpenTools = { showTools = true },
                                        onOpenCommand = {
                                            showSnackbar("命令入口将在后续版本继续设计")
                                        },
                                        onRegenerate = {
                                            viewModel.onIntent(AiCreationAssistantIntent.RegenerateLatest)
                                        },
                                        onVoiceInput = {
                                            showSnackbar("语音输入尚未接入")
                                        },
                                        onSend = {
                                            val submittedContent = state.input.trim().isNotEmpty() ||
                                                state.inputImages.isNotEmpty()
                                            viewModel.onIntent(AiCreationAssistantIntent.Send)
                                            if (
                                                submittedContent &&
                                                viewModel.uiState.value.input.isBlank()
                                            ) {
                                                scope.launch {
                                                    withFrameNanos { }
                                                    focusManager.clearFocus(force = true)
                                                    keyboardController?.hide()
                                                }
                                            }
                                        },
                                        onStop = { viewModel.onIntent(AiCreationAssistantIntent.Stop) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (conversationComposerVisible && imeBottomPx > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(imeBackdropHeight)
                    .background(appearance.mobileComposerBg),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .graphicsLayer { translationY = -conversationImeLiftPx }
                .padding(horizontal = 20.dp, vertical = 92.dp),
        )

        CreationAssistantOverlays(
            state = state,
            viewModel = viewModel,
            appearance = appearance,
            showModelPicker = showModelPicker,
            onDismissModelPicker = { showModelPicker = false },
            showCharacterRoots = showCharacterRoots,
            onDismissCharacterRoots = { showCharacterRoots = false },
        )
    }

    if (showTools) {
        CreationAssistantToolsDialog(
            groups = state.toolGroups,
            enabledGroupIds = state.enabledToolGroupIds,
            imageModelConfigId = state.creatorImageModelConfigId,
            modelConfigs = state.modelConfigs,
            enabled = !state.isRunning,
            appearance = appearance,
            onEnabledChange = { groupId, enabled ->
                viewModel.onIntent(
                    AiCreationAssistantIntent.ChangeToolGroupEnabled(groupId, enabled),
                )
            },
            onImageModelConfigChange = { configId ->
                viewModel.onIntent(
                    AiCreationAssistantIntent.ChangeCreatorImageModelConfig(configId),
                )
            },
            onOpenWebSearchSettings = {
                showTools = false
                onOpenWebSearchSettings()
            },
            onSaveModelConfig = viewModel::saveModelConfig,
            onRefreshModels = viewModel::refreshModels,
            onDismiss = { showTools = false },
        )
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
