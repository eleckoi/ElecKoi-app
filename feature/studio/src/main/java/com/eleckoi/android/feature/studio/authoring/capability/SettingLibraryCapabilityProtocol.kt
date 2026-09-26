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
    put("content", creatorStringSchema("完整正文。选读条目设置 content_mode=ejs 后才执行 EJS；必读原文进入缓存区，引用设定的正文只作为纯文字供 getwi 使用。patch 开场白时更新默认开场，patch 角色扮演计划时更新任务项。"))
    put("enabled", creatorBooleanSchema("条目开关：关闭的设定不进入目录，关闭的 EJS 引用设定由 getwi 读取时返回空内容。"))
    put("trigger_mode", creatorStringSchema("agent_tool 为必读或选读设定；always 为每轮直接注入提示词的常驻设定。", listOf("always", "agent_tool")))
    put("agent_read_strategy", creatorStringSchema("仅 agent_tool 使用：required=必读；normal=选读且关键词开关关闭；keyword=选读且关键词开关开启。", listOf("required", "keyword", "normal")))
    put("content_mode", creatorStringSchema("选读设定正文模式：plain_text 原样读取；ejs 在当前变量下渲染，非空才进入本轮必读目录。必读和 EJS 引用设定只能用 plain_text。", listOf("plain_text", "ejs")))
    put("agent_selection_hint", creatorStringSchema("仅供无关键词、纯文字选读条目使用，帮助 Agent 判断是否读取。关键词或 EJS 条件成立后自动列为本轮必读，无需注释。"))
    put("dynamic_mode", creatorStringSchema("standard=普通设定；ejs_reference=独立纯文字引用设定，只能经选读 EJS 正文中的 getwi 引用，不单独进入目录。", listOf("standard", "ejs_reference")))
    put("keywords", creatorArraySchema("仅选读且 agent_read_strategy=keyword 时启用的主关键词。", creatorStringSchema("关键词。"), 100))
    put("keyword_scan_depth", integerSchema("开启关键词命中时扫描最近多少条非空用户/AI 消息；默认 1 只含本次用户消息。", minimum = 1, maximum = 1000))
    put("condition_keywords", creatorArraySchema("开启关键词命中时使用的辅助条件关键词。", creatorStringSchema("关键词。"), 100))
    put("keyword_condition", creatorStringSchema("开启关键词命中时的辅助关键词组合条件。", listOf("none", "any", "all", "not_any")))
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
    put("contentMode", contentMode.storageValue)
    put("dynamicMode", dynamicMode.storageValue)
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
    put("contentMode", contentMode.storageValue)
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
        put("agent_tool", "Agent 读取：必读或选读；选读可叠加关键词命中和 EJS 正文条件，不使用插入位置。")
        put("always", "提示词常驻：启用条目每轮原样注入，不执行 EJS；必须配置 position，或用 prompt_position_id 选择 inspect 返回的自定义位置，并由 insert_role 和 order 控制角色与顺序。")
    })
    put("agentReadStrategies", buildJsonObject {
        put("required", "必读：正文原样进入前置缓存设定区，使用 plain_text；不要把 EJS 写在这里以免影响缓存命中。按本轮目录顺序分配 #S01 等短编号并附标题，读取工具只返回编号和标题；编号不跨轮固定。")
        put("keyword", "选读且关键词开关开启：配置 keywords，可叠加 condition_keywords/keyword_condition；先展开角色卡宏并应用提示词正则，再按 keyword_scan_depth 扫描最近的非空用户/AI 消息。未命中不进目录；命中后若正文为 EJS，还须渲染出非空正文，才列为本轮必读。默认深度 1 只扫描本次用户消息。")
        put("normal", "选读且关键词开关关闭：完全忽略关键词字段。纯文字条目可由 AI 按目录路径、标题和 agent_selection_hint 选择；EJS 条目按当前变量渲染，结果为空不进目录，非空则列为本轮必读。")
    })
    put("dynamicModes", buildJsonObject {
        put("standard", "普通设定：选读可用 content_mode=ejs 执行 EJS，或用 plain_text 保持原文；固定必读正文原样进入缓存区。EJS 渲染为空时本轮不进入目录，非空时列为本轮必读。")
        put("ejs_reference", "独立的纯文字引用设定：供 EJS 正文通过 getwi 按标题或路径引用，正文中的 EJS 语法也只会当作文字；不单独进入 Agent 目录。启用才可引用，关闭后 getwi 返回空内容。")
    })
    put("ejsUsage", buildJsonObject {
        put("mainEntry", "选读 EJS 设定需设置 trigger_mode=agent_tool、dynamic_mode=standard、content_mode=ejs；agent_read_strategy=normal 表示关键词开关关闭，keyword 表示开关开启。关键词和 EJS 同时启用时必须同时成立。可用 getwi 组合多条引用设定。")
        put("referenceFields", "创建引用设定时设置 trigger_mode=agent_tool、agent_read_strategy=normal、dynamic_mode=ejs_reference、content_mode=plain_text。引用正文写在独立条目的 content，始终作为纯文字交给 getwi；条件和 EJS 代码写在主设定中，引用条目可分别启用或关闭。")
        put("getvar", "在 EJS 中用 getvar('对象.变量', { defaults: 0 }) 读取当前变量状态；变量路径对应对象名和变量标题。")
        put("getwi", "在 EJS 中用 await getwi('引用设定标题') 按标题或路径读取引用设定；已关闭的引用设定返回空内容。例如 <% if (getvar('剧情.阶段', { defaults: 0 }) >= 2) { %><%- await getwi('第二阶段设定') %><% } %>。")
    })
    put("requiredReadFlow", "设定搜索结果的 required_entries 是本轮必读清单，不受搜索词是否命中影响；仅搜索不算读取，Agent 仍需调用读取工具。固定必读纯正文已进入缓存，工具以编号和标题回执；关键词命中或 EJS 条件成立的选读项由工具返回正文。")
    put("variablePatchFlow", "变量修改成功后会按新变量值重新判断 EJS。若回执没有 setting_library_changes，无须重读设定；有变化时仅处理 newly_available_paths、stale_read_paths、unavailable_read_paths 指明的路径，并按新目录中的 required_entries 读取新增必读项。后续搜索和读取始终按当前变量值判断。")
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

