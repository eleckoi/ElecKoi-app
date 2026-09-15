package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.engine.creator.capability.CreatorCapability
import com.eleckoi.android.engine.creator.capability.CreatorCapabilityEffect
import com.eleckoi.android.engine.creator.capability.CreatorOperationDefinition
import com.eleckoi.android.engine.creator.capability.CreatorToolsetDefinition
import com.eleckoi.android.engine.workspace.model.CreatorWorkspace
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.feature.characters.model.CharacterSlot
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringContext
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringException
import com.eleckoi.android.feature.studio.authoring.creatorBoolean
import com.eleckoi.android.feature.studio.authoring.creatorBooleanSchema
import com.eleckoi.android.feature.studio.authoring.creatorInt
import com.eleckoi.android.feature.studio.authoring.creatorObjectSchema
import com.eleckoi.android.feature.studio.authoring.creatorString
import com.eleckoi.android.feature.studio.authoring.creatorStringSchema
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CharacterCreatorCapabilities {
    val toolset = CreatorToolsetDefinition(
        id = "creator.character_roots",
        title = "角色创作根",
        description = "管理一个主角色和多个参考角色；按挂载根隔离查看与写入权限。",
    )

    fun capabilities(): List<CreatorCapability<CreatorAuthoringContext, CreatorOperationDefinition>> = listOf(
        capability(
            id = "workspace.get_roots",
            title = "查看角色根",
            description = "分页查看主角色、参考角色及每个根的权限。",
            schema = creatorObjectSchema {
                put("cursor", creatorStringSchema("上一页 nextCursor；首页留空。"))
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("minimum", 1)
                    put("maximum", 50)
                    put("default", 20)
                })
            },
        ) { context, arguments ->
            val offset = arguments.creatorString("cursor").ifBlank { "0" }.toIntOrNull()
                ?: throw CreatorAuthoringException("INVALID_CURSOR", "角色根 cursor 无效")
            if (offset < 0) throw CreatorAuthoringException("INVALID_CURSOR", "角色根 cursor 无效")
            context.rootsJson(
                workspace = context.workspace(),
                offset = offset,
                limit = arguments.creatorInt("limit", 20).coerceIn(1, 50),
            )
        },
        capability(
            id = "workspace.search_characters",
            title = "搜索角色",
            description = "按名称搜索应用里的角色，只返回摘要；搜索结果不会自动挂载。",
            schema = creatorObjectSchema {
                put("query", creatorStringSchema("角色名称筛选词；留空列出角色目录。"))
                put("cursor", creatorStringSchema("上一页 nextCursor；首页留空。"))
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("minimum", 1)
                    put("maximum", 50)
                })
            },
        ) { context, arguments ->
            val query = arguments.creatorString("query")
            val limit = arguments.creatorInt("limit", 20).coerceIn(1, 50)
            val workspace = context.workspace()
            val attached = workspace.characterRoots.associateBy { it.characterId }
            val page = context.service.searchCreatorCharacters(
                query = query,
                cursor = arguments.creatorString("cursor"),
                limit = limit,
            )
            buildJsonObject {
                put("characters", buildJsonArray {
                    page.items.forEach { character ->
                        add(character.summaryJson(attached[character.id]?.id))
                    }
                })
                put("nextCursor", page.nextCursor)
                put("hasMore", page.nextCursor.isNotBlank())
            }
        },
        capability(
            id = "workspace.attach_character",
            title = "挂载角色",
            description = "把一个已有角色挂到当前创作工作区；默认作为只读参考。",
            effect = CreatorCapabilityEffect.Write,
            schema = creatorObjectSchema(required = listOf("character_id")) {
                put("character_id", creatorStringSchema("workspace.search_characters 返回的角色 id。"))
                put("access", creatorStringSchema("挂载权限。", listOf("read_only", "read_write")))
                put("make_primary", creatorBooleanSchema("是否设为主角色。"))
            },
        ) { context, arguments ->
            context.requireWritePermission()
            val characterId = arguments.creatorString("character_id")
            val access = when (arguments.creatorString("access")) {
                "", "read_only" -> CreatorWorkspaceRootAccess.ReadOnly
                "read_write" -> CreatorWorkspaceRootAccess.ReadWrite
                else -> throw CreatorAuthoringException("INVALID_ARGUMENTS", "access 必须是 read_only 或 read_write")
            }
            val workspace = context.service.attachCreatorCharacter(
                workspaceId = context.workspaceId,
                characterId = characterId,
                access = access,
                makePrimary = arguments.creatorBoolean("make_primary"),
            )
            context.rootsJson(workspace)
        },
        capability(
            id = "workspace.detach_root",
            title = "移除角色根",
            description = "从创作工作区移除挂载，不删除真实角色或设定库。",
            effect = CreatorCapabilityEffect.Write,
            schema = rootIdSchema(),
        ) { context, arguments ->
            context.requireWritePermission()
            val updated = context.service.detachCreatorCharacter(
                context.workspaceId,
                arguments.requiredRootId(),
            )
            context.rootsJson(updated)
        },
        capability(
            id = "workspace.set_primary",
            title = "设置主角色",
            description = "把已挂载角色设为当前创作焦点；原主角色降为只读参考。",
            effect = CreatorCapabilityEffect.Write,
            schema = rootIdSchema(),
        ) { context, arguments ->
            context.requireWritePermission()
            val updated = context.service.setPrimaryCreatorCharacter(
                context.workspaceId,
                arguments.requiredRootId(),
            )
            context.rootsJson(updated)
        },
        capability(
            id = "workspace.set_root_access",
            title = "设置角色根权限",
            description = "把参考角色设为只读或可写；主角色必须保持可写。",
            effect = CreatorCapabilityEffect.Write,
            schema = creatorObjectSchema(required = listOf("root_id", "access")) {
                put("root_id", creatorStringSchema("已挂载角色根 id。"))
                put("access", creatorStringSchema("目标权限。", listOf("read_only", "read_write")))
            },
        ) { context, arguments ->
            context.requireWritePermission()
            val access = when (arguments.creatorString("access")) {
                "read_only" -> CreatorWorkspaceRootAccess.ReadOnly
                "read_write" -> CreatorWorkspaceRootAccess.ReadWrite
                else -> throw CreatorAuthoringException("INVALID_ARGUMENTS", "access 必须是 read_only 或 read_write")
            }
            val updated = context.service.setCreatorCharacterAccess(
                context.workspaceId,
                arguments.requiredRootId(),
                access,
            )
            context.rootsJson(updated)
        },
        capability(
            id = "character.create_and_attach",
            title = "创建并挂载角色",
            description = "从零创建剧情角色、初始化设定库，并作为当前工作区主角色。",
            effect = CreatorCapabilityEffect.Write,
            schema = creatorObjectSchema(required = listOf("name")) {
                put("name", creatorStringSchema("新角色名称。"))
                put("group", creatorStringSchema("可选的角色分组。"))
            },
        ) { context, arguments ->
            context.requireWritePermission()
            val name = arguments.creatorString("name")
                .takeIf(String::isNotBlank)
                ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "name 不能为空")
            val (workspace, character) = context.service.createAndAttachCreatorCharacter(
                workspaceId = context.workspaceId,
                name = name,
                group = arguments.creatorString("group"),
            )
            buildJsonObject {
                put("created", character.summaryJson(workspace.primaryCharacterRootId))
                put("workspace", context.rootsJson(workspace))
            }
        },
    )

    private fun capability(
        id: String,
        title: String,
        description: String,
        effect: CreatorCapabilityEffect = CreatorCapabilityEffect.Read,
        schema: kotlinx.serialization.json.JsonObject = creatorObjectSchema {},
        handler: suspend (CreatorAuthoringContext, kotlinx.serialization.json.JsonObject) -> kotlinx.serialization.json.JsonElement,
    ) = CreatorCapability(
        definition = CreatorOperationDefinition(
            capabilityId = id,
            toolsetId = toolset.id,
            title = title,
            description = description,
            effect = effect,
            inputSchema = schema,
        ),
        handler = handler,
    )

    private fun rootIdSchema() = creatorObjectSchema(required = listOf("root_id")) {
        put("root_id", creatorStringSchema("workspace.get_roots 返回的角色根 id。"))
    }

    private fun kotlinx.serialization.json.JsonObject.requiredRootId(): String =
        creatorString("root_id").takeIf(String::isNotBlank)
            ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "root_id 不能为空")

    private fun CreatorWorkspace.rootsJson(
        characters: List<CharacterSlot>,
        offset: Int,
        limit: Int,
    ) = buildJsonObject {
        val byId = characters.associateBy(CharacterSlot::id)
        val boundedOffset = offset.coerceAtMost(characterRoots.size)
        val page = characterRoots.drop(boundedOffset).take(limit)
        val nextOffset = boundedOffset + page.size
        put("workspaceId", id)
        put("primaryRootId", primaryCharacterRootId.orEmpty())
        put("rootCount", characterRoots.size)
        put("roots", buildJsonArray {
            page.forEach { root ->
                val character = byId[root.characterId]
                add(buildJsonObject {
                    put("rootId", root.id)
                    put("characterId", root.characterId)
                    put("name", character?.name ?: root.alias.ifBlank { "已删除角色" })
                    put("primary", root.id == primaryCharacterRootId)
                    put("access", if (root.access == CreatorWorkspaceRootAccess.ReadWrite) "read_write" else "read_only")
                    put("available", character != null)
                })
            }
        })
        put("nextCursor", if (nextOffset < characterRoots.size) nextOffset.toString() else "")
        put("hasMore", nextOffset < characterRoots.size)
    }

    private suspend fun CreatorAuthoringContext.rootsJson(
        workspace: CreatorWorkspace,
        offset: Int = 0,
        limit: Int = 20,
    ): kotlinx.serialization.json.JsonObject {
        val boundedOffset = offset.coerceAtMost(workspace.characterRoots.size)
        val page = workspace.characterRoots.drop(boundedOffset).take(limit)
        val characters = page.mapNotNull { root -> service.creatorCharacter(root.characterId) }
        return workspace.rootsJson(characters, boundedOffset, limit)
    }

    private fun CharacterSlot.summaryJson(rootId: String?) = buildJsonObject {
        put("id", id)
        put("name", name)
        put("group", group)
        put("rootId", rootId.orEmpty())
        put("attached", rootId != null)
    }
}
