package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.engine.creator.capability.CreatorCapability
import com.eleckoi.android.engine.creator.capability.CreatorCapabilityEffect
import com.eleckoi.android.engine.creator.capability.CreatorOperationDefinition
import com.eleckoi.android.engine.creator.capability.CreatorToolsetDefinition
import com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess
import com.eleckoi.android.engine.generation.model.ImageGenerationProvider
import com.eleckoi.android.feature.characters.model.AvatarSlot
import com.eleckoi.android.feature.studio.api.CreatorCharacterMediaState
import com.eleckoi.android.feature.studio.api.CreatorMediaAsset
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringContext
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringException
import com.eleckoi.android.feature.studio.authoring.creatorArray
import com.eleckoi.android.feature.studio.authoring.creatorArraySchema
import com.eleckoi.android.feature.studio.authoring.creatorInt
import com.eleckoi.android.feature.studio.authoring.creatorObjectSchema
import com.eleckoi.android.feature.studio.authoring.creatorString
import com.eleckoi.android.feature.studio.authoring.creatorStringSchema
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal enum class CharacterMediaChangeAction {
    Assign,
    Clear,
}

internal data class CharacterMediaPendingChange(
    val id: String,
    val rootId: String,
    val baseRevision: String,
    val action: CharacterMediaChangeAction,
    val assetId: String,
    val slots: Set<AvatarSlot>,
)

internal class CharacterMediaCreatorChangeStore {
    private val pending = ConcurrentHashMap<String, CharacterMediaPendingChange>()

    fun put(change: CharacterMediaPendingChange) {
        pending[change.id] = change
    }

    fun get(id: String): CharacterMediaPendingChange? = pending[id]

    fun remove(id: String): CharacterMediaPendingChange? = pending.remove(id)
}

internal object CharacterMediaCreatorCapabilities {
    val toolset = CreatorToolsetDefinition(
        id = "creator.character_media",
        title = "角色图片",
        description = "制作和管理角色的圆形头像、方形头像与 3:4 立绘；可使用工作区永久图片的 asset_id，也可使用当前对话附件提供的临时 asset_id。",
    )

    fun capabilities(): List<CreatorCapability<CreatorAuthoringContext, CreatorOperationDefinition>> = listOf(
        capability(
            id = "character_media.inspect",
            title = "查看角色图片槽位",
            description = "查看指定角色根的三个图片槽位及 revision，不返回设备文件路径。",
            schema = rootSchema(),
        ) { context, arguments ->
            val rootId = context.resolveRootId(arguments.creatorString("root_id"))
            context.service.creatorCharacterMedia(context.workspaceId, rootId).toJson()
        },
        capability(
            id = "character_media.generation_settings",
            title = "查看绘画模型",
            description = "生成图片前读取当前绘画提供商与提示词格式，不返回密钥或接口地址。",
            schema = creatorObjectSchema {},
        ) { context, _ ->
            val provider = context.service.creatorImageGenerationProvider()
                ?: throw CreatorAuthoringException(
                    "IMAGE_MODEL_UNAVAILABLE",
                    "请先在「创作能力」中选择绘图模型",
                )
            buildJsonObject {
                put("provider", provider.id)
                put(
                    "promptFormat",
                    if (provider == ImageGenerationProvider.OpenAi) {
                        "使用自然语言描述画面、人物、构图和光线；negative_prompt 表示避免的内容，可留空。"
                    } else {
                        "使用逗号分隔的英文 NovelAI 视觉标签；negative_prompt 使用英文负面标签。"
                    },
                )
            }
        },
        capability(
            id = "character_media.generate_asset",
            title = "生成创作图片",
            description = "调用当前选择的绘画模型生成一张图片并登记为工作区 asset_id；只生成候选图，不会自动设为头像或立绘。",
            effect = CreatorCapabilityEffect.Preview,
            schema = creatorObjectSchema(required = listOf("prompt")) {
                put("prompt", creatorStringSchema("先读取 generation_settings：OpenAI 使用自然语言画面描述，NovelAI 使用英文视觉标签；最多 4000 字符。"))
                put("negative_prompt", creatorStringSchema("需要避免的内容；OpenAI 使用自然语言，NovelAI 使用英文负面标签。最多 2000 字符；可留空。"))
                put("display_name", creatorStringSchema("候选图名称，方便作者选择；可留空。"))
            },
        ) { context, arguments ->
            val prompt = arguments.creatorString("prompt")
            if (prompt.isBlank()) {
                throw CreatorAuthoringException("INVALID_ARGUMENTS", "prompt 不能为空")
            }
            val asset = try {
                context.service.generateCreatorMediaAsset(
                    workspaceId = context.workspaceId,
                    prompt = prompt,
                    negativePrompt = arguments.creatorString("negative_prompt"),
                    displayName = arguments.creatorString("display_name"),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                val detail = error.message
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.take(240)
                    .orEmpty()
                    .ifBlank { error.javaClass.simpleName.ifBlank { "未知错误" } }
                throw CreatorAuthoringException(
                    code = "IMAGE_GENERATION_FAILED",
                    message = "生图失败：$detail",
                )
            }
            buildJsonObject {
                put("status", "generated")
                put("asset", asset.toJson())
                put("assignedToCharacter", false)
                put("nextStep", "让作者先选择这张候选图；确认槽位后再 preview_change 和 apply_change")
            }
        },
        capability(
            id = "character_media.list_assets",
            title = "列出创作图片资产",
            description = "分页列出已登记到当前工作区的永久图片；返回稳定 asset_id。对话上传附件不会进入这里。",
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
            val page = context.service.searchCreatorMediaAssets(
                workspaceId = context.workspaceId,
                cursor = arguments.creatorString("cursor"),
                limit = arguments.creatorInt("limit", 20).coerceIn(1, 50),
            )
            buildJsonObject {
                put("assets", buildJsonArray { page.items.forEach { add(it.toJson()) } })
                put("nextCursor", page.nextCursor)
                put("hasMore", page.nextCursor.isNotBlank())
            }
        },
        capability(
            id = "character_media.preview_change",
            title = "预览角色图片修改",
            description = "校验用某个 asset 替换图片槽位，或清空指定槽位；只生成预览，不保存。",
            effect = CreatorCapabilityEffect.Preview,
            schema = creatorObjectSchema(required = listOf("action", "slots")) {
                put("root_id", creatorStringSchema("可写角色根 id；留空使用主角色。"))
                put("base_revision", creatorStringSchema("可选的读取版本提示；预览始终自动基于最新角色图片状态，不会因该值过期而失败。"))
                put("action", creatorStringSchema("修改动作。", listOf("assign", "clear")))
                put("asset_id", creatorStringSchema("assign 时必填；来自 list_assets，或当前用户消息提供的临时附件引用。"))
                put(
                    "slots",
                    creatorArraySchema(
                        description = "目标槽位，可一次选择一个或多个。",
                        items = creatorStringSchema(
                            "角色图片槽位。",
                            listOf("avatar_circle", "avatar_square", "portrait"),
                        ),
                        maxItems = 3,
                    ),
                )
            },
        ) { context, arguments ->
            val rootId = context.resolveRootId(arguments.creatorString("root_id"))
            context.requireWritableRoot(rootId)
            val state = context.service.creatorCharacterMedia(context.workspaceId, rootId)
            val action = when (arguments.creatorString("action")) {
                "assign" -> CharacterMediaChangeAction.Assign
                "clear" -> CharacterMediaChangeAction.Clear
                else -> throw CreatorAuthoringException("INVALID_ARGUMENTS", "action 必须是 assign 或 clear")
            }
            val slots = arguments.creatorArray("slots")
                ?.mapIndexed { index, element ->
                    val raw = (element as? JsonPrimitive)?.contentOrNull.orEmpty()
                    AvatarSlot.fromCreatorValue(raw)
                        ?: throw CreatorAuthoringException("INVALID_ARGUMENTS", "slots[$index] 无效：$raw")
                }
                ?.toSet()
                .orEmpty()
            if (slots.isEmpty()) throw CreatorAuthoringException("INVALID_ARGUMENTS", "slots 不能为空")
            val assetId = arguments.creatorString("asset_id")
            val asset = if (action == CharacterMediaChangeAction.Assign) {
                if (assetId.isBlank()) throw CreatorAuthoringException("INVALID_ARGUMENTS", "assign 必须提供 asset_id")
                context.service.creatorMediaAsset(context.workspaceId, assetId)
                    ?: throw CreatorAuthoringException("ASSET_NOT_FOUND", "找不到创作图片 asset：$assetId")
            } else {
                null
            }
            val changeSetId = "media-change-${UUID.randomUUID()}"
            context.characterMediaChanges.put(
                CharacterMediaPendingChange(
                    id = changeSetId,
                    rootId = rootId,
                    baseRevision = state.revision,
                    action = action,
                    assetId = assetId,
                    slots = slots,
                ),
            )
            buildJsonObject {
                put("valid", true)
                put("changeSetId", changeSetId)
                put("rootId", rootId)
                put("baseRevision", state.revision)
                put("action", if (action == CharacterMediaChangeAction.Assign) "assign" else "clear")
                put("slots", buildJsonArray { slots.forEach { add(JsonPrimitive(it.creatorValue)) } })
                if (asset != null) put("asset", asset.toJson())
                put("cropMode", if (asset != null) "center_crop_per_slot" else "none")
                put("requiresWritePermission", context.currentPermissionMode() == com.eleckoi.android.engine.agent.api.AgentPermissionMode.AskForApproval)
            }
        },
        capability(
            id = "character_media.apply_change",
            title = "提交角色图片修改",
            description = "提交已预览的角色图片变更；绑定会为每个槽位分别居中裁剪并复制为角色永久媒体。",
            effect = CreatorCapabilityEffect.Write,
            schema = creatorObjectSchema(required = listOf("change_set_id")) {
                put("change_set_id", creatorStringSchema("preview_change 返回的 changeSetId。"))
            },
        ) { context, arguments ->
            context.requireWritePermission()
            val changeSetId = arguments.creatorString("change_set_id")
            val change = context.characterMediaChanges.get(changeSetId)
                ?: throw CreatorAuthoringException("CHANGE_SET_NOT_FOUND", "角色图片变更集不存在或当前会话已经重建")
            context.requireWritableRoot(change.rootId)
            val current = context.service.creatorCharacterMedia(context.workspaceId, change.rootId)
            if (current.revision != change.baseRevision) {
                context.characterMediaChanges.remove(changeSetId)
                throw CreatorAuthoringException("REVISION_CONFLICT", "角色图片已经变化，旧变更集没有提交")
            }
            when (change.action) {
                CharacterMediaChangeAction.Assign -> context.service.applyCreatorMediaAsset(
                    workspaceId = context.workspaceId,
                    rootId = change.rootId,
                    assetId = change.assetId,
                    slots = change.slots,
                )
                CharacterMediaChangeAction.Clear -> context.service.clearCreatorCharacterMedia(
                    workspaceId = context.workspaceId,
                    rootId = change.rootId,
                    slots = change.slots,
                )
            }
            context.characterMediaChanges.remove(changeSetId)
            buildJsonObject {
                put("status", "applied")
                put("changeSetId", changeSetId)
                put("media", context.service.creatorCharacterMedia(context.workspaceId, change.rootId).toJson())
            }
        },
    )

    private fun capability(
        id: String,
        title: String,
        description: String,
        effect: CreatorCapabilityEffect = CreatorCapabilityEffect.Read,
        schema: JsonObject,
        handler: suspend (CreatorAuthoringContext, JsonObject) -> JsonElement,
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

    private fun rootSchema() = creatorObjectSchema {
        put("root_id", creatorStringSchema("已挂载角色根 id；留空使用主角色。"))
    }

    private suspend fun CreatorAuthoringContext.resolveRootId(requested: String): String {
        val workspace = workspace()
        val rootId = requested.ifBlank { workspace.primaryCharacterRootId.orEmpty() }
        if (rootId.isBlank() || workspace.characterRoots.none { it.id == rootId }) {
            throw CreatorAuthoringException("ROOT_NOT_FOUND", "创作工作区没有这个角色根")
        }
        return rootId
    }

    private suspend fun CreatorAuthoringContext.requireWritableRoot(rootId: String) {
        val root = workspace().characterRoots.firstOrNull { it.id == rootId }
            ?: throw CreatorAuthoringException("ROOT_NOT_FOUND", "创作工作区没有这个角色根")
        if (root.access != CreatorWorkspaceRootAccess.ReadWrite) {
            throw CreatorAuthoringException("ROOT_READ_ONLY", "这个角色根是只读参考，不能修改角色图片")
        }
    }

    private fun CreatorCharacterMediaState.toJson() = buildJsonObject {
        put("rootId", rootId)
        put("characterId", characterId)
        put("characterName", characterName)
        put("revision", revision)
        put("slots", buildJsonArray {
            AvatarSlot.entries.forEach { slot ->
                add(buildJsonObject {
                    put("id", slot.creatorValue)
                    put("label", slot.label)
                    put("ratio", slot.ratioLabel)
                    put("configured", slot in configuredSlots)
                })
            }
        })
    }

    private fun CreatorMediaAsset.toJson() = buildJsonObject {
        put("assetId", id)
        put("displayName", displayName)
        put("mimeType", mimeType)
        put("width", width)
        put("height", height)
        put("byteSize", byteSize)
        put("source", source.storageValue)
        put("createdAt", createdAt)
    }
}

private val AvatarSlot.creatorValue: String
    get() = when (this) {
        AvatarSlot.Circle -> "avatar_circle"
        AvatarSlot.Square -> "avatar_square"
        AvatarSlot.Portrait -> "portrait"
    }

private fun AvatarSlot.Companion.fromCreatorValue(value: String): AvatarSlot? = when (value) {
    "avatar_circle" -> AvatarSlot.Circle
    "avatar_square" -> AvatarSlot.Square
    "portrait" -> AvatarSlot.Portrait
    else -> null
}
