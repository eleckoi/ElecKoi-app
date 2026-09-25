package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryDynamicMode
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryGroup
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryInsertRole
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPosition
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.SettingLibraryPromptPositionSide
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isOpeningEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.isPinnedEntry
import com.eleckoi.android.feature.characters.modes.story.settinglibrary.model.settingLibraryOpeningEntry
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringContext
import com.eleckoi.android.feature.studio.authoring.CreatorAuthoringException
import com.eleckoi.android.feature.studio.authoring.creatorArraySchema
import com.eleckoi.android.feature.studio.authoring.creatorBooleanSchema
import com.eleckoi.android.feature.studio.authoring.creatorObjectSchema
import com.eleckoi.android.feature.studio.authoring.creatorStringSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun rootSchema() = creatorObjectSchema {
    put("root_id", creatorStringSchema("已挂载角色根 id；留空使用主角色。"))
}

internal fun pageLimitSchema(default: Int) = buildJsonObject {
    put("type", "integer")
    put("minimum", 1)
    put("maximum", 50)
    put("default", default)
    put("description", "单页数据库行数；最多 50。")
}

internal fun changeOperationSchema() = creatorObjectSchema(required = listOf("op")) {
    put("op", creatorStringSchema(
        "修改类型。",
        listOf(
            "create_group",
            "patch_group",
            "delete_group",
            "create_prompt_position",
            "patch_prompt_position",
            "delete_prompt_position",
            "create_entry",
            "patch_entry",
            "delete_entry",
        ),
    ))
    put("id", creatorStringSchema("现有目标 id；创建时可作为临时/稳定 id，留空由宿主生成。"))
    put("parent_id", creatorStringSchema("分组父 id；空字符串表示根级。"))
    put("group_id", creatorStringSchema("条目所属分组 id；空字符串表示根级。"))
    put("name", creatorStringSchema("分组或自定义提示词位置名称。不得为了试写、验证工具或占位而创建测试分组。"))
    put("title", creatorStringSchema("普通条目标题；固定开场白和计划的标题由系统保留。"))
    put("icon_id", creatorStringSchema("普通条目图标 id。"))
    put("content", creatorStringSchema("条目完整正文；patch 开场白时更新默认开场，patch 角色扮演计划时更新任务项。"))
    put("enabled", creatorBooleanSchema("条目是否启用。"))
    put("trigger_mode", creatorStringSchema("触发模式。", listOf("always", "agent_tool")))
    put("agent_read_strategy", creatorStringSchema("Agent 读取策略。", listOf("required", "keyword", "normal", "variable_condition")))
    put("agent_selection_hint", creatorStringSchema("帮助 Agent 判断是否读取的简短提示。"))
    put("dynamic_mode", creatorStringSchema("动态内容模式。", SettingLibraryDynamicMode.entries.map { it.storageValue }))
    put("keywords", creatorArraySchema("关键词。", creatorStringSchema("关键词。"), 100))
    put("keyword_scan_depth", integerSchema("关键词扫描最近多少条用户/AI 消息。", minimum = 1, maximum = 1000))
    put("condition_keywords", creatorArraySchema("辅助条件关键词。", creatorStringSchema("关键词。"), 100))
    put("keyword_condition", creatorStringSchema("关键词组合条件。", listOf("none", "any", "all", "not_any")))
    put("keyword_use_regex", creatorBooleanSchema("关键词是否按 SillyTavern 兼容正则解析。"))
    put("keyword_ignore_case", creatorBooleanSchema("关键词匹配是否忽略大小写。"))
    put("keyword_whole_word", creatorBooleanSchema("非中文普通关键词是否要求完整单词。"))
    put("keyword_recursion_depth", integerSchema("关键词关联递归轮数；0 表示关闭。", minimum = 0, maximum = 10))
    put("position", creatorStringSchema("内置插入锚点。", SettingLibraryPosition.entries.map { it.storageValue }))
    put("prompt_position_id", creatorStringSchema("自定义插入位置 id。"))
    put("insert_role", creatorStringSchema("插入消息角色。", SettingLibraryInsertRole.entries.map { it.storageValue }))
    put("anchor", creatorStringSchema("自定义提示词位置对应的运行时锚点。", SettingLibraryPosition.entries.map { it.storageValue }))
    put("side", creatorStringSchema("自定义提示词位置位于设定插入点之前或之后。", SettingLibraryPromptPositionSide.entries.map { it.storageValue }))
    put("order", integerSchema("同一提示词位置内的注入顺序，或自定义位置顺序。", minimum = 1))
    put("tree_view_order", integerSchema("分组/条目在 AI 目录树中的顺序。", minimum = 1))
    put("opening_messages", creatorArraySchema(
        "开场白消息全集；仅用于固定开场白条目。",
        creatorObjectSchema(required = listOf("content")) {
            put("id", creatorStringSchema("稳定消息 id；留空由宿主生成。"))
            put("title", creatorStringSchema("开场白方案标题，可为空。"))
            put("content", creatorStringSchema("开场白正文。"))
            put("initial_variable_state_json", creatorStringSchema("这条开场白对应的初始变量状态 JSON。"))
        },
        50,
    ))
    put("default_opening_message_id", creatorStringSchema("默认开场白消息 id。"))
}

internal fun integerSchema(
    description: String,
    minimum: Int? = null,
    maximum: Int? = null,
) = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
}

internal suspend fun CreatorAuthoringContext.resolveRootId(requested: String): String {
    val workspace = workspace()
    val rootId = requested.ifBlank { workspace.primaryCharacterRootId.orEmpty() }
    if (rootId.isBlank()) {
        throw CreatorAuthoringException("PRIMARY_ROOT_REQUIRED", "当前没有主角色，请指定已挂载 root_id 或先设置主角色")
    }
    if (workspace.characterRoots.none { it.id == rootId }) {
        throw CreatorAuthoringException("ROOT_NOT_FOUND", "角色根没有挂载到当前工作区：$rootId")
    }
    return rootId
}

internal suspend fun CreatorAuthoringContext.requireWritableRoot(rootId: String) {
    val root = workspace().characterRoots.firstOrNull { it.id == rootId }
        ?: throw CreatorAuthoringException("ROOT_NOT_FOUND", "角色根没有挂载到当前工作区：$rootId")
    if (root.access != com.eleckoi.android.engine.workspace.model.CreatorWorkspaceRootAccess.ReadWrite) {
        throw CreatorAuthoringException("ROOT_READ_ONLY", "这个参考角色当前是只读的，请先显式提升为可写")
    }
}

internal fun SettingLibraryEntry.summaryJson() = buildJsonObject {
    put("id", id)
    put("title", title)
    put("kind", kind.storageValue)
    put("groupId", groupId)
    put("enabled", enabled)
    put("triggerMode", triggerMode?.storageValue.orEmpty())
    put("agentReadStrategy", agentReadStrategy.storageValue)
    put("contentPreview", content.compactPreview())
    put("pinned", isPinnedEntry())
    put("editable", !isPinnedEntry() || isOpeningEntry())
    put("deletable", !isPinnedEntry())
}

internal fun SettingLibraryGroup.summaryJson() = buildJsonObject {
    put("id", id)
    put("name", name)
    put("parentId", parentId)
    put("order", order)
    put("treeViewOrder", treeViewOrder)
}

internal fun SettingLibraryPromptPosition.summaryJson() = buildJsonObject {
    put("id", id)
    put("name", name)
    put("anchor", anchor.storageValue)
    put("side", side.storageValue)
    put("order", order)
}

internal fun SettingLibraryEntry.fullJson(
    contentChunk: String,
    contentField: String,
    selectedOpeningMessageId: String,
): JsonObject {
    val opening = takeIf { it.isOpeningEntry() }?.let(::settingLibraryOpeningEntry)
    return buildJsonObject {
    put("id", id)
    put("title", title)
    put("iconId", iconId)
    put("kind", kind.storageValue)
    put("groupId", groupId)
    put("contentField", contentField)
    put("content", contentChunk)
    put("enabled", enabled)
    put("triggerMode", triggerMode?.storageValue.orEmpty())
    put("agentReadStrategy", agentReadStrategy.storageValue)
    put("agentSelectionHint", agentSelectionHint)
    put("dynamicMode", dynamicMode.storageValue)
    put("keywords", buildJsonArray { keywords.forEach { add(JsonPrimitive(it)) } })
    put("keywordScanDepth", keywordScanDepth)
    put("conditionKeywords", buildJsonArray { conditionKeywords.forEach { add(JsonPrimitive(it)) } })
    put("keywordCondition", keywordCondition.storageValue)
    put("keywordUseRegex", keywordUseRegex)
    put("keywordIgnoreCase", keywordIgnoreCase)
    put("keywordWholeWord", keywordWholeWord)
    put("keywordRecursionDepth", keywordRecursionDepth)
    put("position", position?.storageValue.orEmpty())
    put("promptPositionId", promptPositionId)
    put("insertRole", insertRole.storageValue)
    put("order", order)
    put("treeViewOrder", treeViewOrder)
    put("pinned", isPinnedEntry())
    put("editable", !isPinnedEntry() || isOpeningEntry())
    put("deletable", !isPinnedEntry())
    if (opening != null) {
        put("defaultOpeningMessageId", opening.defaultOpeningMessageId)
        put("selectedOpeningMessageId", selectedOpeningMessageId)
        put("openingMessages", buildJsonArray {
            opening.openingMessages.forEach { message ->
                add(buildJsonObject {
                    put("id", message.id)
                    put("title", message.title)
                    put("contentLength", message.content.length)
                    put("initialVariableStateLength", message.initialVariableStateJson.length)
                })
            }
        })
    }
    }
}

internal fun authoringGuideJson() = buildJsonObject {
    put("writeWorkflow", buildJsonArray {
        add(JsonPrimitive("先 inspect/read 确认目标和现状，再 preview_changes；预览自动使用最新快照并只在内存校验，确认后才 apply_changes。"))
        add(JsonPrimitive("不得为了测试工具、验证写入或占位而创建分组、条目或其他持久数据；除非作者明确要求测试数据。"))
        add(JsonPrimitive("默认把新条目放在根级；只有作者要求分类，或现有卡片结构明确需要分组时才 create_group。"))
    })
    put("fixedEntries", buildJsonObject {
        put("opening", "AI角色开场白可修改正文、启用状态、多个 opening_messages、默认开场和初始变量状态；不可删除。")
        put("roleplayPlan", "角色扮演任务计划可修改任务正文和启用状态；正文按非空行保存为固定任务项；不可删除。")
    })
    put("triggerModes", buildJsonObject {
        put("agent_tool", "Agent 读取：启用条目进入 AI 可见目录，由读取策略决定必读、关键词提升、按需选择或变量条件提升；不使用插入位置。")
        put("always", "提示词常驻：启用条目每轮自动注入；必须配置 position，或用 prompt_position_id 选择 inspect 返回的自定义位置，并由 insert_role 和 order 控制角色与顺序。")
    })
    put("agentReadStrategies", buildJsonObject {
        put("required", "固定必读：正文每轮进入前置缓存设定区，按本轮目录顺序分配 #S01 等短编号并附标题；搜索仍列入 required_entries，读取工具只返回编号和标题。本轮编号不是跨轮持久 ID。")
        put("keyword", "关键词：先展开角色卡宏并应用提示词正则，再按 keyword_scan_depth 扫描最近的非空用户/AI 消息；命中 keywords 且满足 condition_keywords/keyword_condition 后提升为本轮必读，读取工具返回正文。默认深度 1 只扫描本次用户消息。")
        put("normal", "按需：AI 根据目录路径、标题和 agent_selection_hint 判断是否读取。")
        put("variable_condition", "变量条件：条目以 ejs_controller 模式执行 EJS；非空渲染结果提升为本轮必读，读取工具返回渲染后的正文。ejs_reference 仅供控制器引用，不独立作为 Agent 可读条目。")
    })
    put("dynamicModes", buildJsonObject {
        put("standard", "普通设定正文；用于必读、关键词、按需等读取策略。")
        put("ejs_controller", "变量条件的 EJS 控制器：content 填 EJS 模板代码，也可直接写输出正文；可用 getvar 判断变量、getwi 引用独立的 EJS 引用设定。只有渲染结果非空时才进入本轮 Agent 目录。")
        put("ejs_reference", "独立创建的 EJS 引用设定：content 填设定正文，可由控制器通过 getwi 按标题或路径读取；自身不直接进入 Agent 目录，也不替代控制器。")
    })
    put("ejsUsage", buildJsonObject {
        put("controllerFields", "创建控制器时设置 trigger_mode=agent_tool、agent_read_strategy=variable_condition、dynamic_mode=ejs_controller；EJS 代码写在 content。")
        put("referenceFields", "创建引用设定时设置 trigger_mode=agent_tool、agent_read_strategy=variable_condition、dynamic_mode=ejs_reference；设定正文写在独立条目的 content。")
        put("getvar", "在 EJS 中用 getvar('对象.变量', { defaults: 0 }) 读取当前变量状态；变量路径对应对象名和变量标题。")
        put("getwi", "在 EJS 中用 await getwi('引用设定标题') 按标题或路径读取已启用的引用设定；例如 <% if (getvar('剧情.阶段', { defaults: 0 }) >= 2) { %><%- await getwi('第二阶段设定') %><% } %>。")
    })
    put("requiredReadFlow", "设定搜索结果的 required_entries 是本轮必读清单，搜索命中与否不影响该清单；Agent 仍需调用读取工具。固定必读以编号和标题回执，关键词及 EJS 动态必读以正文回执；仅搜索不算读取。")
    put("keywordRules", buildJsonObject {
        put("keyword_use_regex", "true 时按 SillyTavern 兼容正则解析关键词。")
        put("keyword_ignore_case", "控制大小写敏感。")
        put("keyword_whole_word", "控制非中文普通关键词是否完整单词匹配。")
        put("keyword_recursion_depth", "允许已命中条目正文继续关联其他关键词条目的轮数；0 为关闭，最大 10。")
        put("keyword_condition", "辅助关键词组合：none/any/all/not_any。")
    })
    put("residentPositions", buildJsonObject {
        put("builtIn", buildJsonArray {
            SettingLibraryPosition.entries.forEach { position ->
                add(buildJsonObject {
                    put("value", position.storageValue)
                    put("label", position.label)
                })
            }
        })
        put("custom", "自定义位置由 create/patch/delete_prompt_position 管理；inspect 的 promptPositions 返回 side，指定在设定插入点之前或之后，order 控制同侧顺序。")
        put("roles", buildJsonArray {
            SettingLibraryInsertRole.entries.forEach { role ->
                add(buildJsonObject {
                    put("value", role.storageValue)
                    put("label", role.label)
                })
            }
        })
    })
}

private fun String.compactPreview(): String = replace(Regex("\\s+"), " ").trim().take(160)

