package com.eleckoi.android.app.service.backup

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.eleckoi.android.engine.generation.config.ModelConfigRepository
import com.eleckoi.android.engine.immersive.project.FrontendProjectRepository
import com.eleckoi.android.engine.story.variables.config.VariableConfigRepository
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.storage.CreatorWorkspaceRepository
import com.eleckoi.android.feature.appfont.data.AppFontRepository
import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.data.UserProfileRepository
import com.eleckoi.android.feature.characters.model.UserProfile
import com.eleckoi.android.feature.characters.presets.data.AgentPresetRepository
import com.eleckoi.android.feature.characters.modes.story.regex.data.RegexRuleRepository
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.SettingLibraryRepository
import com.eleckoi.android.feature.chat.data.ChatSessionStore
import com.eleckoi.android.feature.preferences.UiPreferencesRepository
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.serialization.ElecKoiPrettyJson
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Version 3 backup: bounded logical chunks plus app-owned files streamed through the archive. */
internal class StreamingDataBackupService(
    private val context: Context,
    private val characters: CharacterRepository,
    private val profile: UserProfileRepository,
    private val settingLibrary: SettingLibraryRepository,
    private val variableConfig: VariableConfigRepository,
    private val regexRules: RegexRuleRepository,
    private val agentPresets: AgentPresetRepository,
    private val sessions: ChatSessionStore,
    private val uiPreferences: UiPreferencesRepository,
    private val appFont: AppFontRepository,
    private val modelConfigs: ModelConfigRepository,
    private val frontendProjects: FrontendProjectRepository,
    private val creatorWorkspaces: CreatorWorkspaceRepository,
    private val creatorAssistantBackup: CreatorAssistantBackupStore,
    private val includedRoots: List<String>,
    private val maximumEntryCount: Int,
) {
    suspend fun exportTo(
        uri: Uri,
        onProgress: (BackupProgress) -> Unit,
    ): BackupResult = withContext(Dispatchers.IO) {
        onProgress(BackupProgress(BackupMode.Export, BackupPhase.Preparing, current = "正在整理备份内容"))
        val root = context.filesDir.absolutePath
        val characterItems = characters.loadCharacters().items
        requireSafeUniqueSegments(characterItems.map { it.id }, "角色")
        val historyTargets = sessions.backupHistoryTargets()
        val creatorTargets = creatorAssistantBackup.targets()
        val creatorWorkspaceCount = creatorWorkspaces.list().size
        val archiveTree = collectBackupArchiveTree(context.filesDir, includedRoots)
        val totalEntries = archiveTree.files.size + BaseSections.size +
            characterItems.size * CharacterSectionCount + historyTargets.size +
            creatorTargets.size + 1
        require(totalEntries + archiveTree.directories.size <= maximumEntryCount) { "备份文件数量过多" }

        val expectedNames = buildList {
            addAll(archiveTree.files.map(BackupArchiveFile::entryName))
            addAll(BaseSections)
            characterItems.forEach { character ->
                val id = safeSegment(character.id)
                add("settings/$id.json")
                add("variables/$id.json")
                add("regex/$id.json")
            }
            historyTargets.forEach { target ->
                add("chats/${safeSegment(target.characterId)}/${safeSegment(target.sessionId)}.json")
            }
            creatorTargets.forEach { target ->
                add("creator-chats/${safeSegment(target.workspaceId)}/${safeSegment(target.conversationId)}.json")
            }
        }
        require(expectedNames.size == expectedNames.toSet().size) { "数据 ID 无法安全打包" }

        var completed = 0
        fun report(name: String) {
            completed++
            onProgress(BackupProgress(
                mode = BackupMode.Export,
                phase = BackupPhase.Transferring,
                completed = completed,
                total = totalEntries,
                current = name,
            ))
        }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            StreamingBackupArchiveWriter(output).use { writer ->
                // Large files come first so import cancellation remains safe until every file has
                // reached a private staging root.
                archiveTree.files.forEach { entry ->
                    writer.writeFile(entry.entryName, entry.file)
                    report(entry.entryName)
                }
                suspend fun section(name: String, value: String, ownerId: String = "") {
                    writer.writeText(name, value, BackupEntryKindSection, ownerId)
                    report(name)
                }
                section("characters.json", rewriteJsonPaths(characters.exportCharacters(), root, true))
                section("profile.json", exportProfile(root))
                section("preferences.json", uiPreferences.exportSnapshotJson())
                section("app-font.json", appFont.exportSelectionJson())
                section("model-configs.json", modelConfigs.exportBackupJson())
                section("presets.json", rewriteJsonPaths(agentPresets.exportBackupJson(), root, true))
                section("shared-regex.json", regexRules.exportSharedBackupJson())
                section("author-frontends.json", frontendProjects.exportBackupJson())
                characterItems.forEach { character ->
                    val id = safeSegment(character.id)
                    section("settings/$id.json", rewriteJsonPaths(
                        settingLibrary.exportSnapshotJson(character.id), root, true,
                    ), character.id)
                    section("variables/$id.json", rewriteJsonPaths(
                        variableConfig.exportJson(character.id), root, true,
                    ), character.id)
                    section("regex/$id.json", rewriteJsonPaths(
                        regexRules.exportBackupJson(character.id), root, true,
                    ), character.id)
                }
                historyTargets.forEach { target ->
                    val name = "chats/${safeSegment(target.characterId)}/${safeSegment(target.sessionId)}.json"
                    writer.writeText(
                        name = name,
                        value = rewriteJsonPaths(sessions.exportBackupHistory(target), root, true),
                        kind = BackupEntryKindChat,
                        ownerId = target.characterId,
                    )
                    report(name)
                }
                creatorTargets.forEach { target ->
                    val name = "creator-chats/${safeSegment(target.workspaceId)}/${safeSegment(target.conversationId)}.json"
                    writer.writeText(
                        name = name,
                        value = rewriteJsonPaths(
                            creatorAssistantBackup.exportConversationJson(target), root, true,
                        ),
                        kind = BackupEntryKindCreatorChat,
                        ownerId = target.workspaceId,
                    )
                    report(name)
                }
                writer.finish(
                    directories = archiveTree.directories,
                    excluded = ExcludedSecrets,
                    characterCount = characterItems.size,
                    sessionCount = historyTargets.size,
                    creatorWorkspaceCount = creatorWorkspaceCount,
                    creatorConversationCount = creatorTargets.size,
                )
                report(BackupManifestEntry)
            }
        } ?: throw IOException("无法打开备份保存位置")
        BackupResult(
            mode = BackupMode.Export,
            characters = characterItems.size,
            sessions = historyTargets.size,
            files = archiveTree.files.size,
            creatorWorkspaces = creatorWorkspaceCount,
            creatorConversations = creatorTargets.size,
        )
    }

    suspend fun inspect(
        uri: Uri,
        onProgress: (BackupProgress) -> Unit,
    ): BackupArchiveInspection = withContext(Dispatchers.IO) {
        onProgress(BackupProgress(BackupMode.Import, BackupPhase.Validating, current = "正在检查备份完整性"))
        context.contentResolver.openInputStream(uri)?.use { input ->
            inspectBackupArchive(
                input = input,
                maximumPayloadBytes = maximumImportPayloadBytes(),
                maximumEntryCount = maximumEntryCount,
                onEntryRead = { completed ->
                    onProgress(BackupProgress(
                        mode = BackupMode.Import,
                        phase = BackupPhase.Validating,
                        completed = completed,
                        current = "正在校验第 $completed 项",
                    ))
                },
            )
        } ?: throw IOException("无法读取所选备份")
    }

    suspend fun importFrom(
        uri: Uri,
        archive: StreamingBackupArchive,
        onProgress: (BackupProgress) -> Unit,
    ): BackupResult = withContext(Dispatchers.IO) {
        val manifest = archive.manifest
        val names = manifest.entries.map(StreamingBackupEntry::name)
        require(BaseSections.all { it in names }) { "备份缺少必要数据" }
        val firstStructuredEntry = manifest.entries.indexOfFirst { it.kind != BackupEntryKindFile }
            .let { if (it < 0) manifest.entries.size else it }
        require(manifest.entries.drop(firstStructuredEntry).none { it.kind == BackupEntryKindFile }) {
            "备份文件分块顺序不正确"
        }
        val ownedFileNames = manifest.directories + manifest.entries
            .filter { it.kind == BackupEntryKindFile }
            .map(StreamingBackupEntry::name)
        require(ownedFileNames.all(::isIncludedFileEntry)) { "备份包含未知文件目录" }
        require(manifest.entries.count { it.kind == BackupEntryKindChat } == manifest.sessionCount) {
            "聊天分块数量与备份清单不一致"
        }
        require(manifest.entries.count { it.kind == BackupEntryKindCreatorChat } ==
            manifest.creatorConversationCount) { "创作助手分块数量与备份清单不一致" }

        val fileSwap = BackupFileRootSwap(context.filesDir, includedRoots)
        restoreBackupDirectories(fileSwap.stagingRoot, manifest.directories)
        val presentFiles = manifest.directories + manifest.entries
            .filter { it.kind == BackupEntryKindFile }
            .map(StreamingBackupEntry::name)
        var filesCommitted = false
        var restoredCharacters = characters.loadCharacters()
        var restoredWorkspaces = emptyList<CreatorWorkspace>()
        var restoredSessions = 0
        var restoredCreatorConversations = 0
        var restoredFiles = 0
        val restoredCharacterSections = mutableSetOf<String>()
        fun restoredCharacterIds(): Set<String> =
            restoredCharacters.items.mapTo(mutableSetOf()) { it.id }
        val root = context.filesDir.absolutePath
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readStreamingBackupEntries(
                    input = input,
                    manifest = manifest,
                    onEntryRead = { completed, total ->
                        onProgress(BackupProgress(
                            mode = BackupMode.Import,
                            phase = if (filesCommitted) BackupPhase.Restoring else BackupPhase.Transferring,
                            completed = completed,
                            total = total,
                            current = if (filesCommitted) "正在重建数据库" else "正在暂存文件",
                            cancellable = !filesCommitted,
                        ))
                    },
                    consume = consume@ { entry, source ->
                        if (entry.kind == BackupEntryKindFile) {
                            val target = resolveBackupEntry(fileSwap.stagingRoot, entry.name)
                            target.parentFile?.mkdirs()
                            target.outputStream().buffered().use { output ->
                                copyBackupStream(source, output)
                            }
                            restoredFiles++
                            return@consume
                        }
                        if (!filesCommitted) {
                            onProgress(BackupProgress(
                                mode = BackupMode.Import,
                                phase = BackupPhase.Restoring,
                                current = "正在提交文件并重建数据库",
                                cancellable = false,
                            ))
                            fileSwap.commit(presentFiles)
                            filesCommitted = true
                        }
                        require(entry.bytes <= MaximumStructuredEntryBytes) {
                            "单个结构化数据分块过大：${entry.name}"
                        }
                        val payload = rewriteJsonPaths(readBackupText(source, entry.bytes), root, false)
                        when (entry.kind) {
                            BackupEntryKindSection -> when (entry.name) {
                                "characters.json" -> {
                                    validateCharacters(payload)
                                    restoredCharacters = characters.importCharacters(payload)
                                    restoredWorkspaces = creatorWorkspaces.reloadAfterBackupRestore()
                                }
                                "profile.json" -> profile.restoreSnapshot(profileFromJson(payload))
                                "preferences.json" -> uiPreferences.restoreSnapshotJson(payload)
                                "app-font.json" -> appFont.restoreSelectionJson(payload)
                                "model-configs.json" -> modelConfigs.restoreBackupJson(payload)
                                "presets.json" -> agentPresets.restoreBackupJson(payload)
                                "shared-regex.json" -> regexRules.restoreSharedBackupJson(payload)
                                "author-frontends.json" -> frontendProjects.restoreBackupJson(payload)
                                else -> {
                                    restoreCharacterSection(entry, payload, restoredCharacterIds())
                                    restoredCharacterSections += entry.name
                                }
                            }
                            BackupEntryKindChat -> {
                                require(entry.ownerId in restoredCharacterIds()) {
                                    "聊天记录找不到对应角色"
                                }
                                require(entry.name.startsWith("chats/${safeSegment(entry.ownerId)}/")) {
                                    "聊天分块归属不正确"
                                }
                                val imported = sessions.restoreBackupHistory(entry.ownerId, payload)
                                require(imported == 1) { "单个聊天分块必须只包含一场对话" }
                                restoredSessions += imported
                            }
                            BackupEntryKindCreatorChat -> {
                                restoredCreatorConversations +=
                                    creatorAssistantBackup.restoreConversationJson(payload, restoredWorkspaces)
                            }
                            else -> error("未知备份数据类型：${entry.kind}")
                        }
                    },
                )
            } ?: throw IOException("无法读取所选备份")
            if (!filesCommitted) {
                fileSwap.commit(presentFiles)
            }
            val expectedCharacterSections = restoredCharacterIds().flatMap { id ->
                val safe = safeSegment(id)
                listOf("settings/$safe.json", "variables/$safe.json", "regex/$safe.json")
            }.toSet()
            require(restoredCharacterSections == expectedCharacterSections) { "角色数据分块不完整" }
            require(restoredCharacters.items.size == manifest.characterCount) { "角色数量与备份清单不一致" }
            require(restoredSessions == manifest.sessionCount) { "聊天数量与备份清单不一致" }
            require(restoredFiles == manifest.entries.count { it.kind == BackupEntryKindFile }) {
                "文件数量与备份清单不一致"
            }
            require(restoredWorkspaces.size == manifest.creatorWorkspaceCount) { "工作区数量与备份清单不一致" }
            require(restoredCreatorConversations == manifest.creatorConversationCount) {
                "创作助手对话数量与备份清单不一致"
            }
            fileSwap.finish()
            BackupResult(
                mode = BackupMode.Import,
                characters = restoredCharacters.items.size,
                sessions = restoredSessions,
                files = restoredFiles,
                creatorWorkspaces = restoredWorkspaces.size,
                creatorConversations = restoredCreatorConversations,
            )
        } catch (error: Throwable) {
            fileSwap.rollback()
            throw error
        }
    }

    private suspend fun restoreCharacterSection(
        entry: StreamingBackupEntry,
        payload: String,
        characterIds: Set<String>,
    ) {
        require(entry.ownerId in characterIds) { "角色数据找不到对应角色" }
        val id = safeSegment(entry.ownerId)
        when (entry.name) {
            "settings/$id.json" -> settingLibrary.restoreSnapshotJson(entry.ownerId, payload)
            "variables/$id.json" -> variableConfig.restoreExportJson(entry.ownerId, payload)
            "regex/$id.json" -> regexRules.restoreBackupJson(entry.ownerId, payload)
            else -> error("未知备份数据：${entry.name}")
        }
    }

    private fun maximumImportPayloadBytes(): Long {
        val available = StatFs(context.filesDir.absolutePath).availableBytes
        val reserve = minOf(MaximumFreeSpaceReserveBytes, maxOf(MinimumFreeSpaceReserveBytes, available / 20))
        return (available - reserve).coerceAtLeast(0L)
    }

    private fun exportProfile(root: String): String {
        val value = profile.load()
        return ElecKoiPrettyJson.encodeToString(buildJsonObject {
            put("format", "eleckoi.user-profile")
            put("version", 1)
            put("user_name", value.userName)
            put("user_avatar", rewritePath(value.userAvatar, root, true))
            put("user_square", rewritePath(value.userSquare, root, true))
            put("user_portrait", rewritePath(value.userPortrait, root, true))
            put("user_cover", rewritePath(value.userCover, root, true))
        })
    }

    private fun profileFromJson(json: String): UserProfile {
        val value = ElecKoiJson.parseToJsonElement(json).jsonObject
        require(value["format"]?.jsonPrimitive?.content == "eleckoi.user-profile") {
            "用户资料备份格式不正确"
        }
        require(value["version"]?.jsonPrimitive?.intOrNull == 1) { "不支持的用户资料版本" }
        return UserProfile(
            userName = value["user_name"]?.jsonPrimitive?.content.orEmpty(),
            userAvatar = value["user_avatar"]?.jsonPrimitive?.content.orEmpty(),
            userSquare = value["user_square"]?.jsonPrimitive?.content.orEmpty(),
            userPortrait = value["user_portrait"]?.jsonPrimitive?.content.orEmpty(),
            userCover = value["user_cover"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun validateCharacters(json: String) {
        val element = ElecKoiJson.parseToJsonElement(json)
        require(element is JsonObject && element["items"] is JsonArray) { "角色备份格式不正确" }
    }

    private fun rewriteJsonPaths(json: String, root: String, toMarker: Boolean): String =
        ElecKoiPrettyJson.encodeToString(rewriteElement(ElecKoiJson.parseToJsonElement(json), root, toMarker))

    private fun rewriteElement(element: JsonElement, root: String, toMarker: Boolean): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(element.mapValues { (_, value) -> rewriteElement(value, root, toMarker) })
            is JsonArray -> JsonArray(element.map { rewriteElement(it, root, toMarker) })
            is JsonPrimitive -> if (element.isString) JsonPrimitive(rewritePath(element.content, root, toMarker)) else element
            JsonNull -> element
        }

    private fun rewritePath(value: String, root: String, toMarker: Boolean): String {
        val normalizedValue = value.replace('\\', '/')
        val normalizedRoot = root.replace('\\', '/').trimEnd('/')
        return if (toMarker) {
            when {
                normalizedValue == normalizedRoot -> FileMarker
                normalizedValue.startsWith("$normalizedRoot/") -> FileMarker + normalizedValue.removePrefix(normalizedRoot)
                else -> value
            }
        } else if (normalizedValue == FileMarker || normalizedValue.startsWith("$FileMarker/")) {
            val suffix = normalizedValue.removePrefix(FileMarker).trimStart('/')
            File(root, suffix.replace('/', File.separatorChar)).absolutePath
        } else {
            value
        }
    }

    private fun requireSafeUniqueSegments(values: List<String>, label: String) {
        val segments = values.map(::safeSegment)
        require(segments.size == segments.toSet().size) { "$label ID 无法安全打包" }
    }

    private fun isIncludedFileEntry(entryName: String): Boolean = includedRoots.any { relative ->
        val root = "files/$relative"
        entryName == root || entryName.startsWith("$root/")
    }

    private fun safeSegment(value: String): String {
        val result = value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(result.isNotBlank() && result != "." && result != "..") { "数据 ID 无效" }
        return result
    }

    private companion object {
        const val CharacterSectionCount = 3
        const val FileMarker = "@files"
        const val MaximumStructuredEntryBytes = 256L * 1024L * 1024L
        const val MinimumFreeSpaceReserveBytes = 32L * 1024L * 1024L
        const val MaximumFreeSpaceReserveBytes = 256L * 1024L * 1024L
        val BaseSections = listOf(
            "characters.json",
            "profile.json",
            "preferences.json",
            "app-font.json",
            "model-configs.json",
            "presets.json",
            "shared-regex.json",
            "author-frontends.json",
        )
        val ExcludedSecrets = listOf(
            "model_credentials",
            "web_search_api_key",
            "remote_dsh_credentials",
        )
    }
}
