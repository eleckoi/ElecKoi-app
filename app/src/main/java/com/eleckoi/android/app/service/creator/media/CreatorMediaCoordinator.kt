package com.eleckoi.android.app.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.generation.image.ReplyImageGenerator
import com.eleckoi.android.engine.generation.image.SceneImagePrompt
import com.eleckoi.android.engine.generation.model.isImageGenerationConfig
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.studio.api.CreatorCharacterMediaState
import com.eleckoi.android.feature.studio.api.CreatorMediaAsset
import com.eleckoi.android.feature.studio.api.CreatorMediaAssetPage
import com.eleckoi.android.feature.studio.api.CreatorMediaAssetSource
import com.eleckoi.android.foundation.design.components.centerCropBitmap
import com.eleckoi.android.foundation.design.components.saveBitmapToCache
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import java.io.File
import java.util.Base64
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal class CreatorMediaCoordinator(
    private val creatorWorkspaces: CreatorWorkspaceRepository,
    private val modelConfigs: ModelConfigRepository,
    private val replyImageGenerator: ReplyImageGenerator,
    private val characters: CharacterRepository,
    private val rootResolver: CreatorCharacterRootResolver,
    private val mediaCacheDirectory: File,
    private val imageModelConfigId: () -> String,
) {
    private val mediaMutex = Mutex()

    suspend fun creatorCharacterMedia(
        workspaceId: String,
        rootId: String,
    ): CreatorCharacterMediaState = withContext(Dispatchers.IO) {
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        character.toCreatorMediaState(root.id)
    }

    suspend fun registerCreatorMediaAsset(
        workspaceId: String,
        sourceFile: File,
        displayName: String,
        source: CreatorMediaAssetSource,
    ): CreatorMediaAsset = withContext(Dispatchers.IO) {
        mediaMutex.withLock {
            val image = inspectImage(sourceFile)
            val manifest = loadMediaManifest(workspaceId)
            require(manifest.assets.size < MaxCreatorMediaAssets) { "当前工作区的创作图片已达到上限" }
            val assetId = "media-${UUID.randomUUID()}"
            val storedFile = creatorWorkspaces.importCreatorMediaAsset(
                workspaceId = workspaceId,
                assetId = assetId,
                extension = image.extension,
                source = sourceFile,
            )
            val stored = StoredCreatorMediaAsset(
                id = assetId,
                displayName = displayName.trim().take(80)
                    .ifBlank { sourceFile.nameWithoutExtension.trim().take(80) }
                    .ifBlank { "创作图片" },
                mimeType = image.mimeType,
                width = image.width,
                height = image.height,
                byteSize = storedFile.length(),
                source = source.storageValue,
                createdAt = Instant.now().toString(),
            )
            try {
                saveMediaManifest(workspaceId, manifest.copy(assets = manifest.assets + stored))
                stored.toModel()
            } catch (error: Throwable) {
                creatorWorkspaces.deleteCreatorMediaAsset(workspaceId, assetId)
                throw error
            }
        }
    }

    suspend fun generateCreatorMediaAsset(
        workspaceId: String,
        prompt: String,
        negativePrompt: String,
        displayName: String,
    ): CreatorMediaAsset = withContext(Dispatchers.IO) {
        requireNotNull(creatorWorkspaces.get(workspaceId)) { "创作工作区不存在" }
        val normalizedPrompt = prompt.trim().take(MaxCreatorImagePromptChars)
        require(normalizedPrompt.isNotBlank()) { "图片提示词不能为空" }
        val selectedImageConfigId = imageModelConfigId()
        val imageConfig = modelConfigs.loadModelConfigCollection().configs.firstOrNull {
            it.id == selectedImageConfigId && it.isImageGenerationConfig()
        }
            ?: error("尚未选择图片生成模型，请到 AI 创作助手的「创作能力」中配置")
        require(imageConfig.apiKey.isNotBlank() && !imageConfig.apiKeyNeedsReentry) {
            "图片生成模型 API Key 不可用，请先在模型配置中重新填写"
        }
        val generatedPath = replyImageGenerator.generate(
            imageConfig = imageConfig,
            sessionId = "creator-$workspaceId",
            imageId = "creator-${UUID.randomUUID()}",
            characterImagePrompt = "",
            scenePrompt = SceneImagePrompt(
                prompt = normalizedPrompt,
                negativePrompt = negativePrompt.trim().take(MaxCreatorImageNegativePromptChars),
            ),
            includeConfiguredPromptDefaults = false,
        )
        try {
            registerCreatorMediaAsset(
                workspaceId = workspaceId,
                sourceFile = File(generatedPath),
                displayName = displayName.trim().take(80).ifBlank { "创作图片" },
                source = CreatorMediaAssetSource.Generated,
            )
        } finally {
            replyImageGenerator.deleteGeneratedFiles(listOf(generatedPath))
        }
    }

    suspend fun searchCreatorMediaAssets(
        workspaceId: String,
        cursor: String,
        limit: Int,
    ): CreatorMediaAssetPage = withContext(Dispatchers.IO) {
        mediaMutex.withLock {
            val assets = loadMediaManifest(workspaceId).assets.sortedByDescending { it.createdAt }
            val offset = cursor.ifBlank { "0" }.toIntOrNull()
                ?.takeIf { it >= 0 }
                ?: error("创作媒体 cursor 无效")
            val bounded = limit.coerceIn(1, MaxCreatorPageSize)
            val page = assets.drop(offset).take(bounded)
            val nextOffset = offset + page.size
            CreatorMediaAssetPage(
                items = page.map(StoredCreatorMediaAsset::toModel),
                nextCursor = nextOffset.toString().takeIf { nextOffset < assets.size }.orEmpty(),
            )
        }
    }

    suspend fun creatorMediaAsset(
        workspaceId: String,
        assetId: String,
    ): CreatorMediaAsset? = withContext(Dispatchers.IO) {
        mediaMutex.withLock {
            val stored = loadMediaManifest(workspaceId).assets.firstOrNull { it.id == assetId }
                ?: return@withLock null
            if (creatorWorkspaces.creatorMediaAssetFile(workspaceId, assetId) == null) null else stored.toModel()
        }
    }

    /** Describes a conversation-owned input without importing it into the workspace manifest. */
    suspend fun creatorInputAttachment(
        workspaceId: String,
        assetId: String,
        sourceFile: File,
    ): CreatorMediaAsset = withContext(Dispatchers.IO) {
        requireNotNull(creatorWorkspaces.get(workspaceId)) { "创作工作区不存在" }
        val image = inspectImage(sourceFile)
        CreatorMediaAsset(
            id = assetId,
            displayName = "对话上传图片",
            mimeType = image.mimeType,
            width = image.width,
            height = image.height,
            byteSize = sourceFile.length(),
            source = CreatorMediaAssetSource.ConversationAttachment,
            createdAt = Instant.ofEpochMilli(sourceFile.lastModified()).toString(),
        )
    }

    suspend fun applyCreatorMediaAsset(
        workspaceId: String,
        rootId: String,
        assetId: String,
        slots: Set<AvatarSlot>,
    ): CharacterSlot = withContext(Dispatchers.IO) {
        val sourceFile = mediaMutex.withLock {
            require(loadMediaManifest(workspaceId).assets.any { it.id == assetId }) { "创作媒体 asset 不存在" }
            creatorWorkspaces.creatorMediaAssetFile(workspaceId, assetId)
                ?: error("创作媒体文件已经不存在")
        }
        applyCreatorMediaFile(workspaceId, rootId, sourceFile, slots)
    }

    /** Copies only the selected conversation attachment into permanent character-owned media. */
    suspend fun applyCreatorInputAttachment(
        workspaceId: String,
        rootId: String,
        sourceFile: File,
        slots: Set<AvatarSlot>,
    ): CharacterSlot = withContext(Dispatchers.IO) {
        applyCreatorMediaFile(workspaceId, rootId, sourceFile, slots)
    }

    private suspend fun applyCreatorMediaFile(
        workspaceId: String,
        rootId: String,
        sourceFile: File,
        slots: Set<AvatarSlot>,
    ): CharacterSlot {
        require(slots.isNotEmpty()) { "至少选择一个角色图片槽位" }
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        require(root.access == CreatorWorkspaceRootAccess.ReadWrite) { "这个角色根当前是只读的" }
        val staged = stageCreatorMediaSlots(sourceFile, slots)
        try {
            return characters.saveCharacterAvatars(character.id, staged)
        } finally {
            staged.values.forEach(File::delete)
        }
    }

    suspend fun clearCreatorCharacterMedia(
        workspaceId: String,
        rootId: String,
        slots: Set<AvatarSlot>,
    ): CharacterSlot = withContext(Dispatchers.IO) {
        require(slots.isNotEmpty()) { "至少选择一个角色图片槽位" }
        val (root, character) = rootResolver.requireRoot(workspaceId, rootId)
        require(root.access == CreatorWorkspaceRootAccess.ReadWrite) { "这个角色根当前是只读的" }
        characters.clearCharacterAvatarSlots(character.id, slots)
    }


    private suspend fun loadMediaManifest(workspaceId: String): CreatorMediaAssetManifest {
        val raw = creatorWorkspaces.readInternalState(workspaceId, CreatorMediaManifestFileName)
            ?: return CreatorMediaAssetManifest()
        return runCatching { ElecKoiPrettyJson.decodeFromString<CreatorMediaAssetManifest>(raw) }
            .getOrElse { error("创作媒体资产索引损坏") }
    }

    private suspend fun saveMediaManifest(
        workspaceId: String,
        manifest: CreatorMediaAssetManifest,
    ) {
        creatorWorkspaces.writeInternalState(
            workspaceId,
            CreatorMediaManifestFileName,
            ElecKoiPrettyJson.encodeToString(manifest),
        )
    }

    /** Roll owns generated candidates from the removed branch, not unrelated uploads or assets. */
    suspend fun deleteGeneratedMediaAssets(
        workspaceId: String,
        assetIds: Set<String>,
    ) {
        if (assetIds.isEmpty()) return
        mediaMutex.withLock {
            val manifest = loadMediaManifest(workspaceId)
            val removable = manifest.assets.filter { asset ->
                asset.id in assetIds && asset.source == CreatorMediaAssetSource.Generated.storageValue
            }
            if (removable.isEmpty()) return@withLock
            saveMediaManifest(
                workspaceId,
                manifest.copy(assets = manifest.assets.filterNot { asset -> asset in removable }),
            )
            removable.forEach { asset ->
                creatorWorkspaces.deleteCreatorMediaAsset(workspaceId, asset.id)
            }
        }
    }

    private fun inspectImage(file: File): CreatorImageInfo {
        require(file.isFile && file.length() in 1..MaxCreatorMediaAssetBytes) {
            "图片文件不存在或超过 32 MB"
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0) { "无法读取图片" }
        require(width <= MaxCreatorImageDimension && height <= MaxCreatorImageDimension) { "图片尺寸过大" }
        require(width.toLong() * height.toLong() <= MaxCreatorImagePixels) { "图片像素数过大" }
        val mimeType = bounds.outMimeType?.lowercase().orEmpty()
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> error("只支持 PNG、JPEG、WebP 或 GIF 图片")
        }
        return CreatorImageInfo(width, height, mimeType, extension)
    }

    private fun stageCreatorMediaSlots(
        sourceFile: File,
        slots: Set<AvatarSlot>,
    ): Map<AvatarSlot, File> {
        val info = inspectImage(sourceFile)
        val requiredWidth = slots.maxOf(AvatarSlot::outputWidth)
        val requiredHeight = slots.maxOf { slot ->
            (slot.outputWidth / slot.aspect).toInt().coerceAtLeast(1)
        }
        var sampleSize = 1
        while (
            info.width / (sampleSize * 2) >= requiredWidth &&
            info.height / (sampleSize * 2) >= requiredHeight
        ) {
            sampleSize *= 2
        }
        val source = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: error("无法解码创作图片")
        require(mediaCacheDirectory.mkdirs() || mediaCacheDirectory.isDirectory) { "无法创建图片裁剪缓存" }
        val staged = linkedMapOf<AvatarSlot, File>()
        try {
            slots.forEach { slot ->
                val cropped = centerCropBitmap(source, slot.aspect, slot.outputWidth)
                try {
                    val png = slot == AvatarSlot.Circle
                    staged[slot] = saveBitmapToCache(
                        dir = mediaCacheDirectory,
                        bitmap = cropped,
                        prefix = "creator-${slot.fileNamePrefix}",
                        format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        quality = if (png) 100 else 94,
                    )
                } finally {
                    if (cropped !== source) cropped.recycle()
                }
            }
            return staged
        } catch (error: Throwable) {
            staged.values.forEach(File::delete)
            throw error
        } finally {
            source.recycle()
        }
    }

    private fun CharacterSlot.toCreatorMediaState(rootId: String): CreatorCharacterMediaState {
        val configured = buildSet {
            if (avatar.isNotBlank()) add(AvatarSlot.Circle)
            if (squareImage.isNotBlank()) add(AvatarSlot.Square)
            if (coverImage.isNotBlank()) add(AvatarSlot.Portrait)
        }
        val revisionSource = buildString {
            append(id)
            listOf(avatar, squareImage, coverImage).forEach { path ->
                append('\u0000').append(path)
                val file = path.takeIf(String::isNotBlank)?.let(::File)
                append('\u0000').append(file?.length() ?: 0L)
                append('\u0000').append(file?.lastModified() ?: 0L)
            }
        }
        // This token describes three small path/size/mtime tuples. Encoding keeps it opaque to
        // the authoring protocol without reading or hashing any media file bytes.
        val revision = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(revisionSource.toByteArray(Charsets.UTF_8))
        return CreatorCharacterMediaState(
            rootId = rootId,
            characterId = id,
            characterName = name,
            revision = revision,
            configuredSlots = configured,
        )
    }

    private companion object {
        const val MaxCreatorMediaAssets = 500
        const val MaxCreatorImagePromptChars = 4_000
        const val MaxCreatorImageNegativePromptChars = 2_000
        const val MaxCreatorMediaAssetBytes = 32L * 1024L * 1024L
        const val MaxCreatorImageDimension = 32_768
        const val MaxCreatorImagePixels = 120_000_000L
        const val CreatorMediaManifestFileName = "media-assets.json"
    }
}

@Serializable
private data class CreatorMediaAssetManifest(
    val assets: List<StoredCreatorMediaAsset> = emptyList(),
)

@Serializable
private data class StoredCreatorMediaAsset(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val source: String,
    val createdAt: String,
) {
    fun toModel() = CreatorMediaAsset(
        id = id,
        displayName = displayName,
        mimeType = mimeType,
        width = width,
        height = height,
        byteSize = byteSize,
        source = CreatorMediaAssetSource.fromStorage(source),
        createdAt = createdAt,
    )
}

private data class CreatorImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val extension: String,
)
