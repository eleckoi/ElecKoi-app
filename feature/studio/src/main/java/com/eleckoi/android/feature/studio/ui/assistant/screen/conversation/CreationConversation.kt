package com.eleckoi.android.feature.studio.ui.assistant.screen.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleckoi.android.feature.chat.ui.BindLazyListEndFollow
import com.eleckoi.android.feature.chat.ui.BindLazyListKeyboardViewport
import com.eleckoi.android.feature.chat.ui.LocalStaticListExpansionObserver
import com.eleckoi.android.feature.chat.ui.rememberLazyListEndFollowState
import com.eleckoi.android.feature.chat.ui.blocks.markdown.LocalMarkdownHostScrollInProgress
import com.eleckoi.android.feature.chat.ui.blocks.markdown.rememberMarkdownHistoryListController
import com.eleckoi.android.feature.chat.ui.screen.ChatJumpToBottomButton
import com.eleckoi.android.feature.chat.ui.screen.ChatJumpToBottomButtonGap
import com.eleckoi.android.feature.conversation.markdown.CreationMarkdownNode
import com.eleckoi.android.feature.conversation.timeline.CreationDetailPayload
import com.eleckoi.android.feature.conversation.timeline.CreationTurnListProjector
import com.eleckoi.android.feature.conversation.timeline.activePlanUpdateId
import com.eleckoi.android.feature.conversation.timeline.resolveLiveDetailItems
import com.eleckoi.android.feature.conversation.timeline.model.CreationPendingSteerInput
import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.ui.CreationConversationItem
import com.eleckoi.android.feature.conversation.timeline.ui.CreationGenerationAttemptKey
import com.eleckoi.android.feature.conversation.timeline.ui.creationConversationViewportSignature
import com.eleckoi.android.feature.conversation.timeline.ui.generationAttemptKey
import com.eleckoi.android.feature.conversation.timeline.ui.rememberCreationConversationItems
import com.eleckoi.android.feature.conversation.timeline.ui.shouldCreationViewportMutationOwnBottom
import com.eleckoi.android.feature.conversation.timeline.ui.turn.CreationTurn
import com.eleckoi.android.feature.conversation.timeline.ui.turn.CreationTurnContentSpacing
import com.eleckoi.android.feature.conversation.timeline.ui.turn.CreationTurnFooter
import com.eleckoi.android.feature.conversation.timeline.ui.turn.CreationTurnGeneratedMedia
import com.eleckoi.android.feature.conversation.timeline.ui.turn.CreationTurnSpacing
import com.eleckoi.android.feature.conversation.timeline.detail.CreationOperationDetailDialog
import com.eleckoi.android.foundation.design.AppearanceTheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun CreationConversation(
    workspaceId: String = "",
    conversationId: String,
    timeline: List<CreationTimelineItem>,
    historyHasMore: Boolean,
    historyPageLoading: Boolean,
    pendingSteerInputs: List<CreationPendingSteerInput>,
    isRunning: Boolean,
    currentWorkspacePaths: List<String>,
    canUndo: Boolean,
    isRestoring: Boolean,
    onUndo: () -> Unit,
    onLoadOlder: () -> Unit,
    onEditUserMessage: ((CreationTimelineItem) -> Unit)? = null,
    appearance: AppearanceTheme,
    emptyPrompt: String = "想在这里创作什么？",
    bottomContentPadding: Dp = 18.dp,
    keyboardClearance: Dp = 0.dp,
    showProcessedHeaders: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val markdownCacheScopeKey = "creation:$conversationId"
    val endFollowState = rememberLazyListEndFollowState(conversationId)
    val turnProjector = remember { CreationTurnListProjector() }
    val turns = remember(timeline, isRunning) { turnProjector.project(timeline, isRunning) }
    val conversationItems = rememberCreationConversationItems(
        turns = turns,
        preparationTurns = turns,
        cacheScopeKey = markdownCacheScopeKey,
        allowPreparedSplitsToPublish = !endFollowState.userBrowsingHistory,
    )
    val bottomAnchorIndex = conversationItems.size
    val historyListController = rememberMarkdownHistoryListController(
        scopeKey = markdownCacheScopeKey,
        stateRevisionKey = turns.isNotEmpty(),
        initialFirstVisibleItemIndex = bottomAnchorIndex,
    )
    val listState = historyListController.listState
    val editTapTargets = remember(conversationId) { CreationUserMessageTapTargets() }
    val latestTurnId = turns.lastOrNull()?.id
    val latestTurn = turns.lastOrNull()
    val latestGenerationAttemptKey = latestTurn.generationAttemptKey()
    var historyBrowseStarted by remember(conversationId) { mutableStateOf(false) }
    var historyPageLoadArmed by remember(conversationId) { mutableStateOf(true) }
    var latestTurnHeightPx by remember(latestTurnId) { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<CreationDetailPayload?>(null) }
    val endFollowBinding = BindLazyListEndFollow(
        scopeKey = conversationId,
        followState = endFollowState,
        listState = listState,
        nearEndHandoffEnabled = isRunning,
        streamingHeightFollowEnabled = isRunning,
        tailKey = latestTurnId,
        tailHeightPx = latestTurnHeightPx,
        onUserBrowseStarted = { historyBrowseStarted = true },
    )
    val viewportSignature = remember(latestTurn, conversationItems) {
        creationConversationViewportSignature(latestTurn, conversationItems)
    }
    var appliedViewportSignature by remember(conversationId) {
        mutableStateOf(viewportSignature)
    }
    var appliedBottomContentPadding by remember(conversationId) {
        mutableStateOf(bottomContentPadding)
    }
    val viewportStructureChanged = appliedViewportSignature != viewportSignature
    val composerGeometryChanged = appliedBottomContentPadding != bottomContentPadding
    val geometryMutationOwnsBottom = shouldCreationViewportMutationOwnBottom(
        viewportStructureChanged = viewportStructureChanged,
        composerGeometryChanged = composerGeometryChanged,
        latestTurnRunning = latestTurn?.running == true,
        userBrowsingHistory = endFollowState.userBrowsingHistory,
        isDragged = endFollowBinding.isDragged,
    )
    if (geometryMutationOwnsBottom) {
        // Match ordinary chat's slot hand-off: attach the footer before the new structure is drawn.
        // A later scroll is exactly the visible jump and composer overlap this avoids.
        SideEffect {
            listState.requestScrollToItem(bottomAnchorIndex)
        }
    }
    if (viewportStructureChanged || composerGeometryChanged) {
        SideEffect {
            appliedViewportSignature = viewportSignature
            appliedBottomContentPadding = bottomContentPadding
        }
    }
    BindLazyListKeyboardViewport(
        scopeKey = conversationId,
        listState = listState,
        userBrowsedAwayFromBottom = endFollowState.userBrowsingHistory,
        isDragged = endFollowBinding.isDragged,
    )
    val staticExpansionObserver = remember(endFollowState) {
        { key: Any, expanded: Boolean ->
            endFollowState.setStaticContentExpanded(key, expanded)
        }
    }
    LaunchedEffect(conversationId, timeline.size, historyPageLoading) {
        if (!historyPageLoading) historyPageLoadArmed = true
    }
    LaunchedEffect(
        conversationId,
        listState,
        historyBrowseStarted,
        historyHasMore,
        historyPageLoading,
    ) {
        if (!historyBrowseStarted) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisibleItemIndex ->
                if (firstVisibleItemIndex > CreationHistoryPagePreloadItemThreshold) {
                    historyPageLoadArmed = true
                } else if (historyPageLoadArmed) {
                    // 提前接入上一页，并让屏幕上的真实回合 key 继续充当滚动锚点。
                    if (historyHasMore && !historyPageLoading) {
                        historyPageLoadArmed = false
                        onLoadOlder()
                    }
                }
            }
    }
    LaunchedEffect(
        turns,
        detail?.sourceTurnId,
        detail?.liveTurnId,
        detail?.liveLatestItemOnly,
        detail?.liveCurrentOperationGroup,
        detail?.liveOperationGroupAnchorId,
        detail?.liveSource,
    ) {
        val current = detail ?: return@LaunchedEffect
        val sourceTurnId = current.sourceTurnId ?: current.liveTurnId ?: return@LaunchedEffect
        val sourceTurn = turns.firstOrNull { it.id == sourceTurnId } ?: return@LaunchedEffect
        val activePlanId = activePlanUpdateId(
            items = sourceTurn.processing + sourceTurn.chronologicalTail,
            turnRunning = sourceTurn.running,
        )
        if (current.liveTurnId != sourceTurnId) {
            detail = current.copy(activePlanUpdateId = activePlanId)
            return@LaunchedEffect
        }
        val liveItems = sourceTurn.resolveLiveDetailItems(
            source = current.liveSource,
            latestItemOnly = current.liveLatestItemOnly,
            currentOperationGroup = current.liveCurrentOperationGroup,
            operationGroupAnchorId = current.liveOperationGroupAnchorId,
        )
        detail = current.copy(
            items = liveItems,
            diff = if (current.liveLatestItemOnly || current.liveCurrentOperationGroup) {
                liveItems.asReversed().firstOrNull { it.diff.isNotBlank() }?.diff.orEmpty()
            } else {
                sourceTurn.diff
            },
            activePlanUpdateId = activePlanId,
        )
    }
    var latestTurnTrackingInitialized by remember(conversationId) { mutableStateOf(false) }
    var previouslyObservedGenerationAttemptKey by remember(conversationId) {
        mutableStateOf<CreationGenerationAttemptKey?>(null)
    }
    // 冷启动的历史首次组合已经由 LazyListState 的底部锚点定位，不能再补一次滚动；
    // 只有界面存活期间真正新增的回合才请求到底。
    LaunchedEffect(markdownCacheScopeKey, latestGenerationAttemptKey) {
        if (!latestTurnTrackingInitialized) {
            latestTurnTrackingInitialized = true
            previouslyObservedGenerationAttemptKey = latestGenerationAttemptKey
        } else if (
            latestGenerationAttemptKey != null &&
            latestGenerationAttemptKey != previouslyObservedGenerationAttemptKey
        ) {
            // A new user turn changes turnId; regeneration keeps turnId but changes the attempt
            // start. Both are explicit author actions and must take the viewport back to the live
            // tail even when the author had been reading higher in the previous answer.
            endFollowState.resumeToEnd()
            previouslyObservedGenerationAttemptKey = latestGenerationAttemptKey
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .editVisibleCreationUserMessageOnTap(
                enabled = !isRunning && onEditUserMessage != null,
                targets = editTapTargets,
                onEdit = { item -> onEditUserMessage?.invoke(item) },
            ),
    ) {
        CompositionLocalProvider(
            LocalStaticListExpansionObserver provides staticExpansionObserver,
            LocalMarkdownHostScrollInProgress provides listState.isScrollInProgress,
        ) {
        LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = keyboardClearance)
                    .nestedScroll(endFollowBinding.nestedScrollConnection),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                    bottom = bottomContentPadding,
                ),
                // 使用正常布局方向，所有可展开内容统一向下生长。
                verticalArrangement = Arrangement.Top,
            ) {
        itemsIndexed(
            items = conversationItems,
            key = { _, item -> item.key },
            contentType = { _, item -> item.contentType },
        ) { _, item ->
            val turn = item.turn
            val isLatestTurn = turn.id == latestTurnId
            val finalWorkspacePaths = currentWorkspacePaths.takeIf {
                isLatestTurn && !turn.running
            }
            when (item) {
                is CreationConversationItem.WholeTurn -> CreationTurn(
                    turn = turn,
                    workspaceId = workspaceId,
                    pendingSteerInputs = pendingSteerInputs.takeIf {
                        isLatestTurn && turn.running
                    }.orEmpty(),
                    appearance = appearance,
                    onEditUserMessage = if (isRunning) null else onEditUserMessage,
                    onEditUserMessageBoundsChanged = { message, bounds ->
                        editTapTargets.update(message, bounds)
                    },
                    finalWorkspacePaths = finalWorkspacePaths,
                    canUndo = canUndo && isLatestTurn,
                    isRestoring = isRestoring && isLatestTurn,
                    onUndo = onUndo,
                    onOpenDetail = { detail = it },
                    showProcessedHeader = showProcessedHeaders,
                    showGeneratedMedia = false,
                    modifier = Modifier
                        .padding(bottom = CreationTurnSpacing)
                        .then(
                            if (isLatestTurn) {
                                Modifier.onSizeChanged { latestTurnHeightPx = it.height }
                            } else {
                                Modifier
                            },
                        ),
                )
                is CreationConversationItem.TurnBody -> CreationTurn(
                    turn = turn,
                    workspaceId = workspaceId,
                    pendingSteerInputs = emptyList(),
                    appearance = appearance,
                    onEditUserMessage = if (isRunning) null else onEditUserMessage,
                    onEditUserMessageBoundsChanged = { message, bounds ->
                        editTapTargets.update(message, bounds)
                    },
                    finalWorkspacePaths = null,
                    canUndo = false,
                    isRestoring = false,
                    onUndo = onUndo,
                    onOpenDetail = { detail = it },
                    showFinalAnswer = false,
                    showGeneratedMedia = false,
                    showFileSummary = false,
                    showProcessedHeader = showProcessedHeaders,
                    modifier = Modifier.padding(bottom = CreationTurnContentSpacing),
                )
                is CreationConversationItem.FinalAnswerNode -> CreationMarkdownNode(
                    item = item.answer,
                    node = item.node,
                    appearance = appearance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = if (item.isLastNode) 0.dp else CreationMarkdownBlockSpacing,
                        ),
                )
                is CreationConversationItem.GeneratedMedia -> CreationTurnGeneratedMedia(
                    turn = turn,
                    workspaceId = workspaceId,
                    appearance = appearance,
                )
                is CreationConversationItem.TurnFooter -> CreationTurnFooter(
                    turn = turn,
                    appearance = appearance,
                    finalWorkspacePaths = finalWorkspacePaths,
                    canUndo = canUndo && isLatestTurn,
                    isRestoring = isRestoring && isLatestTurn,
                    onUndo = onUndo,
                    onOpenDetail = { detail = it },
                )
            }
        }
        item(
            key = "creation-bottom-anchor",
            contentType = "bottom-anchor",
        ) {
            Spacer(modifier = Modifier.height(1.dp))
        }
            }
        }

        if (turns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 18.dp,
                        top = 18.dp,
                        end = 18.dp,
                        bottom = bottomContentPadding + keyboardClearance,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = appearance.mobileSoft,
                    )
                    Text(
                        emptyPrompt,
                        color = appearance.mobileText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = turns.isNotEmpty() &&
                endFollowState.userBrowsingHistory &&
                listState.canScrollForward,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = bottomContentPadding + keyboardClearance + ChatJumpToBottomButtonGap,
                ),
            enter = fadeIn(tween(durationMillis = 180)),
            exit = fadeOut(tween(durationMillis = 140)),
        ) {
            ChatJumpToBottomButton(
                appearance = appearance,
                onClick = endFollowState::resumeToEnd,
            )
        }
    }
    detail?.let { payload ->
        CreationOperationDetailDialog(
            payload = payload,
            appearance = appearance,
            onDismiss = { detail = null },
        )
    }
}

// 与普通聊天一致，在用户真正抵达顶部前完成上一页接入和锚点保持。
private const val CreationHistoryPagePreloadItemThreshold = 16
private val CreationMarkdownBlockSpacing = 8.dp
