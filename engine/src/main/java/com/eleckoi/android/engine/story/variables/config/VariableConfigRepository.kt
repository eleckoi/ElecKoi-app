package com.eleckoi.android.engine.story.variables.config

import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableConfigVersion
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.nowIso
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.VariableConfigRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun interface VariableConfigCharacterLookup {
    fun exists(characterId: String): Boolean
}

data class VersionedVariableConfig(
    val config: VariableConfig,
    val revision: Long,
)

class VariableConfigRepository(
    private val database: ElecKoiDatabase,
    private val characters: VariableConfigCharacterLookup,
) {
    private val dao = database.variableConfigDao()

    fun configFlow(characterId: String): Flow<VariableConfig> {
        return dao.configFlow(characterId).map { entity -> configFromEntity(characterId, entity) }
    }

    fun load(characterId: String): VariableConfig {
        return configFromEntity(characterId, dao.config(characterId))
    }

    fun loadVersioned(characterId: String): VersionedVariableConfig =
        database.runInTransaction<VersionedVariableConfig> {
        val entity = dao.config(characterId)
        VersionedVariableConfig(
            config = configFromEntity(characterId, entity),
            revision = entity?.config?.revision ?: 0L,
        )
    }

    fun save(characterId: String, config: VariableConfig): VariableConfig =
        requireNotNull(saveInternal(characterId, config, expectedRevision = null)).config

    /** Atomically rejects a delayed Agent change set when its preview revision is stale. */
    fun saveIfRevision(
        characterId: String,
        config: VariableConfig,
        expectedRevision: Long,
    ): VersionedVariableConfig? = saveInternal(characterId, config, expectedRevision)

    private fun saveInternal(
        characterId: String,
        config: VariableConfig,
        expectedRevision: Long?,
    ): VersionedVariableConfig? {
        val ownerId = requireCharacterId(characterId)
        val now = nowIso()
        val activeVersionId = config.activeVersionId.ifBlank {
            config.versions.firstOrNull()?.id ?: "variable-config-${newId(8)}"
        }
        val previousActive = config.versions.firstOrNull { it.id == activeVersionId }
        val activeVersion = VariableConfigNormalizer.normalizeVersion(
            VariableConfigVersion(
                id = activeVersionId,
                name = config.name,
                initialStateJson = config.initialStateJson,
                schemaCode = config.schemaCode,
                objects = config.objects,
                variables = config.variables,
                expandedObjectIds = config.expandedObjectIds,
                createdAt = previousActive?.createdAt.orEmpty(),
                updatedAt = now,
            ),
            now = now,
            fallbackId = "variable-config-active",
        )
        val versions = (config.versions.filterNot { it.id == activeVersionId } + activeVersion)
            .mapIndexed { index, version ->
                VariableConfigNormalizer.normalizeVersion(
                    version = version,
                    now = version.updatedAt.ifBlank { now },
                    fallbackId = "variable-config-version-$index",
                )
            }
        val saved = activeConfig(ownerId, activeVersion, versions)
        return database.runInTransaction<VersionedVariableConfig?> {
            val currentRecord = dao.config(ownerId)
            val currentRevision = currentRecord?.config?.revision ?: 0L
            if (expectedRevision != null && currentRevision != expectedRevision) {
                return@runInTransaction null
            }
            val current = currentRecord?.let { configFromRecord(ownerId, it) }
            val persisted = saved.withStableItemTimestamps(current, now)
            if (current != null && current.samePersistedContentAs(persisted)) {
                return@runInTransaction VersionedVariableConfig(current, currentRevision)
            }
            val revision = currentRevision + 1L
            dao.upsert(VariableConfigJsonCodec.toRecord(persisted, now, revision))
            VersionedVariableConfig(persisted, revision)
        }
    }

    fun exportJson(characterId: String): String {
        return VariableConfigJsonCodec.encode(load(characterId), nowIso())
    }

    fun restoreExportJson(characterId: String, json: String): VariableConfig {
        val ownerId = requireCharacterId(characterId)
        val document = VariableConfigJsonCodec.decodeRestore(json)
        val active = document.versions
            .firstOrNull { it.id == document.requestedActiveVersionId }
            ?: document.versions.first()
        return save(
            ownerId,
            activeConfig(
                characterId = ownerId,
                active = active,
                versions = document.versions,
                resolveInitialState = false,
            ),
        )
    }

    fun importJson(characterId: String, json: String): VariableConfig {
        val ownerId = requireCharacterId(characterId)
        val current = load(ownerId)
        val importedId = "variable-config-${newId(8)}"
        val imported = VariableConfigJsonCodec.decodeImport(json).copy(id = importedId)
        return save(
            ownerId,
            activeConfig(
                characterId = ownerId,
                active = imported,
                versions = current.versions + imported,
                resolveInitialState = false,
            ),
        )
    }

    fun deleteForCharacters(characterIds: List<String>) {
        val ids = characterIds.filter { it.isNotBlank() }.distinct()
        if (ids.isNotEmpty()) dao.deleteForCharacters(ids)
    }

    fun deleteExceptCharacters(characterIds: List<String>) {
        val ids = characterIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) {
            dao.deleteAll()
        } else {
            dao.deleteExceptCharacters(ids)
        }
    }

    private fun configFromEntity(
        characterId: String,
        entity: VariableConfigRecord?,
    ): VariableConfig {
        val ownerId = requireCharacterId(characterId)
        return configFromRecord(ownerId, entity)
    }

    private fun configFromRecord(
        ownerId: String,
        entity: VariableConfigRecord?,
    ): VariableConfig {
        // Loading is a read. Reuse the persisted timestamp so unchanged data has a stable revision.
        val versions = entity
            ?.let(VariableConfigJsonCodec::versionsFromRecord)
            ?: listOf(VariableConfigNormalizer.emptyVersion())
        val activeVersionId = entity?.config?.activeVersionId ?: versions.first().id
        val activeVersion = versions.firstOrNull { it.id == activeVersionId }
            ?: throw ElecKoiDataException("当前变量版本不存在")
        return activeConfig(ownerId, activeVersion, versions)
    }

    private fun activeConfig(
        characterId: String,
        active: VariableConfigVersion,
        versions: List<VariableConfigVersion>,
        resolveInitialState: Boolean = true,
    ): VariableConfig {
        return VariableConfig(
            characterId = characterId,
            name = active.name,
            initialStateJson = if (resolveInitialState) {
                active.resolvedInitialStateJson()
            } else {
                active.initialStateJson
            },
            schemaCode = active.schemaCode,
            objects = active.objects,
            variables = active.variables,
            expandedObjectIds = active.expandedObjectIds,
            activeVersionId = active.id,
            versions = versions,
        )
    }

    private fun requireCharacterId(characterId: String): String {
        if (!characters.exists(characterId)) throw ElecKoiDataException("角色不存在")
        return characterId
    }
}

/** Modification timestamps describe a write; they must not turn an identical document into one. */
internal fun VariableConfig.samePersistedContentAs(other: VariableConfig): Boolean =
    withoutModificationTimes() == other.withoutModificationTimes()

private fun VariableConfig.withoutModificationTimes(): VariableConfig = copy(
    objects = objects.map { it.copy(updatedAt = "") },
    variables = variables.map { it.copy(updatedAt = "") },
    versions = versions.map { version ->
        version.copy(
            updatedAt = "",
            objects = version.objects.map { it.copy(updatedAt = "") },
            variables = version.variables.map { it.copy(updatedAt = "") },
        )
    },
)

/** Keep timestamps stable for untouched rows so one edited variable produces one changed child row. */
internal fun VariableConfig.withStableItemTimestamps(
    current: VariableConfig?,
    now: String,
): VariableConfig {
    if (current == null) return this
    val currentVersions = current.versions.associateBy(VariableConfigVersion::id)
    val stableVersions = versions.map { version ->
        val oldVersion = currentVersions[version.id] ?: return@map version
        val oldObjects = oldVersion.objects.associateBy { it.id }
        val oldVariables = oldVersion.variables.associateBy { it.id }
        version.copy(
            objects = version.objects.map { candidate ->
                candidate.withStableTimestamp(oldObjects[candidate.id], now)
            },
            variables = version.variables.map { candidate ->
                candidate.withStableTimestamp(oldVariables[candidate.id], now)
            },
        )
    }
    val active = stableVersions.firstOrNull { it.id == activeVersionId }
    return copy(
        objects = active?.objects ?: objects,
        variables = active?.variables ?: variables,
        versions = stableVersions,
    )
}

private fun VariableObjectConfig.withStableTimestamp(
    current: VariableObjectConfig?,
    now: String,
): VariableObjectConfig {
    if (current == null) return this
    val candidate = copy(createdAt = current.createdAt, updatedAt = current.updatedAt)
    return if (candidate == current) candidate else copy(createdAt = current.createdAt, updatedAt = now)
}

private fun VariableItemConfig.withStableTimestamp(
    current: VariableItemConfig?,
    now: String,
): VariableItemConfig {
    if (current == null) return this
    val candidate = copy(createdAt = current.createdAt, updatedAt = current.updatedAt)
    return if (candidate == current) candidate else copy(createdAt = current.createdAt, updatedAt = now)
}
