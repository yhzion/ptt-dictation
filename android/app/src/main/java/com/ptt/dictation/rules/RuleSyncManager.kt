package com.ptt.dictation.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RuleSyncResult {
    data class Updated(
        val oldVersion: Long,
        val newVersion: Long,
        val upserted: Int,
        val deleted: Int,
    ) : RuleSyncResult

    data class UpToDate(
        val localVersion: Long,
        val remoteVersion: Long,
    ) : RuleSyncResult

    data class Failed(
        val localVersion: Long,
        val error: Throwable,
    ) : RuleSyncResult
}

class RuleSyncManager(
    private val store: RuleStore,
    private val syncApi: RuleSyncApi,
) {
    suspend fun syncIfNeeded(): RuleSyncResult =
        withContext(Dispatchers.IO) {
            val localVersion = store.getRulesetVersion()
            try {
                val remoteVersion = syncApi.fetchVersion()
                if (remoteVersion <= localVersion) {
                    return@withContext RuleSyncResult.UpToDate(
                        localVersion = localVersion,
                        remoteVersion = remoteVersion,
                    )
                }

                val delta = syncApi.fetchChanges(sinceVersion = localVersion)
                if (delta.rulesetVersion < localVersion) {
                    return@withContext RuleSyncResult.Failed(
                        localVersion = localVersion,
                        error =
                            IllegalStateException(
                                "Server returned stale ruleset version ${delta.rulesetVersion} < local $localVersion",
                            ),
                    )
                }

                val deleteIds = delta.changes.filter { it.deleted }.map { it.id }
                val upserts =
                    delta.changes
                        .filter { !it.deleted }
                        .map {
                            TextRule(
                                id = it.id,
                                category = it.category,
                                trigger = it.trigger,
                                replacement = it.replacement,
                                locale = it.locale,
                                enabled = it.enabled,
                                priority = it.priority,
                                updatedAt = it.updatedAt,
                            )
                        }

                store.applyServerChanges(
                    newVersion = delta.rulesetVersion,
                    upserts = upserts,
                    deleteRuleIds = deleteIds,
                )

                RuleSyncResult.Updated(
                    oldVersion = localVersion,
                    newVersion = delta.rulesetVersion,
                    upserted = upserts.size,
                    deleted = deleteIds.size,
                )
            } catch (error: Throwable) {
                RuleSyncResult.Failed(localVersion = localVersion, error = error)
            }
        }
}
