package com.eleckoi.android.feature.studio.authoring.capability

import com.eleckoi.android.engine.story.variables.model.VariableConfig
import com.eleckoi.android.engine.story.variables.model.VariableConfigVersion
import com.eleckoi.android.engine.story.variables.model.VariableItemConfig
import com.eleckoi.android.engine.story.variables.model.VariableObjectConfig
import com.eleckoi.android.engine.story.variables.model.VariableReadMode
import com.eleckoi.android.engine.story.variables.model.VariableValueType
import com.eleckoi.android.engine.story.variables.model.isInitializationObject
import com.eleckoi.android.feature.studio.authoring.creatorArraySchema
import com.eleckoi.android.feature.studio.authoring.creatorBooleanSchema
import com.eleckoi.android.feature.studio.authoring.creatorObjectSchema
import com.eleckoi.android.feature.studio.authoring.creatorStringSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun variableRootSchema() = creatorObjectSchema {
    put("root_id", creatorStringSchema("已挂载角色根 id；留空使用主角色。"))
}

internal fun variablePageLimitSchema() = variableIntegerSchema("单页返回数量；最多 50。", 1, MaxVariablePageSize, 20)

internal fun variableChangeOperationSchema() = creatorObjectSchema(required = listOf("op")) {
    put("op", creatorStringSchema("修改类型。", listOf(
        "set_config", "create_object", "patch_object", "delete_object",
        "create_variable", "patch_variable", "delete_variable", "convert_variable_to_object",
        "create_version", "patch_version", "switch_version", "delete_version",
    )))
    put("id", creatorStringSchema("仅供工具内部引用的稳定 id，不是状态 JSON 的键。创建变量时可留空；宿主会先按所属对象和标题复用已有变量。"))
    put("new_id", creatorStringSchema("转换变量为 object 时的新对象 id；留空由宿主生成。"))
    put("name", creatorStringSchema("配置、对象或版本名称。"))
    put("title", creatorStringSchema("变量项标题，也是静态初始化状态 JSON 中对应的键名。"))
    put("parent_id", creatorStringSchema("父对象 id；空字符串表示根级。"))
    put("object_id", creatorStringSchema("变量所属对象 id；空字符串表示根级。同一批新建对象时填写该 create_object 显式声明的 id。"))
    put("enabled", creatorBooleanSchema("是否启用。"))
    put("description", creatorStringSchema("对象或变量说明。"))
    put("update_rule", creatorStringSchema("对象或变量的完整更新规则。AI 创建变量时必填；若只允许用户手动修改，也必须明确写出。"))
    put("dynamic_key", creatorBooleanSchema("对象是否允许动态键。"))
    put("type", creatorStringSchema(
        "叶子变量 JSON 类型；object 是容器节点，请使用 create_object 或 convert_variable_to_object。空字符串表示未设置。",
        listOf("", "number", "string", "boolean", "array"),
    ))
    put("default_value", creatorStringSchema("变量默认值文本。"))
    put("read_mode", creatorStringSchema("AI 读取方式。", VariableReadMode.entries.map { it.storageValue }))
    put("order", variableIntegerSchema("运行顺序。", 0, null))
    put("tree_view_order", variableIntegerSchema("目录显示顺序。", 0, null))
    put("initial_state_json", creatorStringSchema("完整初始化状态 JSON。"))
    put("schema_code", creatorStringSchema("完整变量校验 JavaScript。"))
    put("expanded_object_ids", creatorArraySchema("UI 默认展开对象 id。", creatorStringSchema("对象 id。"), 500))
    put("cascade", creatorBooleanSchema("删除对象时一并删除子对象和所属变量。"))
    put("activate", creatorBooleanSchema("创建版本后是否立即切换为活动版本。"))
}

internal fun variableIntegerSchema(description: String, minimum: Int?, maximum: Int?, default: Int? = null) = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
    default?.let { put("default", it) }
}

internal fun authoringGuideJson(targetName: String) = buildJsonObject {
    put("targetName", targetName)
    put("pagination", "inspect/search 只返回一页；继续使用 nextCursor，长 initial_state_json/schema_code 使用 nextOffset。")
    put("writeWorkflow", buildJsonArray {
        add(JsonPrimitive("先 inspect/read 确认目标和现状，再 preview_changes，最后 apply_changes；预览会自动使用最新快照，无需管理或讲解 revision。"))
        add(JsonPrimitive("预览在内存中完成；不得为了试写创建测试对象、测试变量或占位版本。"))
        add(JsonPrimitive("未要求修改的字段必须保留，长文本修改必须传完整新值。"))
        add(JsonPrimitive("每轮只执行作者当前消息要求的增量修改，不要重新执行上一轮已经完成的创建操作。"))
        add(JsonPrimitive("create_variable 按 object_id + title 幂等：相同路径已存在时更新原变量，不会再创建随机副本。"))
    })
    put("objects", "对象构成变量层级；parent_id 为空是根级，dynamic_key 允许运行时动态键。系统变量运行配置对象不可修改或删除。")
    put("variables", buildJsonObject {
        put("logicalTypes", buildJsonArray { VariableValueType.entries.forEach { add(JsonPrimitive(it.raw)) } })
        put("leafTypeValues", buildJsonArray {
            VariableValueType.entries.filterNot { it == VariableValueType.Object }.forEach { add(JsonPrimitive(it.raw)) }
        })
        put("object", "object 是第五种逻辑类型，但底层表示为可嵌套的 VariableObjectConfig 容器节点，不写入叶子变量的 type 字段。使用 create_object 创建，或 convert_variable_to_object 将现有叶子变量原位转换。")
        put("readModes", buildJsonObject {
            put("required", "每轮作为必读变量提供给 AI。")
            put("on_demand", "按需读取，避免把全部变量塞入上下文。")
        })
        put("defaultValue", "默认值是文本表示；应与 type 和 initial_state_json 保持一致。")
        put("stateKeys", "状态 JSON 的键来自对象 name 和变量 title；内部 id 只供工具定位，不是状态键。面向作者说明结果时优先使用 targetName、对象名和变量标题。")
        put("updateRule", "AI 创建每个变量都必须填写完整 update_rule；静态变量也要明确写“仅由用户手动修改，AI 不得自动更新”。")
    })
    put("zodRuntime", buildJsonObject {
        put("availability", "ElecKoi 固定内置 Zod；schema_code 执行时全局变量 z 已存在。不要 import、require、探测依赖，也不要提供原生 validate(state) 备用方案。")
        put("acceptedForms", buildJsonArray {
            add(JsonPrimitive("z.object({ ... })"))
            add(JsonPrimitive("const Schema = z.object({ ... })"))
            add(JsonPrimitive("export const Schema = z.object({ ... })"))
            add(JsonPrimitive("export default z.object({ ... })"))
        })
        put("requiredResult", "代码最终必须得到一个具有 safeParse 的 Zod schema；普通 export function validate(state) 不受支持。")
        put("example", "z.object({ 角色: z.object({ 好感度: z.number().min(0).max(100), 状态: z.enum(['平静', '紧张']) }) })")
        put("previewValidation", "preview_changes 会在设备内真实加载内置 Zod，先编译 schema，再用最终初始状态执行 safeParse；失败时不会生成可提交变更集。")
        put("emptyWorkspace", "变量为空不构成阻塞。作者描述需求后，AI 应在同一个 preview_changes 中创建对象/变量并写入匹配的 schema_code；不要因为当前 objectCount/variableCount 为 0 而拒绝。")
    })
    put("config", buildJsonObject {
        put("initial_state_json", "角色开始时的完整 JSON 状态。静态变量树变更且未显式提供该字段时，宿主按对象 name、变量 title 和 default_value 自动重建；显式提供时必须与变量树键结构完全一致。")
        put("schema_code", "变量总校验 Zod 代码。作者要求配置时，由 AI 根据变量树与约束主动生成、预览验证并提交；工具原样保存通过真实运行时验证的代码。")
        put("expanded_object_ids", "只影响编辑器默认展开状态。")
    })
    put("versions", "版本保存对象、变量、初始化状态和校验代码快照；可创建、重命名、切换和删除，至少保留一个。")
}

internal data class Page<T>(val items: List<T>, val nextCursor: String)

internal fun <T> page(items: List<T>, cursor: String, limit: Int): Page<T> {
    val offset = cursor.toIntOrNull()?.coerceIn(0, items.size) ?: 0
    val result = items.drop(offset).take(limit)
    val next = offset + result.size
    return Page(result, if (next < items.size) next.toString() else "")
}

internal sealed interface VariableSearchResult {
    data class Object(val value: VariableObjectConfig) : VariableSearchResult
    data class Variable(val value: VariableItemConfig) : VariableSearchResult
}

internal fun VariableSearchResult.summaryJson(): JsonObject = when (this) {
    is VariableSearchResult.Object -> value.summaryJson("object")
    is VariableSearchResult.Variable -> value.summaryJson("variable")
}

internal fun VariableObjectConfig.summaryJson(kind: String = "object") = buildJsonObject {
    put("kind", kind); put("id", id); put("name", name); put("parentId", parentId)
    put("enabled", enabled); put("dynamicKey", dynamicKey); put("system", isInitializationObject())
}

internal fun VariableObjectConfig.fullJson() = buildJsonObject {
    put("id", id); put("name", name); put("parentId", parentId); put("enabled", enabled)
    put("description", description); put("updateRule", updateRule); put("dynamicKey", dynamicKey)
    put("order", order); put("treeViewOrder", treeViewOrder); put("createdAt", createdAt); put("updatedAt", updatedAt)
    put("system", isInitializationObject())
}

internal fun VariableItemConfig.summaryJson(kind: String = "variable") = buildJsonObject {
    put("kind", kind); put("id", id); put("title", title); put("objectId", objectId)
    put("enabled", enabled); put("type", type); put("readMode", readMode.storageValue)
}

internal fun VariableItemConfig.fullJson() = buildJsonObject {
    put("id", id); put("title", title); put("objectId", objectId); put("enabled", enabled)
    put("type", type); put("defaultValue", defaultValue); put("description", description)
    put("updateRule", updateRule); put("readMode", readMode.storageValue); put("order", order)
    put("treeViewOrder", treeViewOrder); put("createdAt", createdAt); put("updatedAt", updatedAt)
}

internal fun VariableConfigVersion.summaryJson() = buildJsonObject {
    put("id", id); put("name", name); put("objectCount", objects.size); put("variableCount", variables.size)
    put("initialStateLength", initialStateJson.length); put("schemaCodeLength", schemaCode.length)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
}
