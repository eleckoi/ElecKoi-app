-- Generated from Room schema; do not edit by hand.
PRAGMA foreign_keys = ON;

BEGIN TRANSACTION;

CREATE TABLE IF NOT EXISTS `chat_sessions` (`id` TEXT NOT NULL, `workspaceId` TEXT NOT NULL, `title` TEXT NOT NULL, `characterId` TEXT NOT NULL, `characterName` TEXT NOT NULL, `characterAvatar` TEXT NOT NULL, `permissionMode` TEXT NOT NULL, `historySummary` TEXT NOT NULL, `historyMessageCount` INTEGER NOT NULL, `historyUserMessageCount` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE INDEX IF NOT EXISTS `index_chat_sessions_characterId` ON `chat_sessions` (`characterId`);

CREATE INDEX IF NOT EXISTS `index_chat_sessions_updatedAt` ON `chat_sessions` (`updatedAt`);

CREATE TABLE IF NOT EXISTS `chat_session_character_snapshots` (`sessionId` TEXT NOT NULL, `personaJson` TEXT NOT NULL, PRIMARY KEY(`sessionId`), FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `chat_session_variable_states` (`sessionId` TEXT NOT NULL, `kind` TEXT NOT NULL, `stateJson` TEXT NOT NULL, PRIMARY KEY(`sessionId`, `kind`), FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `characters` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `avatar` TEXT NOT NULL, `squareImage` TEXT NOT NULL, `coverImage` TEXT NOT NULL, `groupName` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `groupViewOrder` INTEGER NOT NULL, `folder` TEXT NOT NULL, `frontendBeautyEnabled` INTEGER NOT NULL, `assistantName` TEXT NOT NULL, `assistantAvatar` TEXT NOT NULL, `profileAge` TEXT NOT NULL, `profileSex` TEXT NOT NULL, `profileHeight` TEXT NOT NULL, `profileBirthday` TEXT NOT NULL, `profileLike` TEXT NOT NULL, `showOpening` INTEGER NOT NULL, `chatBackground` TEXT NOT NULL, `chatBackgroundOpacity` REAL NOT NULL, `chatBackgroundBlur` REAL NOT NULL, `chatBackgroundScrim` REAL NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `character_text_contents` (`characterId` TEXT NOT NULL, `kind` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`characterId`, `kind`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_character_text_contents_characterId` ON `character_text_contents` (`characterId`);

CREATE TABLE IF NOT EXISTS `character_meta` (`id` TEXT NOT NULL, `activeCharacterId` TEXT NOT NULL, `groupsJson` TEXT NOT NULL, `listAllExpanded` INTEGER NOT NULL, `expandedGroupNamesJson` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `user_profile` (`id` TEXT NOT NULL, `userName` TEXT NOT NULL, `userAvatar` TEXT NOT NULL, `userSquare` TEXT NOT NULL, `userPortrait` TEXT NOT NULL, `userCover` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `model_configs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `provider` TEXT NOT NULL, `apiKey` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `proxyUrl` TEXT NOT NULL, `model` TEXT NOT NULL, `modelOptionsJson` TEXT NOT NULL, `customHeadersJson` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `imageSettingsJson` TEXT NOT NULL, `apiFormat` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `model_config_meta` (`id` TEXT NOT NULL, `activeConfigId` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `variable_configs` (`characterId` TEXT NOT NULL, `activeVersionId` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `revision` INTEGER NOT NULL, PRIMARY KEY(`characterId`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`characterId`, `activeVersionId`) REFERENCES `variable_config_versions`(`characterId`, `versionId`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED);

CREATE INDEX IF NOT EXISTS `index_variable_configs_characterId_activeVersionId` ON `variable_configs` (`characterId`, `activeVersionId`);

CREATE TABLE IF NOT EXISTS `variable_config_versions` (`characterId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `name` TEXT NOT NULL, `expandedObjectIdsJson` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`characterId`, `versionId`), FOREIGN KEY(`characterId`) REFERENCES `variable_configs`(`characterId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `variable_config_version_contents` (`characterId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `kind` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`characterId`, `versionId`, `kind`), FOREIGN KEY(`characterId`, `versionId`) REFERENCES `variable_config_versions`(`characterId`, `versionId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_variable_config_version_contents_characterId_versionId` ON `variable_config_version_contents` (`characterId`, `versionId`);

CREATE TABLE IF NOT EXISTS `variable_config_objects` (`characterId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `objectId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`characterId`, `versionId`, `objectId`), FOREIGN KEY(`characterId`, `versionId`) REFERENCES `variable_config_versions`(`characterId`, `versionId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_variable_config_objects_characterId_versionId` ON `variable_config_objects` (`characterId`, `versionId`);

CREATE TABLE IF NOT EXISTS `variable_config_variables` (`characterId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `variableId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`characterId`, `versionId`, `variableId`), FOREIGN KEY(`characterId`, `versionId`) REFERENCES `variable_config_versions`(`characterId`, `versionId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_variable_config_variables_characterId_versionId` ON `variable_config_variables` (`characterId`, `versionId`);

CREATE TABLE IF NOT EXISTS `global_regex_rules` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `pattern` TEXT NOT NULL, `replacement` TEXT NOT NULL, `targetsJson` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `displayOnly` INTEGER NOT NULL, `promptOnly` INTEGER NOT NULL, `runOnEdit` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, PRIMARY KEY(`id`));

CREATE INDEX IF NOT EXISTS `index_global_regex_rules_sortIndex` ON `global_regex_rules` (`sortIndex`);

CREATE TABLE IF NOT EXISTS `character_regex_rules` (`characterId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `pattern` TEXT NOT NULL, `replacement` TEXT NOT NULL, `targetsJson` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `displayOnly` INTEGER NOT NULL, `promptOnly` INTEGER NOT NULL, `runOnEdit` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, PRIMARY KEY(`characterId`, `id`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_character_regex_rules_characterId_sortIndex` ON `character_regex_rules` (`characterId`, `sortIndex`);

CREATE TABLE IF NOT EXISTS `regex_enablement_versions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `globalEnabledIdsJson` TEXT NOT NULL, `characterEnabledIdsJson` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `regex_state` (`singletonId` INTEGER NOT NULL, `activeVersionId` TEXT, `revision` INTEGER NOT NULL, PRIMARY KEY(`singletonId`), FOREIGN KEY(`activeVersionId`) REFERENCES `regex_enablement_versions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL );

CREATE INDEX IF NOT EXISTS `index_regex_state_activeVersionId` ON `regex_state` (`activeVersionId`);

CREATE TABLE IF NOT EXISTS `frontend_projects` (`id` TEXT NOT NULL, `characterId` TEXT NOT NULL, `name` TEXT NOT NULL, `entryFile` TEXT NOT NULL, `filesJson` TEXT NOT NULL, `importedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_frontend_projects_characterId` ON `frontend_projects` (`characterId`);

CREATE TABLE IF NOT EXISTS `character_frontend_settings` (`characterId` TEXT NOT NULL, `selectedProjectId` TEXT, `messageRendererEnabled` INTEGER NOT NULL, PRIMARY KEY(`characterId`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`selectedProjectId`) REFERENCES `frontend_projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL );

CREATE INDEX IF NOT EXISTS `index_character_frontend_settings_selectedProjectId` ON `character_frontend_settings` (`selectedProjectId`);

CREATE TABLE IF NOT EXISTS `creator_workspaces` (`id` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, `name` TEXT NOT NULL, `linkedCharacterId` TEXT, `characterOwned` INTEGER NOT NULL, `primaryCharacterRootId` TEXT, `previewEntryFile` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `totalBytes` INTEGER NOT NULL, `latestCheckpointId` TEXT, `permissionMode` TEXT NOT NULL, `activeConversationId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`linkedCharacterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION );

CREATE INDEX IF NOT EXISTS `index_creator_workspaces_linkedCharacterId` ON `creator_workspaces` (`linkedCharacterId`);

CREATE INDEX IF NOT EXISTS `index_creator_workspaces_updatedAt` ON `creator_workspaces` (`updatedAt`);

CREATE TABLE IF NOT EXISTS `creator_workspace_files` (`workspaceId` TEXT NOT NULL, `path` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, PRIMARY KEY(`workspaceId`, `path`), FOREIGN KEY(`workspaceId`) REFERENCES `creator_workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_creator_workspace_files_workspaceId` ON `creator_workspace_files` (`workspaceId`);

CREATE TABLE IF NOT EXISTS `creator_workspace_character_roots` (`workspaceId` TEXT NOT NULL, `id` TEXT NOT NULL, `characterId` TEXT NOT NULL, `alias` TEXT NOT NULL, `access` TEXT NOT NULL, PRIMARY KEY(`workspaceId`, `id`), FOREIGN KEY(`workspaceId`) REFERENCES `creator_workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_creator_workspace_character_roots_characterId` ON `creator_workspace_character_roots` (`characterId`);

CREATE TABLE IF NOT EXISTS `creator_workspace_conversations` (`workspaceId` TEXT NOT NULL, `id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`workspaceId`, `id`), FOREIGN KEY(`workspaceId`) REFERENCES `creator_workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_creator_workspace_conversations_id` ON `creator_workspace_conversations` (`id`);

CREATE INDEX IF NOT EXISTS `index_creator_workspace_conversations_workspaceId_updatedAt` ON `creator_workspace_conversations` (`workspaceId`, `updatedAt`);

CREATE TABLE IF NOT EXISTS `cleanup_operations` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `targetId` TEXT NOT NULL, `state` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `lastError` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE UNIQUE INDEX IF NOT EXISTS `index_cleanup_operations_kind_targetId` ON `cleanup_operations` (`kind`, `targetId`);

CREATE INDEX IF NOT EXISTS `index_cleanup_operations_state_updatedAtEpochMs` ON `cleanup_operations` (`state`, `updatedAtEpochMs`);

CREATE TABLE IF NOT EXISTS `agent_conversations` (`id` TEXT NOT NULL, `surface` TEXT NOT NULL, `activeBranchId` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `revision` INTEGER NOT NULL, PRIMARY KEY(`id`));

CREATE INDEX IF NOT EXISTS `index_agent_conversations_activeBranchId` ON `agent_conversations` (`activeBranchId`);

CREATE TABLE IF NOT EXISTS `agent_conversation_display_cache` (`conversationId` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL, `ledgerRevision` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, `rendererVersion` INTEGER NOT NULL, PRIMARY KEY(`conversationId`, `chunkIndex`), FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `agent_branches` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_branches_conversationId` ON `agent_branches` (`conversationId`);

CREATE TABLE IF NOT EXISTS `conversation_speakers` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `sourceSpeakerId` TEXT NOT NULL, `kind` TEXT NOT NULL, `displayName` TEXT NOT NULL, `avatarAssetId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_conversation_speakers_conversationId` ON `conversation_speakers` (`conversationId`);

CREATE UNIQUE INDEX IF NOT EXISTS `index_conversation_speakers_conversationId_sourceSpeakerId` ON `conversation_speakers` (`conversationId`, `sourceSpeakerId`);

CREATE TABLE IF NOT EXISTS `agent_turns` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `speakerId` TEXT NOT NULL, `sourceMessageId` TEXT NOT NULL, `kind` TEXT NOT NULL, `provider` TEXT NOT NULL, `model` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `variableStateJson` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`speakerId`) REFERENCES `conversation_speakers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE INDEX IF NOT EXISTS `index_agent_turns_conversationId` ON `agent_turns` (`conversationId`);

CREATE INDEX IF NOT EXISTS `index_agent_turns_speakerId` ON `agent_turns` (`speakerId`);

CREATE INDEX IF NOT EXISTS `index_agent_turns_conversationId_sourceMessageId` ON `agent_turns` (`conversationId`, `sourceMessageId`);

CREATE TABLE IF NOT EXISTS `agent_responses` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `turnId` TEXT NOT NULL, `responseIndex` INTEGER NOT NULL, `speakerId` TEXT NOT NULL, `sourceMessageId` TEXT NOT NULL, `status` TEXT NOT NULL, `provider` TEXT NOT NULL, `model` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `variableStateJson` TEXT NOT NULL, `runtimeThreadId` TEXT NOT NULL, `runtimeTurnId` TEXT NOT NULL, `turnStartedAtMillis` INTEGER NOT NULL, `turnCompletedAtMillis` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`turnId`) REFERENCES `agent_turns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`speakerId`) REFERENCES `conversation_speakers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );

CREATE INDEX IF NOT EXISTS `index_agent_responses_conversationId` ON `agent_responses` (`conversationId`);

CREATE INDEX IF NOT EXISTS `index_agent_responses_speakerId` ON `agent_responses` (`speakerId`);

CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_responses_turnId_responseIndex` ON `agent_responses` (`turnId`, `responseIndex`);

CREATE TABLE IF NOT EXISTS `agent_branch_turns` (`branchId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `turnId` TEXT NOT NULL, PRIMARY KEY(`branchId`, `sequence`), FOREIGN KEY(`branchId`) REFERENCES `agent_branches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`turnId`) REFERENCES `agent_turns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_branch_turns_branchId_turnId` ON `agent_branch_turns` (`branchId`, `turnId`);

CREATE INDEX IF NOT EXISTS `index_agent_branch_turns_turnId` ON `agent_branch_turns` (`turnId`);

CREATE TABLE IF NOT EXISTS `agent_content_parts` (`conversationId` TEXT NOT NULL, `ownerType` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `partIndex` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL, PRIMARY KEY(`ownerType`, `ownerId`, `partIndex`, `chunkIndex`), FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_content_parts_conversationId` ON `agent_content_parts` (`conversationId`);

CREATE INDEX IF NOT EXISTS `index_agent_content_parts_ownerType_ownerId` ON `agent_content_parts` (`ownerType`, `ownerId`);

CREATE TABLE IF NOT EXISTS `generation_attempts` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `kind` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `parentAttemptId` TEXT, `outputMessageId` TEXT NOT NULL, `attemptNumber` INTEGER NOT NULL, `state` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `startedAtMillis` INTEGER, `finishedAtMillis` INTEGER, `errorMessage` TEXT NOT NULL, `outputPath` TEXT NOT NULL, `supersededByAttemptId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `agent_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_generation_attempts_conversationId` ON `generation_attempts` (`conversationId`);

CREATE UNIQUE INDEX IF NOT EXISTS `index_generation_attempts_conversationId_kind_ownerId_attemptNumber` ON `generation_attempts` (`conversationId`, `kind`, `ownerId`, `attemptNumber`);

CREATE INDEX IF NOT EXISTS `index_generation_attempts_conversationId_state` ON `generation_attempts` (`conversationId`, `state`);

CREATE INDEX IF NOT EXISTS `index_generation_attempts_parentAttemptId` ON `generation_attempts` (`parentAttemptId`);

CREATE INDEX IF NOT EXISTS `index_generation_attempts_outputMessageId` ON `generation_attempts` (`outputMessageId`);

CREATE TABLE IF NOT EXISTS `setting_libraries` (`characterId` TEXT NOT NULL, `name` TEXT NOT NULL, `activeVersionId` TEXT NOT NULL, `listAllExpanded` INTEGER NOT NULL, `expandedGroupIdsJson` TEXT NOT NULL, `promptPositionsJson` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`characterId`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `setting_entry_contents` (`characterId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `revisionId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`characterId`, `entryId`, `revisionId`), FOREIGN KEY(`characterId`) REFERENCES `setting_libraries`(`characterId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `setting_library_entry_links` (`characterId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `revisionId` TEXT NOT NULL, PRIMARY KEY(`characterId`, `entryId`), FOREIGN KEY(`characterId`) REFERENCES `setting_libraries`(`characterId`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`characterId`, `entryId`, `revisionId`) REFERENCES `setting_entry_contents`(`characterId`, `entryId`, `revisionId`) ON UPDATE NO ACTION ON DELETE NO ACTION );

CREATE INDEX IF NOT EXISTS `index_setting_library_entry_links_characterId_entryId_revisionId` ON `setting_library_entry_links` (`characterId`, `entryId`, `revisionId`);

CREATE INDEX IF NOT EXISTS `index_setting_library_entry_links_characterId_sortIndex_entryId` ON `setting_library_entry_links` (`characterId`, `sortIndex`, `entryId`);

CREATE TABLE IF NOT EXISTS `setting_library_groups` (`characterId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`characterId`, `groupId`), FOREIGN KEY(`characterId`) REFERENCES `setting_libraries`(`characterId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `setting_library_versions` (`characterId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `name` TEXT NOT NULL, `listAllExpanded` INTEGER NOT NULL, `expandedGroupIdsJson` TEXT NOT NULL, `promptPositionsJson` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`characterId`, `versionId`), FOREIGN KEY(`characterId`) REFERENCES `setting_libraries`(`characterId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `setting_library_version_entry_links` (`characterId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `revisionId` TEXT NOT NULL, PRIMARY KEY(`characterId`, `versionId`, `entryId`), FOREIGN KEY(`characterId`, `versionId`) REFERENCES `setting_library_versions`(`characterId`, `versionId`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`characterId`, `entryId`, `revisionId`) REFERENCES `setting_entry_contents`(`characterId`, `entryId`, `revisionId`) ON UPDATE NO ACTION ON DELETE NO ACTION );

CREATE INDEX IF NOT EXISTS `index_setting_library_version_entry_links_characterId_entryId_revisionId` ON `setting_library_version_entry_links` (`characterId`, `entryId`, `revisionId`);

CREATE INDEX IF NOT EXISTS `index_setting_library_version_entry_links_characterId_versionId_sortIndex_entryId` ON `setting_library_version_entry_links` (`characterId`, `versionId`, `sortIndex`, `entryId`);

CREATE TABLE IF NOT EXISTS `setting_library_version_groups` (`characterId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`characterId`, `versionId`, `groupId`), FOREIGN KEY(`characterId`, `versionId`) REFERENCES `setting_library_versions`(`characterId`, `versionId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `conversation_setting_changes` (`sessionId` TEXT NOT NULL, `targetType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `operation` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`sessionId`, `targetType`, `targetId`), FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_conversation_setting_changes_sessionId` ON `conversation_setting_changes` (`sessionId`);

CREATE TABLE IF NOT EXISTS `roleplay_rich_heights` (`sessionId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `contentRevision` TEXT NOT NULL, `rootIndex` INTEGER NOT NULL, `viewportWidthPx` INTEGER NOT NULL, `heightPx` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `messageId`, `contentRevision`, `rootIndex`, `viewportWidthPx`), FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_roleplay_rich_heights_sessionId` ON `roleplay_rich_heights` (`sessionId`);

CREATE TABLE IF NOT EXISTS `agent_preset_state` (`singletonId` INTEGER NOT NULL, `activePresetId` TEXT NOT NULL, PRIMARY KEY(`singletonId`));

CREATE TABLE IF NOT EXISTS `agent_preset_library_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `agent_presets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `modelFamily` TEXT NOT NULL, `modelTagsJson` TEXT NOT NULL DEFAULT '[]', `libraryGroupId` TEXT NOT NULL DEFAULT 'agent-preset-group-default', `activeVersionId` TEXT NOT NULL DEFAULT '', `authorName` TEXT NOT NULL DEFAULT '', `authorAvatarPath` TEXT NOT NULL DEFAULT '', `sortIndex` INTEGER NOT NULL, `expandedGroupIdsJson` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE TABLE IF NOT EXISTS `agent_preset_contents` (`presetId` TEXT NOT NULL, `kind` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`presetId`, `kind`), FOREIGN KEY(`presetId`) REFERENCES `agent_presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_preset_contents_presetId` ON `agent_preset_contents` (`presetId`);

CREATE TABLE IF NOT EXISTS `agent_preset_entries` (`presetId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`presetId`, `entryId`), FOREIGN KEY(`presetId`) REFERENCES `agent_presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_preset_entries_presetId` ON `agent_preset_entries` (`presetId`);

CREATE TABLE IF NOT EXISTS `agent_preset_groups` (`presetId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`presetId`, `groupId`), FOREIGN KEY(`presetId`) REFERENCES `agent_presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_preset_groups_presetId` ON `agent_preset_groups` (`presetId`);

CREATE TABLE IF NOT EXISTS `agent_preset_versions` (`presetId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `versionNumber` INTEGER NOT NULL, `name` TEXT NOT NULL DEFAULT '', `createdAtEpochMs` INTEGER NOT NULL, `expandedGroupIdsJson` TEXT NOT NULL, PRIMARY KEY(`presetId`, `versionId`), FOREIGN KEY(`presetId`) REFERENCES `agent_presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_preset_versions_presetId` ON `agent_preset_versions` (`presetId`);

CREATE TABLE IF NOT EXISTS `agent_preset_version_contents` (`presetId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `kind` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`presetId`, `versionId`, `kind`), FOREIGN KEY(`presetId`, `versionId`) REFERENCES `agent_preset_versions`(`presetId`, `versionId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_preset_version_contents_presetId_versionId` ON `agent_preset_version_contents` (`presetId`, `versionId`);

CREATE TABLE IF NOT EXISTS `agent_preset_version_entries` (`presetId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`presetId`, `versionId`, `entryId`), FOREIGN KEY(`presetId`, `versionId`) REFERENCES `agent_preset_versions`(`presetId`, `versionId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_preset_version_entries_presetId_versionId` ON `agent_preset_version_entries` (`presetId`, `versionId`);

CREATE TABLE IF NOT EXISTS `agent_preset_version_groups` (`presetId` TEXT NOT NULL, `versionId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, PRIMARY KEY(`presetId`, `versionId`, `groupId`), FOREIGN KEY(`presetId`, `versionId`) REFERENCES `agent_preset_versions`(`presetId`, `versionId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_agent_preset_version_groups_presetId_versionId` ON `agent_preset_version_groups` (`presetId`, `versionId`);

CREATE VIEW `setting_library_entries` AS SELECT link.characterId, link.entryId, link.sortIndex, content.payloadJson
        FROM setting_library_entry_links AS link
        JOIN setting_entry_contents AS content ON content.characterId = link.characterId
          AND content.entryId = link.entryId AND content.revisionId = link.revisionId;

CREATE VIEW `setting_library_version_entries` AS SELECT link.characterId, link.versionId, link.entryId, link.sortIndex, content.payloadJson
        FROM setting_library_version_entry_links AS link
        JOIN setting_entry_contents AS content ON content.characterId = link.characterId
          AND content.entryId = link.entryId AND content.revisionId = link.revisionId;

PRAGMA user_version = 4;

COMMIT;
