package com.eleckoi.android.engine.agent.api

data class AgentVirtualFile(
    val path: String,
    val content: String,
)

data class AgentVirtualGlobRequest(
    val pattern: String,
    val ignoreCase: Boolean = false,
)

data class AgentVirtualGlobResult(
    val paths: List<String>,
    val omitted: Int,
)

data class AgentVirtualGrepRequest(
    val pattern: String,
    val fileGlob: String? = null,
    val ignoreCase: Boolean = false,
    val multiline: Boolean = false,
    val limit: Int = 100,
)

data class AgentVirtualGrepLine(
    val path: String,
    val line: Int,
    val text: String,
    val matchCount: Int,
)

data class AgentVirtualGrepResult(
    val paths: List<String>,
    val counts: Map<String, Int>,
    val lines: List<AgentVirtualGrepLine>,
    val omittedPaths: Int,
    val omittedLines: Int,
)

interface AgentVirtualFileSearch {
    suspend fun glob(
        files: List<AgentVirtualFile>,
        request: AgentVirtualGlobRequest,
    ): AgentVirtualGlobResult

    suspend fun grep(
        files: List<AgentVirtualFile>,
        request: AgentVirtualGrepRequest,
    ): AgentVirtualGrepResult
}
