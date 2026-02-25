package com.ptt.dictation.rules

object RuleEngine {
    fun apply(
        input: String,
        rules: List<TextRule>,
    ): String {
        if (input.isBlank()) return input
        if (rules.isEmpty()) return input

        var output = input
        for (rule in rules.sortedWith(ruleComparator)) {
            if (!rule.enabled) continue
            val regex = toBoundaryRegex(rule.trigger)
            output =
                regex.replace(output) { match ->
                    val prefix = match.groupValues[1]
                    prefix + rule.replacement
                }
        }
        return output
    }

    private fun toBoundaryRegex(trigger: String): Regex {
        val tokens =
            trigger
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return Regex("$^")
        }

        val body = tokens.joinToString("\\s+") { Regex.escape(it) }
        // Keep leading separator in capture group to preserve punctuation/spacing.
        return Regex("(?i)(^|[\\s\\p{Punct}])($body)(?=$|[\\s\\p{Punct}])")
    }

    private val ruleComparator =
        compareByDescending<TextRule> { it.priority }
            .thenByDescending { it.trigger.length }
            .thenBy { it.id }
}
