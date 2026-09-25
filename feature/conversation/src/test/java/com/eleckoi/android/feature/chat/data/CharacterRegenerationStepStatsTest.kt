package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.chat.model.ChatGenerationMetrics
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatSessionGenerationStats
import com.eleckoi.android.feature.chat.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterRegenerationStepStatsTest {
    @Test
    fun `regenerating an older turn drops every removed reply step even after repeated rolls`() {
        val messages = (1..100).flatMap { turn ->
            listOf(
                ChatMessage("user-$turn", MessageRole.User, "Prompt $turn"),
                ChatMessage(
                    "assistant-$turn", MessageRole.Assistant, "Reply $turn",
                    generationMetrics = ChatGenerationMetrics(turns = 1, steps = turn % 4 + 1),
                ),
            )
        }
        val original = ChatSessionGenerationStats(
            metrics = ChatGenerationMetrics(turns = 100, steps = retainedChatStepCount(messages)),
        )
        val retained = messages.take(20 * 2 - 1)
        val baseline = original.forRegeneration(
            retainedTurns = retainedChatTurnCount(retained),
            retainedSteps = retainedChatStepCount(retained),
        )
        val replacement = ChatMessage(
            "replacement", MessageRole.Assistant, "New reply",
            generationMetrics = ChatGenerationMetrics(turns = 1, steps = 3),
        )
        val replacedBranch = retained + replacement
        val rolledAgain = baseline.copy(metrics = baseline.metrics.copy(steps = baseline.metrics.steps + 3))
            .forRegeneration(
                retainedTurns = retainedChatTurnCount(retained),
                retainedSteps = retainedChatStepCount(retained),
            )

        assertEquals(20, baseline.metrics.turns)
        assertEquals(retainedChatStepCount(messages.take(38)), baseline.metrics.steps)
        assertEquals(baseline.metrics.steps + 3, retainedChatStepCount(replacedBranch))
        assertEquals(baseline.metrics.steps, rolledAgain.metrics.steps)
        assertEquals(0, original.forRegeneration(1, retainedChatStepCount(messages.take(1))).metrics.steps)
    }
}
