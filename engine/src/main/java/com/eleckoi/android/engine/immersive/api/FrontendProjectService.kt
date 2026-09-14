package com.eleckoi.android.engine.immersive.api

import android.net.Uri
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.engine.immersive.model.FrontendWorkspace
import java.io.File
import kotlinx.coroutines.flow.Flow

interface FrontendProjectService {
    fun frontendWorkspaceFlow(characterId: String): Flow<FrontendWorkspace>
    suspend fun importFrontendProject(characterId: String, uri: Uri): FrontendProject
    suspend fun publishFrontendProject(
        characterId: String,
        sourceDirectory: File,
        name: String,
        entryFile: String = "index.html",
        select: Boolean = true,
    ): FrontendProject
    suspend fun saveHtmlFrontendProject(
        characterId: String,
        projectId: String?,
        name: String,
        html: String,
        select: Boolean = true,
    ): FrontendProject
    suspend fun readFrontendProjectEntry(characterId: String, projectId: String): String
    suspend fun selectFrontendProject(characterId: String, projectId: String?)
    suspend fun setMessageRendererEnabled(characterId: String, enabled: Boolean)
    suspend fun deleteFrontendProject(characterId: String, projectId: String)
    fun frontendProjectDirectory(projectId: String): File?
}
