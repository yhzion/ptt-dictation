package com.ptt.dictation.rules

interface TextRuleService {
    fun initialize()

    fun apply(text: String): String

    suspend fun syncFromServerIfNeeded(): RuleSyncResult
}

class NoopTextRuleService : TextRuleService {
    override fun initialize() {}

    override fun apply(text: String): String = text

    override suspend fun syncFromServerIfNeeded(): RuleSyncResult =
        RuleSyncResult.UpToDate(
            localVersion = 0L,
            remoteVersion = 0L,
        )
}

class DbBackedTextRuleService(
    private val store: RuleStore,
    private val syncManager: RuleSyncManager,
    private val defaultRules: List<TextRule> = defaultTextRules(),
) : TextRuleService {
    @Volatile
    private var cachedRules: List<TextRule> = emptyList()

    override fun initialize() {
        if (store.countRules() == 0) {
            store.upsertRules(defaultRules)
        }
        cachedRules = store.getActiveRules()
    }

    override fun apply(text: String): String = RuleEngine.apply(text, cachedRules)

    override suspend fun syncFromServerIfNeeded(): RuleSyncResult {
        val result = syncManager.syncIfNeeded()
        if (result is RuleSyncResult.Updated) {
            cachedRules = store.getActiveRules()
        }
        return result
    }
}
