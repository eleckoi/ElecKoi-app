package com.eleckoi.android.engine.agent.tools

import android.util.AtomicFile
import com.eleckoi.android.foundation.serialization.ElecKoiJson
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Versioned JSON and atomic-file boundary for the tool catalog. */
internal fun readAgentToolCatalogState(file: File): AgentToolCatalogState {
    if (!file.exists()) return AgentToolCatalogState()
    if (!file.isFile) {
        throw ElecKoiDataException("工具配置路径不是文件：${file.absolutePath}")
    }
    return decodeAgentToolCatalogState(file.readText(Charsets.UTF_8), file.absolutePath)
}

internal fun decodeAgentToolCatalogState(
    json: String,
    sourceDescription: String = "Room",
): AgentToolCatalogState {
    return try {
        val root = ElecKoiJson.parseToJsonElement(json).jsonObject
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
            ?: throw ElecKoiDataException("工具配置文件缺少版本")
        require(version == CurrentStateVersion) {
            "工具配置文件版本不受支持：$version（当前为 $CurrentStateVersion）"
        }
        val disabled = (root["disabledGroups"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter(String::isNotBlank)
            .toSet()
        val observed = (root["observedGroups"] as? JsonArray)
            .orEmpty()
            .mapNotNull(::parseGroup)
            .distinctBy(AgentToolGroupSnapshot::id)
        val contextOrder = (root["toolContextOrder"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter(String::isNotBlank)
            .distinct()
        val scoped = (root["scopedDisabledGroups"] as? JsonObject)
            .orEmpty()
            .mapNotNull { (scopeId, ids) ->
                val normalized = AgentToolScopes.normalize(scopeId)
                val groupIds = (ids as? JsonArray)
                    .orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .filter(String::isNotBlank)
                    .toSet()
                normalized to groupIds
            }
            .toMap()
        val scopedSubagentModels = (root["scopedSubagentModelConfigIds"] as? JsonObject)
            .orEmpty()
            .mapNotNull { (scopeId, value) ->
                val configId = (value as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                AgentToolScopes.normalize(scopeId) to configId
            }
            .toMap()
        val scopedEnabledOptInGroups = (root["scopedEnabledOptInGroups"] as? JsonObject)
            .orEmpty()
            .mapNotNull { (scopeId, ids) ->
                val enabledIds = (ids as? JsonArray)
                    .orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .filter { it in ExplicitOptInAgentToolGroupIds }
                    .toSet()
                AgentToolScopes.normalize(scopeId) to enabledIds
            }
            .toMap()
        val scopedSubagentModelNames = (root["scopedSubagentModels"] as? JsonObject)
            .orEmpty()
            .mapNotNull { (scopeId, value) ->
                val model = (value as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                AgentToolScopes.normalize(scopeId) to model
            }
            .toMap()
        val scopedToolModelConfigIds = (root["scopedToolModelConfigIds"] as? JsonObject)
            .orEmpty()
            .mapNotNull { (scopeId, value) ->
                val selections = (value as? JsonObject)
                    .orEmpty()
                    .mapNotNull { (groupId, configValue) ->
                        val configId = (configValue as? JsonPrimitive)
                            ?.contentOrNull
                            ?.trim()
                            ?.takeIf(String::isNotBlank)
                            ?: return@mapNotNull null
                        groupId.trim().takeIf(String::isNotBlank)?.let { it to configId }
                    }
                    .toMap()
                selections.takeIf { it.isNotEmpty() }?.let {
                    AgentToolScopes.normalize(scopeId) to it
                }
            }
            .toMap()
        AgentToolCatalogState(
            defaultDisabledGroups = defaultDisabledToolGroups(
                disabled = disabled,
                observed = observed,
            ),
            scopedDisabledGroups = scoped,
            scopedEnabledOptInGroups = scopedEnabledOptInGroups,
            scopedSubagentModelConfigIds = scopedSubagentModels,
            scopedSubagentModels = scopedSubagentModelNames,
            scopedToolModelConfigIds = scopedToolModelConfigIds,
            observedGroups = observed,
            contextOrder = contextOrder,
        )
    } catch (error: Exception) {
        throw ElecKoiDataException("无法读取工具配置：$sourceDescription", error)
    }
}

internal fun writeAgentToolCatalogState(file: File, value: AgentToolCatalogState) {
    require(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true) {
        "无法创建工具配置目录"
    }
    val content = encodeAgentToolCatalogState(value)
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try {
        stream.write(content.toByteArray(Charsets.UTF_8))
        stream.fd.sync()
        atomic.finishWrite(stream)
    } catch (error: Throwable) {
        atomic.failWrite(stream)
        throw error
    }
}

internal fun encodeAgentToolCatalogState(value: AgentToolCatalogState): String {
    return buildJsonObject {
        put("version", CurrentStateVersion)
        put("disabledGroups", buildJsonArray {
            value.defaultDisabledGroups.sorted().forEach { add(JsonPrimitive(it)) }
        })
        put("scopedDisabledGroups", buildJsonObject {
            value.scopedDisabledGroups.toSortedMap().forEach { (scopeId, groupIds) ->
                put(scopeId, buildJsonArray {
                    groupIds.sorted().forEach { add(JsonPrimitive(it)) }
                })
            }
        })
        put("scopedEnabledOptInGroups", buildJsonObject {
            value.scopedEnabledOptInGroups.toSortedMap().forEach { (scopeId, groupIds) ->
                put(scopeId, buildJsonArray {
                    groupIds.sorted().forEach { add(JsonPrimitive(it)) }
                })
            }
        })
        put("toolContextOrder", buildJsonArray {
            value.contextOrder.forEach { add(JsonPrimitive(it)) }
        })
        put("scopedSubagentModelConfigIds", buildJsonObject {
            value.scopedSubagentModelConfigIds.toSortedMap().forEach { (scopeId, configId) ->
                put(scopeId, configId)
            }
        })
        put("scopedSubagentModels", buildJsonObject {
            value.scopedSubagentModels.toSortedMap().forEach { (scopeId, model) ->
                put(scopeId, model)
            }
        })
        put("scopedToolModelConfigIds", buildJsonObject {
            value.scopedToolModelConfigIds.toSortedMap().forEach { (scopeId, selections) ->
                put(scopeId, buildJsonObject {
                    selections.toSortedMap().forEach { (groupId, configId) ->
                        put(groupId, configId)
                    }
                })
            }
        })
        put("observedGroups", buildJsonArray {
            value.observedGroups.forEach { group ->
                add(buildJsonObject {
                    put("id", group.id)
                    put("name", group.name)
                    put("description", group.description)
                    put("source", group.source.name)
                    put("sourceId", group.sourceId)
                    put("members", buildJsonArray {
                        group.members.forEach { member ->
                            add(buildJsonObject {
                                put("name", member.name)
                                put("displayName", member.displayName)
                                put("description", member.description)
                            })
                        }
                    })
                })
            }
        })
    }.toString()
}

private fun parseGroup(element: kotlinx.serialization.json.JsonElement): AgentToolGroupSnapshot? {
    val group = element as? JsonObject ?: return null
    val id = group.string("id")?.takeIf(String::isNotBlank) ?: return null
    val name = group.string("name")?.takeIf(String::isNotBlank) ?: return null
    val sourceName = group.string("source")
    val source = if (sourceName.isNullOrBlank()) {
        AgentToolGroupSource.Extension
    } else {
        AgentToolGroupSource.valueOf(sourceName)
    }
    return AgentToolGroupSnapshot(
        id = id,
        name = name,
        description = group.string("description").orEmpty(),
        source = source,
        sourceId = group.string("sourceId").orEmpty(),
        members = (group["members"] as? JsonArray)
            .orEmpty()
            .mapNotNull { memberElement ->
                val member = memberElement as? JsonObject ?: return@mapNotNull null
                val memberName = member.string("name")?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                AgentToolMember(
                    name = memberName,
                    displayName = member.string("displayName").orEmpty().ifBlank { memberName },
                    description = member.string("description").orEmpty(),
                )
            },
    )
}

private fun JsonObject.string(name: String): String? =
    (get(name) as? JsonPrimitive)?.contentOrNull

private const val CurrentStateVersion = 7
