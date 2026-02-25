package com.ptt.dictation.rules

data class TextRule(
    val id: String,
    val category: String,
    val trigger: String,
    val replacement: String,
    val locale: String = "*",
    val enabled: Boolean = true,
    val priority: Int = 100,
    val updatedAt: Long = System.currentTimeMillis(),
)

fun defaultTextRules(nowProvider: () -> Long = { System.currentTimeMillis() }): List<TextRule> {
    val now = nowProvider()
    return listOf(
        TextRule(
            id = "builtin/slash-new-ko",
            category = "slash-command",
            trigger = "슬래시 뉴",
            replacement = "/new",
            locale = "ko-KR",
            enabled = true,
            priority = 1_000,
            updatedAt = now,
        ),
        TextRule(
            id = "builtin/slash-new-en",
            category = "slash-command",
            trigger = "slash new",
            replacement = "/new",
            locale = "en-US",
            enabled = true,
            priority = 1_000,
            updatedAt = now,
        ),
    )
}
