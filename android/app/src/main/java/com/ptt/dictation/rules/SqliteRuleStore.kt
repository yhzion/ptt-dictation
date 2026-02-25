package com.ptt.dictation.rules

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SqliteRuleStore(
    context: Context,
) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION), RuleStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_RULES (
                rule_id TEXT PRIMARY KEY,
                category TEXT NOT NULL,
                trigger_text TEXT NOT NULL,
                replacement_text TEXT NOT NULL,
                locale TEXT NOT NULL DEFAULT '*',
                enabled INTEGER NOT NULL DEFAULT 1,
                priority INTEGER NOT NULL DEFAULT 100,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX $INDEX_RULES_ACTIVE
            ON $TABLE_RULES (enabled, priority DESC, trigger_text)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_SYNC_STATE (
                singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
                ruleset_version INTEGER NOT NULL DEFAULT 0,
                last_synced_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO $TABLE_SYNC_STATE (singleton_id, ruleset_version, last_synced_at)
            VALUES (1, 0, 0)
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        // Schema v1 is the initial sync-ready format.
    }

    override fun getRulesetVersion(): Long {
        synchronized(this) {
            val db = readableDatabase
            db.rawQuery(
                "SELECT ruleset_version FROM $TABLE_SYNC_STATE WHERE singleton_id = 1",
                null,
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return 0L
                }
                return cursor.getLong(0)
            }
        }
    }

    override fun setRulesetVersion(version: Long) {
        synchronized(this) {
            val db = writableDatabase
            upsertSyncState(db = db, version = version)
        }
    }

    override fun applyServerChanges(
        newVersion: Long,
        upserts: List<TextRule>,
        deleteRuleIds: List<String>,
    ) {
        synchronized(this) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (ruleId in deleteRuleIds) {
                    db.delete(
                        TABLE_RULES,
                        "rule_id = ?",
                        arrayOf(ruleId),
                    )
                }
                for (rule in upserts) {
                    upsertRule(db, rule)
                }
                upsertSyncState(db = db, version = newVersion)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override fun getActiveRules(): List<TextRule> {
        synchronized(this) {
            val db = readableDatabase
            val result = mutableListOf<TextRule>()
            db.query(
                TABLE_RULES,
                RULE_COLUMNS,
                "enabled = 1",
                null,
                null,
                null,
                "priority DESC, LENGTH(trigger_text) DESC, trigger_text ASC",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result +=
                        TextRule(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("rule_id")),
                            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                            trigger = cursor.getString(cursor.getColumnIndexOrThrow("trigger_text")),
                            replacement = cursor.getString(cursor.getColumnIndexOrThrow("replacement_text")),
                            locale = cursor.getString(cursor.getColumnIndexOrThrow("locale")),
                            enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
                            priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
                            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        )
                }
            }
            return result
        }
    }

    override fun countRules(): Int {
        synchronized(this) {
            val db = readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM $TABLE_RULES", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return 0
                }
                return cursor.getInt(0)
            }
        }
    }

    override fun upsertRules(rules: List<TextRule>) {
        if (rules.isEmpty()) return
        synchronized(this) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (rule in rules) {
                    upsertRule(db, rule)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun upsertRule(
        db: SQLiteDatabase,
        rule: TextRule,
    ) {
        val values =
            ContentValues().apply {
                put("rule_id", rule.id)
                put("category", rule.category)
                put("trigger_text", rule.trigger)
                put("replacement_text", rule.replacement)
                put("locale", rule.locale)
                put("enabled", if (rule.enabled) 1 else 0)
                put("priority", rule.priority)
                put("updated_at", rule.updatedAt)
            }
        db.insertWithOnConflict(
            TABLE_RULES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun upsertSyncState(
        db: SQLiteDatabase,
        version: Long,
    ) {
        val now = System.currentTimeMillis()
        val values =
            ContentValues().apply {
                put("singleton_id", 1)
                put("ruleset_version", version)
                put("last_synced_at", now)
            }
        db.insertWithOnConflict(
            TABLE_SYNC_STATE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun deleteRules(ruleIds: List<String>) {
        if (ruleIds.isEmpty()) return
        synchronized(this) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (ruleId in ruleIds) {
                    db.delete(
                        TABLE_RULES,
                        "rule_id = ?",
                        arrayOf(ruleId),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    companion object {
        private const val DB_NAME = "rules.db"
        private const val DB_VERSION = 1

        private const val TABLE_RULES = "rules"
        private const val TABLE_SYNC_STATE = "rule_sync_state"
        private const val INDEX_RULES_ACTIVE = "idx_rules_active"

        private val RULE_COLUMNS =
            arrayOf(
                "rule_id",
                "category",
                "trigger_text",
                "replacement_text",
                "locale",
                "enabled",
                "priority",
                "updated_at",
            )
    }
}
