package com.eleckoi.android.app.service

import android.graphics.BitmapFactory
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.agent.tools.AgentToolRequestPolicy
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.engine.generation.image.SceneImagePrompt
import com.eleckoi.android.engine.generation.image.parseSceneImagePrompts
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.presets.model.AgentPreset
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.chat.data.GenerationAttemptRepository
import com.eleckoi.android.feature.chat.model.ChatDraft
import com.eleckoi.android.feature.chat.model.ChatMessage
import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatImageStatus
import com.eleckoi.android.feature.chat.model.ChatSession
import com.eleckoi.android.feature.settings.data.appearance.AppearanceRepository
import com.eleckoi.android.foundation.design.AppearanceTheme
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import java.io.File
import kotlinx.coroutines.CancellationException

internal class ChatMediaCoordinator(
    private val characters: CharacterRepository,
    private val sessions: ChatSessionStore,
    private val settings: ModelConfigRepository,
    private val appearance: AppearanceRepository,
    private val replyImageGenerator: ReplyImageGenerator,
    private val generationAttempts: GenerationAttemptRepository,
    private val activeAgentPreset: suspend () -> AgentPreset,
    private val projectDraft: (ChatSession) -> ChatDraft,
) {
    suspend fun regenerateImage(
        sessionId: String,
        messageId: String,
        attachmentId: String,
    ): ChatDraft {
        val session = sessions.load(sessionId, touch = false)
        if (session.messages.any(ChatMessage::pending)) {
            throw ElecKoiDataException("角色回复仍在生成，暂时不能重画图片")
        }
        val messageIndex = session.messages.indexOfFirst { it.id == messageId }
        if (messageIndex < 0) throw ElecKoiDataException("没有找到这条回复")
        val message = session.messages[messageIndex]
        val attachmentIndex = message.imageAttachments.indexOfFirst { it.id == attachmentId }
        if (attachmentIndex < 0) throw ElecKoiDataException("没有找到这张图片")
        val previous = message.imageAttachments[attachmentIndex]
        if (previous.status == ChatImageStatus.Generating) {
            throw ElecKoiDataException("这张图片已经在生成")
        }
        val toolConfiguration = activeAgentPreset().toolConfiguration.normalized()
        val selectedImageConfigId = toolConfiguration.toolModelConfigIds[
            AgentToolRequestPolicy.BuiltInAutoIllustration
        ].orEmpty()
        if (AgentToolRequestPolicy.BuiltInAutoIllustration !in toolConfiguration.enabledGroupIds) {
            throw ElecKoiDataException("当前预设没有启用配图工具")
        }
        val imageConfig = settings.loadModelConfigCollection().configs.firstOrNull {
            it.id == selectedImageConfigId && it.isImageGenerationConfig()
        }
            ?: throw ElecKoiDataException("当前没有启用配图配置")
        val scenePrompt = storedScenePrompt(message, attachmentIndex, previous)
        val imageAttempt = generationAttempts.beginImage(
            conversationId = sessionId,
            attachmentId = previous.id,
            outputMessageId = message.id,
            parentAttemptId = generationAttempts.latestReplyForMessage(sessionId, message.id)?.id,
        )
        val replacement = previous.copy(
            generationAttemptId = imageAttempt.id,
            localPath = "",
            status = ChatImageStatus.Generating,
            errorMessage = "",
            prompt = scenePrompt.prompt,
            negativePrompt = scenePrompt.negativePrompt,
            afterParagraph = scenePrompt.afterParagraph,
            imageWidth = imageConfig.imageSettings.width,
            imageHeight = imageConfig.imageSettings.height,
        )
        if (!sessions.installImageAttempt(sessionId, message.id, replacement)) {
            generationAttempts.cancel(imageAttempt.id, "已被新的重画请求替代")
            return projectDraft(sessions.load(sessionId, touch = false))
        }
        // A manual regeneration owns the same queued -> running lifecycle as an Agent image. A
        // newer long-press request may supersede this attempt before it reaches the generator.
        if (!generationAttempts.markImageRunning(imageAttempt.id)) {
            return projectDraft(sessions.load(sessionId, touch = false))
        }

        val completed = try {
            val path = replyImageGenerator.generate(
                imageConfig = imageConfig,
                sessionId = session.id,
                imageId = "${replacement.id}-${imageAttempt.id}",
                characterImagePrompt = session.characterPersona.imagePrompt,
                scenePrompt = scenePrompt,
            )
            replacement.copy(localPath = path, status = ChatImageStatus.Ready).also {
                generationAttempts.succeed(imageAttempt.id, outputPath = path)
            }
        } catch (error: CancellationException) {
            val cancelled = replacement.copy(
                status = ChatImageStatus.Failed,
                errorMessage = "图片生成已停止",
            )
            generationAttempts.cancel(imageAttempt.id, cancelled.errorMessage)
            sessions.settleImageAttempt(sessionId, message.id, cancelled)
            throw error
        } catch (error: Throwable) {
            replacement.copy(
                status = ChatImageStatus.Failed,
                errorMessage = error.message
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.take(180)
                    .orEmpty()
                    .ifBlank { "图片生成失败" },
            ).also { failed ->
                generationAttempts.fail(imageAttempt.id, failed.errorMessage)
            }
        }
        val committed = sessions.settleImageAttempt(sessionId, message.id, completed)
        if (!committed && completed.localPath.isNotBlank()) {
            replyImageGenerator.deleteGeneratedFiles(listOf(completed.localPath))
        } else if (completed.status == ChatImageStatus.Ready && previous.localPath.isNotBlank()) {
            replyImageGenerator.deleteGeneratedFiles(listOf(previous.localPath))
        }
        return projectDraft(sessions.load(sessionId, touch = false))
    }

    fun saveCharacterChatBackground(
        characterId: String,
        backgroundFile: File?,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): CharacterSlot {
        return characters.saveCharacterChatBackground(characterId, backgroundFile, opacity, blur, scrim)
    }

    fun restoreCharacterChatBackgroundDefault(characterId: String): CharacterSlot {
        return characters.restoreCharacterChatBackgroundDefault(characterId)
    }

    fun useCharacterCardChatBackground(characterId: String): CharacterSlot {
        return characters.useCharacterCardChatBackground(characterId)
    }

    fun useCustomChatBackground(characterId: String): CharacterSlot {
        return characters.useCustomChatBackground(characterId)
    }

    fun useGlobalChatBackground(characterId: String): CharacterSlot {
        return characters.useGlobalChatBackground(characterId)
    }

    fun applyGlobalChatBackground(sourceCharacterId: String): CharacterSlot {
        return characters.applyGlobalChatBackground(sourceCharacterId)
    }

    // A null file means the user only dragged the sliders, so the stored image stays where it is
    // and only the tuning is rewritten — re-encoding the same bitmap on every gesture would churn
    // the settings directory for nothing.
    suspend fun saveGlobalChatBackground(
        backgroundFile: File?,
        opacity: Float,
        blur: Float,
        scrim: Float,
    ): AppearanceTheme {
        val bitmap = backgroundFile
            ?.takeIf(File::exists)
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?: return appearance.saveGlobalBackgroundTuning(opacity, blur, scrim)
        return appearance.saveGlobalBackground(
            bitmap = bitmap,
            opacity = opacity,
            blur = blur,
            scrim = scrim,
        )
    }

    suspend fun clearGlobalChatBackground(): AppearanceTheme = appearance.clearGlobalBackground()

    private fun storedScenePrompt(
        message: ChatMessage,
        attachmentIndex: Int,
        attachment: ChatImageAttachment,
    ): SceneImagePrompt {
        if (attachment.prompt.isNotBlank()) {
            return SceneImagePrompt(
                prompt = attachment.prompt,
                negativePrompt = attachment.negativePrompt,
                frameIndex = attachment.frameIndex,
                afterParagraph = attachment.afterParagraph,
            )
        }
        val fromAction = message.toolCalls.asReversed()
            .firstOrNull { it.toolName == "generate_image" }
            ?.arguments
            ?.let { arguments -> runCatching { parseSceneImagePrompts(arguments) }.getOrNull() }
            ?.getOrNull(attachmentIndex)
        return fromAction ?: throw ElecKoiDataException("这张旧图片没有保存提示词，无法重新生成")
    }}
