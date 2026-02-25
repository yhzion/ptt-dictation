package com.ptt.dictation.rules

interface RuleStore {
    fun getRulesetVersion(): Long

    fun setRulesetVersion(version: Long)

    fun applyServerChanges(
        newVersion: Long,
        upserts: List<TextRule>,
        deleteRuleIds: List<String>,
    )

    fun getActiveRules(): List<TextRule>

    fun countRules(): Int

    fun upsertRules(rules: List<TextRule>)

    fun deleteRules(ruleIds: List<String>)
}
