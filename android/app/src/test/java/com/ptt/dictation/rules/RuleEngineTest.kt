package com.ptt.dictation.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEngineTest {
    @Test
    fun `replaces korean slash command trigger`() {
        val rules =
            listOf(
                TextRule(
                    id = "slash-new-ko",
                    category = "slash-command",
                    trigger = "슬래시 뉴",
                    replacement = "/new",
                    priority = 1000,
                ),
            )

        val output = RuleEngine.apply("슬래시 뉴 프로젝트", rules)

        assertEquals("/new 프로젝트", output)
    }

    @Test
    fun `applies higher priority rule first`() {
        val rules =
            listOf(
                TextRule(
                    id = "generic",
                    category = "slash-command",
                    trigger = "slash new",
                    replacement = "/generic",
                    priority = 100,
                ),
                TextRule(
                    id = "specific",
                    category = "slash-command",
                    trigger = "slash new project",
                    replacement = "/new-project",
                    priority = 900,
                ),
            )

        val output = RuleEngine.apply("slash new project now", rules)

        assertEquals("/new-project now", output)
    }

    @Test
    fun `does not replace when trigger is inside a larger token`() {
        val rules =
            listOf(
                TextRule(
                    id = "new",
                    category = "slash-command",
                    trigger = "new",
                    replacement = "/new",
                    priority = 500,
                ),
            )

        val output = RuleEngine.apply("renew this draft", rules)

        assertEquals("renew this draft", output)
    }
}
