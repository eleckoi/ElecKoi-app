package com.eleckoi.android.app.service.backup

import android.content.Context
import android.net.Uri
import com.eleckoi.android.feature.appfont.data.AppFontRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.data.UserProfileRepository
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.characters.modes.story.presets.data.StoryPresetRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.immersive.project.FrontendProjectRepository
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Owns the one-time, user-visible migration package used when installing a clean app build.
 * Repositories remain responsible for their own current formats; this class only composes them
 * and carries private media files alongside the logical snapshots.
 */
class DataBackupService(
    private val context: Context,
    private val characters: CharacterRepository,
    private val profile: UserProfileRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val variableConfig: VariableConfigRepository,
    private val regexRules: RegexRuleRepository,
    private val storyPresets: StoryPresetRepository,
    private val sessions: ChatSessionStore,
    private val uiPreferences: UiPreferencesRepository,
    private val appFont: AppFontRepository,
    private val modelConfigs: ModelConfigRepository,
    private val frontendProjects: FrontendProjectRepository,
    private val exportToolConfig: () -> String,
    private val restoreToolConfig: (String) -> Unit,
    database: ElecKoiDatabase,
    private val creatorWorkspaces: CreatorWorkspaceRepository,
) {
    private val creatorAssistantBackup = CreatorAssistantBackupStore(database, creatorWorkspaces)
    private val streamingBackup = StreamingDataBackupService(
        context = context,
        characters = characters,
        profile = profile,
        settingLibrary = settingLibrary,
        variableConfig = variableConfig,
        regexRules = regexRules,
        storyPresets = storyPresets,
        sessions = sessions,
        uiPreferences = uiPreferences,
        appFont = appFont,
        modelConfigs = modelConfigs,
        frontendProjects = frontendProjects,
        exportToolConfig = exportToolConfig,
        restoreToolConfig = restoreToolConfig,
        creatorWorkspaces = creatorWorkspaces,
        creatorAssistantBackup = creatorAssistantBackup,
        includedRoots = IncludedRoots,
        maximumEntryCount = MaxEntryCount,
    )

    suspend fun exportTo(
        uri: Uri,
        onProgress: (BackupProgress) -> Unit = {},
    ): BackupResult = streamingBackup.exportTo(uri, onProgress)

    suspend fun importFrom(
        uri: Uri,
        onProgress: (BackupProgress) -> Unit = {},
    ): BackupResult = withContext(Dispatchers.IO) {
        check(characters.loadCharacters().items.isEmpty()) {
            "请在没有角色的干净安装中导入备份"
        }
        when (val archive = streamingBackup.inspect(uri, onProgress)) {
            LegacyBackupArchive -> importLegacyV2(uri, onProgress)
            is StreamingBackupArchive -> streamingBackup.importFrom(uri, archive, onProgress)
        }
    }

    private suspend fun importLegacyV2(
        uri: Uri,
        onProgress: (BackupProgress) -> Unit,
    ): BackupResult = withContext(Dispatchers.IO) {
        onProgress(BackupProgress(
            mode = BackupMode.Import,
            phase = BackupPhase.Restoring,
            current = "正在导入旧备份格式",
            cancellable = false,
        ))
        check(characters.loadCharacters().items.isEmpty()) {
            "请在没有角色的干净安装中导入备份"
        }
        val staging = File(context.cacheDir, "backup-import-${UUID.randomUUID()}")
        staging.mkdirs()
        try {
            unpack(uri, staging)
            val manifest = readJson(File(staging, ManifestEntry))
            require(manifest["format"]?.jsonPrimitive?.content == ArchiveFormat) {
                "不是 ElecKoi 数据备份"
            }
            require(manifest["version"]?.jsonPrimitive?.intOrNull == ArchiveVersion) {
                "不支持的数据备份版本"
            }
            val sectionNames = manifest["sections"]?.jsonArray?.map { it.jsonPrimitive.content }
                ?: error("备份缺少 sections")
            require(sectionNames.size == sectionNames.toSet().size) { "备份包含重复数据" }
            require(RequiredSections.all { it in sectionNames }) { "备份缺少必要数据" }
            val root = context.filesDir.absolutePath
            val sections = sectionNames.associateWith { name ->
                val file = File(staging, name)
                require(file.isFile) { "备份缺少文件：$name" }
                rewriteJsonPaths(file.readText(), root, toMarker = false)
            }
            val payload = sections.getValue("characters.json")
            val profilePayload = sections.getValue("profile.json")
            validateProfile(profilePayload)
            validateCharacters(payload)
            val fileNames = manifest["files"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            require(fileNames.size == fileNames.toSet().size) { "备份包含重复文件" }
            val directoryNames = requireNotNull(manifest["directories"]) { "备份缺少目录清单" }
                .jsonArray
                .map { it.jsonPrimitive.content }
            require(directoryNames.size == directoryNames.toSet().size) { "备份包含重复目录" }
            require(fileNames.size + directoryNames.size <= MaxEntryCount) { "备份文件数量过多" }
            restoreBackupDirectories(context.filesDir, directoryNames)
            restoreFiles(staging, fileNames)

            val restoredProfile = profileFromJson(profilePayload)
            profile.restoreSnapshot(restoredProfile)
            val restoredCharacters = characters.importCharacters(payload)
            val restoredCreatorWorkspaces = creatorWorkspaces.reloadAfterBackupRestore()
            sections["author-frontends.json"]?.let(frontendProjects::restoreBackupJson)
            restoredCharacters.items.forEach { character ->
                val id = safeSegment(character.id)
                settingLibrary.restoreSnapshotJson(
                    character.id,
                    sections.getValue("settings/$id.json"),
                )
                variableConfig.restoreExportJson(
                    character.id,
                    sections.getValue("variables/$id.json"),
                )
                regexRules.restoreBackupJson(
                    character.id,
                    sections.getValue("regex/$id.json"),
                )
            }
            val histories = sections
                .filterKeys { it.startsWith("chats/") && it.endsWith(".json") }
                .mapKeys { (name, _) ->
                    val segment = name.removePrefix("chats/").removeSuffix(".json")
                    restoredCharacters.items.firstOrNull { safeSegment(it.id) == segment }?.id
                        ?: error("聊天记录找不到对应角色")
                }
            val restoredSessions = sessions.restoreBackupHistories(histories)
            storyPresets.restoreBackupJson(sections.getValue("presets.json"))
            sections["shared-regex.json"]?.let(regexRules::restoreSharedBackupJson)
            uiPreferences.restoreSnapshotJson(sections.getValue("preferences.json"))
            appFont.restoreSelectionJson(sections.getValue("app-font.json"))
            modelConfigs.restoreBackupJson(sections.getValue("model-configs.json"))
            sections["tool-config.json"]?.let(restoreToolConfig)
            val restoredCreatorConversations = creatorAssistantBackup.restoreJson(
                sections.getValue("creator-assistant.json"),
                restoredCreatorWorkspaces,
            )
            BackupResult(
                mode = BackupMode.Import,
                characters = restoredCharacters.items.size,
                sessions = restoredSessions,
                files = fileNames.size,
                creatorWorkspaces = restoredCreatorWorkspaces.size,
                creatorConversations = restoredCreatorConversations,
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun exportProfile(root: String): String {
        val value = profile.load()
        return ElecKoiPrettyJson.encodeToString(
            buildJsonObject {
                put("format", "eleckoi.user-profile")
                put("version", 1)
                put("user_name", value.userName)
                put("user_avatar", rewritePath(value.userAvatar, root, true))
                put("user_square", rewritePath(value.userSquare, root, true))
                put("user_portrait", rewritePath(value.userPortrait, root, true))
                put("user_cover", rewritePath(value.userCover, root, true))
            },
        )
    }

    private fun profileFromJson(json: String): UserProfile {
        val root = ElecKoiJson.parseToJsonElement(json).jsonObject
        require(root["format"]?.jsonPrimitive?.content == "eleckoi.user-profile") {
            "用户资料备份格式不正确"
        }
        require(root["version"]?.jsonPrimitive?.intOrNull == 1) { "不支持的用户资料版本" }
        return UserProfile(
            userName = root["user_name"]?.jsonPrimitive?.content.orEmpty(),
            userAvatar = root["user_avatar"]?.jsonPrimitive?.content.orEmpty(),
            userSquare = root["user_square"]?.jsonPrimitive?.content.orEmpty(),
            userPortrait = root["user_portrait"]?.jsonPrimitive?.content.orEmpty(),
            userCover = root["user_cover"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun validateProfile(json: String) {
        profileFromJson(json)
    }

    private fun validateCharacters(json: String) {
        val element = ElecKoiJson.parseToJsonElement(json)
        require(element is JsonObject && element["items"] is JsonArray) {
            "角色备份格式不正确"
        }
    }

    private fun restoreFiles(staging: File, names: List<String>) {
        names.forEach { entryName ->
            require(entryName.startsWith("files/") && entryName.length > "files/".length) {
                "备份文件路径不安全"
            }
            val source = File(staging, entryName)
            require(source.isFile) { "备份缺少文件：$entryName" }
            val target = resolveBackupEntry(context.filesDir, entryName)
            target.parentFile?.mkdirs()
            source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun unpack(uri: Uri, staging: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var total = 0L
                var count = 0
                val seenNames = mutableSetOf<String>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    require(count <= MaxEntryCount) { "备份文件数量过多" }
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val safeName = entry.name.replace('\\', '/')
                    require(seenNames.add(safeName)) { "备份包含重复文件" }
                    require(safeName.isNotBlank() && !safeName.startsWith('/') &&
                        safeName.split('/').none { it.isBlank() || it == "." || it == ".." }) {
                        "备份文件路径不安全"
                    }
                    val target = File(staging, safeName)
                    require(target.canonicalFile.toPath().startsWith(staging.canonicalFile.toPath())) {
                        "备份文件路径越界"
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        total += copyLimited(zip, output, MaxArchiveBytes - total)
                    }
                    zip.closeEntry()
                }
                require(total <= MaxArchiveBytes) { "备份文件过大" }
            }
        } ?: throw IOException("无法读取所选备份")
    }

    private fun copyLimited(input: InputStream, output: OutputStream, remaining: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return copied
            copied += read
            require(copied <= remaining) { "备份文件过大" }
            output.write(buffer, 0, read)
        }
    }

    private fun readJson(file: File): JsonObject =
        ElecKoiJson.parseToJsonElement(file.readText()).jsonObject

    private fun rewriteJsonPaths(json: String, root: String, toMarker: Boolean): String {
        val element = ElecKoiJson.parseToJsonElement(json)
        return ElecKoiPrettyJson.encodeToString(rewriteElement(element, root, toMarker))
    }

    private fun rewriteElement(element: JsonElement, root: String, toMarker: Boolean): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(element.mapValues { (_, value) -> rewriteElement(value, root, toMarker) })
            is JsonArray -> JsonArray(element.map { rewriteElement(it, root, toMarker) })
            is JsonPrimitive -> if (element.isString) {
                JsonPrimitive(rewritePath(element.content, root, toMarker))
            } else {
                element
            }
            JsonNull -> element
        }

    private fun rewritePath(value: String, root: String, toMarker: Boolean): String {
        val normalizedValue = value.replace('\\', '/')
        val normalizedRoot = root.replace('\\', '/').trimEnd('/')
        return if (toMarker) {
            when {
                normalizedValue == normalizedRoot -> FileMarker
                normalizedValue.startsWith("$normalizedRoot/") ->
                    FileMarker + normalizedValue.removePrefix(normalizedRoot)
                else -> value
            }
        } else if (normalizedValue == FileMarker || normalizedValue.startsWith("$FileMarker/")) {
            val suffix = normalizedValue.removePrefix(FileMarker).trimStart('/')
            File(root, suffix.replace('/', File.separatorChar)).absolutePath
        } else {
            value
        }
    }

    private fun safeSegment(value: String): String {
        val result = value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(result.isNotBlank() && result != "." && result != "..") { "数据 ID 无效" }
        return result
    }

    companion object {
        private const val ArchiveFormat = "eleckoi-backup"
        private const val ArchiveVersion = 2
        private const val ManifestEntry = "manifest.json"
        private const val FileMarker = "@files"
        private const val MaxEntryCount = 100_000
        private const val MaxArchiveBytes = 512L * 1024L * 1024L
        private val RequiredSections = setOf(
            "characters.json", "profile.json", "preferences.json", "app-font.json",
            "model-configs.json", "presets.json", "creator-assistant.json",
        )
        private val IncludedRoots = listOf(
            "data/characters",
            "data/user",
            "data/settings",
            "data/story-presets",
            "author_frontends/projects",
            "creator_workspaces/workspaces",
            "creator_workspaces/characters",
            "fonts/imported",
            "fonts/downloaded",
            "chat/input-images",
            "generated/chat-images",
        )
    }
}

data class BackupResult(
    val mode: BackupMode,
    val characters: Int,
    val sessions: Int,
    val files: Int,
    val creatorWorkspaces: Int,
    val creatorConversations: Int,
)

enum class BackupMode { Export, Import }

data class BackupProgress(
    val mode: BackupMode,
    val phase: BackupPhase,
    val completed: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val cancellable: Boolean = true,
) {
    val fraction: Float?
        get() = total.takeIf { it > 0 }?.let { completed.coerceIn(0, it).toFloat() / it }
}

enum class BackupPhase { Preparing, Validating, Transferring, Restoring }
