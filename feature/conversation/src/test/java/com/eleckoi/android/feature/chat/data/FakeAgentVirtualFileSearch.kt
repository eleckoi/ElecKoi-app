package com.eleckoi.android.feature.chat.data

import com.eleckoi.android.engine.agent.api.AgentVirtualFile
import com.eleckoi.android.engine.agent.api.AgentVirtualFileSearch
import com.eleckoi.android.engine.agent.api.AgentVirtualGlobRequest
import com.eleckoi.android.engine.agent.api.AgentVirtualGlobResult
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepRequest
import com.eleckoi.android.engine.agent.api.AgentVirtualGrepResult

internal class FakeAgentVirtualFileSearch(
    private val globHandler: (
        files: List<AgentVirtualFile>,
        request: AgentVirtualGlobRequest,
    ) -> AgentVirtualGlobResult = { _, _ -> AgentVirtualGlobResult(emptyList(), 0) },
    private val grepHandler: (
        files: List<AgentVirtualFile>,
        request: AgentVirtualGrepRequest,
    ) -> AgentVirtualGrepResult = { _, _ ->
        AgentVirtualGrepResult(
            paths = emptyList(),
            counts = emptyMap(),
            lines = emptyList(),
            omittedPaths = 0,
            omittedLines = 0,
        )
    },
) : AgentVirtualFileSearch {
    override suspend fun glob(
        files: List<AgentVirtualFile>,
        request: AgentVirtualGlobRequest,
    ): AgentVirtualGlobResult = globHandler(files, request)

    override suspend fun grep(
        files: List<AgentVirtualFile>,
        request: AgentVirtualGrepRequest,
    ): AgentVirtualGrepResult = grepHandler(files, request)
}
