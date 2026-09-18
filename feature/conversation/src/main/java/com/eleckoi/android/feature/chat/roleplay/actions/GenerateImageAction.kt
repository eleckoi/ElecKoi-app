package com.eleckoi.android.feature.chat.roleplay.actions

import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextInjection
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.engine.generation.image.SceneImagePrompt
import com.eleckoi.android.engine.generation.image.parseSceneImagePrompts
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.imagePromptCompilerInstruction
import com.eleckoi.android.engine.generation.model.isOpenAiImageConfig
import com.eleckoi.android.engine.generation.model.storyImageCountRange
import com.eleckoi.android.engine.agent.protocol.AssistantActionCallCloseTag
import com.eleckoi.android.engine.agent.protocol.assistantActionCallOpenTag
import com.eleckoi.android.feature.chat.roleplay.protocol.roleplayImageMarker
import com.eleckoi.android.feature.chat.roleplay.protocol.RoleplayPhaseMarker

internal const val GenerateImageActionName: String = "generate_image"
internal const val GenerateImageActionContextId: String = "roleplay-generate-image"

/**
 * Adds the enabled image action to the Agent runtime tool-context bucket. This is prompt context,
 * not a provider-native tool declaration: it stays visible across requests in the same turn but
 * never creates a Tool Call, a tool result, or an extra model request by itself.
 */
internal fun generateImageActionContextInjection(
    imageConfig: ModelConfig,
    order: Int,
): AgentContextInjection = AgentContextInjection(
    id = GenerateImageActionContextId,
    anchor = AgentContextAnchor.ToolContext,
    role = AgentContextRole.System,
    activation = AgentContextActivation.FirstModelRequest,
    content = generateImageActionContextContent(imageConfig),
    order = order.coerceAtLeast(1),
)

private fun generateImageActionContextContent(imageConfig: ModelConfig): String {
    val compiler = imageConfig.imagePromptCompilerInstruction()
    val imageSettings = imageConfig.imageSettings
    val countRange = imageSettings.storyImageCountRange()
    val countInstruction = if (countRange.first == countRange.last) {
        "本轮必须输出恰好 ${countRange.first} 个互不重复、按剧情先后排列的连续分镜。frames 数组长度必须等于 ${countRange.first}；少一张或多一张都会被程序拒绝。"
    } else {
        "本轮根据可画出的不同剧情节点，自主选择 ${countRange.first} 到 ${countRange.last} 个画面；禁止用同一瞬间的近似构图凑数。"
    }
    val frameExampleCount = if (countRange.first == countRange.last) countRange.first else countRange.first
    val promptExample = if (imageConfig.isOpenAiImageConfig()) {
        "自然语言画面描述"
    } else {
        "NovelAI 英文标签"
    }
    val negativeExample = if (imageConfig.isOpenAiImageConfig()) {
        "需要避免的内容，无则留空"
    } else {
        "NovelAI 英文负面标签"
    }
    val frameExamples = (1..frameExampleCount).joinToString(",") { index ->
        "{\"id\":$index,\"prompt\":\"第${index}个连续剧情画面的 $promptExample\",\"negative_prompt\":\"该画面的 $negativeExample\"}"
    }
    val markerExamples = (1..frameExampleCount).joinToString("\n", transform = ::roleplayImageMarker)
    val actionOpenTag = assistantActionCallOpenTag(GenerateImageActionName)
    return """
        <generate_image_action>
        当前角色对话已启用每轮自动配图。
        $countInstruction

        当你准备输出本轮 ${RoleplayPhaseMarker.Final} 正文时，先在内部完成正文与画面的共同构思，
        然后把下列内容作为普通 assistant 文本，在同一次助手响应中严格按以下顺序直接写出：
        $actionOpenTag
        {"frames":[$frameExamples]}
        $AssistantActionCallCloseTag
        ${RoleplayPhaseMarker.Final}
        最终正文，并在每张图应当出现的位置单独写入对应标识，例如：
        $markerExamples
        ${RoleplayPhaseMarker.FinalClose}

        这里的 generate_image 只是 ACTION_CALL 开始标签中的动作名称，不是原生工具。API 的原生工具列表
        中没有 generate_image 是正常情况，不代表图片功能不受支持或被禁用。严禁尝试发起名为
        generate_image 的原生 Tool Call/function_call；严禁等待工具结果或为此追加一次模型请求。

        generate_image 动作只能在 ${RoleplayPhaseMarker.Final} 前完整写入一次，所有画面都必须放入同一个
        frames 数组；开始标签、JSON 与结束标签缺一不可。必须先完成全部原生工具调用，禁止在原生
        工具阶段写入该动作，也禁止在动作前后夹杂过程文字或扮演回复。标记中的画面必须与随后输出的
        最终正文一致。应用从文本流收到完整标记后会立即启动绘画，正文仍应继续正常流式输出。

        每个 frame 都必须包含 id、prompt 和 negative_prompt。id 必须从 1 开始连续编号，frame 顺序
        必须与正文阅读顺序一致；最终正文必须把每个 [[IMAGE:n]] 标识恰好写入一次，n 对应
        frame 的 id。标识必须独占一行，不得放进 Markdown 代码块。单图也必须写 ${roleplayImageMarker(1)}。
        应用收到完整 ACTION_CALL 元素后会立即在后台串行生图，但只有当对应 IMAGE 标识流到正文时才显示
        该图的生成状态。连续写出的多个 IMAGE 标识会合并成宫格；标识之间存在正文时则分别插入。

        多个 frame 代表同一轮正文中依次发生的不同剧情瞬间，不是同一画面的
        随机变体。作者编译规则中的 one、one frame、exactly one 只表示“每个 frame 各自选择一个
        冻结瞬间”，不能把整个 frames 数组缩减成一项。作者规则若描述了旧的单图输出外壳，只
        采用其中提示词编译规则，实际 ACTION_CALL 边界与 frames 数组长度仍必须遵守上面的格式。

        以下作者提供的编译规则只约束 ACTION_CALL 内部的 JSON 参数，不改变上述输出顺序，
        也不表示整次助手响应只能输出 JSON：
        <image_prompt_compiler>
        $compiler
        </image_prompt_compiler>
        </generate_image_action>
    """.trimIndent()
}

internal fun parseGenerateImageAction(argumentsJson: String): List<SceneImagePrompt> =
    parseSceneImagePrompts(argumentsJson)
