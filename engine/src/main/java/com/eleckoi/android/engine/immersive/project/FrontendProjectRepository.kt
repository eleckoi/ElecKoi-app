package com.eleckoi.android.engine.immersive.project

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.engine.immersive.model.FrontendWorkspace
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.storage.ImmediateCleanupRunner
import com.eleckoi.android.foundation.storage.PersistentCleanupRunner
import com.eleckoi.android.foundation.storage.deleteOwnedDirectory
import com.eleckoi.android.foundation.storage.room.AuthorFrontendDao
import com.eleckoi.android.foundation.storage.room.CharacterFrontendSettingsEntity
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.FrontendProjectEntity
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Room owns frontend registration; project bytes remain in the app-managed project directory. */
class FrontendProjectRepository(
    private val context: Context,
    private val database: ElecKoiDatabase,
    private val cleanupRunner: PersistentCleanupRunner = ImmediateCleanupRunner,
) {
    private val dao: AuthorFrontendDao = database.authorFrontendDao()
    private val root = File(context.filesDir, "author_frontends")
    private val projectsRoot = File(root, "projects")
    private val deletingRoot = File(root, ".deleting")

    fun workspaceFlow(characterId: String): Flow<FrontendWorkspace> = combine(
        dao.observeProjects(characterId),
        dao.observeSettings(characterId),
    ) { projectRows, settings ->
        val projects = projectRows.map(::fromEntity)
        FrontendWorkspace(
            characterId = characterId,
            projects = projects,
            selectedProjectId = settings?.selectedProjectId
                ?.takeIf { id -> projects.any { it.id == id } },
            messageRendererEnabled = settings?.messageRendererEnabled ?: true,
        )
    }

    suspend fun importProject(characterId: String, uri: Uri): FrontendProject = withContext(Dispatchers.IO) {
        require(characterId.isNotBlank()) { "没有可关联的角色" }
        val originalName = displayName(uri).ifBlank { "frontend.html" }
        val id = UUID.randomUUID().toString()
        val staging = File(root, ".staging/$id")
        val source = File(staging, "source")
        val unpacked = File(staging, "project")
        val destination = File(projectsRoot, id)
        try {
            staging.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        copied += read
                        require(copied <= FrontendProjectImporter.MaxExpandedBytes) {
                            "导入文件不能超过 ${FrontendProjectImporter.MaxExpandedBytes / Megabyte} MB"
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("无法读取所选文件")
            val imported = FrontendProjectImporter.import(source, originalName, unpacked)
            destination.parentFile?.mkdirs()
            require(unpacked.renameTo(destination)) { "无法保存前端项目" }
            val project = FrontendProject(
                id = id,
                characterId = characterId,
                name = originalName.substringBeforeLast('.').ifBlank { "沉浸前端" },
                entryFile = imported.entryFile,
                files = imported.files,
                importedAt = Instant.now().toString(),
            )
            persistProjectAndSelection(project, select = true)
            project
        } catch (error: Throwable) {
            destination.deleteRecursively()
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun publishProject(
        characterId: String,
        sourceDirectory: File,
        name: String,
        entryFile: String = "index.html",
        select: Boolean = true,
    ): FrontendProject = withContext(Dispatchers.IO) {
        require(characterId.isNotBlank()) { "没有可关联的角色" }
        val id = UUID.randomUUID().toString()
        val staging = File(root, ".staging/$id/project")
        val destination = File(projectsRoot, id)
        try {
            val imported = FrontendProjectImporter.importDirectory(
                source = sourceDirectory,
                destination = staging,
                requestedEntryFile = entryFile,
            )
            destination.parentFile?.mkdirs()
            require(staging.renameTo(destination)) { "无法保存前端项目" }
            val project = FrontendProject(
                id = id,
                characterId = characterId,
                name = name.trim().take(80).ifBlank { "AI 创作前端" },
                entryFile = imported.entryFile,
                files = imported.files,
                importedAt = Instant.now().toString(),
            )
            persistProjectAndSelection(project, select)
            project
        } catch (error: Throwable) {
            destination.deleteRecursively()
            throw error
        } finally {
            File(root, ".staging/$id").deleteRecursively()
        }
    }

    suspend fun saveHtmlProject(
        characterId: String,
        projectId: String?,
        name: String,
        html: String,
        select: Boolean = true,
    ): FrontendProject = withContext(Dispatchers.IO) {
        require(characterId.isNotBlank()) { "没有可关联的角色" }
        val normalizedName = name.trim().take(80).ifBlank { "自定义 HTML 主题" }
        if (projectId == null) {
            createHtmlProject(characterId, normalizedName, html, select)
        } else {
            updateHtmlProject(characterId, projectId, normalizedName, html, select)
        }
    }

    suspend fun readProjectEntry(characterId: String, projectId: String): String =
        withContext(Dispatchers.IO) {
            require(characterId.isNotBlank() && SafeProjectId.matches(projectId)) {
                "前端项目编号无效"
            }
            val project = dao.project(projectId)?.takeIf { it.characterId == characterId }
                ?: error("前端项目不存在")
            FrontendHtmlProjectFiles.readEntry(
                projectDirectoryFile(project.id),
                project.entryFile,
            )
        }

    suspend fun selectProject(characterId: String, projectId: String?) = withContext(Dispatchers.IO) {
        if (projectId != null) {
            require(dao.project(projectId)?.characterId == characterId) { "前端项目不存在" }
        }
        val current = currentSettings(characterId)
        val updated = current.copy(selectedProjectId = projectId)
        if (updated != current) dao.upsertSettings(updated)
    }

    suspend fun setMessageRendererEnabled(characterId: String, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            require(characterId.isNotBlank()) { "没有可关联的角色" }
            val current = currentSettings(characterId)
            val updated = current.copy(messageRendererEnabled = enabled)
            if (updated != current) dao.upsertSettings(updated)
        }

    suspend fun deleteProject(characterId: String, projectId: String) = cleanupRunner.run(
        FrontendProjectCleanupKind,
        cleanupTarget(characterId, projectId),
    ) {
        deleteProjectNow(characterId, projectId)
    }

    suspend fun resumeProjectDeletion(targetId: String) {
        val parts = targetId.split(CleanupTargetSeparator, limit = 2)
        require(parts.size == 2) { "前端项目清理目标无效" }
        deleteProjectNow(parts[0], parts[1])
    }

    private suspend fun deleteProjectNow(characterId: String, projectId: String) = withContext(Dispatchers.IO) {
        require(characterId.isNotBlank() && SafeProjectId.matches(projectId)) { "前端项目编号无效" }
        val project = dao.project(projectId)?.takeIf { it.characterId == characterId }
        val discarded = File(deletingRoot, projectId)
        deletingRoot.mkdirs()
        if (project == null) {
            deleteOwnedDirectory(deletingRoot, discarded)
            return@withContext
        }
        val source = projectDirectoryFile(project.id)
        deleteOwnedDirectory(deletingRoot, discarded)
        if (source.exists()) require(source.renameTo(discarded)) { "无法暂存待删除的前端项目" }
        try {
            dao.deleteProject(characterId, projectId)
            deleteOwnedDirectory(deletingRoot, discarded)
        } catch (error: Throwable) {
            if (!source.exists() && discarded.exists()) discarded.renameTo(source)
            throw error
        }
    }

    fun projectDirectory(projectId: String): File? {
        if (!SafeProjectId.matches(projectId)) return null
        return projectDirectoryFile(projectId).takeIf(File::isDirectory)
    }

    suspend fun deleteForCharacters(characterIds: List<String>) = withContext(Dispatchers.IO) {
        val ids = characterIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return@withContext
        val removing = dao.allProjects().filter { it.characterId in ids }
        removing.forEach { project ->
            projectDirectory(project.id)?.let { deleteOwnedDirectory(projectsRoot, it) }
        }
        database.runInTransaction {
            ids.chunked(900).forEach {
                dao.deleteSettingsForCharacters(it)
                dao.deleteProjectsForCharacters(it)
            }
        }
    }

    suspend fun deleteExceptCharacters(characterIds: List<String>) = withContext(Dispatchers.IO) {
        val retained = characterIds.toSet()
        val removing = buildSet {
            addAll(dao.allProjects().map(FrontendProjectEntity::characterId))
            addAll(dao.allSettings().map(CharacterFrontendSettingsEntity::characterId))
        }.filterNot { it in retained }
        deleteForCharacters(removing)
    }

    fun exportBackupJson(): String = ElecKoiJson.encodeToString(
        FrontendCatalog(
            projects = dao.allProjects().map(::fromEntity),
            selectedProjectIds = dao.allSettings().mapNotNull { row ->
                row.selectedProjectId?.let { row.characterId to it }
            }.toMap(),
            messageRendererEnabledByCharacter = dao.allSettings().associate {
                it.characterId to it.messageRendererEnabled
            },
        ),
    )

    fun restoreBackupJson(json: String) {
        val catalog = ElecKoiJson.decodeFromString<FrontendCatalog>(json)
        require(catalog.format == BackupFormat && catalog.version == BackupVersion) {
            "作者前端备份格式不正确"
        }
        require(catalog.projects.map(FrontendProject::id).distinct().size == catalog.projects.size) {
            "作者前端备份包含重复项目"
        }
        catalog.projects.forEach { project ->
            require(SafeProjectId.matches(project.id)) { "前端项目编号无效" }
            require(projectDirectoryFile(project.id).isDirectory) { "前端项目文件不存在：${project.name}" }
        }
        val projectsById = catalog.projects.associateBy(FrontendProject::id)
        catalog.selectedProjectIds.forEach { (characterId, projectId) ->
            require(projectsById[projectId]?.characterId == characterId) {
                "角色选择了不属于自己的前端项目"
            }
        }
        val settings = buildSet {
            addAll(catalog.projects.map(FrontendProject::characterId))
            addAll(catalog.selectedProjectIds.keys)
            addAll(catalog.messageRendererEnabledByCharacter.keys)
        }.map { characterId ->
            CharacterFrontendSettingsEntity(
                characterId = characterId,
                selectedProjectId = catalog.selectedProjectIds[characterId],
                messageRendererEnabled =
                    catalog.messageRendererEnabledByCharacter[characterId] ?: true,
            )
        }
        database.runInTransaction {
            dao.replaceAll(catalog.projects.map(::toEntity), settings)
        }
    }

    private fun persistProjectAndSelection(project: FrontendProject, select: Boolean) {
        database.runInTransaction {
            dao.upsertProject(toEntity(project))
            if (select) {
                dao.upsertSettings(currentSettings(project.characterId).copy(selectedProjectId = project.id))
            }
        }
    }

    private fun createHtmlProject(
        characterId: String,
        name: String,
        html: String,
        select: Boolean,
    ): FrontendProject {
        val id = UUID.randomUUID().toString()
        val staging = File(root, ".staging/$id/project")
        val destination = projectDirectoryFile(id)
        try {
            val imported = FrontendHtmlProjectFiles.create(html, staging)
            destination.parentFile?.mkdirs()
            require(staging.renameTo(destination)) { "无法保存 HTML 主题" }
            val project = FrontendProject(
                id = id,
                characterId = characterId,
                name = name,
                entryFile = imported.entryFile,
                files = imported.files,
                importedAt = Instant.now().toString(),
            )
            persistProjectAndSelection(project, select)
            return project
        } catch (error: Throwable) {
            destination.deleteRecursively()
            throw error
        } finally {
            File(root, ".staging/$id").deleteRecursively()
        }
    }

    private fun updateHtmlProject(
        characterId: String,
        projectId: String,
        name: String,
        html: String,
        select: Boolean,
    ): FrontendProject {
        require(SafeProjectId.matches(projectId)) { "前端项目编号无效" }
        val existing = dao.project(projectId)?.takeIf { it.characterId == characterId }
            ?: error("前端项目不存在")
        val directory = projectDirectoryFile(projectId)
        val previousHtml = FrontendHtmlProjectFiles.readEntry(directory, existing.entryFile)
        val existingProject = fromEntity(existing)
        val updated = existingProject.copy(
            name = name,
            files = (existingProject.files + existing.entryFile).distinct().sorted(),
            importedAt = Instant.now().toString(),
        )
        try {
            FrontendHtmlProjectFiles.replaceEntry(directory, existing.entryFile, html)
            persistProjectAndSelection(updated, select)
            return updated
        } catch (error: Throwable) {
            runCatching {
                FrontendHtmlProjectFiles.replaceEntry(directory, existing.entryFile, previousHtml)
            }
            throw error
        }
    }

    private fun currentSettings(characterId: String): CharacterFrontendSettingsEntity {
        return dao.allSettings().firstOrNull { it.characterId == characterId }
            ?: CharacterFrontendSettingsEntity(characterId, null, true)
    }

    private fun displayName(uri: Uri): String {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }
            .orEmpty()
    }

    private fun projectDirectoryFile(projectId: String) = File(projectsRoot, projectId)

    private fun toEntity(project: FrontendProject) = FrontendProjectEntity(
        id = project.id,
        characterId = project.characterId,
        name = project.name,
        entryFile = project.entryFile,
        filesJson = ElecKoiJson.encodeToString(project.files),
        importedAt = project.importedAt,
    )

    private fun fromEntity(entity: FrontendProjectEntity) = FrontendProject(
        id = entity.id,
        characterId = entity.characterId,
        name = entity.name,
        entryFile = entity.entryFile,
        files = ElecKoiJson.decodeFromString(entity.filesJson),
        importedAt = entity.importedAt,
    )

    private companion object {
        const val Megabyte = 1024L * 1024L
        const val BackupFormat = "eleckoi.author-frontends"
        const val BackupVersion = 1
        const val FrontendProjectCleanupKind = "frontend_project"
        const val CleanupTargetSeparator = "::"
        val SafeProjectId = Regex("[A-Za-z0-9._-]+")

        fun cleanupTarget(characterId: String, projectId: String): String {
            require(CleanupTargetSeparator !in characterId && CleanupTargetSeparator !in projectId) {
                "前端项目清理目标无效"
            }
            return "$characterId$CleanupTargetSeparator$projectId"
        }
    }
}

@Serializable
private data class FrontendCatalog(
    val format: String = "eleckoi.author-frontends",
    val version: Int = 1,
    val projects: List<FrontendProject> = emptyList(),
    val selectedProjectIds: Map<String, String> = emptyMap(),
    val messageRendererEnabledByCharacter: Map<String, Boolean> = emptyMap(),
)
