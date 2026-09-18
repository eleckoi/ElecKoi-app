package com.eleckoi.android.feature.chat.roleplay.actions

import com.eleckoi.android.engine.agent.api.AgentContextActivation
import com.eleckoi.android.engine.agent.api.AgentContextAnchor
import com.eleckoi.android.engine.agent.api.AgentContextRole
import com.eleckoi.android.engine.generation.model.ImageGenerationSettings
import com.eleckoi.android.engine.generation.model.ModelConfig
import com.eleckoi.android.engine.generation.model.OpenAiImageProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateImageActionTest {
    @Test
    fun `image action is permanent developer tool context with action before final reply`() {
        val injection = generateImageActionContextInjection(
            imageConfig = ModelConfig(
                imageSettings = ImageGenerationSettings(
                    promptCompilerInstruction = "Return JSON only.",
                    fixedImageCount = 3,
                ),
            ),
            order = 4_001,
        )
        val instructions = injection.content

        assertEquals(GenerateImageActionContextId, injection.id)
        assertEquals(AgentContextAnchor.ToolContext, injection.anchor)
        assertEquals(AgentContextRole.System, injection.role)
        assertEquals(AgentContextActivation.FirstModelRequest, injection.activation)
        assertEquals(4_001, injection.order)
        val action = instructions.indexOf("<ACTION_CALL name=\"generate_image\">")
        val actionClose = instructions.indexOf("</ACTION_CALL>", startIndex = action)
        val final = instructions.indexOf("<FINAL>", startIndex = actionClose)
        assertTrue(action >= 0)
        assertTrue(actionClose > action)
        assertTrue(final > actionClose)
        assertTrue(instructions.contains("{\"frames\":"))
        assertTrue(instructions.contains("</FINAL>"))
        assertTrue(instructions.contains("\"frames\""))
        assertTrue(instructions.contains("恰好 3 个"))
        val actionExample = instructions.substring(action, final)
        assertEquals(3, Regex("\\\"id\\\"").findAll(actionExample).count())
        assertTrue(instructions.contains("连续分镜"))
        assertTrue(instructions.contains("[[IMAGE:1]]"))
        assertTrue(instructions.contains("[[IMAGE:2]]"))
        assertTrue(instructions.contains("[[IMAGE:3]]"))
        assertTrue(instructions.contains("连续写出的多个 IMAGE 标识会合并成宫格"))
        assertTrue(instructions.contains("作为普通 assistant 文本"))
        assertTrue(instructions.contains("不是原生工具"))
        assertTrue(instructions.contains("没有 generate_image 是正常情况"))
        assertTrue(instructions.contains("严禁尝试发起名为"))
        assertTrue(instructions.contains("generate_image 的原生 Tool Call/function_call"))
        assertTrue(instructions.contains("严禁等待工具结果或为此追加一次模型请求"))
        assertTrue(instructions.contains("只约束 ACTION_CALL 内部的 JSON 参数"))
        assertTrue(instructions.contains("不表示整次助手响应只能输出 JSON"))
    }

    @Test
    fun `image action keeps an instruction longer than the former limit`() {
        val instruction = "BEGIN\n" + "preserve this compiler rule\n".repeat(600) + "END"

        val content = generateImageActionContextInjection(
            imageConfig = ModelConfig(
                imageSettings = ImageGenerationSettings(
                    promptCompilerInstruction = instruction,
                ),
            ),
            order = 1,
        ).content

        assertTrue(instruction.length > 12_000)
        assertTrue(content.contains(instruction))
    }

    @Test
    fun `OpenAI image action requests natural language descriptions`() {
        val content = generateImageActionContextInjection(
            imageConfig = ModelConfig(provider = OpenAiImageProviderId),
            order = 1,
        ).content

        assertTrue(content.contains("自然语言画面描述"))
        assertTrue(content.contains("需要避免的内容，无则留空"))
        assertTrue(content.contains("Write coherent natural-language descriptions"))
        assertTrue(!content.contains("NovelAI 英文标签"))
    }

    @Test
    fun `parser reads generate image arguments`() {
        val prompt = parseGenerateImageAction(
            """{"frames":[{"id":1,"prompt":"1girl, rain","negative_prompt":"text, watermark"}]}""",
        ).single()

        assertEquals("1girl, rain", prompt.prompt)
        assertEquals("text, watermark", prompt.negativePrompt)
        assertEquals(1, prompt.frameIndex)
    }

    @Test
    fun `parser keeps ordered story frame ids`() {
        val prompts = parseGenerateImageAction(
            """{"frames":[{"id":1,"prompt":"girl opens door","negative_prompt":"text"},{"id":2,"prompt":"girl under rain","negative_prompt":"watermark"}]}""",
        )

        assertEquals(2, prompts.size)
        assertEquals(listOf(1, 2), prompts.map { it.frameIndex })
        assertEquals(listOf("girl opens door", "girl under rain"), prompts.map { it.prompt })
    }
}
