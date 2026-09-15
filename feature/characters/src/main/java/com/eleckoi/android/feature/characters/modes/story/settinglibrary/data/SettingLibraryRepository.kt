package com.eleckoi.android.feature.characters.modes.story.settinglibrary.data

import com.eleckoi.android.feature.characters.data.CharacterRepository
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibrary
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.data.importing.SettingLibraryImportCodec
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryAgentReadStrategy
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryTriggerMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryVersion
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isFixedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryOpeningEntry
import com.eleckoi.android.foundation.storage.ElecKoiDataException
import com.eleckoi.android.foundation.storage.newId
import com.eleckoi.android.foundation.storage.nowIso
import com.eleckoi.android.foundation.storage.room.ConversationSettingChangeDao
import com.eleckoi.android.foundation.storage.room.ConversationSettingChangeEntity
import com.eleckoi.android.foundation.storage.room.ElecKoiDatabase
import com.eleckoi.android.foundation.storage.room.SettingLibraryDao
import com.eleckoi.android.foundation.storage.room.SettingLibraryRecord
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Room-native setting library. Creator workspaces are never part of normal reads or writes.
 */
class SettingLibraryRepository internal constructor(
    private val dao: SettingLibraryDao,
    private val conversationChanges: ConversationSettingChangeDao,
    private val characterById: (String) -> CharacterSlot?,
    private val runInTransaction: (() -> Unit) -> Unit = { it() },
) {
    constructor(
        database: ElecKoiDatabase,
        characters: CharacterRepository,
    ) : this(
        dao = database.settingLibraryDao(),
        conversationChanges = database.conversationSettingChangeDao(),
        characterById = characters::characterById,
        runInTransaction = { block -> database.runInTransaction(block) },
    )

    fun libraryFlow(characterId: String): Flow<SettingLibrary> = flow {
        val initial = load(characterId)
        emit(initial)
        emitAll(
            dao.libraryFlow(characterId).map { entity ->
                entity?.let(::libraryFromEntity) ?: initial
            },
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    fun load(characterId: String): SettingLibrary {
        requireCharacter(characterId)
        return dao.library(characterId)?.let(::libraryFromEntity) ?: emptyLibrary(characterId)
    }

    /** Metadata/count query only; it does not materialize entry, group, or version rows. */
    fun rowMetadata(characterId: String): SettingLibraryRowMetadata {
        requireCharacter(characterId)
        val metadata = dao.metadata(characterId)
        val promptPositions = metadata?.promptPositionsJson
            ?.let { encoded ->
                runCatching {
                    org.json.JSONArray(encoded).let { array ->
                        List(array.length()) { index ->
                            SettingLibraryJsonCodec.promptPositionFromJson(index, array.getJSONObject(index))
                        }
                    }
                }.getOrDefault(emptyList())
            }
            .orEmpty()
        return SettingLibraryRowMetadata(
            characterId = characterId,
            name = metadata?.name.orEmpty(),
            updatedAt = metadata?.updatedAt.orEmpty(),
            entryCount = dao.entryCount(characterId),
            groupCount = dao.groupCount(characterId),
            promptPositions = promptPositions,
        )
    }

    /** Keyset page over independent Room rows. At most [limit] + 1 rows are read. */
    fun entryRows(
        characterId: String,
        query: String,
        afterSortIndex: Int,
        afterId: String,
        limit: Int,
    ): List<SettingLibraryEntryRow> {
        requireCharacter(characterId)
        return dao.entryPage(
            characterId = characterId,
            query = query.toSqlLikePattern(),
            afterSortIndex = afterSortIndex,
            afterId = afterId,
            limit = limit.coerceIn(1, 51),
        ).map { row ->
            SettingLibraryEntryRow(
                sortIndex = row.sortIndex,
                entry = SettingLibraryJsonCodec.entryFromJson(JSONObject(row.payloadJson)),
            )
        }
    }

    fun groupRows(
        characterId: String,
        query: String,
        afterSortIndex: Int,
        afterId: String,
        limit: Int,
    ): List<SettingLibraryGroupRow> {
        requireCharacter(characterId)
        return dao.groupPage(
            characterId = characterId,
            query = query.toSqlLikePattern(),
            afterSortIndex = afterSortIndex,
            afterId = afterId,
            limit = limit.coerceIn(1, 51),
        ).map { row ->
            SettingLibraryGroupRow(
                sortIndex = row.sortIndex,
                group = SettingLibraryJsonCodec.groupFromJson(row.sortIndex, JSONObject(row.payloadJson)),
            )
        }
    }

    fun entryRow(characterId: String, entryId: String): SettingLibraryEntry? {
        requireCharacter(characterId)
        val row = dao.entry(characterId, entryId.trim()) ?: return null
        return SettingLibraryJsonCodec.entryFromJson(JSONObject(row.payloadJson))
    }

    fun loadEffective(characterId: String, sessionId: String): SettingLibrary {
        val base = load(characterId)
        if (sessionId.isBlank()) return base
        return SettingLibraryConversationOverlay.merge(base, conversationChanges.changes(sessionId))
    }

    fun conversationLibraries(characterId: String): Map<String, SettingLibrary> {
        val base = load(characterId)
        return conversationChanges.changesForCharacter(characterId)
            .groupBy(ConversationSettingChangeEntity::sessionId)
            .mapValues { (_, changes) ->
                SettingLibraryConversationOverlay.merge(base, changes).copy(versions = emptyList())
            }
    }

    fun saveConversationAsVersion(
        characterId: String,
        sessionId: String,
        name: String,
    ): SettingLibrary {
        requireCharacter(characterId)
        val normalizedName = name.trim().take(60)
        if (normalizedName.isBlank()) throw ElecKoiDataException("请输入版本名称")

        val characterChanges = conversationChanges.changesForCharacter(characterId)
        if (characterChanges.none { it.sessionId == sessionId }) {
            throw ElecKoiDataException("找不到这条对话的动态设定")
        }

        val current = load(characterId)
        if (current.versions.any { it.name.trim() == normalizedName }) {
            throw ElecKoiDataException("版本名称已存在，请换一个名称")
        }
        val effective = SettingLibraryConversationOverlay.merge(
            current,
            characterChanges.filter { it.sessionId == sessionId },
        )
        val now = nowIso()
        val version = SettingLibraryVersion(
            id = "library-${newId(8)}",
            name = normalizedName,
            entries = effective.entries,
            groups = effective.groups,
            listAllExpanded = effective.listAllExpanded,
            expandedGroupIds = effective.expandedGroupIds,
            createdAt = now,
            updatedAt = now,
        )
        return save(
            characterId,
            current.copy(versions = current.versions + version),
        )
    }

    fun deleteConversationChanges(
        characterId: String,
        sessionId: String,
    ) {
        requireCharacter(characterId)
        if (sessionId.isBlank()) throw ElecKoiDataException("当前对话不存在，不能删除动态设定")
        val belongsToCharacter = conversationChanges.changesForCharacter(characterId)
            .any { change -> change.sessionId == sessionId }
        if (!belongsToCharacter) throw ElecKoiDataException("找不到这条对话的动态设定")
        conversationChanges.deleteForSession(sessionId)
    }

    fun applySessionMutations(
        characterId: String,
        sessionId: String,
        mutations: List<SettingLibrarySessionMutation>,
    ): SettingLibrarySessionMutationResult {
        requireCharacter(characterId)
        return applySettingLibrarySessionMutations(
            sessionId = sessionId,
            mutations = mutations,
            base = load(characterId),
            persisted = conversationChanges.changes(sessionId),
            upsertChanges = conversationChanges::upsertChanges,
        )
    }

    /**
     * Builds the hot-path view for one Agent turn.
     *
     * AI-selected entries are exposed only through request-scoped setting-domain tools. Stable
     * entry IDs, rather than paths, identify the authoritative Room rows for this turn.
     */
    suspend fun loadAgentTurnContext(
        characterId: String,
        sessionId: String = "",
        additionalLibrary: SettingLibrary? = null,
    ): SettingLibraryAgentTurnContext {
        val characterLibrary = loadEffective(characterId, sessionId)
        val library = additionalLibrary?.let { additional ->
            characterLibrary.copy(
                entries = characterLibrary.entries + additional.entries,
                groups = characterLibrary.groups + additional.groups,
            )
        } ?: characterLibrary
        return SettingLibraryAgentContextProjector.project(characterId, library)
    }

    fun save(characterId: String, library: SettingLibrary): SettingLibrary {
        requireCharacter(characterId)
        lateinit var saved: SettingLibrary
        runInTransaction {
            val currentRecord = dao.library(characterId)
            val previous = currentRecord?.let(SettingLibraryJsonCodec::fromEntity)
            val now = nowIso()
            saved = settleSettingLibraryChangeTimestamps(
                previous = previous,
                normalized = SettingLibraryNormalizer.normalize(characterId, library),
                now = now,
            )
            val metadataUpdatedAt = currentRecord?.library?.updatedAt
                ?.takeIf { saved == previous }
                ?: now
            dao.upsert(SettingLibraryJsonCodec.toEntity(saved, metadataUpdatedAt))
        }
        return saved
    }

    fun deleteForCharacters(characterIds: List<String>) {
        val ids = characterIds.filter(String::isNotBlank).distinct()
        if (ids.isNotEmpty()) dao.deleteForCharacters(ids)
    }

    fun deleteExceptCharacters(characterIds: List<String>) {
        val retained = characterIds.filter(String::isNotBlank).distinct()
        if (retained.isEmpty()) dao.deleteAll() else dao.deleteExceptCharacters(retained)
    }

    fun exportJson(characterId: String): String {
        return SettingLibraryJsonCodec.exportLibrary(load(characterId))
    }

    fun exportSnapshotJson(characterId: String): String {
        val library = load(characterId)
        return SettingLibraryJsonCodec.exportSnapshot(library)
    }

    fun restoreSnapshotJson(characterId: String, json: String): SettingLibrary {
        requireCharacter(characterId)
        val snapshot = SettingLibraryJsonCodec.parseSnapshot(json)
        val versions = snapshot.versions
        if (versions.isEmpty()) return load(characterId)
        val activeVersionId = snapshot.activeVersionId
        val active = versions.first { it.id == activeVersionId }
        return save(
            characterId,
            SettingLibrary(
                characterId = characterId,
                name = active.name,
                entries = active.entries,
                groups = active.groups,
                activeVersionId = activeVersionId,
                versions = versions,
                listAllExpanded = active.listAllExpanded,
                expandedGroupIds = active.expandedGroupIds,
            ),
        )
    }

    /**
     * Reads an exported library into memory without touching any character's workspace.
     *
     * [importJson] lands a file as a whole new version; merging a few entries out of one needs the
     * same parse but no write, so the parse lives here on its own and both callers share it.
     */
    fun parseJson(json: String): SettingLibraryVersion {
        return SettingLibraryImportCodec.parse(json, versionId = "library-${newId(8)}")
    }

    fun importJson(characterId: String, json: String): SettingLibrary {
        requireCharacter(characterId)
        val current = load(characterId)
        val importedVersion = parseJson(json)
        return save(
            characterId,
            current.copy(
                name = importedVersion.name,
                entries = importedVersion.entries,
                groups = importedVersion.groups,
                promptPositions = importedVersion.promptPositions,
                activeVersionId = importedVersion.id,
                versions = current.versions + importedVersion,
                listAllExpanded = importedVersion.listAllExpanded,
                expandedGroupIds = importedVersion.expandedGroupIds,
            ),
        )
    }


    private fun requireCharacter(characterId: String) = characterById(characterId)
        ?: throw ElecKoiDataException("角色不存在")

    private fun emptyLibrary(characterId: String): SettingLibrary {
        return SettingLibraryNormalizer.normalize(characterId, SettingLibrary(characterId = characterId))
    }

    private fun libraryFromEntity(entity: SettingLibraryRecord): SettingLibrary =
        SettingLibraryJsonCodec.fromEntity(entity)

}

private fun String.toSqlLikePattern(): String {
    val value = trim().take(120)
    if (value.isBlank()) return ""
    val escaped = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}
