package com.eleckoi.android.feature.chat.ui.trajectory

import androidx.compose.foundation.lazy.LazyListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DshTrajectoryLedgerStateTest {
    @Test
    fun `opening compact inspector preserves the exact ledger viewport`() {
        val state = DshTrajectoryLedgerState(
            listState = LazyListState(
                firstVisibleItemIndex = 37,
                firstVisibleItemScrollOffset = 19,
            ),
        )

        state.preserveViewportForInspector()

        assertEquals(37, state.listState.firstVisibleItemIndex)
        assertEquals(19, state.listState.firstVisibleItemScrollOffset)
        assertTrue(state.initialScrollCompleted)
        assertTrue(state.restoreViewportOnNextAttach)
    }
}
