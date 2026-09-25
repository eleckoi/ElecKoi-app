package com.eleckoi.android.feature.chat.ui.roleplay.web.contract

import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ImmutableAppendedList
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.feature.chat.ui.roleplay.web.host.RoleplayTranscriptPatchPlanner
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptContentPart
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptMessage
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptModel
import com.eleckoi.android.feature.chat.ui.roleplay.web.model.RoleplayTranscriptStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayTranscriptPatchPlannerTest {
    @Test
    fun identicalConfirmedModelsProduceNoBrowserWrite() {
        val model = model(listOf(message("one", "same")))

        assertNull(RoleplayTranscriptPatchPlanner.plan(model, model.copy()))
    }

    @Test
    fun oneChangedMessageDoesNotReplaceTheTranscriptOrder() {
        val baseline = model(listOf(message("one", "before"), message("two", "stable")))
        val next = baseline.copy(messages = listOf(message("one", "after"), message("two", "stable")))

        val patch = requireNotNull(RoleplayTranscriptPatchPlanner.plan(baseline, next))

        assertFalse(patch.has("order"))
        assertEquals(1, patch.getJSONArray("messages").length())
        assertEquals("one", patch.getJSONArray("messages").getJSONObject(0).getString("id"))
    }

    @Test
    fun tenThousandMessageHistoryStillProducesAOneMessagePatch() {
        val stablePrefix = (0 until 9_999).map { index ->
            message("message-$index", "content-$index")
        }
        val baselineMessages = ImmutableAppendedList(
            stablePrefix,
            message("message-9999", "content-9999"),
        )
        val nextMessages = ImmutableAppendedList(
            stablePrefix,
            message("message-9999", "changed"),
        )

        val patch = requireNotNull(
            RoleplayTranscriptPatchPlanner.plan(model(baselineMessages), model(nextMessages)),
        )

        assertFalse(patch.has("order"))
        assertEquals(1, patch.getJSONArray("messages").length())
        assertTrue(patch.toString().length < 2_000)
    }

    @Test
    fun rendererToggleProducesASettingsOnlyPatch() {
        val baseline = model(listOf(message("one", "```html\n<html></html>\n```")))
        val next = baseline.copy(frontendRendererEnabled = false)

        val patch = requireNotNull(RoleplayTranscriptPatchPlanner.plan(baseline, next))

        assertFalse(patch.getBoolean("frontendRendererEnabled"))
        assertFalse(patch.has("messages"))
        assertFalse(patch.has("order"))
    }

    @Test
    fun prependingHistoryUpdatesTheFloorStartWithoutReplacingMessagePayloads() {
        val baseline = model(listOf(message("recent", "same"))).copy(floorStart = 42)
        val next = baseline.copy(floorStart = 41)

        val patch = requireNotNull(RoleplayTranscriptPatchPlanner.plan(baseline, next))

        assertEquals(41, patch.getInt("floorStart"))
        assertFalse(patch.has("messages"))
        assertFalse(patch.has("order"))
    }

    @Test
    fun roleplayMessageTogglesPatchWithoutRebuildingHistory() {
        val baseline = model(listOf(message("one", "same")))
        val next = baseline.copy(
            showRoleplayTimestamps = false,
            showRoleplayMessageFloors = false,
        )

        val patch = requireNotNull(RoleplayTranscriptPatchPlanner.plan(baseline, next))

        assertFalse(patch.getBoolean("showRoleplayTimestamps"))
        assertFalse(patch.getBoolean("showRoleplayMessageFloors"))
        assertFalse(patch.has("messages"))
        assertFalse(patch.has("order"))
    }

    @Test
    fun deleteSelectionProducesAStateOnlyPatch() {
        val baseline = model(listOf(message("one", "first"), message("two", "second")))
        val next = baseline.copy(
            deleteMode = true,
            deleteFromMessageId = "two",
        )

        val patch = requireNotNull(RoleplayTranscriptPatchPlanner.plan(baseline, next))

        assertTrue(patch.getBoolean("deleteMode"))
        assertEquals("two", patch.getString("deleteFromMessageId"))
        assertFalse(patch.has("messages"))
        assertFalse(patch.has("order"))
    }

    @Test
    fun layoutChangeProducesAStateOnlyPatch() {
        val baseline = model(listOf(message("one", "same")))
        val next = baseline.copy(layoutMode = "agent")

        val patch = requireNotNull(RoleplayTranscriptPatchPlanner.plan(baseline, next))

        assertEquals("agent", patch.getString("layoutMode"))
        assertFalse(patch.has("messages"))
        assertFalse(patch.has("order"))
    }

    private fun model(messages: List<RoleplayTranscriptMessage>) = RoleplayTranscriptModel(
        sessionId = "session",
        messages = messages,
        style = style(),
        media = emptyMap(),
        historyHasMore = false,
        historyLoading = false,
    )

    private fun message(id: String, content: String): RoleplayTranscriptMessage {
        val source = ChatMessage(id = id, role = MessageRole.Assistant, content = content)
        return RoleplayTranscriptMessage(
            source = source,
            name = "角色",
            avatarUrl = null,
            copyText = content,
            contentParts = listOf(RoleplayTranscriptContentPart.Text(content)),
            reasoning = "",
            openingOptionIds = emptyList(),
            selectedOpeningIndex = -1,
            hasAgentProcess = false,
            regenerateEnabled = true,
        )
    }

    private fun style() = RoleplayTranscriptStyle(
        text = "#fff", bodyText = "#fff", italicText = "#fff", underlineText = "#fff",
        quoteText = "#fff", inlineCodeText = "#fff", muted = "#aaa", soft = "#888",
        accent = "#09f", panel = "#111", line = "#333", jumpSurface = "#fff",
        avatarBackground = "#222", avatarPlaceholder = "#fff", fontSizePx = 16f,
        lineHeightPx = 24f, letterSpacingPx = 0f, paragraphSpacingPx = 8f,
        nameFontSizePx = 16f, nameLineHeightPx = 22f, avatarWidthPx = 48f,
        avatarHeightPx = 48f, avatarRadiusPx = 12f, avatarGapPx = 12f,
        horizontalPaddingPx = 16f, replySpacingPx = 8f, turnSpacingPx = 12f,
        cardPanel = false, codeForeground = "#fff", codeBackground = "#111",
        codeBorder = "#333", codeHeaderBackground = "#222", codeStyle = "plain",
        codeWrap = true, codeShowAll = true, dark = true,
    )
}
