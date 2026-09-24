package com.eleckoi.android.feature.studio.ui.assistant.session

import com.eleckoi.android.feature.conversation.timeline.model.CreationTimelineItem
import com.eleckoi.android.feature.conversation.timeline.toCreationTurns

internal data class CreationRegenerationPlan(
    val prompt: String,
    val stableHistory: List<CreationTimelineItem>,
    val retainedUser: CreationTimelineItem,
    val retainedTurns: Int,
)

/**
 * Selects a committed user branch and removes every generated event that followed it. The original
 * user id is retained so Room truncates the exact turn, while an optional replacement becomes the
 * new raw prompt. Runtime ownership is cleared so the new Harness turn cannot accidentally inherit
 * the deleted reply's identity or native history.
 */
internal fun planCreationRegeneration(
    timeline: List<CreationTimelineItem>,
    restartedAtMillis: Long,
    targetUserId: String? = null,
    replacementText: String? = null,
): CreationRegenerationPlan? {
    // A committed steer is also a User item, but it lives inside the current runtime turn and is
    // not a Room branch turn of its own. Regeneration must retain the turn's source user—the same
    // identity Room can truncate—not accidentally target a supplemental steer from the response.
    val committedUsers = timeline
        .toCreationTurns(isRunning = false)
        .mapNotNull { turn ->
            turn.user?.takeIf { it.text.isNotBlank() || it.inputImages.isNotEmpty() }
        }
    val sourceUser = if (targetUserId == null) {
        committedUsers.lastOrNull()
    } else {
        committedUsers.firstOrNull { it.id == targetUserId }
    }
        ?: return null
    val userIndex = timeline.indexOfLast { item -> item.id == sourceUser.id }
    if (userIndex < 0) return null
    val source = timeline[userIndex]
    val prompt = replacementText?.trim()?.takeIf(String::isNotEmpty) ?: source.text
    val stableHistory = timeline.subList(0, userIndex).toList()
    return CreationRegenerationPlan(
        prompt = prompt,
        stableHistory = stableHistory,
        retainedTurns = stableHistory.toCreationTurns(isRunning = false).count { it.user != null } + 1,
        retainedUser = source.copy(
            text = prompt,
            running = false,
            failed = false,
            workItemId = null,
            workItemType = null,
            turnId = null,
            // The prompt is still the same raw message, while the regenerated answer is a new
            // processing attempt. Keep those two clocks separate.
            turnStartedAtMillis = restartedAtMillis,
            completedAtMillis = null,
            fileChanges = emptyList(),
            paths = emptyList(),
            diff = "",
            turnDiffObserved = false,
            messagePhase = null,
            phaseHeader = null,
            toolName = "",
            toolArguments = "",
            delegatedModel = "",
            childTimeline = emptyList(),
            delegatedSessionId = "",
            rawCommand = "",
            commandActions = emptyList(),
            modelHistoryItems = emptyList(),
        ),
    )
}
