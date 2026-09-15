package com.eleckoi.android.compatibility.mvu.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MvuMessageDisplayAdapterTest {
    private val state = """
        {
          "场景模式": "测试",
          "叙事模式": "默认",
          "user": {"名字": "", "当前位置": "示例地点"},
          "character": {"好感度": 0, "${'$'}internal": "hidden"}
        }
    """.trimIndent()

    @Test
    fun resolvesInlineStatusBoardScalarsFromStatData() {
        val source = """
            <span>{{format_message_variable::stat_data.场景模式}} | {{format_message_variable::stat_data.叙事模式}}</span>
            <span>{{format_message_variable::stat_data.user.当前位置}}</span>
        """.trimIndent()

        assertEquals(
            """
                <span>测试 | 默认</span>
                <span>示例地点</span>
            """.trimIndent(),
            MvuMessageDisplayAdapter.resolveVariableMacros(source, state),
        )
    }

    @Test
    fun formatsObjectsAndKeepsTheCallingLinesIndentation() {
        val resolved = """
            当前状态:
              {{format_message_variable::stat_data.user}}
        """.trimIndent().let { MvuMessageDisplayAdapter.resolveVariableMacros(it, state) }

        assertEquals(
            """
                当前状态:
                  名字: ""
                  当前位置: 示例地点
            """.trimIndent(),
            resolved,
        )
    }

    @Test
    fun getMacroUsesCompactJsonAndFormattingOmitsInternalDollarFields() {
        assertEquals(
            "0",
            MvuMessageDisplayAdapter.resolveVariableMacros(
                "{{get_message_variable::stat_data.character.好感度}}",
                state,
            ),
        )
        val formatted = MvuMessageDisplayAdapter.resolveVariableMacros(
            "{{format_message_variable::stat_data.character}}",
            state,
        )
        assertEquals("好感度: 0", formatted)
        assertFalse(formatted.contains("internal"))
    }

    @Test
    fun appendsStatusPlaceholderOnlyForCompletedAssistantDisplayUsingThatProtocol() {
        val patterns = listOf("/<StatusPlaceHolderImpl\\/>/g")

        assertEquals(
            "回复\n\n<StatusPlaceHolderImpl/>",
            MvuMessageDisplayAdapter.prepareAssistantText("回复", true, patterns),
        )
        assertEquals(
            "流式回复",
            MvuMessageDisplayAdapter.prepareAssistantText("流式回复", false, patterns),
        )
        assertEquals(
            "普通回复",
            MvuMessageDisplayAdapter.prepareAssistantText("普通回复", true, listOf("状态栏")),
        )
    }

    @Test
    fun neverDuplicatesAnAuthoredStatusPlaceholder() {
        val source = "回复\n\n<StatusPlaceHolderImpl/>"

        assertEquals(
            source,
            MvuMessageDisplayAdapter.prepareAssistantText(
                source,
                complete = true,
                displayRulePatterns = listOf("<StatusPlaceHolderImpl/>"),
            ),
        )
    }

    @Test
    fun injectsReadOnlySnapshotBridgeBeforeAnImportedMvuFrontendStarts() {
        val source = """
            <!doctype html>
            <html>
            <head><title>Status</title></head>
            <body>
              <script id="authored">
                await waitGlobalInitialized('Mvu');
                const state = getAllVariables();
                eventOn(Mvu.events.VARIABLE_UPDATE_ENDED, render);
              </script>
            </body>
            </html>
        """.trimIndent()

        val resolved = MvuMessageDisplayAdapter.resolveVariableMacros(source, state)

        assertTrue(resolved.contains("id=\"$MvuFrontendBridgeMarker\""))
        assertTrue(resolved.indexOf(MvuFrontendBridgeMarker) < resolved.indexOf("id=\"authored\""))
        assertTrue(resolved.contains("global.getAllVariables = () => clone(snapshot)"))
        assertTrue(resolved.contains("global.Mvu = mvu"))
        assertTrue(resolved.contains("stat_data: nativeSnapshot || {}"))
        assertTrue(resolved.contains("escape(value)"))
        assertTrue(resolved.contains("readOnly: true"))
        assertFalse(resolved.contains("replaceMvuData"))
    }

    @Test
    fun doesNotInjectMvuBrowserGlobalsIntoOrdinaryRichHtml() {
        val source = "<html><head></head><body><button>普通页面</button></body></html>"

        assertEquals(source, MvuMessageDisplayAdapter.resolveVariableMacros(source, state))
    }

    @Test
    fun neverDuplicatesTheFrontendSnapshotBridge() {
        val source = "<html><head></head><script>await waitGlobalInitialized('Mvu')</script></html>"
        val once = MvuMessageDisplayAdapter.resolveVariableMacros(source, state)
        val twice = MvuMessageDisplayAdapter.resolveVariableMacros(once, state)

        assertEquals(1, MvuFrontendBridgeMarker.toRegex().findAll(twice).count())
    }

    @Test
    fun safelyEmbedsVariableTextThatLooksLikeAClosingScriptTag() {
        val source = "<script>await waitGlobalInitialized('Mvu')</script>"
        val resolved = MvuMessageDisplayAdapter.resolveVariableMacros(
            source,
            """{"note":"</script><script>bad()</script>"}""",
        )

        assertFalse(resolved.contains("JSON.parse(\"{\\\"note\\\":\\\"</script>"))
        assertTrue(resolved.contains("<\\/script>"))
    }

    @Test
    fun injectsNarrowCardActionsIntoInteractiveImportedFrontend() {
        val source = """
            <html><head></head><body>
              <script>
                const start = (prompt) => triggerSlash('/send ' + prompt + '|/trigger');
              </script>
            </body></html>
        """.trimIndent()

        val resolved = MvuMessageDisplayAdapter.resolveVariableMacros(source, state)

        assertTrue(resolved.contains("id=\"$MvuFrontendActionBridgeMarker\""))
        assertTrue(resolved.indexOf(MvuFrontendActionBridgeMarker) < resolved.indexOf("const start"))
        assertTrue(resolved.contains("return requireSdk().chat.send(text)"))
        assertTrue(resolved.contains("value.endsWith(suffix)"))
        assertTrue(resolved.contains("ElecKoi only supports the /send ...|/trigger card action"))
        assertFalse(resolved.contains("eval(command)"))
    }

    @Test
    fun actionBridgeSupportsOpeningSwitchesWithoutDuplicatingItself() {
        val source = "<html><head></head><script>setChatMessages([{message_id:0,swipe_id:1}])</script></html>"
        val once = MvuMessageDisplayAdapter.resolveVariableMacros(source, state)
        val twice = MvuMessageDisplayAdapter.resolveVariableMacros(once, state)

        assertTrue(once.contains("sdk.openings.select(option.id)"))
        assertEquals(1, MvuFrontendActionBridgeMarker.toRegex().findAll(twice).count())
    }

    @Test
    fun ordinaryRichHtmlDoesNotReceiveInteractiveCardActions() {
        val source = "<html><head></head><body><button>普通按钮</button></body></html>"

        val resolved = MvuMessageDisplayAdapter.resolveVariableMacros(source, state)

        assertFalse(resolved.contains(MvuFrontendActionBridgeMarker))
    }
}
