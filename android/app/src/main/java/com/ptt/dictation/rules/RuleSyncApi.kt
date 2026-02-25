package com.ptt.dictation.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface RuleSyncApi {
    suspend fun fetchVersion(): Long

    suspend fun fetchChanges(sinceVersion: Long): RuleDeltaResponse
}

@Serializable
data class RuleVersionResponse(
    val rulesetVersion: Long,
)

@Serializable
data class RuleDeltaResponse(
    val rulesetVersion: Long,
    val changes: List<RuleChangePayload> = emptyList(),
)

@Serializable
data class RuleChangePayload(
    val id: String,
    val category: String,
    val trigger: String,
    val replacement: String,
    val locale: String = "*",
    val enabled: Boolean = true,
    val priority: Int = 100,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

class NoopRuleSyncApi : RuleSyncApi {
    override suspend fun fetchVersion(): Long = 0L

    override suspend fun fetchChanges(sinceVersion: Long): RuleDeltaResponse = RuleDeltaResponse(rulesetVersion = sinceVersion)
}

class HttpRuleSyncApi(
    baseUrl: String,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RuleSyncApi {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun fetchVersion(): Long =
        withContext(Dispatchers.IO) {
            val body = get("/v1/rules/version")
            json.decodeFromString<RuleVersionResponse>(body).rulesetVersion
        }

    override suspend fun fetchChanges(sinceVersion: Long): RuleDeltaResponse =
        withContext(Dispatchers.IO) {
            val body = get("/v1/rules/changes?sinceVersion=$sinceVersion")
            json.decodeFromString<RuleDeltaResponse>(body)
        }

    @Throws(IOException::class)
    private fun get(pathWithQuery: String): String {
        val target = URL("$normalizedBaseUrl$pathWithQuery")
        val connection =
            (target.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
            }

        return try {
            val status = connection.responseCode
            val stream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) {
                throw IOException("Rule sync request failed ($status): $body")
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
