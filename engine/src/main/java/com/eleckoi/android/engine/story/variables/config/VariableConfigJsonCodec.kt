package com.eleckoi.android.engine.story.variables.config

import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableConfigVersion
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import com.eleckoi.android.engine.story.variables.model.VariableReadMode
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.objects
import com.eleckoi.android.foundation.storage.room.VariableConfigEntity
import com.eleckoi.android.foundation.storage.room.VariableConfigRecord
import com.eleckoi.android.foundation.storage.room.VariableConfigVersionEntity
import com.eleckoi.android.foundation.storage.room.VariableConfigVersionContentEntity
import com.eleckoi.android.foundation.storage.room.VariableConfigObjectEntity
import com.eleckoi.android.foundation.storage.room.VariableConfigVariableEntity
import com.eleckoi.android.foundation.storage.stringOrEmpty
import com.eleckoi.android.foundation.storage.strings
import org.json.JSONArray
import org.json.JSONObject

internal data class RestoredVariableConfigDocument(
    val requestedActiveVersionId: String,
    val versions: List<VariableConfigVersion>,
)

internal object VariableConfigJsonCodec {
    fun encode(config: VariableConfig, updatedAt: String): String {
        return JSONObject()
            .put("format", VariableConfigFormat)
            .put("version", 1)
            .put("character_id", config.characterId)
            .put("name", config.name)
            .put("active_version_id", config.activeVersionId)
            .put("updated_at", updatedAt)
            .put("initial_state", JSONObject(config.initialStateJson.ifBlank { "{}" }))
            .put("schema_code", config.schemaCode)
            .put("objects", JSONArray(config.objects.map(::objectJson)))
            .put("variables", JSONArray(config.variables.map(::variableJson)))
            .put("expanded_object_ids", JSONArray(config.expandedObjectIds))
            .put("versions", JSONArray(config.versions.map(::versionJson)))
            .toString(2)
    }

    fun decodeRestore(json: String): RestoredVariableConfigDocument {
        val data = parse(json)
        if (data.optString("format") != VariableConfigFormat) {
            throw ElecKoiDataException("这不是 ElecKoi 变量配置文件")
        }
        val versions = data.optJSONArray("versions")
            ?.objects()
            ?.map(::versionFromJson)
            ?.toList()
            .orEmpty()
            .takeIf { it.isNotEmpty() }
            ?: listOf(rootVersion(data))
        return RestoredVariableConfigDocument(
            requestedActiveVersionId = data.stringOrEmpty("active_version_id"),
            versions = versions,
        )
    }

    fun decodeImport(json: String): VariableConfigVersion {
        val data = parse(json)
        val format = data.stringOrEmpty("format")
        if (format.isNotBlank() && format != VariableConfigFormat) {
            throw ElecKoiDataException("这不是 ElecKoi 变量配置文件")
        }
        return rootVersion(data)
    }

    fun toRecord(config: VariableConfig, updatedAt: String, revision: Long): VariableConfigRecord {
        val versions = config.versions.mapIndexed { index, version ->
            VariableConfigVersionEntity(
                characterId = config.characterId,
                versionId = version.id,
                sortIndex = index,
                name = version.name,
                expandedObjectIdsJson = JSONArray(version.expandedObjectIds).toString(),
                createdAt = version.createdAt,
                updatedAt = version.updatedAt,
            )
        }
        return VariableConfigRecord(
            config = VariableConfigEntity(config.characterId, config.activeVersionId, updatedAt, revision),
            versions = versions,
            contents = config.versions.flatMap { version ->
                listOf(
                    VariableConfigVersionContentEntity(
                        config.characterId, version.id, InitialStateContentKind,
                        version.resolvedInitialStateJson(),
                    ),
                    VariableConfigVersionContentEntity(
                        config.characterId, version.id, SchemaCodeContentKind, version.schemaCode,
                    ),
                )
            },
            objects = config.versions.flatMap { version ->
                version.objects.mapIndexed { index, item ->
                    VariableConfigObjectEntity(
                        config.characterId, version.id, item.id, index, objectJson(item).toString(),
                    )
                }
            },
            variables = config.versions.flatMap { version ->
                version.variables.mapIndexed { index, item ->
                    VariableConfigVariableEntity(
                        config.characterId, version.id, item.id, index, variableJson(item).toString(),
                    )
                }
            },
        )
    }

    fun versionsFromRecord(record: VariableConfigRecord): List<VariableConfigVersion> {
        val contentByKey = record.contents.associate { (it.versionId to it.kind) to it.content }
        val objectsByVersion = record.objects.groupBy(VariableConfigObjectEntity::versionId)
        val variablesByVersion = record.variables.groupBy(VariableConfigVariableEntity::versionId)
        return record.versions.sortedBy(VariableConfigVersionEntity::sortIndex).map { entity ->
            VariableConfigVersion(
                id = entity.versionId,
                name = entity.name,
                initialStateJson = contentByKey[entity.versionId to InitialStateContentKind].orEmpty(),
                schemaCode = contentByKey[entity.versionId to SchemaCodeContentKind].orEmpty(),
                objects = objectsByVersion[entity.versionId].orEmpty()
                    .sortedBy(VariableConfigObjectEntity::sortIndex)
                    .mapIndexed { index, row -> objectFromJson(JSONObject(row.payloadJson), index) },
                variables = variablesByVersion[entity.versionId].orEmpty()
                    .sortedBy(VariableConfigVariableEntity::sortIndex)
                    .mapIndexed { index, row -> variableFromJson(JSONObject(row.payloadJson), index) },
                expandedObjectIds = JSONArray(entity.expandedObjectIdsJson).strings(),
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
        }
    }

    private fun parse(json: String): JSONObject {
        return runCatching { JSONObject(json) }
            .getOrElse { throw ElecKoiDataException("变量配置文件格式不正确", it) }
    }

    private fun rootVersion(data: JSONObject): VariableConfigVersion {
        return VariableConfigVersion(
            id = data.stringOrEmpty("active_version_id"),
            name = data.stringOrEmpty("name"),
            initialStateJson = data.optJSONObject("initial_state")?.toString(2).orEmpty(),
            schemaCode = data.stringOrEmpty("schema_code"),
            objects = data.optJSONArray("objects")
                ?.objects()
                ?.mapIndexed { index, value -> objectFromJson(value, index) }
                ?.toList()
                .orEmpty(),
            variables = data.optJSONArray("variables")
                ?.objects()
                ?.mapIndexed { index, value -> variableFromJson(value, index) }
                ?.toList()
                .orEmpty(),
            expandedObjectIds = data.optJSONArray("expanded_object_ids")?.strings().orEmpty(),
        )
    }

    private fun objectFromJson(value: JSONObject, index: Int): VariableObjectConfig {
        return VariableObjectConfig(
            id = value.stringOrEmpty("id"),
            name = value.stringOrEmpty("name"),
            parentId = value.stringOrEmpty("parent_id"),
            enabled = value.optBoolean("enabled", true),
            description = value.stringOrEmpty("description"),
            updateRule = value.stringOrEmpty("update_rule"),
            dynamicKey = value.optBoolean("dynamic_key", false),
            order = value.optInt("order", index + 1).coerceAtLeast(0),
            treeViewOrder = value.optInt("tree_view_order", index + 1).coerceAtLeast(0),
            createdAt = value.stringOrEmpty("created_at"),
            updatedAt = value.stringOrEmpty("updated_at"),
        )
    }

    private fun variableFromJson(value: JSONObject, index: Int): VariableItemConfig {
        return VariableItemConfig(
            id = value.stringOrEmpty("id"),
            title = value.stringOrEmpty("title"),
            objectId = value.stringOrEmpty("object_id"),
            enabled = value.optBoolean("enabled", true),
            type = value.stringOrEmpty("type"),
            defaultValue = value.stringOrEmpty("default_value"),
            description = value.stringOrEmpty("description"),
            updateRule = value.stringOrEmpty("update_rule"),
            readMode = VariableReadMode.entries.firstOrNull {
                it.storageValue == value.stringOrEmpty("read_mode")
            } ?: VariableReadMode.OnDemand,
            order = value.optInt("order", index + 1).coerceAtLeast(1),
            treeViewOrder = value.optInt("tree_view_order", index + 1).coerceAtLeast(0),
            createdAt = value.stringOrEmpty("created_at"),
            updatedAt = value.stringOrEmpty("updated_at"),
        )
    }

    private fun versionFromJson(value: JSONObject): VariableConfigVersion {
        return VariableConfigVersion(
            id = value.stringOrEmpty("id"),
            name = value.stringOrEmpty("name"),
            initialStateJson = value.optJSONObject("initial_state")?.toString(2).orEmpty(),
            schemaCode = value.stringOrEmpty("schema_code"),
            objects = value.optJSONArray("objects")
                ?.objects()
                ?.mapIndexed { index, item -> objectFromJson(item, index) }
                ?.toList()
                .orEmpty(),
            variables = value.optJSONArray("variables")
                ?.objects()
                ?.mapIndexed { index, item -> variableFromJson(item, index) }
                ?.toList()
                .orEmpty(),
            expandedObjectIds = value.optJSONArray("expanded_object_ids")?.strings().orEmpty(),
            createdAt = value.stringOrEmpty("created_at"),
            updatedAt = value.stringOrEmpty("updated_at"),
        )
    }

    private fun objectJson(variableObject: VariableObjectConfig): JSONObject {
        return JSONObject()
            .put("id", variableObject.id)
            .put("name", variableObject.name)
            .put("parent_id", variableObject.parentId)
            .put("enabled", variableObject.enabled)
            .put("description", variableObject.description)
            .put("update_rule", variableObject.updateRule)
            .put("dynamic_key", variableObject.dynamicKey)
            .put("order", variableObject.order)
            .put("tree_view_order", variableObject.treeViewOrder)
            .put("created_at", variableObject.createdAt)
            .put("updated_at", variableObject.updatedAt)
    }

    private fun variableJson(item: VariableItemConfig): JSONObject {
        return JSONObject()
            .put("id", item.id)
            .put("title", item.title)
            .put("object_id", item.objectId)
            .put("enabled", item.enabled)
            .put("type", item.type)
            .put("default_value", item.defaultValue)
            .put("description", item.description)
            .put("update_rule", item.updateRule)
            .put("read_mode", item.readMode.storageValue)
            .put("order", item.order)
            .put("tree_view_order", item.treeViewOrder)
            .put("created_at", item.createdAt)
            .put("updated_at", item.updatedAt)
    }

    private fun versionJson(version: VariableConfigVersion): JSONObject {
        return JSONObject()
            .put("id", version.id)
            .put("name", version.name)
            .put("initial_state", JSONObject(version.resolvedInitialStateJson()))
            .put("schema_code", version.schemaCode)
            .put("objects", JSONArray(version.objects.map(::objectJson)))
            .put("variables", JSONArray(version.variables.map(::variableJson)))
            .put("expanded_object_ids", JSONArray(version.expandedObjectIds))
            .put("created_at", version.createdAt)
            .put("updated_at", version.updatedAt)
    }
}

private const val VariableConfigFormat = "eleckoi.variable-config"
private const val InitialStateContentKind = "initial_state"
private const val SchemaCodeContentKind = "schema_code"
