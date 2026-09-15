package com.eleckoi.android.feature.chat.ui

import com.eleckoi.android.feature.chat.ui.screen.isCurrentLiveReplyMeasurement
import com.eleckoi.android.feature.chat.ui.screen.keyboardViewportOwnsLiveTail
import com.eleckoi.android.feature.chat.ui.screen.keyboardViewportScrollDelta
import com.eleckoi.android.feature.chat.ui.screen.shouldAnchorGeneratingChatToEnd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyListEndFollowerTest {
    @Test
    fun `native generation owns the footer only until the reader takes control`() {
        assertTrue(shouldAnchorGeneratingChatToEnd(true, true, false, false))
        assertFalse(shouldAnchorGeneratingChatToEnd(true, false, false, false))
        assertFalse(shouldAnchorGeneratingChatToEnd(true, true, true, false))
        assertFalse(shouldAnchorGeneratingChatToEnd(true, true, false, true))
        assertFalse(shouldAnchorGeneratingChatToEnd(false, true, false, false))
    }

    @Test
    fun `regenerated reply cannot reuse a height measured by an older generation`() {
        val oldGeneration = ChatVisualReplyKey(messageId = "assistant-1", generation = 1)
        val newGeneration = ChatVisualReplyKey(messageId = "assistant-1", generation = 2)

        assertFalse(isCurrentLiveReplyMeasurement(newGeneration, oldGeneration))
        assertTrue(isCurrentLiveReplyMeasurement(newGeneration, newGeneration))
        assertFalse(isCurrentLiveReplyMeasurement(null, newGeneration))
    }

    @Test
    fun `process and final phase get distinct pre-draw anchors without changing reply owner`() {
        val replyKey = ChatVisualReplyKey(messageId = "assistant-1", generation = 3)
        val process = replyKey.phaseAnchor(finalAnswerVisible = false)
        val finalAnswer = replyKey.phaseAnchor(finalAnswerVisible = true)

        assertEquals(replyKey, process.replyKey)
        assertEquals(replyKey, finalAnswer.replyKey)
        assertEquals(ChatVisualReplyPhase.Process, process.phase)
        assertEquals(ChatVisualReplyPhase.FinalAnswer, finalAnswer.phase)
    }

    @Test
    fun `static expansion remains registered until its matching row closes`() {
        val state = LazyListEndFollowState()

        state.setStaticContentExpanded("turn-a", true)
        state.setStaticContentExpanded("turn-b", true)
        assertTrue(state.staticExpansionOwnsViewport)

        state.setStaticContentExpanded("turn-a", false)
        assertTrue(state.staticExpansionOwnsViewport)

        state.setStaticContentExpanded("turn-b", false)
        assertFalse(state.staticExpansionOwnsViewport)
    }

    @Test
    fun `explicit jump to end releases static expansion ownership`() {
        val state = LazyListEndFollowState()
        state.setStaticContentExpanded("turn-a", true)

        state.resumeToEnd()

        assertFalse(state.staticExpansionOwnsViewport)
    }

    @Test
    fun `explicit tail action releases history browsing and requests the footer`() {
        val state = LazyListEndFollowState()
        state.pauseForUser()
        val previousRequest = state.resumeRequest

        state.resumeToEnd()

        assertFalse(state.userBrowsingHistory)
        assertEquals(previousRequest + 1, state.resumeRequest)
    }

    @Test
    fun `measures the footer and its preceding content item`() {
        assertEquals(
            53f,
            calculateDistanceToCurrentEndPx(
                totalItemsCount = 8,
                lastVisibleItemIndex = 7,
                lastVisibleItemOffset = 800,
                lastVisibleItemSize = 1,
                viewportEndOffset = 900,
                afterContentPadding = 152,
                itemSpacing = 0,
                bottomAnchorSizePx = 1f,
            ),
            0f,
        )
        assertEquals(
            53f,
            calculateDistanceToCurrentEndPx(
                totalItemsCount = 8,
                lastVisibleItemIndex = 6,
                lastVisibleItemOffset = 300,
                lastVisibleItemSize = 500,
                viewportEndOffset = 900,
                afterContentPadding = 152,
                itemSpacing = 0,
                bottomAnchorSizePx = 1f,
            ),
            0f,
        )
    }

    @Test
    fun `does not infer a near end distance while earlier items are visible`() {
        assertEquals(
            Float.POSITIVE_INFINITY,
            calculateDistanceToCurrentEndPx(
                totalItemsCount = 8,
                lastVisibleItemIndex = 5,
                lastVisibleItemOffset = 0,
                lastVisibleItemSize = 900,
                viewportEndOffset = 900,
                afterContentPadding = 152,
                itemSpacing = 0,
                bottomAnchorSizePx = 1f,
            ),
            0f,
        )
    }

    @Test
    fun `active turn resumes when the reader deliberately reaches the near end handoff`() {
        assertTrue(shouldResumeEndFollow(true, true, reachedExactEnd = false))
        assertFalse(shouldResumeEndFollow(true, false, reachedExactEnd = false))
    }

    @Test
    fun `idle conversation resumes only at the exact end without near end snapping`() {
        assertFalse(shouldResumeEndFollow(false, true, reachedExactEnd = false))
        assertTrue(shouldResumeEndFollow(false, true, reachedExactEnd = true))
        assertTrue(shouldResumeEndFollow(false, false, reachedExactEnd = true))
        assertFalse(shouldResumeEndFollow(false, false, reachedExactEnd = false))
    }

    @Test
    fun `upward gesture inside the handoff never gives the footer back`() {
        val reachedHandoff = isMeasuredEndHandoffVisit(
            currentDistanceToEndPx = 12f,
            referenceDistanceToEndPx = 8f,
            handoffPx = 16f,
        )

        assertFalse(reachedHandoff)
        assertFalse(
            shouldResumeEndFollow(
                nearEndHandoffEnabled = true,
                reachedNearEndHandoff = reachedHandoff,
                reachedExactEnd = false,
            ),
        )
    }

    @Test
    fun `active turn handoff requires movement toward the small end zone`() {
        assertTrue(
            isMeasuredEndHandoffVisit(
                currentDistanceToEndPx = 12f,
                referenceDistanceToEndPx = 400f,
                handoffPx = 16f,
            ),
        )
        assertFalse(
            isMeasuredEndHandoffVisit(
                currentDistanceToEndPx = 20f,
                referenceDistanceToEndPx = 400f,
                handoffPx = 16f,
            ),
        )
        assertFalse(
            isMeasuredEndHandoffVisit(
                currentDistanceToEndPx = 12f,
                referenceDistanceToEndPx = 8f,
                handoffPx = 16f,
            ),
        )
        assertFalse(
            isMeasuredEndHandoffVisit(
                currentDistanceToEndPx = Float.POSITIVE_INFINITY,
                referenceDistanceToEndPx = Float.POSITIVE_INFINITY,
                handoffPx = 16f,
            ),
        )
    }

    @Test
    fun `exact end handoff requires a downward visit to the physical footer`() {
        assertTrue(
            isMeasuredEndHandoffVisit(
                currentDistanceToEndPx = 0f,
                referenceDistanceToEndPx = 400f,
                handoffPx = 0f,
            ),
        )
        assertFalse(
            isMeasuredEndHandoffVisit(
                currentDistanceToEndPx = 12f,
                referenceDistanceToEndPx = 400f,
                handoffPx = 0f,
            ),
        )
        assertFalse(
            isMeasuredEndHandoffVisit(
                currentDistanceToEndPx = 0f,
                referenceDistanceToEndPx = 0f,
                handoffPx = 0f,
            ),
        )
    }

    @Test
    fun `one gesture can leave the footer and deliberately return to it`() {
        val furthestDistance = fartherDistanceToEndPx(
            previousDistanceToEndPx = 0f,
            currentDistanceToEndPx = 400f,
        )

        assertTrue(
            isMeasuredEndHandoffVisit(
                currentDistanceToEndPx = 0f,
                referenceDistanceToEndPx = furthestDistance,
                handoffPx = 0f,
            ),
        )
    }

    @Test
    fun `renderer shrink at exact footer does not manufacture a gap`() {
        assertEquals(
            0,
            preserveExactEndDuringTailShrink(
                heightDeltaPx = -198,
                distanceToEndPx = 0f,
            ),
        )
        assertEquals(
            -48,
            preserveExactEndDuringTailShrink(
                heightDeltaPx = -48,
                distanceToEndPx = 120f,
            ),
        )
        assertEquals(
            36,
            preserveExactEndDuringTailShrink(
                heightDeltaPx = 36,
                distanceToEndPx = 0f,
            ),
        )
    }

    @Test
    fun `streaming tail follows both growth and agent stage shrink`() {
        assertEquals(
            36,
            streamingTailScrollDelta(
                previousHeightPx = 200,
                measuredHeightPx = 236,
                streamingHeightFollowEnabled = true,
                userBrowsingHistory = false,
                isDragged = false,
            ),
        )
        assertEquals(
            -48,
            streamingTailScrollDelta(
                previousHeightPx = 236,
                measuredHeightPx = 188,
                streamingHeightFollowEnabled = true,
                userBrowsingHistory = false,
                isDragged = false,
            ),
        )
    }

    @Test
    fun `streaming tail geometry never fights the reader`() {
        assertEquals(
            0,
            streamingTailScrollDelta(236, 188, true, userBrowsingHistory = true, isDragged = false),
        )
        assertEquals(
            0,
            streamingTailScrollDelta(236, 188, true, userBrowsingHistory = false, isDragged = true),
        )
        assertEquals(
            0,
            streamingTailScrollDelta(236, 188, false, userBrowsingHistory = false, isDragged = false),
        )
    }

    @Test
    fun `a row giving back its own height keeps the viewport`() {
        // A processed stage folding away at the end of a turn is not the tail of a stream. Gliding
        // with that shrink carries the conversation past the composer.
        assertEquals(
            0,
            streamingTailScrollDelta(
                previousHeightPx = 640,
                measuredHeightPx = 188,
                streamingHeightFollowEnabled = true,
                userBrowsingHistory = false,
                isDragged = false,
                staticExpansionOwnsViewport = true,
            ),
        )
    }

    @Test
    fun `keyboard shrink follows the bottom by only the removed viewport height`() {
        assertEquals(
            420,
            keyboardViewportScrollDelta(
                previousViewportEnd = 960,
                currentViewportEnd = 540,
                bottomOwned = true,
                userBrowsedAwayFromBottom = false,
                isDragged = false,
            ),
        )
    }

    @Test
    fun `keyboard live tail ownership ignores unsettled list geometry`() {
        assertTrue(
            keyboardViewportOwnsLiveTail(
                userBrowsedAwayFromBottom = false,
                isDragged = false,
            ),
        )
        assertFalse(
            keyboardViewportOwnsLiveTail(
                userBrowsedAwayFromBottom = true,
                isDragged = false,
            ),
        )
        assertFalse(
            keyboardViewportOwnsLiveTail(
                userBrowsedAwayFromBottom = false,
                isDragged = true,
            ),
        )
    }

    @Test
    fun `keyboard shrink moves the visible history together with the composer`() {
        assertEquals(
            420,
            keyboardViewportScrollDelta(
                previousViewportEnd = 960,
                currentViewportEnd = 540,
                bottomOwned = true,
                userBrowsedAwayFromBottom = true,
                isDragged = false,
            ),
        )
    }

    @Test
    fun `keyboard viewport never fights an active finger drag`() {
        assertEquals(
            0,
            keyboardViewportScrollDelta(
                previousViewportEnd = 960,
                currentViewportEnd = 540,
                bottomOwned = true,
                userBrowsedAwayFromBottom = false,
                isDragged = true,
            ),
        )
    }

    @Test
    fun `keyboard shrink leaves a non bottom non history viewport alone`() {
        assertEquals(
            0,
            keyboardViewportScrollDelta(
                previousViewportEnd = 960,
                currentViewportEnd = 540,
                bottomOwned = false,
                userBrowsedAwayFromBottom = false,
                isDragged = false,
            ),
        )
    }

    @Test
    fun `keyboard expansion moves a history viewport back down`() {
        assertEquals(
            -420,
            keyboardViewportScrollDelta(
                previousViewportEnd = 540,
                currentViewportEnd = 960,
                bottomOwned = false,
                userBrowsedAwayFromBottom = true,
                isDragged = false,
            ),
        )
    }

    @Test
    fun `keyboard expansion leaves live tail positioning to measured height follow`() {
        assertEquals(
            0,
            keyboardViewportScrollDelta(
                previousViewportEnd = 540,
                currentViewportEnd = 960,
                bottomOwned = true,
                userBrowsedAwayFromBottom = false,
                isDragged = false,
            ),
        )
    }
}
