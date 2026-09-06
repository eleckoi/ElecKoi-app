package com.eleckoi.android.feature.characters.modes.story.presets.data

import com.eleckoi.android.feature.characters.modes.story.presets.model.DefaultStoryPresetId
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPreset
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetCatalog
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetModelTag
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetModelFamily
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetProfile
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetSummary
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetTimelineItem
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetImportDocument
import com.eleckoi.android.feature.characters.modes.story.presets.model.StoryPresetImportSource
import com.eleckoi.android.feature.characters.modes.story.presets.model.defaultStoryPreset
import com.eleckoi.android.feature.characters.modes.story.presets.model.withRequiredBuiltIns
import com.eleckoi.android.feature.characters.modes.story.presets.model.toTag
import com.eleckoi.android.feature.characters.modes.story.presets.data.importing.StoryPresetImportCoordinator
import com.eleckoi.android.feature.characters.modes.story.presets.data.library.StoryPresetLibraryGroupCoordinator
import com.eleckoi.android.feature.characters.modes.story.presets.data.media.StoryPresetAuthorAvatarStore
import com.eleckoi.android.feature.characters.modes.story.presets.data.policy.uniqueStoryPresetName
import com.eleckoi.android.feature.characters.modes.story.presets.data.storage.StoryPresetMetadataCodec
import com.eleckoi.android.feature.characters.modes.story.presets.data.storage.RegexRulesContentKind
import com.eleckoi.android.feature.characters.modes.story.presets.data.storage.toStorageRecord
import com.eleckoi.android.feature.characters.modes.story.presets.data.storage.toStoryPreset
import com.eleckoi.android.feature.characters.modes.story.presets.data.storage.toVersionRecord
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.HiddenToolTimelineEntryId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHiddenToolTimelineEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.HistoryCompactionEntryId
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isHistoryCompactionEntry
import com.eleckoi.android.feature.characters.modes.story.regex.data.normalizedRegexRules
import com.eleckoi.android.foundation.storage.room.StoryPresetDao
import com.eleckoi.android.foundation.storage.room.StoryPresetStateEntity
import com.eleckoi.android.foundation.storage.JsonFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Base64
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Application-scoped preset persistence.
 *
 * The manager observes metadata-only rows. Prompt payload rows are queried only for the preset being
 * edited or for the single globally active preset used by an Agent turn.
 */
class StoryPresetRepository(
    private val dao: StoryPresetDao,
    store: JsonFileStore,
    private val onActivePresetChanged: () -> Unit = {},
) {
    private val initializationMutex = Mutex()
    private val authorAvatars = StoryPresetAuthorAvatarStore(store)
    private val importer = StoryPresetImportCoordinator(dao, authorAvatars)
    private val libraryGroups = StoryPresetLibraryGroupCoordinator(dao)
    @Volatile
    private var initialized = false

    val catalog: Flow<StoryPresetCatalog> = combine(
        dao.stateFlow(),
        dao.libraryGroupsFlow(),
        dao.summariesFlow(),
    ) { state, groupRows, rows ->
        val groups = groupRows.map { StoryPresetLibraryGroup(it.id, it.name, it.sortIndex) }
        val knownGroupIds = groups.mapTo(mutableSetOf()) { it.id }
        val summaries = rows.map { row ->
            StoryPresetSummary(
                id = row.id,
                name = row.name,
                modelFamily = StoryPresetModelFamily.fromStorage(row.modelFamily),
                modelTags = StoryPresetMetadataCodec.decodeModelTags(row.modelTagsJson, row.modelFamily),
                libraryGroupId = row.libraryGroupId.takeIf(knownGroupIds::contains).orEmpty(),
                activeVersionId = row.activeVersionId,
                activeVersionNumber = row.activeVersionNumber,
                entryCount = row.entryCount,
                profile = StoryPresetMetadataCodec.decodeProfile(
                    authorName = row.authorName,
                    authorAvatarPath = row.authorAvatarPath,
                    authorTagsJson = row.authorTagsJson,
                    description = row.description,
                    timelineJson = row.timelineJson,
                ),
            )
        }
        StoryPresetCatalog(
            activePresetId = state?.activePresetId
                ?.takeIf { id -> summaries.any { it.id == id } }
                ?: summaries.firstOrNull()?.id
                ?: DefaultStoryPresetId,
            groups = groups,
            presets = summaries,
        )
    }

    suspend fun ensureInitialized() {
        if (initialized) return
        initializationMutex.withLock {
            if (initialized) return@withLock
            if (!dao.presetExists(DefaultStoryPresetId)) {
                val preset = defaultStoryPreset().copy(
                    activeVersionId = "$DefaultStoryPresetId:v1",
                    activeVersionNumber = 1,
                )
                val record = preset.toStorageRecord(sortIndex = 0)
                dao.replace(record)
                dao.replaceVersion(record.toVersionRecord(preset.activeVersionId, 1, preset.name, 0L))
            }
            val currentState = dao.state()
            val validActiveId = currentState?.activePresetId?.takeIf { dao.presetExists(it) }
                ?: dao.firstPresetId()
                ?: DefaultStoryPresetId
            if (currentState?.activePresetId != validActiveId) {
                dao.upsertState(StoryPresetStateEntity(activePresetId = validActiveId))
            }
            initialized = true
        }
    }

    suspend fun preset(presetId: String): StoryPreset? {
        ensureInitialized()
        return dao.preset(presetId)?.let { record ->
            record.toStoryPreset().copy(
                activeVersionNumber = dao.versionNumber(presetId, record.preset.activeVersionId) ?: 1,
            )
        }
    }

    suspend fun activePreset(): StoryPreset {
        ensureInitialized()
        val activeId = dao.state()?.activePresetId ?: DefaultStoryPresetId
        return preset(activeId) ?: defaultStoryPreset()
    }

    suspend fun setActive(presetId: String) {
        ensureInitialized()
        if (dao.state()?.activePresetId != presetId && dao.presetExists(presetId)) {
            dao.upsertState(StoryPresetStateEntity(activePresetId = presetId))
            onActivePresetChanged()
        }
    }

    suspend fun create(
        name: String,
        modelTags: List<StoryPresetModelTag>,
        libraryGroupId: String = "",
    ): StoryPreset {
        ensureInitialized()
        val id = "story-preset-${UUID.randomUUID()}"
        val versionId = "$id:v1"
        val normalizedTags = modelTags
            .map { it.copy(id = it.id.trim().lowercase(), label = it.label.trim()) }
            .filter { it.id.isNotBlank() && it.label.isNotBlank() }
            .distinctBy(StoryPresetModelTag::id)
            .take(8)
            .ifEmpty { listOf(StoryPresetModelFamily.General.toTag()) }
        val modelFamily = StoryPresetModelFamily.entries
            .firstOrNull { family -> normalizedTags.any { it.id == family.storageValue } }
            ?: StoryPresetModelFamily.Other
        val preset = StoryPreset(
            id = id,
            name = uniqueStoryPresetName(name.trim().ifBlank { "新预设" }, dao.presetNames()),
            modelFamily = modelFamily,
            modelTags = normalizedTags,
            libraryGroupId = libraryGroupId,
            activeVersionId = versionId,
            activeVersionNumber = 1,
        ).withRequiredBuiltIns()
        val record = preset.toStorageRecord(sortIndex = dao.nextSortIndex())
        dao.replace(record)
        dao.replaceVersion(record.toVersionRecord(versionId, 1, preset.name, System.currentTimeMillis()))
        return preset
    }

    suspend fun update(preset: StoryPreset) = update(previous = null, preset = preset)

    suspend fun update(previous: StoryPreset?, preset: StoryPreset) {
        ensureInitialized()
        val sortIndex = dao.presetSortIndex(preset.id) ?: return
        val activeVersionId = preset.activeVersionId.ifBlank {
            previous?.activeVersionId?.takeIf(String::isNotBlank)
                ?: dao.presetActiveVersionId(preset.id).orEmpty()
        }
        val normalized = preset.copy(activeVersionId = activeVersionId).normalizedForStorage()
        val record = normalized.toStorageRecord(sortIndex = sortIndex)
        val previousRecord = previous
            ?.takeIf { it.id == normalized.id }
            ?.normalizedForStorage()
            ?.toStorageRecord(sortIndex = sortIndex)
        val knownPreviousRecord = previousRecord ?: dao.preset(normalized.id)
        dao.replaceKnown(record, knownPreviousRecord)
        val versionId = normalized.activeVersionId.ifBlank {
            knownPreviousRecord?.preset?.activeVersionId.orEmpty()
        }
        val versionSummary = dao.versionSummaries(normalized.id).firstOrNull { it.id == versionId }
        val versionNumber = versionSummary?.number ?: normalized.activeVersionNumber.coerceAtLeast(1)
        val createdAt = versionSummary?.createdAtEpochMs ?: System.currentTimeMillis()
        val versionRecord = record.toVersionRecord(
            versionId = versionId,
            versionNumber = versionNumber,
            versionName = normalized.name,
            createdAtEpochMs = createdAt,
        )
        val previousVersionRecord = knownPreviousRecord
            ?.takeIf { it.preset.activeVersionId == versionId }
            ?.toVersionRecord(
                versionId = versionId,
                versionNumber = versionNumber,
                versionName = knownPreviousRecord.preset.name,
                createdAtEpochMs = createdAt,
            )
        if (previousVersionRecord == null) {
            dao.replaceVersion(versionRecord)
        } else {
            dao.replaceVersionKnown(versionRecord, previousVersionRecord)
        }
        if (
            dao.state()?.activePresetId == normalized.id &&
            knownPreviousRecord?.contents?.firstOrNull { it.kind == RegexRulesContentKind }?.content !=
                record.contents.firstOrNull { it.kind == RegexRulesContentKind }?.content
        ) {
            onActivePresetChanged()
        }
    }

    private fun StoryPreset.normalizedForStorage(): StoryPreset {
        val normalized = withRequiredBuiltIns()
        return normalized.copy(
            name = normalized.name.trim().take(60).ifBlank { "未命名预设" },
            modelTags = normalized.modelTags.distinctBy { it.id.trim().lowercase() }.take(8),
            expandedGroupIds = normalized.expandedGroupIds.distinct(),
            promptPositions = normalized.promptPositions
                .distinctBy { it.id }
                .mapIndexed { index, position -> position.copy(order = index + 1) },
            regexRules = normalized.regexRules.normalizedRegexRules(),
        )
    }

    suspend fun rename(presetId: String, name: String) {
        ensureInitialized()
        if (!dao.presetExists(presetId)) return
        val normalizedName = uniqueStoryPresetName(
            requested = name.trim().ifBlank { "未命名预设" },
            existingNames = dao.otherPresetNames(presetId),
        )
        dao.renamePreset(presetId, normalizedName)
    }

    suspend fun duplicate(presetId: String): StoryPreset? {
        ensureInitialized()
        val source = dao.preset(presetId)?.toStoryPreset() ?: return null
        val copyId = "story-preset-${System.currentTimeMillis()}"
        val copyVersionId = "$copyId:v1"
        val copiedGroups = source.groups.mapIndexed { index, group ->
            group.copy(id = "$copyId-group-$index", createdAt = "", updatedAt = "")
        }
        val groupIds = source.groups.mapIndexed { index, group -> group.id to copiedGroups[index].id }.toMap()
        val promptPositionIds = source.promptPositions.mapIndexed { index, position ->
            position.id to "$copyId-position-$index"
        }.toMap()
        val copy = source.copy(
            id = copyId,
            name = uniqueStoryPresetName("${source.name} 副本", dao.presetNames()),
            activeVersionId = copyVersionId,
            activeVersionNumber = 1,
            groups = copiedGroups.mapIndexed { index, group ->
                group.copy(parentId = groupIds[source.groups[index].parentId].orEmpty())
            },
            entries = source.entries.mapIndexed { index, entry ->
                entry.copy(
                    id = when {
                        entry.isHistoryCompactionEntry() -> HistoryCompactionEntryId
                        entry.isHiddenToolTimelineEntry() -> HiddenToolTimelineEntryId
                        else -> "$copyId-entry-$index"
                    },
                    groupId = groupIds[entry.groupId].orEmpty(),
                    createdAt = "",
                    updatedAt = "",
                    promptPositionId = promptPositionIds[entry.promptPositionId].orEmpty(),
                )
            },
            promptPositions = source.promptPositions.mapIndexed { index, position ->
                position.copy(
                    id = "$copyId-position-$index",
                    order = index + 1,
                    createdAt = "",
                    updatedAt = "",
                )
            },
            regexRules = source.regexRules.mapIndexed { index, rule ->
                rule.copy(id = "$copyId-regex-$index", order = index)
            }.normalizedRegexRules(),
            expandedGroupIds = source.expandedGroupIds.mapNotNull(groupIds::get),
        )
        val record = copy.toStorageRecord(sortIndex = dao.nextSortIndex())
        dao.replace(record)
        dao.replaceVersion(record.toVersionRecord(copyVersionId, 1, copy.name, System.currentTimeMillis()))
        return copy
    }

    suspend fun importPreset(source: StoryPreset, authorAvatarPng: ByteArray? = null): StoryPreset {
        ensureInitialized()
        return importer.import(source, authorAvatarPng)
    }

    /** Exports the complete preset catalog as a current, app-owned backup section. */
    suspend fun exportBackupJson(): String {
        ensureInitialized()
        val activeId = dao.state()?.activePresetId.orEmpty()
        val groups = dao.libraryGroups()
        val presets = dao.presetIds().mapNotNull { id ->
            preset(id)?.let { value ->
                val avatar = value.profile.authorAvatarPath
                    .takeIf(String::isNotBlank)
                    ?.let(::File)
                    ?.takeIf { it.isFile && it.length() <= 8L * 1024L * 1024L }
                    ?.readBytes()
                    ?.let(Base64.getEncoder()::encodeToString)
                JSONObject()
                    .put("source_id", value.id)
                    .put("library_group_id", value.libraryGroupId)
                    .put("author_avatar_base64", avatar ?: JSONObject.NULL)
                    .put("payload", JSONObject(StoryPresetImportCodec.encodeElecKoi(value)))
            }
        }
        return JSONObject()
            .put("format", "eleckoi.story-presets-backup")
            .put("version", 1)
            .put("active_source_id", activeId)
            .put(
                "groups",
                JSONArray(groups.map { group ->
                    JSONObject()
                        .put("id", group.id)
                        .put("name", group.name)
                        .put("sort_index", group.sortIndex)
                }),
            )
            .put("presets", JSONArray(presets))
            .toString(2)
    }

    /** Restores a complete catalog into a clean installation and selects its prior active preset. */
    suspend fun restoreBackupJson(json: String) {
        ensureInitialized()
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("故事预设备份已损坏", it) }
        require(root.optString("format") == "eleckoi.story-presets-backup") {
            "故事预设备份格式不正确"
        }
        require(root.optInt("version", -1) == 1) { "不支持的故事预设备份版本" }

        val groupIds = linkedMapOf<String, String>()
        val groupArray = root.optJSONArray("groups") ?: JSONArray()
        for (index in 0 until groupArray.length()) {
            val group = groupArray.optJSONObject(index) ?: continue
            val sourceId = group.optString("id").trim()
            val name = group.optString("name").trim()
            if (sourceId.isBlank() || name.isBlank()) continue
            val created = libraryGroups.create(name)
            groupIds[sourceId] = created.id
        }

        val importedIds = linkedMapOf<String, String>()
        val presetArray = root.optJSONArray("presets") ?: JSONArray()
        for (index in 0 until presetArray.length()) {
            val item = presetArray.optJSONObject(index) ?: continue
            val sourceId = item.optString("source_id").trim()
            val payload = item.optJSONObject("payload")?.toString().orEmpty()
            if (payload.isBlank()) continue
            val authorAvatar = decodeBackupAuthorAvatar(item)
            val conversion = StoryPresetImportCodec.decode(
                StoryPresetImportDocument(
                    fileName = "backup-preset-$index.json",
                    json = payload,
                ),
                StoryPresetImportSource.ElecKoi,
            )
            val imported = if (sourceId == DefaultStoryPresetId) {
                val current = defaultStoryPreset().copy(
                    id = DefaultStoryPresetId,
                    activeVersionId = "$DefaultStoryPresetId:v1",
                )
                update(conversion.preset.copy(id = current.id, activeVersionId = current.activeVersionId))
                preset(DefaultStoryPresetId) ?: current
            } else {
                importPreset(conversion.preset, authorAvatar)
            }
            importedIds[sourceId] = imported.id
            val targetGroup = groupIds[item.optString("library_group_id").trim()]
            if (targetGroup != null) moveToLibraryGroup(imported.id, targetGroup)
        }

        val activeSourceId = root.optString("active_source_id").trim()
        importedIds[activeSourceId]?.let { setActive(it) }
    }

    suspend fun createLibraryGroup(name: String): StoryPresetLibraryGroup {
        ensureInitialized()
        return libraryGroups.create(name)
    }

    suspend fun renameLibraryGroup(groupId: String, name: String) {
        ensureInitialized()
        libraryGroups.rename(groupId, name)
    }

    suspend fun updateProfile(presetId: String, profile: StoryPresetProfile) {
        ensureInitialized()
        if (!dao.presetExists(presetId)) return
        val normalized = profile.copy(
            authorName = profile.authorName.trim().take(40),
            tags = profile.tags.map(String::trim).filter(String::isNotBlank).distinct().take(8),
            description = profile.description.trim().take(1000),
            timeline = profile.timeline.mapNotNull { item ->
                val title = item.title.trim().take(80)
                if (title.isBlank()) null else item.copy(
                    id = item.id.ifBlank { "timeline-${UUID.randomUUID()}" },
                    title = title,
                    dateLabel = item.dateLabel.trim().take(24),
                    note = item.note.trim().take(800),
                )
            }.distinctBy(StoryPresetTimelineItem::id).take(100),
        )
        dao.updatePresetProfile(
            presetId = presetId,
            authorName = normalized.authorName,
            authorTagsJson = StoryPresetMetadataCodec.encodeStringList(normalized.tags),
            description = normalized.description,
            timelineJson = StoryPresetMetadataCodec.encodeTimeline(normalized.timeline),
        )
    }

    suspend fun updateModelTags(presetId: String, modelTags: List<StoryPresetModelTag>) {
        ensureInitialized()
        if (!dao.presetExists(presetId)) return
        val normalizedTags = modelTags
            .map { it.copy(id = it.id.trim().lowercase(), label = it.label.trim()) }
            .filter { it.id.isNotBlank() && it.label.isNotBlank() }
            .distinctBy(StoryPresetModelTag::id)
            .take(8)
            .ifEmpty { listOf(StoryPresetModelFamily.General.toTag()) }
        val modelFamily = StoryPresetModelFamily.entries
            .firstOrNull { family -> normalizedTags.any { it.id == family.storageValue } }
            ?: StoryPresetModelFamily.Other
        dao.updatePresetModelTags(
            presetId = presetId,
            modelFamily = modelFamily.storageValue,
            modelTagsJson = StoryPresetMetadataCodec.encodeModelTags(normalizedTags),
        )
    }

    suspend fun updateAuthorAvatar(presetId: String, source: File) {
        ensureInitialized()
        if (!dao.presetExists(presetId)) return
        val destination = authorAvatars.copyFrom(presetId, source) ?: return
        dao.updatePresetAuthorAvatar(presetId, destination.absolutePath)
    }

    suspend fun moveToLibraryGroup(presetId: String, groupId: String) {
        ensureInitialized()
        libraryGroups.movePreset(presetId, groupId)
    }

    suspend fun deleteLibraryGroup(groupId: String) {
        ensureInitialized()
        libraryGroups.delete(groupId)
    }

    suspend fun delete(presetId: String) {
        ensureInitialized()
        if (presetId == DefaultStoryPresetId || !dao.presetExists(presetId)) return
        val wasActive = dao.state()?.activePresetId == presetId
        dao.deletePreset(presetId)
        if (wasActive) {
            dao.upsertState(
                StoryPresetStateEntity(
                    activePresetId = dao.firstPresetId() ?: DefaultStoryPresetId,
                ),
            )
            onActivePresetChanged()
        }
    }

}

/** JSON null means that the source preset has no author avatar; only non-null payloads are decoded. */
internal fun decodeBackupAuthorAvatar(item: JSONObject): ByteArray? {
    val encoded = item.opt("author_avatar_base64")
        ?.takeUnless { it == JSONObject.NULL }
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    return runCatching { Base64.getDecoder().decode(encoded) }
        .getOrElse { throw IllegalArgumentException("预设作者头像已损坏", it) }
}
