package com.eleckoi.android.foundation.storage.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthorFrontendDao {
    @Query("SELECT * FROM frontend_projects WHERE characterId = :characterId ORDER BY importedAt, id")
    fun observeProjects(characterId: String): Flow<List<FrontendProjectEntity>>

    @Query("SELECT * FROM character_frontend_settings WHERE characterId = :characterId LIMIT 1")
    fun observeSettings(characterId: String): Flow<CharacterFrontendSettingsEntity?>

    @Query("SELECT * FROM frontend_projects ORDER BY characterId, importedAt, id")
    fun allProjects(): List<FrontendProjectEntity>

    @Query("SELECT * FROM character_frontend_settings ORDER BY characterId")
    fun allSettings(): List<CharacterFrontendSettingsEntity>

    @Query("SELECT * FROM frontend_projects WHERE id = :projectId LIMIT 1")
    fun project(projectId: String): FrontendProjectEntity?

    @Upsert
    fun upsertProject(project: FrontendProjectEntity)

    @Upsert
    fun upsertSettings(settings: CharacterFrontendSettingsEntity)

    @Query("DELETE FROM frontend_projects WHERE id = :projectId AND characterId = :characterId")
    fun deleteProject(characterId: String, projectId: String)

    @Query("DELETE FROM frontend_projects WHERE characterId IN (:characterIds)")
    fun deleteProjectsForCharacters(characterIds: List<String>)

    @Query("DELETE FROM character_frontend_settings WHERE characterId IN (:characterIds)")
    fun deleteSettingsForCharacters(characterIds: List<String>)

    @Query("DELETE FROM frontend_projects")
    fun deleteAllProjects()

    @Query("DELETE FROM character_frontend_settings")
    fun deleteAllSettings()

    @Transaction
    fun replaceAll(
        projects: List<FrontendProjectEntity>,
        settings: List<CharacterFrontendSettingsEntity>,
    ) {
        deleteAllSettings()
        deleteAllProjects()
        projects.forEach(::upsertProject)
        settings.forEach(::upsertSettings)
    }
}
