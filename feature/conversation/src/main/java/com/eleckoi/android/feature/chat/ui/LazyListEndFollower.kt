package com.eleckoi.android.feature.chat.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Shared user-ownership state for every normally ordered conversation list.
 *
 * Rendering models may differ (plain messages, tool turns, approvals), but pausing follow while the
 * user reads history and handing control back near the real footer must never diverge by screen.
 */
@Stable
class LazyListEndFollowState constructor() {
    private val userBrowsingState = mutableStateOf(false)
    private val expandedStaticContentKeys = mutableStateMapOf<Any, Unit>()
    private val staticExpansionOwnsViewportState = mutableStateOf(false)

    var userBrowsingHistory: Boolean
        get() = userBrowsingState.value
        private set(value) {
            userBrowsingState.value = value
        }

    var resumeRequest by mutableIntStateOf(0)
        private set

    fun pauseForUser() {
        userBrowsingHistory = true
    }

    fun resumeToEnd() {
        userBrowsingHistory = false
        staticExpansionOwnsViewportState.value = false
        resumeRequest += 1
    }

    val staticExpansionOwnsViewport: Boolean
        get() = staticExpansionOwnsViewportState.value

    fun setStaticContentExpanded(key: Any, expanded: Boolean) {
        if (expanded) {
            expandedStaticContentKeys[key] = Unit
            staticExpansionOwnsViewportState.value = true
        } else {
            expandedStaticContentKeys.remove(key)
            if (expandedStaticContentKeys.isEmpty()) {
                staticExpansionOwnsViewportState.value = false
            }
        }
    }
}

/** Keeps a static expansion anchored at the row the user deliberately opened. */
val LocalStaticListExpansionObserver =
    staticCompositionLocalOf<(Any, Boolean) -> Unit> { { _, _ -> } }

@Composable
fun rememberLazyListEndFollowState(scopeKey: Any): LazyListEndFollowState =
    remember(scopeKey) { LazyListEndFollowState() }

data class LazyListEndFollowBinding(
    val nestedScrollConnection: NestedScrollConnection,
    val isDragged: Boolean,
)

/**
 * Binds the shared end-follow policy to one list. Callers keep only their content model and expose
 * the measured height of the current tail; all gesture ownership and footer handoff lives here.
 * [nearEndHandoffEnabled] permits a small handoff zone only while an active turn can move the
 * footer. At rest, ownership is returned only at the measured zero-pixel physical end.
 */
@Composable
fun BindLazyListEndFollow(
    scopeKey: Any,
    followState: LazyListEndFollowState,
    listState: LazyListState,
    nearEndHandoffEnabled: Boolean,
    streamingHeightFollowEnabled: Boolean,
    tailKey: Any?,
    tailHeightPx: Int,
    onUserBrowseStarted: () -> Unit = {},
): LazyListEndFollowBinding {
    val isDraggedState = listState.interactionSource.collectIsDraggedAsState()
    val isDragged by isDraggedState
    val currentOnUserBrowseStarted = rememberUpdatedState(onUserBrowseStarted)
    val currentTailHeightPx = rememberUpdatedState(tailHeightPx)
    val currentNearEndHandoffEnabled = rememberUpdatedState(nearEndHandoffEnabled)
    val density = LocalDensity.current
    val nearEndHandoffPx = with(density) { SharedNearEndHandoff.toPx() }
    val bottomAnchorSizePx = with(density) { SharedBottomAnchorSize.toPx() }
    val scrollConnection = remember(
        scopeKey,
        listState,
        followState,
        nearEndHandoffPx,
        bottomAnchorSizePx,
    ) {
        object : NestedScrollConnection {
            private var gestureActive = false
            private var userScrollObserved = false
            private var furthestDistanceToEndPx = Float.POSITIVE_INFINITY
            private var reachedNearEndHandoff = false

            private fun beginGestureIfNeeded() {
                if (gestureActive) return
                gestureActive = true
                furthestDistanceToEndPx = listState.distanceToCurrentEndPx(bottomAnchorSizePx)
                reachedNearEndHandoff = false
            }

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) beginGestureIfNeeded()
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    beginGestureIfNeeded()
                    if (consumed.y != 0f) {
                        userScrollObserved = true
                        followState.pauseForUser()
                        currentOnUserBrowseStarted.value()
                    }
                }
                if (
                    gestureActive &&
                    userScrollObserved
                ) {
                    // During an active turn the footer can start moving as soon as waiting hands off
                    // to a process row or final answer. Latch a deliberately small near-end visit
                    // so that hand-off can resume following without requiring the user to catch a
                    // continuously moving exact pixel.
                    val currentDistance = listState.distanceToCurrentEndPx(bottomAnchorSizePx)
                    val reachedNow = currentNearEndHandoffEnabled.value &&
                        isMeasuredEndHandoffVisit(
                            currentDistanceToEndPx = currentDistance,
                            referenceDistanceToEndPx = furthestDistanceToEndPx,
                            handoffPx = nearEndHandoffPx,
                        )
                    reachedNearEndHandoff = reachedNearEndHandoff || reachedNow
                    furthestDistanceToEndPx = fartherDistanceToEndPx(
                        previousDistanceToEndPx = furthestDistanceToEndPx,
                        currentDistanceToEndPx = currentDistance,
                    )
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (userScrollObserved) {
                    val currentDistance = listState.distanceToCurrentEndPx(bottomAnchorSizePx)
                    val endedWithinNearEndHandoff = isMeasuredEndHandoffVisit(
                        currentDistanceToEndPx = currentDistance,
                        referenceDistanceToEndPx = furthestDistanceToEndPx,
                        handoffPx = nearEndHandoffPx,
                    )
                    val endedAtExactEnd = isMeasuredEndHandoffVisit(
                        currentDistanceToEndPx = currentDistance,
                        referenceDistanceToEndPx = furthestDistanceToEndPx,
                        handoffPx = 0f,
                    )
                    val reachedNearEnd = reachedNearEndHandoff || endedWithinNearEndHandoff
                    val shouldResume = shouldResumeEndFollow(
                        nearEndHandoffEnabled = currentNearEndHandoffEnabled.value,
                        reachedNearEndHandoff = reachedNearEnd,
                        reachedExactEnd = endedAtExactEnd,
                    )
                    if (shouldResume) {
                        followState.resumeToEnd()
                    }
                }
                gestureActive = false
                userScrollObserved = false
                furthestDistanceToEndPx = Float.POSITIVE_INFINITY
                reachedNearEndHandoff = false
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(
        scopeKey,
        listState,
        tailKey,
        streamingHeightFollowEnabled,
    ) {
        var followedHeightPx = currentTailHeightPx.value
        snapshotFlow { currentTailHeightPx.value }.collect { measuredHeightPx ->
            val heightDeltaPx = streamingTailScrollDelta(
                previousHeightPx = followedHeightPx,
                measuredHeightPx = measuredHeightPx,
                streamingHeightFollowEnabled = streamingHeightFollowEnabled,
                userBrowsingHistory = followState.userBrowsingHistory,
                isDragged = isDraggedState.value,
                staticExpansionOwnsViewport = followState.staticExpansionOwnsViewport,
            )
            val distanceBefore = listState.distanceToCurrentEndPx(bottomAnchorSizePx)
            val requestedDeltaPx = preserveExactEndDuringTailShrink(
                heightDeltaPx = heightDeltaPx,
                distanceToEndPx = distanceBefore,
            )
            followedHeightPx = measuredHeightPx
            if (requestedDeltaPx != 0) {
                // Growth remains delta-followed. When a renderer hand-off shrinks a row while the
                // footer is already exact, LazyColumn has already clamped to its new max scroll;
                // applying the same negative delta again manufactures a gap above the composer.
                listState.scrollBy(requestedDeltaPx.toFloat())
            }
        }
    }
    LaunchedEffect(scopeKey, listState, followState.resumeRequest) {
        if (followState.resumeRequest <= 0) return@LaunchedEffect
        withFrameNanos { }
        if (!followState.userBrowsingHistory) {
            listState.scrollToCurrentEnd()
        }
    }

    return LazyListEndFollowBinding(
        nestedScrollConnection = scrollConnection,
        isDragged = isDragged,
    )
}

/** Instantly targets the current footer; measured tail deltas handle later height revisions. */
suspend fun LazyListState.scrollToCurrentEnd() {
    val lastItemIndex = layoutInfo.totalItemsCount - 1
    if (lastItemIndex >= 0) scrollToItem(lastItemIndex)
}

/**
 * Returns the remaining pixel distance to a one-pixel footer when the footer or its preceding
 * content item is already visible. Farther positions deliberately return infinity: callers may
 * offer an explicit jump button there, but must not silently steal a user's history position.
 */
internal fun LazyListState.distanceToCurrentEndPx(bottomAnchorSizePx: Float): Float {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return Float.POSITIVE_INFINITY
    return calculateDistanceToCurrentEndPx(
        totalItemsCount = info.totalItemsCount,
        lastVisibleItemIndex = lastVisible.index,
        lastVisibleItemOffset = lastVisible.offset,
        lastVisibleItemSize = lastVisible.size,
        viewportEndOffset = info.viewportEndOffset,
        afterContentPadding = info.afterContentPadding,
        itemSpacing = info.mainAxisItemSpacing,
        bottomAnchorSizePx = bottomAnchorSizePx,
    )
}

/**
 * Hands scrolling back only after a gesture moved toward a measured footer.
 *
 * [LazyListState.canScrollForward] can briefly become false while Markdown replaces placeholders
 * or changes item geometry. Treating that transient value as proof of reaching the footer clears
 * user ownership and lets live tail updates pull the reader back to the latest message.
 */
internal fun isMeasuredEndHandoffVisit(
    currentDistanceToEndPx: Float,
    referenceDistanceToEndPx: Float,
    handoffPx: Float,
): Boolean = currentDistanceToEndPx <= handoffPx &&
    currentDistanceToEndPx < referenceDistanceToEndPx

internal fun fartherDistanceToEndPx(
    previousDistanceToEndPx: Float,
    currentDistanceToEndPx: Float,
): Float = maxOf(previousDistanceToEndPx, currentDistanceToEndPx)

internal fun calculateDistanceToCurrentEndPx(
    totalItemsCount: Int,
    lastVisibleItemIndex: Int,
    lastVisibleItemOffset: Int,
    lastVisibleItemSize: Int,
    viewportEndOffset: Int,
    afterContentPadding: Int,
    itemSpacing: Int,
    bottomAnchorSizePx: Float,
): Float {
    val bottomAnchorIndex = totalItemsCount - 1
    if (bottomAnchorIndex < 0) return 0f
    val unmeasuredTailPx = when (lastVisibleItemIndex) {
        bottomAnchorIndex -> 0f
        bottomAnchorIndex - 1 -> itemSpacing + bottomAnchorSizePx
        else -> return Float.POSITIVE_INFINITY
    }
    return (
        lastVisibleItemOffset +
            lastVisibleItemSize +
            unmeasuredTailPx +
            afterContentPadding -
            viewportEndOffset
        ).coerceAtLeast(0f)
}

internal fun shouldResumeEndFollow(
    nearEndHandoffEnabled: Boolean,
    reachedNearEndHandoff: Boolean,
    reachedExactEnd: Boolean,
): Boolean = reachedExactEnd || (nearEndHandoffEnabled && reachedNearEndHandoff)

internal fun preserveExactEndDuringTailShrink(
    heightDeltaPx: Int,
    distanceToEndPx: Float,
): Int = if (heightDeltaPx < 0 && distanceToEndPx <= 1f) 0 else heightDeltaPx

internal fun streamingTailScrollDelta(
    previousHeightPx: Int,
    measuredHeightPx: Int,
    streamingHeightFollowEnabled: Boolean,
    userBrowsingHistory: Boolean,
    isDragged: Boolean,
    /**
     * A row is giving back height on purpose — a processed stage folding away, an expansion
     * closing. Gliding with that shrink drags the whole conversation past the composer; the row
     * that changed decides where the viewport lands.
     */
    staticExpansionOwnsViewport: Boolean = false,
): Int = if (
    streamingHeightFollowEnabled &&
    !userBrowsingHistory &&
    !isDragged &&
    !staticExpansionOwnsViewport
) {
    measuredHeightPx - previousHeightPx
} else {
    0
}

private val SharedBottomAnchorSize = 1.dp
private val SharedNearEndHandoff = 16.dp
