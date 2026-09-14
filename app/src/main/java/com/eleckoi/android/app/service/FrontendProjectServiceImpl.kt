package com.eleckoi.android.app.service

import android.net.Uri
import com.eleckoi.android.engine.immersive.api.FrontendProjectService
import com.eleckoi.android.engine.immersive.model.FrontendProject
import com.eleckoi.android.engine.immersive.model.FrontendWorkspace
import com.eleckoi.android.engine.immersive.project.FrontendProjectRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

internal class FrontendProjectServiceImpl(
    private val frontendProjects: FrontendProjectRepository,
) : FrontendProjectService {
    override fun frontendWorkspaceFlow(characterId: String): Flow<FrontendWorkspace> {
        return frontendProjects.workspaceFlow(characterId).distinctUntilChanged()
    }

    override suspend fun importFrontendProject(characterId: String, uri: Uri): FrontendProject {
        return frontendProjects.importProject(characterId, uri)
    }

    override suspend fun publishFrontendProject(
        characterId: String,
        sourceDirectory: File,
        name: String,
        entryFile: String,
        select: Boolean,
    ): FrontendProject {
        return frontendProjects.publishProject(characterId, sourceDirectory, name, entryFile, select)
    }

    override suspend fun saveHtmlFrontendProject(
        characterId: String,
        projectId: String?,
        name: String,
        html: String,
        select: Boolean,
    ): FrontendProject {
        return frontendProjects.saveHtmlProject(characterId, projectId, name, html, select)
    }

    override suspend fun readFrontendProjectEntry(characterId: String, projectId: String): String {
        return frontendProjects.readProjectEntry(characterId, projectId)
    }

    override suspend fun selectFrontendProject(characterId: String, projectId: String?) {
        frontendProjects.selectProject(characterId, projectId)
    }

    override suspend fun setMessageRendererEnabled(characterId: String, enabled: Boolean) {
        frontendProjects.setMessageRendererEnabled(characterId, enabled)
    }

    override suspend fun deleteFrontendProject(characterId: String, projectId: String) {
        frontendProjects.deleteProject(characterId, projectId)
    }

    override fun frontendProjectDirectory(projectId: String): File? {
        return frontendProjects.projectDirectory(projectId)
    }
}
