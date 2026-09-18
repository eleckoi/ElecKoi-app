package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.feature.chat.model.ChatImageAttachment
import com.eleckoi.android.feature.chat.model.ChatImageStatus

internal fun roleConversationId(sessionId: String): String =
    "role_${java.util.UUID.nameUUIDFromBytes(sessionId.toByteArray(Charsets.UTF_8))}"

internal fun recoverImageAttachment(
    image: ChatImageAttachment,
    latest: GenerationAttempt?,
    interruptedReason: String,
): ChatImageAttachment {
    if (latest == null) {
        return if (image.status == ChatImageStatus.Generating) {
            image.copy(status = ChatImageStatus.Failed, errorMessage = interruptedReason)
        } else {
            image
        }
    }
    val projected = image.copy(generationAttemptId = latest.id)
    return when (latest.state) {
        GenerationAttemptState.Queued,
        GenerationAttemptState.Running,
        -> projected.copy(
            localPath = "",
            status = ChatImageStatus.Generating,
            errorMessage = "",
        )

        GenerationAttemptState.Succeeded -> if (latest.outputPath.isNotBlank()) {
            projected.copy(
                localPath = latest.outputPath,
                status = ChatImageStatus.Ready,
                errorMessage = "",
            )
        } else {
            projected.copy(
                localPath = "",
                status = ChatImageStatus.Failed,
                errorMessage = "图片生成结果缺少本地文件",
            )
        }

        GenerationAttemptState.Failed,
        GenerationAttemptState.Cancelled,
        GenerationAttemptState.Interrupted,
        GenerationAttemptState.Superseded,
        -> projected.copy(
            localPath = "",
            status = ChatImageStatus.Failed,
            errorMessage = latest.errorMessage.ifBlank { interruptedReason },
        )
    }
}
