package com.eleckoi.android.feature.chat.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Keeps a LazyColumn passage stable while the software keyboard changes viewport height. */
@Composable
fun BindLazyListKeyboardViewport(
    scopeKey: String,
    listState: LazyListState,
    userBrowsedAwayFromBottom: Boolean,
    isDragged: Boolean,
) {
    LaunchedEffect(scopeKey, listState, userBrowsedAwayFromBottom, isDragged) {
        var previousViewportEnd = listState.layoutInfo.viewportEndOffset
        var bottomOwned = keyboardViewportOwnsLiveTail(
            userBrowsedAwayFromBottom = userBrowsedAwayFromBottom,
            isDragged = isDragged,
        )
        snapshotFlow {
            LazyListKeyboardViewportSnapshot(
                viewportEnd = listState.layoutInfo.viewportEndOffset,
                userBrowsedAwayFromBottom = userBrowsedAwayFromBottom,
                isDragged = isDragged,
            )
        }
            .distinctUntilChanged()
            .collect { snapshot ->
                val scrollDelta = keyboardViewportScrollDelta(
                    previousViewportEnd = previousViewportEnd,
                    currentViewportEnd = snapshot.viewportEnd,
                    bottomOwned = bottomOwned,
                    userBrowsedAwayFromBottom = snapshot.userBrowsedAwayFromBottom,
                    isDragged = snapshot.isDragged,
                )
                val viewportShrank = snapshot.viewportEnd < previousViewportEnd
                previousViewportEnd = snapshot.viewportEnd
                if (scrollDelta != 0) listState.scrollBy(scrollDelta.toFloat())
                if (!viewportShrank) {
                    bottomOwned = keyboardViewportOwnsLiveTail(
                        userBrowsedAwayFromBottom = snapshot.userBrowsedAwayFromBottom,
                        isDragged = snapshot.isDragged,
                    )
                }
            }
    }
}

internal fun keyboardViewportOwnsLiveTail(
    userBrowsedAwayFromBottom: Boolean,
    isDragged: Boolean,
): Boolean = !userBrowsedAwayFromBottom && !isDragged

internal fun keyboardViewportScrollDelta(
    previousViewportEnd: Int,
    currentViewportEnd: Int,
    bottomOwned: Boolean,
    userBrowsedAwayFromBottom: Boolean,
    isDragged: Boolean,
): Int = when {
    isDragged -> 0
    userBrowsedAwayFromBottom -> previousViewportEnd - currentViewportEnd
    bottomOwned && currentViewportEnd < previousViewportEnd ->
        previousViewportEnd - currentViewportEnd
    else -> 0
}

private data class LazyListKeyboardViewportSnapshot(
    val viewportEnd: Int,
    val userBrowsedAwayFromBottom: Boolean,
    val isDragged: Boolean,
)
