package com.eleckoi.android.foundation.storage.room

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Executes the same exported DDL that a desktop SQLite client consumes, without an Android device. */
class SqliteSchemaContractTest {
    private val directory = File(requireNotNull(System.getProperty("eleckoi.schemaDirectory")))
    private val storageSources = File(requireNotNull(System.getProperty("eleckoi.storageSourceDirectory")))
    private val schema = JSONObject(File(directory,
        "com.eleckoi.android.foundation.storage.room.ElecKoiDatabase/3.json").readText()).getJSONObject("database")
    private val version1Schema = JSONObject(File(directory,
        "com.eleckoi.android.foundation.storage.room.ElecKoiDatabase/1.json").readText()).getJSONObject("database")

    @Test fun `version 2 to 3 is data-only and preserves the complete schema`() {
        sqliteDatabase("eleckoi-common-schema-v2.sql") { db ->
            assertEquals(freshSchemaStructure(), db.schemaStructure())
        }
    }

    @Test fun `version 1 to 2 removes obsolete data and exactly matches the clean schema`() {
        sqliteDatabase("eleckoi-common-schema-v1.sql") { db ->
            insertV1(db, "characters", mapOf("id" to "character-a", "name" to "角色", "characterMode" to "story"))
            insertV1(db, "setting_libraries", mapOf("characterId" to "character-a"))
            insertV1(
                db,
                "setting_entry_contents",
                mapOf(
                    "characterId" to "character-a",
                    "entryId" to "fixed-roleplay-plan",
                    "revisionId" to "roleplay-revision",
                    "payloadJson" to "{\"kind\":\"roleplay_plan\",\"content\":\"读取设定\\n输出正文\"}",
                ),
            )
            insertV1(
                db,
                "setting_library_entry_links",
                mapOf(
                    "characterId" to "character-a",
                    "entryId" to "fixed-roleplay-plan",
                    "revisionId" to "roleplay-revision",
                ),
            )
            db.execute(
                "INSERT INTO character_text_contents(characterId, kind, content) " +
                    "VALUES ('character-a', 'assistant_prompt', '废弃提示词')",
            )
            db.execute(
                "INSERT INTO character_text_contents(characterId, kind, content) " +
                    "VALUES ('character-a', 'opening', '保留开场白')",
            )
            db.execute(
                "INSERT INTO global_tool_config(singletonId, payloadJson, updatedAt) " +
                    "VALUES (1, '{\"shared\":true}', 'before')",
            )
            db.execute(
                "INSERT INTO character_tool_configs(characterId, payloadJson, updatedAt) " +
                    "VALUES ('character-a', '{\"scoped\":true}', 'before')",
            )
            insertV1(db, "chat_sessions", mapOf("id" to "chat-a", "updatedAt" to "before", "characterMode" to "story"))
            db.execute(
                "INSERT INTO chat_session_model_settings(sessionId, settingsJson) " +
                    "VALUES ('chat-a', '{\"chat\":{\"config_id\":\"openai-config\",\"model\":\"gpt-5\"}}')",
            )
            insertV1(
                db,
                "chat_session_character_snapshots",
                mapOf("sessionId" to "chat-a", "personaJson" to "{\"name\":\"角色\"}"),
            )
            insertV1(
                db,
                "chat_session_variable_states",
                mapOf("sessionId" to "chat-a", "kind" to "current", "stateJson" to "{\"turn\":1}"),
            )
            insertV1(
                db,
                "conversation_setting_changes",
                mapOf(
                    "sessionId" to "chat-a",
                    "targetType" to "entry",
                    "targetId" to "entry-a",
                    "operation" to "update",
                    "payloadJson" to "{\"value\":1}",
                    "updatedAt" to "before",
                ),
            )
            insertV1(
                db,
                "conversation_setting_changes",
                mapOf(
                    "sessionId" to "chat-a",
                    "targetType" to "entry",
                    "targetId" to "fixed-roleplay-plan",
                    "operation" to "update",
                    "payloadJson" to "{\"kind\":\"roleplay_plan\",\"content\":\"旧对话计划\"}",
                    "updatedAt" to "before",
                ),
            )
            db.execute(
                "INSERT INTO roleplay_rich_heights(" +
                    "sessionId, messageId, contentRevision, rootIndex, viewportWidthPx, heightPx, measuredAtEpochMs" +
                    ") VALUES ('chat-a', 'message-a', 'revision-a', 0, 640, 812, 1)",
            )
            insertV1(
                db,
                "agent_conversations",
                mapOf("id" to "chat-a", "activeBranchId" to "branch-a"),
            )
            db.execute(
                "INSERT INTO agent_conversation_display_cache(" +
                    "conversationId, chunkIndex, ledgerRevision, payloadJson, rendererVersion, updatedAt" +
                    ") VALUES ('chat-a', 0, 0, '[{\"id\":\"message-a\"}]', 1, 'before')",
            )
            db.execute(
                "INSERT INTO agent_branches(" +
                    "id, conversationId, parentBranchId, forkedFromTurnId, headSequence, name, reason, createdAt" +
                    ") VALUES ('branch-a', 'chat-a', NULL, NULL, 0, '主分支', 'conversation_created', '')",
            )
            insertV1(
                db,
                "conversation_speakers",
                mapOf(
                    "id" to "speaker-a",
                    "conversationId" to "chat-a",
                    "sourceSpeakerId" to "user",
                    "kind" to "user",
                ),
            )
            insertV1(
                db,
                "agent_turns",
                mapOf(
                    "id" to "turn-a",
                    "conversationId" to "chat-a",
                    "speakerId" to "speaker-a",
                    "sourceMessageId" to "message-a",
                ),
            )
            insertV1(
                db,
                "agent_branch_turns",
                mapOf("branchId" to "branch-a", "sequence" to 0, "turnId" to "turn-a"),
            )
            insertV1(
                db,
                "creator_workspaces",
                mapOf(
                    "id" to "workspace-a",
                    "name" to "角色工作区",
                    "linkedCharacterId" to "character-a",
                    "linkedCharacterMode" to "story",
                ),
            )
            insertV1(
                db,
                "creator_workspace_character_roots",
                mapOf(
                    "workspaceId" to "workspace-a",
                    "id" to "root-a",
                    "characterId" to "character-a",
                ),
            )
            insertV1(
                db,
                "story_presets",
                mapOf(
                    "id" to "story-preset-default",
                    "name" to "默认故事预设",
                    "modelFamily" to "general",
                    "activeVersionId" to "story-preset-default:v1",
                    "description" to "迁移后的使用说明",
                ),
            )
            insertV1(
                db,
                "story_preset_state",
                mapOf("singletonId" to 0, "activePresetId" to "story-preset-default"),
            )
            insertV1(
                db,
                "story_preset_versions",
                mapOf(
                    "presetId" to "story-preset-default",
                    "versionId" to "story-preset-default:v1",
                    "versionNumber" to 1,
                ),
            )
            insertV1(
                db,
                "story_preset_entries",
                mapOf(
                    "presetId" to "story-preset-default",
                    "entryId" to "fixed-roleplay-plan",
                    "payloadJson" to "{\"kind\":\"roleplay_plan\",\"content\":\"读取设定\\n输出正文\"}",
                ),
            )
            insertV1(
                db,
                "story_preset_entries",
                mapOf(
                    "presetId" to "story-preset-default",
                    "entryId" to "built-in-dsh-harness-identity",
                    "sortIndex" to 1,
                    "payloadJson" to "{\"kind\":\"normal\",\"content\":\"旧 DSH 身份\"}",
                ),
            )
            insertV1(
                db,
                "story_preset_entries",
                mapOf(
                    "presetId" to "story-preset-default",
                    "entryId" to "author-entry",
                    "sortIndex" to 2,
                    "payloadJson" to "{\"kind\":\"normal\",\"content\":\"保留作者提示词\"}",
                ),
            )
            insertV1(
                db,
                "story_preset_version_entries",
                mapOf(
                    "presetId" to "story-preset-default",
                    "versionId" to "story-preset-default:v1",
                    "entryId" to "fixed-roleplay-plan",
                    "payloadJson" to "{\"kind\":\"roleplay_plan\",\"content\":\"读取版本设定\\n输出版本正文\"}",
                ),
            )
            insertV1(
                db,
                "model_configs",
                mapOf(
                    "id" to "model-a",
                    "name" to "保留的模型配置",
                    "provider" to "custom",
                    "model" to "model-a",
                    "supportsTools" to 1,
                ),
            )

            // Android may run the schema migration while FK enforcement is temporarily disabled.
            // In that mode rebuilding a parent table does not cascade-delete its existing child rows,
            // so restoring the child backups must replace those rows instead of inserting duplicates.
            db.execute("PRAGMA foreign_keys = OFF")
            db.execute("PRAGMA legacy_alter_table = ON")
            db.autoCommit = false
            try {
                ElecKoiDatabaseMigrations.version1To2Statements.forEachIndexed { index, statement ->
                    try {
                        db.execute(statement)
                    } catch (error: SQLException) {
                        throw SQLException("Migration statement #$index failed: $statement", error)
                    }
                }
                db.execute("PRAGMA user_version = 2")
                val foreignKeyViolations = db.foreignKeyViolations()
                if (foreignKeyViolations.isNotEmpty()) {
                    throw SQLException("Migration left foreign-key violations: $foreignKeyViolations")
                }
                db.commit()
            } catch (error: Throwable) {
                db.rollback()
                throw error
            } finally {
                db.autoCommit = true
                db.execute("PRAGMA legacy_alter_table = OFF")
                db.execute("PRAGMA foreign_keys = ON")
            }

            assertEquals(1, db.number("SELECT COUNT(*) FROM chat_sessions WHERE id = 'chat-a'"))
            assertEquals(1, db.number("SELECT COUNT(*) FROM chat_session_character_snapshots WHERE sessionId = 'chat-a'"))
            assertEquals(1, db.number("SELECT COUNT(*) FROM chat_session_variable_states WHERE sessionId = 'chat-a'"))
            assertEquals(1, db.number("SELECT COUNT(*) FROM conversation_setting_changes WHERE sessionId = 'chat-a'"))
            assertEquals(1, db.number("SELECT COUNT(*) FROM creator_workspace_character_roots WHERE workspaceId = 'workspace-a'"))
            assertEquals(
                0,
                db.number(
                    "SELECT COUNT(*) FROM sqlite_master " +
                        "WHERE type = 'table' AND name = 'chat_session_model_settings'",
                ),
            )
            assertEquals(2, db.number("PRAGMA user_version"))
            assertEquals(schemaTableNames(), db.tableNames())
            assertEquals(freshSchemaStructure(), db.schemaStructure())
            assertEquals(setOf("id", "conversationId"), db.columns("agent_branches"))
            assertEquals(1, db.number("SELECT COUNT(*) FROM agent_branch_turns WHERE turnId = 'turn-a'"))
            assertEquals(
                "保留开场白",
                db.text("SELECT content FROM character_text_contents WHERE kind = 'opening'"),
            )
            assertEquals(
                0,
                db.number("SELECT COUNT(*) FROM character_text_contents WHERE kind = 'assistant_prompt'"),
            )
            assertEquals(
                0,
                db.number("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('global_tool_config', 'character_tool_configs')"),
            )
            assertFalse("characterMode" in db.columns("characters"))
            assertFalse("characterMode" in db.columns("chat_sessions"))
            assertFalse("supportsTools" in db.columns("model_configs"))
            assertEquals(
                "保留的模型配置",
                db.text("SELECT name FROM model_configs WHERE id = 'model-a'"),
            )
            assertEquals("agent-preset-default", db.text("SELECT activePresetId FROM agent_preset_state"))
            assertEquals(
                "迁移后的使用说明",
                db.text("SELECT content FROM agent_preset_contents WHERE presetId = 'agent-preset-default' AND kind = 'usage_instructions'"),
            )
            val toolConfiguration = db.text(
                "SELECT content FROM agent_preset_contents WHERE presetId = 'agent-preset-default' AND kind = 'tool_configuration'",
            )
            assertTrue(toolConfiguration.contains("builtin:variables"))
            assertTrue(toolConfiguration.contains("builtin:setting-library"))
            assertEquals(
                0,
                db.number(
                    "SELECT COUNT(*) FROM agent_preset_entries " +
                        "WHERE entryId IN ('fixed-roleplay-plan', 'built-in-dsh-harness-identity')",
                ),
            )
            assertEquals(
                1,
                db.number("SELECT COUNT(*) FROM agent_preset_entries WHERE entryId = 'author-entry'"),
            )
            assertEquals(
                0,
                db.number("SELECT COUNT(*) FROM agent_preset_version_entries WHERE entryId = 'fixed-roleplay-plan'"),
            )
            assertEquals(
                0,
                db.number("SELECT COUNT(*) FROM setting_entry_contents WHERE entryId = 'fixed-roleplay-plan'"),
            )
            assertEquals(
                0,
                db.number("SELECT COUNT(*) FROM conversation_setting_changes WHERE targetId = 'fixed-roleplay-plan'"),
            )
            assertFalse("updatedAt" in db.columns("agent_conversation_display_cache"))
            assertEquals(
                "[{\"id\":\"message-a\"}]",
                db.text("SELECT payloadJson FROM agent_conversation_display_cache WHERE conversationId = 'chat-a'"),
            )
            assertEquals(812, db.number("SELECT heightPx FROM roleplay_rich_heights WHERE messageId = 'message-a'"))
            assertFalse("measuredAtEpochMs" in db.columns("roleplay_rich_heights"))
            db.createStatement().use { statement ->
                statement.executeQuery("PRAGMA foreign_key_check").use { assertFalse(it.next()) }
            }
        }
    }

    @Test fun `common DDL preserves business views and foreign keys without Room metadata`() = database { db ->
        assertEquals(schema.getInt("version"), db.number("PRAGMA user_version"))
        val entities = schema.getJSONArray("entities")
        assertEquals(entities.length(), db.number("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'"))
        assertEquals(0, db.number("SELECT COUNT(*) FROM sqlite_master WHERE name = 'room_master_table'"))
        assertEquals(2, db.number("SELECT COUNT(*) FROM sqlite_master WHERE type = 'view'"))
        db.createStatement().use { statement ->
            statement.executeQuery("PRAGMA foreign_key_check").use { assertFalse(it.next()) }
            statement.executeQuery("SELECT * FROM setting_library_entries").close()
            statement.executeQuery("SELECT * FROM setting_library_version_entries").close()
        }
    }

    @Test fun `business schema and Room storage layer contain no integrity hashes`() {
        val forbiddenColumn = Regex("(?i)(hash|digest|checksum|sha(?:1|2|3|256|512)?)")
        val entities = schema.getJSONArray("entities")
        val forbiddenColumns = buildList {
            for (entityIndex in 0 until entities.length()) {
                val entity = entities.getJSONObject(entityIndex)
                val fields = entity.getJSONArray("fields")
                for (fieldIndex in 0 until fields.length()) {
                    val column = fields.getJSONObject(fieldIndex).getString("columnName")
                    if (forbiddenColumn.containsMatchIn(column)) add("${entity.getString("tableName")}.$column")
                }
            }
        }
        assertTrue("Business hash columns are forbidden: $forbiddenColumns", forbiddenColumns.isEmpty())

        val forbiddenSource = Regex("MessageDigest|getInstance\\(\\\"SHA|\\bsha256\\b|content_hash|contentHash|checksum")
        val sourceViolations = storageSources.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    if (forbiddenSource.containsMatchIn(line)) "${file.name}:${index + 1}" else null
                }
            }
            .toList()
        assertTrue("Room storage must not hash business data: $sourceViolations", sourceViolations.isEmpty())
    }

    @Test fun `monotonic revision columns stay limited to real concurrency and publication owners`() {
        val owners = buildSet {
            val entities = schema.getJSONArray("entities")
            for (entityIndex in 0 until entities.length()) {
                val entity = entities.getJSONObject(entityIndex)
                val fields = entity.getJSONArray("fields")
                for (fieldIndex in 0 until fields.length()) {
                    if (fields.getJSONObject(fieldIndex).getString("columnName") == "revision") {
                        add(entity.getString("tableName"))
                    }
                }
            }
        }
        assertEquals(
            setOf("agent_conversations", "regex_state", "variable_configs"),
            owners,
        )
    }

    @Test fun `frequently updated rows do not contain unrelated large documents`() = database { db ->
        assertFalse(db.columns("characters").any {
            it in setOf("imagePrompt", "opening")
        })
        assertFalse(db.columns("chat_sessions").any {
            it in setOf("characterPersonaJson", "modelSettingsJson", "initialVariableStateJson", "variableStateJson")
        })
        assertFalse("filesJson" in db.columns("creator_workspaces"))
        assertFalse(db.columns("variable_config_versions").any {
            it in setOf("initialStateJson", "currentStateJson", "schemaCode", "objectsJson", "variablesJson")
        })
        assertFalse(db.columns("agent_presets").any {
            it in setOf("timelineJson", "regexRulesJson", "promptPositionsJson")
        })

        val large = "长内容".repeat(100_000)
        insert(db, "characters", mapOf("id" to "split-card", "name" to "原名"))
        insert(db, "character_text_contents", mapOf(
            "characterId" to "split-card",
            "kind" to "opening",
            "content" to large,
        ))
        db.execute("UPDATE characters SET name = '新名' WHERE id = 'split-card'")
        assertEquals(large, db.text("SELECT content FROM character_text_contents WHERE characterId = 'split-card'"))

        insert(db, "chat_sessions", mapOf("id" to "split-chat", "updatedAt" to "before"))
        insert(db, "chat_session_character_snapshots", mapOf(
            "sessionId" to "split-chat",
            "personaJson" to large,
        ))
        insert(db, "chat_session_variable_states", mapOf(
            "sessionId" to "split-chat",
            "kind" to "initial",
            "stateJson" to large,
        ))
        insert(db, "chat_session_variable_states", mapOf(
            "sessionId" to "split-chat",
            "kind" to "current",
            "stateJson" to "{}",
        ))
        db.execute("UPDATE chat_session_variable_states SET stateJson = '{\"turn\":2}' WHERE sessionId = 'split-chat' AND kind = 'current'")
        db.execute("UPDATE chat_sessions SET updatedAt = 'after' WHERE id = 'split-chat'")
        assertEquals(large, db.text("SELECT personaJson FROM chat_session_character_snapshots WHERE sessionId = 'split-chat'"))
        assertEquals(large, db.text("SELECT stateJson FROM chat_session_variable_states WHERE sessionId = 'split-chat' AND kind = 'initial'"))

        insert(db, "creator_workspaces", mapOf("id" to "split-workspace"))
        insert(db, "creator_workspace_files", mapOf(
            "workspaceId" to "split-workspace",
            "path" to "src/index.html",
        ))
        db.execute("UPDATE creator_workspaces SET totalBytes = 123 WHERE id = 'split-workspace'")
        assertEquals("src/index.html", db.text("SELECT path FROM creator_workspace_files WHERE workspaceId = 'split-workspace'"))

        insert(db, "agent_presets", mapOf("id" to "split-preset", "name" to "原预设"))
        insert(db, "agent_preset_contents", mapOf(
            "presetId" to "split-preset",
            "kind" to "regex_rules",
            "content" to large,
        ))
        db.execute("UPDATE agent_presets SET name = '新预设' WHERE id = 'split-preset'")
        assertEquals(large, db.text("SELECT content FROM agent_preset_contents WHERE presetId = 'split-preset'"))
    }

    @Test fun `deleting one card removes its variable versions and regex but preserves shared data`() = database { db ->
        listOf("a", "b").forEach { id ->
            insert(db, "characters", mapOf("id" to id))
            db.autoCommit = false
            insert(db, "variable_configs", mapOf("characterId" to id, "activeVersionId" to "v"))
            insert(db, "variable_config_versions", mapOf("characterId" to id, "versionId" to "v"))
            db.commit()
            db.autoCommit = true
            insert(db, "character_regex_rules", mapOf("characterId" to id, "id" to "r"))
        }
        insert(db, "global_regex_rules", mapOf("id" to "g"))
        repeat(2) { db.execute("DELETE FROM characters WHERE id = 'a'") }
        assertEquals(1, db.number("SELECT COUNT(*) FROM variable_config_versions"))
        assertEquals("b", db.text("SELECT characterId FROM character_regex_rules"))
        assertEquals(1, db.number("SELECT COUNT(*) FROM global_regex_rules"))
        assertEquals(0, db.number("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'app_preferences'"))
        assertThrows(SQLException::class.java) {
            insert(db, "variable_configs", mapOf("characterId" to "missing"))
        }
        assertThrows(SQLException::class.java) {
            insert(db, "character_regex_rules", mapOf("characterId" to "missing", "id" to "r"))
        }
    }

    @Test fun `many saved setting versions reuse content and keep their history after current edit`() = database { db ->
        insert(db, "characters", mapOf("id" to "a"))
        insert(db, "setting_libraries", mapOf("characterId" to "a"))
        val original = "设定正文".repeat(8000)
        val originalRevision = "revision-original"
        insert(db, "setting_entry_contents", mapOf("characterId" to "a", "entryId" to "e", "revisionId" to originalRevision, "payloadJson" to original))
        insert(db, "setting_library_entry_links", mapOf("characterId" to "a", "entryId" to "e", "revisionId" to originalRevision))
        repeat(200) { number ->
            insert(db, "setting_library_versions", mapOf("characterId" to "a", "versionId" to "v$number"))
            insert(db, "setting_library_version_entry_links", mapOf("characterId" to "a", "versionId" to "v$number", "entryId" to "e", "revisionId" to originalRevision))
        }
        assertEquals(1, db.number("SELECT COUNT(*) FROM setting_entry_contents"))
        val changed = original + "新剧情"
        val changedRevision = "revision-changed"
        insert(db, "setting_entry_contents", mapOf("characterId" to "a", "entryId" to "e", "revisionId" to changedRevision, "payloadJson" to changed))
        db.execute("UPDATE setting_library_entry_links SET revisionId = '$changedRevision'")
        assertEquals(changed, db.text("SELECT payloadJson FROM setting_library_entries"))
        assertEquals(original, db.text("SELECT payloadJson FROM setting_library_version_entries WHERE versionId = 'v0'"))
        assertEquals(200, db.number("SELECT COUNT(*) FROM setting_library_version_entries"))
        assertThrows(SQLException::class.java) {
            db.execute("DELETE FROM setting_entry_contents WHERE revisionId = '$originalRevision'")
        }
        assertThrows(SQLException::class.java) {
            insert(db, "setting_library_entry_links", mapOf("characterId" to "a", "entryId" to "another-entry", "revisionId" to originalRevision))
        }
        db.execute("DELETE FROM characters WHERE id = 'a'")
        assertEquals(0, db.number("SELECT COUNT(*) FROM setting_entry_contents"))
        assertEquals(0, db.number("SELECT COUNT(*) FROM setting_library_version_entries"))
    }

    @Test fun `failed transaction leaves original rule intact`() = database { db ->
        insert(db, "global_regex_rules", mapOf("id" to "g", "replacement" to "original"))
        db.autoCommit = false
        try {
            db.execute("UPDATE global_regex_rules SET replacement = 'partial'")
            assertThrows(SQLException::class.java) {
                insert(db, "character_regex_rules", mapOf("characterId" to "missing", "id" to "bad"))
            }
        } finally {
            db.rollback()
            db.autoCommit = true
        }
        assertEquals("original", db.text("SELECT replacement FROM global_regex_rules"))
    }

    @Test fun `new business domains share card ownership and cleanup work survives interruption`() = database { db ->
        insert(db, "characters", mapOf("id" to "card-a"))
        insert(db, "frontend_projects", mapOf("id" to "front-a", "characterId" to "card-a"))
        insert(db, "character_frontend_settings", mapOf(
            "characterId" to "card-a",
            "selectedProjectId" to "front-a",
        ))
        insert(db, "creator_workspaces", mapOf("id" to "workspace-a"))
        insert(db, "creator_workspace_character_roots", mapOf(
            "id" to "root-a",
            "workspaceId" to "workspace-a",
            "characterId" to "card-a",
        ))
        insert(db, "creator_workspace_conversations", mapOf(
            "id" to "assistant-chat-a",
            "workspaceId" to "workspace-a",
        ))
        insert(db, "cleanup_operations", mapOf(
            "id" to "cleanup-a",
            "kind" to "frontend_project",
            "targetId" to "card-a::front-a",
            "state" to "failed",
        ))

        assertThrows(SQLException::class.java) {
            insert(db, "frontend_projects", mapOf("id" to "front-b", "characterId" to "missing"))
        }
        db.execute("DELETE FROM characters WHERE id = 'card-a'")

        assertEquals(0, db.number("SELECT COUNT(*) FROM frontend_projects"))
        assertEquals(0, db.number("SELECT COUNT(*) FROM character_frontend_settings"))
        assertEquals(0, db.number("SELECT COUNT(*) FROM creator_workspace_character_roots"))
        assertEquals(1, db.number("SELECT COUNT(*) FROM creator_workspaces"))
        assertEquals(1, db.number("SELECT COUNT(*) FROM creator_workspace_conversations"))
        assertEquals(1, db.number("SELECT COUNT(*) FROM cleanup_operations"))
    }

    @Test fun `one turn can store ordered replies from distinct speakers`() = database { db ->
        insert(db, "agent_conversations", mapOf("id" to "chat-a", "activeBranchId" to "branch-a"))
        insert(db, "agent_branches", mapOf(
            "id" to "branch-a",
            "conversationId" to "chat-a",
            "headSequence" to 0,
        ))
        listOf("user", "member-a", "member-b").forEach { sourceId ->
            insert(db, "conversation_speakers", mapOf(
                "id" to "speaker-$sourceId",
                "conversationId" to "chat-a",
                "sourceSpeakerId" to sourceId,
                "kind" to if (sourceId == "user") "user" else "card_character",
            ))
        }
        insert(db, "agent_turns", mapOf(
            "id" to "turn-a",
            "conversationId" to "chat-a",
            "speakerId" to "speaker-user",
            "sourceMessageId" to "message-user",
        ))
        insert(db, "agent_branch_turns", mapOf(
            "branchId" to "branch-a",
            "sequence" to 0,
            "turnId" to "turn-a",
        ))
        listOf("member-a", "member-b").forEachIndexed { index, sourceId ->
            insert(db, "agent_responses", mapOf(
                "id" to "response-$index",
                "conversationId" to "chat-a",
                "turnId" to "turn-a",
                "responseIndex" to index,
                "speakerId" to "speaker-$sourceId",
                "sourceMessageId" to "message-$sourceId",
                "model" to "execution-model-$index",
            ))
        }

        assertEquals(2, db.number("SELECT COUNT(*) FROM agent_responses WHERE turnId = 'turn-a'"))
        assertEquals(
            "member-a,member-b",
            db.text(
                "SELECT group_concat(s.sourceSpeakerId, ',') " +
                    "FROM agent_responses r JOIN conversation_speakers s ON s.id = r.speakerId " +
                    "WHERE r.turnId = 'turn-a' ORDER BY r.responseIndex",
            ),
        )
        db.execute("DELETE FROM agent_conversations WHERE id = 'chat-a'")
        assertEquals(0, db.number("SELECT COUNT(*) FROM conversation_speakers"))
        assertEquals(0, db.number("SELECT COUNT(*) FROM agent_responses"))
    }

    private fun database(block: (Connection) -> Unit) {
        sqliteDatabase("eleckoi-common-schema-v3.sql", block)
    }

    private fun sqliteDatabase(schemaFileName: String, block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            File(directory, schemaFileName).readLines()
                .filterNot { it.trimStart().startsWith("--") }
                .joinToString("\n").split(';').filter(String::isNotBlank)
                .forEach { db.execute(it) }
            block(db)
            assertEquals("ok", db.text("PRAGMA integrity_check"))
        }
    }

    private fun insert(db: Connection, table: String, values: Map<String, Any>) {
        insertFromSchema(db, schema, table, values)
    }

    private fun insertV1(db: Connection, table: String, values: Map<String, Any>) {
        insertFromSchema(db, version1Schema, table, values)
    }

    private fun insertFromSchema(db: Connection, sourceSchema: JSONObject, table: String, values: Map<String, Any>) {
        val entities = sourceSchema.getJSONArray("entities")
        val entity = (0 until entities.length()).map { entities.getJSONObject(it) }
            .single { it.getString("tableName") == table }
        val fields = entity.getJSONArray("fields")
        val rows = (0 until fields.length()).map { fields.getJSONObject(it) }.associate { field ->
            val name = field.getString("columnName")
            name to (values[name] ?: if (!field.optBoolean("notNull", false)) null
                else if (field.getString("affinity") == "TEXT") "" else 0)
        }
        db.prepareStatement("INSERT INTO $table (${rows.keys.joinToString { "`$it`" }}) VALUES (${rows.keys.joinToString { "?" }})").use { statement ->
            rows.values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    private fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }
    private fun Connection.number(sql: String): Int = createStatement().use { statement ->
        statement.executeQuery(sql).use { it.next(); it.getInt(1) }
    }
    private fun Connection.text(sql: String): String = createStatement().use { statement ->
        statement.executeQuery(sql).use { it.next(); it.getString(1) }
    }

    private fun Connection.columns(table: String): Set<String> = createStatement().use { statement ->
        statement.executeQuery("PRAGMA table_info(`$table`)").use { rows ->
            buildSet { while (rows.next()) add(rows.getString("name")) }
        }
    }

    private fun Connection.foreignKeyViolations(): List<String> = createStatement().use { statement ->
        statement.executeQuery("PRAGMA foreign_key_check").use { rows ->
            buildList {
                while (rows.next()) {
                    add("${rows.getString(1)}:${rows.getString(2)} -> ${rows.getString(3)}#${rows.getInt(4)}")
                }
            }
        }
    }

    private fun schemaTableNames(): Set<String> {
        val entities = schema.getJSONArray("entities")
        return buildSet {
            for (index in 0 until entities.length()) {
                add(entities.getJSONObject(index).getString("tableName"))
            }
        }
    }

    private fun Connection.tableNames(): Set<String> = createStatement().use { statement ->
        statement.executeQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        ).use { rows ->
            buildSet { while (rows.next()) add(rows.getString("name")) }
        }
    }

    private fun freshSchemaStructure(): DatabaseStructure {
        lateinit var structure: DatabaseStructure
        sqliteDatabase("eleckoi-common-schema-v3.sql") { db ->
            structure = db.schemaStructure()
        }
        return structure
    }

    private fun Connection.schemaStructure(): DatabaseStructure = DatabaseStructure(
        tables = tableNames().sorted().associateWith { table -> tableStructure(table) },
        views = createStatement().use { statement ->
            statement.executeQuery(
                "SELECT name, sql FROM sqlite_master WHERE type = 'view' ORDER BY name",
            ).use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(rows.getString("name"), rows.getString("sql").normalizedSql())
                    }
                }
            }
        },
    )

    private fun Connection.tableStructure(table: String): TableStructure = TableStructure(
        columns = createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(`$table`)").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(ColumnStructure(
                            name = rows.getString("name"),
                            type = rows.getString("type"),
                            notNull = rows.getInt("notnull"),
                            defaultValue = rows.getString("dflt_value"),
                            primaryKeyOrder = rows.getInt("pk"),
                        ))
                    }
                }
            }
        },
        foreignKeys = createStatement().use { statement ->
            statement.executeQuery("PRAGMA foreign_key_list(`$table`)").use { rows ->
                buildList {
                    while (rows.next()) {
                        add(ForeignKeyStructure(
                            id = rows.getInt("id"),
                            sequence = rows.getInt("seq"),
                            parentTable = rows.getString("table"),
                            childColumn = rows.getString("from"),
                            parentColumn = rows.getString("to"),
                            onUpdate = rows.getString("on_update"),
                            onDelete = rows.getString("on_delete"),
                        ))
                    }
                }.sortedWith(compareBy(ForeignKeyStructure::id, ForeignKeyStructure::sequence))
            }
        },
        indices = explicitIndices(table),
    )

    private fun Connection.explicitIndices(table: String): List<IndexStructure> {
        val headers = createStatement().use { statement ->
            statement.executeQuery("PRAGMA index_list(`$table`)").use { rows ->
                buildList {
                    while (rows.next()) {
                        val name = rows.getString("name")
                        if (!name.startsWith("sqlite_autoindex_")) {
                            add(Triple(name, rows.getInt("unique"), rows.getString("origin")))
                        }
                    }
                }
            }
        }
        return headers.map { (name, unique, origin) ->
            val columns = createStatement().use { statement ->
                statement.executeQuery("PRAGMA index_info(`$name`)").use { rows ->
                    buildList { while (rows.next()) add(rows.getString("name")) }
                }
            }
            IndexStructure(name, unique, origin, columns)
        }.sortedBy(IndexStructure::name)
    }

    private fun String.normalizedSql(): String = replace(Regex("\\s+"), " ").trim()

    private data class DatabaseStructure(
        val tables: Map<String, TableStructure>,
        val views: Map<String, String>,
    )

    private data class TableStructure(
        val columns: List<ColumnStructure>,
        val foreignKeys: List<ForeignKeyStructure>,
        val indices: List<IndexStructure>,
    )

    private data class ColumnStructure(
        val name: String,
        val type: String,
        val notNull: Int,
        val defaultValue: String?,
        val primaryKeyOrder: Int,
    )

    private data class ForeignKeyStructure(
        val id: Int,
        val sequence: Int,
        val parentTable: String,
        val childColumn: String,
        val parentColumn: String,
        val onUpdate: String,
        val onDelete: String,
    )

    private data class IndexStructure(
        val name: String,
        val unique: Int,
        val origin: String,
        val columns: List<String>,
    )
}
