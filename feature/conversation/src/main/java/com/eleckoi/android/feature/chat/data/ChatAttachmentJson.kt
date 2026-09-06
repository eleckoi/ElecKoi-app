package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatImageStatus
import com.eleckoi.android.feature.chat.model.ChatUserImageAttachment
import com.eleckoi.android.feature.chat.model.DefaultGeneratedImageHeight
import com.eleckoi.android.feature.chat.model.DefaultGeneratedImageWidth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatImageAttachmentJson(
    val id: String = "",
    @SerialName("generation_attempt_id")
    val generationAttemptId: String = "",
    @SerialName("local_path")
    val localPath: String = "",
    val status: String = "generating",
    @SerialName("error_message")
    val errorMessage: String = "",
    val prompt: String = "",
    @SerialName("negative_prompt")
    val negativePrompt: String = "",
    @SerialName("after_paragraph")
    val afterParagraph: Int = Int.MAX_VALUE,
    @SerialName("frame_index")
    val frameIndex: Int = 1,
    @SerialName("frame_count")
    val frameCount: Int = 1,
    @SerialName("image_width")
    val imageWidth: Int = DefaultGeneratedImageWidth,
    @SerialName("image_height")
    val imageHeight: Int = DefaultGeneratedImageHeight,
) {
    fun toDomain(): ChatImageAttachment = ChatImageAttachment(
        id = id,
        generationAttemptId = generationAttemptId,
        localPath = localPath,
        status = ChatImageStatus.entries.firstOrNull { it.name.equals(status, ignoreCase = true) }
            ?: ChatImageStatus.Generating,
        errorMessage = errorMessage,
        prompt = prompt,
        negativePrompt = negativePrompt,
        afterParagraph = afterParagraph.coerceAtLeast(1),
        frameIndex = frameIndex.coerceAtLeast(1),
        frameCount = frameCount.coerceAtLeast(1),
        imageWidth = imageWidth.coerceAtLeast(1),
        imageHeight = imageHeight.coerceAtLeast(1),
    )

    companion object {
        fun fromDomain(image: ChatImageAttachment): ChatImageAttachmentJson = ChatImageAttachmentJson(
            id = image.id,
            generationAttemptId = image.generationAttemptId,
            localPath = image.localPath,
            status = image.status.name.lowercase(),
            errorMessage = image.errorMessage,
            prompt = image.prompt,
            negativePrompt = image.negativePrompt,
            afterParagraph = image.afterParagraph,
            frameIndex = image.frameIndex,
            frameCount = image.frameCount,
            imageWidth = image.imageWidth,
            imageHeight = image.imageHeight,
        )
    }
}

@Serializable
internal data class ChatUserImageAttachmentJson(
    val id: String = "",
    @SerialName("local_path") val localPath: String = "",
    @SerialName("media_type") val mediaType: String = "image/jpeg",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("creator_media_reference") val creatorMediaReference: String = "",
    val bytes: Long = 0L,
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
) {
    fun toDomain(): ChatUserImageAttachment = ChatUserImageAttachment(
        id = id,
        localPath = localPath,
        mediaType = mediaType,
        displayName = displayName,
        creatorMediaReference = creatorMediaReference,
        bytes = bytes.coerceAtLeast(0L),
        imageWidth = imageWidth.coerceAtLeast(0),
        imageHeight = imageHeight.coerceAtLeast(0),
    )

    companion object {
        fun fromDomain(image: ChatUserImageAttachment) = ChatUserImageAttachmentJson(
            id = image.id,
            localPath = image.localPath,
            mediaType = image.mediaType,
            displayName = image.displayName,
            creatorMediaReference = image.creatorMediaReference,
            bytes = image.bytes,
            imageWidth = image.imageWidth,
            imageHeight = image.imageHeight,
        )
    }
}
