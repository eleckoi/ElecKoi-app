package com.eleckoi.android.feature.chat.data

import java.time.LocalDateTime
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptMacrosTest {
    @Test
    fun `getvar resolves values set by a later preset entry`() {
        val resolver = PromptMacroResolver("{}", random = { 0.0 })
        val readEntry = resolver.applyMutations(
            "阶段={{getvar::macro_test.phase}} 计数={{getvar::macro_test.count}}",
        )
        val writeEntry = resolver.applyMutations(
            "{{setvar::macro_test.phase::preset-ok}}" +
                "{{setvar::macro_test.count::2}}" +
                "{{addvar::macro_test.count::3}}" +
                "{{incvar::macro_test.count}}{{decvar::macro_test.count}}",
        )

        assertEquals("阶段=preset-ok 计数=5", resolver.resolveValues(readEntry))
        assertEquals("65", resolver.resolveValues(writeEntry))
        assertEquals(
            "preset-ok",
            JSONObject(resolver.stateJson).getJSONObject("macro_test").getString("phase"),
        )
        assertEquals(
            5L,
            JSONObject(resolver.stateJson).getJSONObject("macro_test").getLong("count"),
        )
    }

    @Test
    fun `random roll and time macros are resolved before the model sees them`() {
        val resolver = PromptMacroResolver(
            stateJson = "{}",
            random = { 0.999999 },
            now = LocalDateTime.of(2026, 9, 13, 8, 9),
            locale = Locale.US,
        )
        val staged = resolver.applyMutations(
            "{{random::A::B::C}}/{{random::甲,乙,丙}}/{{random::red, blue::green}}/" +
                "{{roll::1d6}}/{{roll::20}}/" +
                "{{date}}/{{time}}/{{weekday}}/{{isodate}}/{{isotime}}",
        )

        assertEquals(
            "C/丙/green/6/20/September 13, 2026/8:09 AM/Sunday/2026-09-13/08:09",
            resolver.resolveValues(staged),
        )
        assertEquals(
            "red, blue",
            PromptMacroResolver("{}", random = { 0.0 }).resolveValues(
                "{{random::red, blue::green}}",
            ),
        )
    }

    @Test
    fun `nested json values share the turn state`() {
        val resolver = PromptMacroResolver("{}")
        val staged = resolver.applyMutations(
            "{{setvar::profile::\u007b\"hp\":2\u007d}}{{addvar::profile.hp::3}}",
        )

        assertEquals("5", resolver.resolveValues(staged + "{{getvar::profile.hp}}"))
    }

    @Test
    fun `unknown macros stay unchanged`() {
        val resolver = PromptMacroResolver("{}")
        assertEquals("{{unsupported::value}}", resolver.resolveValues("{{unsupported::value}}"))
        assertEquals("{{getglobalvar::value}}", resolver.resolveValues("{{getglobalvar::value}}"))
        assertEquals("{{pick::A::B}}", resolver.resolveValues("{{pick::A::B}}"))
    }
}
