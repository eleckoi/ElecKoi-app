package com.eleckoi.android.foundation.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eleckoi.android.foundation.storage.room.agent.dao.AgentLedgerDao
import com.eleckoi.android.foundation.storage.room.agent.dao.GenerationAttemptDao
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentBranchEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentBranchTurnEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentContentPartEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentConversationDisplayCacheEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentConversationEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.ConversationSpeakerEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentResponseEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.AgentTurnEntity
import com.eleckoi.android.foundation.storage.room.agent.entity.GenerationAttemptEntity

/** Current clean-install Room schema authority. */
@Database(
    entities = [
        ChatSessionEntity::class,
        ChatSessionCharacterSnapshotEntity::class,
        ChatSessionModelSettingsEntity::class,
        ChatSessionVariableStateEntity::class,
        CharacterEntity::class,
        CharacterTextContentEntity::class,
        CharacterMetaEntity::class,
        UserProfileEntity::class,
        ModelConfigEntity::class,
        ModelConfigMetaEntity::class,
        VariableConfigEntity::class,
        VariableConfigVersionEntity::class,
        VariableConfigVersionContentEntity::class,
        VariableConfigObjectEntity::class,
        VariableConfigVariableEntity::class,
        GlobalRegexRuleEntity::class,
        CharacterRegexRuleEntity::class,
        RegexEnablementVersionEntity::class,
        RegexStateEntity::class,
        GlobalToolConfigEntity::class,
        CharacterToolConfigEntity::class,
        FrontendProjectEntity::class,
        CharacterFrontendSettingsEntity::class,
        CreatorWorkspaceEntity::class,
        CreatorWorkspaceFileEntity::class,
        CreatorWorkspaceCharacterRootEntity::class,
        CreatorWorkspaceConversationEntity::class,
        CleanupOperationEntity::class,
        AgentConversationEntity::class,
        AgentConversationDisplayCacheEntity::class,
        AgentBranchEntity::class,
        ConversationSpeakerEntity::class,
        AgentTurnEntity::class,
        AgentResponseEntity::class,
        AgentBranchTurnEntity::class,
        AgentContentPartEntity::class,
        GenerationAttemptEntity::class,
        SettingLibraryEntity::class,
        SettingEntryContentEntity::class,
        SettingLibraryEntryLinkEntity::class,
        SettingLibraryGroupEntity::class,
        SettingLibraryVersionEntity::class,
        SettingLibraryVersionEntryLinkEntity::class,
        SettingLibraryVersionGroupEntity::class,
        ConversationSettingChangeEntity::class,
        RoleplayRichHeightEntity::class,
        StoryPresetStateEntity::class,
        StoryPresetLibraryGroupEntity::class,
        StoryPresetEntity::class,
        StoryPresetContentEntity::class,
        StoryPresetEntryEntity::class,
        StoryPresetGroupEntity::class,
        StoryPresetRuntimeEntryEntity::class,
        StoryPresetVersionEntity::class,
        StoryPresetVersionContentEntity::class,
        StoryPresetVersionEntryEntity::class,
        StoryPresetVersionGroupEntity::class,
        StoryPresetVersionRuntimeEntryEntity::class,
    ],
    views = [SettingLibraryEntryEntity::class, SettingLibraryVersionEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ElecKoiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun characterDao(): CharacterDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun variableConfigDao(): VariableConfigDao
    abstract fun regexRuleDao(): RegexRuleDao
    abstract fun agentToolConfigDao(): AgentToolConfigDao
    abstract fun authorFrontendDao(): AuthorFrontendDao
    abstract fun creatorWorkspaceDao(): CreatorWorkspaceDao
    abstract fun cleanupOperationDao(): CleanupOperationDao
    abstract fun agentLedgerDao(): AgentLedgerDao
    abstract fun generationAttemptDao(): GenerationAttemptDao
    abstract fun settingLibraryDao(): SettingLibraryDao
    abstract fun conversationSettingChangeDao(): ConversationSettingChangeDao
    abstract fun roleplayRichHeightDao(): RoleplayRichHeightDao
    abstract fun storyPresetDao(): StoryPresetDao

    companion object {
        @Volatile
        private var instance: ElecKoiDatabase? = null

        fun get(context: Context): ElecKoiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ElecKoiDatabase::class.java,
                "eleckoi-dsh.db",
            )
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Scrub deleted b-tree cells without adding extra disk I/O for freelist pages.
                        db.query("PRAGMA secure_delete = FAST").use { cursor -> cursor.moveToFirst() }
                    }
                })
                .build()
                .also { instance = it }
        }
    }
}
