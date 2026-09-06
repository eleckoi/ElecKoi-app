package com.eleckoi.android.feature.characters.transfer.data

import android.content.Context
import com.eleckoi.android.engine.immersive.project.FrontendProjectRepository
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.characters.model.CharacterCard
import com.eleckoi.android.feature.characters.model.AppDefaultChatBackground
import com.eleckoi.android.feature.characters.model.CustomChatBackground
import com.eleckoi.android.feature.characters.model.GlobalChatBackground
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.modes.story.model.StoryToolSettings
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.withRoleplayPlanEnabled
import com.eleckoi.android.feature.characters.transfer.format.CharacterCardFormatRegistry
import com.eleckoi.android.feature.characters.transfer.format.json.JsonCharacterCardFormat
import com.eleckoi.android.feature.characters.transfer.format.png.PngCharacterCardFormat
import com.eleckoi.android.feature.characters.transfer.format.png.PngTextChunkCodec
import com.eleckoi.android.feature.characters.transfer.format.sillytavern.SillyTavernCharacterCardFormat
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportSource
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportPreview
import com.eleckoi.android.feature.characters.transfer.model.CharacterImportPreviewItem
import com.eleckoi.android.feature.characters.transfer.model.DecodedCharacterCard
import com.eleckoi.android.feature.characters.transfer.model.ExportedCharacterCard
import com.eleckoi.android.feature.characters.transfer.model.PortableAsset
import com.eleckoi.android.feature.characters.transfer.model.PortableCharacter
import com.eleckoi.android.feature.characters.transfer.model.PortableCharacterPackage
import com.eleckoi.android.feature.characters.transfer.model.PortableFrontendProject
import com.eleckoi.android.feature.characters.transfer.model.PortableProjectFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first

class CharacterTransferRepository(
    context: Context,
    private val characters: CharacterRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val variableConfig: VariableConfigRepository,
    private val regexRules: RegexRuleRepository,
    private val frontendProjects: FrontendProjectRepository,
    private val initializeImportedCharacterTools: (characterId: String) -> Unit,
    private val deleteImportedCharacterTools: (Collection<String>) -> Unit,
) {
    private val cacheRoot = File(context.cacheDir, "character_transfer")
    private val formats = CharacterCardFormatRegistry(
        listOf(PngCharacterCardFormat, JsonCharacterCardFormat),
    )
    private val prepared = linkedMapOf<String, PreparedImport>()
    private val lock = Any()

    fun prepareImports(files: List<File>, source: CharacterImportSource): CharacterImportPreview {
        require(files.isNotEmpty()) { "请至少选择一张角色卡" }
        require(files.size <= MaxImportCount) { "一次最多导入 $MaxImportCount 张角色卡" }
        return try {
            val items = files.mapIndexed { index, file ->
                val decoded = runCatching {
                    require(file.isFile && file.length() <= MaxInputBytes) { "角色卡不能超过 64 MB" }
                    val bytes = file.readBytes()
                    val card = when (source) {
                        CharacterImportSource.ElecKoi -> formats.decode(bytes)
                        CharacterImportSource.SillyTavern -> SillyTavernCharacterCardFormat.decode(bytes)
                    }
                    PreparedImportItem(
                        id = "card-${index + 1}",
                        decoded = card,
                        sourceFile = file,
                        isPng = PngTextChunkCodec.isPng(bytes),
                    )
                }
                decoded.getOrElse { error ->
                    PreparedImportItem(
                        id = "card-${index + 1}",
                        decoded = null,
                        sourceFile = file,
                        isPng = runCatching {
                            file.inputStream().use { input ->
                                PngTextChunkCodec.isPng(input.readNBytes(PngSignatureBytes))
                            }
                        }.getOrDefault(false),
                        errorMessage = error.message ?: "无法读取这张角色卡",
                    )
                }
            }
            val token = UUID.randomUUID().toString()
            synchronized(lock) {
                prepared.values.flatMap(PreparedImport::items)
                    .forEach { pending -> deleteImportSource(pending.sourceFile) }
                prepared.clear()
                prepared[token] = PreparedImport(items)
            }
            CharacterImportPreview(
                token = token,
                items = items.mapIndexed { index, item ->
                    val card = item.decoded
                    CharacterImportPreviewItem(
                        id = item.id,
                        name = card?.packageData?.character?.name
                            ?: item.sourceFile.nameWithoutExtension.ifBlank { "第 ${index + 1} 张角色卡" },
                        summary = card?.summary?.ifBlank {
                            if (card.complete) "ElecKoi 完整角色卡" else "标准角色卡"
                        }.orEmpty(),
                        imageFile = item.sourceFile.takeIf { item.isPng },
                        errorMessage = item.errorMessage,
                    )
                },
            )
        } catch (error: Throwable) {
            files.forEach(::deleteImportSource)
            throw error
        }
    }

    suspend fun importPrepared(token: String): List<CharacterSlot> {
        val pending = synchronized(lock) { prepared.remove(token) }
            ?: error("角色卡已失效，请重新选择")
        val failures = mutableListOf<String>()
        return try {
            val result = pending.items.mapNotNull { item ->
                val decoded = item.decoded ?: return@mapNotNull null
                val source = decoded.packageData.character
                var createdId = ""
                runCatching {
                    val created = characters.createCharacter(source.group)
                    createdId = created.id
                    savePortableCharacter(created, source)
                    restoreMedia(created.id, decoded)
                    if (decoded.packageData.settingLibraryJson.isNotBlank()) {
                        settingLibrary.restoreSnapshotJson(created.id, decoded.packageData.settingLibraryJson)
                    }
                    if (decoded.packageData.variableConfigJson.isNotBlank()) {
                        variableConfig.restoreExportJson(created.id, decoded.packageData.variableConfigJson)
                    }
                    decoded.settingLibrary?.let { imported ->
                        // Importing content never grants an executable capability. The author can
                        // opt into the roleplay-plan tool after inspecting the imported card.
                        settingLibrary.save(
                            created.id,
                            imported
                                .copy(characterId = created.id)
                                .withRoleplayPlanEnabled(false),
                        )
                    }
                    decoded.variableConfig?.let { imported ->
                        variableConfig.save(created.id, imported.copy(characterId = created.id))
                    }
                    if (decoded.regexRules.isNotEmpty()) {
                        val current = regexRules.load(created.id)
                        regexRules.save(
                            created.id,
                            current.copy(characterRules = current.characterRules + decoded.regexRules),
                        )
                    }
                    restoreFrontends(created.id, decoded.packageData.frontends)
                    val importedCharacter = characters.characterById(created.id)
                        ?: error("导入角色失败")
                    // The imported content is already committed at this point. Give the new
                    // character the minimum capabilities needed to use its own setting library
                    // and plot variables; every unrelated tool keeps the default-off baseline.
                    initializeImportedCharacterTools(created.id)
                    importedCharacter
                }.getOrElse { error ->
                    if (createdId.isNotBlank()) cleanupImportedCharacter(createdId)
                    failures += "${source.name}：${error.message ?: "导入失败"}"
                    null
                }
            }
            if (result.isEmpty()) error(failures.firstOrNull() ?: "没有可导入的角色卡")
            result.firstOrNull()?.let { characters.selectCharacter(it.id) }
            result
        } finally {
            pending.items.forEach { deleteImportSource(it.sourceFile) }
        }
    }

    fun discardImport(token: String) {
        synchronized(lock) { prepared.remove(token) }?.items?.forEach { deleteImportSource(it.sourceFile) }
    }

    suspend fun exportCharacter(characterId: String): ExportedCharacterCard {
        val slot = characters.characterById(characterId) ?: error("角色不存在")
        val value = portablePackage(slot)
        val imageSource = listOf(
            slot.persona.assistantCover,
            slot.coverImage,
            slot.persona.assistantSquare,
            slot.squareImage,
            slot.persona.assistantAvatar,
            slot.avatar,
        ).firstNotNullOfOrNull { path -> File(path).takeIf { path.isNotBlank() && it.isFile } }
        val image = CharacterCardMedia.cardPng(imageSource, slot.name)
        val encoded = PngCharacterCardFormat.encode(image, value)
        val directory = File(
            cacheRoot,
            "exports/${System.currentTimeMillis()}-${UUID.randomUUID()}",
        ).apply { mkdirs() }
        val safeName = slot.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "角色" }
        // Keep cache uniqueness in the directory. Share targets display the leaf file name, so
        // exposing the timestamp here makes a character card look like it has a product suffix.
        val output = File(directory, "$safeName.png")
        output.writeBytes(encoded)
        return ExportedCharacterCard(slot.id, slot.name, output)
    }

    suspend fun exportCharacters(characterIds: List<String>): List<ExportedCharacterCard> {
        val distinctIds = characterIds.distinct()
        require(distinctIds.isNotEmpty()) { "请先选择角色" }
        return distinctIds.map { characterId -> exportCharacter(characterId) }
    }

    private fun savePortableCharacter(created: CharacterSlot, value: PortableCharacter) {
        val current = characters.loadCharacters()
        val persona = CharacterCard(
            characterId = created.id,
            characterName = value.name,
            assistantName = value.name,
            assistantPrompt = value.assistantPrompt,
            profileAge = value.profileAge,
            profileSex = value.profileSex,
            profileHeight = value.profileHeight,
            profileBirthday = value.profileBirthday,
            profileLike = value.profileLike,
            imagePrompt = value.imagePrompt,
            opening = value.opening,
            showOpening = value.showOpening,
            chatBackground = when (value.chatBackgroundMode) {
                "app_default" -> AppDefaultChatBackground
                "custom" -> CustomChatBackground
                "global" -> GlobalChatBackground
                else -> ""
            },
            chatBackgroundOpacity = value.chatBackgroundOpacity,
            chatBackgroundBlur = value.chatBackgroundBlur,
            chatBackgroundScrim = value.chatBackgroundScrim,
            userName = created.persona.userName,
            userAvatar = created.persona.userAvatar,
            userSquare = created.persona.userSquare,
            userPortrait = created.persona.userPortrait,
        )
        val updated = created.copy(
            name = value.name,
            group = value.group,
            characterMode = value.characterMode,
            storyTools = StoryToolSettings(frontendBeautyEnabled = value.frontendBeautyEnabled),
            persona = persona,
        )
        characters.saveCharacters(
            current.copy(
                activeCharacterId = created.id,
                groups = (current.groups + value.group.takeIf(String::isNotBlank).orEmpty())
                    .filter(String::isNotBlank)
                    .distinct(),
                items = current.items.map { if (it.id == created.id) updated else it },
            ),
        )
    }

    private fun restoreMedia(characterId: String, decoded: DecodedCharacterCard) {
        val assets = decoded.packageData.assets.associateBy(PortableAsset::key)
        val working = File(cacheRoot, "restore/$characterId").apply { mkdirs() }
        try {
            val explicitAvatars = buildMap {
                assets[AssetCircle]?.let { put(AvatarSlot.Circle, writeTemp(working, "circle.png", it.bytes)) }
                assets[AssetSquare]?.let { put(AvatarSlot.Square, writeTemp(working, "square.jpg", it.bytes)) }
                assets[AssetPortrait]?.let { put(AvatarSlot.Portrait, writeTemp(working, "portrait.jpg", it.bytes)) }
            }
            val avatarFiles = if (explicitAvatars.isNotEmpty()) {
                explicitAvatars
            } else {
                decoded.sourceImage?.let { CharacterCardMedia.avatarFiles(File(working, "avatars"), it) }.orEmpty()
            }
            if (avatarFiles.isNotEmpty()) characters.saveCharacterAvatars(characterId, avatarFiles)
        } finally {
            working.deleteRecursively()
        }
    }

    private suspend fun portablePackage(slot: CharacterSlot): PortableCharacterPackage {
        val persona = slot.persona
        // Chat wallpapers are local user presentation data, not character-card content. In
        // particular, a custom wallpaper may be a private photo. Keep the exported card neutral
        // so it can never package that file (or the user's per-character presentation settings).
        val assets = listOfNotNull(
            asset(AssetCircle, persona.assistantAvatar.ifBlank { slot.avatar }),
            asset(AssetSquare, persona.assistantSquare.ifBlank { slot.squareImage }),
            asset(AssetPortrait, persona.assistantCover.ifBlank { slot.coverImage }),
        )
        require(assets.sumOf { it.bytes.size.toLong() } <= MaxAttachmentBytes) {
            "角色图片总大小不能超过 48 MB"
        }
        return PortableCharacterPackage(
            character = PortableCharacter(
                name = slot.name,
                group = slot.group,
                characterMode = slot.characterMode,
                frontendBeautyEnabled = slot.storyTools.frontendBeautyEnabled,
                assistantPrompt = persona.assistantPrompt,
                profileAge = persona.profileAge,
                profileSex = persona.profileSex,
                profileHeight = persona.profileHeight,
                profileBirthday = persona.profileBirthday,
                profileLike = persona.profileLike,
                imagePrompt = persona.imagePrompt,
                opening = persona.opening,
                showOpening = persona.showOpening,
                chatBackgroundMode = "card",
                chatBackgroundOpacity = 0.72f,
                chatBackgroundBlur = 0f,
                chatBackgroundScrim = 0.22f,
            ),
            assets = assets,
            settingLibraryJson = settingLibrary.exportSnapshotJson(slot.id),
            variableConfigJson = variableConfig.exportJson(slot.id),
            frontends = captureFrontends(slot.id),
        )
    }

    private suspend fun captureFrontends(characterId: String): List<PortableFrontendProject> {
        val workspace = frontendProjects.workspaceFlow(characterId).first()
        var total = 0L
        return workspace.projects.map { project ->
            val root = frontendProjects.projectDirectory(project.id) ?: error("前端项目文件不存在")
            val rootPath = root.canonicalFile.toPath()
            val files = project.files.map { relative ->
                val file = File(root, relative).canonicalFile
                require(file.toPath().startsWith(rootPath) && file.isFile) { "前端项目文件路径无效" }
                val bytes = file.readBytes()
                total += bytes.size
                require(total <= MaxAttachmentBytes) { "角色附件总大小不能超过 48 MB" }
                PortableProjectFile(relative.replace('\\', '/'), bytes)
            }
            PortableFrontendProject(
                name = project.name,
                entryFile = project.entryFile,
                selected = workspace.selectedProjectId == project.id,
                files = files,
            )
        }
    }

    private suspend fun restoreFrontends(characterId: String, projects: List<PortableFrontendProject>) {
        projects.forEachIndexed { index, project ->
            val root = File(cacheRoot, "frontend/$characterId/$index").apply { mkdirs() }.canonicalFile
            try {
                project.files.forEach { item ->
                    val output = File(root, item.path).canonicalFile
                    require(output.toPath().startsWith(root.toPath())) { "角色卡包含不安全的文件路径" }
                    output.parentFile?.mkdirs()
                    output.writeBytes(item.bytes)
                }
                frontendProjects.publishProject(
                    characterId = characterId,
                    sourceDirectory = root,
                    name = project.name,
                    entryFile = project.entryFile,
                    select = project.selected,
                )
            } finally {
                root.deleteRecursively()
            }
        }
    }

    private fun asset(key: String, path: String): PortableAsset? {
        val file = File(path)
        if (path.isBlank() || !file.isFile) return null
        return PortableAsset(key, mediaType(file), file.readBytes())
    }

    private fun writeTemp(root: File, name: String, bytes: ByteArray): File = File(root, name).also {
        it.parentFile?.mkdirs()
        it.writeBytes(bytes)
    }

    private fun deleteImportSource(file: File) {
        val importRoot = File(cacheRoot, "imports").canonicalFile.toPath()
        val target = file.canonicalFile
        if (target.toPath().startsWith(importRoot)) target.delete()
    }

    private suspend fun cleanupImportedCharacter(characterId: String) {
        val ids = listOf(characterId)
        settingLibrary.deleteForCharacters(ids)
        variableConfig.deleteForCharacters(ids)
        regexRules.deleteForCharacters(ids)
        frontendProjects.deleteForCharacters(ids)
        deleteImportedCharacterTools(ids)
        characters.deleteCharacters(ids)
    }

    private fun mediaType(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private data class PreparedImport(
        val items: List<PreparedImportItem>,
    )

    private data class PreparedImportItem(
        val id: String,
        val decoded: DecodedCharacterCard?,
        val sourceFile: File,
        val isPng: Boolean,
        val errorMessage: String = "",
    )

    private companion object {
        const val MaxInputBytes = 64L * 1024 * 1024
        const val PngSignatureBytes = 8
        const val MaxImportCount = 50
        const val MaxAttachmentBytes = 48L * 1024 * 1024
        const val AssetCircle = "avatar.circle"
        const val AssetSquare = "avatar.square"
        const val AssetPortrait = "avatar.portrait"
    }
}
