package com.eleckoi.android.foundation.storage.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object ElecKoiDatabaseMigrations {
    internal const val DefaultToolConfiguration =
        """{"version":4,"includedGroupIds":["builtin:variables","builtin:setting-library"],"enabledGroupIds":["builtin:variables","builtin:setting-library"],"subagentModelSelection":{"configId":"","model":""},"roleplayPlan":{"steps":["必须先并行调用工具调研阅读设定，这里不扮演回复，禁止未阅读设定直接回复","等前置任务都完成，直接输出 <FINAL> 正文，不要再次调用 update_roleplay_plan；应用检测到正文后会自动完成最终项的标记。"]},"toolModelConfigIds":{}}"""

    val version1To2Statements: List<String> = buildList {
        val chatDependentTables = listOf(
            "chat_session_character_snapshots",
            "chat_session_variable_states",
            "conversation_setting_changes",
            "roleplay_rich_heights",
        )
        val characterDependentTables = listOf(
            "character_text_contents",
            "variable_configs",
            "variable_config_versions",
            "variable_config_version_contents",
            "variable_config_objects",
            "variable_config_variables",
            "character_regex_rules",
            "frontend_projects",
            "character_frontend_settings",
            "setting_libraries",
            "setting_entry_contents",
            "setting_library_entry_links",
            "setting_library_groups",
            "setting_library_versions",
            "setting_library_version_entry_links",
            "setting_library_version_groups",
        )
        val creatorWorkspaceDependentTables = listOf(
            "creator_workspace_files",
            "creator_workspace_character_roots",
            "creator_workspace_conversations",
        )
        val preservedTables = chatDependentTables + characterDependentTables + creatorWorkspaceDependentTables

        add("PRAGMA foreign_keys = OFF")
        add("PRAGMA defer_foreign_keys = ON")
        add("DROP TABLE IF EXISTS `chat_session_model_settings`")
        add("DELETE FROM `character_text_contents` WHERE `kind` = 'assistant_prompt'")
        add("DROP TABLE IF EXISTS `global_tool_config`")
        add("DROP TABLE IF EXISTS `character_tool_configs`")
        add(
            "DELETE FROM `conversation_setting_changes` WHERE `targetType` = 'entry' " +
                "AND `targetId` IN ('${LegacyAgentPresetMigration.RoleplayPlanEntryId}', " +
                "'${LegacyAgentPresetMigration.DshHarnessIdentityEntryId}')",
        )
        add(
            "DELETE FROM `setting_library_version_entry_links` WHERE `entryId` IN " +
                "('${LegacyAgentPresetMigration.RoleplayPlanEntryId}', " +
                "'${LegacyAgentPresetMigration.DshHarnessIdentityEntryId}')",
        )
        add(
            "DELETE FROM `setting_library_entry_links` WHERE `entryId` IN " +
                "('${LegacyAgentPresetMigration.RoleplayPlanEntryId}', " +
                "'${LegacyAgentPresetMigration.DshHarnessIdentityEntryId}')",
        )
        add(
            "DELETE FROM `setting_entry_contents` WHERE `entryId` IN " +
                "('${LegacyAgentPresetMigration.RoleplayPlanEntryId}', " +
                "'${LegacyAgentPresetMigration.DshHarnessIdentityEntryId}')",
        )
        preservedTables.forEach { table ->
            add("CREATE TEMP TABLE `__v1_backup_$table` AS SELECT * FROM `$table`")
        }

        add(
            """
                CREATE TABLE `chat_sessions_v2` (
                    `id` TEXT NOT NULL, `workspaceId` TEXT NOT NULL, `title` TEXT NOT NULL,
                    `characterId` TEXT NOT NULL, `characterName` TEXT NOT NULL,
                    `characterAvatar` TEXT NOT NULL, `permissionMode` TEXT NOT NULL,
                    `historySummary` TEXT NOT NULL, `historyMessageCount` INTEGER NOT NULL,
                    `historyUserMessageCount` INTEGER NOT NULL, `createdAt` TEXT NOT NULL,
                    `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`)
                )
            """.trimIndent(),
        )
        add(
            """
                INSERT INTO `chat_sessions_v2` (
                    `id`, `workspaceId`, `title`, `characterId`, `characterName`, `characterAvatar`,
                    `permissionMode`, `historySummary`, `historyMessageCount`,
                    `historyUserMessageCount`, `createdAt`, `updatedAt`
                )
                SELECT `id`, `workspaceId`, `title`, `characterId`, `characterName`, `characterAvatar`,
                    `permissionMode`, `historySummary`, `historyMessageCount`,
                    `historyUserMessageCount`, `createdAt`, `updatedAt`
                FROM `chat_sessions`
            """.trimIndent(),
        )
        add("DROP TABLE `chat_sessions`")
        add("ALTER TABLE `chat_sessions_v2` RENAME TO `chat_sessions`")
        add("CREATE INDEX `index_chat_sessions_characterId` ON `chat_sessions` (`characterId`)")
        add("CREATE INDEX `index_chat_sessions_updatedAt` ON `chat_sessions` (`updatedAt`)")
        chatDependentTables.forEach { table ->
            add("DELETE FROM `$table`")
            add("INSERT INTO `$table` SELECT * FROM `__v1_backup_$table`")
        }

        add(
            """
                CREATE TABLE `characters_v2` (
                    `id` TEXT NOT NULL, `name` TEXT NOT NULL, `avatar` TEXT NOT NULL,
                    `squareImage` TEXT NOT NULL, `coverImage` TEXT NOT NULL, `groupName` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL, `groupViewOrder` INTEGER NOT NULL, `folder` TEXT NOT NULL,
                    `frontendBeautyEnabled` INTEGER NOT NULL, `assistantName` TEXT NOT NULL,
                    `assistantAvatar` TEXT NOT NULL, `profileAge` TEXT NOT NULL, `profileSex` TEXT NOT NULL,
                    `profileHeight` TEXT NOT NULL, `profileBirthday` TEXT NOT NULL, `profileLike` TEXT NOT NULL,
                    `showOpening` INTEGER NOT NULL, `chatBackground` TEXT NOT NULL,
                    `chatBackgroundOpacity` REAL NOT NULL, `chatBackgroundBlur` REAL NOT NULL,
                    `chatBackgroundScrim` REAL NOT NULL, PRIMARY KEY(`id`)
                )
            """.trimIndent(),
        )
        add(
            """
                INSERT INTO `characters_v2` (
                    `id`, `name`, `avatar`, `squareImage`, `coverImage`, `groupName`, `orderIndex`,
                    `groupViewOrder`, `folder`, `frontendBeautyEnabled`, `assistantName`, `assistantAvatar`,
                    `profileAge`, `profileSex`, `profileHeight`, `profileBirthday`, `profileLike`,
                    `showOpening`, `chatBackground`, `chatBackgroundOpacity`, `chatBackgroundBlur`,
                    `chatBackgroundScrim`
                )
                SELECT `id`, `name`, `avatar`, `squareImage`, `coverImage`, `groupName`, `orderIndex`,
                    `groupViewOrder`, `folder`, `frontendBeautyEnabled`, `assistantName`, `assistantAvatar`,
                    `profileAge`, `profileSex`, `profileHeight`, `profileBirthday`, `profileLike`,
                    `showOpening`, `chatBackground`, `chatBackgroundOpacity`, `chatBackgroundBlur`,
                    `chatBackgroundScrim`
                FROM `characters`
            """.trimIndent(),
        )
        add("DROP TABLE `characters`")
        add("ALTER TABLE `characters_v2` RENAME TO `characters`")
        characterDependentTables.forEach { table ->
            add("DELETE FROM `$table`")
            add("INSERT INTO `$table` SELECT * FROM `__v1_backup_$table`")
        }

        add(
            """
                CREATE TABLE `creator_workspaces_v2` (
                    `id` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, `name` TEXT NOT NULL,
                    `linkedCharacterId` TEXT, `characterOwned` INTEGER NOT NULL,
                    `primaryCharacterRootId` TEXT, `previewEntryFile` TEXT, `createdAt` TEXT NOT NULL,
                    `updatedAt` TEXT NOT NULL, `totalBytes` INTEGER NOT NULL, `latestCheckpointId` TEXT,
                    `permissionMode` TEXT NOT NULL, `activeConversationId` TEXT, PRIMARY KEY(`id`),
                    FOREIGN KEY(`linkedCharacterId`) REFERENCES `characters`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
            """.trimIndent(),
        )
        add(
            """
                INSERT INTO `creator_workspaces_v2` (
                    `id`, `schemaVersion`, `name`, `linkedCharacterId`, `characterOwned`,
                    `primaryCharacterRootId`, `previewEntryFile`, `createdAt`, `updatedAt`, `totalBytes`,
                    `latestCheckpointId`, `permissionMode`, `activeConversationId`
                )
                SELECT `id`, `schemaVersion`, `name`, `linkedCharacterId`,
                    CASE WHEN `linkedCharacterId` IS NULL THEN 0 ELSE 1 END,
                    `primaryCharacterRootId`, `previewEntryFile`, `createdAt`, `updatedAt`, `totalBytes`,
                    `latestCheckpointId`, `permissionMode`, `activeConversationId`
                FROM `creator_workspaces`
            """.trimIndent(),
        )
        add("DROP TABLE `creator_workspaces`")
        add("ALTER TABLE `creator_workspaces_v2` RENAME TO `creator_workspaces`")
        add("CREATE INDEX `index_creator_workspaces_linkedCharacterId` ON `creator_workspaces` (`linkedCharacterId`)")
        add("CREATE INDEX `index_creator_workspaces_updatedAt` ON `creator_workspaces` (`updatedAt`)")
        creatorWorkspaceDependentTables.forEach { table ->
            add("DELETE FROM `$table`")
            add("INSERT INTO `$table` SELECT * FROM `__v1_backup_$table`")
        }

        add(
            "CREATE TEMP TABLE `__v1_backup_agent_branch_turns` AS " +
                "SELECT `branchId`, `sequence`, `turnId` FROM `agent_branch_turns`",
        )
        add(
            """
                CREATE TABLE `agent_branches_v2` (
                    `id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent(),
        )
        add("INSERT INTO `agent_branches_v2` (`id`, `conversationId`) SELECT `id`, `conversationId` FROM `agent_branches`")
        add("DROP TABLE `agent_branch_turns`")
        add("DROP TABLE `agent_branches`")
        add("ALTER TABLE `agent_branches_v2` RENAME TO `agent_branches`")
        add(
            """
                CREATE TABLE `agent_branch_turns` (
                    `branchId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `turnId` TEXT NOT NULL,
                    PRIMARY KEY(`branchId`, `sequence`),
                    FOREIGN KEY(`branchId`) REFERENCES `agent_branches`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`turnId`) REFERENCES `agent_turns`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent(),
        )
        add(
            "INSERT INTO `agent_branch_turns` (`branchId`, `sequence`, `turnId`) " +
                "SELECT `branchId`, `sequence`, `turnId` FROM `__v1_backup_agent_branch_turns`",
        )
        add("DROP TABLE `__v1_backup_agent_branch_turns`")
        add("CREATE INDEX `index_agent_branches_conversationId` ON `agent_branches` (`conversationId`)")
        add("CREATE UNIQUE INDEX `index_agent_branch_turns_branchId_turnId` ON `agent_branch_turns` (`branchId`, `turnId`)")
        add("CREATE INDEX `index_agent_branch_turns_turnId` ON `agent_branch_turns` (`turnId`)")

        add(
            """
                CREATE TABLE `agent_conversation_display_cache_v2` (
                    `conversationId` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL,
                    `ledgerRevision` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL,
                    `rendererVersion` INTEGER NOT NULL, PRIMARY KEY(`conversationId`, `chunkIndex`),
                    FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent(),
        )
        add(
            """
                INSERT INTO `agent_conversation_display_cache_v2` (
                    `conversationId`, `chunkIndex`, `ledgerRevision`, `payloadJson`, `rendererVersion`
                ) SELECT `conversationId`, `chunkIndex`, `ledgerRevision`, `payloadJson`, `rendererVersion`
                FROM `agent_conversation_display_cache`
            """.trimIndent(),
        )
        add("DROP TABLE `agent_conversation_display_cache`")
        add("ALTER TABLE `agent_conversation_display_cache_v2` RENAME TO `agent_conversation_display_cache`")

        add(
            """
                CREATE TABLE `roleplay_rich_heights_v2` (
                    `sessionId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `contentRevision` TEXT NOT NULL,
                    `rootIndex` INTEGER NOT NULL, `viewportWidthPx` INTEGER NOT NULL, `heightPx` INTEGER NOT NULL,
                    PRIMARY KEY(`sessionId`, `messageId`, `contentRevision`, `rootIndex`, `viewportWidthPx`),
                    FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent(),
        )
        add(
            """
                INSERT INTO `roleplay_rich_heights_v2` (
                    `sessionId`, `messageId`, `contentRevision`, `rootIndex`, `viewportWidthPx`, `heightPx`
                ) SELECT `sessionId`, `messageId`, `contentRevision`, `rootIndex`, `viewportWidthPx`, `heightPx`
                FROM `roleplay_rich_heights`
            """.trimIndent(),
        )
        add("DROP TABLE `roleplay_rich_heights`")
        add("ALTER TABLE `roleplay_rich_heights_v2` RENAME TO `roleplay_rich_heights`")
        add("CREATE INDEX `index_roleplay_rich_heights_sessionId` ON `roleplay_rich_heights` (`sessionId`)")

        add(
            """
                CREATE TABLE `model_configs_v2` (
                    `id` TEXT NOT NULL, `name` TEXT NOT NULL, `provider` TEXT NOT NULL,
                    `apiKey` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `proxyUrl` TEXT NOT NULL,
                    `model` TEXT NOT NULL, `modelOptionsJson` TEXT NOT NULL,
                    `customHeadersJson` TEXT NOT NULL, `enabled` INTEGER NOT NULL,
                    `imageSettingsJson` TEXT NOT NULL, `apiFormat` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent(),
        )
        add(
            """
                INSERT INTO `model_configs_v2` (
                    `id`, `name`, `provider`, `apiKey`, `baseUrl`, `proxyUrl`, `model`,
                    `modelOptionsJson`, `customHeadersJson`, `enabled`, `imageSettingsJson`, `apiFormat`
                )
                SELECT `id`, `name`, `provider`, `apiKey`, `baseUrl`, `proxyUrl`, `model`,
                    `modelOptionsJson`, `customHeadersJson`, `enabled`, `imageSettingsJson`, `apiFormat`
                FROM `model_configs`
            """.trimIndent(),
        )
        add("DROP TABLE `model_configs`")
        add("ALTER TABLE `model_configs_v2` RENAME TO `model_configs`")

        addAll(agentPresetMigrationStatements())
        preservedTables.forEach { table ->
            add("DROP TABLE `__v1_backup_$table`")
        }
        add("PRAGMA defer_foreign_keys = OFF")
        add("PRAGMA foreign_keys = ON")
    }

    private fun agentPresetMigrationStatements(): List<String> = listOf(
        "CREATE TABLE `agent_preset_state` (`singletonId` INTEGER NOT NULL, `activePresetId` TEXT NOT NULL, PRIMARY KEY(`singletonId`))",
        "CREATE TABLE `agent_preset_library_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        """
            CREATE TABLE `agent_presets` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `modelFamily` TEXT NOT NULL,
                `modelTagsJson` TEXT NOT NULL DEFAULT '[]',
                `libraryGroupId` TEXT NOT NULL DEFAULT 'agent-preset-group-default',
                `activeVersionId` TEXT NOT NULL DEFAULT '', `authorName` TEXT NOT NULL DEFAULT '',
                `authorAvatarPath` TEXT NOT NULL DEFAULT '', `sortIndex` INTEGER NOT NULL,
                `expandedGroupIdsJson` TEXT NOT NULL, PRIMARY KEY(`id`)
            )
        """.trimIndent(),
        """
            CREATE TABLE `agent_preset_contents` (
                `presetId` TEXT NOT NULL, `kind` TEXT NOT NULL, `content` TEXT NOT NULL,
                PRIMARY KEY(`presetId`, `kind`), FOREIGN KEY(`presetId`) REFERENCES `agent_presets`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent(),
        """
            CREATE TABLE `agent_preset_entries` (
                `presetId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL,
                `payloadJson` TEXT NOT NULL, PRIMARY KEY(`presetId`, `entryId`),
                FOREIGN KEY(`presetId`) REFERENCES `agent_presets`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent(),
        """
            CREATE TABLE `agent_preset_groups` (
                `presetId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL,
                `payloadJson` TEXT NOT NULL, PRIMARY KEY(`presetId`, `groupId`),
                FOREIGN KEY(`presetId`) REFERENCES `agent_presets`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent(),
        """
            CREATE TABLE `agent_preset_versions` (
                `presetId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `versionNumber` INTEGER NOT NULL,
                `name` TEXT NOT NULL DEFAULT '', `createdAtEpochMs` INTEGER NOT NULL,
                `expandedGroupIdsJson` TEXT NOT NULL, PRIMARY KEY(`presetId`, `versionId`),
                FOREIGN KEY(`presetId`) REFERENCES `agent_presets`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent(),
        """
            CREATE TABLE `agent_preset_version_contents` (
                `presetId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `kind` TEXT NOT NULL,
                `content` TEXT NOT NULL, PRIMARY KEY(`presetId`, `versionId`, `kind`),
                FOREIGN KEY(`presetId`, `versionId`) REFERENCES `agent_preset_versions`(`presetId`, `versionId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent(),
        """
            CREATE TABLE `agent_preset_version_entries` (
                `presetId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `entryId` TEXT NOT NULL,
                `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL,
                PRIMARY KEY(`presetId`, `versionId`, `entryId`),
                FOREIGN KEY(`presetId`, `versionId`) REFERENCES `agent_preset_versions`(`presetId`, `versionId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent(),
        """
            CREATE TABLE `agent_preset_version_groups` (
                `presetId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `groupId` TEXT NOT NULL,
                `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL,
                PRIMARY KEY(`presetId`, `versionId`, `groupId`),
                FOREIGN KEY(`presetId`, `versionId`) REFERENCES `agent_preset_versions`(`presetId`, `versionId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent(),
        """
            INSERT INTO `agent_preset_state` (`singletonId`, `activePresetId`)
            SELECT `singletonId`, CASE WHEN `activePresetId` LIKE 'story-preset-%'
                THEN 'agent-preset-' || SUBSTR(`activePresetId`, 14) ELSE `activePresetId` END
            FROM `story_preset_state`
        """.trimIndent(),
        """
            INSERT INTO `agent_preset_library_groups` (`id`, `name`, `sortIndex`)
            SELECT CASE WHEN `id` LIKE 'story-preset-group-%'
                THEN 'agent-preset-group-' || SUBSTR(`id`, 20) ELSE `id` END, `name`, `sortIndex`
            FROM `story_preset_library_groups`
        """.trimIndent(),
        """
            INSERT INTO `agent_presets` (
                `id`, `name`, `modelFamily`, `modelTagsJson`, `libraryGroupId`, `activeVersionId`,
                `authorName`, `authorAvatarPath`, `sortIndex`, `expandedGroupIdsJson`
            )
            SELECT CASE WHEN `id` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`id`, 14) ELSE `id` END,
                `name`, `modelFamily`, `modelTagsJson`,
                CASE WHEN `libraryGroupId` LIKE 'story-preset-group-%'
                    THEN 'agent-preset-group-' || SUBSTR(`libraryGroupId`, 20) ELSE `libraryGroupId` END,
                CASE WHEN `activeVersionId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`activeVersionId`, 14) ELSE `activeVersionId` END,
                `authorName`, `authorAvatarPath`, `sortIndex`, `expandedGroupIdsJson`
            FROM `story_presets`
        """.trimIndent(),
        """
            INSERT INTO `agent_preset_contents` (`presetId`, `kind`, `content`)
            SELECT CASE WHEN `presetId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`presetId`, 14) ELSE `presetId` END,
                `kind`, `content` FROM `story_preset_contents`
        """.trimIndent(),
        """
            INSERT OR IGNORE INTO `agent_preset_contents` (`presetId`, `kind`, `content`)
            SELECT CASE WHEN `id` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`id`, 14) ELSE `id` END,
                'usage_instructions', `description` FROM `story_presets`
        """.trimIndent(),
        "INSERT OR IGNORE INTO `agent_preset_contents` (`presetId`, `kind`, `content`) SELECT `id`, 'tool_configuration', '$DefaultToolConfiguration' FROM `agent_presets`",
        """
            INSERT INTO `agent_preset_entries` (`presetId`, `entryId`, `sortIndex`, `payloadJson`)
            SELECT CASE WHEN `presetId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`presetId`, 14) ELSE `presetId` END,
                `entryId`, `sortIndex`, `payloadJson` FROM `story_preset_entries`
            WHERE `entryId` NOT IN ('${LegacyAgentPresetMigration.RoleplayPlanEntryId}',
                '${LegacyAgentPresetMigration.DshHarnessIdentityEntryId}')
        """.trimIndent(),
        """
            INSERT INTO `agent_preset_groups` (`presetId`, `groupId`, `sortIndex`, `payloadJson`)
            SELECT CASE WHEN `presetId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`presetId`, 14) ELSE `presetId` END,
                `groupId`, `sortIndex`, `payloadJson` FROM `story_preset_groups`
        """.trimIndent(),
        """
            INSERT INTO `agent_preset_versions` (`presetId`, `versionId`, `versionNumber`, `name`, `createdAtEpochMs`, `expandedGroupIdsJson`)
            SELECT CASE WHEN `presetId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`presetId`, 14) ELSE `presetId` END,
                CASE WHEN `versionId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`versionId`, 14) ELSE `versionId` END,
                `versionNumber`, `name`, `createdAtEpochMs`, `expandedGroupIdsJson`
            FROM `story_preset_versions`
        """.trimIndent(),
        """
            INSERT INTO `agent_preset_version_contents` (`presetId`, `versionId`, `kind`, `content`)
            SELECT CASE WHEN `presetId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`presetId`, 14) ELSE `presetId` END,
                CASE WHEN `versionId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`versionId`, 14) ELSE `versionId` END,
                `kind`, `content` FROM `story_preset_version_contents`
        """.trimIndent(),
        """
            INSERT OR IGNORE INTO `agent_preset_version_contents` (`presetId`, `versionId`, `kind`, `content`)
            SELECT `version`.`presetId`, `version`.`versionId`, 'usage_instructions', `preset`.`description`
            FROM `agent_preset_versions` AS `version`
            INNER JOIN `story_presets` AS `preset`
                ON (CASE WHEN `preset`.`id` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`preset`.`id`, 14) ELSE `preset`.`id` END) = `version`.`presetId`
        """.trimIndent(),
        "INSERT OR IGNORE INTO `agent_preset_version_contents` (`presetId`, `versionId`, `kind`, `content`) SELECT `presetId`, `versionId`, 'tool_configuration', '$DefaultToolConfiguration' FROM `agent_preset_versions`",
        """
            INSERT INTO `agent_preset_version_entries` (`presetId`, `versionId`, `entryId`, `sortIndex`, `payloadJson`)
            SELECT CASE WHEN `presetId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`presetId`, 14) ELSE `presetId` END,
                CASE WHEN `versionId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`versionId`, 14) ELSE `versionId` END,
                `entryId`, `sortIndex`, `payloadJson` FROM `story_preset_version_entries`
            WHERE `entryId` NOT IN ('${LegacyAgentPresetMigration.RoleplayPlanEntryId}',
                '${LegacyAgentPresetMigration.DshHarnessIdentityEntryId}')
        """.trimIndent(),
        """
            INSERT INTO `agent_preset_version_groups` (`presetId`, `versionId`, `groupId`, `sortIndex`, `payloadJson`)
            SELECT CASE WHEN `presetId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`presetId`, 14) ELSE `presetId` END,
                CASE WHEN `versionId` LIKE 'story-preset-%'
                    THEN 'agent-preset-' || SUBSTR(`versionId`, 14) ELSE `versionId` END,
                `groupId`, `sortIndex`, `payloadJson` FROM `story_preset_version_groups`
        """.trimIndent(),
        "DROP TABLE `story_preset_version_runtime_entries`",
        "DROP TABLE `story_preset_version_groups`",
        "DROP TABLE `story_preset_version_entries`",
        "DROP TABLE `story_preset_version_contents`",
        "DROP TABLE `story_preset_versions`",
        "DROP TABLE `story_preset_runtime_entries`",
        "DROP TABLE `story_preset_groups`",
        "DROP TABLE `story_preset_entries`",
        "DROP TABLE `story_preset_contents`",
        "DROP TABLE `story_presets`",
        "DROP TABLE `story_preset_library_groups`",
        "DROP TABLE `story_preset_state`",
        "CREATE INDEX `index_agent_preset_contents_presetId` ON `agent_preset_contents` (`presetId`)",
        "CREATE INDEX `index_agent_preset_entries_presetId` ON `agent_preset_entries` (`presetId`)",
        "CREATE INDEX `index_agent_preset_groups_presetId` ON `agent_preset_groups` (`presetId`)",
        "CREATE INDEX `index_agent_preset_versions_presetId` ON `agent_preset_versions` (`presetId`)",
        "CREATE INDEX `index_agent_preset_version_contents_presetId_versionId` ON `agent_preset_version_contents` (`presetId`, `versionId`)",
        "CREATE INDEX `index_agent_preset_version_entries_presetId_versionId` ON `agent_preset_version_entries` (`presetId`, `versionId`)",
        "CREATE INDEX `index_agent_preset_version_groups_presetId_versionId` ON `agent_preset_version_groups` (`presetId`, `versionId`)",
    )

    val version1To2: Migration by lazy {
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val legacyRoleplayPlans = LegacyAgentPresetMigration.capturePlans(db)
                version1To2Statements.forEach(db::execSQL)
                LegacyAgentPresetMigration.finish(db, legacyRoleplayPlans)
            }
        }
    }

    val version2To3: Migration by lazy {
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                HiddenToolTimelinePositionMigration.migrate(db)
            }
        }
    }

    val version3To4: Migration by lazy {
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                HiddenToolTimelinePositionMigration.migrate(db)
            }
        }
    }
}
