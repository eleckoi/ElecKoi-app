package com.eleckoi.android.engine.agent.tools

import com.eleckoi.android.foundation.storage.room.CharacterToolConfigEntity
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.GlobalToolConfigEntity
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Persistent tool visibility, keyed by [AgentToolScopes]. Which Harness tools exist is a property of
 * the installed runtime and stays shared; which of them a turn may call belongs to one character.
 */
class AgentToolCatalogStore private constructor(
    load: () -> AgentToolCatalogState,
    private val persist: (AgentToolCatalogState, AgentToolCatalogState) -> Unit,
) {
    internal constructor(
        file: File,
        persist: (File, AgentToolCatalogState) -> Unit,
    ) : this(
        load = { readAgentToolCatalogState(file) },
        persist = { _, value -> persist(file, value) },
    )

    constructor(file: File) : this(file, ::writeAgentToolCatalogState)

    constructor(database: ElecKoiDatabase) : this(
        load = { runRoomIo { readRoomState(database) } },
        persist = { previous, value -> runRoomIo { writeRoomState(database, previous, value) } },
    )

    private val lock = Any()
    private var state = load()

    fun exportBackupJson(): String = synchronized(lock) {
        encodeAgentToolCatalogState(state)
    }

    fun restoreBackupJson(json: String) = synchronized(lock) {
        updateState(decodeAgentToolCatalogState(json, "tool-config.json"))
    }

    fun deleteForCharacters(characterIds: Collection<String>) = synchronized(lock) {
        val scopes = characterIds.filter(String::isNotBlank).map(AgentToolScopes::character).toSet()
        if (scopes.isEmpty()) return@synchronized
        updateState(state.copy(
            scopedDisabledGroups = state.scopedDisabledGroups - scopes,
            scopedEnabledOptInGroups = state.scopedEnabledOptInGroups - scopes,
            scopedSubagentModelConfigIds = state.scopedSubagentModelConfigIds - scopes,
            scopedSubagentModels = state.scopedSubagentModels - scopes,
            scopedToolModelConfigIds = state.scopedToolModelConfigIds - scopes,
        ))
    }

    fun filterRequest(scopeId: String, request: JsonObject): JsonObject {
        val result = synchronized(lock) {
            val disabled = state.disabledIn(scopeId)
            val knownGroupIds = AgentToolRequestPolicy.builtInGroups()
                .mapTo(hashSetOf(), AgentToolGroupSnapshot::id)
                .apply { addAll(state.observedGroups.map(AgentToolGroupSnapshot::id)) }
            AgentToolRequestPolicy.filter(request) { groupId ->
                state.groupEnabled(scopeId, groupId, disabled, knownGroupIds)
            }
        }
        recordObservedGroups(result.observedGroups)
        return result.request
    }

    fun groups(scopeId: String): List<AgentToolGroupSnapshot> = synchronized(lock) {
        val disabled = state.disabledIn(scopeId)
        val observedById = state.observedGroups.associateBy(AgentToolGroupSnapshot::id)
        val builtInGroups = AgentToolRequestPolicy.builtInGroups()
        val knownGroupIds = builtInGroups
            .mapTo(hashSetOf(), AgentToolGroupSnapshot::id)
            .apply { addAll(observedById.keys) }
        val builtIns = builtInGroups.map { fallback ->
            val observed = observedById[fallback.id]
            fallback.copy(
                members = resolveBuiltInMembers(fallback, observed),
                enabled = state.groupEnabled(scopeId, fallback.id, disabled, knownGroupIds),
            )
        }
        val additional = state.observedGroups
            .filterNot { observed -> builtIns.any { it.id == observed.id } }
            .map { it.copy(enabled = it.id !in disabled) }
        (builtIns + additional)
            .filterNot { it.id in AgentToolRequestPolicy.HiddenGroupIds }
            .sortedWith(
                compareBy<AgentToolGroupSnapshot> { it.source.ordinal }.thenBy { it.name.lowercase() },
            )
    }

    fun setEnabled(scopeId: String, groupId: String, enabled: Boolean) = synchronized(lock) {
        updateState(if (isExplicitOptInGroup(scopeId, groupId)) {
            state.copy(
                scopedEnabledOptInGroups = toggleScopedOptInGroup(
                    scopeId = scopeId,
                    groupId = groupId,
                    enabled = enabled,
                    scoped = state.scopedEnabledOptInGroups,
                ),
            )
        } else {
            state.copy(
                scopedDisabledGroups = toggleScopedToolGroup(
                    scopeId = scopeId,
                    groupId = groupId,
                    enabled = enabled,
                    defaults = state.defaultDisabledGroups,
                    scoped = state.scopedDisabledGroups,
                ),
            )
        })
    }

    fun isEnabled(scopeId: String, groupId: String): Boolean = synchronized(lock) {
        val knownGroupIds = AgentToolRequestPolicy.builtInGroups()
            .mapTo(hashSetOf(), AgentToolGroupSnapshot::id)
            .apply { addAll(state.observedGroups.map(AgentToolGroupSnapshot::id)) }
        state.groupEnabled(scopeId, groupId, state.disabledIn(scopeId), knownGroupIds)
    }

    fun hasExplicitConfiguration(scopeId: String): Boolean = synchronized(lock) {
        val scope = AgentToolScopes.normalize(scopeId)
        scope in state.scopedDisabledGroups ||
            scope in state.scopedEnabledOptInGroups ||
            scope in state.scopedSubagentModelConfigIds ||
            scope in state.scopedSubagentModels ||
            scope in state.scopedToolModelConfigIds
    }

    fun subagentModelConfigId(scopeId: String): String = synchronized(lock) {
        state.scopedSubagentModelConfigIds[AgentToolScopes.normalize(scopeId)].orEmpty()
    }

    fun subagentModel(scopeId: String): String = synchronized(lock) {
        state.scopedSubagentModels[AgentToolScopes.normalize(scopeId)].orEmpty()
    }

    fun setSubagentModelConfigId(scopeId: String, configId: String) = synchronized(lock) {
        val scope = AgentToolScopes.normalize(scopeId)
        val normalized = configId.trim()
        val next = state.scopedSubagentModelConfigIds.toMutableMap().apply {
            if (normalized.isBlank()) remove(scope) else put(scope, normalized)
        }
        updateState(
            state.copy(
                scopedSubagentModelConfigIds = next,
                scopedSubagentModels = if (normalized.isBlank()) {
                    state.scopedSubagentModels - scope
                } else {
                    state.scopedSubagentModels
                },
            ),
        )
    }

    fun setSubagentModel(scopeId: String, configId: String, model: String) = synchronized(lock) {
        val scope = AgentToolScopes.normalize(scopeId)
        val normalizedConfig = configId.trim()
        val normalizedModel = model.trim()
        val nextConfigs = state.scopedSubagentModelConfigIds.toMutableMap().apply {
            if (normalizedConfig.isBlank()) remove(scope) else put(scope, normalizedConfig)
        }
        val nextModels = state.scopedSubagentModels.toMutableMap().apply {
            if (normalizedConfig.isBlank() || normalizedModel.isBlank()) remove(scope)
            else put(scope, normalizedModel)
        }
        updateState(
            state.copy(
                scopedSubagentModelConfigIds = nextConfigs,
                scopedSubagentModels = nextModels,
            ),
        )
    }

    fun toolModelConfigId(scopeId: String, groupId: String): String = synchronized(lock) {
        scopedToolModelConfigId(scopeId, groupId, state.scopedToolModelConfigIds)
    }

    fun setToolModelConfigId(scopeId: String, groupId: String, configId: String) = synchronized(lock) {
        updateState(
            state.copy(
                scopedToolModelConfigIds = selectScopedToolModelConfig(
                    scopeId = scopeId,
                    groupId = groupId,
                    configId = configId,
                    scoped = state.scopedToolModelConfigIds,
                ),
            ),
        )
    }

    /**
     * One shared order drives both the insertion preview and the model-visible context bucket.
     * Keeping it independently persisted allows a later reorder UI without a protocol redesign.
     */
    fun toolContextSnapshot(scopeId: String): AgentToolContextSnapshot = synchronized(lock) {
        buildAgentToolContextSnapshot(
            allGroups = groups(scopeId),
            contextOrder = state.contextOrder,
        )
    }

    fun setToolContextOrder(groupIds: List<String>) = synchronized(lock) {
        updateState(state.copy(contextOrder = groupIds.filter(String::isNotBlank).distinct()))
    }

    fun recordMcpServer(
        serverId: String,
        displayName: String,
        description: String,
        tools: List<AgentToolMember>,
    ) {
        val normalizedId = serverId.trim()
        if (normalizedId.isBlank()) return
        val group = AgentToolGroupSnapshot(
            id = AgentToolRequestPolicy.mcpGroupId(normalizedId),
            name = if (normalizedId == "exa") "联网（Exa）" else displayName.ifBlank { normalizedId },
            description = description.ifBlank {
                if (normalizedId == "exa") "搜索网页并抓取完整页面内容" else "来自 ${displayName.ifBlank { normalizedId }} MCP 服务器"
            },
            source = AgentToolGroupSource.Mcp,
            sourceId = normalizedId,
            members = tools.distinctBy(AgentToolMember::name),
        )
        recordObservedGroups(listOf(group))
    }

    fun forgetMcpServer(serverId: String) = synchronized(lock) {
        val groupId = AgentToolRequestPolicy.mcpGroupId(serverId)
        updateState(
            state.copy(
                defaultDisabledGroups = state.defaultDisabledGroups - groupId,
                scopedDisabledGroups = state.scopedDisabledGroups.mapValues { (_, ids) -> ids - groupId },
                observedGroups = state.observedGroups.filterNot { it.id == groupId },
            ),
        )
    }

    private fun recordObservedGroups(groups: List<AgentToolGroupSnapshot>) {
        if (groups.isEmpty()) return
        synchronized(lock) {
            val newlyObservedIds = newlyObservedCapabilityGroupIds(
                previous = state.observedGroups,
                incoming = groups,
            )
            val next = state.copy(
                // A provider/MCP group that appears for the first time is a new capability, not
                // implicit consent. Add it to every baseline; the user can then enable it in the
                // intended scope.
                defaultDisabledGroups = state.defaultDisabledGroups + newlyObservedIds,
                scopedDisabledGroups = state.scopedDisabledGroups.mapValues { (_, ids) ->
                    ids + newlyObservedIds
                },
                observedGroups = mergeObservedToolGroups(state.observedGroups, groups),
            )
            if (next != state) updateState(next)
        }
    }

    private fun updateState(next: AgentToolCatalogState) {
        if (next == state) return
        persist(state, next)
        state = next
    }

}

private fun readRoomState(database: ElecKoiDatabase): AgentToolCatalogState {
    val dao = database.agentToolConfigDao()
    val global = dao.global()?.payloadJson
        ?.let { decodeAgentToolCatalogState(it, "global_tool_config") }
        ?: AgentToolCatalogState()
    return dao.characters().fold(global) { current, row ->
        val decoded = decodeAgentToolCatalogState(
            row.payloadJson,
            "character_tool_configs/${row.characterId}",
        )
        current.copy(
            scopedDisabledGroups = current.scopedDisabledGroups + decoded.scopedDisabledGroups,
            scopedEnabledOptInGroups =
                current.scopedEnabledOptInGroups + decoded.scopedEnabledOptInGroups,
            scopedSubagentModelConfigIds =
                current.scopedSubagentModelConfigIds + decoded.scopedSubagentModelConfigIds,
            scopedSubagentModels = current.scopedSubagentModels + decoded.scopedSubagentModels,
            scopedToolModelConfigIds =
                current.scopedToolModelConfigIds + decoded.scopedToolModelConfigIds,
        )
    }
}

private fun writeRoomState(
    database: ElecKoiDatabase,
    previous: AgentToolCatalogState,
    state: AgentToolCatalogState,
) {
    val now = Instant.now().toString()
    val previousShared = encodeAgentToolCatalogState(previous.onlyScopes(setOf(AgentToolScopes.Shared)))
    val shared = encodeAgentToolCatalogState(state.onlyScopes(setOf(AgentToolScopes.Shared)))
    val previousCharacters = characterToolPayloads(previous)
    val characters = characterToolPayloads(state)
    val removed = previousCharacters.keys.filterNot(characters::containsKey)
    val changed = characters.mapNotNull { (characterId, payloadJson) ->
        if (previousCharacters[characterId] == payloadJson) null else CharacterToolConfigEntity(
            characterId = characterId,
            payloadJson = payloadJson,
            updatedAt = now,
        )
    }
    database.runInTransaction {
        val dao = database.agentToolConfigDao()
        if (previousShared != shared) {
            dao.upsertGlobal(GlobalToolConfigEntity(payloadJson = shared, updatedAt = now))
        }
        removed.chunked(900).forEach(dao::deleteCharacters)
        if (changed.isNotEmpty()) dao.upsertCharacters(changed)
    }
}

private fun characterToolPayloads(state: AgentToolCatalogState): Map<String, String> {
    val characterScopes = buildSet {
        addAll(state.scopedDisabledGroups.keys)
        addAll(state.scopedEnabledOptInGroups.keys)
        addAll(state.scopedSubagentModelConfigIds.keys)
        addAll(state.scopedSubagentModels.keys)
        addAll(state.scopedToolModelConfigIds.keys)
    }.mapNotNull { scope -> AgentToolScopes.characterId(scope)?.let { it to scope } }
    return characterScopes.associate { (characterId, scope) ->
        characterId to encodeAgentToolCatalogState(
            state.onlyScopes(setOf(scope)).copy(
                observedGroups = emptyList(),
                contextOrder = emptyList(),
            ),
        )
    }
}

private fun AgentToolCatalogState.onlyScopes(scopes: Set<String>) = copy(
    scopedDisabledGroups = scopedDisabledGroups.filterKeys { it in scopes },
    scopedEnabledOptInGroups = scopedEnabledOptInGroups.filterKeys { it in scopes },
    scopedSubagentModelConfigIds = scopedSubagentModelConfigIds.filterKeys { it in scopes },
    scopedSubagentModels = scopedSubagentModels.filterKeys { it in scopes },
    scopedToolModelConfigIds = scopedToolModelConfigIds.filterKeys { it in scopes },
)

private fun <T> runRoomIo(block: () -> T): T = runBlocking {
    withContext(Dispatchers.IO) { block() }
}
