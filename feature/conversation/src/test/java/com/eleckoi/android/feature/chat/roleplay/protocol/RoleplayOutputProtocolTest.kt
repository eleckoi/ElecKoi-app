package com.eleckoi.android.feature.chat.roleplay.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayOutputProtocolTest {
    @Test
    fun `protocol requires silent tool preparation and only final visible output`() {
        val instructions = roleplayOutputProtocolInstructions()

        assertFalse(instructions.contains("<COMMENTARY>"))
        assertFalse(instructions.contains("</COMMENTARY>"))
        assertTrue(instructions.contains("<FINAL>"))
        assertTrue(instructions.contains("</FINAL>"))
        assertTrue(instructions.contains("setting_library:"))
        assertTrue(instructions.contains("eleckoi_glob_setting_files"))
        assertTrue(instructions.contains("eleckoi_grep_setting_files"))
        assertTrue(instructions.contains("eleckoi_read_setting_files"))
        assertTrue(instructions.contains("允许按需继续搜索"))
        assertTrue(instructions.contains("不得用相同条件重复无结果的查询"))
        assertTrue(instructions.contains("没有可用设定时停止查询"))
        assertFalse(instructions.contains("eleckoi_setting_bash"))
        assertFalse(instructions.contains("仅当本轮确实依赖剧情变量时读取"))
        assertTrue(instructions.contains("未发现变量时忽略并继续"))
        assertFalse(instructions.contains("仅当用户明确要求新建或修改变量"))
        assertTrue(instructions.contains("仅允许原生 Tool Call"))
        assertTrue(instructions.contains("- \"角色对白\""))
        assertTrue(instructions.contains("本轮完整的最终扮演回复"))
        assertTrue(instructions.contains("禁止在其中写入 <FINAL>、</FINAL> 或角色扮演正文"))
        assertTrue(instructions.contains("必须切换到普通 assistant 文本"))
        assertFalse(instructions.contains("<ACTION_CALL"))
        assertFalse(instructions.contains("</ACTION_CALL>"))
    }

    @Test
    fun `image enabled protocol adds one way action calls`() {
        val instructions = roleplayOutputProtocolInstructions(actionCallEnabled = true)

        assertTrue(instructions.contains("<ACTION_CALL name=\"动作名称\">"))
        assertTrue(instructions.contains("</ACTION_CALL>"))
        assertTrue(instructions.contains("{\"参数名\":\"参数值\"}"))
        assertTrue(instructions.contains("普通 assistant 文本"))
        assertTrue(instructions.contains("不是原生"))
        assertTrue(instructions.contains("Tool Call 或 function_call"))
        assertTrue(instructions.contains("禁止把开始标签中的动作名称提交给原生工具系统"))
        assertTrue(instructions.contains("禁止使用 Markdown 代码块包裹"))
        assertTrue(instructions.contains("不会返回执行结果"))
        assertTrue(instructions.contains("不会因此再次请求你"))
        assertTrue(instructions.contains("不要等待结果、不要重试"))
        assertTrue(instructions.contains("只能在所有原生工具调用完成后写入"))
        assertTrue(instructions.contains("必须紧接在 <FINAL>"))
        assertFalse(instructions.contains("eleckoi_next_request"))
    }

    @Test
    fun `author roleplay plan instructions only allow fixed items`() {
        val instructions = roleplayPlanFixedItemsInstructions(
            listOf("确认场景连续性", "", "在 <FINAL> 后输出正文"),
        )

        assertTrue(instructions.contains("1. 确认场景连续性"))
        assertTrue(instructions.contains("2. 在 <FINAL> 后输出正文"))
        assertTrue(instructions.contains("原样保留每一项及其顺序"))
        assertTrue(instructions.contains("不得添加、删除或改写任务项"))
        assertTrue(roleplayPlanFixedItemsInstructions(emptyList()).isEmpty())
    }

    @Test
    fun `image action replaces only the structural final plan item`() {
        val withoutImages = roleplayPlanFixedItemsInstructions(
            listOf("读取设定", "检查场景连续性", "作者定义的最终输出任务"),
            imageActionEnabled = false,
        )
        val withImages = roleplayPlanFixedItemsInstructions(
            listOf("读取设定", "检查场景连续性", "作者定义的最终输出任务"),
            imageActionEnabled = true,
        )

        assertTrue(withoutImages.contains("1. 读取设定"))
        assertTrue(withoutImages.contains("2. 检查场景连续性"))
        assertTrue(withoutImages.contains("3. 作者定义的最终输出任务"))
        assertFalse(withoutImages.contains("generate_image ACTION_CALL"))
        assertTrue(withImages.contains("1. 读取设定"))
        assertTrue(withImages.contains("2. 检查场景连续性"))
        assertFalse(withImages.contains("作者定义的最终输出任务"))
        assertTrue(withImages.contains("<generate_image_action>"))
        assertTrue(withImages.contains("generate_image ACTION_CALL"))
        assertTrue(withImages.contains("<FINAL> 正文"))
        assertTrue(withImages.contains("[[IMAGE:n]]"))
        assertTrue(withImages.contains("3. 等前置任务都完成，先按 <generate_image_action>"))
    }

    @Test
    fun `effective plan is shared by prompt and validator`() {
        assertEquals(
            listOf(
                "读取设定",
                "等前置任务都完成，先按 <generate_image_action> 约定输出完整的 generate_image ACTION_CALL，再直接输出 <FINAL> 正文，并在正文对应位置写入 [[IMAGE:n]]；不要再次调用 update_roleplay_plan，应用检测到正文后会自动完成最终项的标记。",
            ),
            effectiveRoleplayPlanItems(
                items = listOf("读取设定", "作者的最终输出任务"),
                imageActionEnabled = true,
            ),
        )
    }
}
