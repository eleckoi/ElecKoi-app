package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatImageStatus
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.chat.model.MessageRole
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CharacterAgentRegenerationTest {
    @Test
    fun `generation restores raw ledger messages instead of reusing display projection`() {
        val rawOpening = message(
            id = "opening",
            role = MessageRole.Assistant,
            content = "<DisplayMount/>\n<DisplayMount/>",
        )
        val displayOpening = rawOpening.copy(
            content = "<!-- eleckoi:rich-replacement:start -->rendered<!-- eleckoi:rich-replacement:end -->\n" +
                "<DisplayMount/>",
        )
        val persisted = ChatSession(
            id = "session",
            title = "title",
            characterId = "character",
            characterName = "character",
            characterAvatar = "",
            characterPersona = CharacterCard(),
            messages = listOf(displayOpening),
            updatedAt = "now",
        )

        val restored = authoritativeGenerationSession(
            persisted = persisted,
            activeMessages = listOf(rawOpening),
        )

        assertEquals(listOf(rawOpening), restored.messages)
    }

    @Test
    fun `rounded provider completion cannot make displayed processing time count backwards`() {
        assertEquals(
            10_850L,
            stableChatTurnCompletionAtMillis(
                providerCompletedAtMillis = 9_000L,
                locallyObservedAtMillis = 10_850L,
            ),
        )
    }

    @Test
    fun `regeneration deletes the target reply and every message below it`() {
        val messages = listOf(
            message("user-1", MessageRole.User, "第一问"),
            message("assistant-1", MessageRole.Assistant, "第一答"),
            message("user-2", MessageRole.User, "第二问"),
            message(
                "assistant-2",
                MessageRole.Assistant,
                "不要的第二答",
                imagePath = "/generated/old-second.png",
                runtimeThreadId = "runtime-old-branch",
            ),
            message("user-3", MessageRole.User, "下面也不要"),
            message(
                "assistant-3",
                MessageRole.Assistant,
                "下面全部不要",
                imagePath = "/generated/old-third.png",
            ),
        )

        val result = truncateForRegeneration(
            messages = messages,
            targetMessageId = "assistant-2",
            replacementMessage = null,
            provider = "provider",
            model = "model",
        )

        assertEquals(listOf("user-1", "assistant-1", "user-2"), result.messages.map { it.id })
        assertEquals("第二问", result.prompt)
        assertEquals("assistant-2", result.replacementMessageId)
        assertEquals(
            listOf("/generated/old-second.png", "/generated/old-third.png"),
            result.removedImagePaths,
        )
        assertEquals(setOf("runtime-old-branch"), result.obsoleteRuntimeThreadIds)
    }

    @Test
    fun `a user message without an assistant reply can start regeneration`() {
        val messages = listOf(
            message("opening", MessageRole.Assistant, "开场白"),
            message("user-1", MessageRole.User, "一起走吗"),
        )

        val result = truncateForRegeneration(
            messages = messages,
            targetMessageId = "user-1",
            replacementMessage = null,
            provider = "provider",
            model = "model",
        )

        assertEquals(listOf("opening", "user-1"), result.messages.map { it.id })
        assertEquals("一起走吗", result.prompt)
        assertEquals(null, result.replacementMessageId)
    }

    @Test
    fun `regeneration clears stale trajectory bindings from retained messages`() {
        val result = truncateForRegeneration(
            messages = listOf(
                message("user-1", MessageRole.User, "第一问"),
                message(
                    id = "assistant-1",
                    role = MessageRole.Assistant,
                    content = "第一答",
                    runtimeThreadId = "runtime-whole-branch",
                ),
                message("user-2", MessageRole.User, "第二问"),
                message(
                    id = "assistant-2",
                    role = MessageRole.Assistant,
                    content = "第二答",
                    runtimeThreadId = "runtime-whole-branch",
                ),
            ),
            targetMessageId = "assistant-2",
            replacementMessage = null,
            provider = "provider",
            model = "model",
        )

        assertEquals(setOf("runtime-whole-branch"), result.obsoleteRuntimeThreadIds)
        assertEquals(listOf("user-1", "assistant-1", "user-2"), result.messages.map { it.id })
        assertEquals(listOf("", "", ""), result.messages.map { it.runtimeThreadId })
        assertEquals(listOf("", "", ""), result.messages.map { it.runtimeTurnId })
    }

    @Test
    fun `regeneration restores the retained pre-reply variable snapshot`() {
        val messages = listOf(
            message(
                id = "user-1",
                role = MessageRole.User,
                content = "一起走吗",
                variableStateJson = """{"好感度":25}""",
            ),
            message(
                id = "assistant-1",
                role = MessageRole.Assistant,
                content = "第一次回答",
                variableStateJson = """{"好感度":31}""",
            ),
        )

        val firstReroll = truncateForRegeneration(
            messages = messages,
            targetMessageId = "assistant-1",
            replacementMessage = null,
            provider = "provider",
            model = "model",
        )
        val secondReroll = truncateForRegeneration(
            messages = firstReroll.messages + message(
                id = "assistant-1",
                role = MessageRole.Assistant,
                content = "第二次回答",
                variableStateJson = """{"好感度":52}""",
            ),
            targetMessageId = "assistant-1",
            replacementMessage = null,
            provider = "provider",
            model = "model",
        )

        assertEquals("""{"好感度":25}""", firstReroll.retainedVariableStateJson)
        assertEquals("""{"好感度":25}""", secondReroll.retainedVariableStateJson)
    }

    @Test
    fun `regeneration uses the nearest retained snapshot for a legacy user message`() {
        val result = truncateForRegeneration(
            messages = listOf(
                message(
                    id = "assistant-previous",
                    role = MessageRole.Assistant,
                    content = "上一轮",
                    variableStateJson = """{"好感度":18}""",
                ),
                message("user-legacy", MessageRole.User, "继续"),
                message(
                    id = "assistant-current",
                    role = MessageRole.Assistant,
                    content = "要被替换",
                    variableStateJson = """{"好感度":30}""",
                ),
            ),
            targetMessageId = "assistant-current",
            replacementMessage = null,
            provider = "provider",
            model = "model",
        )

        assertEquals("""{"好感度":18}""", result.retainedVariableStateJson)
    }

    @Test
    fun `regeneration never invents a baseline when retained history has no snapshot`() {
        val result = truncateForRegeneration(
            messages = listOf(
                message("user-legacy", MessageRole.User, "继续"),
                message("assistant-current", MessageRole.Assistant, "要被替换"),
            ),
            targetMessageId = "assistant-current",
            replacementMessage = null,
            provider = "provider",
            model = "model",
        )

        assertNull(result.retainedVariableStateJson)
        assertThrows(ElecKoiDataException::class.java) {
            regenerationSessionVariableState(
                currentStateJson = """{"好感度":52}""",
                retainedStateJson = result.retainedVariableStateJson,
                variablesConfigured = true,
            )
        }
    }

    @Test
    fun `chat without configured variables may keep its inert session state`() {
        assertEquals(
            "{}",
            regenerationSessionVariableState(
                currentStateJson = "{}",
                retainedStateJson = null,
                variablesConfigured = false,
            ),
        )
    }

    private fun message(
        id: String,
        role: MessageRole,
        content: String,
        imagePath: String = "",
        variableStateJson: String = "",
        runtimeThreadId: String = "",
    ) = ChatMessage(
        id = id,
        role = role,
        content = content,
        variableStateJson = variableStateJson,
        runtimeThreadId = runtimeThreadId,
        imageAttachments = imagePath.takeIf(String::isNotBlank)?.let { path ->
            listOf(
                ChatImageAttachment(
                    id = "image-$id",
                    localPath = path,
                    status = ChatImageStatus.Ready,
                ),
            )
        }.orEmpty(),
    )
}
